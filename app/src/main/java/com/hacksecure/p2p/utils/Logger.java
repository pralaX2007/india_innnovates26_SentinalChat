package com.hacksecure.p2p.utils;

import android.util.Log;

public class Logger {
    private static final String TAG = "HackSecure";

    public static void d(String message) {
        Log.d(TAG, message);
    }

    public static void e(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }

    public static void e(String message) {
        Log.e(TAG, message);
    }

    public static void i(String message) {
        Log.i(TAG, message);
    }
}
