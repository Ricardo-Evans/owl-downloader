package com.owl.downloader.io;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

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
        return DefaultIOScheduler.getInstance();
    }

    /**
     * Start the IO scheduler so that it can accept io tasks
     */
    void start() throws IOException;

    /**
     * Stop the IO scheduler, release all related resources
     */
    void stop() throws IOException;

    /**
     * Read data from the given channel and put to the given buffer, call the callback once io finish (at least one byte read or io fail)
     * <p>Especially, if the given channel is a selectable channel, a selector is used to wait until the channel is ready</p>
     *
     * @param channel  the source channel
     * @param buffer   the destination data buffer
     * @param callback io callback
     */
    void read(ReadableByteChannel channel, ByteBuffer buffer, IOCallback callback);

    /**
     * Write data from the given buffer to the given channel, call the callback once io finish (at least one byte write or io fail)
     * <p>Especially, if the given channel is a selectable channel, a selector is used to wait until the channel is ready</p>
     *
     * @param channel  the destination channel
     * @param buffer   the source data buffer
     * @param callback io callback
     */
    void write(WritableByteChannel channel, ByteBuffer buffer, IOCallback callback);
}




