import CoreBluetooth
import iOS_BLE_Library_Mock

//class MockPeripheral: CBPeripheral {
//    private let mockName: String
//    private let mockIdentifier: UUID
//    
//    init(name: String, identifier: UUID) {
//        self.mockName = name
//        self.mockIdentifier = identifier
//        super.init()
//    }
//    
//    required init?(coder: NSCoder) {
//        fatalError("init(coder:) has not been implemented")
//    }
//    
//    var mockState: CBPeripheralState = .disconnected
//    
//    override var state: CBPeripheralState {
//        return mockState
//    }
//    
//    override var name: String? {
//        return mockName
//    }
//    
//    override var identifier: UUID {
//        return mockIdentifier
//    }
//} 
