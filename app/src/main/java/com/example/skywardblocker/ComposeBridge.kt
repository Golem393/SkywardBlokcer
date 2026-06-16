package com.example.skywardblocker

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.skywardblocker.ui.SetupScreen

object ComposeBridge {
    private val stateFlow = MutableStateFlow<SetupViewState?>(null)

    @JvmStatic
    fun setup(
        composeView: ComposeView,
        onActionClicked: java.util.function.Consumer<SetupViewState.Step>,
        onLoginClicked: java.util.function.BiConsumer<String, String>,
        onCloseClicked: Runnable,
        onTestAccClicked: Runnable,
        onTestDnsClicked: Runnable,
        onClearCacheClicked: Runnable,
        onTestSkipApiClicked: Runnable,
        onTestPrintAppsClicked: Runnable,
        onTestResetWarningClicked: Runnable,
        onTestResetStateClicked: Runnable,
        onTestLoginClicked: Runnable
    ) {
        composeView.setContent {
            val state by stateFlow.collectAsState()
            state?.let { currentState ->
                SetupScreen(
                    state = currentState,
                    onActionClicked = { onActionClicked.accept(currentState.currentStep) },
                    onLoginClicked = { e, p -> onLoginClicked.accept(e, p) },
                    onCloseClicked = { onCloseClicked.run() },
                    onTestAccClicked = { onTestAccClicked.run() },
                    onTestDnsClicked = { onTestDnsClicked.run() },
                    onClearCacheClicked = { onClearCacheClicked.run() },
                    onTestSkipApiClicked = { onTestSkipApiClicked.run() },
                    onTestPrintAppsClicked = { onTestPrintAppsClicked.run() },
                    onTestResetWarningClicked = { onTestResetWarningClicked.run() },
                    onTestResetStateClicked = { onTestResetStateClicked.run() },
                    onTestLoginClicked = { onTestLoginClicked.run() }
                )
            }
        }
    }

    @JvmStatic
    fun updateState(newState: SetupViewState) {
        stateFlow.value = newState
    }

    @JvmStatic
    fun setupWarning(
        composeView: ComposeView,
        title: String,
        message: String,
        isActionButtonVisible: Boolean,
        onActionClicked: Runnable,
        onCloseClicked: Runnable
    ) {
        composeView.setContent {
            val state = SetupViewState(
                title,
                message,
                "Uninstall App",
                isActionButtonVisible,
                true,
                SetupViewState.Step.COMPLETE
            )
            SetupScreen(
                state = state,
                onActionClicked = { onActionClicked.run() },
                onLoginClicked = { _, _ -> },
                onCloseClicked = { onCloseClicked.run() },
                onTestAccClicked = {},
                onTestDnsClicked = {},
                onClearCacheClicked = {},
                onTestSkipApiClicked = {},
                onTestPrintAppsClicked = {},
                onTestResetWarningClicked = {},
                onTestResetStateClicked = {},
                onTestLoginClicked = {}
            )
        }
    }
}
