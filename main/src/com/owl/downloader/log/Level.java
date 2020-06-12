package com.owl.downloader.log;

/**
 * Indicate the level of log
 *
 * @author Ricardo Evans
 * @version 1.0
 */
public enum Level {
    /**
     * The level used for verbose message
     */
    VERBOSE,
    /**
     * The level used for debug message
     */
    DEBUG,
    /**
     * The level used for information record
     */
    INFO,
    /**
     * The level when something serious happen but can still work
     */
    WARNING,
    /**
     * The level when an error occur and cannot work
     */
    ERROR,
}
