package com.example.skywardblocker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.skywardblocker.admin.DevicePolicyHelper;
import com.example.skywardblocker.api.ApiClient;
import com.example.skywardblocker.blocking.AppMonitorService;
import com.example.skywardblocker.blocking.CategoryManager;
import com.example.skywardblocker.schedule.ScheduleAlarmScheduler;
import com.example.skywardblocker.schedule.ScheduleManager;

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
 *
 *   3) Push a daily locked-hours schedule from desktop companion app:
 *      adb shell am broadcast -a com.example.skywardblocker.PUSH_SCHEDULE \
 *        -n com.example.skywardblocker/.receiver.AdbCommandReceiver \
 *        --ei lock_start_hour 8 --ei lock_start_minute 0 \
 *        --ei lock_end_hour 22 --ei lock_end_minute 0 --es timezone_id "Europe/Zurich"
 *
 *   4) Finalize provisioning (seals USB debugging) — sent once the desktop installer has
 *      finished pushing config/schedule and launching the app:
 *      adb shell am broadcast -a com.example.skywardblocker.FINALIZE_SETUP \
 *        -n com.example.skywardblocker/.receiver.AdbCommandReceiver
 */
public class AdbCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "SkywardDebug";

    public static final String ACTION_CLEAR_OWNER = "com.example.skywardblocker.CLEAR_OWNER";
    public static final String ACTION_PUSH_CONFIG = "com.example.skywardblocker.PUSH_CONFIG";
    public static final String ACTION_PUSH_SCHEDULE = "com.example.skywardblocker.PUSH_SCHEDULE";
    public static final String ACTION_FINALIZE_SETUP = "com.example.skywardblocker.FINALIZE_SETUP";

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
        else if (ACTION_PUSH_SCHEDULE.equals(action)) {
            int startHour = intent.getIntExtra("lock_start_hour", 0);
            int startMinute = intent.getIntExtra("lock_start_minute", 0);
            int endHour = intent.getIntExtra("lock_end_hour", 0);
            int endMinute = intent.getIntExtra("lock_end_minute", 0);
            String timezoneId = intent.getStringExtra("timezone_id");

            Log.i(TAG, "Received PUSH_SCHEDULE broadcast: locked " + startHour + ":" + startMinute
                    + " -> " + endHour + ":" + endMinute + " (tz=" + timezoneId + ")");
            ScheduleManager.saveSchedule(context, startHour, startMinute, endHour, endMinute, timezoneId);
            ScheduleAlarmScheduler.rescheduleAll(context);
            CategoryManager.applyEnforcement(context);
        }
        else if (ACTION_FINALIZE_SETUP.equals(action)) {
            Log.i(TAG, "Received FINALIZE_SETUP broadcast — sealing USB debugging.");
            DevicePolicyHelper helper = new DevicePolicyHelper(context);
            if (helper.isDeviceOwner()) {
                helper.finalizeProvisioning();
            } else {
                Log.w(TAG, "App is not currently Device Owner; ignoring finalize command.");
            }
        }
    }
}
