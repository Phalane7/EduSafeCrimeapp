package com.example.edusafe;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Net {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // Run work off the main thread (needed for all network calls)
    public static void bg(Runnable r) {
        POOL.execute(r);
    }

    // Run work back on the main thread (needed to update the screen)
    public static void main(Runnable r) {
        MAIN.post(r);
    }
}