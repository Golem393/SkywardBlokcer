package com.example.skywardblocker.appblock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class CategoryManager {

    private static final String TAG = "SkywardDebug";
    private static Set<String> customBlocklist = null;

    private static void loadCustomBlocklist(Context context) {
        if (customBlocklist != null) return;
        customBlocklist = new HashSet<>();
        try {
            InputStream is = context.getAssets().open("custom_blocklist.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    String[] parts = line.split(",");
                    customBlocklist.add(parts[0].trim());
                }
            }
            reader.close();
            Log.d(TAG, "Loaded custom blocklist: " + customBlocklist);
        } catch (Exception e) {
            Log.e(TAG, "Error loading custom blocklist", e);
        }
    }

    public static boolean isAppInBlockedCategory(Context context, String packageName) {
        loadCustomBlocklist(context);
        if (customBlocklist.contains(packageName)) {
            Log.d(TAG, "Status: BLOCKED by custom blocklist (" + packageName + ")");
            return true;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "OS version too low to check categories.");
            return false;
        }

        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);

            Log.d(TAG, "Opened: " + packageName + " | Category ID: " + info.category);

            switch (info.category) {
                case ApplicationInfo.CATEGORY_GAME:   // ID 0
                case ApplicationInfo.CATEGORY_SOCIAL: // ID 4
                case ApplicationInfo.CATEGORY_VIDEO:  // ID 2 (YouTube usually falls here)
                    Log.d(TAG, "Status: BLOCKED");
                    return true;
                default:
                    Log.d(TAG, "Status: ALLOWED");
                    return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            // This is where YouTube was failing silently.
            Log.e(TAG, "Package INVISIBLE or missing: " + packageName, e);
            return false;
        }
    }
}