#include <bluefruit.h>

// BLE Services and Characteristics
BLEService        batteryService = BLEService(UUID16_SVC_BATTERY);
BLECharacteristic batteryLevelChar = BLECharacteristic(UUID16_CHR_BATTERY_LEVEL);

BLEService        writeService = BLEService(0x1818);  // Custom UUID for Write Service
BLECharacteristic writeChar = BLECharacteristic(0x2A3D);

BLEService        readService = BLEService(0x1819);  // Custom UUID for Read Service
BLECharacteristic notifyChar = BLECharacteristic(0x2A3E);

// Struct to send notifications
struct NIRSpectrographyData {
  uint32_t timestamp;  // Timestamp in milliseconds
  uint16_t sensor[18]; // 18 sensor channels (random values)
} dataPacket;

bool notifyEnabled = false;  // Flag for enabling notifications

// Setup Function
void setup() {
  Serial.begin(115200);
  while (!Serial) delay(10);
  Serial.println("Starting BLE Peripheral...");

  // Initialize Bluefruit
  Bluefruit.begin();
  Bluefruit.setName("GT TURBO");
  Bluefruit.setConnLedInterval(255);  // Bluetooth LED interval

  // Start BLE Services
  startBatteryService();
  startWriteService();
  startReadService();

  // Set Connect/Disconnect Callbacks
  Bluefruit.Periph.setConnectCallback(connectCallback);
  Bluefruit.Periph.setDisconnectCallback(disconnectCallback);

  // Begin Advertising
  startAdvertising();
}

void loop() {
  // Do nothing in the main loop
}

// Callback when connected
void connectCallback(uint16_t conn_handle) {
  Serial.print("Connected to central, handle: ");
  Serial.println(conn_handle);
}

// Callback when disconnected
void disconnectCallback(uint16_t conn_handle, uint8_t reason) {
  (void)conn_handle;
  Serial.println("Disconnected!");
  startAdvertising();  // Restart advertising
}

// Battery Service Setup
void startBatteryService() {
  batteryService.begin();
  batteryLevelChar.setProperties(CHR_PROPS_READ);
  batteryLevelChar.setFixedLen(1);
  batteryLevelChar.begin();

  uint8_t batteryLevel = 60;  // Constant 60% battery
  batteryLevelChar.write8(batteryLevel);
}

// Write Service Setup
void startWriteService() {
  writeService.begin();
  writeChar.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
  writeChar.setWriteCallback(writeCallback);
  writeChar.setMaxLen(12);  // Variable length: 2 or 12
  writeChar.begin();
}

// Read/Notification Service Setup
void startReadService() {
  readService.begin();
  notifyChar.setProperties(CHR_PROPS_NOTIFY);
  notifyChar.setFixedLen(sizeof(NIRSpectrographyData));
  notifyChar.begin();
}

// Callback when Write Service receives data
void writeCallback(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  if (data) {
    Serial.print("Received Data: 0x");
    for (int i = 0; i < len; i++) {
      Serial.printf("%02X", data[i]); // Print each byte in hexadecimal format
    }
    Serial.println();
  } 
  if(len == 1)
  {
    if((int8_t)data[0] == 1)
    {
      sendNotification(millis());
    }

  }
  // else if (len == 6) {
  //   Serial.print("Received Timestamp (12 bytes): ");
  //   for (uint8_t i = 0; i < len; i++) {
  //     Serial.print((int8_t)data[i]);
  //   }
  //   Serial.println();

  //   // Convert data to single numeric value

  // } 
  // else {
  //   Serial.println(len);
  // }
}

// Function to send notifications
void sendNotification(uint32_t timestamp) {
  // Fill the NIRSpectrographyData packet
  dataPacket.timestamp = timestamp;
  for (int i = 0; i < 18; i++) {
    dataPacket.sensor[i] = random(0, 1024);  // Random values for sensor
  }

  // Send the notification
  notifyChar.notify((uint8_t*)&dataPacket, sizeof(dataPacket));
  Serial.println("Notification Sent:");
  Serial.print("Timestamp: "); Serial.println(dataPacket.timestamp);
  for (int i = 0; i < 18; i++) {
    Serial.print("Sensor["); Serial.print(i); Serial.print("] = ");
    Serial.println(dataPacket.sensor[i]);
  }
}

// Function to start advertising
void startAdvertising() {
  // Advertising Packet
  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();
  Bluefruit.Advertising.addService(batteryService);
  Bluefruit.Advertising.addName();

  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(32, 244);    // Advertising interval
  Bluefruit.Advertising.setFastTimeout(30);      // Timeout for fast mode
  Bluefruit.Advertising.start(0);                // Advertise forever
}
