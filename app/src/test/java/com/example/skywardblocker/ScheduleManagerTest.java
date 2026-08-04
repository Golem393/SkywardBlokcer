package com.example.skywardblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.skywardblocker.schedule.ScheduleManager;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Exercises the pure schedule decision core: the interaction of the daily window (including
 * midnight wraparound), the weekday mask, and the bounded block period.
 *
 * These are the cases where an off-by-one silently un-blocks a phone, so they are pinned
 * here rather than left to manual on-device checking.
 */
public class ScheduleManagerTest {

    private static final TimeZone TZ = TimeZone.getTimeZone("Europe/Zurich");

    // Weekday bits: bit 0 = Sunday … bit 6 = Saturday.
    private static final int SUN = 1;
    private static final int MON = 1 << 1;
    private static final int TUE = 1 << 2;
    private static final int WED = 1 << 3;
    private static final int THU = 1 << 4;
    private static final int FRI = 1 << 5;
    private static final int SAT = 1 << 6;
    private static final int WEEKDAYS = MON | TUE | WED | THU | FRI;

    /** Epoch millis for a local wall-clock instant in TZ. */
    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance(TZ);
        cal.clear();
        cal.set(year, month - 1, day, hour, minute, 0);
        return cal.getTimeInMillis();
    }

    private static ScheduleManager.Spec spec(int startMin, int endMin, int daysMask,
                                             long from, long until) {
        return new ScheduleManager.Spec(startMin, endMin, daysMask, from, until);
    }

    // 2026-08-03 is a Monday; 2026-08-07 a Friday, 2026-08-08 a Saturday.
    private static final long PERIOD_START = at(2026, 8, 3, 0, 0);
    private static final long PERIOD_END = at(2026, 8, 10, 0, 0);

    // ── Same-day window ─────────────────────────────────────────────────────

    @Test
    public void sameDayWindow_locksOnlyInsideTheWindow() {
        // Locked 08:00–15:00 on weekdays.
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, WEEKDAYS, PERIOD_START, PERIOD_END);

        assertFalse("before start", s.isLockedAt(at(2026, 8, 3, 7, 59), TZ));
        assertTrue("at start", s.isLockedAt(at(2026, 8, 3, 8, 0), TZ));
        assertTrue("mid window", s.isLockedAt(at(2026, 8, 3, 12, 0), TZ));
        assertFalse("at end is exclusive", s.isLockedAt(at(2026, 8, 3, 15, 0), TZ));
        assertFalse("after end", s.isLockedAt(at(2026, 8, 3, 20, 0), TZ));
    }

    @Test
    public void sameDayWindow_ignoresUnselectedDays() {
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, WEEKDAYS, PERIOD_START, PERIOD_END);

        // Saturday 2026-08-08 is not in the weekday mask.
        assertFalse(s.isLockedAt(at(2026, 8, 8, 12, 0), TZ));
        // Sunday 2026-08-09 likewise.
        assertFalse(s.isLockedAt(at(2026, 8, 9, 12, 0), TZ));
    }

    // ── Overnight wraparound ────────────────────────────────────────────────

    @Test
    public void overnightWindow_staysLockedPastMidnightIntoTheNextMorning() {
        // Locked 21:00–07:00, Friday only. The block must survive midnight into Saturday
        // morning even though Saturday is not itself a selected day.
        ScheduleManager.Spec s = spec(21 * 60, 7 * 60, FRI, PERIOD_START, PERIOD_END);

        assertFalse("Friday before start", s.isLockedAt(at(2026, 8, 7, 20, 59), TZ));
        assertTrue("Friday night", s.isLockedAt(at(2026, 8, 7, 22, 0), TZ));
        assertTrue("just past midnight", s.isLockedAt(at(2026, 8, 8, 0, 30), TZ));
        assertTrue("Saturday early morning", s.isLockedAt(at(2026, 8, 8, 6, 59), TZ));
        assertFalse("Saturday after end", s.isLockedAt(at(2026, 8, 8, 7, 0), TZ));
    }

    @Test
    public void overnightWindow_doesNotLockOnTheMorningAfterAnUnselectedDay() {
        // Friday-only: Friday morning belongs to Thursday night's window, which is not
        // selected, so it must be unlocked.
        ScheduleManager.Spec s = spec(21 * 60, 7 * 60, FRI, PERIOD_START, PERIOD_END);

        assertFalse("Friday morning follows unselected Thursday", s.isLockedAt(at(2026, 8, 7, 6, 0), TZ));
    }

    // ── Full-day lock ───────────────────────────────────────────────────────

    @Test
    public void fullDayLock_coversTheWholeSelectedDayOnly() {
        // start == end == 00:00, Wednesday only.
        ScheduleManager.Spec s = spec(0, 0, WED, PERIOD_START, PERIOD_END);

        assertTrue(s.isFullDay());
        assertTrue("Wednesday midnight", s.isLockedAt(at(2026, 8, 5, 0, 0), TZ));
        assertTrue("Wednesday midday", s.isLockedAt(at(2026, 8, 5, 12, 0), TZ));
        assertTrue("Wednesday last minute", s.isLockedAt(at(2026, 8, 5, 23, 59), TZ));
        assertFalse("Thursday", s.isLockedAt(at(2026, 8, 6, 0, 0), TZ));
        assertFalse("Tuesday", s.isLockedAt(at(2026, 8, 4, 12, 0), TZ));
    }

    // ── Block period bounds ─────────────────────────────────────────────────

    @Test
    public void blockPeriod_isNotActiveBeforeItStarts() {
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, WEEKDAYS, PERIOD_START, PERIOD_END);

        // Monday 2026-07-27, a week before the period opens.
        assertFalse(s.isLockedAt(at(2026, 7, 27, 12, 0), TZ));
    }

    @Test
    public void blockPeriod_expiresPermanentlyAtActiveUntil() {
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, WEEKDAYS, PERIOD_START, PERIOD_END);

        assertTrue("last weekday inside the period", s.isLockedAt(at(2026, 8, 7, 12, 0), TZ));
        // 2026-08-10 is a Monday and would otherwise be locked, but the period has ended.
        assertFalse("after activeUntil", s.isLockedAt(at(2026, 8, 10, 12, 0), TZ));
        assertFalse("long after activeUntil", s.isLockedAt(at(2026, 9, 14, 12, 0), TZ));
    }

    @Test
    public void emptyDayMask_neverLocks() {
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, 0, PERIOD_START, PERIOD_END);
        assertFalse(s.isLockedAt(at(2026, 8, 3, 12, 0), TZ));
    }

    // ── Boundary computation ────────────────────────────────────────────────

    @Test
    public void nextBoundary_findsTodaysStartWhenUnlocked() {
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, WEEKDAYS, PERIOD_START, PERIOD_END);

        long now = at(2026, 8, 3, 6, 0);
        assertEquals(at(2026, 8, 3, 8, 0), s.nextBoundaryAfter(now, TZ));
    }

    @Test
    public void nextBoundary_findsTodaysEndWhenLocked() {
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, WEEKDAYS, PERIOD_START, PERIOD_END);

        long now = at(2026, 8, 3, 9, 0);
        assertEquals(at(2026, 8, 3, 15, 0), s.nextBoundaryAfter(now, TZ));
    }

    @Test
    public void nextBoundary_skipsOverUnselectedDays() {
        // Friday-only 21:00–07:00. From Monday, the next transition is Friday's start.
        ScheduleManager.Spec s = spec(21 * 60, 7 * 60, FRI, PERIOD_START, PERIOD_END);

        long now = at(2026, 8, 3, 9, 0);
        assertEquals(at(2026, 8, 7, 21, 0), s.nextBoundaryAfter(now, TZ));
    }

    @Test
    public void nextBoundary_returnsOvernightEndFromAfterMidnight() {
        ScheduleManager.Spec s = spec(21 * 60, 7 * 60, FRI, PERIOD_START, PERIOD_END);

        long now = at(2026, 8, 8, 2, 0);
        assertEquals(at(2026, 8, 8, 7, 0), s.nextBoundaryAfter(now, TZ));
    }

    @Test
    public void nextBoundary_includesActiveUntilSoEnforcementIsReleasedAtExpiry() {
        // Locked 00:00–23:00 daily, so the period end is the nearest transition from
        // late on the final day. Without activeUntil in the candidate set nothing would
        // ever wake the device to unblock.
        ScheduleManager.Spec s = spec(0, 23 * 60, WEEKDAYS | SAT | SUN, PERIOD_START, PERIOD_END);

        long now = at(2026, 8, 9, 23, 30);
        assertEquals(PERIOD_END, s.nextBoundaryAfter(now, TZ));
    }

    @Test
    public void nextBoundary_returnsSentinelOnceExpired() {
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, WEEKDAYS, PERIOD_START, PERIOD_END);

        long now = at(2026, 8, 11, 12, 0);
        assertEquals(ScheduleManager.NO_BOUNDARY, s.nextBoundaryAfter(now, TZ));
    }

    @Test
    public void nextBoundary_returnsActiveFromBeforeThePeriodOpens() {
        ScheduleManager.Spec s = spec(8 * 60, 15 * 60, WEEKDAYS, PERIOD_START, PERIOD_END);

        long now = at(2026, 7, 30, 12, 0);
        assertEquals(PERIOD_START, s.nextBoundaryAfter(now, TZ));
    }

    // ── Duration helper ─────────────────────────────────────────────────────

    @Test
    public void durationMinutes_handlesWrapAndFullDay() {
        assertEquals(7 * 60, spec(8 * 60, 15 * 60, WEEKDAYS, 0, Long.MAX_VALUE).durationMinutes());
        assertEquals(10 * 60, spec(21 * 60, 7 * 60, WEEKDAYS, 0, Long.MAX_VALUE).durationMinutes());
        assertEquals(24 * 60, spec(0, 0, WEEKDAYS, 0, Long.MAX_VALUE).durationMinutes());
    }
}
