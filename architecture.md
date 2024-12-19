# GT TURBO NIR Spectrography System

## System Overview
An iOS application for NIR (Near-Infrared) Spectrography measurements using BLE communication with a GT TURBO device.

## Technical Stack
- CoreBluetooth for BLE communication
- SwiftUI for UI
- Combine for reactive programming

## Architecture Components

### 1. BLE Layer
- GTTurboManager: Singleton BLE manager using CoreBluetooth
- BLEConstants: Service/Characteristic UUIDs and commands
- BLEDataParser: Parsing NIR data packets

### 2. Data Layer
- LocalStorage: CoreData for local data persistence
- APIService: Mock API service (to be replaced with real implementation)
- DataManager: Coordinates between BLE, storage, and API

### 3. UI Layer
- ConnectionView: BLE connection management
- DataCollectionView: Start/Stop data collection
- DebugConsoleView: Debug information and logs
- BatteryIndicator: Device battery status

## Data Flow
1. User connects to GT TURBO device via BLE
2. Start command (0x01) initiates data collection
3. Device sends NIR data packets via notifications
4. Data is stored locally and queued for server upload
5. Stop command (0x02) ends data collection
6. Collected data is processed and synchronized

## BLE Services
- Battery Service (0x180F)
- Write Service (0x1818)
- Read Service (0x1819)

## Data Storage
- Local storage using CoreData
- Backup/sync mechanism for server upload
- Debug logs persistence


