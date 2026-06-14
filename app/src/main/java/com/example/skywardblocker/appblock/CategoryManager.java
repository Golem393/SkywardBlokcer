package com.example.skywardblocker.appblock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.example.skywardblocker.MdmApiClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            "VIDEO_PLAYERS"
    ));

    // In-memory cache: packageName → category
    private static final Map<String, String> cache = new HashMap<>();
    private static boolean cacheLoaded = false;

    // Packages currently being resolved (prevent duplicate API calls)
    private static final Set<String> pendingLookups = new HashSet<>();

    // ── Public API ────────────────────────────────────────────────────

    private static String lastCheckedPackage = "";

    public static void forceFetchPopularApps(Context context) {
        Log.d(TAG, "Forcing fetch of popular apps...");
        MdmApiClient.fetchPopularApps(context, new MdmApiClient.ApiCallback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> popularApps) {
                synchronized (CategoryManager.class) {
                    cache.putAll(popularApps);
                    saveCache(context);
                }
                Log.d(TAG, "Force-fetched and merged " + popularApps.size() + " popular apps into cache");
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Failed to force-fetch popular apps: " + errorMessage);
            }
        });
    }

    public static void printCache() {
        synchronized (CategoryManager.class) {
            Log.d(TAG, "--- Current App Cache (" + cache.size() + " entries) ---");
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                Log.d(TAG, "App: " + entry.getKey() + " -> Category: " + entry.getValue());
            }
            Log.d(TAG, "--- End of Cache ---");
        }
    }

    /**
     * Returns true if the app is in a blocked category.
     * If the category is unknown, returns false and fires an async lookup.
     * The app will be blocked on subsequent opens once the category is resolved.
     */
    public static synchronized boolean isAppInBlockedCategory(Context context, String packageName) {
        if (context.getPackageManager().getLaunchIntentForPackage(packageName) == null) {
            // There is no UI for the user to open. It's a background OS component.
            return false;
        }

        loadCacheIfNeeded(context);

        String category = cache.get(packageName);
        if (category != null) {
            boolean blocked = BLOCKED_CATEGORIES.contains(category);
            if (!packageName.equals(lastCheckedPackage)) {
                Log.d(TAG, packageName + " → " + category + " → " + (blocked ? "BLOCKED" : "ALLOWED"));
                lastCheckedPackage = packageName;
            }
            return blocked;
        }

        // Category unknown — resolve in background
        resolveAndCache(context, packageName);

        // TEMPORARY: Immediately block if the OS reports it as a game
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                if (!packageName.equals(lastCheckedPackage)) {
                    Log.d(TAG, packageName + " → OS_CATEGORY_GAME → BLOCKED (Waiting for API)");
                    lastCheckedPackage = packageName;
                }
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Ignore
        }

        return false;
    }

    /**
     * Call once at app startup (e.g. in AppBlockerService.onServiceConnected).
     * Fetches bulk popular-apps list and scans installed packages.
     */
    public static void initializeCache(Context context) {
        loadCacheIfNeeded(context);

        if (!cache.isEmpty()) {
            Log.d(TAG, "Cache already populated (" + cache.size() + " items), skipping bulk fetch.");
            scanInstalledApps(context);
            return;
        }

        // 1. Fetch popular apps from backend (bulk)
        MdmApiClient.fetchPopularApps(context, new MdmApiClient.ApiCallback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> popularApps) {
                synchronized (CategoryManager.class) {
                    cache.putAll(popularApps);
                    saveCache(context);
                }
                Log.d(TAG, "Merged " + popularApps.size() + " popular apps into cache");

                // 2. After popular apps are loaded, scan installed apps for gaps
                scanInstalledApps(context);
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Failed to fetch popular apps: " + errorMessage);
                // Still scan installed apps even if bulk fetch fails
                scanInstalledApps(context);
            }
        });
    }

    /**
     * Resolve a single package's category via API and cache it.
     */
    public static synchronized void resolveAndCache(Context context, String packageName) {
        if (cache.containsKey(packageName)) return;
        if (pendingLookups.contains(packageName)) return;
        pendingLookups.add(packageName);

        Log.d(TAG, "Resolving category for: " + packageName);

        MdmApiClient.fetchAppCategory(context, packageName, new MdmApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String category) {
                synchronized (CategoryManager.class) {
                    cache.put(packageName, category);
                    pendingLookups.remove(packageName);
                    saveCache(context);
                }
                Log.d(TAG, "Cached: " + packageName + " → " + category);
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

    // ── Internal ──────────────────────────────────────────────────────

    private static void scanInstalledApps(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);

        for (ApplicationInfo app : apps) {
            // Skip apps that have no launchable UI (background/system processes)
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