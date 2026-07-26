package com.example.skywardblocker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.skywardblocker.blocking.CategoryManager;

/**
 * Listens for app install/uninstall events and updates the category cache.
 */
public class PackageChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "SkywardDebug";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;

        String packageName = intent.getData().getSchemeSpecificPart();
        if (packageName == null) return;

        String action = intent.getAction();

        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            Log.d(TAG, "App installed: " + packageName);
            CategoryManager.resolveAndCache(context, packageName);

        } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
            Log.d(TAG, "App uninstalled: " + packageName);
            CategoryManager.removePackage(context, packageName);
        }
    }
}
