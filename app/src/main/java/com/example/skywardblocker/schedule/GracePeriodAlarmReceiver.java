package com.example.skywardblocker.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.skywardblocker.blocking.CategoryManager;

/**
 * Fired by AlarmManager when an active grace period's duration elapses. Re-applies
 * enforcement, which naturally re-locks apps once GracePeriodManager reports the window
 * as no longer active.
 */
public class GracePeriodAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "SkywardDebug";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "GracePeriodAlarmReceiver: grace window elapsed, resuming enforcement");
        CategoryManager.applyEnforcement(context.getApplicationContext());
    }
}
