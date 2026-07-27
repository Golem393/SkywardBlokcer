package com.example.skywardblocker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.skywardblocker.admin.DevicePolicyHelper;
import com.example.skywardblocker.api.ApiClient;
import com.example.skywardblocker.blocking.AppMonitorService;
import com.example.skywardblocker.blocking.CategoryManager;

/**
 * Listens for commands dispatched over ADB from the desktop companion app (or during development).
 *
 * Supported commands:
 *   1) Clear Device Owner status:
 *      adb shell am broadcast -a com.example.skywardblocker.CLEAR_OWNER
 *
 *   2) Push Configuration from desktop companion app:
 *      adb shell am broadcast -a com.example.skywardblocker.PUSH_CONFIG \
 *        -n com.example.skywardblocker/.receiver.AdbCommandReceiver \
 *        --es base_url "https://..." --es api_key "api_..." --es dns_hostname "..."
 */
public class AdbCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "SkywardDebug";

    public static final String ACTION_CLEAR_OWNER = "com.example.skywardblocker.CLEAR_OWNER";
    public static final String ACTION_PUSH_CONFIG = "com.example.skywardblocker.PUSH_CONFIG";

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
        else if (ACTION_PUSH_CONFIG.equals(action)) {
            String baseUrl = intent.getStringExtra("base_url");
            String apiKey = intent.getStringExtra("api_key");
            String dnsHostname = intent.getStringExtra("dns_hostname");

            Log.i(TAG, "Received PUSH_CONFIG broadcast. base_url=" + baseUrl + ", dns=" + dnsHostname);
            ApiClient.saveConfig(context, baseUrl, apiKey, dnsHostname);

            // If Device Owner is active, apply full protection, Private DNS, and background service immediately!
            DevicePolicyHelper helper = new DevicePolicyHelper(context);
            if (helper.isDeviceOwner()) {
                if (dnsHostname != null && !dnsHostname.trim().isEmpty()) {
                    helper.setPrivateDns(dnsHostname);
                    Log.i(TAG, "Applied new Private DNS immediately upon config push!");
                }
                helper.lockdownSkyward();
                CategoryManager.initializeCache(context);
                AppMonitorService.start(context);
                Log.i(TAG, "Full Skyward protection activated via ADB config push!");
            }
        }
    }
}
