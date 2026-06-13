package com.example.skywardblocker;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MdmApiClient {

    private static final String TAG = "SkywardDebug";

    // ── Generic callback ──────────────────────────────────────────────

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    // Keep old interface for backward compat with SetupViewModel
    public interface MdmCallback {
        void onSuccess(String memberId);
        void onError(String errorMessage);
    }

    // ── Dynamic Config Fetchers ───────────────────────────────────────

    private static String getBaseUrl(Context context) {
        RestrictionsManager rm = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        return rm.getApplicationRestrictions().getString("mdm_base_url");
    }

    private static String getApiKey(Context context) {
        RestrictionsManager rm = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        return rm.getApplicationRestrictions().getString("mdm_api_key");
    }

    // ── Shared HTTP helper ────────────────────────────────────────────

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private static String doRequest(Context context, String method, String path, String jsonBody) throws Exception {
        URL url = new URL(getBaseUrl(context) + path);
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                Log.d(TAG, method + " " + url + " (Attempt " + attempt + " of " + MAX_RETRIES + ")");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestMethod(method);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-API-Key", getApiKey(context)); // <-- Dynamically injected here

                if (jsonBody != null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(jsonBody.getBytes("utf-8"));
                    }
                }

                int code = conn.getResponseCode();
                Log.d(TAG, "Response " + code + " from " + path);

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
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Request interrupted during retry delay", ie);
                    }
                }
            }
        }
        
        throw new Exception("Request failed after " + MAX_RETRIES + " attempts. Last error: " + lastException.getMessage(), lastException);
    }

    // ── Existing MDM methods (refactored to use doRequest) ────────────

    public static void finalizeDeviceSetup(Context context, String targetSerial, MdmCallback callback) {
        new Thread(() -> {
            try {
                // Step 1: Get kiosk devices
                String devicesJson = doRequest(context, "GET", "/kiosk-devices", null);
                JSONObject root = new JSONObject(devicesJson);
                JSONArray devices = root.optJSONArray("devices");

                String memberId = null;
                if (devices != null) {
                    for (int i = 0; i < devices.length(); i++) {
                        JSONObject d = devices.getJSONObject(i);
                        if (targetSerial != null && targetSerial.equalsIgnoreCase(d.optString("serial_number"))) {
                            memberId = d.optString("device_id");
                            Log.i(TAG, "Matched serial → memberId: " + memberId);
                            break;
                        }
                    }
                }

                if (memberId == null || memberId.isEmpty()) {
                    callback.onError("Serial " + targetSerial + " not found in kiosk group.");
                    return;
                }

                // Step 2: Move to official group
                String body = "{\"memberId\": \"" + memberId + "\"}";
                doRequest(context, "PUT", "/move-member-to-official", body);
                callback.onSuccess(memberId);

            } catch (Exception e) {
                Log.e(TAG, "finalizeDeviceSetup failed", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }



    // ── New category endpoints ────────────────────────────────────────

    /**
     * Fetches bulk popular-apps list from backend.
     * Expected response: { "apps": [ {"packageName":"...", "category":"GAME"}, ... ] }
     */
    public static void fetchPopularApps(Context context, ApiCallback<Map<String, String>> callback) {
        new Thread(() -> {
            try {
                String json = doRequest(context, "GET", "/popular-apps", null);
                JSONObject root = new JSONObject(json);
                JSONArray apps = root.getJSONArray("apps");

                Map<String, String> result = new HashMap<>();
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.getJSONObject(i);
                    result.put(app.getString("packageName"), app.getString("category"));
                }
                Log.d(TAG, "Fetched " + result.size() + " popular apps");
                callback.onSuccess(result);
            } catch (Exception e) {
                Log.e(TAG, "fetchPopularApps failed", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

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
}