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

    public StatusUiState(boolean isDeviceOwner, MaintenanceState maintenanceState, String email, String password, boolean isLoginButtonEnabled, String formattedRemainingTime) {
        this.isDeviceOwner = isDeviceOwner;
        this.maintenanceState = maintenanceState;
        this.email = email != null ? email : "";
        this.password = password != null ? password : "";
        this.isLoginButtonEnabled = isLoginButtonEnabled;
        this.formattedRemainingTime = formattedRemainingTime != null ? formattedRemainingTime : "10:00 remaining";
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

    public StatusUiState copy(MaintenanceState newMaintenanceState, String newEmail, String newPassword, boolean newIsLoginButtonEnabled, String newFormattedRemainingTime) {
        return new StatusUiState(this.isDeviceOwner, newMaintenanceState, newEmail, newPassword, newIsLoginButtonEnabled, newFormattedRemainingTime);
    }
}
