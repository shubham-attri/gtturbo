# GT TURBO NIR Spectrography iOS Application

## Overview
GT TURBO is an iOS application designed for Near-Infrared (NIR) Spectrography measurements using Bluetooth Low Energy (BLE) communication. The app interfaces with a custom GT TURBO device that contains an AS7265X spectral sensor, enabling real-time NIR measurements and data collection.

## Features
- Automatic BLE device discovery and connection
- Real-time battery level monitoring
- Independent start/stop measurement controls
- Automatic data collection and storage
- Debug console for monitoring device communication
- Automatic file upload to server after measurement
- Background BLE operation support
- Modern SwiftUI interface with intuitive controls

## Technical Stack
- **iOS Application**:
  - SwiftUI + Combine
  - SwiftData
  - CoreBluetooth
  - URLSession
  - iOS 14.0+

- **Server Infrastructure**:
  - Python FastAPI backend
  - Raw file storage system
  - Nginx reverse proxy
  - ngrok for secure tunneling

## System Architecture

```
┌─────────────────┐     BLE      ┌──────────────┐
│   GT TURBO      │◄──────────►  │ iOS App      │
│  (AS7265X)      │   Protocol   │ (Swift/UIKit)│
└─────────────────┘              └──────┬───────┘
                                       │
                                       │ HTTPS
                                       ▼
                              ┌────────────────┐
                              │   ngrok Tunnel │
                              └────────┬───────┘
                                      │
                                      ▼
┌───────────────────────────────────────────────────┐
│                  Server Stack                      │
│  ┌─────────────┐    ┌─────────────┐              │
│  │   Nginx     │ ►  │  FastAPI    │              │
│  │(Reverse Proxy)   │  Backend    │              │
│  └─────────────┘    └──────┬──────┘              │
│                            │                      │
│               ┌────────────┴──────────┐          │
│               │    Raw File Storage   │          │
│               │                       │          │
│               └─────────────────────┘           │
└───────────────────────────────────────────────────┘
```

## Server Implementation

### Backend Architecture
The server is built using FastAPI, a modern Python web framework, with a single endpoint:

```python
@app.post("/upload-file/")
async def upload_file(file: UploadFile):
    # Store raw measurement file
    return {"status": "success"}
```

### File Storage System
- Direct file system storage for measurement data
- Organized directory structure by date and device ID
- Raw data preservation for maximum flexibility
- Simple file naming convention for easy retrieval

### Data Flow
1. iOS app collects NIR data
2. After 45-second delay, initiates file upload
3. File transmitted via HTTPS through ngrok tunnel
4. Nginx routes request to FastAPI backend
5. FastAPI stores raw measurement file
6. Response sent back to iOS app

### Security Measures
- HTTPS encryption for all communications
- ngrok secure tunneling
- Request validation and sanitization
- File type verification
- Size limits on uploads
- Rate limiting

## Server Deployment

### Prerequisites
- Python 3.9+
- ngrok account
- Domain name (optional)

### Installation Steps
1. Clone server repository
2. Configure environment variables:
   ```bash
   cp .env.example .env
   # Edit .env with your settings
   ```
3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Start FastAPI server:
   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8000
   ```
5. Start ngrok tunnel:
   ```bash
   ngrok http 8000
   ```
6. Update iOS app with new server URL

### Monitoring
- Server health checks
- Request logging
- Error tracking
- Storage space monitoring
- File integrity verification

## Architecture
The application follows a clean architecture pattern with clear separation of concerns:

### BLE Layer
- Implements CoreBluetooth protocols for device communication
- Handles device discovery, connection, and data transfer
- Manages BLE services and characteristics
- Based on Apple's CoreBluetooth framework and inspired by iOS-nRF-Toolbox

### Data Layer
- Manages measurement data persistence
- Handles file creation and management
- Implements server upload functionality
- Uses SwiftData for local storage

### UI Layer
- Modern SwiftUI implementation
- Reactive updates using Combine
- Clean and intuitive user interface
- Real-time status updates

## Implementation Details

### BLE Communication
- **Services**:
  - Battery Service (0x180F)
  - Write Service (0x1818)
  - Read Service (0x1819)
- **Characteristics**:
  - Battery Level (0x2A19)
  - Write Characteristic (0x2A3D)
  - Notify Characteristic (0x2A3E)

### Data Collection
1. Start Command:
   - Sends current timestamp to device
   - Creates new measurement file
   - Begins data collection

2. Data Processing:
   - Receives NIR spectral data
   - Stores raw data in measurement file
   - Updates UI with collection status

3. Stop Command:
   - Sends stop signal to device
   - Finalizes measurement file
   - Initiates upload timer

### File Management
- Creates unique files for each measurement session
- Implements 45-second delay before upload
- Automatic server upload with retry mechanism
- Local storage backup

## Setup Instructions

### Prerequisites
- Xcode 15.0+
- iOS 14.0+
- GT TURBO device
- Active internet connection for file upload

### Installation
1. Clone the repository
2. Open `gtturbo.xcodeproj` in Xcode
3. Configure signing certificate
4. Build and run on target device

### Device Pairing
1. Enable Bluetooth on iOS device
2. Power on GT TURBO device
3. App will automatically discover and connect
4. Verify connection status in app

## Usage Guide

### Starting a Measurement
1. Connect to GT TURBO device
2. Wait for stable connection
3. Press "Start Measurement"
4. Monitor data collection in debug console

### Stopping a Measurement
1. Press "Stop Measurement"
2. Wait for data processing
3. File will automatically upload after 45 seconds
4. Check upload status in recent measurements


## Development References
- [Apple CoreBluetooth Documentation](https://developer.apple.com/documentation/corebluetooth)
- [iOS-nRF-Toolbox](https://github.com/NordicSemiconductor/IOS-nRF-Toolbox)
- [Apple SwiftUI Documentation](https://developer.apple.com/documentation/swiftui)
- [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines)

## Processed File Access

The server exposes two endpoints to interact with processed CSV files:

- **List Files**:
  ```bash
   curl -L https://pro-physically-squirrel.ngrok-free.app/list-processed-files/
  ```

- **Download a File**:
  ```bash
  curl -O https://pro-physically-squirrel.ngrok-free.app/download-file/GT_TURBO_3fa1edc9820a7229_20250510_145045.csv
  ```

Replace `<filename>.csv` with the actual name of the file you want to download.

## Contributing
Contributions are welcome! Please read our contributing guidelines and submit pull requests for any enhancements.

## License
This project is proprietary and confidential. All rights reserved.

## Support
For support and questions, please contact the development team. 