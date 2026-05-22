package com.example.skywardblocker;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.skywardblocker.appblock.AppBlockerService;

public class SetupViewModel extends AndroidViewModel {
    private final MutableLiveData<SetupViewState> stateLiveData = new MutableLiveData<>();

    private boolean isProcessingApi = false;
    public SetupViewModel(@NonNull Application application) {
        super(application);
        evaluateState();
    }

    public LiveData<SetupViewState> getViewState() {
        return stateLiveData;
    }

    public void evaluateState() {
        Application app = getApplication();
        StateManager.AppState currentState = StateManager.getState(app);

        switch (currentState) {
            case START_SCREEN:
            case ACCESSIBILITY_SCREEN:
                // Check if the condition is already met. If yes, auto-advance.
                if (isAccessibilityServiceEnabled(app, AppBlockerService.class)) {
                    StateManager.nextState(app);
                    evaluateState(); // Recursively evaluate the new state
                    return;
                }

                stateLiveData.setValue(new SetupViewState(
                        "Step 1: Action Required",
                        "Please enable Accessibility Options for Skyward Blocker.",
                        "Open Settings",
                        true,
                        false,
                        SetupViewState.Step.ENABLE_ACCESSIBILITY
                ));
                break;

            /*case LAUNCHER_SELECTION: // Ensure this is uncommented in StateManager
                stateLiveData.setValue(new SetupViewState(
                        "Step 2: Setup Olauncher",
                        "Please set 'Olauncher' as your default Home App to complete the setup.",
                        "Select Launcher",
                        true,
                        false,
                        SetupViewState.Step.SET_LAUNCHER
                ));
                break;*/

            case EXIT_KIOSK: // Using this as your API / Finalize step based on your enum
                stateLiveData.setValue(new SetupViewState(
                        "Step 2: Finalize",
                        "Click below to register your device and finalize setup.",
                        "Complete Setup",
                        true,
                        false,
                        SetupViewState.Step.FINALIZE_API
                ));
                break;

            case BLOCKING:
                stateLiveData.setValue(new SetupViewState(
                        "Skyward Blocker",
                        "All setup steps are complete! Service is active and running securely.",
                        null,
                        false,
                        true,
                        SetupViewState.Step.COMPLETE
                ));
                break;
        }
    }

    // Called by MainActivity when the launcher button is clicked
    /*public void onLauncherAttempted() {
        // Move to the next state (EXIT_KIOSK/Finalize) and refresh UI
        StateManager.nextState(getApplication());
        evaluateState();
    }*/

    // Called by MainActivity when the API button is clicked
    public void onFinalizeClicked() {
        if (isProcessingApi) return;
        String serialNumber = getMdmProvidedSerialNumber(getApplication());

        if (serialNumber == null) {
            Log.e("SkywardDebug", "Could not read Serial Number from MDM restrictions.");
            // Optional: You could show a Toast or update UI here indicating failure
            // return; // Uncomment this to stop if not managed by MDM during production

            // For testing locally without MDM, you might want to hardcode a fallback serial here:
            // serialNumber = "YOUR_TEST_SERIAL";
        }

        isProcessingApi = true;
        stateLiveData.setValue(new SetupViewState(
                "Finalizing...",
                "Please wait, communicating with MDM server...",
                "Processing...",
                true,
                false,
                SetupViewState.Step.FINALIZE_API
        ));

        // Call our separated API script
        MdmApiClient.finalizeDeviceSetup(serialNumber, new MdmApiClient.MdmCallback() {
            @Override
            public void onSuccess(String memberId) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    isProcessingApi = false;
                    // Save it locally so we never have to look it up again
                    StateManager.setMemberId(getApplication(), memberId);

                    StateManager.nextState(getApplication());
                    evaluateState();
                });
            }
            @Override
            public void onError(String errorMessage) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    isProcessingApi = false; // Unlock so they can try again
                    Log.e("SkywardDebug", "Finalization Failed: " + errorMessage);

                    // Push an error state so the user knows it failed
                    stateLiveData.setValue(new SetupViewState(
                            "Network Error",
                            "Failed to reach MDM server. Check connection and try again.\nError: " + errorMessage,
                            "Retry",
                            true,
                            false,
                            SetupViewState.Step.FINALIZE_API
                    ));
                });
            }
        });
    }

    private String getMdmProvidedSerialNumber(Context context) {
        RestrictionsManager restrictionsManager =
                (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);

        if (restrictionsManager != null) {
            Bundle appRestrictions = restrictionsManager.getApplicationRestrictions();
            // CHANGE: Updated key to match the XML restriction key "mdm_serial_number"
            if (appRestrictions != null && appRestrictions.containsKey("mdm_serial_number")) {
                String serial = appRestrictions.getString("mdm_serial_number");
                Log.d("SkywardDebug", "Retrieved MDM Serial: " + serial);
                return serial;
            }
        }
        return null;
    }

    public void onResetToKioskClicked() {
        // Fetch the cached ID instead of the serial number
        String cachedMemberId = StateManager.getMemberId(getApplication());

        MdmApiClient.moveDeviceToKiosk(cachedMemberId, new MdmApiClient.MdmCallback() {
            @Override
            public void onSuccess(String memberId) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    // Wipe the cached ID and reset state
                    StateManager.setMemberId(getApplication(), null);
                    StateManager.resetState(getApplication());
                    evaluateState();
                });
            }
            @Override
            public void onError(String errorMessage) {
                Log.e("SkywardDebug", "Rollback Failed: " + errorMessage);
            }
        });
    }

    private boolean isAccessibilityServiceEnabled(Application context, Class<?> accessibilityService) {
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