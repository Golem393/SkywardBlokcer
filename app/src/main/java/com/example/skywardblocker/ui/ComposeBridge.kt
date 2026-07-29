package com.example.skywardblocker.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView

object ComposeBridge {
    @JvmStatic
    fun setup(
        composeView: ComposeView,
        controller: StatusController
    ) {
        val stateHolder = mutableStateOf(controller.currentState)
        controller.setStateChangeListener { newState ->
            stateHolder.value = newState
        }

        composeView.setContent {
            val state by stateHolder
            StatusScreen(state = state, controller = controller)
        }
    }
}
