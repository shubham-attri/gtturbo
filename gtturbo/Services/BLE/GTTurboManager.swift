import Foundation
import CoreBluetooth
import Combine
import SwiftData

extension Notification.Name {
    static let newNIRDataReceived = Notification.Name("newNIRDataReceived")
}

public final class GTTurboManager: NSObject, ObservableObject {
    // MARK: - Published Properties
    @Published var discoveredDevices: [BLEDevice] = []
    @Published var isScanning = false
    @Published var connectionState: ConnectionState = .disconnected
    @Published var batteryLevel: Int?
    @Published private(set) var isCollectingData = false
    @Published var currentFileStatus: FileStatus = .none
    @Published var dataFiles: [DataFile] = []
    
    // MARK: - Private Properties
    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?
    private var notifyCharacteristic: CBCharacteristic?
    private var currentMeasurementData: [(timestamp: UInt32, values: [UInt16])] = []
    private var modelContext: ModelContext?
    private var currentFileData: Data = Data()
    private var currentFileStartTime: Date?
    
    enum ConnectionState: Equatable {
        case disconnected
        case connecting
        case connected
        case error(String)
        
        // Implement custom equality comparison
        static func == (lhs: ConnectionState, rhs: ConnectionState) -> Bool {
            switch (lhs, rhs) {
            case (.disconnected, .disconnected),
                 (.connecting, .connecting),
                 (.connected, .connected):
                return true
            case (.error(let lhsMessage), .error(let rhsMessage)):
                return lhsMessage == rhsMessage
            default:
                return false
            }
        }
    }
    
    // Add FileStatus enum
    enum FileStatus {
        case none
        case receiving
        case uploading
        case uploaded
    }
    
    // Add DataFile struct
    struct DataFile: Identifiable {
        let id = UUID()
        let timestamp: Date
        let deviceId: String
        let filePath: URL
        var status: FileStatus = .receiving
    }
    
    // MARK: - Initialization
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    // MARK: - Public Methods
    func startScanning() {
        guard centralManager.state == .poweredOn else { return }
        isScanning = true
        discoveredDevices.removeAll()
        
        // Scan for devices advertising the Battery Service (which our device advertises)
        centralManager.scanForPeripherals(
            withServices: [CBUUID(string: BLEConstants.batteryServiceUUID)],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
    }
    
    func stopScanning() {
        centralManager.stopScan()
        isScanning = false
    }
    
    func connect(to device: BLEDevice) {
        peripheral = device.peripheral
        connectionState = .connecting
        centralManager.connect(device.peripheral, options: nil)
    }
    
    func disconnect() {
        guard let peripheral = peripheral else { return }
        centralManager.cancelPeripheralConnection(peripheral)
    }
    
    func startMeasurement() {
        guard let characteristic = writeCharacteristic,
              let peripheral = peripheral else { return }
        
        print("🟢 Starting measurement...")
        let command: [UInt8] = [BLEConstants.startCommand]
        
        // Create a new file for start command
        currentFileStartTime = Date()
        let fileName = "start_\(currentFileStartTime!.timeIntervalSince1970).dat"
        let fileURL = getDocumentsDirectory().appendingPathComponent(fileName)
        
        do {
            try command.withUnsafeBytes { Data($0) }.write(to: fileURL)
            let newFile = DataFile(
                timestamp: currentFileStartTime!,
                deviceId: peripheral.name ?? "GT TURBO",
                filePath: fileURL,
                status: .receiving
            )
            dataFiles.append(newFile)
            print("💾 Saved start command file: \(fileName)")
        } catch {
            print("❌ Error saving start file: \(error)")
        }
        
        isCollectingData = true
        currentFileStatus = .receiving
        currentFileData = Data()
        
        peripheral.writeValue(Data(command), for: characteristic, type: .withResponse)
    }
    
    func simulateConnection() {
        self.connectionState = .connecting
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            self.connectionState = .connected
            self.isScanning = false
        }
    }
    
