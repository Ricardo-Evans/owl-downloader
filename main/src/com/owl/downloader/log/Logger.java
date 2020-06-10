package com.owl.downloader.log;

import java.util.Objects;

/**
 * Logger used for log system.
 *
 * <p>
 * Logger is a singleton, use <code>Logger.getInstance()</code> to get the instance, and <code>Logger.serInstance(Logger)</code> to set the instance.
 * The instance implementation may differ across platforms in order to make convenience.
 * </p>
 *
 * @author Ricardo Evans
 * @version 1.0
 * @see Logger#getInstance()
 * @see Logger#setInstance(Logger)
 */
public abstract class Logger {
    private static Logger instance = null;

    /**
     * Get the logger instance, this method is thread safe.
     *
     * @return the logger instance
     */
    public static Logger getInstance() {
        if (instance == null) {
            synchronized (Logger.class) {
                if (instance == null) instance = new DefaultLogger();
            }
        }
        return instance;
    }

    /**
     * Set the logger instance, this method is thread safe.
     *
     * @param logger the logger instance
     * @throws NullPointerException if logger is null
     */
    public static void setInstance(Logger logger) {
        Objects.requireNonNull(logger);
        synchronized (Logger.class) {
            Logger.instance = logger;
        }
    }

    /**
     * Core method used for logging, implementation depends on platform
     *
     * @param level     the log level
     * @param message   the attached message
     * @param exception the exception if exist
     */
    public abstract void log(Level level, String message, Exception exception);

    /**
     * Same as <code>log(level, message, null)</code>
     *
     * @param level   the log level
     * @param message the attached message
     * @see Logger#log(Level, String, Exception)
     */
    public void log(Level level, String message) {
        log(level, message, null);
    }

    /**
     * Same as <code>verbose(message, null)</code>
     *
     * @param message the attached message
     * @see Logger#debug(String, Exception)
     */
    public void verbose(String message) {
        verbose(message, null);
    }

    /**
     * Same as <code>log(VERBOSE, message, exception)</code>
     *
     * @param message   the attached message
     * @param exception the exception if exist
     * @see Logger#log(Level, String, Exception)
     */
    public void verbose(String message, Exception exception) {
        log(Level.VERBOSE, message, exception);
    }


    /**
     * Same as <code>debug(message, null)</code>
     *
     * @param message the attached message
     * @see Logger#debug(String, Exception)
     */
    public void debug(String message) {
        debug(message, null);
    }

    /**
     * Same as <code>log(DEBUG, message, exception)</code>
     *
     * @param message   the attached message
     * @param exception the exception if exist
     * @see Logger#log(Level, String, Exception)
     */
    public void debug(String message, Exception exception) {
        log(Level.DEBUG, message, exception);
    }

    /**
     * Same as <code>info(message, null)</code>
     *
     * @param message the attached message
     * @see Logger#info(String, Exception)
     */
    public void info(String message) {
        info(message, null);
    }

    /**
     * Same as <code>log(INFO, message, exception)</code>
     *
     * @param message   the attached message
     * @param exception the exception if exist
     * @see Logger#log(Level, String, Exception)
     */
    public void info(String message, Exception exception) {
        log(Level.INFO, message, exception);
    }

    /**
     * Same as <code>warning(message, null)</code>
     *
     * @param message the attached message
     * @see Logger#warning(String, Exception)
     */
    public void warning(String message) {
        warning(message, null);
    }

    /**
     * Same as <code>log(WARNING, message, exception)</code>
     *
     * @param message   the attached message
     * @param exception the exception if exist
     * @see Logger#log(Level, String, Exception)
     */
    public void warning(String message, Exception exception) {
        log(Level.WARNING, message, exception);
    }

    /**
     * Same as <code>error(message, null)</code>
     *
     * @param message the attached message
     * @see Logger#error(String, Exception)
     */
    public void error(String message) {
        error(message, null);
    }

    /**
     * Same as <code>log(ERROR, message, exception)</code>
     *
     * @param message   the attached message
     * @param exception the exception if exist
     * @see Logger#log(Level, String, Exception)
     */
    public void error(String message, Exception exception) {
        log(Level.ERROR, message, exception);
    }
}
