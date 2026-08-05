package com.example.skywardblocker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * Purely declarative and stateless UI screen for displaying device status.
 * All logic and state transitions are managed by the Java [StatusController].
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
                // Top section: logo + status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(80.dp)
                    )

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

                // Bottom section: actions & close button
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isDeviceOwner) {
                        if (state.isScheduleEnabled) {
                            Text(
                                text = state.scheduleStatusText,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 8.dp),
                                textAlign = TextAlign.Center,
                                color = Color.DarkGray
                            )
                            if (!state.isScheduleCurrentlyLocked) {
                                Button(
                                    onClick = { controller.onScheduleLockNowClicked() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2A3C))
                                ) {
                                    Text("Lock Now", color = Color.White)
                                }
                            }
                        }
                        if (state.isBlockingScheduled) {
                            if (state.isGraceActive) {
                                Text(
                                    text = state.graceStatusText,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF2E7D32)
                                )
                            } else if (state.graceUsesRemaining > 0) {
                                Button(
                                    onClick = { controller.onUnlockBreakClicked() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) {
                                    Text(
                                        "Unlock apps for 2 min (${state.graceUsesRemaining} left today)",
                                        color = Color.White
                                    )
                                }
                            } else {
                                Text(
                                    text = "No unlocks left today",
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    textAlign = TextAlign.Center,
                                    color = Color.Gray
                                )
                            }
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
            }
        }
    }
}
