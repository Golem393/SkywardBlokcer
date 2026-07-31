package com.example.skywardblocker.blocking;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.example.skywardblocker.api.ApiClient;
import com.example.skywardblocker.admin.DevicePolicyHelper;
import com.example.skywardblocker.schedule.ScheduleManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages app category resolution, caching, and Device Owner-based blocking.
 *
 * When a category is resolved and it's in BLOCKED_CATEGORIES, the app is
 * immediately suspended via DevicePolicyManager (Device Owner).
 */
public class CategoryManager {

    private static final String TAG = "SkywardDebug";
    private static final String CSV_FILE = "app_categories.csv";

    private static final Set<String> BLOCKED_CATEGORIES = new HashSet<>(Arrays.asList(
            "GAME",
            "GAME_ACTION",
            "GAME_ADVENTURE",
            "GAME_ARCADE",
            "GAME_BOARD",
            "GAME_CARD",
            "GAME_CASINO",
            "GAME_CASUAL",
            "GAME_EDUCATIONAL",
            "GAME_MUSIC",
            "GAME_PUZZLE",
            "GAME_RACING",
            "GAME_ROLE_PLAYING",
            "GAME_SIMULATION",
            "GAME_SPORTS",
            "GAME_STRATEGY",
            "GAME_TRIVIA",
            "GAME_WORD",
            "SOCIAL",
            "ENTERTAINMENT",
            "VIDEO_PLAYERS_ENTERTAINMENT",
            "BROWSER"
    ));

    // In-memory cache: packageName → category
    private static final Map<String, String> cache = new HashMap<>();
    private static boolean cacheLoaded = false;

    // Packages currently being resolved (prevent duplicate API calls)
    private static final Set<String> pendingLookups = new HashSet<>();

    // Lazy DevicePolicyHelper instance (avoids repeated allocation in hot paths)
    private static DevicePolicyHelper cachedDph;

