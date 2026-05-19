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

    private long lastEventTime = 0;
    private static final long COOLDOWN_MS = 500;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEventTime < COOLDOWN_MS) {
            return;
        }

        int eventType = event.getEventType();

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        CharSequence packageName = event.getPackageName();
        if (packageName == null) return;
        String pkg = packageName.toString();

        // --- 1. Block Instagram ---
        if (pkg.equals("com.instagram.android")) {
            lastEventTime = currentTime;

            // Fire the home command immediately
            performGlobalAction(GLOBAL_ACTION_HOME);

            // Delay the popup launch so it doesn't get swallowed by the Home transition
            launchBlockScreen(pkg);
            return;
        }

        // --- 2. Defend the App ---
        if (pkg.equals("com.android.settings")) {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();

            if (rootNode != null) {
                List<AccessibilityNodeInfo> detailScreenNodes = rootNode.findAccessibilityNodeInfosByText("SkywardBlocker shortcut");
                List<AccessibilityNodeInfo> dialogNodes = rootNode.findAccessibilityNodeInfosByText("Turn off SkywardBlocker?");

                if ((detailScreenNodes != null && !detailScreenNodes.isEmpty()) ||
                        (dialogNodes != null && !dialogNodes.isEmpty())) {

                    lastEventTime = currentTime;
                    launchSettingsDefenseScreen();
                }
                rootNode.recycle();
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