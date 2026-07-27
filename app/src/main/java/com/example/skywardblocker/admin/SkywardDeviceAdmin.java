package com.example.skywardblocker.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;

import com.example.skywardblocker.api.ApiClient;
import com.example.skywardblocker.blocking.AppMonitorService;
import com.example.skywardblocker.blocking.CategoryManager;

/**
 * DeviceAdminReceiver for SkywardBlocker.
 *
 * When this app is set as Device Owner via ADB:
 *   adb shell dpm set-device-owner com.example.skywardblocker/.admin.SkywardDeviceAdmin
 *
 * ...it automatically initializes lockdown protections, Private DNS, and background
 * monitoring immediately upon being enabled!
 */
public class SkywardDeviceAdmin extends DeviceAdminReceiver {

    private static final String TAG = "SkywardDebug";

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "Device Admin / Owner enabled via ADB — automatically initializing protection!");

        DevicePolicyHelper dph = new DevicePolicyHelper(context);
        if (dph.isDeviceOwner()) {
            dph.lockdownSkyward();
            String dnsHostname = ApiClient.getDnsHostname(context);
            if (dnsHostname != null && !dnsHostname.trim().isEmpty()) {
                dph.setPrivateDns(dnsHostname);
            } else {
                Log.w(TAG, "DNS hostname not yet pushed; skipping setPrivateDns in SkywardDeviceAdmin.");
            }
            CategoryManager.initializeCache(context);
            AppMonitorService.start(context);
        }
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device Admin disabled");
    }

    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        Log.w(TAG, "Device Admin disable requested — this should not happen for Device Owner");
        return "Skyward cannot be disabled. Contact your administrator.";
    }
}
