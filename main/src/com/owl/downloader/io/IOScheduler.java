package com.owl.downloader.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * Singleton class, used to manage io
 * The implementation use selector to wait for the selectable io, use a thread pool to execute the actual io
 *
 * @author Ricardo Evans
 * @version 1.0
 */
public interface IOScheduler {

    /**
     * Get the unique IOScheduler
     *
     * @return the unique IOScheduler
     */
    static IOScheduler getInstance() {
        // TODO: implement it
        return null;
    }

    /**
     * Read data from the given channel and put to the given buffer, call the callback once io finish (at least one byte read or io fail)
     * <p>Especially, if the given channel is a selectable channel, a selector is used to wait until the channel is ready</p>
     *
     * @param channel  the source channel
     * @param buffer   the destination data buffer
     * @param callback io callback
     */
    void read(ReadableByteChannel channel, ByteBuffer buffer, IOCallback callback) throws IOException;

    /**
     * Write data from the given buffer to the given channel, call the callback once io finish (at least one byte write or io fail)
     * <p>Especially, if the given channel is a selectable channel, a selector is used to wait until the channel is ready</p>
     *
     * @param channel  the destination channel
     * @param buffer   the source data buffer
     * @param callback io callback
     */
    void write(WritableByteChannel channel, ByteBuffer buffer, IOCallback callback) throws IOException;
}

/**
 *Used to transfer data from socket channel to file channel
 */
class ReadScheduler implements IOScheduler{
    private static ReadScheduler readscheduler = null;

    public static ReadScheduler getInstance(){
        if (readscheduler == null){
            readscheduler = new ReadScheduler();
        }
        return readscheduler;
    }

    /**
     *
     * @param channel   the sorce channel
     * @param buffer    the dst buffer
     * @throws IOException  mistakes may happen when write to the buffer
     */
    public void doRead(SocketChannel channel, ByteBuffer buffer) throws IOException {
        int len = channel.read(buffer);
        if(len == -1) channel.close();
    }

    /**
     * manage a selector, and read data from the given channel and put to the given buffer
     * @param channel  the source channel
     * @param buffer   the destination data buffer
     * @param callback io callback
     * @throws IOException haven't handle
     */
    @Override
    public void read(ReadableByteChannel channel, ByteBuffer buffer, IOCallback callback) throws IOException {
        SocketChannel readChannel = (SocketChannel) channel;
        Selector selector = Selector.open();

        readChannel.configureBlocking(false);
        SelectionKey selectionkey = readChannel.register(selector, SelectionKey.OP_READ);

        while(true){
            int ready = selector.select();
            if(ready == 0) break;

            Set selectableKeys = selector.selectedKeys();   //valid channel
            Iterator keyIterator = selectableKeys.iterator();   //iterator

            while(keyIterator.hasNext()){
                SelectionKey key = (SelectionKey) keyIterator.next();

                if(key.isReadable()){
                    doRead((SocketChannel) key.channel(), buffer);  //use doRead func to do it
                }
                keyIterator.remove();   //get rid of the processed key
            }

        }
        selector.close();
        readChannel.close();
    }

    /**
     * write from buffer to file channel
     * @param channel  the destination channel
     * @param buffer   the source data buffer
     * @param callback io callback
     * @throws IOException haven't handle
     */
    @Override
    public void write(WritableByteChannel channel, ByteBuffer buffer, IOCallback callback) throws IOException {
        FileChannel writeChannel = (FileChannel) channel;
        buffer.flip();
        writeChannel.read(buffer);
    }
}




