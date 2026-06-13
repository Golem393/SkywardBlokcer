package com.example.skywardblocker.dns;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.provider.Settings;
import android.content.RestrictionsManager;

import com.example.skywardblocker.MainActivity;
import com.example.skywardblocker.StateManager;

import java.util.List;

public class DnsAutoSetupScript {
    private static final String TAG = "SkywardDebug";

    private boolean isFinished = false;
    private boolean isTransitioning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private View overlayView;
    private WindowManager windowManager;

    public void reset() {
        if (isTransitioning) return;
        isFinished = false;
    }

    public void processEvent(AccessibilityEvent event, AccessibilityService service) {
        if (isFinished) return;

        showOverlay(service);

        AccessibilityNodeInfo source = event.getSource();
        AccessibilityNodeInfo rootNode = null;
        if (source != null && source.getWindow() != null) {
            rootNode = source.getWindow().getRoot();
        } else {
            rootNode = service.getRootInActiveWindow();
        }

        if (rootNode == null) {
            Log.d(TAG, "Could not find root node.");
            return;
        }

        Log.d(TAG, "DNS Script processEvent triggered. Screen contents:");
        debugPrintNodes(rootNode, 0);

        // Step 0.1: Check for "Private DNS" menu item (if we are in the correct sub-menu, but not the dialog)
        // Note: The dialog might also have "Private DNS" as title, but we only click if it's a menu item. 
        // We can just rely on exact text click.
        boolean clickedPrivateDns = clickNodeByExactText(rootNode, "Private DNS");
        if (clickedPrivateDns) {
            Log.d(TAG, "Clicked Private DNS menu item to open dialog.");
            // Wait for dialog to open, next event will handle the rest.
        }

        // Step 0.2: Check for "More connection settings" (Samsung)
        boolean clickedMoreConnections = false;
        if (!clickedPrivateDns) {
            clickedMoreConnections = clickNodeByExactText(rootNode, "More connection settings");
            if (clickedMoreConnections) {
                Log.d(TAG, "Clicked More connection settings.");
            }
        }

        // Step 0.3: Check for "Network & internet" or "Connections"
        boolean clickedNetwork = false;
        if (!clickedPrivateDns && !clickedMoreConnections) {
            clickedNetwork = clickNodeByExactText(rootNode, "Network & internet");
            if (!clickedNetwork) {
                clickedNetwork = clickNodeByExactText(rootNode, "Connections");
            }
            if (clickedNetwork) {
                Log.d(TAG, "Clicked Network/Connections menu.");
            }
        }

        // Step 1: Find and click the "Private DNS provider hostname" radio button
        boolean clickedRadio = clickNodeByTextSubstrings(rootNode, "hostname", "provider", "designated");

        // Step 2: Find the EditText and set the DNS string
        AccessibilityNodeInfo editText = findEditText(rootNode);
        
        if (editText != null) {
            String dnsHostname = getDnsHostname(service);
            if (dnsHostname != null) {
                Log.e(TAG, "Cannot configure DNS: No hostname provided by MDM.");
            }
            
            // Step 3: Find and click the "Save" button
            boolean clickedSave = clickNodeByText(rootNode, "Save");
            if (!clickedSave) {
                clickedSave = clickNodeByText(rootNode, "SAVE");
            }
            
            if (clickedSave) {
                Log.d(TAG, "Save clicked, completing DNS setup.");
                finishSetup(service);
            }
        }
        
        // Don't recycle rootNode here to avoid IllegalStateException during later iterations if another event fires
    }

