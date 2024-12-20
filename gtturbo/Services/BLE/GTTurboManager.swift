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
        
        // Create a single file for this measurement session
        currentFileStartTime = Date()
        let fileName = "measurement_\(currentFileStartTime!.timeIntervalSince1970).dat"
        let fileURL = getDocumentsDirectory().appendingPathComponent(fileName)
        
        // Send current timestamp to device
        let currentTimestamp = UInt32(Date().timeIntervalSince1970)
        let timestampBytes = withUnsafeBytes(of: currentTimestamp) { Array($0) }
        peripheral.writeValue(Data(timestampBytes), for: characteristic, type: .withResponse)
        
        // Create file and write initial timestamp
        try? Data(timestampBytes).write(to: fileURL)
        
        let newFile = DataFile(
            timestamp: currentFileStartTime!,
            deviceId: peripheral.name ?? "GT TURBO",
            filePath: fileURL,
            status: .receiving
        )
        dataFiles.append(newFile)
        
        isCollectingData = true
        currentFileStatus = .receiving
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
        
        // Create a file for stop command data
        let stopTime = Date()
        let fileName = "measurement_stop_\(stopTime.timeIntervalSince1970).dat"
        let fileURL = getDocumentsDirectory().appendingPathComponent(fileName)
        
        // Create file and write stop command
        try? Data(command).write(to: fileURL)
        
        let newFile = DataFile(
            timestamp: stopTime,
            deviceId: peripheral.name ?? "GT TURBO",
            filePath: fileURL,
            status: .receiving
        )
        dataFiles.append(newFile)
        
        peripheral.writeValue(Data(command), for: characteristic, type: .withResponse)
        isCollectingData = false
        currentFileStatus = .none
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
        guard error == nil else { return }
        
        switch characteristic.uuid.uuidString {
        case BLEConstants.batteryLevelCharUUID:
            if let data = characteristic.value {
                batteryLevel = Int(data[0])
            }
        case BLEConstants.notifyCharUUID:
            if let data = characteristic.value,
               let fileURL = dataFiles.last?.filePath {
                do {
                    let fileHandle = try FileHandle(forWritingTo: fileURL)
                    fileHandle.seekToEndOfFile()
                    fileHandle.write(data)
                    try fileHandle.close()
                    
                    // Force UI update
                    DispatchQueue.main.async {
                        self.objectWillChange.send()
                    }
                    
                    print("📦 Appended \(data.count) bytes to measurement file")
                } catch {
                    print("❌ Error writing to file: \(error)")
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
