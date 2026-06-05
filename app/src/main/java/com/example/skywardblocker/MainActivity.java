package com.example.skywardblocker;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

public class MainActivity extends AppCompatActivity {

    private TextView titleText;
    private TextView messageText;
    private Button actionButton;
    private Button closeButton;
    private Button testAccButton;
    private Button testDnsButton;
    private Button testApiButton;
    private SetupViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleText = findViewById(R.id.titleText);
        messageText = findViewById(R.id.messageText);
        actionButton = findViewById(R.id.actionButton);
        closeButton = findViewById(R.id.closeButton);
        testAccButton = findViewById(R.id.testAccButton);
        testDnsButton = findViewById(R.id.testDnsButton);
        testApiButton = findViewById(R.id.testApiButton);

        viewModel = new ViewModelProvider(this).get(SetupViewModel.class);
        viewModel.getViewState().observe(this, this::updateUI);
        
        if (testAccButton != null) {
            testAccButton.setOnClickListener(v -> handleActionClick(SetupViewState.Step.ENABLE_ACCESSIBILITY));
        }
        if (testDnsButton != null) {
            testDnsButton.setOnClickListener(v -> handleActionClick(SetupViewState.Step.SETUP_DNS));
        }
        if (testApiButton != null) {
            testApiButton.setOnClickListener(v -> handleActionClick(SetupViewState.Step.FINALIZE_API));
        }

        // Restrict back button usage unless setup is complete (IDLE state)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (StateManager.getState(MainActivity.this) == StateManager.AppState.BLOCKING) {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-evaluate state when returning to the app (e.g., from Settings)
        viewModel.evaluateState();
    }

    private void updateUI(SetupViewState state) {
        if (state == null) return;

        titleText.setText(state.title);
        messageText.setText(state.message);

        // Configure Action Button
        if (state.isActionButtonVisible) {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText(state.actionButtonText);

            // Disable the button if it says Processing... to prevent the ripple effect
            if ("Processing...".equals(state.actionButtonText)) {
                actionButton.setEnabled(false);
                actionButton.setAlpha(0.5f); // Make it look grayed out
            } else {
                actionButton.setEnabled(true);
                actionButton.setAlpha(1.0f);
                actionButton.setOnClickListener(v -> handleActionClick(state.currentStep));
            }
        } else {
            actionButton.setVisibility(View.GONE);
        }

        // Configure Close Button
        if (state.isCloseButtonVisible) {
            closeButton.setVisibility(View.VISIBLE);
            closeButton.setText("Close App");
            closeButton.setOnClickListener(v -> finish());
        } else {
            closeButton.setVisibility(View.GONE);
        }
    }

    private void handleActionClick(SetupViewState.Step currentStep) {
        switch (currentStep) {
            case ENABLE_ACCESSIBILITY:
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                break;
            case SETUP_DNS:
                // 1. Tell the Accessibility Service to expect the DNS screen
                getSharedPreferences("skyward_prefs", MODE_PRIVATE)
                        .edit().putBoolean("auto_configure_dns", true).apply();

                // 2. Launch the Network & Internet settings screen
                try {
                    // This is a real intent. It opens the menu where Private DNS lives.
                    Intent intent2 = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                    startActivity(intent2);
                } catch (Exception e) {
                    Log.d("SkywardDebug", "failed: " + e.getMessage());
                    try {
                        // Safe fallback to the main settings page
                        Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                        startActivity(fallback);
                    } catch (Exception e2) {
                        Log.d("SkywardDebug", "fallback failed: " + e2.getMessage());
                    }
                }
                break;
            case FINALIZE_API:
                viewModel.onFinalizeClicked();
                break;
            case COMPLETE:
                // No action needed here; the close button handles exit
                break;
        }
    }
}