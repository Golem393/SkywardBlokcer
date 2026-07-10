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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.skywardblocker.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

val InterFontFamily = FontFamily(Font(R.font.inter))

private val defaultTypography = Typography()
val InterTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = InterFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = InterFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = InterFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = InterFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = InterFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = InterFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = InterFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = InterFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = InterFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = InterFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = InterFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = InterFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = InterFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = InterFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = InterFontFamily)
)

@Composable
fun SetupScreen(
    state: SetupViewState,
    onActionClicked: () -> Unit,
    onLoginClicked: (String, String) -> Unit,
    onCloseClicked: () -> Unit,
    onBypassClicked: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    MaterialTheme(typography = InterTypography) {
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

                    if (state.currentStep == SetupViewState.Step.FINALIZE_API) {
                        TextButton(
                            onClick = onBypassClicked,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bypass (Test)", color = Color.Gray, fontWeight = FontWeight.W600)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupHeader(title: String, message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "App Logo",
            modifier = Modifier.size(80.dp)
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
                text = actionButtonText,
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
            Text("Close app", color = Color(0xFF1B2A3C), fontWeight = FontWeight.W600)
        }
    }
}
