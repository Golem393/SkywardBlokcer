package com.example.skywardblocker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.skywardblocker.admin.DevicePolicyHelper;
import com.example.skywardblocker.blocking.AppMonitorService;
import com.example.skywardblocker.blocking.CategoryManager;

/**
 * Listens for system boot and package replacement events to automatically resume
 * background monitoring without requiring the user to ever open the app.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "SkywardDebug";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d(TAG, "BootReceiver received action: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            DevicePolicyHelper dph = new DevicePolicyHelper(context);
            if (dph.isDeviceOwner()) {
                Log.i(TAG, "Device Owner confirmed after " + action + " — resuming AppMonitorService and category scan!");
                AppMonitorService.start(context);
                CategoryManager.initializeCache(context);
            } else {
                Log.d(TAG, "BootReceiver: not Device Owner yet; skipping start.");
            }
        }
    }
}
