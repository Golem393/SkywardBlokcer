package com.example.skywardblocker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends AppCompatActivity {

    private TextView titleText;
    private TextView messageText;
    private Button actionButton;
    private Button closeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleText = findViewById(R.id.titleText);
        messageText = findViewById(R.id.messageText);
        actionButton = findViewById(R.id.actionButton);
        closeButton = findViewById(R.id.closeButton);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (StateManager.isSetupComplete(MainActivity.this)) {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean isAccEnabled = isAccessibilityServiceEnabled(this, AppBlockerService.class);
        boolean isLauncherSet = StateManager.isLauncherAttempted(this);
        boolean isSetupComplete = StateManager.isSetupComplete(this);

        if (!isAccEnabled) {
            // STEP 1
            StateManager.setSetupComplete(this, false);

            titleText.setText("Step 1: Action Required");
            messageText.setText("Please enable Accessibility Options for Skyward Blocker.");

            actionButton.setText("Open Settings");
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            });
            closeButton.setVisibility(View.GONE);

        } else if (!isLauncherSet) {
            // STEP 2
            StateManager.setSetupComplete(this, false);

            titleText.setText("Step 2: Setup Olauncher");
            messageText.setText("Please set 'Olauncher' as your default Home App to complete the setup.");
            actionButton.setText("Select Launcher");
            actionButton.setVisibility(View.VISIBLE);
            closeButton.setVisibility(View.GONE);

            actionButton.setOnClickListener(v -> {
                StateManager.setLauncherAttempted(MainActivity.this, true);
                openOlauncher();
            });

        } else if (!isSetupComplete) {
            // STEP 3: API Call Trigger
            titleText.setText("Step 3: Finalize");
            messageText.setText("Click below to register your device and finalize setup.");

            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText("Complete Setup");
            closeButton.setVisibility(View.GONE);

            actionButton.setOnClickListener(v -> {
                // Fire the API call in the background
                new Thread(() -> {
                    try {
                        URL url = new URL("https://mdm-backend-i4b0.onrender.com/api/move-member-to-official");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("PUT");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);

                        String jsonInputString = "{\"memberId\": \"217106000000134084\"}";

                        try(OutputStream os = conn.getOutputStream()) {
                            byte[] input = jsonInputString.getBytes("utf-8");
                            os.write(input, 0, input.length);
                        }
                        conn.getResponseCode();
                    } catch (Exception e) {
                        Log.e("SkywardDebug", "API Call failed", e);
                    }
                }).start();

                // Mark as complete and immediately refresh UI to show the final screen
                StateManager.setSetupComplete(MainActivity.this, true);
                updateUI();
            });

        } else {
            // FINAL SCREEN
            titleText.setText("Skyward Blocker");
            messageText.setText("All setup steps are complete! Service is active and running securely in the background.");

            actionButton.setVisibility(View.GONE);

            closeButton.setVisibility(View.VISIBLE);
            closeButton.setText("Close App");
            closeButton.setOnClickListener(v -> finish());
        }
    }

    private void openOlauncher() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("app.olauncher");
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
            Log.d("SkywardDebug", "Opened Olauncher");
        } else {
            Log.e("SkywardDebug", "Olauncher not found. Is it installed?");
            // Optional: Send them to the Play Store or fallback to the old settings menu here
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        ComponentName expectedComponentName = new ComponentName(context, accessibilityService);
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (enabledServicesSetting == null) return false;

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);

        while (colonSplitter.hasNext()) {
            ComponentName enabledService = ComponentName.unflattenFromString(colonSplitter.next());
            if (enabledService != null && enabledService.equals(expectedComponentName)) {
                return true;
            }
        }
        return false;
    }
}