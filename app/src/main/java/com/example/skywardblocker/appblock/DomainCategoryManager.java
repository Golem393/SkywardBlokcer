package com.example.skywardblocker.appblock;

import android.content.Context;
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
import java.util.Map;
import java.util.Set;

public class DomainCategoryManager {

    private static final String TAG = "SkywardDebug";
    private static final String CSV_FILE = "domain_categories.csv";

    private static final Set<String> BLOCKED_CATEGORIES = new HashSet<>(Arrays.asList(
            "ADULT",
            "GAME",
            "SOCIAL",
            "ENTERTAINMENT",
            "GAMBLING"
    ));

    // In-memory cache: domain → category
    private static final Map<String, String> cache = new HashMap<>();
    private static boolean cacheLoaded = false;

    // Domains currently being resolved (prevent duplicate API calls)
    private static final Set<String> pendingLookups = new HashSet<>();
    private static final Map<String, Long> failedLookups = new HashMap<>();
    private static final long RETRY_COOLDOWN_MS = 60000; // 1 minute

    private static String lastCheckedDomain = "";

    /**
     * Returns true if the domain is in a blocked category.
     * If the category is unknown, returns false and fires an async lookup.
     * The domain will be blocked on subsequent checks once the category is resolved.
     */
    public static synchronized boolean isDomainInBlockedCategory(Context context, String domain) {
        if (domain == null || domain.isEmpty()) return false;

        loadCacheIfNeeded(context);

        String category = cache.get(domain);
        if (category != null) {
            boolean blocked = BLOCKED_CATEGORIES.contains(category);
            if (!domain.equals(lastCheckedDomain)) {
                Log.d(TAG, "Domain: " + domain + " → " + category + " → " + (blocked ? "BLOCKED" : "ALLOWED"));
                lastCheckedDomain = domain;
            }
            return blocked;
        }

        // Category unknown — resolve in background
        resolveAndCache(context, domain);

        return false;
    }

    /**
     * Resolve a single domain's category via API and cache it.
     */
    public static synchronized void resolveAndCache(Context context, String domain) {
        if (cache.containsKey(domain)) return;
        if (pendingLookups.contains(domain)) return;
        if (failedLookups.containsKey(domain)) {
            if (System.currentTimeMillis() - failedLookups.get(domain) < RETRY_COOLDOWN_MS) {
                return; // Cooldown active
            }
        }
        pendingLookups.add(domain);

        Log.d(TAG, "Resolving category for domain: " + domain);

        MdmApiClient.fetchDomainCategory(context, domain, new MdmApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String category) {
                synchronized (DomainCategoryManager.class) {
                    cache.put(domain, category);
                    pendingLookups.remove(domain);
                    saveCache(context);
                }
                Log.d(TAG, "Cached Domain: " + domain + " → " + category);
            }

            @Override
            public void onError(String errorMessage) {
                synchronized (DomainCategoryManager.class) {
                    pendingLookups.remove(domain);
                    failedLookups.put(domain, System.currentTimeMillis());
                }
                Log.w(TAG, "Failed to resolve domain " + domain + ": " + errorMessage);
            }
        });
    }

    public static synchronized void clearCache(Context context) {
        cache.clear();
        saveCache(context);
        Log.d(TAG, "Domain category cache cleared.");
    }

    // ── Internal ──────────────────────────────────────────────────────

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
            Log.d(TAG, "Loaded " + cache.size() + " entries from domain category cache");
        } catch (Exception e) {
            Log.e(TAG, "Error loading domain category cache", e);
        }
    }

    private static void saveCache(Context context) {
        File file = new File(context.getFilesDir(), CSV_FILE);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (Map.Entry<String, String> entry : cache.entrySet()) {
                writer.println(entry.getKey() + "," + entry.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving domain category cache", e);
        }
    }
}
