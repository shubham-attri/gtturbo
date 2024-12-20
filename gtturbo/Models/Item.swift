import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    var deviceId: String
    var rawData: Data
    var sensorData: [Double]
    
    init(timestamp: Date = Date(), deviceId: String = "", rawData: Data = Data(), sensorData: [Double] = []) {
        self.timestamp = timestamp
        self.deviceId = deviceId
        self.rawData = rawData
        self.sensorData = sensorData
    }
} 