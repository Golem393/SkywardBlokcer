package com.example.skywardblocker;

public class SetupViewState {

    public final String title;
    public final String message;
    public final String actionButtonText;
    public final boolean isActionButtonVisible;
    public final boolean isCloseButtonVisible;
    public final Step currentStep;

    public enum Step {
        ENABLE_ACCESSIBILITY,
        SETUP_DNS,
        FINALIZE_API,
        COMPLETE
    }

    public SetupViewState(
            String title,
            String message,
            String actionButtonText,
            boolean isActionButtonVisible,
            boolean isCloseButtonVisible,
            Step currentStep) {

        this.title = title;
        this.message = message;
        this.actionButtonText = actionButtonText;
        this.isActionButtonVisible = isActionButtonVisible;
        this.isCloseButtonVisible = isCloseButtonVisible;
        this.currentStep = currentStep;
    }
}