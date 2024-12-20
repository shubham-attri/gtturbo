# GT TURBO NIR Spectrography System Architecture

## System Overview
The GT TURBO NIR Spectrography System is a comprehensive solution for collecting, processing, and analyzing Near-Infrared (NIR) spectral data. The system consists of an iOS application that communicates with a custom GT TURBO device containing an AS7265X spectral sensor via Bluetooth Low Energy (BLE).

## Technical Stack
- **iOS Application**:
  - SwiftUI for modern, declarative UI
  - Combine for reactive programming
  - CoreBluetooth for BLE communication
  - SwiftData for local data persistence
  - URLSession for server communication

- **Microcontroller**:
  - Arduino-based firmware
  - Adafruit Bluefruit library for BLE
  - AS7265X sensor integration
  - SPI Flash for data storage

## Architecture Components

### 1. BLE Layer
#### GTTurboManager
- Singleton manager implementing CoreBluetooth protocols
- Handles device discovery and connection
- Manages BLE services and characteristics
- Implements data transfer protocols
- Battery level monitoring
- Connection state management

#### BLE Services
- **Battery Service (0x180F)**:
  - Standard BLE service for battery monitoring
  - Characteristic: Battery Level (0x2A19)

- **Write Service (0x1818)**:
  - Custom service for command transmission
  - Characteristic: Write (0x2A3D)
  - Handles start/stop commands
  - Timestamp synchronization

- **Read Service (0x1819)**:
  - Custom service for data reception
  - Characteristic: Notify (0x2A3E)
  - Receives NIR spectral data
  - Handles notifications

### 2. Data Layer
#### Data Storage
- SwiftData for structured data persistence
- FileManager for raw measurement data
- Unique file creation per measurement
- Automatic file management system

#### File Upload Service
- REST API integration
- Multipart file upload
- 45-second upload delay implementation
- Retry mechanism for failed uploads
- Upload status tracking

#### Data Processing
- Raw data collection and storage
- Timestamp synchronization
- Data integrity verification
- Real-time data visualization

### 3. UI Layer
#### Views
- **ConnectionView**:
  - Device discovery and connection
  - Connection status display
  - Battery level indicator
  - RSSI strength monitoring

- **MeasurementView**:
  - Start/Stop measurement controls
  - Real-time status updates
  - File status monitoring
  - Upload progress tracking

- **DebugConsoleView**:
  - Command logging
  - Data reception monitoring
  - Error reporting
  - System status updates

## Data Flow
1. **Device Connection**:
   - Automatic scanning for GT TURBO device
   - Service and characteristic discovery
   - Connection state management
   - Battery level monitoring

2. **Measurement Initiation**:
   - Current timestamp transmission
   - File creation with timestamp
   - Start command (0x01) transmission
   - Data collection preparation

3. **Data Collection**:
   - NIR spectral data reception
   - Real-time data storage
   - File system management
   - UI status updates

4. **Measurement Completion**:
   - Stop command (0x02) transmission
   - File finalization
   - Upload timer initiation
   - Status updates

5. **Data Upload**:
   - 45-second delay timer
   - Server communication
   - Upload status tracking
   - Error handling

## Security Considerations
- Secure BLE communication
- Data encryption in transit
- Secure file storage
- Error handling and recovery
- Access control implementation

## Performance Optimization
- Efficient BLE packet handling
- Optimized file operations
- Background task management
- Memory usage optimization
- Battery consumption management

## Future Enhancements
- Enhanced data visualization
- Advanced analytics integration
- Cloud synchronization
- Multiple device support
- Offline operation mode

## Design Patterns
- MVVM architecture
- Observer pattern (Combine)
- Singleton (BLE Manager)
- Delegate pattern (BLE)
- Repository pattern (Data)

## Testing Strategy
- Unit testing core components
- Integration testing BLE stack
- UI testing key workflows
- Performance testing
- Security testing

## References
- Apple CoreBluetooth Framework
- iOS-nRF-Toolbox Implementation
- SwiftUI Best Practices
- BLE Design Guidelines

## System Components

```
┌──────────────────────────────────────────────────────────────────┐
│                     GT TURBO System Architecture                  │
├──────────────┬─────────────────────┬────────────────────────────┤
│  Hardware    │     Mobile App      │        Server              │
├──────────────┼─────────────────────┼────────────────────────────┤
│ AS7265X      │     SwiftUI        │      FastAPI               │
│ Arduino      │     Combine        │      SQLite                │
│ BLE Module   │     CoreBluetooth  │      Nginx                 │
│              │     SwiftData      │      Docker                │
└──────────────┴─────────────────────┴────────────────────────────┘
```

## Data Flow Architecture

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Device    │    │   Mobile    │    │   Server    │    │  Database   │
│   Layer     │◄──►│    Layer    │◄──►│    Layer    │◄──►│    Layer    │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
      │                   │                  │                  │
      ▼                   ▼                  ▼                  ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│ NIR Data    │    │ Data        │    │ API         │    │ Measurement  │
│ Collection  │───►│ Processing  │───►│ Processing  │───►│ Storage     │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

## Server Components

### API Layer
```
┌──────────────────────────────────────────┐
│              FastAPI Backend             │
├──────────────────────────────────────────┤
│ ┌────────────────┐    ┌───────────────┐ │
│ │  File Upload   │    │  Measurement  │ │
│ │   Endpoint    │    │   Endpoints   │ │
│ └────────────────┘    └───────────────┘ │
│ ┌────────────────┐    ┌───────────────┐ │
│ │  Data Process  │    │    Status     │ │
│ │    Service    │    │   Updates     │ │
│ └────────────────┘    └───────────────┘ │
└──────────────────────────────────────────┘
```

### Database Schema
```
┌────────────────────────────────┐
│         Measurements           │
├────────────────┬───────────────┤
│ id             │ TEXT PRIMARY  │
│ timestamp      │ DATETIME      │
│ device_id      │ TEXT          │
│ file_path      │ TEXT          │
│ status         │ TEXT          │
└────────────────┴───────────────┘
```

### Server Infrastructure
```
┌─────────────────────────────────────────┐
│            Docker Container             │
├─────────────────────────────────────────┤
│ ┌─────────────┐      ┌──────────────┐  │
│ │   Nginx     │ ───► │   FastAPI    │  │
│ └─────────────┘      └──────┬───────┘  │
│                             │          │
│                      ┌──────┴───────┐  │
│                      │   SQLite DB  │  │
│                      └──────────────┘  │
└─────────────────────────────────────────┘
```