    func stopMeasurement() {
        guard let characteristic = writeCharacteristic,
              let peripheral = peripheral else { return }
        
        print("🔴 Stopping measurement...")
        let command: [UInt8] = [BLEConstants.stopCommand]
        
        // Save the data collected during measurement
        if !currentFileData.isEmpty {
            let dataFileName = "data_\(Date().timeIntervalSince1970).dat"
            let dataFileURL = getDocumentsDirectory().appendingPathComponent(dataFileName)
            
            do {
                try currentFileData.write(to: dataFileURL)
                let dataFile = DataFile(
                    timestamp: currentFileStartTime ?? Date(),
                    deviceId: peripheral.name ?? "GT TURBO",
                    filePath: dataFileURL,
                    status: .receiving
                )
                dataFiles.append(dataFile)
                print("💾 Saved measurement data file: \(dataFileName) with \(currentFileData.count) bytes")
            } catch {
                print("❌ Error saving data file: \(error)")
            }
        }
        
        // Create a new file for stop command
        let stopFileName = "stop_\(Date().timeIntervalSince1970).dat"
        let stopFileURL = getDocumentsDirectory().appendingPathComponent(stopFileName)
        
        do {
            try command.withUnsafeBytes { Data($0) }.write(to: stopFileURL)
            let stopFile = DataFile(
                timestamp: Date(),
                deviceId: peripheral.name ?? "GT TURBO",
                filePath: stopFileURL,
                status: .receiving
            )
            dataFiles.append(stopFile)
            print("💾 Saved stop command file: \(stopFileName)")
        } catch {
            print("❌ Error saving stop file: \(error)")
        }
        
        peripheral.writeValue(Data(command), for: characteristic, type: .withResponse)
        isCollectingData = false
        currentFileData = Data()
        currentFileStartTime = nil
    }
    
    func setModelContext(_ context: ModelContext) {
        self.modelContext = context
    }
    
    private func getDocumentsDirectory() -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }
}

// MARK: - CBCentralManagerDelegate
extension GTTurboManager: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            print("Bluetooth is powered on")
        } else {
            connectionState = .error("Bluetooth is not available")
            stopScanning()
        }
    }
    
    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        // Check if this is our GT TURBO device
        if peripheral.name == "GT TURBO" {
            print("Found GT TURBO device!")
            stopScanning() // Stop scanning once we find our device
            let device = BLEDevice(peripheral: peripheral, rssi: RSSI.intValue)
            // Auto-connect to the device
            connect(to: device)
        }
    }
    
    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectionState = .connected
        peripheral.delegate = self
        peripheral.discoverServices([
            CBUUID(string: BLEConstants.batteryServiceUUID),
            CBUUID(string: BLEConstants.writeServiceUUID),
            CBUUID(string: BLEConstants.readServiceUUID)
        ])
    }
    
    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        connectionState = .error(error?.localizedDescription ?? "Failed to connect")
    }
    
    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        connectionState = .disconnected
        self.peripheral = nil
    }
}

// MARK: - CBPeripheralDelegate
extension GTTurboManager: CBPeripheralDelegate {
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil else {
            connectionState = .error(error?.localizedDescription ?? "Service discovery failed")
            return
        }
        
        peripheral.services?.forEach { service in
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }
    
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard error == nil else {
            connectionState = .error(error?.localizedDescription ?? "Characteristic discovery failed")
            return
        }
        
        service.characteristics?.forEach { characteristic in
            switch characteristic.uuid.uuidString {
            case BLEConstants.writeCharUUID:
                writeCharacteristic = characteristic
            case BLEConstants.notifyCharUUID:
                notifyCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            case BLEConstants.batteryLevelCharUUID:
                peripheral.readValue(for: characteristic)
            default:
                break
            }
        }
    }
    
    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil else {
            print("Error updating value: \(error?.localizedDescription ?? "")")
            return
        }
        
        switch characteristic.uuid.uuidString {
        case BLEConstants.batteryLevelCharUUID:
            if let data = characteristic.value {
                batteryLevel = Int(data[0])
            }
        case BLEConstants.notifyCharUUID:
            if let data = characteristic.value {
                // Append data to current file data
                currentFileData.append(data)
                print("📦 Received data packet: \(data.count) bytes")
                
                // Save data periodically or when it reaches a certain size
                if currentFileData.count >= 1024 * 10 { // Save every 10KB
                    let fileName = "data_\(Date().timeIntervalSince1970).dat"
                    let fileURL = getDocumentsDirectory().appendingPathComponent(fileName)
                    
                    do {
                        try currentFileData.write(to: fileURL)
                        let newFile = DataFile(
                            timestamp: currentFileStartTime ?? Date(),
                            deviceId: peripheral.name ?? "GT TURBO",
                            filePath: fileURL,
                            status: .receiving
                        )
                        dataFiles.append(newFile)
                        print("💾 Saved data chunk file: \(fileName) with \(currentFileData.count) bytes")
                        currentFileData = Data() // Clear the buffer after saving
                    } catch {
                        print("❌ Error saving data chunk: \(error)")
                    }
                }
            }
        default:
            break
        }
    }
    
    public func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if error == nil {
            print("Command successfully sent to GT TURBO")
        } else {
            print("Error sending command: \(error?.localizedDescription ?? "Unknown error")")
        }
    }
} 
