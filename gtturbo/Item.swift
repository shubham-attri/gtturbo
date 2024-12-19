//
//  Item.swift
//  gtturbo
//
//  Created by Shubham Attri on 19/12/24.
//

import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    
    init(timestamp: Date) {
        self.timestamp = timestamp
    }
}
