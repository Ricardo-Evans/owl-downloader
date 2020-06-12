package com.owl.downloader.core;

import com.owl.downloader.io.IOCallback;
import com.owl.downloader.io.IOScheduler;
import com.owl.downloader.log.Level;
import com.owl.downloader.log.Logger;
import com.owl.downloader.util.MyX509TrustManager;
import com.owl.downloader.util.SSLEngineUtil;
import com.owl.downloader.util.Util;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Task that downloads http url
 *
 * @author Daqige
 * @version 1.0
 */
public class HttpTask extends BaseTask implements Task {
    private final URI uri;
    private final Proxy proxy;
    private String type;
    private final String protocol;
    private final IOScheduler ioScheduler = IOScheduler.getInstance();
    public AtomicInteger currentConnections;
    private long downloadSpeed = 0;
    private long lastDownloadedLength = 0;
    private long downloadedLength = 0;
    private long totalLength = 0;
    private long currentTime = 0;
    private final LongAdder downloadLengthAdder = new LongAdder();
    private final Logger logger = Logger.getInstance();
    private final List<FileData> files = new LinkedList<>();

    public HttpTask(URI uri) {
        super(new File(uri.getPath()).getName());
        this.uri = uri;
        this.protocol = uri.getScheme();
        this.proxy = getProxySelector().select(uri).get(0);
        currentConnections = new AtomicInteger(0);
    }

    @Override
    public long downloadSpeed() {
        long lastTime = currentTime;
        currentTime = System.currentTimeMillis();
        System.out.println(downloadedLength - lastDownloadedLength);
        System.out.println(currentTime - lastTime);
        downloadSpeed = Util.calculateSpeed(downloadedLength() - lastDownloadedLength, currentTime - lastTime, downloadSpeed);
        lastDownloadedLength = downloadedLength;
        return downloadSpeed;
    }

    @Override
    public long uploadSpeed() {
        return 0;
    }

