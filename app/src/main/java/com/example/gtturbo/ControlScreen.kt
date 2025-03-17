package com.example.gtturbo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ControlScreen(
    bleService: BleService,
    onDisconnect: () -> Unit
) {
    val batteryLevel by bleService.batteryLevel
    val sessionStarted by bleService.sessionStarted
    val sessionStatusMessage by bleService.sessionStatusMessage
    
    val isDataCollectionActive by bleService.isDataCollectionActive
    val fileStoredSuccessfully by bleService.fileStoredSuccessfully
    val fileStatusMessage by bleService.fileStatusMessage
    
    val isUploading by bleService.isUploading
    val uploadStatusMessage by bleService.uploadStatusMessage
    
    // Show messages temporarily
    var showSessionMessage by remember { mutableStateOf(false) }
    var showFileMessage by remember { mutableStateOf(false) }
    var showUploadMessage by remember { mutableStateOf(false) }
    
    // When session status message changes, show the session message
    LaunchedEffect(sessionStatusMessage) {
        if (sessionStatusMessage.isNotEmpty() && sessionStatusMessage.contains("success")) {
            showSessionMessage = true
            delay(5000) // Show for 5 seconds
            showSessionMessage = false
        }
    }
    
    // When file status message changes, show the file message
    LaunchedEffect(fileStatusMessage) {
        if (fileStatusMessage.isNotEmpty()) {
            showFileMessage = true
            if (fileStatusMessage.contains("success")) {
                delay(7000) // Show success message longer (7 seconds)
            } else {
                delay(5000) // Show other messages for 5 seconds
            }
            showFileMessage = false
        }
    }
    
    // When upload status message changes, show the upload message
    LaunchedEffect(uploadStatusMessage) {
        if (uploadStatusMessage.isNotEmpty()) {
            showUploadMessage = true
            delay(7000) // Show for 7 seconds
            showUploadMessage = false
        }
    }
    
    // Softer button colors
    val startButtonColor = Color(0xFF4CAF50) // Softer green
    val stopButtonColor = Color(0xFFE57373)  // Softer red
    
    // Periodically read the battery level
    LaunchedEffect(Unit) {
        while (true) {
            bleService.readBatteryLevel()
            delay(5000) // Update every 5 seconds
        }
    }
    
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "GT TURBO",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Battery section with horizontal layout
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Battery label
                Text(
                    text = "Battery:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
                
                // Battery icon - now smaller and vertical
                BatteryIcon(
                    batteryLevel = batteryLevel,
                    modifier = Modifier.size(40.dp)
                )
                
                // Battery percentage
                Text(
                    text = "$batteryLevel%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                // Start button (Softer Green)
                Button(
                    onClick = { bleService.startSession() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = startButtonColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    enabled = !isDataCollectionActive && !fileStoredSuccessfully && !isUploading
                ) {
                    Text(
                        text = "START",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Stop button (Softer Red)
                Button(
                    onClick = { bleService.stopSession() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = stopButtonColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    enabled = sessionStarted && !isDataCollectionActive && !isUploading
                ) {
                    Text(
                        text = "STOP",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Show data collection in progress indicator
            if (isDataCollectionActive) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD) // Light blue background
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF2196F3) // Blue
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Collecting data...",
                            color = Color(0xFF0D47A1), // Dark blue text
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Show upload in progress indicator
            if (isUploading) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9) // Light green background
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF4CAF50) // Green
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Uploading data to server...",
                            color = Color(0xFF2E7D32), // Dark green text
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Session started message popup
            AnimatedVisibility(
                visible = showSessionMessage && !isDataCollectionActive,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFDFF2D8) // Light green background
                    )
                ) {
                    Text(
                        text = sessionStatusMessage,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF2E7D32), // Dark green text
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // File saved message popup
            AnimatedVisibility(
                visible = showFileMessage,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (fileStoredSuccessfully) Color(0xFFDFF2D8) else Color(0xFFFFEBEE) // Green if success, light red if error
                    )
                ) {
                    Text(
                        text = fileStatusMessage,
                        modifier = Modifier.padding(12.dp),
                        color = if (fileStoredSuccessfully) Color(0xFF2E7D32) else Color(0xFFB71C1C), // Green or red text
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Upload status message popup
            AnimatedVisibility(
                visible = showUploadMessage && !isUploading,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uploadStatusMessage.contains("success")) Color(0xFFDFF2D8) else Color(0xFFFBE9E7) // Green if success, light red if error
                    )
                ) {
                    Text(
                        text = uploadStatusMessage,
                        modifier = Modifier.padding(12.dp),
                        color = if (uploadStatusMessage.contains("success")) Color(0xFF2E7D32) else Color(0xFFBF360C), // Green or red text
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                enabled = !isDataCollectionActive && !isUploading
            ) {
                Text("Disconnect")
            }
        }
    }
} 