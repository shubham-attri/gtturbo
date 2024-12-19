//
//  gtturboApp.swift
//  gtturbo
//
//  Created by Shubham Attri on 19/12/24.
//

import SwiftUI
import SwiftData

@main
struct gtturboApp: App {
    let container: ModelContainer
    
    init() {
        do {
            let schema = Schema([Item.self])
            let config = ModelConfiguration("GTTurbo", schema: schema)
            container = try ModelContainer(for: schema, configurations: config)
        } catch {
            fatalError("Could not configure SwiftData container: \(error)")
        }
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .modelContainer(container)
        }
    }
}
