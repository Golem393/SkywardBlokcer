package com.example.skywardblocker.appblock;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

import com.example.skywardblocker.R;
import com.example.skywardblocker.StateManager;

public class AppBlockerService extends AccessibilityService {

    private WindowManager windowManager;
    private View overlayView;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        // The moment the user enables the service in Android Settings,
        // fire an intent to snap them immediately back into your app.
        Intent intent = new Intent(this, com.example.skywardblocker.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return;
        }

        CharSequence packageName = event.getPackageName();
        if (packageName == null) return;
        String pkg = packageName.toString();

        if (StateManager.getState(this) != StateManager.AppState.BLOCKING) {
            return;
        }

        // --- 1. Block Instagram ---
        if (pkg.equals("com.instagram.android")) {
            showOverlay(pkg, "App Blocked", "This app was blocked by SkywardBlocker.", false);
            return;
        }

        // --- 2. Defend the App ---
        if (pkg.equals("com.android.settings")) {
            String appName = getString(R.string.app_name);
            boolean isDefending = false;

            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                AccessibilityNodeInfo source = event.getSource();
                String sourceText = (source != null && source.getText() != null) ? source.getText().toString() : "";
                String eventTextStr = event.getText() != null ? event.getText().toString() : "";

                if (sourceText.contains(appName) || eventTextStr.contains(appName)) {
                    isDefending = true;
                }
            }

            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                for (CharSequence text : event.getText()) {
                    if (text != null && text.toString().contains(appName)) {
                        isDefending = true;
                    }
                }
            }

            if (isDefending) {
                showOverlay(pkg, "Access Denied", "You don't have the permission to change these settings.", true);
                return;
            }
        }

        // If the user is on any other app (like the Home Screen), remove the overlay
        if (!pkg.equals("com.instagram.android") && !pkg.equals("com.android.settings")) {
            removeOverlay();
        }
    }

    private void showOverlay(String blockedPackage, String title, String message, boolean isSettings) {
        if (overlayView != null) return;

        overlayView = LayoutInflater.from(this).inflate(R.layout.activity_main, null);
        // ADD THIS BLOCK: Forces the overlay to hide the top status bar and bottom nav bar
        overlayView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );


        TextView titleText = overlayView.findViewById(R.id.titleText);
        TextView messageText = overlayView.findViewById(R.id.messageText);
        Button closeButton = overlayView.findViewById(R.id.closeButton);
        Button actionButton = overlayView.findViewById(R.id.actionButton);

        if (titleText != null) titleText.setText(title);
        if (messageText != null) messageText.setText(message);

        // UI Logic just like your old Activities
        if (isSettings) {
            if (actionButton != null) actionButton.setVisibility(View.GONE);
        } else {
            if (actionButton != null) {
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText("Uninstall App");
                actionButton.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(android.net.Uri.parse("package:" + blockedPackage));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    removeOverlay();
                });
            }
        }

        closeButton.setOnClickListener(v -> {
            performGlobalAction(GLOBAL_ACTION_HOME);
            removeOverlay();
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.OPAQUE // CHANGED from TRANSLUCENT
        );

        windowManager.addView(overlayView, params);
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    @Override
    public void onInterrupt() {
        removeOverlay();
    }
}