    @Override
    public long downloadedLength() {
        return downloadedLength += downloadLengthAdder.sumThenReset();
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
        if (protocol.equals("http")) {
            try {
                setHttpFileAttributes();
            } catch (IOException e) {
                logger.log(Level.ERROR, "Failed to connect to get meta data");
                changeStatus(Status.ERROR, e);
                return;
            }
        } else {
            try {
                setHttpsFileAttributes();
            } catch (Exception e) {
                logger.log(Level.ERROR, "Failed to connect to get meta data");
                changeStatus(Status.ERROR, e);
                return;
            }
        }
        createFile();
        FileData.BlockSelector blockSelector = getBlockSelector();
        List<FileData.Block> availableBlocks = files.get(0).getBlocks();  //need to change.
        FileData.Block block;
        while (status() == Status.ACTIVE && !Thread.currentThread().isInterrupted()) {
            while (currentConnections.get() < getMaximumConnections()) {
                logger.debug("create connection, current: " + currentConnections.get());
                block = Objects.requireNonNull(blockSelector).select(availableBlocks);
                if (block == null) {
                    if (currentConnections.get() <= 0) {
                        changeStatus(Status.COMPLETED);
                        return;
                    } else break;
                }
                block.available = false;
                if (protocol.equals("http")) {
                    createHttpConnection(block);
                } else {
                    createHttpsConnection(block);
                }
                currentConnections.incrementAndGet();
            }
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Set Http source file's length and type.
     */
    private void setHttpFileAttributes() throws IOException {
        HttpURLConnection httpConnection;
        httpConnection = (HttpURLConnection) this.uri.toURL().openConnection();
        httpConnection.connect();
        totalLength = httpConnection.getContentLength();
        type = httpConnection.getContentType();
    }

    private void setHttpsFileAttributes() throws NoSuchAlgorithmException, KeyManagementException, IOException {
        SSLContext sslcontext = SSLContext.getInstance("SSL");
        sslcontext.init(null, new TrustManager[]{new MyX509TrustManager()}, new java.security.SecureRandom());
        HostnameVerifier ignoreHostnameVerifier = (s, sslSession) -> true;
        HttpsURLConnection.setDefaultHostnameVerifier(ignoreHostnameVerifier);
        HttpsURLConnection.setDefaultSSLSocketFactory(sslcontext.getSocketFactory());
        HttpsURLConnection httpsConnection;
        try {
            httpsConnection = (HttpsURLConnection) this.uri.toURL().openConnection(proxy);
        } catch (IOException e) {
            logger.log(Level.ERROR, "Failed to connect to get meta data");
            changeStatus(Status.ERROR, e);
            return;
        }
        httpsConnection.setInstanceFollowRedirects(false);
        httpsConnection.connect();
        totalLength = httpsConnection.getContentLength();
        type = httpsConnection.getContentType();
    }

    /**
     * Create connection for a block,send channels and buffers to IOScheduler.
     */
    private void createHttpConnection(FileData.Block block) {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);

            String host = uri.getHost();
            String path = uri.getPath();
            int port = uri.getPort();
            if (port == -1) {
                port = 80;
            }
            InetSocketAddress address = new InetSocketAddress(host, port);
            socketChannel.connect(address);


            String requestMessage = ("GET " + path + " HTTP/1.1\r\n") +
                    "Host:" + host + "\r\n" +
                    "Range: bytes=" + block.offset + "-" + (block.length + block.offset - 1) + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
            ByteBuffer requestBuffer = ByteBuffer.wrap(requestMessage.getBytes());
            boolean connected = socketChannel.finishConnect();
            while (!connected) {
                connected = socketChannel.finishConnect();
            }
            socketChannel.write(requestBuffer);
            skipHttpHeader(socketChannel);

            RandomAccessFile randomAccessFile = new RandomAccessFile(files.get(0).getFile(), "rw");
            FileChannel fileChannel = randomAccessFile.getChannel();

            fileChannel.position(block.offset);
            ByteBuffer responseBuffer = ByteBuffer.allocateDirect(64 * 1024);

            httpRead(socketChannel, fileChannel, responseBuffer);

        } catch (IOException e) {
            block.available = true;
            logger.error("exception during create http connection", e);
        }
    }

    private void createHttpsConnection(FileData.Block block) {
        try {
            String host = uri.getHost();
            String path = uri.getPath();
            int port = uri.getPort();
            if (port == -1) {
                port = 443;
            }
            SSLEngine sslEngine = SSLEngineUtil.prepareEngine(host, port);
            SocketChannel socketChannel = SSLEngineUtil.prepareChannel(host, port);
            boolean connected = socketChannel.finishConnect();
            while (!connected) {
                connected = socketChannel.finishConnect();
            }
            SSLSession session = sslEngine.getSession();
            ByteBuffer myAppBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());
            ByteBuffer myNetBuffer = ByteBuffer.allocate(session.getPacketBufferSize());
            ByteBuffer peerAppBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());
            ByteBuffer peerNetBuffer = ByteBuffer.allocate(session.getPacketBufferSize());

            SSLEngineUtil.doHandshake(socketChannel, sslEngine, myNetBuffer, peerNetBuffer);

            SSLEngineUtil.sendRequest(host, port, path, sslEngine, myAppBuffer, myNetBuffer, socketChannel, block);

            RandomAccessFile randomAccessFile = new RandomAccessFile(files.get(0).getFile(), "rw");
            FileChannel fileChannel = randomAccessFile.getChannel();
            fileChannel.position(block.offset);

