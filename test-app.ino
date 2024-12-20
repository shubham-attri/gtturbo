#include <bluefruit.h>
#include <Adafruit_SPIFlash.h>
#include "SparkFun_AS7265X.h"
#include <Wire.h>

void init_BT();
void init_mem();
void init_sensor();
void init_serial();


uint32_t manageTimestamp(uint32_t timestamp);

bool set = false;

struct NIRSpectrographyData {
  uint32_t timestamp;  // Timestamp in milliseconds
  uint16_t sensor[18]; // 18 channels for the single sensor
};

NIRSpectrographyData nirData;

uint32_t currentAddress = 0;
const uint32_t BLOCK_SIZE = 4096;  // Size of a block in flash memory
const uint32_t DATA_SIZE = sizeof(NIRSpectrographyData);
const uint32_t FLASH_SIZE = 1 * 1024 * 1024; 
Adafruit_FlashTransport_QSPI flashTransport;
Adafruit_SPIFlash flash(&flashTransport);

uint32_t rx_timestamp,current_timestamp;

unsigned long previousMillis = 0; 
int sampling_time = 1;
const unsigned long sampling_rate = sampling_time * 60 * 1000;
int numReadings = 3;

AS7265X sensor;


BLEService        batteryService = BLEService(UUID16_SVC_BATTERY);
BLECharacteristic batteryLevelChar = BLECharacteristic(UUID16_CHR_BATTERY_LEVEL);

BLEService        writeService = BLEService(0x1818);  // Custom UUID for Write Service
BLECharacteristic writeChar = BLECharacteristic(0x2A3D);

BLEService        readService = BLEService(0x1819);  // Custom UUID for Read Service
BLECharacteristic notifyChar = BLECharacteristic(0x2A3E);


void setup() 
{
  init_serial();
  init_mem();
  // init_sensor();
  init_bt();
}

void loop()
 {

  if(set)
  {
    unsigned long currentMillis = millis();
    if (currentMillis - previousMillis >= sampling_rate)
    {
      previousMillis = currentMillis;
      Serial.println("Time to take measurements and store data in flash memory");
      int i = 0;
      uint16_t channelSums[18] = {0};

      while(i < numReadings)
      {
        for (int j = 0; j < 18; j++) 
        {
           channelSums[j] += random(0, 101);  // Generate random number between 0 and 100
        }
        i++;
      }

      for (int i = 0; i < 18; i++) 
      {
        nirData.sensor[i] = channelSums[i] / numReadings;
      }
      nirData.timestamp = current_timestamp;
      current_timestamp = manageTimestamp(current_timestamp);
      Serial.println("Average data to be written:");
      Serial.print("Timestamp: ");
      Serial.println(nirData.timestamp);
      for (int i = 0; i < 18; i++) {
        Serial.print("Sensor, Channel ");
        Serial.print(i + 1);
        Serial.print(": ");
        Serial.println(nirData.sensor[i]);
      }
      if (writeDataToFlash()) 
      {
        Serial.println("Data successfully written to flash memory");
      } 
      else 
      {
        Serial.println("Error: Failed to write data to flash memory");
      }
    }
    else 
    {
       __WFE();
    }

  }
  else 
  {
    __WFE(); 
  }
 }


void init_serial()
{
  Serial.begin(115200);
}


void init_mem()
{
  if (!flash.begin()) {
    Serial.println("Error: Failed to initialize flash memory!");
    while (1);
  }
}

void init_bt()
{
  Bluefruit.configPrphBandwidth(BANDWIDTH_MAX);
  Bluefruit.configUuid128Count(15);
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



void connectCallback(uint16_t conn_handle) {
  Serial.print("Connected to central, handle: ");
  Serial.println(conn_handle);
  BLEConnection* connection = Bluefruit.Connection(conn_handle);
  connection->requestMtuExchange(sizeof(nirData) + 3);     
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
  notifyChar.setFixedLen(40);
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

    if(len == 1)
    {
      if(data[0] == 2)
      {
        set = false;
        notify_data();
      }
    }
    if(len == 4)
    {
      bool state = isFlashEmpty(0,flash.size());
      if(state){}
      else
      {
        notify_data();
      }
      rx_timestamp = (data[0] << 24) | (data[1] << 16) | (data[2] << 8) | data[3];
      current_timestamp = rx_timestamp;
      if (!flash.eraseChip()) 
      {
        Serial.println("Error erasing flash memory!");
        while (1);
      }
      Serial.println("Flash memory erased!");
      set = true;
    }
  } 
}