    private boolean clickNodeByText(AccessibilityNodeInfo node, String textToFind) {
        if (node == null) return false;
        
        List<AccessibilityNodeInfo> foundNodes = node.findAccessibilityNodeInfosByText(textToFind);
        if (foundNodes != null && !foundNodes.isEmpty()) {
            for (AccessibilityNodeInfo targetNode : foundNodes) {
                if (targetNode.isClickable()) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
                AccessibilityNodeInfo parent = targetNode.getParent();
                if (parent != null && parent.isClickable()) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean clickNodeByExactText(AccessibilityNodeInfo node, String exactText) {
        if (node == null) return false;
        
        List<AccessibilityNodeInfo> foundNodes = node.findAccessibilityNodeInfosByText(exactText);
        if (foundNodes != null && !foundNodes.isEmpty()) {
            for (AccessibilityNodeInfo targetNode : foundNodes) {
                // Ensure it's not the radio button label by checking if it exactly matches "Private DNS"
                if (targetNode.getText() != null && targetNode.getText().toString().equalsIgnoreCase(exactText)) {
                    if (targetNode.isClickable()) {
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }
                    AccessibilityNodeInfo parent = targetNode.getParent();
                    if (parent != null && parent.isClickable()) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean clickNodeByTextSubstrings(AccessibilityNodeInfo node, String... substrings) {
        if (node == null) return false;

        if (node.getText() != null) {
            String text = node.getText().toString().toLowerCase();
            for (String sub : substrings) {
                if (text.contains(sub)) {
                    if (node.isClickable()) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    } else if (node.getParent() != null && node.getParent().isClickable()) {
                        node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (clickNodeByTextSubstrings(node.getChild(i), substrings)) {
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo findEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if ("android.widget.EditText".equals(node.getClassName())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findEditText(node.getChild(i));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private void debugPrintNodes(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");
        Log.d(TAG, indent.toString() + "Node: " + node.getClassName() + " | Text: " + node.getText() + " | Desc: " + node.getContentDescription());
        for (int i = 0; i < node.getChildCount(); i++) {
            debugPrintNodes(node.getChild(i), depth + 1);
        }
    }

    private void finishSetup(AccessibilityService service) {
        if (isTransitioning) return;
        isTransitioning = true;
        isFinished = true;
        
        StateManager.nextState(service);

        handler.postDelayed(() -> {
            SharedPreferences prefs = service.getSharedPreferences("skyward_prefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("auto_configure_dns", false).apply();
            
            hideOverlay();
            isTransitioning = false;

            Intent intent = new Intent(service, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            service.startActivity(intent);
        }, 800);
    }

    public void showOverlay(AccessibilityService service) {
        if (overlayView != null) return;
        Log.d(TAG, "Showing touch blocking overlay...");
        windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        overlayView = new View(service);
        overlayView.setBackgroundColor(0x80000000); // Semi-transparent black for debugging

        // Set a timeout to automatically abort if setup stalls
        timeoutRunnable = () -> {
            Log.d(TAG, "DNS Script timed out. Aborting auto-setup.");
            isFinished = true;
            isTransitioning = false;

            // Disable the auto script to prevent infinite loops
            SharedPreferences prefs = service.getSharedPreferences("skyward_prefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("auto_configure_dns", false).apply();

            // Send user back to main settings page to start manual setup from scratch
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                service.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to jump back to settings on timeout.", e);
            }

            // Keep the overlay active for a short time to hide the transition
            handler.postDelayed(() -> {
                hideOverlay();
            }, 400);
        };
        handler.postDelayed(timeoutRunnable, 3000);

        overlayView.setOnTouchListener((v, touchEvent) -> {
            Log.d(TAG, "Overlay intercepted touch!");
            return true;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        windowManager.addView(overlayView, params);
        Log.d(TAG, "Touch blocking overlay added.");
    }

    public void hideOverlay() {
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
            Log.d(TAG, "Touch blocking overlay removed.");
        }
    }

    public static String getDnsHostname(Context context) {
        RestrictionsManager rm = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (rm != null) {
            Bundle restrictions = rm.getApplicationRestrictions();
            if (restrictions != null && restrictions.containsKey("mdm_dns_hostname")) {
                String hostname = restrictions.getString("mdm_dns_hostname");
                if (hostname != null && !hostname.trim().isEmpty()) {
                    return hostname;
                }
            }
        }
        return null;
    }

    public static boolean isDnsConfigured(Context context) {
        String expectedHostname = getDnsHostname(context);
        if (expectedHostname == null) {
            return false;
        }
        
        String mode = Settings.Global.getString(context.getContentResolver(), "private_dns_mode");
        String specifier = Settings.Global.getString(context.getContentResolver(), "private_dns_specifier");
        return "hostname".equals(mode) && expectedHostname.equals(specifier);
    }
}
