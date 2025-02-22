package com.owl.downloader.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Default IOScheduler implements the IOScheduler interface
 *
 * @version 1.0
 */
public class DefaultIOScheduler implements IOScheduler, Runnable {
    private static DefaultIOScheduler scheduler = null;
    private Selector selector = null;
    private Thread daemon = null;
    private ExecutorService executor = null;
    private volatile boolean running = false;

    @Override
    public void run() {
        running = true;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int count = selector.select();
                if (count > 0) {
                    for (SelectionKey key : selector.selectedKeys()) {
                        Attachment attachment = (Attachment) key.attachment();
                        if (key.isReadable())
                            executor.execute(() -> doRead((ReadableByteChannel) key.channel(), attachment.buffer, attachment.callback));
                        if (key.isWritable())
                            executor.execute(() -> doWrite((WritableByteChannel) key.channel(), attachment.buffer, attachment.callback));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        running = false;
    }

    private static class Attachment {
        private final ByteBuffer buffer;
        private final IOCallback callback;

        public Attachment(ByteBuffer buffer, IOCallback callback) {
            this.buffer = buffer;
            this.callback = callback;
        }
    }

    public static DefaultIOScheduler getInstance() {
        if (scheduler == null) {
            synchronized (DefaultIOScheduler.class) {
                if (scheduler == null)
                    scheduler = new DefaultIOScheduler();
            }
        }
        return scheduler;
    }

    @Override
    public synchronized void start() throws IOException {
        if (selector == null) selector = Selector.open();
        if (executor == null) executor = Executors.newWorkStealingPool();
        if (daemon == null) daemon = new Thread(this);
        daemon.setDaemon(true);
        daemon.start();
    }

    @Override
    public synchronized void stop() throws IOException {
        if (daemon != null) daemon.interrupt();
        daemon = null;
        if (executor != null) executor.shutdown();
        executor = null;
        if (selector != null) selector.close();
        selector = null;
    }

    /**
     * Read data from the given channel and put to the given buffer, call the callback once io finish (at least one byte read or io fail)
     * <p>Especially, if the given channel is a selectable channel, a selector is used to wait until the channel is ready</p>
     * If the given channel is a selectable channel, it should be previously configured to non-blocking by the specific Task.
     *
     * @param channel  the source channel
     * @param buffer   the destination data buffer
     * @param callback io callback
     */
    @Override
    public void read(ReadableByteChannel channel, ByteBuffer buffer, IOCallback callback) {
        if (!running) throw new IllegalStateException();
        if (channel instanceof SelectableChannel) {
            try {
                ((SelectableChannel) channel).register(selector, SelectionKey.OP_READ, new Attachment(buffer, callback));
            } catch (ClosedChannelException e) {
                callback.callback(channel, buffer, 0, e);
            }
        } else executor.execute(() -> doRead(channel, buffer, callback));
    }

    /**
     * Write data from the given buffer to the given channel, call the callback once io finish (at least one byte write or io fail)
     * <p>Especially, if the given channel is a selectable channel, a selector is used to wait until the channel is ready</p>
     * If the given channel is a selectable channel, it should be previously configured to non-blocking by the specific Task.
     *
     * @param channel  the destination channel
     * @param buffer   the source data buffer
     * @param callback io callback
     */
    @Override
    public void write(WritableByteChannel channel, ByteBuffer buffer, IOCallback callback) {
        if (!running) throw new IllegalStateException();
        if (channel instanceof SelectableChannel) {
            try {
                ((SelectableChannel) channel).register(selector, SelectionKey.OP_WRITE, new Attachment(buffer, callback));
            } catch (ClosedChannelException e) {
                callback.callback(channel, buffer, 0, e);
            }
        } else executor.execute(() -> doWrite(channel, buffer, callback));
    }

    private void doRead(ReadableByteChannel channel, ByteBuffer buffer, IOCallback callback) {
        int size = 0;
        Exception exception = null;
        try {
            size = channel.read(buffer);
        } catch (IOException e) {
            exception = e;
        } finally {
            callback.callback(channel, buffer, size, exception);
        }
    }

    private void doWrite(WritableByteChannel channel, ByteBuffer buffer, IOCallback callback) {
        int size = 0;
        Exception exception = null;
        try {
            size = channel.write(buffer);
        } catch (IOException e) {
            exception = e;
        } finally {
            callback.callback(channel, buffer, size, exception);
        }
    }
}
