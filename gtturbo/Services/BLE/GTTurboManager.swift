import Foundation
import CoreBluetooth
import Combine

final class GTTurboManager: NSObject, ObservableObject {
    // MARK: - Published Properties
    @Published var discoveredDevices: [BLEDevice] = []
    @Published var isScanning = false
    @Published var connectionState: ConnectionState = .disconnected
    @Published var batteryLevel: Int?
    
    // MARK: - Private Properties
    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?
    private var notifyCharacteristic: CBCharacteristic?
    
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
        
        let command: [UInt8] = [BLEConstants.startCommand]
        print("Sending START command (0x01) to GT TURBO")
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
        
        let command: [UInt8] = [BLEConstants.stopCommand]
        print("Sending STOP command (0x02) to GT TURBO")
        peripheral.writeValue(Data(command), for: characteristic, type: .withResponse)
    }
}

// MARK: - CBCentralManagerDelegate
extension GTTurboManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            print("Bluetooth is powered on")
        } else {
            connectionState = .error("Bluetooth is not available")
            stopScanning()
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        // Debug logging
        print("Found device: \(peripheral.name ?? "Unknown")")
        print("Identifier: \(peripheral.identifier)")
        print("RSSI: \(RSSI)")
        print("Advertisement data:")
        advertisementData.forEach { key, value in
            print("  \(key): \(value)")
        }
        
        // Check if this is our GT TURBO device
        if peripheral.name == "GT TURBO" {
            print("Found GT TURBO device!")
            let device = BLEDevice(peripheral: peripheral, rssi: RSSI.intValue)
            if !discoveredDevices.contains(where: { $0.id == device.id }) {
                discoveredDevices.append(device)
            }
        }
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectionState = .connected
        peripheral.delegate = self
        peripheral.discoverServices([
            CBUUID(string: BLEConstants.batteryServiceUUID),
            CBUUID(string: BLEConstants.writeServiceUUID),
            CBUUID(string: BLEConstants.readServiceUUID)
        ])
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        connectionState = .error(error?.localizedDescription ?? "Failed to connect")
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        connectionState = .disconnected
        self.peripheral = nil
    }
}

// MARK: - CBPeripheralDelegate
extension GTTurboManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil else {
            connectionState = .error(error?.localizedDescription ?? "Service discovery failed")
            return
        }
        
        peripheral.services?.forEach { service in
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
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
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
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
                let nirData = NIRSpectrographyData(data: data)
                // Handle the NIR data
                print("Received NIR data with timestamp: \(nirData.timestamp)")
            }
        default:
            break
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if error == nil {
            print("Command successfully sent to GT TURBO")
        } else {
            print("Error sending command: \(error?.localizedDescription ?? "Unknown error")")
        }
    }
} 