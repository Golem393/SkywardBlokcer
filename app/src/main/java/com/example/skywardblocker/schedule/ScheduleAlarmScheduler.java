package com.example.skywardblocker.schedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.example.skywardblocker.time.TrustedTimeManager;

/**
 * Schedules the AlarmManager alarm that fires the next lock/unlock transition.
 *
 * Uses ELAPSED_REALTIME_WAKEUP (boot-relative, monotonic, immune to Settings > Date & Time
 * changes) with an offset computed from the trusted, network-corrected clock — so the
 * alarm fires at the correct real-world moment without ever depending on the user-settable
 * wall clock.
 */
public class ScheduleAlarmScheduler {

    private static final String TAG = "SkywardDebug";
    public static final String ACTION_BOUNDARY = "com.example.skywardblocker.ACTION_SCHEDULE_BOUNDARY";

    private ScheduleAlarmScheduler() {
    }

    public static void rescheduleAll(Context context) {
        Context appContext = context.getApplicationContext();
        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(appContext);
        am.cancel(pi);

        if (!ScheduleManager.isScheduleEnabled(appContext)) {
            Log.d(TAG, "ScheduleAlarmScheduler: no schedule configured, nothing to arm");
            return;
        }

        Long trustedNow = TrustedTimeManager.getCurrentTrustedEpochMillis(appContext);
        if (trustedNow == null) {
            Log.w(TAG, "ScheduleAlarmScheduler: no trusted time anchor yet — will retry once time syncs");
            return;
        }

        long millisToNext = ScheduleManager.millisUntilNextBoundary(appContext, trustedNow);
        if (millisToNext == ScheduleManager.NO_BOUNDARY) {
            Log.i(TAG, "ScheduleAlarmScheduler: schedule has expired, no further boundaries to arm");
            return;
        }
        long triggerElapsed = SystemClock.elapsedRealtime() + millisToNext;

        try {
            if (!canScheduleExactAlarms(appContext)) {
                Log.w(TAG, "Exact alarms unavailable — falling back to inexact alarm (AppMonitorService backstop covers correctness)");
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi);
            }
            Log.d(TAG, "ScheduleAlarmScheduler: next boundary in " + millisToNext + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule boundary alarm", e);
        }
    }

    /**
     * Whether this device grants exact-alarm scheduling. Expected to be true automatically
     * for Device Owner apps, but some OS versions/OEMs don't apply that exemption to devices
     * provisioned via `dpm set-device-owner` (vs. full managed provisioning) — when false,
     * the boundary alarm falls back to inexact, which Doze/App Standby may delay, so
     * AppMonitorService uses this to decide whether it needs to run a periodic backstop.
     */
    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) context.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        intent.setAction(ACTION_BOUNDARY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }
}
