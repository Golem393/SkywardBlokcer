package com.example.skywardblocker.blocking;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.skywardblocker.receiver.PackageChangeReceiver;
import com.example.skywardblocker.schedule.ScheduleAlarmScheduler;
import com.example.skywardblocker.time.TrustedTimeManager;



/**
 * Lightweight Foreground Service that runs continuously in memory.
 *
 * Required on Android 8.0+ (API 26+): Android ignores implicit broadcast receivers
 * (like ACTION_PACKAGE_ADDED) registered in the manifest while the app is dormant.
 * This service keeps the process alive so PackageChangeReceiver is always active.
 *
 * Because Skyward is set as Device Owner, Android's battery optimizer cannot terminate
 * this service, guaranteeing any newly installed app is instantly evaluated and suspended.
 */
public class AppMonitorService extends Service {

    private static final String TAG = "SkywardDebug";
    private static final String CHANNEL_ID = "skyward_monitor_channel";
    private static final int NOTIFICATION_ID = 4091;

    private static final long SAFETY_TICK_MS = 60_000L;

    private PackageChangeReceiver packageChangeReceiver;
    private boolean isReceiverRegistered = false;
    private final Handler tickHandler = new Handler(Looper.getMainLooper());

    private final Runnable safetyTick = new Runnable() {
        @Override
        public void run() {
            if (TrustedTimeManager.getCurrentTrustedEpochMillis(AppMonitorService.this) == null) {
                // Bounded polling backstop, independent of the ConnectivityManager callback:
                // keep retrying the trusted-time sync while no anchor exists.
                TrustedTimeManager.syncNow(AppMonitorService.this, success -> {
                    if (success) ScheduleAlarmScheduler.rescheduleAll(AppMonitorService.this);
                });
            }
            tickHandler.postDelayed(this, SAFETY_TICK_MS);
        }
    };

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, AppMonitorService.class);
            context.startForegroundService(intent);
            Log.d(TAG, "AppMonitorService start requested");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AppMonitorService", e);
        }
    }

    public static void stop(Context context) {
        try {
            Intent intent = new Intent(context, AppMonitorService.class);
            context.stopService(intent);
            Log.d(TAG, "AppMonitorService stop requested");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop AppMonitorService", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AppMonitorService onCreate");
        setupForegroundNotification();
        registerPackageReceiver();
        TrustedTimeManager.registerNetworkCallback(this);
        tickHandler.removeCallbacks(safetyTick);
        tickHandler.postDelayed(safetyTick, SAFETY_TICK_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupForegroundNotification();
        registerPackageReceiver();

        // Also ensure cached blocked apps are actively (un)enforced per the current schedule
        // whenever service commands start
        CategoryManager.applyEnforcement(this);

        return START_STICKY; // Ensure OS restarts service if memory ever reloads
    }

    private void setupForegroundNotification() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Skyward Active Protection",
                        NotificationManager.IMPORTANCE_MIN // Minimal intrusiveness / silent
                );
                channel.setDescription("Ensures continuous device compliance and distraction blocking.");
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Skyward is active")
                    .setContentText("Device compliance protection running in background.")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setOngoing(true)
                    .build();

            if (Build.VERSION.SDK_INT >= 34) {
                // API 34+ requires an explicit foreground service type (Device Owners qualify for systemExempt)
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground notification in AppMonitorService", e);
        }
    }

    private synchronized void registerPackageReceiver() {
        if (!isReceiverRegistered) {
            try {
                packageChangeReceiver = new PackageChangeReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_PACKAGE_ADDED);
                filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
                filter.addDataScheme("package");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(packageChangeReceiver, filter);
                }
                isReceiverRegistered = true;
                Log.i(TAG, "Successfully registered dynamic PackageChangeReceiver in memory!");
            } catch (Exception e) {
                Log.e(TAG, "Failed to register PackageChangeReceiver in AppMonitorService", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w(TAG, "AppMonitorService onDestroy");
        tickHandler.removeCallbacks(safetyTick);
        TrustedTimeManager.unregisterNetworkCallback(this);
        if (isReceiverRegistered && packageChangeReceiver != null) {
            try {
                unregisterReceiver(packageChangeReceiver);
            } catch (Exception ignored) {}
            isReceiverRegistered = false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
