package com.example.skywardblocker.ui;

import android.os.CountDownTimer;
import android.util.Log;
import com.example.skywardblocker.admin.DevicePolicyHelper;
import java.util.Locale;

/**
 * Controller responsible for all business logic, countdown timers, state management,
 * and input validation for the Status dashboard. Keeps the Jetpack Compose UI purely stateless.
 */
public class StatusController {
    private static final String TAG = "StatusController";
    private static final long MAINTENANCE_DURATION_MS = 600_000L; // 10 minutes
    private static final long TICK_INTERVAL_MS = 1_000L;

    private final DevicePolicyHelper dph;
    private final Runnable onCloseClickedCallback;

    private StatusUiState currentState;
    private StateChangeListener stateChangeListener;
    private CountDownTimer countdownTimer;

    public interface StateChangeListener {
        void onStateChanged(StatusUiState newState);
    }

    public StatusController(DevicePolicyHelper dph, Runnable onCloseClickedCallback) {
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
        Log.i(TAG, "Authentication successful: unlocking USB debugging for 10 minutes");
        dph.setTemporaryDebugging(true);
        startMaintenanceTimer();
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
        this.stateChangeListener = null;
    }
}
