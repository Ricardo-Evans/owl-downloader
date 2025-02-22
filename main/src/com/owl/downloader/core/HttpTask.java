package com.owl.downloader.core;

import com.owl.downloader.io.IOCallback;
import com.owl.downloader.io.IOScheduler;
import com.owl.downloader.util.MyX509TrustManager;
import com.owl.downloader.util.SSLEngineUtil;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
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
import java.security.NoSuchProviderException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

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
    private long currentConnections = 0;
    private long downloadSpeed = 0;
    private long downloadedLength = 0;
    private long totalLength = 0;
    private long currentTime;
    private final List<FileData> files = new LinkedList<>();

    public HttpTask(URI uri) {
        super(new File(uri.getPath()).getName());
        this.uri = uri;
        this.protocol = uri.getScheme();
        this.proxy = getProxySelector().select(uri).get(0);
    }


    /**
     * Adjust downloaded length,always occurs during callback.
     */
    private void adjustDownloadedLength(int size) {
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
        if (protocol.equals("http")) {
            try {
                setHttpFileAttributes();
            } catch (IOException e) {
                changeStatus(Status.ERROR, e);
                return;
            }
        } else {
            try {
                setHttpsFileAttributes();
            } catch (Exception e) {
                changeStatus(Status.ERROR, e);
                return;
            }
        }

        createFile();
        FileData.BlockSelector blockSelector = FileData.BlockSelector.getDefault();
        List<FileData.Block> availableBlocks = files.get(0).getBlocks();  //need to change.


        FileData.Block block;
        currentTime = System.currentTimeMillis();

        while ((block = Objects.requireNonNull(blockSelector).select(availableBlocks)) != null) {
            if (currentConnections < getMaximumConnections() && status() == Status.ACTIVE) {
                ++currentConnections;
                block.available = false;
                if (protocol.equals("http")) {
                    createHttpConnection(block);
                } else {
                    createHttpsConnection(block);
                }
            }
        }

        changeStatus(Status.COMPLETED);
    }

    /**
     * Set Http source file's length and type.
     */
    private void setHttpFileAttributes() throws IOException {
        HttpURLConnection httpConnection = null;
        httpConnection = (HttpURLConnection) this.uri.toURL().openConnection();
        httpConnection.connect();
        totalLength = httpConnection.getContentLength();
        type = httpConnection.getContentType();
    }

    private void setHttpsFileAttributes() throws NoSuchProviderException, NoSuchAlgorithmException, KeyManagementException, IOException {
        SSLContext sslcontext = SSLContext.getInstance("SSL", "SunJSSE");
        sslcontext.init(null, new TrustManager[]{new MyX509TrustManager()}, new java.security.SecureRandom());
        HostnameVerifier ignoreHostnameVerifier = (s, sslsession) -> true;
        HttpsURLConnection.setDefaultHostnameVerifier(ignoreHostnameVerifier);
        HttpsURLConnection.setDefaultSSLSocketFactory(sslcontext.getSocketFactory());
        HttpsURLConnection httpsConnection = null;
        try {
            httpsConnection = (HttpsURLConnection) this.uri.toURL().openConnection(proxy);
        } catch (IOException e) {
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
        try (SocketChannel socketChannel = SocketChannel.open()) {
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
                    "Connection: close\r\n" +
                    "Range: bytes=" + block.offset + "-" + (block.length + block.offset - 1) + "\r\n" +
                    "\r\n";
            ByteBuffer requestBuffer = ByteBuffer.wrap(requestMessage.getBytes());
            while (!socketChannel.finishConnect()) {
            }
            socketChannel.write(requestBuffer);
            skipHttpHeader(socketChannel);

            FileChannel fileChannel = (new FileInputStream(getDirectory() + name())).getChannel().position(block.offset);
            ByteBuffer responseBuffer = ByteBuffer.allocate(16 * 1024);

            httpRead(socketChannel, fileChannel, responseBuffer);

        } catch (IOException e) {
            block.available = true;
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
            while (!socketChannel.finishConnect()) {
            }
            SSLSession session = sslEngine.getSession();
            ByteBuffer myAppBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());
            ByteBuffer myNetBuffer = ByteBuffer.allocate(session.getPacketBufferSize());
            ByteBuffer peerAppBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());
            ByteBuffer peerNetBuffer = ByteBuffer.allocate(session.getPacketBufferSize());

            SSLEngineUtil.doHandshake(socketChannel, sslEngine, myNetBuffer, peerNetBuffer);

            SSLEngineUtil.sendRequest(host, port, path, sslEngine, myAppBuffer, myNetBuffer, socketChannel, block);

            FileChannel fileChannel = (new FileInputStream(getDirectory() + name())).getChannel().position(block.offset);

            httpsRead(socketChannel, fileChannel, peerNetBuffer, peerAppBuffer, sslEngine);
        } catch (Exception e) {
            block.available = true;
        }
    }


    private void httpsRead(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer netBuffer, ByteBuffer appBuffer, SSLEngine sslEngine) {
        IOCallback httpsReadCallback = (Channel socketchannel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            if (size != -1) {
                responseBuffer.flip();
                SSLEngineResult res = null;
                try {
                    res = sslEngine.unwrap(responseBuffer, appBuffer);
                } catch (SSLException e) {
                }
                if (res.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    responseBuffer.compact();
                    httpsRead((ReadableByteChannel) socketchannel, writeChannel, responseBuffer, appBuffer, sslEngine);
                }
                if (res.getStatus() == SSLEngineResult.Status.OK) {
                    responseBuffer.compact();
                    skipHttpsHeader(appBuffer);
                    httpsWrite((ReadableByteChannel) socketchannel, writeChannel, appBuffer, appBuffer, sslEngine);
                }
            } else {
                --currentConnections;
            }
        };
        Objects.requireNonNull(ioScheduler).read(readChannel, netBuffer, httpsReadCallback);

    }

    private void httpsWrite(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer netBuffer, ByteBuffer appBuffer, SSLEngine sslEngine) {
        IOCallback httpsWriteCallback = (Channel fileChannel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            long lastTime = currentTime;
            currentTime = System.currentTimeMillis();
            downloadSpeed = size / (currentTime - lastTime) * 1000;//B/s
            System.out.println(size);
            synchronized (this) {
                adjustDownloadedLength(size);
            }
            httpsRead(readChannel, (WritableByteChannel) fileChannel, netBuffer, appBuffer, sslEngine);
        };
        Objects.requireNonNull(ioScheduler).write(writeChannel, appBuffer, httpsWriteCallback);
    }


    private void httpRead(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer buffer) {

        IOCallback httpReadCallback = (Channel socketchannel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            long lastTime = currentTime;
            currentTime = System.currentTimeMillis();
            downloadSpeed = size / (currentTime - lastTime) * 1000;//B/s
            System.out.println(size);
            synchronized (this) {
                adjustDownloadedLength(size);
            }
            if (size != -1) {
                httpWrite((ReadableByteChannel) socketchannel, writeChannel, responseBuffer);
            } else {
                --currentConnections;
            }
        };
        Objects.requireNonNull(ioScheduler).read(readChannel, buffer, httpReadCallback);

    }

    private void httpWrite(ReadableByteChannel readChannel, WritableByteChannel writeChannel, ByteBuffer buffer) {
        IOCallback httpWriteCallback = (Channel fileChannel, ByteBuffer responseBuffer, int size, Exception exception) -> {
            httpRead(readChannel, (WritableByteChannel) fileChannel, responseBuffer);
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
            count++;
            appBuffer.position(count);
        } else {
            appBuffer.position(0);
        }
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



