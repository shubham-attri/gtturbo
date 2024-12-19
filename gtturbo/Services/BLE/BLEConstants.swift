import Foundation

enum BLEConstants {
    // Service UUIDs
    static let batteryServiceUUID = "180F"
    static let writeServiceUUID = "1818"
    static let readServiceUUID = "1819"
    
    // Characteristic UUIDs
    static let batteryLevelCharUUID = "2A19"
    static let writeCharUUID = "2A3D"
    static let notifyCharUUID = "2A3E"
    
    // Commands
    static let startCommand: UInt8 = 0x01
    static let stopCommand: UInt8 = 0x02
} 