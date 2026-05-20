package com.example.skywardblocker;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseBlockerActivity extends AppCompatActivity {

    protected TextView titleText;
    protected TextView messageText;
    protected Button actionButton;
    protected Button closeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        titleText = findViewById(R.id.titleText);
        messageText = findViewById(R.id.messageText);
        actionButton = findViewById(R.id.actionButton);
        closeButton = findViewById(R.id.closeButton);

        // Closes the popup (and back button does this automatically by default)
        closeButton.setOnClickListener(v -> finish());

        // Let the child classes set their specific text and buttons
        setupUI();
    }

    protected abstract void setupUI();
}