package com.owl.downloader.core;

import com.owl.downloader.io.IOCallback;
import com.owl.downloader.io.IOScheduler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * Task that downloads http url
 *
 * @author Daqige
 * @version 1.0
 */
public class HttpTask extends BaseTask implements Task {
    private final URI uri;
    private Proxy proxy;
    private String type;
    private String protocol;
    private final IOScheduler ioScheduler = IOScheduler.getInstance();
    private long currentConnections;
    private long downloadSpeed = 0;
    private long downloadedLength = 0;
    private long totalLength = 0;
    private long currentTime;
    private final List<FileData> files = new LinkedList<>();
    HttpIOCallback httpIOCallback = new HttpIOCallback();

    static {
        uriTaskFactories.put("http", HttpTask::new);
        uriTaskFactories.put("https", HttpTask::new);
    }

    public HttpTask(URI uri) {
        super(new File(uri.getPath()).getName());
        this.uri = uri;
        this.protocol = uri.getScheme();
        this.proxy = getProxySelector().select(uri).get(0);
    }

    /**
     * Adjust downloaded length,always occurs during callback.
     */
    void adjustDownloadedLength(int size) {
        downloadedLength += size;
    }

    @Override
    public long downloadSpeed() {
        return downloadSpeed;
    }

    @Override
    public long uploadSpeed() {
        return 0;
    }

    @Override
    public long downloadedLength() {
        return downloadedLength;
    }

    @Override
    public long uploadedLength() {
        return 0;
    }

    @Override
    public long totalLength() {
        return totalLength;
    }

    @Override
    public List<FileData> files() {
        return files;
    }

    /**
     * Create connections for available blocks.
     */
    @Override
    public void run() {
        if(protocol == "http"){
            setHttpFileAttributes();
        }else {

        }

        createFile();
        FileData.BlockSelector blockSelector = FileData.BlockSelector.getDefault();
        List<FileData.Block> availableBlocks = files.get(0).getBlocks();  //need to change.
        FileData.Block block;
        currentConnections = System.currentTimeMillis();

        while ((block = blockSelector.select(availableBlocks)) != null) {
            if (status() == Status.PAUSED){
                long lastLength = downloadedLength;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                downloadSpeed = (downloadedLength - lastLength)/1024;
                continue;
            }
            if (currentConnections < getMaximumConnections() && status() == Status.ACTIVE) {
                ++currentConnections;
                block.available = false;
                if(protocol == "http") {
                    createHttpConnection(block);
                }else {

                }
            }
        }

        changeStatus(Status.COMPLETED);
    }

    /**
     * Set Http source file's length and type.
     */
    private void setHttpFileAttributes(){
        HttpURLConnection httpConnection = null;
        try {
            httpConnection = (HttpURLConnection) this.uri.toURL().openConnection(proxy);
        } catch (IOException e) {
            changeStatus(Status.ERROR);
            return;
        }
        totalLength = httpConnection.getContentLength();
        type = httpConnection.getContentType();
    }

    private void setHttpsFileAttributes(){

    }

    /**
     * Create connection for a block,send channels and buffers to IOScheduler.
     */
    private void createHttpConnection(FileData.Block block) {
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);


            String host = uri.getHost();
            String fileUrl = uri.getPath();
            int port = uri.getPort();
            if (port == -1) {
                port = 80;
            }
            InetSocketAddress address = new InetSocketAddress(host, port);
            socketChannel.connect(address);

            String requestMessage = ("GET " + fileUrl + " HTTP/1.1\r\n") +
                    "Host:" + host + "\r\n" +
                    "Connection: close\r\n" +
                    "Range: bytes=" + block.offset + "-" + (block.length + block.offset - 1) + "\r\n" +
                    "\r\n";
            ByteBuffer requestBuffer = ByteBuffer.wrap(requestMessage.getBytes());
            socketChannel.write(requestBuffer);
            skipHeader(socketChannel);

            FileChannel fileChannel = (new FileInputStream(getDirectory() + name() + "." + type)).getChannel().position(block.offset);
            ByteBuffer responseBuffer = ByteBuffer.allocate(16 * 1024);

            ioScheduler.read(socketChannel, responseBuffer, httpIOCallback);
            ioScheduler.write(fileChannel, responseBuffer, httpIOCallback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Skip the header to download content.
     */
    private void skipHeader(SocketChannel socketChannel) throws IOException {
        ByteBuffer tempBuffer = ByteBuffer.allocate(1);
        byte LastByte = 0;
        int read = socketChannel.read(tempBuffer);
        while (read != -1) {
            if (LastByte == 10 && tempBuffer.get(0) == 13) {
                break;
            }
            LastByte = tempBuffer.get(0);
            tempBuffer.clear();
            read = socketChannel.read(tempBuffer);
        }
        tempBuffer.clear();
        socketChannel.read(tempBuffer);
    }

    /**
     * Create a fixed size file to store resource file.
     */
    private void createFile() {
        File file = new File(getDirectory() + name() + "." + type);
        if (!file.exists()) {
            RandomAccessFile randomAccessFile = null;
            try {
                randomAccessFile = new RandomAccessFile(file, "rw");
                randomAccessFile.setLength(totalLength);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (randomAccessFile != null) {
                    try {
                        randomAccessFile.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        this.files.add(new FileData(file, (int) getBlockSize()));
    }

    /**
     * Callback class,called once io finish,adjust downloaded size, speed and the amount of current connctions.
     */
    class HttpIOCallback implements IOCallback {
        @Override
        public void callback(Channel channel, ByteBuffer buffer, int size, Exception exception) {
            long lastTime = currentTime;
            currentTime = System.currentTimeMillis();
            downloadSpeed = size/(currentTime - lastTime)/1000/1024;  //kb/s
            adjustDownloadedLength(size);
            --currentConnections;
        }
    }

}



