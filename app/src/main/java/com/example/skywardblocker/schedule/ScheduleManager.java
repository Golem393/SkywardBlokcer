package com.example.skywardblocker.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.skywardblocker.time.TrustedTimeManager;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Holds the parent-configured daily locked-hours window and decides whether the device
 * should currently be enforcing app blocking.
 *
 * Enforcement never trusts the wall clock directly — it goes through
 * TrustedTimeManager's elapsedRealtime-anchored trusted time, and fails locked (assumes
 * enforcement should be active) whenever no trusted anchor is available yet.
 */
public class ScheduleManager {

    private static final String TAG = "SkywardDebug";
    private static final String PREF_NAME = "skyward_schedule";

    private static final String KEY_ENABLED = "schedule_enabled";
    private static final String KEY_START_HOUR = "lock_start_hour";
    private static final String KEY_START_MINUTE = "lock_start_minute";
    private static final String KEY_END_HOUR = "lock_end_hour";
    private static final String KEY_END_MINUTE = "lock_end_minute";
    private static final String KEY_TIMEZONE_ID = "timezone_id";
    private static final String KEY_MANUAL_OVERRIDE = "manual_override_active";

    private ScheduleManager() {
    }

    public static void saveSchedule(Context context, int startHour, int startMinute,
                                     int endHour, int endMinute, String timezoneId) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_START_HOUR, startHour)
                .putInt(KEY_START_MINUTE, startMinute)
                .putInt(KEY_END_HOUR, endHour)
                .putInt(KEY_END_MINUTE, endMinute)
                .putString(KEY_TIMEZONE_ID, timezoneId)
                .apply();
        Log.i(TAG, "Schedule saved: locked " + startHour + ":" + startMinute
                + " -> " + endHour + ":" + endMinute + " (tz=" + timezoneId + ")");
    }

    public static boolean isScheduleEnabled(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    /** {hour, minute} of the start of the locked window. */
    public static int[] getLockStart(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new int[]{p.getInt(KEY_START_HOUR, 0), p.getInt(KEY_START_MINUTE, 0)};
    }

    /** {hour, minute} of the end of the locked window. */
    public static int[] getLockEnd(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new int[]{p.getInt(KEY_END_HOUR, 0), p.getInt(KEY_END_MINUTE, 0)};
    }

    /** IANA timezone id captured at setup time. Metadata only — not read by live enforcement. */
    public static String getTimezoneId(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TIMEZONE_ID, "");
    }

    public static void setManualOverride(Context context, boolean active) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_MANUAL_OVERRIDE, active)
                .apply();
    }

    public static boolean getManualOverride(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MANUAL_OVERRIDE, false);
    }

    /**
     * True if the configured window has no start/end distinction (start == end), meaning
     * the device is always locked with no scheduled unlock — e.g. the "Full-Day
     * Restriction" preset. Lets the UI show "Blocked 24 hours" instead of a misleading
     * countdown toward an unlock that will never happen.
     */
    public static boolean isFullDayLock(Context context) {
        int[] start = getLockStart(context);
        int[] end = getLockEnd(context);
        return start[0] * 60 + start[1] == end[0] * 60 + end[1];
    }

    /**
     * Overnight-wrap-aware check of whether the given trusted epoch millis falls within
     * the configured locked window, evaluated in the device's current default timezone
     * (fetched fresh, never cached, so it reflects any future automatic timezone change).
     */
    public static boolean isWithinLockWindow(Context context, long trustedEpochMillis) {
        int[] start = getLockStart(context);
        int[] end = getLockEnd(context);

        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.setTimeInMillis(trustedEpochMillis);
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        int startMin = start[0] * 60 + start[1];
        int endMin = end[0] * 60 + end[1];

        if (startMin == endMin) return true; // Full-day restriction
        if (startMin < endMin) {
            return nowMin >= startMin && nowMin < endMin; // same-day window
        } else {
            return nowMin >= startMin || nowMin < endMin; // wraps past midnight
        }
    }

    /**
     * Whether the device should currently be locked, combining trusted time, fail-locked
     * behavior, and the manual "opt back in early" override. The override self-clears once
     * the schedule naturally re-enters a locked window, so it never outlives the point
     * where the device would have locked anyway.
     */
    public static boolean isCurrentlyLocked(Context context) {
        Long trustedNow = TrustedTimeManager.getCurrentTrustedEpochMillis(context);
        if (trustedNow == null) {
            Log.w(TAG, "No trusted time anchor available — FAIL LOCKED");
            return true;
        }

        boolean scheduleLocked = isWithinLockWindow(context, trustedNow);
        boolean override = getManualOverride(context);
        if (scheduleLocked && override) {
            setManualOverride(context, false);
            override = false;
        }
        return scheduleLocked || override;
    }

    /**
     * Whether blocking is currently called for by the schedule alone (ignoring any active
     * grace period). If no schedule has ever been pushed, this is always true (preserves
     * pre-scheduling always-on behavior). Used both by shouldEnforceNow() and by the UI to
     * decide when a grace-period "unlock" button is relevant to offer.
     */
    public static boolean isBlockingScheduled(Context context) {
        return !isScheduleEnabled(context) || isCurrentlyLocked(context);
    }

    /**
     * Whether app-blocking enforcement should be active right now. An active grace period
     * (see GracePeriodManager) temporarily overrides the schedule, letting the device
     * holder unlock apps for a short, limited-per-day window.
     */
    public static boolean shouldEnforceNow(Context context) {
        if (GracePeriodManager.isGraceActive(context)) return false;
        return isBlockingScheduled(context);
    }

    /**
     * Milliseconds from the given trusted epoch until the next lock/unlock boundary,
     * accounting for overnight wraparound. Used to schedule the next AlarmManager alarm.
     */
    public static long millisUntilNextBoundary(Context context, long trustedEpochMillis) {
        int[] start = getLockStart(context);
        int[] end = getLockEnd(context);

        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.setTimeInMillis(trustedEpochMillis);
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int nowSec = cal.get(Calendar.SECOND);
        int nowMillis = cal.get(Calendar.MILLISECOND);

        int startMin = start[0] * 60 + start[1];
        int endMin = end[0] * 60 + end[1];

        boolean locked = isWithinLockWindow(context, trustedEpochMillis);
        int boundaryMin = locked ? endMin : startMin;

        int minutesUntil = boundaryMin - nowMin;
        if (minutesUntil <= 0) {
            minutesUntil += 24 * 60; // boundary is tomorrow
        }

        long millisUntil = minutesUntil * 60_000L;
        millisUntil -= (nowSec * 1000L + nowMillis); // align to the exact minute boundary
        return Math.max(millisUntil, 1_000L);
    }
}
