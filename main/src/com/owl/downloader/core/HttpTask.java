package com.owl.downloader.core;

import com.owl.downloader.io.IOCallback;
import com.owl.downloader.io.IOScheduler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
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
    private final IOScheduler ioScheduler = IOScheduler.getInstance();
    private long currentConnections;
    private long downloadSpeed = 0;
    private long downloadedLength = 0;
    private long totalLength = 0;
    private final List<FileData> files = new LinkedList<>();
    HttpIOCallback httpIOCallback = new HttpIOCallback();

    static {
        uriTaskFactories.put("http", HttpTask::new);
        uriTaskFactories.put("https", HttpTask::new);
    }

    public HttpTask(URI uri) {
        super(new File(uri.getPath()).getName());
        this.uri = uri;
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
        HttpURLConnection httpConnection = null;
        try {
            httpConnection = (HttpURLConnection) this.uri.toURL().openConnection(proxy);
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert httpConnection != null;
        totalLength = httpConnection.getContentLength();
        type = httpConnection.getContentType();
        createFile();
        FileData.BlockSelector blockSelector = FileData.BlockSelector.getDefault();
        List<FileData.Block> availableBlocks = files.get(0).getBlocks();  //need to change.
        FileData.Block block;
        while ((block = blockSelector.select(availableBlocks)) != null) {
            if (currentConnections < getMaximumConnections()) {
                ++currentConnections;
                block.available = false;
                createConnection(block);
            }
        }
    }

    /**
     * Create connection for a block,send channels and buffers to IOScheduler.
     */
    private void createConnection(FileData.Block block) {
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

            StringBuilder requestMessage = new StringBuilder();
            requestMessage.append("GET " + fileUrl + " HTTP/1.1\r\n");
            requestMessage.append("Host:" + host + "\r\n");
            requestMessage.append("Connection: close\r\n");
            requestMessage.append("Range: bytes=" + block.offset + "-" + (block.length + block.offset - 1) + "\r\n");
            requestMessage.append("\r\n");

            ByteBuffer requestBuffer = ByteBuffer.wrap(requestMessage.toString().getBytes());
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
    public void skipHeader(SocketChannel socketChannel) throws IOException {
        ByteBuffer tempBuffer = ByteBuffer.allocate(1);
        Byte LastByte = 0;
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
     * Callback class,called once io finish,adjust downloaded size and the mount of current connctions.
     */
    class HttpIOCallback implements IOCallback {
        @Override
        public void callback(Channel channel, ByteBuffer buffer, int size, Exception exception) {
            adjustDownloadedLength(size);
            --currentConnections;
        }
    }

    /**
     * Used to calculate download speed.
     */
    class HttpDownloadSpeed implements Runnable {
        @Override
        public void run() {
            long lastDownloadedLength;
            while (downloadedLength < totalLength) {
                lastDownloadedLength = downloadedLength;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                downloadSpeed = downloadedLength - lastDownloadedLength; //kb/s
            }
        }
    }
}



