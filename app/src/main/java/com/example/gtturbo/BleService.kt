package com.example.gtturbo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "BleService"
private const val TARGET_DEVICE_NAME = "GT TURBO"

// Server endpoint URL
private const val UPLOAD_SERVER_URL = "https://pro-physically-squirrel.ngrok-free.app/upload-file/"

// Upload retry configuration
private const val MAX_UPLOAD_RETRIES = 10
private const val UPLOAD_RETRY_DELAY_MS = 5000L

// Standard GATT Battery Service UUID
private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
// Standard Battery Level Characteristic UUID
private val BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

// Command service and characteristic UUIDs - for writing timestamp and stop command
private val COMMAND_SERVICE_UUID = UUID.fromString("00001818-0000-1000-8000-00805F9B34FB")
private val COMMAND_CHARACTERISTIC_UUID = UUID.fromString("00002A3D-0000-1000-8000-00805F9B34FB")

// Notification service and characteristic UUIDs - for receiving data
private val NOTIFICATION_SERVICE_UUID = UUID.fromString("00001819-0000-1000-8000-00805F9B34FB")
private val NOTIFICATION_CHARACTERISTIC_UUID = UUID.fromString("00002A3E-0000-1000-8000-00805F9B34FB")

// Client characteristic config descriptor UUID (for enabling notifications)
private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
// Command value to stop session and receive data
private val STOP_COMMAND_VALUE = byteArrayOf(0x01)

class BleService(private val context: Context) {

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    
    private val _scanState = MutableStateFlow(ScanState.NOT_SCANNING)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    val deviceFound = mutableStateOf(false)
    val isConnected = mutableStateOf(false)
    val isScanning = mutableStateOf(false)
    val statusMessage = mutableStateOf("Ready to scan")
    
    // Battery level state
    val batteryLevel = mutableStateOf(0)
    
    // Session state
    val sessionStarted = mutableStateOf(false)
    val sessionStatusMessage = mutableStateOf("")
    
    // Data collection state
    private val receivedNotifications = mutableListOf<Pair<Int, ByteArray>>()
    private var notificationCount = 0
    val isDataCollectionActive = mutableStateOf(false)
    val fileStoredSuccessfully = mutableStateOf(false)
    val fileStatusMessage = mutableStateOf("")
    val isUploading = mutableStateOf(false)
    val uploadStatusMessage = mutableStateOf("")
    
    // Handler for timeout detection
    private val handler = Handler(Looper.getMainLooper())
    private var completionRunnable: Runnable? = null
    private val NOTIFICATION_TIMEOUT = 5000L // 5 seconds timeout
    private var lastNotificationTime = 0L
    
    // Upload retry tracking
    private var uploadRetryCount = 0
    private var uploadRetryHandler: Handler = Handler(Looper.getMainLooper())
    private var currentUploadFile: File? = null
    private val pendingUploads = mutableListOf<File>()
    
    // Session manager for persistent session state
    private val sessionManager = SessionManager(context)
    
