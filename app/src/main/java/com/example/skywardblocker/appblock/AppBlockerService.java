package com.example.skywardblocker.appblock;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.example.skywardblocker.R;
import com.example.skywardblocker.StateManager;
import com.example.skywardblocker.dns.DnsAutoSetupScript;

import android.content.IntentFilter;
import java.util.HashMap;
import java.util.Map;

public class AppBlockerService extends AccessibilityService {

    private static final String TAG = "SkywardDebug";

    private static final int TARGET_EVENTS_MASK = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

    // Per-package cooldown to prevent spam-blocking the same app
    private static final long BLOCK_COOLDOWN_MS = 2000;
    private final Map<String, Long> lastBlockedTimes = new HashMap<>();

    // Tracks whether a WarningActivity is currently showing.
    // Only cleared when the user explicitly dismisses it.
    private static volatile boolean isWarningShowing = false;
    private static String lastDroppedPackage = "";

    private DnsAutoSetupScript dnsAutoSetupScript;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PackageChangeReceiver packageChangeReceiver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        com.example.skywardblocker.StateManager.AppState currentState = com.example.skywardblocker.StateManager.getState(getApplicationContext());
        Log.d(TAG, "onServiceConnected: currentState = " + currentState);

        if (currentState != com.example.skywardblocker.StateManager.AppState.BLOCKING) {
            Intent intent = new Intent(this, com.example.skywardblocker.MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }

        dnsAutoSetupScript = new DnsAutoSetupScript();

        // Dynamically register PackageChangeReceiver for Android 8.0+
        packageChangeReceiver = new PackageChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(packageChangeReceiver, filter);

        // Initialize the app category cache (bulk fetch + scan installed apps)
        CategoryManager.initializeCache(getApplicationContext());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || (event.getEventType() & TARGET_EVENTS_MASK) == 0) return;

        CharSequence packageNameChar = event.getPackageName();
        if (packageNameChar == null) return;

        String pkg = packageNameChar.toString();

        // Ignore our own package and system UI
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;

        // --- Auto DNS Setup ---
        boolean autoConfigureDns = getSharedPreferences("skyward_prefs", MODE_PRIVATE)
                .getBoolean("auto_configure_dns", false);
        if (autoConfigureDns) {
            Log.d(TAG, "Auto DNS is active, saw package: " + pkg);
            if (pkg.equals("com.android.settings")) {
                if (dnsAutoSetupScript == null) dnsAutoSetupScript = new DnsAutoSetupScript();
                // Ensure it's not permanently finished from a previous attempt
                dnsAutoSetupScript.reset();
                dnsAutoSetupScript.processEvent(event, this);
                return; // consume event and don't block
            }
        }

        // Only block when in BLOCKING state
        if (StateManager.getState(this) != StateManager.AppState.BLOCKING) return;

        // If a WarningActivity is already showing, drop all events.
        // This is the key fix: we don't re-trigger while warning is visible.
        if (isWarningShowing) {
            if (!pkg.equals(lastDroppedPackage)) {
                Log.d(TAG, "DROP | WarningActivity already showing, ignoring pkg=" + pkg);
                lastDroppedPackage = pkg;
            }
            return;
        }

        // --- Settings defense: keep WarningActivity for this special case ---
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && pkg.equals("com.android.settings") && event.getText() != null) {

            String appName = getString(R.string.app_name);
            boolean isDefending = false;

            for (CharSequence text : event.getText()) {
                if (text != null && (text.toString().contains(appName) || text.toString().toLowerCase().contains("private dns"))) {
                    isDefending = true;
                    break;
                }
            }

            if (isDefending) {
                Log.d(TAG, "SETTINGS DEFENSE | blocking settings modification attempt");
                triggerBlock(pkg, "Access Denied", "You do not have permission to modify these parameters.", true);
                return;
            }
        }

        // --- Regular app blocking ---
        // For TYPE_WINDOW_CONTENT_CHANGED, ignore content description changes (noisy, irrelevant)
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && event.getContentChangeTypes() == AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION) {
            return;
        }

        // Check if app is in a blocked category
        if (CategoryManager.isAppInBlockedCategory(this, pkg)) {
            // Per-package cooldown: don't re-block the same package within 2 seconds
            Long lastBlocked = lastBlockedTimes.get(pkg);
            long now = System.currentTimeMillis();
            if (lastBlocked != null && (now - lastBlocked) < BLOCK_COOLDOWN_MS) {
                Log.d(TAG, "COOLDOWN SKIP | pkg=" + pkg + " | blocked " + (now - lastBlocked) + "ms ago");
                return;
            }

            Log.d(TAG, "BLOCK | pkg=" + pkg + " | sending home then showing warning");
            lastBlockedTimes.put(pkg, now);

            triggerBlock(pkg, "App Blocked", "This category of app is restricted by SkywardBlocker.", false);
        }
    }

    /**
     * Shows the WarningActivity on top of the blocked app immediately.
     */
    private void triggerBlock(String blockedPackage, String title, String message, boolean isSettings) {
        if (isSettings) {
            // Go back one option out of the blocked settings page so reopening Settings doesn't re-trigger
            performGlobalAction(GLOBAL_ACTION_BACK);
            // After a tiny delay, send the user home
            handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 150);
        } else {
            // Step 1: Press Home to kill the blocked app's UI immediately
            performGlobalAction(GLOBAL_ACTION_HOME);
        }

        // Step 2: After a short delay (let home screen settle), show WarningActivity
        long warningDelay = isSettings ? 450 : 300;
        handler.postDelayed(() -> {
            Intent dialogIntent = new Intent(this, WarningActivity.class);
            dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            dialogIntent.putExtra("blocked_package", blockedPackage);
            dialogIntent.putExtra("title", title);
            dialogIntent.putExtra("message", message);
            dialogIntent.putExtra("is_settings", isSettings);
            startActivity(dialogIntent);
        }, warningDelay);

    }

    /**
     * Called by WarningActivity when it successfully shows up.
     */
    public static void onWarningShown() {
        isWarningShowing = true;
    }

    /**
     * Called by WarningActivity when the user dismisses it.
     * Only then do we allow new blocking events to be processed.
     */
    public static void onWarningDismissed() {
        isWarningShowing = false;
        lastDroppedPackage = "";
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (packageChangeReceiver != null) {
            try {
                unregisterReceiver(packageChangeReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
    }
}