package com.example.skywardblocker.ui;

/**
 * Immutable model holding all state required by the status UI screen.
 * All formatting, authentication logic, and timer calculations are evaluated in Java before creation.
 */
public class StatusUiState {
    private final boolean isDeviceOwner;
    private final MaintenanceState maintenanceState;
    private final String email;
    private final String password;
    private final boolean isLoginButtonEnabled;
    private final String formattedRemainingTime;
    private final boolean scheduleEnabled;
    private final boolean scheduleCurrentlyLocked;
    private final String scheduleStatusText;
    private final boolean blockingScheduled;
    private final boolean graceActive;
    private final String graceStatusText;
    private final int graceUsesRemaining;

    public StatusUiState(boolean isDeviceOwner, MaintenanceState maintenanceState, String email, String password, boolean isLoginButtonEnabled, String formattedRemainingTime) {
        this(isDeviceOwner, maintenanceState, email, password, isLoginButtonEnabled, formattedRemainingTime, false, false, "");
    }

    public StatusUiState(boolean isDeviceOwner, MaintenanceState maintenanceState, String email, String password, boolean isLoginButtonEnabled, String formattedRemainingTime, boolean scheduleEnabled, boolean scheduleCurrentlyLocked, String scheduleStatusText) {
        this(isDeviceOwner, maintenanceState, email, password, isLoginButtonEnabled, formattedRemainingTime, scheduleEnabled, scheduleCurrentlyLocked, scheduleStatusText, false, false, "", 0);
    }

    public StatusUiState(boolean isDeviceOwner, MaintenanceState maintenanceState, String email, String password, boolean isLoginButtonEnabled, String formattedRemainingTime, boolean scheduleEnabled, boolean scheduleCurrentlyLocked, String scheduleStatusText, boolean blockingScheduled, boolean graceActive, String graceStatusText, int graceUsesRemaining) {
        this.isDeviceOwner = isDeviceOwner;
        this.maintenanceState = maintenanceState;
        this.email = email != null ? email : "";
        this.password = password != null ? password : "";
        this.isLoginButtonEnabled = isLoginButtonEnabled;
        this.formattedRemainingTime = formattedRemainingTime != null ? formattedRemainingTime : "10:00 remaining";
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

    public MaintenanceState getMaintenanceState() {
        return maintenanceState;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public boolean isLoginButtonEnabled() {
        return isLoginButtonEnabled;
    }

    public String getFormattedRemainingTime() {
        return formattedRemainingTime;
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

    public StatusUiState copy(MaintenanceState newMaintenanceState, String newEmail, String newPassword, boolean newIsLoginButtonEnabled, String newFormattedRemainingTime) {
        return new StatusUiState(this.isDeviceOwner, newMaintenanceState, newEmail, newPassword, newIsLoginButtonEnabled, newFormattedRemainingTime, this.scheduleEnabled, this.scheduleCurrentlyLocked, this.scheduleStatusText, this.blockingScheduled, this.graceActive, this.graceStatusText, this.graceUsesRemaining);
    }

    public StatusUiState withSchedule(boolean newScheduleEnabled, boolean newScheduleCurrentlyLocked, String newScheduleStatusText) {
        return new StatusUiState(this.isDeviceOwner, this.maintenanceState, this.email, this.password, this.isLoginButtonEnabled, this.formattedRemainingTime, newScheduleEnabled, newScheduleCurrentlyLocked, newScheduleStatusText, this.blockingScheduled, this.graceActive, this.graceStatusText, this.graceUsesRemaining);
    }

    public StatusUiState withGrace(boolean newBlockingScheduled, boolean newGraceActive, String newGraceStatusText, int newGraceUsesRemaining) {
        return new StatusUiState(this.isDeviceOwner, this.maintenanceState, this.email, this.password, this.isLoginButtonEnabled, this.formattedRemainingTime, this.scheduleEnabled, this.scheduleCurrentlyLocked, this.scheduleStatusText, newBlockingScheduled, newGraceActive, newGraceStatusText, newGraceUsesRemaining);
    }
}
