package com.tanimsarker.lossless;

/** Thin JNI wrapper over the shared C++ core (core/ll_core.cpp). Same class on Android and desktop. */
public final class Engine {
    static { System.loadLibrary("lossless_jni"); }
    private Engine() {}
    public static native int open(String path);   // 0 = ok
    public static native void close();
    public static native void play();
    public static native void pause();
    public static native void seek(double seconds);
    public static native double position();
    public static native double duration();
    public static native int playing();
    public static native int finished();
    public static native void eq(boolean enabled);          // false = bypass
    public static native void band(int index, float db);    // 10 bands, -12..+12
    public static native void preamp(float db);
    public static native int sourceRate();
    public static native int sourceBits();
    public static native int outputRate();

    public static final String[] BAND_LABELS = {"31","62","125","250","500","1k","2k","4k","8k","16k"};

    public static String khz(int hz) {
        String s = String.format(java.util.Locale.US, "%.1f", hz / 1000.0);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Source quality + stream rate. Never claims bit-perfect output. */
    public static String describe() {
        int r = sourceRate(), b = sourceBits(), o = outputRate();
        if (r == 0) return " ";
        String s = b > 0 ? "FLAC " + khz(r) + " kHz / " + b + "-bit " + ((r > 44100 || b > 16) ? "Hi-Res" : "CD quality")
                         : "Source " + khz(r) + " kHz";
        return s + "  |  Output stream " + khz(o) + " kHz (OS/device may still resample)";
    }

    public static String fmt(double s) {
        int t = (int) s;
        return (t / 60) + ":" + String.format("%02d", t % 60);
    }
}
