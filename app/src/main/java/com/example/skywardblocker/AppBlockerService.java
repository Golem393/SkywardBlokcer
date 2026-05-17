package com.example.skywardblocker;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.List;

public class AppBlockerService extends AccessibilityService {

    // IMPORTANT: This must exactly match the label of your app in the Android settings
    private static final String APP_NAME = "SkywardBlocker";

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
                performGlobalAction(GLOBAL_ACTION_HOME);
                Toast.makeText(this, "Instagram is blocked.", Toast.LENGTH_SHORT).show();
                return;
            }

            // --- 2. Defend the App (Block access to Accessibility and App Info pages) ---
            if (pkg.equals("com.android.settings")) {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();

                if (rootNode != null) {
                    // Scan the screen for text matching your app's name
                    List<AccessibilityNodeInfo> foundNodes = rootNode.findAccessibilityNodeInfosByText(APP_NAME);

                    if (foundNodes != null && !foundNodes.isEmpty()) {
                        // The user is on a settings screen that contains your app's name. Kick them out.
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