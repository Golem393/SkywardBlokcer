package com.example.skywardblocker.appblock;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen blocking activity shown after the blocked app has been killed.
 * Provides "Close" (go home) and "Uninstall App" options.
 */
public class WarningActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.compose.ui.platform.ComposeView composeView = new androidx.compose.ui.platform.ComposeView(this);
        setContentView(composeView);

        // Enforce immersive fullscreen directly on the activity level safely
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        String blockedPackage = getIntent().getStringExtra("blocked_package");
        String title = getIntent().getStringExtra("title");
        String message = getIntent().getStringExtra("message");
        boolean isSettings = getIntent().getBooleanExtra("is_settings", false);

        Runnable onActionClicked = () -> {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + blockedPackage));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finishAndRemoveTask();
        };

        com.example.skywardblocker.ComposeBridge.setupWarning(
                composeView,
                title != null ? title : "",
                message != null ? message : "",
                !isSettings,
                onActionClicked,
                this::dismissAndGoHome
        );
    }

    /**
     * Dismisses this activity, tells the service we're done, and goes home.
     */
    private void dismissAndGoHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finishAndRemoveTask();
    }

    @Override
    public void onBackPressed() {
        // Prevent back button from dismissing without proper cleanup
        dismissAndGoHome();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // User pressed Home or Recent Apps, we should finish to unblock the service
        finishAndRemoveTask();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Tell the service it's safe to process new events as soon as this activity leaves the screen.
        // Using onStop instead of onDestroy because Android can delay onDestroy indefinitely.
        AppBlockerService.onWarningDismissed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}