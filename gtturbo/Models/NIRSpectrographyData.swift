import Foundation

struct NIRSpectrographyData {
    let timestamp: UInt32
    let sensorValues: [UInt16]
    
    init(data: Data) {
        // Debug print raw data
        print("📦 Raw data received: \(data.map { String(format: "%02x", $0) }.joined(separator: " "))")
        print("📏 Data length: \(data.count) bytes")
        
        // Expected size check
        let expectedSize = 4 + (18 * 2) // 4 bytes timestamp + (18 sensors × 2 bytes)
        if data.count != expectedSize {
            print("⚠️ Warning: Received \(data.count) bytes, expected \(expectedSize) bytes")
        }
        
        // Parse timestamp (first 4 bytes)
        timestamp = data.prefix(4).withUnsafeBytes { $0.load(as: UInt32.self) }
        print("⏰ Timestamp: \(timestamp)")
        
        // Parse sensor values (18 values, 2 bytes each)
        var values: [UInt16] = []
        let sensorData = data.dropFirst(4)
        
        // Process in pairs of bytes
        stride(from: 0, to: sensorData.count - 1, by: 2).forEach { i in
            let value = sensorData[i...i+1].withUnsafeBytes { $0.load(as: UInt16.self) }
            values.append(value)
            print("📊 Sensor \(values.count): \(value)")
        }
        
        sensorValues = values
        print("✅ Parsed \(values.count) sensor values")
    }
} 