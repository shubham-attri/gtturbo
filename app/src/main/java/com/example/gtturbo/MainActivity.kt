package com.example.gtturbo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gtturbo.ui.theme.GTTURBOTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GTTURBOTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BlePermissionHandler(
                        onPermissionsGranted = { /* Permissions granted */ },
                        content = { GTTurboApp() }
                    )
                }
            }
        }
    }
}

enum class AppScreen {
    SCANNING,
    CONTROL
}

@Composable
fun GTTurboApp() {
    val context = LocalContext.current
    val bleService = remember { BleService(context) }
    
    var currentScreen by remember { mutableStateOf(AppScreen.SCANNING) }
    
    val isScanning by bleService.isScanning
    val isConnected by bleService.isConnected
    val statusMessage by bleService.statusMessage
    val scanState by bleService.scanState.collectAsState()
    
    // When connected, switch to the control screen
    LaunchedEffect(isConnected) {
        if (isConnected) {
            currentScreen = AppScreen.CONTROL
        } else {
            currentScreen = AppScreen.SCANNING
        }
    }
    
    // Start scanning when the app launches and we're on the scanning screen
    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.SCANNING) {
            bleService.startScan()
        }
    }
    
    when (currentScreen) {
        AppScreen.SCANNING -> {
            ScanningScreen(
                isScanning = isScanning,
                isConnected = isConnected,
                statusMessage = statusMessage,
                onStartScan = { bleService.startScan() }
            )
        }
        AppScreen.CONTROL -> {
            ControlScreen(
                bleService = bleService,
                onDisconnect = { 
                    bleService.disconnect()
                    currentScreen = AppScreen.SCANNING
                }
            )
        }
    }
}

@Composable
fun ScanningScreen(
    isScanning: Boolean,
    isConnected: Boolean,
    statusMessage: String,
    onStartScan: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "GT TURBO",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (isScanning) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(60.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Scanning for GT TURBO device...",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else if (isConnected) {
                Text(
                    text = "✓ Connected to GT TURBO!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onStartScan
                ) {
                    Text("Scan for GT TURBO")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GTTURBOTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "GT TURBO",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            CircularProgressIndicator(
                modifier = Modifier.size(60.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Scanning for GT TURBO device...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}