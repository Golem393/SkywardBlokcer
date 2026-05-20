package com.example.skywardblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class AppBlockerService extends AccessibilityService {

    private long lastBlockTime = 0;
    private long lastSetupForceTime = 0;
    private static final long COOLDOWN_MS = 500;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (!StateManager.isSetupComplete(this)) {
            forceUserBackToSetup();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return;
        }

        CharSequence packageName = event.getPackageName();
        if (packageName == null) return;
        String pkg = packageName.toString();

        // --- TRAP THE USER DURING SETUP ---
        if (!StateManager.isSetupComplete(this)) {
            if (!isAllowedSetupPackage(pkg)) {
                forceUserBackToSetup();
            }
            return; // Do not execute standard blocks while setup is incomplete
        }

        // --- 1. Block Instagram ---
        if (pkg.equals("com.instagram.android")) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBlockTime < COOLDOWN_MS) return;
            lastBlockTime = currentTime;

            performGlobalAction(GLOBAL_ACTION_HOME);
            launchBlockScreen(pkg);
            return;
        }

        // --- 2. Defend the App (Only active after Setup is complete) ---
        if (pkg.equals("com.android.settings")) {
            String appName = getString(R.string.app_name);

            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                AccessibilityNodeInfo source = event.getSource();
                String sourceText = (source != null && source.getText() != null) ? source.getText().toString() : "";
                String eventTextStr = event.getText() != null ? event.getText().toString() : "";

                if (sourceText.contains(appName) || eventTextStr.contains(appName)) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastBlockTime < COOLDOWN_MS) return;
                    lastBlockTime = currentTime;
                    launchSettingsDefenseScreen();
                    return;
                }
            }

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                for (CharSequence text : event.getText()) {
                    if (text != null && text.toString().contains(appName)) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastBlockTime < COOLDOWN_MS) return;
                        lastBlockTime = currentTime;
                        launchSettingsDefenseScreen();
                        return;
                    }
                }
            }
        }
    }

    // Identifies packages the user is allowed to be in during the setup steps
    private boolean isAllowedSetupPackage(String pkg) {
        if (pkg.equals(getPackageName())) return true;
        if (pkg.contains("settings")) return true; // Allows all OEM setting apps
        if (pkg.contains("systemui")) return true; // Allows navigation bar clicks
        if (pkg.contains("permissioncontroller")) return true; // Pixel role manager
        if (pkg.contains("rolemanager")) return true; // Android 10+ default app dialog
        return false;
    }

    // Aggressively pulls the user back to the app if they try to press home/leave
        // Aggressively pulls the user back to the app if they try to press home/leave
    private void forceUserBackToSetup() {
        long currentTime = System.currentTimeMillis();
        // Increase cooldown slightly to prevent overlapping delayed launches
        if (currentTime - lastSetupForceTime < 2000) return;
        lastSetupForceTime = currentTime;

        // 1000ms delay bypasses Android's strict "Stop App Switches" home button block
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            // SINGLE_TOP and CLEAR_TOP are required to bypass Android 10+ background launch restrictions
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }, 1000);
    }

    private void launchBlockScreen(String blockedPackage) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, AppBlockedActivity.class);
            intent.putExtra("BLOCKED_PACKAGE", blockedPackage);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }, 300);
    }

    private void launchSettingsDefenseScreen() {
        Intent resetSettingsIntent = new Intent(Settings.ACTION_SETTINGS);
        resetSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(resetSettingsIntent);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, SettingsBlockedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }, 300);
    }

    @Override
    public void onInterrupt() {}
}