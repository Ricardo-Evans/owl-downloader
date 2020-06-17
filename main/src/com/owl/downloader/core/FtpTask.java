package com.owl.downloader.core;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Proxy;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.*;
import java.util.Objects;

import com.owl.downloader.io.IOCallback;
import com.owl.downloader.io.IOScheduler;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;



/**
 * Used for ftp download
 * ftp format: ftp://username:password@host:port[/url]
 *
 * @author Hahh
 * @version 1.0
 */
public class FtpTask extends BaseTask implements Task{
    private final IOScheduler ioScheduler = IOScheduler.getInstance();
    private URI uri = null;
    private String userName;
    private String ftpHost;
    private String path;
    private String workingPath;
    private String fileName;
    private String type;
    private String protocol;
    private String psw;
    private int serverPort;
    private Proxy proxy;
    private long currentConnections = 0;
    private long downloadSpeed = 0;
    private long downloadedLength = 0;
    private long totalLength = 0;
    private long currentTime;
    private final List<FileData> files = new LinkedList<>();
    private FTPClient client = new FTPClient();

    /**
     * initiate
     *
     * @param uri the uri
     * @param serverPort server port
     */
    public FtpTask(URI uri, int serverPort) {
        super(new File(uri.getPath()).getName());
        this.uri = uri;
        this.protocol = uri.getScheme();
        this.proxy = getProxySelector().select(uri).get(0);
        this.ftpHost = uri.getHost();
        this.path = uri.getPath();
        this.serverPort = serverPort;

        String filePath = uri.getPath();
        this.workingPath = filePath.substring(0, path.lastIndexOf("/"));
        this.fileName = filePath.substring(path.lastIndexOf("/") + 1, path.length());
        this.type = fileName.split(".")[1];
    }

    @Override
    public long downloadSpeed() {
        return downloadSpeed;
    }

