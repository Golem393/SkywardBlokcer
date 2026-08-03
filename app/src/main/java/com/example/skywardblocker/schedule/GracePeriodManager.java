package com.example.skywardblocker.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.example.skywardblocker.time.TrustedTimeManager;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Lets the device holder temporarily lift app blocking for a short "grace" window, a
 * limited number of times per day. Independent of the locked-hours schedule — it can
 * interrupt a locked window, or the default always-locked state when no schedule has
 * ever been configured.
 *
 * The daily quota resets on the trusted-time calendar day (device default timezone), not
 * the user-settable wall clock, so it can't be replayed by turning the clock back.
 */
public class GracePeriodManager {

    private static final String TAG = "SkywardDebug";
    private static final String PREF_NAME = "skyward_grace_period";

    private static final String KEY_QUOTA_DATE = "quota_date";
    private static final String KEY_USES_TODAY = "uses_today";
    private static final String KEY_ACTIVE_START_ELAPSED = "active_start_elapsed";
    private static final String KEY_ACTIVE_END_ELAPSED = "active_end_elapsed";

    public static final int MAX_USES_PER_DAY = 3;
    public static final long DURATION_MS = 2 * 60_000L;

    private GracePeriodManager() {
    }

    /** True if a grace window is currently in effect (apps temporarily unblocked). */
    public static boolean isGraceActive(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long start = p.getLong(KEY_ACTIVE_START_ELAPSED, -1);
        long end = p.getLong(KEY_ACTIVE_END_ELAPSED, -1);
        if (start < 0 || end < 0) return false;

        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed < start) {
            // elapsedRealtime() reset (device rebooted) since this window was recorded.
            return false;
        }
        return nowElapsed < end;
    }

    /** Milliseconds remaining in the active grace window, or 0 if none is active. */
    public static long remainingMillis(Context context) {
        if (!isGraceActive(context)) return 0;
        SharedPreferences p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long end = p.getLong(KEY_ACTIVE_END_ELAPSED, -1);
        return Math.max(0, end - SystemClock.elapsedRealtime());
    }

    /** How many of today's grace uses remain, applying the daily reset first. */
    public static int usesRemainingToday(Context context) {
        return Math.max(0, MAX_USES_PER_DAY - usesToday(context));
    }

    /**
     * True if a new grace window can be started right now: a trusted-time anchor must
     * exist (so the daily quota can't be reset by turning the clock back) and today's
     * quota must not already be exhausted.
     */
    public static boolean canStartGrace(Context context) {
        if (isGraceActive(context)) return false;
        if (TrustedTimeManager.getCurrentTrustedEpochMillis(context) == null) return false;
        return usesRemainingToday(context) > 0;
    }

    /**
     * Consumes one of today's uses and opens a DURATION_MS grace window starting now.
     * Returns false (no-op) if canStartGrace() would have returned false.
     */
    public static boolean startGrace(Context context) {
        if (!canStartGrace(context)) return false;

        SharedPreferences p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String today = currentDateKey(context);
        int usesToday = usesToday(context);
        long nowElapsed = SystemClock.elapsedRealtime();

        p.edit()
                .putString(KEY_QUOTA_DATE, today)
                .putInt(KEY_USES_TODAY, usesToday + 1)
                .putLong(KEY_ACTIVE_START_ELAPSED, nowElapsed)
                .putLong(KEY_ACTIVE_END_ELAPSED, nowElapsed + DURATION_MS)
                .apply();

        Log.i(TAG, "Grace period started (" + (usesToday + 1) + "/" + MAX_USES_PER_DAY + " used today)");
        return true;
    }

    /** Ends any active grace window immediately, without touching today's use count. */
    public static void endGraceNow(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_ACTIVE_START_ELAPSED, -1)
                .putLong(KEY_ACTIVE_END_ELAPSED, -1)
                .apply();
    }

    private static int usesToday(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String today = currentDateKey(context);
        if (!today.equals(p.getString(KEY_QUOTA_DATE, ""))) {
            return 0; // rolled over to a new day; nothing persisted for it yet
        }
        return p.getInt(KEY_USES_TODAY, 0);
    }

    /** yyyy-MM-dd for the current trusted time in the device's default timezone. */
    private static String currentDateKey(Context context) {
        Long trustedNow = TrustedTimeManager.getCurrentTrustedEpochMillis(context);
        long epoch = trustedNow != null ? trustedNow : System.currentTimeMillis();
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.setTimeInMillis(epoch);
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }
}
