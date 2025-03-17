package com.example.gtturbo

import android.Manifest
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@ExperimentalPermissionsApi
@Composable
fun BlePermissionHandler(
    onPermissionsGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    val permissionsToRequest = remember {
        mutableListOf<String>().apply {
            // Bluetooth permissions based on API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            
            // Storage permissions based on API level
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)
    val showRationale = remember { mutableStateOf(false) }

    if (permissionsState.allPermissionsGranted) {
        LaunchedEffect(Unit) {
            onPermissionsGranted()
        }
        content()
    } else {
        PermissionRequestContent(
            permissionsState = permissionsState,
            showRationale = showRationale.value,
            onDismissRationale = { showRationale.value = false },
            onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
        )
    }
}

@ExperimentalPermissionsApi
@Composable
private fun PermissionRequestContent(
    permissionsState: MultiplePermissionsState,
    showRationale: Boolean,
    onDismissRationale: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    if (showRationale) {
        AlertDialog(
            onDismissRequest = onDismissRationale,
            title = { Text("Permissions Required") },
            text = { Text("Bluetooth, location, and storage permissions are required for this app to function properly. Bluetooth and location permissions are needed to scan for and connect to BLE devices. Storage permissions are needed to save data files.") },
            confirmButton = {
                Button(onClick = {
                    onDismissRationale()
                    onRequestPermissions()
                }) {
                    Text("Request Permissions")
                }
            },
            dismissButton = {
                Button(onClick = onDismissRationale) {
                    Text("Cancel")
                }
            }
        )
    } else {
        LaunchedEffect(Unit) {
            onRequestPermissions()
        }
        Text("Requesting permissions...")
    }
} 