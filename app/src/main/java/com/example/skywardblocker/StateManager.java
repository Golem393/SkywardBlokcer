package com.example.skywardblocker;

import android.content.Context;
import android.content.SharedPreferences;

public class StateManager {
    private static final String PREFS_NAME = "skyward_prefs";
    private static final String KEY_SETUP_COMPLETE = "setup_complete";
    private static final String KEY_LAUNCHER_ATTEMPTED = "launcher_attempted";

    public static boolean isSetupComplete(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SETUP_COMPLETE, false);
    }

    public static void setSetupComplete(Context context, boolean complete) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SETUP_COMPLETE, complete).apply();
    }

    // 1. Add these two methods to StateManager.java
    public static boolean isLauncherAttempted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LAUNCHER_ATTEMPTED, false);
    }

    public static void setLauncherAttempted(Context context, boolean attempted) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LAUNCHER_ATTEMPTED, attempted).apply();
    }
}