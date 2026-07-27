package com.example.skywardblocker;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;

import com.example.skywardblocker.admin.DevicePolicyHelper;
import com.example.skywardblocker.api.ApiClient;
import com.example.skywardblocker.blocking.AppMonitorService;
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
            String dnsHostname = ApiClient.getDnsHostname(this);
            if (dnsHostname != null && !dnsHostname.trim().isEmpty()) {
                dph.setPrivateDns(dnsHostname);
            } else {
                Log.w(TAG, "DNS hostname not yet pushed; skipping setPrivateDns.");
            }

            // Initialize category cache, suspend blocked apps, and start protection service
            CategoryManager.initializeCache(this);
            AppMonitorService.start(this);
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