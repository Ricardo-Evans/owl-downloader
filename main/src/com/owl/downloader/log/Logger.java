package com.owl.downloader.log;

import java.util.Objects;

public abstract class Logger {
    private static Logger instance = null;

    public static Logger getInstance() {
        if (instance == null) {
            synchronized (Logger.class) {
                if (instance == null) instance = new DefaultLogger();
            }
        }
        return instance;
    }

    public static void setInstance(Logger logger) {
        Objects.requireNonNull(logger);
        synchronized (Logger.class) {
            Logger.instance = logger;
        }
    }

    public abstract void log(Level level, String message, Exception exception);

    public void log(Level level, String message) {
        log(level, message, null);
    }

    public void debug(String message) {
        debug(message, null);
    }

    public void debug(String message, Exception exception) {
        log(Level.DEBUG, message, exception);
    }

    public void info(String message) {
        info(message, null);
    }

    public void info(String message, Exception exception) {
        log(Level.INFO, message, exception);
    }

    public void warning(String message) {
        warning(message, null);
    }

    public void warning(String message, Exception exception) {
        log(Level.WARNING, message, exception);
    }

    public void error(String message) {
        debug(message, null);
    }

    public void error(String message, Exception exception) {
        log(Level.ERROR, message, exception);
    }
}
