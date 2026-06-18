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
        onCloseClicked: Runnable
    ) {
        composeView.setContent {
            val state by stateFlow.collectAsState()
            state?.let { currentState ->
                SetupScreen(
                    state = currentState,
                    onActionClicked = { onActionClicked.accept(currentState.currentStep) },
                    onLoginClicked = { e, p -> onLoginClicked.accept(e, p) },
                    onCloseClicked = { onCloseClicked.run() }
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
                onCloseClicked = { onCloseClicked.run() }
            )
        }
    }
}
