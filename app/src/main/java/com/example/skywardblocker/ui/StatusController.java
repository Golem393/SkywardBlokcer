package com.example.skywardblocker.ui;

import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import com.example.skywardblocker.admin.DevicePolicyHelper;
import com.example.skywardblocker.api.ApiClient;
import com.example.skywardblocker.blocking.CategoryManager;
import com.example.skywardblocker.schedule.GracePeriodAlarmScheduler;
import com.example.skywardblocker.schedule.GracePeriodManager;
import com.example.skywardblocker.schedule.ScheduleManager;
import com.example.skywardblocker.time.TrustedTimeManager;
import java.util.Locale;

/**
 * Controller responsible for all business logic, countdown timers, state management,
 * and input validation for the Status dashboard. Keeps the Jetpack Compose UI purely stateless.
 */
public class StatusController {
    private static final String TAG = "StatusController";
    private static final long MAINTENANCE_DURATION_MS = 600_000L; // 10 minutes
    private static final long TICK_INTERVAL_MS = 1_000L;
    private static final long SCHEDULE_TICK_INTERVAL_MS = 30_000L;

    private final Context context;
    private final DevicePolicyHelper dph;
    private final Runnable onCloseClickedCallback;

    private StatusUiState currentState;
    private StateChangeListener stateChangeListener;
    private CountDownTimer countdownTimer;
    private CountDownTimer graceCountdownTimer;
    private final Handler scheduleTickHandler = new Handler(Looper.getMainLooper());
    private final Runnable scheduleTick = new Runnable() {
        @Override
        public void run() {
            refreshScheduleState();
            scheduleTickHandler.postDelayed(this, SCHEDULE_TICK_INTERVAL_MS);
        }
    };

    public interface StateChangeListener {
        void onStateChanged(StatusUiState newState);
    }

    public StatusController(Context context, DevicePolicyHelper dph, Runnable onCloseClickedCallback) {
        this.context = context;
        this.dph = dph;
        this.onCloseClickedCallback = onCloseClickedCallback;
        this.currentState = new StatusUiState(
                dph.isDeviceOwner(),
                MaintenanceState.NORMAL,
                "",
                "",
                false,
                "10:00 remaining"
        );
        refreshScheduleState();
        // Periodic ticking is started/stopped by the host Activity's onResume/onPause
        // (see startScheduleTicking/stopScheduleTicking) so it only runs while the
        // status screen is actually visible, not for as long as the Activity merely
        // exists in memory.
    }

    /** Call from the host Activity's onResume — (re)starts the periodic countdown refresh. */
    public void startScheduleTicking() {
        scheduleTickHandler.removeCallbacks(scheduleTick);
        refreshScheduleState();
        scheduleTickHandler.postDelayed(scheduleTick, SCHEDULE_TICK_INTERVAL_MS);
    }

    /** Call from the host Activity's onPause — stops ticking while the screen isn't visible. */
    public void stopScheduleTicking() {
        scheduleTickHandler.removeCallbacks(scheduleTick);
    }

    public void setStateChangeListener(StateChangeListener listener) {
        this.stateChangeListener = listener;
        if (listener != null) {
            listener.onStateChanged(currentState);
        }
    }

    public StatusUiState getCurrentState() {
        return currentState;
    }

    private void updateState(StatusUiState newState) {
        this.currentState = newState;
        if (stateChangeListener != null) {
            stateChangeListener.onStateChanged(currentState);
        }
    }

    // ── Action Handlers ───────────────────────────────────────────────────

    public void onStartLoginClicked() {
        Log.d(TAG, "onStartLoginClicked: transitioning to LOGIN mode");
        updateState(currentState.copy(MaintenanceState.LOGIN, "", "", false, "10:00 remaining"));
    }

    public void onEmailChanged(String email) {
        boolean valid = !email.trim().isEmpty() && !currentState.getPassword().trim().isEmpty();
        updateState(currentState.copy(currentState.getMaintenanceState(), email, currentState.getPassword(), valid, currentState.getFormattedRemainingTime()));
    }

