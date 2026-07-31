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
 *   - Configure Private DNS (enforces content-filtering DNS)
 *   - Set user restrictions (prevent settings tampering)
 */
public class DevicePolicyHelper {

    private static final String TAG = "SkywardDebug";

    // ── Developer / Test Flag ─────────────────────────────────────────
    // Set to TRUE during development & testing so USB debugging (ADB) remains enabled.
    // Set to FALSE for production releases so users cannot bypass protection via terminal commands.
    public static final boolean ALLOW_USB_DEBUGGING = false;

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

    // ── DNS configuration ─────────────────────────────────────────────

    /**
     * Configures Private DNS via Device Owner policy.
     *
     * @param hostname The DNS hostname (e.g., "dns.example.com"), or null to use auto mode
     */
    public boolean setPrivateDns(String hostname) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "setPrivateDns: not Device Owner, skipping");
            return false;
        }
        // Run DNS configuration on a background thread to avoid NetworkOnMainThreadException on Android 10+
        new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (hostname != null && !hostname.isEmpty()) {
                        int result = dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, hostname);
                        Log.d(TAG, "setPrivateDns(" + hostname + ") result: " + result);
                    } else {
                        dpm.setGlobalPrivateDnsModeOpportunistic(adminComponent);
                        Log.d(TAG, "setPrivateDns: set to opportunistic mode");
                    }
                } else {
                    Log.w(TAG, "setPrivateDns: requires API 29+, current: " + Build.VERSION.SDK_INT);
                }
            } catch (Exception e) {
                Log.e(TAG, "setPrivateDns failed", e);
            }
        }).start();
        return true;
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
     * Blocks the user from enabling/disabling USB debugging.
     */
    public void setDebuggingDisabled(boolean disabled) {
        setUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, disabled);
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

    /**
     * Toggles USB debugging restrictions for temporary maintenance mode.
     * Uses boxed Boolean to support method reference matching for Consumer<Boolean>.
     *
     * @param enabled If true, temporarily removes debugging restriction. If false, enforces debugging restriction.
     */
    public void setTemporaryDebugging(Boolean enabled) {
        boolean allowDebugging = enabled != null && enabled;
        setUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, !allowDebugging);
        Log.i(TAG, "Temporary debugging state changed: enabled=" + allowDebugging);
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
     * Deliberately does NOT touch USB debugging — the desktop installer still needs ADB
     * to push config/schedule and launch the app right after Device Owner is set. Call
     * finalizeProvisioning() once that's done to seal USB debugging.
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

        Log.d(TAG, "lockdownSkyward: protections applied (USB debugging left as-is until finalizeProvisioning())");
    }

    /**
     * Seals USB debugging once the desktop installer has finished pushing config/schedule
     * and launching the app. Called via the ADB FINALIZE_SETUP broadcast — the last thing
     * the installer does before disconnecting, since it can't reach the device over ADB
     * anymore afterward (by design, same as any other production lockdown).
     */
    public void finalizeProvisioning() {
        if (!isDeviceOwner()) {
            Log.w(TAG, "finalizeProvisioning: not Device Owner, skipping");
            return;
        }
        if (!ALLOW_USB_DEBUGGING) {
            setDebuggingDisabled(true);
            Log.i(TAG, "finalizeProvisioning: USB debugging locked down");
        } else {
            Log.i(TAG, "finalizeProvisioning: ALLOW_USB_DEBUGGING is true — keeping USB debugging open for developer testing!");
        }
    }

    /**
     * Removes all Skyward protections. Call before clearing Device Owner.
     */
    public void unlockSkyward() {
        if (!isDeviceOwner()) return;

        setUninstallBlocked(context.getPackageName(), false);
        setFactoryResetDisabled(false);
        setDebuggingDisabled(false);
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

        // 3. Revert Private DNS to opportunistic (automatic) mode synchronously
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                dpm.setGlobalPrivateDnsModeOpportunistic(adminComponent);
                Log.d(TAG, "clearDeviceOwner: DNS reset to opportunistic mode");
            } catch (Exception ignored) {}
        }

        // 4. Remove Skyward self-protections & user restrictions
        unlockSkyward();

        // 5. Relinquish Device Owner privileges
        try {
            dpm.clearDeviceOwnerApp(context.getPackageName());
            Log.d(TAG, "clearDeviceOwner: Device Owner status removed");
        } catch (Exception e) {
            Log.e(TAG, "clearDeviceOwner failed", e);
        }
    }
}
