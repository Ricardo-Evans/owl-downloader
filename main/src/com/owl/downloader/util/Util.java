package com.owl.downloader.util;

public final class Util {
    private Util() {
    }

    /**
     * Calculate speed, in bytes/second
     *
     * @param delta the delta data size (byte)
     * @param time  the time spend (ms)
     * @param speed the speed calculated last time (byte/s)
     * @return the current speed (byte/s)
     */
    public static long calculateSpeed(long delta, long time, long speed) {
        //throw new RuntimeException("not implemented yet");
        long origin_speed = 1000*delta/(time+1);
        long ratio = 1 / (1+speed*origin_speed);
        return ratio * origin_speed + (1-ratio) * speed;
    }
}
