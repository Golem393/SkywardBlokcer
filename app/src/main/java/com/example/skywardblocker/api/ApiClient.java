package com.example.skywardblocker.api;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * API client for communicating with the Skyward backend.
 *
 * Config is hardcoded — the desktop installer handles authentication.
 * This client only handles dynamic category lookups and blocklist fetching.
 */
public class ApiClient {

    private static final String TAG = "SkywardDebug";

    // ── Config constants ─────────────────────────────────────────────

    private static final String BASE_URL = "https://mdm-backend-i4b0.onrender.com/api";
    private static final String API_KEY = "api_3d9a7c1f5b824e9aa4d6f7c8b1e2a3d4";
    public static final String DNS_HOSTNAME = "1w2whn92y8e.dns.controld.com";

    // ── Generic callback ──────────────────────────────────────────────

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    // ── Shared HTTP helper ────────────────────────────────────────────

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private static String doRequest(String method, String path, String jsonBody) throws Exception {
        URL url = new URL(BASE_URL + path);
        int maxAttempts = MAX_RETRIES;
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
                conn.setRequestProperty("X-API-Key", API_KEY);

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

        throw new Exception("Request failed after " + maxAttempts + " attempts. Last error: " + lastException.getMessage(), lastException);
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
                String json = doRequest("POST", "/app-category", body);
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
