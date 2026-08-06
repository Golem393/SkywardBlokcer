package com.example.skywardblocker.admin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import com.example.skywardblocker.blocking.AppMonitorService;
import com.example.skywardblocker.blocking.CategoryManager;

/**
 * Helper class that wraps DevicePolicyManager calls.
 * Only works when the app is set as Device Owner via:
 *   adb shell dpm set-device-owner com.example.skywardblocker/.admin.SkywardDeviceAdmin
 *
 * Provides system-level controls:
 *   - Suspend/hide apps (prevents launching blocked categories)
 *   - Block uninstallation (prevents removal of Skyward or protected apps)
 *   - Set user restrictions (prevent settings tampering)
 */
public class DevicePolicyHelper {

    private static final String TAG = "SkywardDebug";

    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName adminComponent;

    public DevicePolicyHelper(Context context) {
        this.context = context.getApplicationContext();
        this.dpm = (DevicePolicyManager) this.context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.adminComponent = new ComponentName(this.context, SkywardDeviceAdmin.class);
    }

    // ── Status checks ─────────────────────────────────────────────────

    /**
     * Returns true if this app is the Device Owner.
     */
    public boolean isDeviceOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    // ── App blocking ──────────────────────────────────────────────────

    /**
     * Suspends packages so they can't be launched. Shows a "suspended" dialog when tapped.
     * Requires Device Owner.
     *
     * @param packageNames Array of package names to suspend
     * @param suspended    true to suspend, false to unsuspend
     * @return Array of package names that could NOT be suspended (empty = all succeeded)
     */
    public String[] setPackagesSuspended(String[] packageNames, boolean suspended) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "setPackagesSuspended: not Device Owner, skipping");
            return packageNames;
        }
        try {
            return dpm.setPackagesSuspended(adminComponent, packageNames, suspended);
        } catch (Exception e) {
            Log.e(TAG, "setPackagesSuspended failed", e);
            return packageNames;
        }
    }

    /**
     * Prevents a specific package from being uninstalled.
     * The "Uninstall" button in Settings will be greyed out.
     */
    public void setUninstallBlocked(String packageName, boolean blocked) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "setUninstallBlocked: not Device Owner, skipping");
            return;
        }
        try {
            dpm.setUninstallBlocked(adminComponent, packageName, blocked);
            Log.d(TAG, "setUninstallBlocked(" + packageName + ", " + blocked + ") OK");
        } catch (Exception e) {
            Log.e(TAG, "setUninstallBlocked failed for " + packageName, e);
        }
    }

    // ── User restrictions (prevent settings tampering) ────────────────

    /**
     * Blocks the user from performing a factory reset.
     */
    public void setFactoryResetDisabled(boolean disabled) {
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, disabled);
    }

    /**
     * Blocks the user from booting into Safe Mode.
     */
    public void setSafeModeDisabled(boolean disabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, disabled);
        } else {
            Log.w(TAG, "setSafeModeDisabled: DISALLOW_SAFE_BOOT requires API 33+");
        }
    }

    /**
     * Blocks the user from manually changing date, time, or timezone (Settings > Date & Time
     * becomes non-editable). Enabling this also forces automatic date/time and automatic
     * timezone to stay on, so the schedule feature's boundary checks can trust
     * TimeZone.getDefault() instead of it being a manual bypass vector.
     */
    public void setDateTimeConfigDisabled(boolean disabled) {
        setUserRestriction(UserManager.DISALLOW_CONFIG_DATE_TIME, disabled);
    }

    private void setUserRestriction(String restriction, boolean add) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "setUserRestriction: not Device Owner, skipping " + restriction);
            return;
        }
        try {
            if (add) {
                dpm.addUserRestriction(adminComponent, restriction);
            } else {
                dpm.clearUserRestriction(adminComponent, restriction);
            }
            Log.d(TAG, "setUserRestriction(" + restriction + ", " + add + ") OK");
        } catch (Exception e) {
            Log.e(TAG, "setUserRestriction failed for " + restriction, e);
        }
    }

    // ── Self-protection ───────────────────────────────────────────────

    /**
     * Makes Skyward itself uninstallable and blocks tampering.
     * Call this after Device Owner is confirmed active.
     *
     * Deliberately does NOT touch USB debugging — it is left enabled at all times so the
     * desktop companion app can always reach the device over ADB.
     */
    public void lockdownSkyward() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "lockdownSkyward: not Device Owner, skipping");
            return;
        }

        // Prevent uninstalling Skyward itself
        setUninstallBlocked(context.getPackageName(), true);

        // Prevent factory reset bypass
        setFactoryResetDisabled(true);

        // Prevent Safe Mode bypass (API 33+)
        setSafeModeDisabled(true);

        // Prevent manual clock/timezone tampering (schedule anti-bypass)
        setDateTimeConfigDisabled(true);

        Log.d(TAG, "lockdownSkyward: protections applied (USB debugging left enabled)");
    }

    /**
     * Removes all Skyward protections. Call before clearing Device Owner.
     */
    public void unlockSkyward() {
        if (!isDeviceOwner()) return;

        setUninstallBlocked(context.getPackageName(), false);
        setFactoryResetDisabled(false);
        setSafeModeDisabled(false);
        setDateTimeConfigDisabled(false);

        Log.d(TAG, "unlockSkyward: all protections removed");
    }

    /**
     * Completely removes Device Owner status. After this, the app can be uninstalled normally.
     * This is a one-way operation — you'd need to re-run ADB to restore it.
     */
    public void clearDeviceOwner() {
        if (!isDeviceOwner()) return;

        // 1. Stop background service to prevent race conditions during teardown
        AppMonitorService.stop(context);

        // 2. Un-suspend all installed apps so they become accessible again
        CategoryManager.unblockAllApps(context);

        // 3. Remove Skyward self-protections & user restrictions
        unlockSkyward();

        // 4. Relinquish Device Owner privileges
        try {
            dpm.clearDeviceOwnerApp(context.getPackageName());
            Log.d(TAG, "clearDeviceOwner: Device Owner status removed");
        } catch (Exception e) {
            Log.e(TAG, "clearDeviceOwner failed", e);
        }
    }
}
