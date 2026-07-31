package com.example.skywardblocker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.skywardblocker.admin.DevicePolicyHelper;
import com.example.skywardblocker.blocking.AppMonitorService;
import com.example.skywardblocker.blocking.CategoryManager;
import com.example.skywardblocker.schedule.ScheduleAlarmScheduler;

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
                // ELAPSED_REALTIME_WAKEUP alarms don't survive reboot; re-arm the next
                // schedule boundary. If no trusted-time anchor exists yet this is a no-op —
                // AppMonitorService's ConnectivityManager callback picks up the sync as
                // soon as connectivity actually validates, and reschedules from there.
                ScheduleAlarmScheduler.rescheduleAll(context);
            } else {
                Log.d(TAG, "BootReceiver: not Device Owner yet; skipping start.");
            }
        }
    }
}
