package com.example.skywardblocker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.skywardblocker.admin.DevicePolicyHelper;

/**
 * Listens for commands dispatched over ADB from the desktop companion app (or during development).
 *
 * Supported commands:
 *   adb shell am broadcast -a com.example.skywardblocker.CLEAR_OWNER
 */
public class AdbCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "SkywardDebug";

    public static final String ACTION_CLEAR_OWNER = "com.example.skywardblocker.CLEAR_OWNER";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "AdbCommandReceiver received broadcast action: " + action);

        if (ACTION_CLEAR_OWNER.equals(action)) {
            Log.d(TAG, "Executing command to clear Device Owner status...");
            DevicePolicyHelper helper = new DevicePolicyHelper(context);
            if (helper.isDeviceOwner()) {
                helper.clearDeviceOwner();
                Log.i(TAG, "SUCCESS: Device Owner status relinquished. Skyward can now be uninstalled.");
            } else {
                Log.w(TAG, "App is not currently Device Owner; ignoring clear command.");
            }
        }
    }
}
