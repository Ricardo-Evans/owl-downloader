package com.owl.downloader.util;

public final class Util {
    private Util() {
    }

    /**
     * Calculate speed, in bytes/second
     *
     * @param delta the delta data size, in bytes
     * @param time  the time spend, in milliseconds
     * @param speed the speed calculated last time, in bytes/second
     * @return the current speed, in bytes/second
     */
    public static long calculateSpeed(long delta, long time, long speed) {
        long origin_speed = (long) (1000 * delta / (time + 0.001));
        double ratio = 0.5;
        return (long) (ratio * origin_speed + (1 - ratio) * speed);
    }
}
