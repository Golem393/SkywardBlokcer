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
import com.example.skywardblocker.dns.DnsAutoSetupScript;

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
            case LOGIN_SCREEN:
                stateLiveData.setValue(new SetupViewState(
                        "Step 1: Authentication",
                        "Please sign in to continue setup.",
                        "Login",
                        true,
                        false,
                        SetupViewState.Step.LOGIN
                ));
                break;

            case START_SCREEN:
            case ACCESSIBILITY_SCREEN:
                // Check if the condition is already met. If yes, auto-advance.
                if (isAccessibilityServiceEnabled(app, AppBlockerService.class)) {
                    StateManager.nextState(app);
                    evaluateState(); // Recursively evaluate the new state
                    return;
                }

                stateLiveData.setValue(new SetupViewState(
                        "Step 2: Action Required",
                        "Please enable Accessibility Options for Skyward Blocker.",
                        "Open Settings",
                        true,
                        false,
                        SetupViewState.Step.ENABLE_ACCESSIBILITY
                ));
                break;

            case DNS_SCREEN:
                // Check if the condition is already met. If yes, auto-advance.
                if (DnsAutoSetupScript.isDnsConfigured(app)) {
                    StateManager.nextState(app);
                    evaluateState(); // Recursively evaluate the new state
                    return;
                }

                stateLiveData.setValue(new SetupViewState(
                        "Step 3: Auto Configure DNS",
                        "Skyward will attempt to automatically configure DNS on your device. Click below to begin.",
                        "Try Auto Setup",
                        true,
                        false,
                        SetupViewState.Step.SETUP_DNS
                ));
                break;

            case DNS_MANUAL_SCREEN:
                // Check if the condition is already met. If yes, auto-advance.
                if (DnsAutoSetupScript.isDnsConfigured(app)) {
                    StateManager.nextState(app);
                    evaluateState(); // Recursively evaluate the new state
                    return;
                }

                stateLiveData.setValue(new SetupViewState(
                        "Step 3: Manual DNS Setup",
                        "Automatic setup failed or your device is unsupported. Please click below to open settings and configure Private DNS manually.",
                        "Open Settings",
                        true,
                        false,
                        SetupViewState.Step.SETUP_DNS_MANUAL
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
                        "Step 4: Finalize",
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

    public void onLoginClicked(String email, String password) {
        if (isProcessingApi) return;

        isProcessingApi = true;
        stateLiveData.setValue(new SetupViewState(
                "Authenticating...",
                "Please wait, communicating with MDM server...",
                "Processing...",
                true,
                false,
                SetupViewState.Step.LOGIN
        ));

        MdmApiClient.authenticateSetup(getApplication(), email, password, new MdmApiClient.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    isProcessingApi = false;
                    StateManager.nextState(getApplication());
                    evaluateState();
                });
            }

            @Override
            public void onError(String errorMessage) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    isProcessingApi = false;
                    stateLiveData.setValue(new SetupViewState(
                            "Login Failed",
                            errorMessage,
                            "Login",
                            true,
                            false,
                            SetupViewState.Step.LOGIN
                    ));
                });
            }
        });
    }

    // Called by MainActivity when the API button is clicked
    public void onFinalizeClicked() {
        if (isProcessingApi) return;

        // Final sanity check before calling API
        if (!isAccessibilityServiceEnabled(getApplication(), AppBlockerService.class) || !DnsAutoSetupScript.isDnsConfigured(getApplication())) {
            Log.e("SkywardDebug", "Conditions not met before finalizing! Re-evaluating state.");
            evaluateState();
            return;
        }

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
        MdmApiClient.finalizeDeviceSetup(getApplication(), serialNumber, new MdmApiClient.MdmCallback() {
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