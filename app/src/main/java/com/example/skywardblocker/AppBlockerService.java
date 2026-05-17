package com.example.skywardblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.List;
import android.util.Log;

public class AppBlockerService extends AccessibilityService {

    private static final String TAG = "BlockerDebug";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            CharSequence packageName = event.getPackageName();
            if (packageName == null) return;

            String pkg = packageName.toString();

            // --- 1. Block Instagram ---
            if (pkg.equals("com.instagram.android")) {
                Log.d(TAG, "BLOCKED: Kicking user out of Instagram");
                performGlobalAction(GLOBAL_ACTION_HOME);
                Toast.makeText(this, "Instagram is blocked.", Toast.LENGTH_SHORT).show();
                return;
            }

            // --- 2. Defend the App (Block the detail page and the turn-off dialog) ---
            if (pkg.equals("com.android.settings")) {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();

                if (rootNode != null) {

                    List<AccessibilityNodeInfo> detailScreenNodes = rootNode.findAccessibilityNodeInfosByText("SkywardBlocker shortcut");
                    List<AccessibilityNodeInfo> dialogNodes = rootNode.findAccessibilityNodeInfosByText("Turn off SkywardBlocker?");

                    if ((detailScreenNodes != null && !detailScreenNodes.isEmpty()) ||
                            (dialogNodes != null && !dialogNodes.isEmpty())) {

                        Log.d(TAG, "DEFENSE TRIGGERED: Resetting Settings app and going Home.");

                        // STEP A: Reset the Settings app back to its main menu in the background
                        Intent resetSettingsIntent = new Intent(Settings.ACTION_SETTINGS);
                        resetSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(resetSettingsIntent);

                        // STEP B: Force the user back to the Home screen
                        performGlobalAction(GLOBAL_ACTION_HOME);

                        Toast.makeText(this, "Security settings locked.", Toast.LENGTH_SHORT).show();
                    }
                    rootNode.recycle();
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}