package com.example.skywardblocker;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

public class AppBlockedActivity extends BaseBlockerActivity {

    @Override
    protected void setupUI() {
        titleText.setText("App Blocked");
        messageText.setText("This app was blocked by SkywardBlocker.");

        String blockedPackage = getIntent().getStringExtra("BLOCKED_PACKAGE");

        actionButton.setVisibility(View.VISIBLE);
        actionButton.setText("Uninstall App");

        actionButton.setOnClickListener(v -> {
            if (blockedPackage != null) {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + blockedPackage));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
    }
}