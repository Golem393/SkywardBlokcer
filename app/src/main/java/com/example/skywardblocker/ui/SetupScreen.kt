package com.example.skywardblocker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.skywardblocker.SetupViewState

@Composable
fun SetupScreen(
    state: SetupViewState,
    onActionClicked: () -> Unit,
    onLoginClicked: (String, String) -> Unit,
    onCloseClicked: () -> Unit,
    onTestAccClicked: () -> Unit,
    onTestDnsClicked: () -> Unit,
    onClearCacheClicked: () -> Unit,
    onTestSkipApiClicked: () -> Unit,
    onTestPrintAppsClicked: () -> Unit,
    onTestResetWarningClicked: () -> Unit,
    onTestResetStateClicked: () -> Unit,
    onTestLoginClicked: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SetupHeader(
                title = state.title ?: "",
                message = state.message ?: ""
            )

            if (state.currentStep == SetupViewState.Step.LOGIN) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SetupActions(
                    actionButtonText = state.actionButtonText ?: "",
                    isActionButtonVisible = state.isActionButtonVisible,
                    isCloseButtonVisible = state.isCloseButtonVisible,
                    onActionClicked = {
                        if (state.currentStep == SetupViewState.Step.LOGIN) {
                            onLoginClicked(email, password)
                        } else {
                            onActionClicked()
                        }
                    },
                    onCloseClicked = onCloseClicked
                )

                SetupDebugButtons(
                    onTestAccClicked = onTestAccClicked,
                    onTestDnsClicked = onTestDnsClicked,
                    onClearCacheClicked = onClearCacheClicked,
                    onTestSkipApiClicked = onTestSkipApiClicked,
                    onTestPrintAppsClicked = onTestPrintAppsClicked,
                    onTestResetWarningClicked = onTestResetWarningClicked,
                    onTestResetStateClicked = onTestResetStateClicked,
                    onTestLoginClicked = onTestLoginClicked
                )
            }
        }
    }
}

@Composable
private fun SetupHeader(title: String, message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SKYWARD",
            fontSize = 16.sp,
            color = Color.Black
        )
        
        Text(
            text = title,
            fontSize = 30.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(top = 50.dp),
            textAlign = TextAlign.Center,
            color = Color.Black
        )
        
        Text(
            text = message,
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 50.dp),
            textAlign = TextAlign.Center,
            color = Color.Black
        )
    }
}

@Composable
private fun SetupActions(
    actionButtonText: String,
    isActionButtonVisible: Boolean,
    isCloseButtonVisible: Boolean,
    onActionClicked: () -> Unit,
    onCloseClicked: () -> Unit
) {
    if (isActionButtonVisible) {
        val isProcessing = actionButtonText == "Processing..."
        Button(
            onClick = onActionClicked,
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1B2A3C),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF1B2A3C).copy(alpha = 0.5f),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(30.dp)
        ) {
            Text(
                text = actionButtonText.uppercase(),
                fontWeight = FontWeight.W600,
                fontSize = 16.sp
            )
        }
    }

    if (isCloseButtonVisible) {
        TextButton(
            onClick = onCloseClicked,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CLOSE APP", color = Color(0xFF1B2A3C), fontWeight = FontWeight.W600)
        }
    }
}

@Composable
private fun SetupDebugButtons(
    onTestAccClicked: () -> Unit,
    onTestDnsClicked: () -> Unit,
    onClearCacheClicked: () -> Unit,
    onTestSkipApiClicked: () -> Unit,
    onTestPrintAppsClicked: () -> Unit,
    onTestResetWarningClicked: () -> Unit,
    onTestResetStateClicked: () -> Unit,
    onTestLoginClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onTestAccClicked) { Text("T:Acc", fontSize = 10.sp, color = Color.Gray) }
            TextButton(onClick = onTestDnsClicked) { Text("T:DNS", fontSize = 10.sp, color = Color.Gray) }
            TextButton(onClick = onClearCacheClicked) { Text("T:ClearCache", fontSize = 10.sp, color = Color.Gray) }
            TextButton(onClick = onTestSkipApiClicked) { Text("T:Skip", fontSize = 10.sp, color = Color.Gray) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onTestLoginClicked) { Text("T:Login", fontSize = 10.sp, color = Color.Gray) }
            TextButton(onClick = onTestPrintAppsClicked) { Text("T:PrintApps", fontSize = 10.sp, color = Color.Gray) }
            TextButton(onClick = onTestResetWarningClicked) { Text("T:ResetLock", fontSize = 10.sp, color = Color.Gray) }
            TextButton(onClick = onTestResetStateClicked) { Text("T:ResetState", fontSize = 10.sp, color = Color.Gray) }
        }
    }
}
