package com.example.skywardblocker.schedule;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.skywardblocker.time.TrustedTimeManager;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Holds the parent-configured locked-hours schedule and decides whether the device should
 * currently be enforcing app blocking.
 *
 * A schedule is a daily time window (which may wrap past midnight), restricted to a set of
 * weekdays, and bounded by a fixed block period [activeFrom, activeUntil). The parent picks
 * all of this once in the desktop companion; it is immutable afterwards. When the block
 * period ends the schedule expires and the device unblocks permanently.
 *
 * Enforcement never trusts the wall clock directly — it goes through TrustedTimeManager's
 * elapsedRealtime-anchored trusted time, and fails locked (assumes enforcement should be
 * active) whenever no trusted anchor is available yet.
 *
 * The decision logic lives in the static {@link Spec} helpers, which take an explicit
 * TimeZone and no Context so they can be unit-tested directly (see ScheduleManagerTest).
 */
public class ScheduleManager {

    private static final String TAG = "SkywardDebug";
    private static final String PREF_NAME = "skyward_schedule";

    private static final String KEY_ENABLED = "schedule_enabled";
    private static final String KEY_START_HOUR = "lock_start_hour";
    private static final String KEY_START_MINUTE = "lock_start_minute";
    private static final String KEY_END_HOUR = "lock_end_hour";
    private static final String KEY_END_MINUTE = "lock_end_minute";
    private static final String KEY_DAYS_MASK = "days_mask";
    private static final String KEY_ACTIVE_FROM = "active_from";
    private static final String KEY_ACTIVE_UNTIL = "active_until";
    private static final String KEY_TIMEZONE_ID = "timezone_id";
    private static final String KEY_MANUAL_OVERRIDE = "manual_override_active";

    /** Every weekday selected — the mask used when a caller doesn't specify days. */
    public static final int ALL_DAYS = 0b1111111;

