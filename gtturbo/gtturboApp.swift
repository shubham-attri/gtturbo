//
//  gtturboApp.swift
//  gtturbo
//
//  Created by Shubham Attri on 19/12/24.
//

import SwiftUI
import SwiftData
import FirebaseCore

// Firebase configuration delegate
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        FirebaseApp.configure()
        return true
    }
}

@main
struct gtturboApp: App {
    // Register app delegate for Firebase setup
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    let container: ModelContainer
    
    init() {
        do {
            let schema = Schema([gtturbo.Item.self])
            let config = ModelConfiguration("GTTurbo", schema: schema)
            container = try ModelContainer(for: schema, configurations: config)
        } catch {
            fatalError("Could not configure SwiftData container: \(error)")
        }
    }
    
    var body: some Scene {
        WindowGroup {
            NavigationView {
                ContentView()
                    .modelContainer(container)
            }
        }
    }
}