            httpsRead(socketChannel, fileChannel, peerNetBuffer, peerAppBuffer, sslEngine);
        } catch (Exception e) {
            block.available = true;
            logger.error("exception during create https connection", e);
        }
    }


    private void httpsRead(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer netBuffer, ByteBuffer appBuffer, SSLEngine sslEngine) {
        IOCallback httpsReadCallback = (Channel socketchannel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            if (size != -1) {
                if (size > 0) {
                    netBuffer.flip();
                    SSLEngineResult res = null;
                    try {
                        res = sslEngine.unwrap(netBuffer, appBuffer);
                    } catch (SSLException e) {
                        changeStatus(Status.ERROR, e);
                    }
                    if (Objects.requireNonNull(res).getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        netBuffer.compact();
                        httpsRead(readChannel, writeChannel, netBuffer, appBuffer, sslEngine);
                    }
                    if (res.getStatus() == SSLEngineResult.Status.OK) {
                        netBuffer.compact();
                        appBuffer.flip();
                        skipHttpsHeader(appBuffer);
                        httpsWrite(readChannel, writeChannel, netBuffer, appBuffer, sslEngine);
                    }
                } else {
                    netBuffer.compact();
                    httpsRead(readChannel, writeChannel, netBuffer, appBuffer, sslEngine);
                }
            } else {
                try {
                    readChannel.close();
                    writeChannel.close();

                } catch (IOException e) {
                    logger.log(Level.ERROR, "fail to close channel", e);
                    changeStatus(Status.ERROR, e);
                }
                currentConnections.decrementAndGet();
                synchronized (this) {
                    notify();
                }
            }
        };
        Objects.requireNonNull(ioScheduler).read(readChannel, netBuffer, httpsReadCallback);

    }

    private void httpsWrite(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer netBuffer, ByteBuffer appBuffer, SSLEngine sslEngine) {
        IOCallback httpsWriteCallback = (Channel fileChannel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            downloadLengthAdder.add(size);
            while (appBuffer.hasRemaining()) {
                httpsWrite(readChannel, writeChannel, netBuffer, appBuffer, sslEngine);
            }
            appBuffer.clear();
            httpsRead(readChannel, writeChannel, netBuffer, appBuffer, sslEngine);
        };
        Objects.requireNonNull(ioScheduler).write(writeChannel, appBuffer, httpsWriteCallback);
    }


    private void httpRead(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer buffer) {
        IOCallback httpReadCallback = (Channel channel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            if (exception != null) {
                logger.log(Level.ERROR, "fail to read from socket", exception);
                changeStatus(Status.ERROR, exception);
                return;
            }
            if (size != -1) {

                buffer.flip();
                httpWrite(readChannel, writeChannel, buffer);

                downloadLengthAdder.add(size);
            } else {
//                System.out.println("block complete");
                try {
                    readChannel.close();
                    writeChannel.close();
                } catch (Exception e) {
                    logger.log(Level.ERROR, "fail to close channel", e);
                }
                currentConnections.decrementAndGet();
                synchronized (this) {
                    this.notify();
                }
            }
        };
        Objects.requireNonNull(ioScheduler).read(readChannel, buffer, httpReadCallback);
    }

    private void httpWrite(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer buffer) {
        IOCallback httpWriteCallback = (Channel Channel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            buffer.compact();
            httpRead(readChannel, writeChannel, buffer);
        };
        Objects.requireNonNull(ioScheduler).write(writeChannel, buffer, httpWriteCallback);
    }

    /**
     * Skip the header to download content.
     */
    private void skipHttpHeader(SocketChannel socketChannel) throws IOException {
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


    private void skipHttpsHeader(ByteBuffer appBuffer) {
        int count = 0;
        byte lastByte = 0;
        if ((char) appBuffer.get(0) == 'H' && (char) appBuffer.get(1) == 'T') {
            while (true) {
                if (lastByte == 10 && appBuffer.get(count) == 13) {
                    count += 2;
                    break;
                }
                lastByte = appBuffer.get(count);
                count++;
            }
            appBuffer.position(count);
        } else {
            appBuffer.position(0);
        }
    }

    /**
     * Create a fixed size file to store resource file.
     */
    private void createFile() {
        File file = new File(getDirectory(), name());  //type
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
        this.files.add(new FileData(file, getBlockSize()));
        logger.log(Level.INFO, "Create file successfully");
    }


}