    // HTTP client for file uploads
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        // Check for active sessions when the service is created
        checkForActiveSessions()
    }
    
    // Check for active sessions from persistent storage
    private fun checkForActiveSessions() {
        // Check both SharedPreferences and file system for active sessions
        val hasActiveSession = sessionManager.isSessionActive() || sessionManager.checkForActiveSessionsInFileSystem()
        
        if (hasActiveSession) {
            Log.d(TAG, "Found active session in persistent storage")
            sessionStarted.value = true
            sessionStatusMessage.value = "Ongoing session detected"
        } else {
            Log.d(TAG, "No active session found in persistent storage")
            sessionStarted.value = false
        }
    }

    enum class ScanState {
        NOT_SCANNING,
        SCANNING,
        DEVICE_FOUND
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRequiredPermissions()) {
            statusMessage.value = "Missing required permissions"
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            statusMessage.value = "Bluetooth is disabled"
            return
        }
        
        isScanning.value = true
        statusMessage.value = "Scanning for GT TURBO device..."
        _scanState.value = ScanState.SCANNING
        
        val scanFilter = ScanFilter.Builder()
            .setDeviceName(TARGET_DEVICE_NAME)
            .build()
            
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        bluetoothAdapter.bluetoothLeScanner.startScan(
            listOf(scanFilter),
            scanSettings,
            scanCallback
        )
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (hasRequiredPermissions() && bluetoothAdapter.isEnabled) {
            bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        }
        isScanning.value = false
        if (_scanState.value != ScanState.DEVICE_FOUND) {
            statusMessage.value = "Scan stopped"
            _scanState.value = ScanState.NOT_SCANNING
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        statusMessage.value = "Connecting to GT TURBO..."
        _connectionState.value = ConnectionState.CONNECTING
        
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        removePendingCallbacks()
        clearUploadRetryState()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected.value = false
        _connectionState.value = ConnectionState.DISCONNECTED
        statusMessage.value = "Disconnected"
    }
    
    @SuppressLint("MissingPermission")
    fun readBatteryLevel() {
        val gatt = bluetoothGatt ?: return
        
        val batteryService = gatt.getService(BATTERY_SERVICE_UUID) ?: run {
            Log.w(TAG, "Battery service not found")
            return
        }
        
        val batteryCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID) ?: run {
            Log.w(TAG, "Battery level characteristic not found")
            return
        }
        
        gatt.readCharacteristic(batteryCharacteristic)
    }
    
    @SuppressLint("MissingPermission")
    fun enableBatteryNotifications(enable: Boolean) {
        val gatt = bluetoothGatt ?: return
        
        val batteryService = gatt.getService(BATTERY_SERVICE_UUID) ?: run {
            Log.w(TAG, "Battery service not found")
            return
        }
        
        val batteryCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID) ?: run {
            Log.w(TAG, "Battery level characteristic not found")
            return
        }
        
        // Enable notifications on the client side
        gatt.setCharacteristicNotification(batteryCharacteristic, enable)
        
        // Write to the descriptor to enable notifications on the server side
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                batteryCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID),
                if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            )
        } else {
            // For older Android versions
            val descriptor = batteryCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startSession() {
        val gatt = bluetoothGatt ?: run {
            sessionStatusMessage.value = "Error: Not connected to device"
            return
        }
        
        val commandService = gatt.getService(COMMAND_SERVICE_UUID) ?: run {
            sessionStatusMessage.value = "Error: Command service not found"
            Log.w(TAG, "Command service not found")
            return
        }
        
        val commandCharacteristic = commandService.getCharacteristic(COMMAND_CHARACTERISTIC_UUID) ?: run {
            sessionStatusMessage.value = "Error: Command characteristic not found"
            Log.w(TAG, "Command characteristic not found")
            return
        }
        
        // Get current timestamp and encode it
        val timestamp = encodeTimestamp()
        
        // Convert timestamp to bytes (4 bytes for uint32_t)
        val bytes = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN) // Using big endian to match microcontroller implementation
            .putInt(timestamp.toInt())
            .array()
        
        // Write to the characteristic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(commandCharacteristic, bytes, writeType)
        } else {
            // For older Android versions
            commandCharacteristic.setValue(bytes)
            commandCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(commandCharacteristic)
        }
        
        // Update both in-memory and persistent session state
        sessionStarted.value = true
        sessionManager.startSession()
        sessionManager.createSessionMarkerFile()
        
        sessionStatusMessage.value = "Session started successfully!"
        Log.d(TAG, "Session timestamp written: $timestamp")
        
        // Reset data collection state when starting a new session
        resetDataCollection()
    }
    
    @SuppressLint("MissingPermission")
    fun stopSession() {
        val gatt = bluetoothGatt ?: run {
            fileStatusMessage.value = "Error: Not connected to device"
            return
        }
        
        // Getting the notification service for enabling notifications
        val notificationService = gatt.getService(NOTIFICATION_SERVICE_UUID) ?: run {
            fileStatusMessage.value = "Error: Notification service not found"
            Log.w(TAG, "Notification service not found")
            return
        }
        
        // Get the notification characteristic for enabling notifications
        val notificationCharacteristic = notificationService.getCharacteristic(NOTIFICATION_CHARACTERISTIC_UUID) ?: run {
            fileStatusMessage.value = "Error: Notification characteristic not found"
            Log.w(TAG, "Notification characteristic not found")
            return
        }
        
        // Get the command service and characteristic for writing the stop command
        val commandService = gatt.getService(COMMAND_SERVICE_UUID) ?: run {
            fileStatusMessage.value = "Error: Command service not found"
            Log.w(TAG, "Command service not found")
            return
        }
        
        val commandCharacteristic = commandService.getCharacteristic(COMMAND_CHARACTERISTIC_UUID) ?: run {
            fileStatusMessage.value = "Error: Command characteristic not found"
            Log.w(TAG, "Command characteristic not found")
            return
        }
        
        // Reset data collection state
        resetDataCollection()
        isDataCollectionActive.value = true
        fileStatusMessage.value = "Stopping session and collecting data..."
        
        // Set up the completion timeout in case we never get any notifications
        setupCompletionTimeout()
        
        // Step 1: Enable notifications on the notification characteristic
        gatt.setCharacteristicNotification(notificationCharacteristic, true)
        
        // Step 2: Write to the descriptor to enable notifications on the server side
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                notificationCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID),
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
        } else {
            // For older Android versions
            val descriptor = notificationCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
        
        // Step 3: Write the stop command to the command characteristic
        // This will be executed in the descriptor write callback to ensure proper sequence
    }
    
    private fun resetDataCollection() {
        removePendingCallbacks()
        receivedNotifications.clear()
        notificationCount = 0
        isDataCollectionActive.value = false
        fileStoredSuccessfully.value = false
        fileStatusMessage.value = ""
        lastNotificationTime = 0L
        isUploading.value = false
        uploadStatusMessage.value = ""
    }
    
    private fun removePendingCallbacks() {
        completionRunnable?.let {
            handler.removeCallbacks(it)
            completionRunnable = null
        }
    }
    
    private fun setupCompletionTimeout() {
        // Remove any existing callbacks
        removePendingCallbacks()
        
        // Create a new completion runnable
        completionRunnable = Runnable {
            // Only trigger if we're still in data collection mode
            if (isDataCollectionActive.value) {
                // If we received some notifications but now they've stopped, save the data
                if (receivedNotifications.isNotEmpty()) {
                    Log.d(TAG, "Completion timeout triggered. Storing collected data.")
                    storeDataToFile()
                } else {
                    // If we didn't receive any notifications at all after a timeout
                    Log.d(TAG, "No notifications received after timeout.")
                    isDataCollectionActive.value = false
                    fileStatusMessage.value = "No data received from device"
                }
            }
        }
        
        // Schedule this to run after the timeout period
        handler.postDelayed(completionRunnable!!, NOTIFICATION_TIMEOUT)
    }
    
    @SuppressLint("MissingPermission")
    private fun writeStopCommand() {
        val gatt = bluetoothGatt ?: return
        
        val commandService = gatt.getService(COMMAND_SERVICE_UUID) ?: return
        val commandCharacteristic = commandService.getCharacteristic(COMMAND_CHARACTERISTIC_UUID) ?: return
        
        // Write the stop command (0x01)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(commandCharacteristic, STOP_COMMAND_VALUE, writeType)
        } else {
            // For older Android versions
            commandCharacteristic.setValue(STOP_COMMAND_VALUE)
            commandCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(commandCharacteristic)
        }
        
        Log.d(TAG, "Stop command sent")
    }
    
    // Helper function to print byte array as hex string
    private fun ByteArray.toHexString(): String {
        return joinToString("") { String.format("%02X", it) }
    }
    
    // Helper function to log notification data in a readable format
    private fun logNotificationData(index: Int, data: ByteArray) {
        val hexString = data.toHexString()
        val sb = StringBuilder()
        
        sb.append("Notification #$index Data:\n")
        sb.append("Raw Hex: $hexString\n")
        sb.append("Size: ${data.size} bytes\n")
        
        // If data is large, only show beginning and end
        if (data.size > 64) {
            val prefix = data.copyOfRange(0, 32).toHexString()
            val suffix = data.copyOfRange(data.size - 32, data.size).toHexString()
            sb.append("First 32 bytes: $prefix\n")
            sb.append("Last 32 bytes: $suffix\n")
        } else {
            sb.append("Complete data: $hexString\n")
        }
        
        // Try to interpret some values if data has enough bytes
        if (data.size >= 4) {
            val int32Value = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            sb.append("First 4 bytes as Int: $int32Value\n")
        }
        
        if (data.size >= 8) {
            val int64Value = ByteBuffer.wrap(data, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
            sb.append("First 8 bytes as Long: $int64Value\n")
        }
        
        // Print some sample values at different offsets if data is large enough
        if (data.size >= 16) {
            sb.append("Sample bytes at different offsets:\n")
            for (i in 0 until minOf(4, data.size / 4)) {
                val offset = i * (data.size / 4)
                val value = if (offset + 1 < data.size) {
                    ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short
                } else {
                    data[offset].toInt()
                }
                sb.append("  Offset $offset: $value\n")
            }
        }
        
        Log.d(TAG, sb.toString())
    }
    
    private fun storeDataToFile() {
        // Remove any pending completion callbacks
        removePendingCallbacks()
        
        // If we're not in data collection mode anymore or already processed, just return
        if (!isDataCollectionActive.value || fileStoredSuccessfully.value) {
            return
        }
        
        if (receivedNotifications.isEmpty()) {
            fileStatusMessage.value = "No data received to store"
            isDataCollectionActive.value = false
            return
        }
        
        try {
            // Create a JSON array to hold all notifications
            val jsonArray = JSONArray()
            
            // Log summary of all received notifications
            Log.d(TAG, "==== Received ${receivedNotifications.size} total notifications ====")
            
            // Add each notification to the JSON array
            for ((index, notificationData) in receivedNotifications) {
                val jsonObject = JSONObject()
                jsonObject.put("notification_number", index)
                
                // Convert byte array to hex string for JSON storage
                val dataString = notificationData.toHexString()
                jsonObject.put("notification_value", dataString)
                
                jsonArray.put(jsonObject)
                
                // Log each notification's data
                logNotificationData(index, notificationData)
            }
            
            // Create the final JSON object
            val jsonObject = JSONObject()
            jsonObject.put("notifications", jsonArray)
            jsonObject.put("total_notifications", receivedNotifications.size)
            jsonObject.put("device_name", TARGET_DEVICE_NAME)
            jsonObject.put("timestamp", System.currentTimeMillis())
            
            // Format the current date for the filename
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val filename = "GT_TURBO_${dateFormat.format(Date())}.json"
            
            // Get the app's external files directory
            val fileDir = context.getExternalFilesDir(null)
            if (fileDir != null) {
                val file = File(fileDir, filename)
                
                // Write the JSON to the file
                FileOutputStream(file).use { fos ->
                    fos.write(jsonObject.toString().toByteArray())
                }
                
                Log.d(TAG, "Data saved to file: ${file.absolutePath}")
                fileStatusMessage.value = "Session ended and file saved successfully!"
                fileStoredSuccessfully.value = true
                
                // End the session in persistent storage
                sessionStarted.value = false
                sessionManager.endSession(filename)
                sessionManager.removeSessionMarkerFile()
                isDataCollectionActive.value = false
                
                // Check for any pending uploads from previous sessions
                val pendingUploadFiles = loadPendingUploads()
                if (pendingUploadFiles.isNotEmpty()) {
                    Log.d(TAG, "Found ${pendingUploadFiles.size} pending uploads from previous sessions")
                    pendingUploads.addAll(pendingUploadFiles)
                }
                
                // Upload the current file
                uploadFileToServer(file)
            } else {
                fileStatusMessage.value = "Error: Could not access storage"
                Log.e(TAG, "Could not access storage")
                isDataCollectionActive.value = false
            }
        } catch (e: Exception) {
            fileStatusMessage.value = "Error saving file: ${e.message}"
            Log.e(TAG, "Error saving file", e)
            isDataCollectionActive.value = false
        }
    }
    
    // Upload the file to the server
    private fun uploadFileToServer(file: File) {
        // Check if the file exists
        if (!file.exists()) {
            uploadStatusMessage.value = "Error: File does not exist"
            return
        }
        
        isUploading.value = true
        uploadStatusMessage.value = "Uploading data to server..."
        currentUploadFile = file
        uploadRetryCount = 0
        
        performUpload(file)
    }
    
    private fun performUpload(file: File) {
        // Create request body
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/json".toMediaType())
            )
            .build()
        
        // Create request
        val request = Request.Builder()
            .url(UPLOAD_SERVER_URL)
            .post(requestBody)
            .build()
        
        // Execute the request asynchronously
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Upload failed: ${e.message}", e)
                handler.post {
                    if (uploadRetryCount < MAX_UPLOAD_RETRIES) {
                        // Schedule retry after delay
                        uploadRetryCount++
                        uploadStatusMessage.value = "Upload failed, retrying (${uploadRetryCount}/${MAX_UPLOAD_RETRIES})..."
                        Log.d(TAG, "Will retry upload in $UPLOAD_RETRY_DELAY_MS ms (attempt ${uploadRetryCount}/${MAX_UPLOAD_RETRIES})")
                        
                        uploadRetryHandler.postDelayed({
                            performUpload(file)
                        }, UPLOAD_RETRY_DELAY_MS)
                    } else {
                        // Max retries reached, store for next session
                        isUploading.value = false
                        uploadStatusMessage.value = "Upload failed after $MAX_UPLOAD_RETRIES attempts. Will retry in next session."
                        storePendingUpload(file)
                        processPendingUploads()
                    }
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                val message = if (success) "Data uploaded successfully" else "Upload failed: ${response.code}"
                
                Log.d(TAG, "Upload response: $success, ${response.code}")
                
                handler.post {
                    if (success) {
                        isUploading.value = false
                        uploadStatusMessage.value = message
                        currentUploadFile = null
                        
                        // Try to upload any pending files from previous sessions
                        processPendingUploads()
                    } else if (uploadRetryCount < MAX_UPLOAD_RETRIES) {
                        // Schedule retry after delay
                        uploadRetryCount++
                        uploadStatusMessage.value = "Upload failed (HTTP ${response.code}), retrying (${uploadRetryCount}/${MAX_UPLOAD_RETRIES})..."
                        Log.d(TAG, "Will retry upload in $UPLOAD_RETRY_DELAY_MS ms (attempt ${uploadRetryCount}/${MAX_UPLOAD_RETRIES})")
                        
                        uploadRetryHandler.postDelayed({
                            performUpload(file)
                        }, UPLOAD_RETRY_DELAY_MS)
                    } else {
                        // Max retries reached, store for next session
                        isUploading.value = false
                        uploadStatusMessage.value = "Upload failed after $MAX_UPLOAD_RETRIES attempts. Will retry in next session."
                        storePendingUpload(file)
                        processPendingUploads()
                    }
                }
                
                response.close()
            }
        })
    }
    
    private fun processPendingUploads() {
        if (pendingUploads.isEmpty()) {
            return
        }
        
        val nextFile = pendingUploads.removeFirstOrNull()
        if (nextFile != null && nextFile.exists()) {
            Log.d(TAG, "Processing pending upload: ${nextFile.name}")
            uploadStatusMessage.value = "Uploading previous session data: ${nextFile.name}"
            isUploading.value = true
            currentUploadFile = nextFile
            uploadRetryCount = 0
            performUpload(nextFile)
        } else {
            // If file doesn't exist, remove it and try the next one
            processPendingUploads()
        }
    }
    
    private fun storePendingUpload(file: File) {
        // Add to pending uploads if not already there
        if (!pendingUploads.contains(file)) {
            pendingUploads.add(file)
        }
        
        // Save the list of pending uploads to shared preferences
        val pendingUploadPaths = pendingUploads.map { it.absolutePath }
        
        val prefs = context.getSharedPreferences("gtturbo_uploads", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putStringSet("pending_uploads", pendingUploadPaths.toSet())
            apply()
        }
        
        Log.d(TAG, "Stored ${pendingUploads.size} pending uploads for next session")
    }
    
    private fun loadPendingUploads(): List<File> {
        val prefs = context.getSharedPreferences("gtturbo_uploads", Context.MODE_PRIVATE)
        val pendingUploadPaths = prefs.getStringSet("pending_uploads", setOf()) ?: setOf()
        
        return pendingUploadPaths.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) file else null
        }
    }
    
    private fun clearUploadRetryState() {
        uploadRetryHandler.removeCallbacksAndMessages(null)
        uploadRetryCount = 0
        currentUploadFile = null
    }
    
    // Encode timestamp according to the provided algorithm
    private fun encodeTimestamp(): UInt {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        
        val adjustedYear = year - 2000
        
        // Encode the timestamp
        var timestamp: UInt = 0u
        timestamp = timestamp or ((day.toUInt() and 0x1Fu) shl 27)     // Day (5 bits)
        timestamp = timestamp or ((month.toUInt() and 0x0Fu) shl 23)   // Month (4 bits)
        timestamp = timestamp or ((adjustedYear.toUInt() and 0x1Fu) shl 18) // Year (5 bits)
        timestamp = timestamp or ((hour.toUInt() and 0x1Fu) shl 13)    // Hour (5 bits)
        timestamp = timestamp or ((minute.toUInt() and 0x3Fu) shl 7)   // Minute (6 bits)
        timestamp = timestamp or ((second.toUInt() and 0x3Fu) shl 1)   // Second (6 bits)
        
        return timestamp
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: return
            
            if (deviceName == TARGET_DEVICE_NAME) {
                Log.d(TAG, "Found GT TURBO device: ${device.address}")
                deviceFound.value = true
                _scanState.value = ScanState.DEVICE_FOUND
                statusMessage.value = "GT TURBO device found! Connecting..."
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            isScanning.value = false
            _scanState.value = ScanState.NOT_SCANNING
            statusMessage.value = "Scan failed with error: $errorCode"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server.")
                    _connectionState.value = ConnectionState.CONNECTED
                    isConnected.value = true
                    statusMessage.value = "Connected to GT TURBO!"
                    
                    // Discover services
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server.")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    isConnected.value = false
                    statusMessage.value = "Disconnected from GT TURBO"
                    sessionStarted.value = false
                    isDataCollectionActive.value = false
                    gatt.close()
                }
            } else {
                Log.w(TAG, "Connection state change with status: $status")
                _connectionState.value = ConnectionState.DISCONNECTED
                isConnected.value = false
                statusMessage.value = "Connection error: $status"
                sessionStarted.value = false
                isDataCollectionActive.value = false
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                
                // Check if battery service is available
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                if (batteryService != null) {
                    Log.d(TAG, "Battery service found")
                    // Read battery level after finding the service
                    readBatteryLevel()
                    // Enable notifications for battery level
                    enableBatteryNotifications(true)
                } else {
                    Log.w(TAG, "Battery service not available on this device")
                }
                
                // Check if command service is available
                val commandService = gatt.getService(COMMAND_SERVICE_UUID)
                if (commandService != null) {
                    Log.d(TAG, "Command service found")
                } else {
                    Log.w(TAG, "Command service not available on this device")
                }
                
                // Check if notification service is available
                val notificationService = gatt.getService(NOTIFICATION_SERVICE_UUID)
                if (notificationService != null) {
                    Log.d(TAG, "Notification service found")
                } else {
                    Log.w(TAG, "Notification service not available on this device")
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // If we just finished enabling notifications on the notification characteristic
                if (descriptor.characteristic.uuid == NOTIFICATION_CHARACTERISTIC_UUID &&
                    descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                    Log.d(TAG, "Data notifications enabled, sending stop command")
                    // Now we can send the stop command
                    writeStopCommand()
                }
            } else {
                Log.e(TAG, "Descriptor write failed: $status")
                if (isDataCollectionActive.value) {
                    fileStatusMessage.value = "Error enabling notifications"
                    isDataCollectionActive.value = false
                }
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                    val level = value[0].toInt() and 0xFF
                    Log.d(TAG, "Battery level: $level%")
                    batteryLevel.value = level
                }
            } else {
                Log.w(TAG, "onCharacteristicRead failed with status: $status")
            }
        }
        
        // Support for older Android versions
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                    val value = characteristic.value
                    val level = value[0].toInt() and 0xFF
                    Log.d(TAG, "Battery level: $level%")
                    batteryLevel.value = level
                }
            } else {
                Log.w(TAG, "onCharacteristicRead failed with status: $status")
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == COMMAND_CHARACTERISTIC_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // If this is a write to start the session
                    if (sessionStarted.value && !isDataCollectionActive.value) {
                        Log.d(TAG, "Timestamp written successfully")
                        sessionStatusMessage.value = "Session started successfully!"
                    } 
                    // If this is a write to stop the session
                    else if (isDataCollectionActive.value) {
                        Log.d(TAG, "Stop command written successfully")
                        fileStatusMessage.value = "Collecting data..."
                        // Reset the completion timeout after successfully sending the stop command
                        setupCompletionTimeout()
                    }
                } else {
                    Log.e(TAG, "Failed to write characteristic: $status")
                    if (isDataCollectionActive.value) {
                        fileStatusMessage.value = "Error sending stop command"
                        isDataCollectionActive.value = false
                    } else {
                        sessionStatusMessage.value = "Error: Failed to start session"
                    }
                }
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                val level = value[0].toInt() and 0xFF
                Log.d(TAG, "Battery level notification: $level%")
                batteryLevel.value = level
            } 
            else if (characteristic.uuid == NOTIFICATION_CHARACTERISTIC_UUID) {
                if (isDataCollectionActive.value) {
                    // Remove any pending completion callbacks since we got a notification
                    removePendingCallbacks()
                    
                    // Update the last notification time
                    lastNotificationTime = System.currentTimeMillis()
                    
                    // Process received data notification
                    notificationCount++
                    
                    // Log detailed notification information
                    Log.d(TAG, "===== Received data notification #$notificationCount (${value.size} bytes) =====")
                    logNotificationData(notificationCount, value)
                    
                    // Store the notification data
                    receivedNotifications.add(Pair(notificationCount, value))
                    
                    // Set up a new completion timeout for after this notification
                    completionRunnable = Runnable {
                        Log.d(TAG, "No new notifications for $NOTIFICATION_TIMEOUT ms, considering data collection complete")
                        storeDataToFile()
                    }
                    
                    // Post the completion runnable to execute after the timeout
                    handler.postDelayed(completionRunnable!!, NOTIFICATION_TIMEOUT)
                }
            }
        }
        
        // Support for older Android versions
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                val value = characteristic.value
                val level = value[0].toInt() and 0xFF
                Log.d(TAG, "Battery level notification: $level%")
                batteryLevel.value = level
            }
            else if (characteristic.uuid == NOTIFICATION_CHARACTERISTIC_UUID) {
                if (isDataCollectionActive.value) {
                    // Remove any pending completion callbacks since we got a notification
                    removePendingCallbacks()
                    
                    // Update the last notification time
                    lastNotificationTime = System.currentTimeMillis()
                    
                    // Process received data notification
                    val value = characteristic.value
                    notificationCount++
                    
                    // Log detailed notification information
                    Log.d(TAG, "===== Received data notification #$notificationCount (${value.size} bytes) =====")
                    logNotificationData(notificationCount, value)
                    
                    // Store the notification data
                    receivedNotifications.add(Pair(notificationCount, value))
                    
                    // Set up a new completion timeout for after this notification
                    completionRunnable = Runnable {
                        Log.d(TAG, "No new notifications for $NOTIFICATION_TIMEOUT ms, considering data collection complete")
                        storeDataToFile()
                    }
                    
                    // Post the completion runnable to execute after the timeout
                    handler.postDelayed(completionRunnable!!, NOTIFICATION_TIMEOUT)
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
} 