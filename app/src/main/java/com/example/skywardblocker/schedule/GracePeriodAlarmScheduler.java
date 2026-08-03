package com.example.skywardblocker.schedule;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Schedules the one-shot alarm that re-locks apps when an active grace period (see
 * GracePeriodManager) ends, so enforcement resumes even if the app isn't in the
 * foreground when the window closes.
 */
public class GracePeriodAlarmScheduler {

    private static final String TAG = "SkywardDebug";
    public static final String ACTION_GRACE_END = "com.example.skywardblocker.ACTION_GRACE_END";

    private GracePeriodAlarmScheduler() {
    }

    public static void scheduleGraceEnd(Context context, long delayMillis) {
        Context appContext = context.getApplicationContext();
        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(appContext);
        long triggerElapsed = SystemClock.elapsedRealtime() + delayMillis;

        try {
            if (ScheduleAlarmScheduler.canScheduleExactAlarms(appContext)) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi);
            }
            Log.d(TAG, "GracePeriodAlarmScheduler: grace-end alarm armed in " + delayMillis + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule grace-end alarm", e);
        }
    }

    public static void cancel(Context context) {
        Context appContext = context.getApplicationContext();
        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(buildPendingIntent(appContext));
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, GracePeriodAlarmReceiver.class);
        intent.setAction(ACTION_GRACE_END);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }
}
