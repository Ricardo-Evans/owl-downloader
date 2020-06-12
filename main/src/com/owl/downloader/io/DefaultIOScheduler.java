package com.owl.downloader.io;

import com.owl.downloader.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DefaultIOScheduler implements IOScheduler, Runnable {
    private static DefaultIOScheduler scheduler = null;
    private Selector selector = null;
    private Thread daemon = null;
    private ExecutorService executor = null;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean running = false;

    @Override
    public void run() {
        running = true;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int count = selector.select();
                lock.writeLock().lock();
                try {
                    if (count > 0) {
                        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            if (!key.isValid()) continue;
                            Attachment attachment = (Attachment) key.attachment();
                            if (key.isReadable())
                                executor.execute(() -> doRead((ReadableByteChannel) key.channel(), attachment.buffer, attachment.callback));
                            if (key.isWritable())
                                executor.execute(() -> doWrite((WritableByteChannel) key.channel(), attachment.buffer, attachment.callback));
                            iterator.remove();
                            key.cancel();
                        }
                    }
                } finally {
                    lock.writeLock().unlock();
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

    @Override
    public void read(ReadableByteChannel channel, ByteBuffer buffer, IOCallback callback) {
        if (!running) throw new IllegalStateException();
        if (channel instanceof SelectableChannel) {
            try {
                lock.readLock().lock();
                try {
                    selector.wakeup();
                    selector.selectNow();
                    ((SelectableChannel) channel).register(selector, SelectionKey.OP_READ, new Attachment(buffer, callback));
                } finally {
                    lock.readLock().unlock();
                }
            } catch (IOException e) {
                callback.callback(channel, buffer, 0, e);
            }
        } else executor.execute(() -> doRead(channel, buffer, callback));
    }

    @Override
    public void write(WritableByteChannel channel, ByteBuffer buffer, IOCallback callback) {
        if (!running) throw new IllegalStateException();
        if (channel instanceof SelectableChannel) {
            try {
                lock.readLock().lock();
                try {
                    selector.wakeup();
                    selector.selectNow();
                    ((SelectableChannel) channel).register(selector, SelectionKey.OP_WRITE, new Attachment(buffer, callback));
                } finally {
                    lock.readLock().unlock();
                }
            } catch (IOException e) {
                callback.callback(channel, buffer, 0, e);
            }
        } else executor.execute(() -> doWrite(channel, buffer, callback));
    }

    private static void doRead(ReadableByteChannel channel, ByteBuffer buffer, IOCallback callback) {
        int size = 0;
        Exception exception = null;
        synchronized (buffer) {
            try {
                size = channel.read(buffer);
                Logger.getInstance().info("do read " + size);
            } catch (IOException e) {
                exception = e;
            } finally {
                callback.callback(channel, buffer, size, exception);
            }
        }
    }

    private static void doWrite(WritableByteChannel channel, ByteBuffer buffer, IOCallback callback) {
        int size = 0;
        Exception exception = null;
        synchronized (buffer) {
            try {
                size = channel.write(buffer);
                Logger.getInstance().info("do write " + size);
            } catch (IOException e) {
                exception = e;
            } finally {
                callback.callback(channel, buffer, size, exception);
            }
        }
    }
}
