package com.example.skywardblocker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skywardblocker.R

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

/**
 * Purely declarative and stateless UI screen for displaying device status and maintenance options.
 * All logic, state transitions, timers, and validation rules are managed by the Java [StatusController].
 */
@Composable
fun StatusScreen(
    state: StatusUiState,
    controller: StatusController
) {
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
                // Top section: logo + status or maintenance mode UI
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(80.dp)
                    )

                    when (state.maintenanceState) {
                        MaintenanceState.NORMAL -> {
                            Text(
                                text = if (state.isDeviceOwner) "Skyward is active" else "Setup required",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.W600,
                                modifier = Modifier.padding(top = 40.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Black
                            )

                            Text(
                                text = if (state.isDeviceOwner)
                                    "Your device is protected. Distracting apps are blocked."
                                else
                                    "Please complete setup using the Skyward desktop app.",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(top = 30.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Black
                            )
                        }

                        MaintenanceState.LOGIN -> {
                            Text(
                                text = "Admin Authentication",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.W600,
                                modifier = Modifier.padding(top = 30.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Black
                            )

                            Text(
                                text = "Log in to temporarily unlock Developer Mode for USB companion app changes.",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.DarkGray
                            )

                            // 1:1 Login UI structure bound directly to controller actions
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.email,
                                    onValueChange = { controller.onEmailChanged(it) },
                                    label = { Text("Email") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = state.password,
                                    onValueChange = { controller.onPasswordChanged(it) },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                            }
                        }

                        MaintenanceState.TIMER_ACTIVE -> {
                            Text(
                                text = "Maintenance Mode Active",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.W600,
                                modifier = Modifier.padding(top = 30.dp),
                                textAlign = TextAlign.Center,
                                color = Color(0xFFD9534F)
                            )

                            Text(
                                text = state.formattedRemainingTime,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 20.dp),
                                color = Color.Black
                            )

                            Text(
                                text = "USB Debugging is unlocked. Connect to the Skyward desktop app to update or remove protections.",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 20.dp),
                                textAlign = TextAlign.Center,
                                color = Color.DarkGray
                            )
                        }
                    }
                }

                // Bottom section: actions & close button
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (state.maintenanceState) {
                        MaintenanceState.NORMAL -> {
                            if (state.isDeviceOwner) {
                                Button(
                                    onClick = { controller.onStartLoginClicked() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2A3C))
                                ) {
                                    Text("Turn on Dev Mode for 10 minutes", color = Color.White)
                                }
                            }
                            TextButton(
                                onClick = { controller.onCloseAppClicked() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Close app",
                                    color = Color(0xFF1B2A3C),
                                    fontWeight = FontWeight.W600
                                )
                            }
                        }

                        MaintenanceState.LOGIN -> {
                            Button(
                                onClick = { controller.onLoginAndUnlockClicked() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.isLoginButtonEnabled,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2A3C))
                            ) {
                                Text("Log in & Unlock", color = Color.White)
                            }
                            TextButton(
                                onClick = { controller.onCancelLoginClicked() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel", color = Color.DarkGray)
                            }
                        }

                        MaintenanceState.TIMER_ACTIVE -> {
                            Button(
                                onClick = { controller.onLockNowClicked() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2A3C))
                            ) {
                                Text("Lock Now & Exit Maintenance", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
