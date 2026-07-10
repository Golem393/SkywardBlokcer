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
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.skywardblocker.R;
import com.example.skywardblocker.StateManager;
import com.example.skywardblocker.dns.DnsAutoSetupScript;

import android.content.IntentFilter;
import android.database.ContentObserver;
import android.provider.Settings;
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
    private ContentObserver dnsObserver;

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

        // Listen for DNS changes to immediately transition when user manually saves DNS
        dnsObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                checkDnsAndReturnToApp();
            }
        };
        try {
            getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor("private_dns_specifier"),
                    false,
                    dnsObserver
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to register DNS ContentObserver", e);
        }
    }

    private void checkDnsAndReturnToApp() {
        StateManager.AppState currentState = StateManager.getState(this);
        if (currentState == StateManager.AppState.DNS_MANUAL_SCREEN || currentState == StateManager.AppState.DNS_SCREEN) {
            if (DnsAutoSetupScript.isDnsConfigured(this)) {
                Log.d(TAG, "DNS configured correctly (detected via observer), returning to app.");
                
                // If auto script is running, disable it
                getSharedPreferences("skyward_prefs", MODE_PRIVATE)
                        .edit().putBoolean("auto_configure_dns", false).apply();
                if (dnsAutoSetupScript != null) {
                    dnsAutoSetupScript.reset();
                }

                Intent intent = new Intent(this, com.example.skywardblocker.MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || (event.getEventType() & TARGET_EVENTS_MASK) == 0) return;

        CharSequence packageNameChar = event.getPackageName();
        if (packageNameChar == null) return;

        String pkg = packageNameChar.toString();

        // Ignore our own package and system UI
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;

        Log.d(TAG, "EVENT TRIGGERED | pkg=" + pkg + " | eventType=" + event.getEventType());

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
            String dnsHostname = DnsAutoSetupScript.getDnsHostname(this);
            boolean isDefending = false;

            for (CharSequence text : event.getText()) {
                if (text != null) {
                    String textStr = text.toString();
                    if (textStr.contains(appName)) {
                        isDefending = true;
                        break;
                    }
                    if (dnsHostname != null && textStr.contains(dnsHostname)) {
                        isDefending = true;
                        break;
                    }
                }
            }

            if (isDefending) {
                Log.d(TAG, "SETTINGS DEFENSE | blocking settings modification attempt");
                triggerBlock(pkg, "Access Denied", "You do not have permission to modify these parameters.", true, false);
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

            triggerBlock(pkg, "App blocked", "This app is restricted by Skyward.", false, false);
            return;
        }

        // --- Domain blocking ---
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            String extractedDomain = findDomainInNode(root);
            if (extractedDomain != null) {
                if (DomainCategoryManager.isDomainInBlockedCategory(this, extractedDomain)) {
                    Long lastBlocked = lastBlockedTimes.get(pkg);
                    long now = System.currentTimeMillis();
                    if (lastBlocked != null && (now - lastBlocked) < BLOCK_COOLDOWN_MS) {
                        return;
                    }
                    Log.d(TAG, "BLOCK DOMAIN | domain=" + extractedDomain + " | pkg=" + pkg);
                    lastBlockedTimes.put(pkg, now);
                    triggerBlock(pkg, "Website blocked", "This website is restricted by Skyward.", false, true);
                    return;
                }
            }
        }
    }

    private String findDomainInNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        // 1. Try known browser URL bar View IDs for exact matches (fastest and most reliable)
        String[] urlBarIds = {
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.opera.browser:id/url_field",
            "com.microsoft.emmx:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.duckduckgo.mobile.android:id/omnibarTextInput"
        };

        for (String id : urlBarIds) {
            java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isFocused()) {
                        // User is actively typing, ignore auto-suggestions to prevent premature blocking
                        return null;
                    }
                    if (node.getText() != null) {
                        String domain = extractDomain(node.getText().toString());
                        if (domain != null) return domain;
                    }
                }
            }
        }

        // 2. Generic fallback: traverse and look for an EditText that contains a valid URL.
        // URL bars are usually EditTexts. We limit checking to EditText to avoid false positives in page content.
        return traverseForDomain(root, 0);
    }

    private String traverseForDomain(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 10) return null; // limit depth to prevent lag

        if ("android.widget.EditText".equals(node.getClassName()) && node.getText() != null) {
            if (node.isFocused()) {
                return null;
            }
            String domain = extractDomain(node.getText().toString());
            if (domain != null) {
                return domain;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String domain = traverseForDomain(child, depth + 1);
                child.recycle();
                if (domain != null) return domain;
            }
        }
        return null;
    }

    private String extractDomain(String text) {
        if (text == null) return null;
        text = text.trim();
        // Ignore very long text (not a URL bar)
        if (text.length() > 200 || text.isEmpty()) return null;
        
        // Fast fail: URLs shouldn't have spaces or newlines (unless encoded, but URL bars usually show spaces as %20 or don't have them)
        if (text.contains(" ") || text.contains("\n")) return null;
        
        String url = text;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // Needs at least a dot to be a domain
            if (!url.contains(".")) return null;
            url = "http://" + url;
        }
        
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            String host = parsedUrl.getHost();
            if (host != null && host.contains(".")) {
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }
                return host;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Shows the WarningActivity on top of the blocked app immediately.
     */
    private void triggerBlock(String blockedPackage, String title, String message, boolean isSettings, boolean isWebsite) {
        if (isSettings) {
            // Go back one option out of the blocked settings page so reopening Settings doesn't re-trigger
            performGlobalAction(GLOBAL_ACTION_BACK);
            // After a tiny delay, send the user home
            handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 150);
        } else {
            if (isWebsite) {
                // Press back to navigate the browser away from the blocked URL or close the new tab
                performGlobalAction(GLOBAL_ACTION_BACK);
                handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 150);
            } else {
                // Press Home to kill the blocked app's UI immediately
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        }

        // After a short delay (let home screen settle), show WarningActivity
        long warningDelay = (isSettings || isWebsite) ? 450 : 300;
        handler.postDelayed(() -> {
            Intent dialogIntent = new Intent(this, WarningActivity.class);
            dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            dialogIntent.putExtra("blocked_package", blockedPackage);
            dialogIntent.putExtra("title", title);
            dialogIntent.putExtra("message", message);
            dialogIntent.putExtra("is_settings", isSettings);
            dialogIntent.putExtra("is_website", isWebsite);
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
        if (dnsObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(dnsObserver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering DNS observer", e);
            }
        }
    }
}