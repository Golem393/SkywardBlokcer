package com.example.skywardblocker.time;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.skywardblocker.api.ApiClient;
import com.example.skywardblocker.blocking.CategoryManager;
import com.example.skywardblocker.schedule.ScheduleAlarmScheduler;

/**
 * Provides a tamper-resistant "trusted now" that never reads System.currentTimeMillis()
 * or any user-settable wall-clock source. Trusted time is anchored to
 * SystemClock.elapsedRealtime() (monotonic since boot, not user-settable) and corrected
 * opportunistically against network time (the HTTP Date response header from the backend).
 *
 * The anchor intentionally does NOT survive reboots: elapsedRealtime() resets to ~0 at
 * boot, so a stale anchor (whose recorded elapsedRealtime is now in the "future" relative
 * to the current session) is detected and discarded automatically.
 */
public class TrustedTimeManager {

    private static final String TAG = "SkywardDebug";
    private static final String PREF_NAME = "skyward_trusted_time";
    private static final String KEY_ANCHOR_EPOCH = "anchor_epoch_millis";
    private static final String KEY_ANCHOR_ELAPSED = "anchor_elapsed_realtime";

    private static ConnectivityManager.NetworkCallback networkCallback;

    public interface SyncCallback {
        void onDone(boolean success);
    }

    /**
     * Returns the current trusted UTC epoch millis, or null if no valid anchor exists
     * (never synced yet, or the anchor predates this boot session).
     */
    @Nullable
    public static Long getCurrentTrustedEpochMillis(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long anchorEpoch = prefs.getLong(KEY_ANCHOR_EPOCH, -1);
        long anchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED, -1);
        if (anchorEpoch < 0 || anchorElapsed < 0) {
            return null;
        }

        long nowElapsed = SystemClock.elapsedRealtime();
        if (anchorElapsed > nowElapsed) {
            Log.w(TAG, "Trusted-time anchor predates this boot session — invalidating");
            return null;
        }

        return anchorEpoch + (nowElapsed - anchorElapsed);
    }

    /**
     * Records a freshly-obtained trusted UTC epoch millis value, anchored to the current
     * elapsedRealtime().
     */
    public static void recordTrustedTime(Context context, long epochMillisFromServer) {
        long nowElapsed = SystemClock.elapsedRealtime();
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_ANCHOR_EPOCH, epochMillisFromServer)
                .putLong(KEY_ANCHOR_ELAPSED, nowElapsed)
                .apply();
        Log.i(TAG, "Trusted time anchor recorded: epoch=" + epochMillisFromServer);
    }

    /**
     * Actively attempts to (re)establish the trusted-time anchor via a network round trip.
     * Safe to call opportunistically (e.g. on connectivity change, on a periodic tick) —
     * onDone is always invoked, with success=false on failure so callers can proceed with
     * whatever anchor state exists (including "still none", which correctly fails locked).
     */
    public static void syncNow(Context context, @Nullable SyncCallback onDone) {
        Context appContext = context.getApplicationContext();
        ApiClient.fetchTrustedTime(appContext, new ApiClient.ApiCallback<Long>() {
            @Override
            public void onSuccess(Long epochMillis) {
                recordTrustedTime(appContext, epochMillis);
                if (onDone != null) onDone.onDone(true);
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "syncNow failed: " + errorMessage);
                if (onDone != null) onDone.onDone(false);
            }
        });
    }

    /**
     * Registers a ConnectivityManager callback that triggers a trusted-time sync attempt
     * as soon as Android confirms real, validated internet reachability — not merely
     * "link up." This is what makes recovery from fail-locked reliable: it doesn't race
     * the post-boot Wi-Fi/cellular association delay, and it fires again any time
     * connectivity is regained after being lost, with no dependency on any other API call
     * ever happening. Idempotent — safe to call if already registered.
     */
    public static void registerNetworkCallback(Context context) {
        if (networkCallback != null) return;

        Context appContext = context.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.i(TAG, "Validated network became available — attempting trusted-time sync");
                syncNow(appContext, success -> {
                    Log.d(TAG, "Connectivity-triggered trusted-time sync " + (success ? "succeeded" : "failed"));
                    if (success) {
                        ScheduleAlarmScheduler.rescheduleAll(appContext);
                        CategoryManager.applyEnforcement(appContext);
                    }
                });
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();

        try {
            cm.registerNetworkCallback(request, networkCallback);
            Log.d(TAG, "TrustedTimeManager: registered ConnectivityManager network callback");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
            networkCallback = null;
        }
    }

    public static void unregisterNetworkCallback(Context context) {
        if (networkCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {
        }
        networkCallback = null;
    }
}
