package com.owl.downloader.core;

import java.io.Serializable;
import java.net.ProxySelector;
import java.util.List;

/**
 * Represent a download/upload task
 *
 * @author Ricardo Evans
 * @version 1.0
 */
public interface Task extends Runnable, Serializable {
    /**
     * Represent the status of task.
     */
    enum Status {
        ACTIVE,
        WAITING,
        PAUSED,
        COMPLETED,
        ERROR
    }

    /**
     * Get the current task status
     *
     * @return the current status
     */
    Status status();

    /**
     * Get the display name, usually the related file name
     *
     * @return the display name
     */
    String name();

    /**
     * Change the status to WAITING, the task will be executed once possible
     *
     * @throws IllegalStateException if the task is not in WAITING status
     */
    void start();

    /**
     * Change the status to PAUSED, can be restarted via start
     *
     * @throws IllegalStateException if the task is not in ACTIVE or WAITING status
     */
    void pause();

    /**
     * Prepare the task to be really run
     */
    void prepare();

    /**
     * Get the working directory
     *
     * @return the working directory
     */
    String getDirectory();

    /**
     * Set the working directory
     *
     * @param directory the working directory
     */
    void setDirectory(String directory);

    /**
     * Get the maximum connections count
     *
     * @return the maximum connections count
     */
    int getMaximumConnections();

    /**
     * Set the maximum connections count
     *
     * @param maximumConnections the maximum connections count
     */
    void setMaximumConnections(int maximumConnections);

    /**
     * Get the block size
     *
     * @return the block size, in bytes
     */
    int getBlockSize();

    /**
     * Set the block size
     *
     * @param blockSize the block size, in bytes
     */
    void setBlockSize(int blockSize);

    /**
     * Get the download speed, in bytes/second
     *
     * @return the download speed
     */
    long downloadSpeed();

    /**
     * Get the upload speed, in bytes/second
     *
     * @return the upload speed
     */
    long uploadSpeed();

    /**
     * Get the downloaded length, in bytes
     *
     * @return the downloaded length
     */
    long downloadedLength();

    /**
     * Get the uploaded length, in bytes
     *
     * @return the uploaded length
     */
    long uploadedLength();

    /**
     * Get the total length, in bytes
     *
     * @return the total length
     */
    long totalLength();

    /**
     * Get the related file data
     *
     * @return a readonly list of the related file data
     */
    List<FileData> files();

    /**
     * Get the block selector of this task
     *
     * @return the block selector
     */
    FileData.BlockSelector getBlockSelector();

    /**
     * Set the block selector of this task
     *
     * @param selector the selector
     */
    void setBlockSelector(FileData.BlockSelector selector);

    /**
     * Get the proxy selector
     *
     * @return the proxy selector
     */
    ProxySelector getProxySelector();

    /**
     * Set the proxy selector
     *
     * @param selector the proxy selector
     */
    void setProxySelector(ProxySelector selector);
}
