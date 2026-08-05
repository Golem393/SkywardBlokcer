package com.example.skywardblocker.ui;

/**
 * Immutable model holding all state required by the status UI screen.
 * All formatting and state calculations are evaluated in Java before creation.
 */
public class StatusUiState {
    private final boolean isDeviceOwner;
    private final boolean scheduleEnabled;
    private final boolean scheduleCurrentlyLocked;
    private final String scheduleStatusText;
    private final boolean blockingScheduled;
    private final boolean graceActive;
    private final String graceStatusText;
    private final int graceUsesRemaining;

    public StatusUiState(boolean isDeviceOwner) {
        this(isDeviceOwner, false, false, "", false, false, "", 0);
    }

    public StatusUiState(boolean isDeviceOwner, boolean scheduleEnabled, boolean scheduleCurrentlyLocked, String scheduleStatusText, boolean blockingScheduled, boolean graceActive, String graceStatusText, int graceUsesRemaining) {
        this.isDeviceOwner = isDeviceOwner;
        this.scheduleEnabled = scheduleEnabled;
        this.scheduleCurrentlyLocked = scheduleCurrentlyLocked;
        this.scheduleStatusText = scheduleStatusText != null ? scheduleStatusText : "";
        this.blockingScheduled = blockingScheduled;
        this.graceActive = graceActive;
        this.graceStatusText = graceStatusText != null ? graceStatusText : "";
        this.graceUsesRemaining = graceUsesRemaining;
    }

    public boolean isDeviceOwner() {
        return isDeviceOwner;
    }

    public boolean isScheduleEnabled() {
        return scheduleEnabled;
    }

    public boolean isScheduleCurrentlyLocked() {
        return scheduleCurrentlyLocked;
    }

    public String getScheduleStatusText() {
        return scheduleStatusText;
    }

    public boolean isBlockingScheduled() {
        return blockingScheduled;
    }

    public boolean isGraceActive() {
        return graceActive;
    }

    public String getGraceStatusText() {
        return graceStatusText;
    }

    public int getGraceUsesRemaining() {
        return graceUsesRemaining;
    }

    public StatusUiState withSchedule(boolean newScheduleEnabled, boolean newScheduleCurrentlyLocked, String newScheduleStatusText) {
        return new StatusUiState(this.isDeviceOwner, newScheduleEnabled, newScheduleCurrentlyLocked, newScheduleStatusText, this.blockingScheduled, this.graceActive, this.graceStatusText, this.graceUsesRemaining);
    }

    public StatusUiState withGrace(boolean newBlockingScheduled, boolean newGraceActive, String newGraceStatusText, int newGraceUsesRemaining) {
        return new StatusUiState(this.isDeviceOwner, this.scheduleEnabled, this.scheduleCurrentlyLocked, this.scheduleStatusText, newBlockingScheduled, newGraceActive, newGraceStatusText, newGraceUsesRemaining);
    }
}