void notify_data() 
{
  delay(2000);
  uint32_t readAddress = 0;

  while (readAddress < 138240) {
    if (!flash.readBuffer(readAddress, (uint8_t*)&nirData, sizeof(nirData))) {
      Serial.println("Error: Failed to read data from flash memory");
      return;
    }

    Serial.println("Data read from flash memory:");
    Serial.print("Timestamp: ");
    Serial.println(nirData.timestamp);
    for (int i = 0; i < 18; i++) {
      Serial.print("Sensor, Channel ");
      Serial.print(i + 1);
      Serial.print(": ");
      Serial.println(nirData.sensor[i]);
    }

    notifyChar.notify((uint8_t*)&nirData, sizeof(nirData));

    readAddress += DATA_SIZE;
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

bool isFlashEmpty(size_t startAddress, size_t length) {
  uint8_t buffer[256]; 
  memset(buffer, 0xFF, sizeof(buffer)); 
  for (size_t i = startAddress; i < startAddress + length; i += sizeof(buffer)) {
    size_t readLength = min(sizeof(buffer), startAddress + length - i);
    if (!flash.readBuffer(i, buffer, readLength)) {
      Serial.println("Failed to read from flash!");
      return false; 
    }
    for (size_t j = 0; j < readLength; j++) {
      if (buffer[j] != 0xFF) {
        return false; // Flash has some data
      }
    }
  }
  
  return true; // Flash is empty
}


uint32_t manageTimestamp(uint32_t timestamp) {
    uint8_t day = (timestamp >> 27) & 0x1F;   // First 5 bits for day (0-31)
    uint8_t month = (timestamp >> 23) & 0x0F; // Next 4 bits for month (0-12)
    uint8_t year = (timestamp >> 18) & 0x1F;  // Next 5 bits for year (0-31)
    uint8_t hour = (timestamp >> 13) & 0x1F;  // Next 5 bits for hour (0-23)
    uint8_t minute = (timestamp >> 7) & 0x3F; // Next 6 bits for minutes (0-59)
    uint8_t second = (timestamp >> 1) & 0x3F; // Next 6 bits for seconds (0-59)

    minute += sampling_time; 
    if (minute >= 60) {
        minute -= 60;
        hour += 1;
    }
    if (hour >= 24) {
        hour = 0;
        day += 1;
    }

    uint8_t daysInMonth[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    uint16_t actualYear = 2000 + year;
    if ((actualYear % 4 == 0 && actualYear % 100 != 0) || (actualYear % 400 == 0)) {
        daysInMonth[1] = 29; // Leap year
    }

    if (day > daysInMonth[month - 1]) {
        day = 1;
        month += 1;
    }

    if (month > 12) {
        month = 1;
        year += 1;
    }

    uint32_t newTimestamp = 0;
    newTimestamp |= (day & 0x1F) << 27;
    newTimestamp |= (month & 0x0F) << 23;
    newTimestamp |= (year & 0x1F) << 18;
    newTimestamp |= (hour & 0x1F) << 13;
    newTimestamp |= (minute & 0x3F) << 7;
    newTimestamp |= (second & 0x3F) << 1;

    return newTimestamp;
}

bool writeDataToFlash() 
{
  if (!flash.writeBuffer(currentAddress, (uint8_t*)&nirData, sizeof(nirData)))
  {
    Serial.println("Error: Failed to write data to flash memory");
    return false;
  }
  currentAddress += DATA_SIZE;
  return true;
}


void init_sensor()
{
  while (sensor.begin() == false)
  {
    Serial.println("Sensor does not appear to be connected. Please check wiring. Freezing...");
  }
  sensor.disableIndicator();
}

