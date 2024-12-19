import Foundation

struct NIRSpectrographyData {
    let timestamp: UInt32
    let sensorValues: [UInt16]
    
    init(data: Data) {
        // Parse timestamp (first 4 bytes)
        timestamp = data.prefix(4).withUnsafeBytes { $0.load(as: UInt32.self) }
        
        // Parse sensor values (18 values, 2 bytes each)
        var values: [UInt16] = []
        let sensorData = data.dropFirst(4)
        for i in stride(from: 0, to: sensorData.count, by: 2) {
            let value = sensorData[i...i+1].withUnsafeBytes { $0.load(as: UInt16.self) }
            values.append(value)
        }
        sensorValues = values
    }
} 