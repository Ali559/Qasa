//
//  Item.swift
//  qasa
//
//  Created by MacbookShop on 8/2/26.
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
