package com.example.skywardblocker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skywardblocker.SetupViewState

@Composable
fun SetupScreen(
    state: SetupViewState,
    onActionClicked: () -> Unit,
    onCloseClicked: () -> Unit,
    onTestAccClicked: () -> Unit,
    onTestDnsClicked: () -> Unit,
    onTestApiClicked: () -> Unit,
    onTestSkipApiClicked: () -> Unit,
    onTestFetchAppsClicked: () -> Unit,
    onTestPrintAppsClicked: () -> Unit
) {
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SetupActions(
                    actionButtonText = state.actionButtonText ?: "",
                    isActionButtonVisible = state.isActionButtonVisible,
                    isCloseButtonVisible = state.isCloseButtonVisible,
                    onActionClicked = onActionClicked,
                    onCloseClicked = onCloseClicked
                )

                SetupDebugButtons(
                    onTestAccClicked = onTestAccClicked,
                    onTestDnsClicked = onTestDnsClicked,
                    onTestApiClicked = onTestApiClicked,
                    onTestSkipApiClicked = onTestSkipApiClicked,
                    onTestFetchAppsClicked = onTestFetchAppsClicked,
                    onTestPrintAppsClicked = onTestPrintAppsClicked
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
    onTestApiClicked: () -> Unit,
    onTestSkipApiClicked: () -> Unit,
    onTestFetchAppsClicked: () -> Unit,
    onTestPrintAppsClicked: () -> Unit
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
            TextButton(onClick = onTestApiClicked) { Text("T:API", fontSize = 10.sp, color = Color.Gray) }
            TextButton(onClick = onTestSkipApiClicked) { Text("T:Skip", fontSize = 10.sp, color = Color.Gray) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onTestFetchAppsClicked) { Text("T:FetchApps", fontSize = 10.sp, color = Color.Gray) }
            TextButton(onClick = onTestPrintAppsClicked) { Text("T:PrintApps", fontSize = 10.sp, color = Color.Gray) }
        }
    }
}
