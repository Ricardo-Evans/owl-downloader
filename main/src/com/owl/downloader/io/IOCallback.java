package com.owl.downloader.io;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;

/**
 * Callback used when io finish, usually called in io thread, block operations is not suggested
 *
 * @author Ricardo Evans
 * @version 1.0
 */
@FunctionalInterface
public interface IOCallback {
    /**
     * Called by IOScheduler when io finish
     *
     * @param channel   the io channel
     * @param buffer    the data buffer
     * @param exception any exception during the io if exist
     */
    void callback(Channel channel, ByteBuffer buffer, Exception exception);
}