    private static synchronized DevicePolicyHelper getDph(Context context) {
        if (cachedDph == null) {
            cachedDph = new DevicePolicyHelper(context);
        }
        return cachedDph;
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Returns true if the app is in a blocked category (based on cache only).
     */
    public static synchronized boolean isAppInBlockedCategory(Context context, String packageName) {
        if (context.getPackageManager().getLaunchIntentForPackage(packageName) == null) {
            return false;
        }

        loadCacheIfNeeded(context);

        String category = cache.get(packageName);
        if (category != null) {
            return BLOCKED_CATEGORIES.contains(category);
        }

        // Category unknown — resolve in background (will suspend if blocked)
        resolveAndCache(context, packageName);
        return false;
    }

    /**
     * Call once at startup (e.g. from BootReceiver or SkywardDeviceAdmin).
     * Loads the on-disk cache, scans installed packages for any gaps,
     * then suspends all blocked apps via Device Owner.
     */
    public static void initializeCache(Context context) {
        loadCacheIfNeeded(context);
        scanInstalledApps(context);
        applyEnforcement(context);
    }

    /**
     * Resolve a single package's category via API and cache it.
     * If the resolved category is blocked, the app is immediately suspended.
     */
    public static synchronized void resolveAndCache(Context context, String packageName) {
        if (cache.containsKey(packageName)) {
            String existingCat = cache.get(packageName);
            Log.d(TAG, "Package " + packageName + " already in cache (" + existingCat + "). Checking block status...");
            if (BLOCKED_CATEGORIES.contains(existingCat)) {
                Log.i(TAG, "New install " + packageName + " matches blocked category (" + existingCat + ") — suspending instantly!");
                if (ScheduleManager.shouldEnforceNow(context)) {
                    suspendApp(context, packageName, true);
                }
            }
            return;
        }
        if (pendingLookups.contains(packageName)) return;

        String baseUrl = ApiClient.getBaseUrl(context);
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            // Config hasn't been pushed yet (e.g. right after set-device-owner, before the
            // desktop installer's push_config lands) — every lookup would fail identically.
            // Skip quietly; nothing gets cached, so scanInstalledApps() retries this package
            // the next time initializeCache() runs (push_config, next boot, next app open).
            return;
        }

        pendingLookups.add(packageName);

        Log.d(TAG, "Resolving category for: " + packageName);

        ApiClient.fetchAppCategory(context, packageName, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String category) {
                synchronized (CategoryManager.class) {
                    cache.put(packageName, category);
                    pendingLookups.remove(packageName);
                    saveCache(context);
                }
                Log.d(TAG, "Cached: " + packageName + " → " + category);

                // If blocked, suspend immediately via Device Owner
                if (BLOCKED_CATEGORIES.contains(category) && ScheduleManager.shouldEnforceNow(context)) {
                    suspendApp(context, packageName, true);
                }
            }

            @Override
            public void onError(String errorMessage) {
                synchronized (CategoryManager.class) {
                    pendingLookups.remove(packageName);
                }
                Log.w(TAG, "Failed to resolve " + packageName + ": " + errorMessage);
            }
        });
    }

    /**
     * Remove a package from the cache (call on uninstall).
     */
    public static synchronized void removePackage(Context context, String packageName) {
        cache.remove(packageName);
        saveCache(context);
        Log.d(TAG, "Removed from cache: " + packageName);
    }

    /**
     * Iterates all cached apps and suspends any in blocked categories.
     * Call after cache is loaded/updated to enforce blocking via Device Owner.
     */
    public static void enforceBlockedApps(Context context) {
        DevicePolicyHelper dph = getDph(context);
        if (!dph.isDeviceOwner()) {
            Log.w(TAG, "enforceBlockedApps: not Device Owner, skipping");
            return;
        }

        PackageManager pm = context.getPackageManager();
        List<String> toSuspend = new ArrayList<>();
        synchronized (CategoryManager.class) {
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                if (BLOCKED_CATEGORIES.contains(entry.getValue())) {
                    try {
                        // Only attempt suspension if the app is actually installed on this device
                        pm.getPackageInfo(entry.getKey(), 0);
                        toSuspend.add(entry.getKey());
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // Package from popular-apps list is not installed locally; ignore
                    }
                }
            }
        }

        if (!toSuspend.isEmpty()) {
            String[] packages = toSuspend.toArray(new String[0]);
            String[] failed = dph.setPackagesSuspended(packages, true);
            Log.d(TAG, "Suspended " + (packages.length - failed.length) + "/" + packages.length + " installed blocked apps");
            if (failed.length > 0) {
                Log.w(TAG, "Failed to suspend: " + Arrays.toString(failed));
            }
        }
    }

    /**
     * Schedule-aware entry point: suspends cached blocked-category apps if enforcement
     * should currently be active, otherwise un-suspends them. Call this instead of
     * enforceBlockedApps() directly anywhere the schedule needs to gate blocking.
     */
    public static void applyEnforcement(Context context) {
        if (ScheduleManager.shouldEnforceNow(context)) {
            enforceBlockedApps(context);
        } else {
            unsuspendCachedBlockedApps(context);
        }
    }

    /**
     * Un-suspends all installed apps in blocked categories (mirrors enforceBlockedApps()
     * but reverses it). Used when the schedule enters an unlocked window.
     */
    public static void unsuspendCachedBlockedApps(Context context) {
        DevicePolicyHelper dph = getDph(context);
        if (!dph.isDeviceOwner()) {
            Log.w(TAG, "unsuspendCachedBlockedApps: not Device Owner, skipping");
            return;
        }

        PackageManager pm = context.getPackageManager();
        List<String> toUnsuspend = new ArrayList<>();
        synchronized (CategoryManager.class) {
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                if (BLOCKED_CATEGORIES.contains(entry.getValue())) {
                    try {
                        pm.getPackageInfo(entry.getKey(), 0);
                        toUnsuspend.add(entry.getKey());
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // Not installed locally; ignore
                    }
                }
            }
        }

        if (!toUnsuspend.isEmpty()) {
            String[] packages = toUnsuspend.toArray(new String[0]);
            String[] failed = dph.setPackagesSuspended(packages, false);
            Log.d(TAG, "Unsuspended " + (packages.length - failed.length) + "/" + packages.length + " apps for unlocked schedule window");
            if (failed.length > 0) {
                Log.w(TAG, "Failed to unsuspend: " + Arrays.toString(failed));
            }
        }
    }

    /**
     * Un-suspends all installed applications on the device.
     * Called before relinquishing Device Owner status during deactivation.
     */
    public static void unblockAllApps(Context context) {
        DevicePolicyHelper dph = getDph(context);
        if (!dph.isDeviceOwner()) return;

        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> allPackages = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            allPackages.add(app.packageName);
        }

        if (!allPackages.isEmpty()) {
            String[] packages = allPackages.toArray(new String[0]);
            dph.setPackagesSuspended(packages, false);
            Log.i(TAG, "Unblocked all (" + packages.length + ") applications during Device Owner removal.");
        }
    }

    // ── Internal ──────────────────────────────────────────────────────

    private static void suspendApp(Context context, String packageName, boolean suspend) {
        DevicePolicyHelper dph = getDph(context);
        if (!dph.isDeviceOwner()) return;
        dph.setPackagesSuspended(new String[]{packageName}, suspend);
        Log.d(TAG, (suspend ? "Suspended" : "Unsuspended") + ": " + packageName);
    }

    private static void scanInstalledApps(Context context) {
        String baseUrl = ApiClient.getBaseUrl(context);
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            Log.d(TAG, "scanInstalledApps: no config pushed yet, skipping category resolution for now (will retry once config lands)");
            return;
        }

        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);

        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue;

            synchronized (CategoryManager.class) {
                if (!cache.containsKey(app.packageName)) {
                    resolveAndCache(context, app.packageName);
                }
            }
        }
    }

    private static synchronized void loadCacheIfNeeded(Context context) {
        if (cacheLoaded) return;
        cacheLoaded = true;

        File file = new File(context.getFilesDir(), CSV_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    cache.put(parts[0].trim(), parts[1].trim());
                }
            }
            Log.d(TAG, "Loaded " + cache.size() + " entries from category cache");
        } catch (Exception e) {
            Log.e(TAG, "Error loading category cache", e);
        }
    }

    private static void saveCache(Context context) {
        File file = new File(context.getFilesDir(), CSV_FILE);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                writer.println(entry.getKey() + "," + entry.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving category cache", e);
        }
    }

}
