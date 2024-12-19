import Foundation

enum BLEConstants {
    // Service UUIDs (matching Arduino code)
    static let batteryServiceUUID = "180F"  // Standard Battery Service
    static let writeServiceUUID = "1818"    // Custom Write Service
    static let readServiceUUID = "1819"     // Custom Read Service
    
    // Characteristic UUIDs
    static let batteryLevelCharUUID = "2A19" // Standard Battery Level
    static let writeCharUUID = "2A3D"       // Custom Write Characteristic
    static let notifyCharUUID = "2A3E"      // Custom Notify Characteristic
    
    // Commands (matching Arduino code)
    static let startCommand: UInt8 = 0x01
    static let stopCommand: UInt8 = 0x02
} 