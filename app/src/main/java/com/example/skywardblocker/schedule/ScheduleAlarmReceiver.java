package com.example.skywardblocker.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.skywardblocker.blocking.CategoryManager;

/**
 * Fired by AlarmManager at each computed lock/unlock boundary. Applies the new
 * enforcement state immediately, then chains to the next boundary.
 */
public class ScheduleAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "SkywardDebug";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "ScheduleAlarmReceiver: boundary reached, applying enforcement");
        Context appContext = context.getApplicationContext();
        CategoryManager.applyEnforcement(appContext);
        ScheduleAlarmScheduler.rescheduleAll(appContext);
    }
}