    public void onPasswordChanged(String password) {
        boolean valid = !currentState.getEmail().trim().isEmpty() && !password.trim().isEmpty();
        updateState(currentState.copy(currentState.getMaintenanceState(), currentState.getEmail(), password, valid, currentState.getFormattedRemainingTime()));
    }

    public void onCancelLoginClicked() {
        Log.d(TAG, "onCancelLoginClicked: returning to NORMAL mode");
        updateState(currentState.copy(MaintenanceState.NORMAL, "", "", false, "10:00 remaining"));
    }

    public void onLoginAndUnlockClicked() {
        if (!currentState.isLoginButtonEnabled()) {
            Log.w(TAG, "onLoginAndUnlockClicked: inputs invalid, ignored");
            return;
        }
        Log.i(TAG, "Starting authentication for Dev Mode...");
        updateState(currentState.copy(currentState.getMaintenanceState(), currentState.getEmail(), currentState.getPassword(), false, currentState.getFormattedRemainingTime()));

        ApiClient.authenticateSetup(context, currentState.getEmail(), currentState.getPassword(), new ApiClient.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.i(TAG, "Authentication successful: unlocking USB debugging for 10 minutes");
                    dph.setTemporaryDebugging(true);
                    startMaintenanceTimer();
                });
            }

            @Override
            public void onError(String errorMessage) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.w(TAG, "Authentication failed: " + errorMessage);
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
                    updateState(currentState.copy(currentState.getMaintenanceState(), currentState.getEmail(), currentState.getPassword(), true, currentState.getFormattedRemainingTime()));
                });
            }
        });
    }

    public void onLockNowClicked() {
        Log.i(TAG, "onLockNowClicked: manually locking USB debugging and exiting maintenance mode");
        lockAndExit();
    }

    public void onCloseAppClicked() {
        if (onCloseClickedCallback != null) {
            onCloseClickedCallback.run();
        }
    }

    // ── Schedule status ────────────────────────────────────────────────────

    /**
     * Recomputes the current schedule lock state and refreshes the UI. Safe to call
     * repeatedly (e.g. from a periodic UI tick) — it never mutates the schedule itself.
     */
    public void refreshScheduleState() {
        boolean enabled = ScheduleManager.isScheduleEnabled(context);
        boolean locked = enabled && ScheduleManager.isCurrentlyLocked(context);
        String text = buildScheduleStatusText(enabled, locked);

        boolean blockingScheduled = ScheduleManager.isBlockingScheduled(context);
        boolean graceActive = GracePeriodManager.isGraceActive(context);
        int graceUsesRemaining = GracePeriodManager.usesRemainingToday(context);
        String graceText = graceActive
                ? "Unlocked for " + formatMmSs(GracePeriodManager.remainingMillis(context))
                : "";

        if (graceActive && graceCountdownTimer == null) {
            startGraceCountdown(GracePeriodManager.remainingMillis(context));
        }

        updateState(currentState.withSchedule(enabled, locked, text)
                .withGrace(blockingScheduled, graceActive, graceText, graceUsesRemaining));
    }

    /**
     * Child manually opts back into locked mode early, during an otherwise-unlocked
     * schedule window.
     */
    public void onScheduleLockNowClicked() {
        Log.i(TAG, "onScheduleLockNowClicked: child manually opted back into locked mode early");
        ScheduleManager.setManualOverride(context, true);
        CategoryManager.applyEnforcement(context);
        refreshScheduleState();
    }

    /**
     * Starts a short grace period that temporarily lifts blocking, up to
     * GracePeriodManager.MAX_USES_PER_DAY times per day. No-ops if the quota is already
     * used up, a grace period is already active, or there's no trusted-time anchor yet.
     */
    public void onUnlockBreakClicked() {
        if (!GracePeriodManager.startGrace(context)) {
            Log.w(TAG, "onUnlockBreakClicked: cannot start grace period right now");
            return;
        }
        Log.i(TAG, "onUnlockBreakClicked: grace period started");
        CategoryManager.applyEnforcement(context);
        GracePeriodAlarmScheduler.scheduleGraceEnd(context, GracePeriodManager.DURATION_MS);
        refreshScheduleState();
    }

    private String buildScheduleStatusText(boolean enabled, boolean locked) {
        if (!enabled) return "";

        if (ScheduleManager.isExpired(context)) {
            return "Schedule finished";
        }

        if (locked && ScheduleManager.isFullDayLock(context)) {
            return "Blocked 24 hours";
        }

        int[] boundary = locked ? ScheduleManager.getLockEnd(context) : ScheduleManager.getLockStart(context);
        String time = String.format(Locale.US, "%02d:%02d", boundary[0], boundary[1]);
        String action = locked ? "Unlocks" : "Locks";

        Long trustedNow = TrustedTimeManager.getCurrentTrustedEpochMillis(context);
        if (trustedNow == null) {
            // No trusted time yet — can't compute a countdown, fall back to just the clock time.
            return (locked ? "Locked until " : "Unlocked until ") + time;
        }

        long millisRemaining = ScheduleManager.millisUntilNextBoundary(context, trustedNow);
        if (millisRemaining == ScheduleManager.NO_BOUNDARY) {
            // Nothing left in the block period — the schedule has run its course.
            return "Schedule finished";
        }
        return action + " in " + formatCountdown(millisRemaining) + " (at " + time + ")";
    }

    private String formatCountdown(long millis) {
        long totalMinutes = Math.max(0, millis / 60_000L);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String formatMmSs(long millis) {
        long totalSeconds = Math.max(0, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    /**
     * Drives a 1-second-resolution UI countdown for the active grace period. Re-attaches
     * automatically from refreshScheduleState() if the screen resumes mid-grace, so it
     * isn't limited to only the moment the button was tapped.
     */
    private void startGraceCountdown(long durationMillis) {
        cancelGraceTimer();
        graceCountdownTimer = new CountDownTimer(durationMillis, TICK_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                refreshScheduleState();
            }

            @Override
            public void onFinish() {
                graceCountdownTimer = null;
                Log.i(TAG, "Grace period ended: resuming enforcement");
                CategoryManager.applyEnforcement(context);
                GracePeriodAlarmScheduler.cancel(context);
                refreshScheduleState();
            }
        }.start();
    }

    private void cancelGraceTimer() {
        if (graceCountdownTimer != null) {
            graceCountdownTimer.cancel();
            graceCountdownTimer = null;
        }
    }

    // ── Timer Management ──────────────────────────────────────────────────

    private void startMaintenanceTimer() {
        cancelTimer();
        updateState(currentState.copy(MaintenanceState.TIMER_ACTIVE, "", "", false, "10:00 remaining"));

        countdownTimer = new CountDownTimer(MAINTENANCE_DURATION_MS, TICK_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSeconds = millisUntilFinished / 1000L;
                long minutes = totalSeconds / 60L;
                long seconds = totalSeconds % 60L;
                String timeFormatted = String.format(Locale.US, "%02d:%02d remaining", minutes, seconds);
                updateState(currentState.copy(MaintenanceState.TIMER_ACTIVE, "", "", false, timeFormatted));
            }

            @Override
            public void onFinish() {
                Log.i(TAG, "Maintenance window expired: automatically locking USB debugging");
                lockAndExit();
            }
        }.start();
    }

    private void lockAndExit() {
        cancelTimer();
        dph.setTemporaryDebugging(false);
        updateState(currentState.copy(MaintenanceState.NORMAL, "", "", false, "10:00 remaining"));
    }

    private void cancelTimer() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    /**
     * Called when host Activity is destroyed to release background timers.
     */
    public void cleanup() {
        cancelTimer();
        cancelGraceTimer();
        scheduleTickHandler.removeCallbacks(scheduleTick);
        this.stateChangeListener = null;
    }
}
