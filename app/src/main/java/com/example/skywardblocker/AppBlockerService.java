package com.example.skywardblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class AppBlockerService extends AccessibilityService {

    private long lastBlockTime = 0;
    private static final long COOLDOWN_MS = 500;

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

        // --- 1. Block Instagram ---
        if (pkg.equals("com.instagram.android")) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBlockTime < COOLDOWN_MS) return; // Add check here
            lastBlockTime = currentTime;

            // Fire the home command immediately
            performGlobalAction(GLOBAL_ACTION_HOME);

            // Delay the popup launch so it doesn't get swallowed by the Home transition
            launchBlockScreen(pkg);
            return;
        }

        // --- 2. Defend the App ---
        if (pkg.equals("com.android.settings")) {
            // Dynamically get your app's name to make it language-invariant
            String appName = getString(R.string.app_name);

            // Trigger 1: The Click
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                AccessibilityNodeInfo source = event.getSource();
                String sourceText = (source != null && source.getText() != null) ? source.getText().toString() : "";
                String eventTextStr = event.getText() != null ? event.getText().toString() : "";

                if (sourceText.contains(appName) || eventTextStr.contains(appName)) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastBlockTime < COOLDOWN_MS) return; // Add check here
                    lastBlockTime = currentTime;
                    launchSettingsDefenseScreen();
                    return;
                }
            }

            // Trigger 2: The Window Title / Screen Open
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // Check event text first (often contains the window title)
                for (CharSequence text : event.getText()) {
                    if (text != null && text.toString().contains(appName)) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastBlockTime < COOLDOWN_MS) return; // Add check here
                        lastBlockTime = currentTime;
                        launchSettingsDefenseScreen();
                        return;
                    }
                }

                // Fallback: Scan the active window for your app name
                /*AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(appName);
                    if (nodes != null && !nodes.isEmpty()) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastBlockTime < COOLDOWN_MS) {
                            rootNode.recycle();
                            return; // Add check here
                        }
                        lastBlockTime = currentTime;
                        launchSettingsDefenseScreen();
                    }
                    rootNode.recycle();
                }*/
            }
        }
    }

    private void launchBlockScreen(String blockedPackage) {
        // Wait 300ms for the Home screen transition to finish
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, AppBlockedActivity.class);
            intent.putExtra("BLOCKED_PACKAGE", blockedPackage);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }, 300);
    }

    private void launchSettingsDefenseScreen() {
        // Nuke Settings immediately
        Intent resetSettingsIntent = new Intent(Settings.ACTION_SETTINGS);
        resetSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(resetSettingsIntent);

        // Wait 300ms before throwing the defense popup
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, SettingsBlockedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }, 300);
    }

    @Override
    public void onInterrupt() {
    }
}