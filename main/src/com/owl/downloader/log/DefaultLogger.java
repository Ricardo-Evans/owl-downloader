package com.owl.downloader.log;

public class DefaultLogger extends Logger {
    @Override
    public void log(Level level, String message, Exception exception) {
        if (exception == null)
            System.out.println(level + message);
        else {
            System.out.println(level + message + exception);
            exception.printStackTrace();
        }
    }
}
