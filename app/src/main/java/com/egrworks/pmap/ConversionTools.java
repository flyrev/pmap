package com.egrworks.pmap;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by egr on 8/28/2016.
 */
public class ConversionTools {

    private ConversionTools() {}

    private static double long2Double(long e) {
        long t = (2147483648L & e) != 0 ? -1 : 1;
        long n = (e >> 23 & 255L) - 127L;
        long i = 8388607L & e;
        double new_i;
        if (128 == n)
            return t * (i != 0 ? Double.NaN : Double.POSITIVE_INFINITY);
        if (-127 == n) {
            if (0 == i) return 0 * t;
            n = -126;
            new_i = ((double)i) / (1 << 22);
        } else {
            new_i = ((double)(i | 1 << 23)) / (1 << 23);
        }
        return t * new_i * Math.pow(2, n);
    }

    private static long bytes2Long(byte[] e, int t) {
        long var1 = ((e[t + 3] & 0xff) << 24);
        long var2 = ((e[t + 2] & 0xff) << 16);
        long var3 = ((e[t + 1] & 0xff) << 8);
        long var4 = e[t] & 0xff;
        return var1 + var2 + var3 + var4;
    }

    public static Pokemon decompress(byte[] stream, int offset) {
        double lat = long2Double(ConversionTools.bytes2Long(stream, offset));
        double lng = long2Double(ConversionTools.bytes2Long(stream, offset + 4));
        int id = stream[offset + 8] & 0xff;
        int spawnTime = stream[offset + 9] & 0xff;
        int n = ConversionTools.calculateSpawnDiff(spawnTime);
        while (n > 900) n -= 900;
        if ( n <= 0 && n >= -900) n+= 900;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, n);
        return new Pokemon(lat, lng, id, cal.getTimeInMillis());
    }

    /*public static long calculateSpawnTime(int e) {
        Date t = new Date();
        t.setMinutes(e);
        t.setSeconds(0);
        long spawnTime = t.getTime();
        long seconds = spawnTime - (new Date()).getTime() / 1000;

        if (seconds < -900) spawnTime += 3600000;
        if (seconds >= 2700) spawnTime -=3600000;
        return spawnTime;
    }*/

    public static String getTimerString(int e) {
        if ( e <= 0 && e >= -900) e+= 900;
        int n = e % 60;
        int t = (e - n)/60;
        return (t > 9 ? "" : "0") + t + ":" + (n > 9 ? "" : "0") + n;

        /*0 >= e && e >= -900 && (e = 900 + e);
        var t = Math.floor(Math.abs(e / 60)),
                n = Math.floor(Math.abs(e % 60));
        return 10 > t && (t = "0" + t), 10 > n && (n = "0" + n), t + ":" + n*/
    }

    private static int calculateSpawnDiff(int e) {
        Date t1 = new Date();
        t1.setMinutes(e);
        t1.setSeconds(0);
        Date t2 = new Date();
        long n = (t1.getTime() - t2.getTime()) / 1000L;
        //return -900 >= n && (n += 3600), n >= 2700 && (n -= 3600), n
        if (n <= -900) n +=3600;
        if (n >= 2700) n -=3600;
        return (int)n;
    }
}
