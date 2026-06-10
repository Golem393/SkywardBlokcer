package com.example.skywardblocker.appblock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

public class CategoryManager {

    private static final String TAG = "SkywardDebug";

    public static boolean isAppInBlockedCategory(Context context, String packageName) {
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