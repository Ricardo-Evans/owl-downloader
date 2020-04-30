package com.owl.downloader.exception;

/**
 * Exception thrown when the given protocol is not supported
 *
 * @author Ricardo Evans
 * @version 1.0
 */
public class UnsupportedProtocolException extends RuntimeException {
    public UnsupportedProtocolException() {
    }

    public UnsupportedProtocolException(String message) {
        super(message);
    }

    public UnsupportedProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedProtocolException(Throwable cause) {
        super(cause);
    }

    public UnsupportedProtocolException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