    @Override
    public long uploadSpeed() {
        return uploadSpeed();
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

    @Override
    public void run() {
        if(protocol.equals("ftp")){

            try{
                connectAndLogin();
                getUserInfo();
            }catch (IOException e){
                changeStatus(Status.ERROR, e);
                return;
            }
        }

        /**
         * Use passive mode
         * Use binary upload
         */
        client.enterLocalPassiveMode();
        try {
            client.setFileType(FTP.BINARY_FILE_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // get the length of the file
        FTPFile[] fs = new FTPFile[0];
        try {
            fs = client.listFiles(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        totalLength = fs[0].getSize();

        createFile();
        FileData.BlockSelector blockSelector = FileData.BlockSelector.getDefault();
        List<FileData.Block> availableBlocks = files.get(0).getBlocks();  //need to change.

        FileData.Block block;
        currentTime = System.currentTimeMillis();

        // objects.requireNonNull: if Objects is empty, throw NullPointerException; else return this object
        while ((block = Objects.requireNonNull(blockSelector).select(availableBlocks)) != null) {
            if (currentConnections < getMaximumConnections() && status() == Status.ACTIVE) {
                ++currentConnections;
                block.available = false;
                createFTPConnection(block);

            }
        }
        changeStatus(Status.COMPLETED);
        try {
            closeFTP();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adjust downloaded length,always occurs during callback.
     */
    private void adjustDownloadedLength(int size) {
        downloadedLength += size;
    }

    /**
     * Get the username and password from uri
     */
    private  void getUserInfo(){
        String userInfo = uri.getUserInfo();
        if(userInfo == null){
            userName = null;
            psw = null;
        }else{
            userName = userInfo.split(":")[0];
            psw = userInfo.split(":")[1];
        }
    }

    private void createFTPConnection(FileData.Block block) {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false); //non-blocking

            int port = uri.getPort();
            if (port == -1) {
                port = 21;
            }

            // connect
            InetSocketAddress address = new InetSocketAddress(ftpHost, port);
            socketChannel.connect(address);

            boolean connected = socketChannel.finishConnect();
            while (!connected) {
                connected = socketChannel.finishConnect();
            }
            System.out.println("是否连接" + socketChannel.isConnected());

            // login
            ByteBuffer logInBuffer = ByteBuffer.wrap(("User" + userName +"\r\n").getBytes());
            ByteBuffer logInResponseBuffer = ByteBuffer.allocate(16);
            socketChannel.write(logInBuffer);
            // TODO: check if succeed; not find a way to get the check code yet

            logInBuffer = ByteBuffer.wrap(("PASS" + psw + "\r\n").getBytes());
            socketChannel.write(logInBuffer);
            // TODO: check if succeed; not find a way to get the check code yet

            client.changeWorkingDirectory(workingPath);

            String changeMenu = "CDW" + workingPath + "\r\n";
//            String positionMsg = "REST" + block.offset;
            String downloadMsg = "RETR" + fileName + "\r\n";

            // change to the working path
            ByteBuffer msgBuffer = ByteBuffer.wrap(changeMenu.getBytes());
            socketChannel.write(msgBuffer);
            // TODO: check if succeed; not find a way to get the check code yet

            // send instruction to download
            msgBuffer.clear();
            msgBuffer = ByteBuffer.wrap(downloadMsg.getBytes());
            socketChannel.write(msgBuffer);
            // TODO: check if succeed; not find a way to get the check code yet

            RandomAccessFile randomAccessFile = new RandomAccessFile(files.get(0).getFile(), "rw");
            FileChannel fileChannel = randomAccessFile.getChannel();

            fileChannel.position(block.offset);
            ByteBuffer responseBuffer = ByteBuffer.allocateDirect(64 * 1024);

            ftpRead(socketChannel, fileChannel, responseBuffer);

        }catch(IOException e){
            block.available = true;
        }
    }

    private void ftpRead(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer buffer) {

        IOCallback httpReadCallback = (Channel socketchannel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            long lastTime = currentTime;
            currentTime = System.currentTimeMillis();
            downloadSpeed = size / (currentTime - lastTime) * 1000;//B/s
            System.out.println(size);
            synchronized (this) {
                adjustDownloadedLength(size);
            }
            if (size != -1) {
                ftpWrite((ReadableByteChannel) socketchannel, writeChannel, responseBuffer);
            } else {
                --currentConnections;
            }
        };
        Objects.requireNonNull(ioScheduler).read(readChannel, buffer, httpReadCallback);


    }

    private void ftpWrite(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer buffer) {
        IOCallback httpWriteCallback = (Channel fileChannel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            ftpRead(readChannel, (WritableByteChannel) fileChannel, responseBuffer);
        };
        Objects.requireNonNull(ioScheduler).write(writeChannel, buffer, httpWriteCallback);
    }

    /**
     * Try to connect the host and log in
     * If reply is wrong, abort and disconnect
     *
     * abort: cut down the file transfer, return true if succeed, or false
     * disconnect: cut down the connection with ftp-server, and recover the FTPClient its default parameters
     *
     * @throws IOException
     */
    private void connectAndLogin() throws IOException{
        client.connect(ftpHost, serverPort);
        client.login(userName, psw);

        int reply = client.getReplyCode();
        if(!FTPReply.isPositiveCompletion(reply)){
            client.abort();
            client.disconnect();
        }
    }

    /**
     * Log out and close ftp
     * abort: cut down the file transfer, return true if succeed, or false
     * disconnect: cut down the connection with ftp-server, and recover the FTPClient its default parameters
     *
     * @return the FTPClient instance
     * @throws Exception
     */
    private FTPClient closeFTP() throws Exception{
        try {
            if (client != null && client.isConnected()) {
                client.abort();
                client.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return client;
    }

    /**
     * Get the list of files
     *
     * @param client a FTPClient instance
     * @return A list of type FTPFile
     */
    private FTPFile[] getFileList(FTPClient client) throws IOException{
        FTPFile[] files = client.listFiles();
        return files;
    }

    /**
     * Create a fixed size file to store resource file.
     */
    private void createFile() {
        File file = new File(getDirectory() + name());  //type
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
                randomAccessFile.setLength(totalLength);
            } catch (IOException e) {
                changeStatus(Status.ERROR, e);
            }
        }
        this.files.add(new FileData(file, (int) getBlockSize()));
    }



}
