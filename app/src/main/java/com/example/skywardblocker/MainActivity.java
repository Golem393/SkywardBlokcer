package com.example.skywardblocker;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;

import com.example.skywardblocker.admin.DevicePolicyHelper;
import com.example.skywardblocker.api.ApiClient;
import com.example.skywardblocker.blocking.CategoryManager;
import com.example.skywardblocker.ui.ComposeBridge;

/**
 * Minimal status activity for SkywardBlocker.
 *
 * All setup (Device Owner, DNS, auth) is handled by the desktop installer via ADB.
 * This activity just shows the current status and triggers the initial app scan.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SkywardDebug";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ComposeView composeView = new ComposeView(this);
        setContentView(composeView);

        DevicePolicyHelper dph = new DevicePolicyHelper(this);

        // Auto-configure if Device Owner (idempotent)
        if (dph.isDeviceOwner()) {
            Log.d(TAG, "Device Owner active — applying lockdown + DNS + Monitor Service");
            dph.lockdownSkyward();
            dph.setPrivateDns(ApiClient.DNS_HOSTNAME);

            // Initialize category cache and suspend blocked apps
            CategoryManager.initializeCache(this);
            // Note: AppMonitorService is started by SkywardDeviceAdmin.onEnabled() and BootReceiver — no need to restart here.
        }

        // Set up the status UI
        ComposeBridge.setup(composeView, dph.isDeviceOwner(), this::finish);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-enforce blocked apps each time the activity resumes
        CategoryManager.enforceBlockedApps(this);
    }
}