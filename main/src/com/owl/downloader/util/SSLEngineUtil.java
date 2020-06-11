package com.owl.downloader.util;

import com.owl.downloader.core.FileData;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


public class SSLEngineUtil {
    public static SSLEngine prepareEngine(String host, int port) throws Exception {
        SSLEngine sslEngine = SSLContext.getDefault().createSSLEngine(host, port);
        sslEngine.setUseClientMode(true);

        return sslEngine;
    }

    public static SocketChannel prepareChannel(String host, int port) throws Exception {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(host, port));

        return socketChannel;
    }

    public static void doHandshake(SocketChannel socketChannel, SSLEngine engine,
                                   ByteBuffer myNetData, ByteBuffer peerNetData) throws Exception {

        // Create byte buffers to use for holding application data
        int appBufferSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);

        // Begin handshake
        engine.beginHandshake();
        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();

        // Process handshaking message
        while (hs != SSLEngineResult.HandshakeStatus.FINISHED &&
                hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

            switch (hs) {

                case NEED_UNWRAP:
                    // Receive handshaking data from peer
                    socketChannel.read(peerNetData);// The channel has reached end-of-stream

                    peerNetData.flip();
                    SSLEngineResult res = engine.unwrap(peerNetData, peerAppData);
//                    MySSlEngine.print(peerAppData);
                    peerNetData.compact();
                    hs = res.getHandshakeStatus();

                    // Check status
                    // Handle OK status
                    // Handle other status: BUFFER_UNDERFLOW, BUFFER_OVERFLOW, CLOSED
                    break;

                case NEED_WRAP:
                    // Empty the local network packet buffer.
                    myNetData.clear();

                    // Generate handshaking data
                    res = engine.wrap(myAppData, myNetData);
//                    MySSlEngine.print(myNetData);
                    hs = res.getHandshakeStatus();

                    // Check status
                    if (res.getStatus() == SSLEngineResult.Status.OK) {
                        myNetData.flip();

                        // Send the handshaking data to peer
                        while (myNetData.hasRemaining()) {
                            socketChannel.write(myNetData);
                        }

                        // Handle other status:  BUFFER_OVERFLOW, BUFFER_UNDERFLOW, CLOSED
                    }
                    break;

                case NEED_TASK:
                    // Handle blocking tasks
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        new Thread(task).start();
                    }
                    hs = engine.getHandshakeStatus();
                    break;

                // Handle other status:  // FINISHED or NOT_HANDSHAKING
            }
        }

        // Processes after handshaking
//        System.out.println("after handshaking");
    }

    private static void runDelegatedTasks(SSLEngineResult result, SSLEngine engine) {

        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                runnable.run();
            }
            SSLEngineResult.HandshakeStatus hsStatus = engine.getHandshakeStatus();

        }
    }

    public static void sendRequest(String host, int port, String path,
                                   SSLEngine sslEngine, ByteBuffer myAppData, ByteBuffer myNetData, SocketChannel socketChannel, FileData.Block block)
            throws Exception {
        String header = ("GET " + path + " HTTP/1.1\r\n") +
                "Host: " + host + "\r\n" +
                "Connection: close\r\n" +
                "Range: bytes=" + block.offset + "-" + (block.length + block.offset - 1) + "\r\n" +
                "\r\n";
        myAppData.put(header.getBytes());
        myAppData.flip();

        myNetData.clear();
        SSLEngineResult res = sslEngine.wrap(myAppData, myNetData);
        if (res.getStatus() == SSLEngineResult.Status.OK) {
            myNetData.flip();
            socketChannel.write(myNetData);
        }
    }

}
