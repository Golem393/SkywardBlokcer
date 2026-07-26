package com.example.skywardblocker.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView

object ComposeBridge {
    @JvmStatic
    fun setup(
        composeView: ComposeView,
        isDeviceOwner: Boolean,
        onCloseClicked: Runnable
    ) {
        composeView.setContent {
            StatusScreen(
                isDeviceOwner = isDeviceOwner,
                onCloseClicked = { onCloseClicked.run() }
            )
        }
    }
}
