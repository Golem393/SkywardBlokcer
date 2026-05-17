package com.example.skywardblocker;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class AppBlockerService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence packageName = event.getPackageName();

            if (packageName != null && packageName.toString().equals("com.instagram.android")) {

                // Instantly force the user back to the home screen
                performGlobalAction(GLOBAL_ACTION_HOME);

                // Show a quick message so they know it wasn't an app crash
                Toast.makeText(this, "Instagram is blocked by policy.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Required to be overridden, but you don't need to put anything here.
    }
}