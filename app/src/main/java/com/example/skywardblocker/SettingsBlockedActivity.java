package com.example.skywardblocker;

import android.view.View;

public class SettingsBlockedActivity extends BaseBlockerActivity {

    @Override
    protected void setupUI() {
        titleText.setText("Access Denied");
        messageText.setText("You don't have the permission to change these settings.\n\nInstructed by SkywardBlocker.");

        // Hide the extra button, we only need the close button here
        actionButton.setVisibility(View.GONE);
    }
}