package com.example.skywardblocker.dns;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.skywardblocker.MainActivity;
import com.example.skywardblocker.StateManager;

import java.util.List;

public class DnsAutoSetupScript {
    private static final String TAG = "SkywardDebug";
    private static final String DNS_HOSTNAME = "623d88.dns.nextdns.io";

    private boolean isFinished = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public void reset() {
        isFinished = false;
    }

    public void processEvent(AccessibilityEvent event, AccessibilityService service) {
        if (isFinished) return;

        AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
        if (rootNode == null) return;

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
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, DNS_HOSTNAME);
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            Log.d(TAG, "DNS hostname entered.");
            
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
        isFinished = true;
        
        SharedPreferences prefs = service.getSharedPreferences("skyward_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("auto_configure_dns", false).apply();

        StateManager.nextState(service);

        handler.postDelayed(() -> {
            Intent intent = new Intent(service, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            service.startActivity(intent);
        }, 500);
    }
}
