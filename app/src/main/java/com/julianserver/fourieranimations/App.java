package com.julianserver.fourieranimations;

import android.app.Application;
import android.util.Log;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Log.i("FourierApp", "Fourier Animations app started");

        // You can add any global app initialization here if needed
        // For example: crash reporting, analytics, global configurations
    }
}