    private static final long MINUTE_MS = 60_000L;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private ScheduleManager() {
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    public static void saveSchedule(Context context, int startHour, int startMinute,
                                     int endHour, int endMinute, int daysMask,
                                     long activeFrom, long activeUntil, String timezoneId) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_START_HOUR, startHour)
                .putInt(KEY_START_MINUTE, startMinute)
                .putInt(KEY_END_HOUR, endHour)
                .putInt(KEY_END_MINUTE, endMinute)
                .putInt(KEY_DAYS_MASK, daysMask)
                .putLong(KEY_ACTIVE_FROM, activeFrom)
                .putLong(KEY_ACTIVE_UNTIL, activeUntil)
                .putString(KEY_TIMEZONE_ID, timezoneId)
                .apply();
        Log.i(TAG, "Schedule saved: locked " + startHour + ":" + startMinute
                + " -> " + endHour + ":" + endMinute
                + " days=" + Integer.toBinaryString(daysMask)
                + " from=" + activeFrom + " until=" + activeUntil
                + " (tz=" + timezoneId + ")");
    }

    public static boolean isScheduleEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    /** {hour, minute} of the start of the locked window. */
    public static int[] getLockStart(Context context) {
        SharedPreferences p = prefs(context);
        return new int[]{p.getInt(KEY_START_HOUR, 0), p.getInt(KEY_START_MINUTE, 0)};
    }

    /** {hour, minute} of the end of the locked window. */
    public static int[] getLockEnd(Context context) {
        SharedPreferences p = prefs(context);
        return new int[]{p.getInt(KEY_END_HOUR, 0), p.getInt(KEY_END_MINUTE, 0)};
    }

    /** Weekday bitmask, bit 0 = Sunday … bit 6 = Saturday. */
    public static int getDaysMask(Context context) {
        return prefs(context).getInt(KEY_DAYS_MASK, ALL_DAYS);
    }

    /** Epoch millis at which the block period begins. */
    public static long getActiveFrom(Context context) {
        return prefs(context).getLong(KEY_ACTIVE_FROM, 0L);
    }

    /** Epoch millis at which the block period ends and the schedule expires for good. */
    public static long getActiveUntil(Context context) {
        return prefs(context).getLong(KEY_ACTIVE_UNTIL, Long.MAX_VALUE);
    }

    /** IANA timezone id captured at setup time. Metadata only — not read by live enforcement. */
    public static String getTimezoneId(Context context) {
        return prefs(context).getString(KEY_TIMEZONE_ID, "");
    }

    public static void setManualOverride(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_MANUAL_OVERRIDE, active).apply();
    }

    public static boolean getManualOverride(Context context) {
        return prefs(context).getBoolean(KEY_MANUAL_OVERRIDE, false);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Snapshot of the stored schedule, for handing to the pure decision helpers. */
    public static Spec getSpec(Context context) {
        int[] start = getLockStart(context);
        int[] end = getLockEnd(context);
        return new Spec(
                start[0] * 60 + start[1],
                end[0] * 60 + end[1],
                getDaysMask(context),
                getActiveFrom(context),
                getActiveUntil(context));
    }

    // ── Decision logic (Context-bound wrappers) ─────────────────────────────

    /**
     * True if the configured window has no start/end distinction (start == end), meaning
     * the device is locked for the whole of every selected day with no scheduled unlock.
     * Lets the UI show "Blocked 24 hours" instead of a misleading countdown toward an
     * unlock that will never happen that day.
     */
    public static boolean isFullDayLock(Context context) {
        return getSpec(context).isFullDay();
    }

    /**
     * Whether the block period has already ended. Once expired the device unblocks
     * permanently — the parent must create and push a new schedule to block again.
     */
    public static boolean isExpired(Context context) {
        Long trustedNow = TrustedTimeManager.getCurrentTrustedEpochMillis(context);
        // Without trusted time we cannot claim expiry; fail closed and let the caller's
        // fail-locked path decide.
        if (trustedNow == null) return false;
        return isScheduleEnabled(context) && trustedNow >= getActiveUntil(context);
    }

    /**
     * Whether the given trusted epoch millis falls inside the locked window, evaluated in
     * the device's current default timezone (fetched fresh, never cached, so it reflects
     * any future automatic timezone change).
     */
    public static boolean isWithinLockWindow(Context context, long trustedEpochMillis) {
        return getSpec(context).isLockedAt(trustedEpochMillis, TimeZone.getDefault());
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
        // A manual override must not resurrect blocking after the block period has ended —
        // expiry means the parent's commitment is over, full stop.
        if (trustedNow >= getActiveUntil(context)) {
            if (override) setManualOverride(context, false);
            return false;
        }
        return scheduleLocked || override;
    }

    /**
     * Whether blocking is currently called for by the schedule alone (ignoring any active
     * grace period). With no schedule enabled, there is nothing to enforce, so this is
     * false. Used both by shouldEnforceNow() and by the UI to decide when a grace-period
     * "unlock" button is relevant to offer.
     */
    public static boolean isBlockingScheduled(Context context) {
        return isScheduleEnabled(context) && isCurrentlyLocked(context);
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
     * Milliseconds from the given trusted epoch until the next lock/unlock transition, or
     * {@link #NO_BOUNDARY} when the schedule has no further transitions (it has expired).
     * Used to schedule the next AlarmManager alarm.
     */
    public static long millisUntilNextBoundary(Context context, long trustedEpochMillis) {
        long boundary = getSpec(context).nextBoundaryAfter(trustedEpochMillis, TimeZone.getDefault());
        if (boundary == NO_BOUNDARY) return NO_BOUNDARY;
        return Math.max(boundary - trustedEpochMillis, 1_000L);
    }

    /** Sentinel returned when no further lock/unlock transition will ever occur. */
    public static final long NO_BOUNDARY = -1L;

    // ── Pure decision core ──────────────────────────────────────────────────

    /**
     * An immutable schedule, expressed in minutes-since-midnight plus a weekday mask and a
     * block period. All decision logic lives here as pure functions of (spec, instant,
     * timezone) so it can be exercised directly in unit tests.
     */
    public static final class Spec {
        public final int startMin;
        public final int endMin;
        /** Bit 0 = Sunday … bit 6 = Saturday, matching Calendar.DAY_OF_WEEK - 1. */
        public final int daysMask;
        public final long activeFrom;
        public final long activeUntil;

        public Spec(int startMin, int endMin, int daysMask, long activeFrom, long activeUntil) {
            this.startMin = startMin;
            this.endMin = endMin;
            this.daysMask = daysMask;
            this.activeFrom = activeFrom;
            this.activeUntil = activeUntil;
        }

        /** Window covers a whole selected day, with no unlock boundary within it. */
        public boolean isFullDay() {
            return startMin == endMin;
        }

        /** Length of one occurrence of the window, in minutes. Always in (0, 1440]. */
        public int durationMinutes() {
            if (isFullDay()) return MINUTES_PER_DAY;
            int raw = endMin - startMin;
            return raw > 0 ? raw : raw + MINUTES_PER_DAY;
        }

        private boolean dayEnabled(int dayIndex) {
            return (daysMask & (1 << dayIndex)) != 0;
        }

        /**
         * Whether the device is locked at the given instant.
         *
         * The weekday mask gates the day the window <em>starts</em> on, not the day it is
         * currently observed on. For an overnight window, an instant before endMin belongs
         * to the window that began yesterday — checking today's bit there would make a
         * Friday-night block silently evaporate at midnight.
         */
        public boolean isLockedAt(long epochMillis, TimeZone tz) {
            if (epochMillis < activeFrom || epochMillis >= activeUntil) return false;
            if (daysMask == 0) return false;

            Calendar cal = Calendar.getInstance(tz);
            cal.setTimeInMillis(epochMillis);
            int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            int todayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1;
            int yesterdayIndex = (todayIndex + 6) % 7;

            if (isFullDay()) {
                // Locked for the entire day the window starts on, i.e. from startMin today
                // back through startMin yesterday.
                return nowMin >= startMin ? dayEnabled(todayIndex) : dayEnabled(yesterdayIndex);
            }
            if (startMin < endMin) {
                return nowMin >= startMin && nowMin < endMin && dayEnabled(todayIndex);
            }
            // Wraps past midnight.
            if (nowMin >= startMin) return dayEnabled(todayIndex);
            if (nowMin < endMin) return dayEnabled(yesterdayIndex);
            return false;
        }

        /**
         * Epoch millis of the first lock/unlock transition strictly after the given
         * instant, or {@link #NO_BOUNDARY} if none remains.
         *
         * Candidates are the start and end instants of every window occurrence in the
         * surrounding days, plus activeFrom and activeUntil themselves. activeUntil must be
         * in the set, otherwise nothing would wake the device to release enforcement when
         * the block period ends.
         *
         * Days are walked with Calendar arithmetic rather than fixed millisecond offsets so
         * DST transitions shift boundaries correctly.
         */
        public long nextBoundaryAfter(long epochMillis, TimeZone tz) {
            long best = NO_BOUNDARY;

            if (activeFrom > epochMillis) best = activeFrom;
            if (activeUntil > epochMillis && (best == NO_BOUNDARY || activeUntil < best)) {
                best = activeUntil;
            }

            if (daysMask != 0) {
                Calendar day = Calendar.getInstance(tz);
                day.setTimeInMillis(epochMillis);
                day.set(Calendar.HOUR_OF_DAY, 0);
                day.set(Calendar.MINUTE, 0);
                day.set(Calendar.SECOND, 0);
                day.set(Calendar.MILLISECOND, 0);
                // Start a day back so a window that began yesterday still contributes its
                // end boundary.
                day.add(Calendar.DAY_OF_MONTH, -1);

                int duration = durationMinutes();
                for (int i = 0; i < 9; i++) {
                    if (dayEnabled(day.get(Calendar.DAY_OF_WEEK) - 1)) {
                        Calendar startCal = (Calendar) day.clone();
                        startCal.add(Calendar.MINUTE, startMin);
                        long start = startCal.getTimeInMillis();

                        Calendar endCal = (Calendar) startCal.clone();
                        endCal.add(Calendar.MINUTE, duration);
                        long end = endCal.getTimeInMillis();

                        for (long candidate : new long[]{start, end}) {
                            if (candidate <= epochMillis) continue;
                            if (candidate < activeFrom || candidate >= activeUntil) continue;
                            if (best == NO_BOUNDARY || candidate < best) best = candidate;
                        }
                    }
                    day.add(Calendar.DAY_OF_MONTH, 1);
                }
            }

            return best;
        }
    }
}
