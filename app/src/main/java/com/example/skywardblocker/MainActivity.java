package com.example.skywardblocker;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.compose.ui.platform.ComposeView;

public class MainActivity extends AppCompatActivity {

    private SetupViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ComposeView composeView = new ComposeView(this);
        setContentView(composeView);

        viewModel = new ViewModelProvider(this).get(SetupViewModel.class);
        viewModel.getViewState().observe(this, this::updateUI);

        ComposeBridge.setup(
                composeView,
                this::handleActionClick,
                this::finish,
                () -> handleActionClick(SetupViewState.Step.ENABLE_ACCESSIBILITY),
                () -> handleActionClick(SetupViewState.Step.SETUP_DNS),
                () -> handleActionClick(SetupViewState.Step.FINALIZE_API),
                () -> viewModel.onSkipApiClicked(),
                () -> com.example.skywardblocker.appblock.CategoryManager.forceFetchPopularApps(this),
                () -> com.example.skywardblocker.appblock.CategoryManager.printCache(),
                () -> com.example.skywardblocker.appblock.AppBlockerService.onWarningDismissed()
        );

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
        ComposeBridge.updateState(state);
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