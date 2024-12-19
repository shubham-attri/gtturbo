import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    var deviceId: String
    var sensorData: [Double]
    
    init(timestamp: Date = Date(), deviceId: String = "", sensorData: [Double] = []) {
        self.timestamp = timestamp
        self.deviceId = deviceId
        self.sensorData = sensorData
    }
} 