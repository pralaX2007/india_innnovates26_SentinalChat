package com.hacksecure.p2p.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

public class Logger {
    private static final String TAG = "HackSecure";
    private static boolean sIsDebug = true; // default true until init

    /**
     * Call once from Application.onCreate() to resolve debug flag at runtime.
     * Avoids dependency on generated BuildConfig class.
     */
    public static void init(Context context) {
        sIsDebug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public static void d(String message) {
        if (sIsDebug) {
            Log.d(TAG, message);
        }
    }

    public static void e(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }

    public static void e(String message) {
        Log.e(TAG, message);
    }

    public static void i(String message) {
        if (sIsDebug) {
            Log.i(TAG, message);
        }
    }
}

