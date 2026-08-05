package com.example.skywardblocker.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.skywardblocker.time.TrustedTimeManager;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * API client for communicating with the Skyward backend.
 *
 * Config is strictly loaded from SharedPreferences ("skyward_config"), pushed over ADB
 * by the desktop installer during provisioning. No fallback default strings exist.
 */
public class ApiClient {

    private static final String TAG = "SkywardDebug";
    private static final String PREF_NAME = "skyward_config";

    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_DNS_HOSTNAME = "dns_hostname";

    // ── Config Getters & Setters (No Hardcoded Fallbacks!) ─────────────────

    public static String getBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BASE_URL, null);
    }

    public static String getApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_API_KEY, null);
    }

    public static String getDnsHostname(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DNS_HOSTNAME, null);
    }

    public static void saveConfig(Context context, String baseUrl, String apiKey, String dnsHostname) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        if (baseUrl != null) editor.putString(KEY_BASE_URL, baseUrl);
        if (apiKey != null) editor.putString(KEY_API_KEY, apiKey);
        if (dnsHostname != null) editor.putString(KEY_DNS_HOSTNAME, dnsHostname);
        editor.apply();
        Log.i(TAG, "Saved pushed configuration to SharedPreferences!");
    }

    // ── Generic callback ──────────────────────────────────────────────

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    // ── Shared HTTP helper ────────────────────────────────────────────

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private static String doRequest(Context context, String method, String path, String jsonBody) throws Exception {
        return doRequest(context, method, path, jsonBody, true);
    }

    private static String doRequest(Context context, String method, String path, String jsonBody, boolean allowRetries) throws Exception {
        String baseUrl = getBaseUrl(context);
        String apiKey = getApiKey(context);

        if (baseUrl == null || baseUrl.trim().isEmpty() || apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Configuration missing! The Skyward Desktop Installer must push configuration over ADB first.");
        }

        URL url = new URL(baseUrl + path);
        int maxAttempts = allowRetries ? MAX_RETRIES : 1;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                Log.d(TAG, method + " " + url + " (Attempt " + attempt + " of " + MAX_RETRIES + ")");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(120000);
                conn.setReadTimeout(120000);
                conn.setRequestMethod(method);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);

                if (jsonBody != null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(jsonBody.getBytes("utf-8"));
                    }
                }

                int code = conn.getResponseCode();
                Log.d(TAG, "Response " + code + " from " + path);

                long dateHeader = conn.getHeaderFieldDate("Date", -1);
                if (dateHeader > 0) {
                    TrustedTimeManager.recordTrustedTime(context, dateHeader);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                if (code >= 400) {
                    throw new Exception("HTTP " + code + ": " + sb);
                }
                return sb.toString();

            } catch (Exception e) {
                lastException = e;
                Log.w(TAG, "Request failed " + method + " " + path + ": " + e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Request interrupted during retry delay", ie);
                    }
                }
            }
        }

        throw new Exception("Request failed after " + maxAttempts + " attempts. Last error: " + (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }

    // ── Category endpoints (dynamic) ──────────────────────────────────

    /**
     * Looks up a single app's Play Store category.
     * Expected response: { "packageName":"...", "category":"SOCIAL" }
     */
    public static void fetchAppCategory(Context context, String packageName, ApiCallback<String> callback) {
        new Thread(() -> {
            try {
                String body = "{\"packageName\": \"" + packageName + "\"}";
                String json = doRequest(context, "POST", "/app-category", body);
                JSONObject root = new JSONObject(json);
                String category = root.getString("category");
                Log.d(TAG, "Resolved " + packageName + " → " + category);
                callback.onSuccess(category);
            } catch (Exception e) {
                Log.e(TAG, "fetchAppCategory failed for " + packageName, e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    /**
     * Active trusted-time sync: issues a lightweight HEAD request to the configured base URL
     * and hands back the server's HTTP "Date" response header as trusted UTC epoch millis.
     * Used by TrustedTimeManager when no other API call is imminent (e.g. right after a
     * connectivity change, before any category lookup fires).
     */
    public static void fetchTrustedTime(Context context, ApiCallback<Long> callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String baseUrl = getBaseUrl(context);
                if (baseUrl == null || baseUrl.trim().isEmpty()) {
                    callback.onError("no base_url configured");
                    return;
                }
                URL url = new URL(baseUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("HEAD");
                conn.connect();
                long dateHeader = conn.getHeaderFieldDate("Date", -1);
                if (dateHeader > 0) {
                    callback.onSuccess(dateHeader);
                } else {
                    callback.onError("response had no Date header");
                }
            } catch (Exception e) {
                Log.w(TAG, "fetchTrustedTime failed", e);
                callback.onError(e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

}
