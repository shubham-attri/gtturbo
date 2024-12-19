//
//  ContentView.swift
//  gtturbo
//
//  Created by Shubham Attri on 19/12/24.
//

import SwiftUI
import SwiftData
import CoreBluetooth
#if canImport(UIKit)
import UIKit
#endif

struct MeasurementDetailView: View {
    let item: Item
    
    var body: some View {
        List {
            Section("Timestamp") {
                Text(item.timestamp, format: .dateTime)
            }
            
            Section("Device") {
                Text(item.deviceId)
            }
            
            Section("Sensor Data") {
                ForEach(Array(item.sensorData.enumerated()), id: \.offset) { index, value in
                    HStack {
                        Text("Sensor \(index + 1)")
                        Spacer()
                        Text(String(format: "%.2f", value))
                    }
                }
            }
        }
        .navigationTitle("Measurement Details")
    }
}

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \Item.timestamp, order: .reverse) private var items: [Item]
    @StateObject private var bleManager = GTTurboManager()
    @State private var showingDeviceList = false
    @State private var debugLogs: [DebugLog] = []
    @State private var isCollectingData = false
    
    var body: some View {
        NavigationSplitView {
            List {
                // Connection Section
                Section {
                    VStack(spacing: 24) {
                        // Status Text
                        Text("Scan for GT TURBO")
                            .font(.title2)
                            .foregroundColor(.secondary)
                        
                        // Connection Status Indicator
                        HStack(spacing: 8) {
                            Circle()
                                .fill(statusColor)
                                .frame(width: 12, height: 12)
                            Text(connectionStatusText)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        
                        // Scan Button
                        Button(action: {
                            toggleScan()
                            logAction(bleManager.isScanning ? "Started scanning" : "Stopped scanning")
                        }) {
                            Text(bleManager.isScanning ? "Stop Scan" : "Scan")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(bleManager.isScanning ? Color.red.opacity(0.1) : Color.blue)
                                .foregroundColor(bleManager.isScanning ? .red : .white)
                                .cornerRadius(12)
                        }
                        .disabled(bleManager.connectionState == GTTurboManager.ConnectionState.connected)
                        
                        // Measurement Controls (only show when connected)
                        if case GTTurboManager.ConnectionState.connected = bleManager.connectionState {
                            VStack(spacing: 16) {
                                if let battery = bleManager.batteryLevel {
                                    BatteryIndicator(level: battery)
                                        .padding(.bottom, 8)
                                }
                                
                                Button(action: toggleMeasurement) {
                                    HStack {
                                        Image(systemName: isCollectingData ? "stop.circle.fill" : "play.circle.fill")
                                            .font(.system(size: 24))
                                        Text(isCollectingData ? "Stop" : "Start")
                                            .font(.headline)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 50)
                                    .background(isCollectingData ? Color.red : Color.green)
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                                }
                            }
                            .padding(.top, 8)
                        }
                    }
                    .padding(.vertical)
                }
                
                // Measurements Section
                Section {
                    if items.isEmpty {
                        Text("No measurements recorded")
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(items) { item in
                            NavigationLink {
                                MeasurementDetailView(item: item)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.timestamp, format: .dateTime)
                                        .font(.headline)
                                    Text("Device: \(item.deviceId)")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                        .onDelete(perform: deleteItems)
                    }
                } header: {
                    Text("Recent Measurements")
                }
                
                // Debug Console Section
                Section {
                    VStack(spacing: 12) {
                        // Console Output
                        ScrollView {
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(debugLogs) { log in
                                    VStack(alignment: .leading, spacing: 4) {
                                        // Timestamp
                                        Text(log.timestamp.formatted(.dateTime.hour().minute().second()))
                                            .font(.system(.caption2, design: .monospaced))
                                            .foregroundColor(.secondary)
                                        
                                        // Log Message
                                        Text(log.message)
                                            .font(.system(.caption, design: .monospaced))
                                            .foregroundColor(logColor(for: log.message))
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .padding(8)
                                    .frame(maxWidth: .infinity)
                                    .background(Color.secondary.opacity(0.1))
                                    .cornerRadius(6)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .frame(maxWidth: .infinity, maxHeight: 200)
                        .background(Color.black.opacity(0.05))
                        .cornerRadius(8)
                        
                        // Control Buttons
                        HStack {
                            Button(action: clearLogs) {
                                Label("Clear", systemImage: "trash")
                                    .font(.caption)
                            }
                            .buttonStyle(.borderless)
                            
                            Spacer()
                            
                            Button(action: copyLogs) {
                                Label("Copy", systemImage: "doc.on.doc")
                                    .font(.caption)
                            }
                            .buttonStyle(.borderless)
                        }
                        .padding(.horizontal, 4)
                    }
                } header: {
                    HStack {
                        Text("Debug Console")
                        Spacer()
                        Text("\(debugLogs.count) entries")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
            }
        } detail: {
            Text("Select a measurement")
        }
    }
    
    private var connectionStatusText: String {
        switch bleManager.connectionState {
        case .disconnected:
            return "Not Connected"
        case .connecting:
            return "Connecting..."
        case .connected:
            return "Connected"
        case .error(let message):
            return "Error: \(message)"
        }
    }
    
    private func toggleScan() {
        if bleManager.isScanning {
            bleManager.stopScanning()
        } else {
            bleManager.startScanning()
        }
    }
    
    private func toggleMeasurement() {
        isCollectingData.toggle()
        if isCollectingData {
            bleManager.startMeasurement()
            logAction("Started measurement")
        } else {
            // Add stop measurement functionality to GTTurboManager
            logAction("Stopped measurement")
        }
    }
    
    private func logAction(_ message: String) {
        let log = DebugLog(timestamp: Date(), message: message)
        debugLogs.append(log)
    }
    
    private func deleteItems(offsets: IndexSet) {
        withAnimation {
            for index in offsets {
                modelContext.delete(items[index])
            }
        }
    }
    
    private var statusColor: Color {
        if bleManager.isScanning {
            return .yellow
        }
        switch bleManager.connectionState {
        case .connected:
            return .green
        case .error:
            return .red
        default:
            return .red
        }
    }
    
    private func logColor(for message: String) -> Color {
        if message.contains("Error") || message.contains("Failed") {
            return .red
        } else if message.contains("Connected") || message.contains("Started") {
            return .green
        } else if message.contains("Scanning") {
            return .blue
        }
        return .primary
    }
    
    private func clearLogs() {
        withAnimation {
            debugLogs.removeAll()
        }
    }
    
    private func copyLogs() {
        let logText = debugLogs.map { 
            "[\($0.timestamp.formatted(.dateTime.hour().minute().second()))] \($0.message)" 
        }.joined(separator: "\n")
        
        #if os(iOS)
        UIPasteboard.general.string = logText
        #endif
    }
}

struct BatteryIndicator: View {
    let level: Int
    
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: batteryIcon)
            Text("\(level)%")
        }
        .foregroundColor(batteryColor)
    }
    
    private var batteryIcon: String {
        switch level {
        case 0...20: return "battery.25"
        case 21...50: return "battery.50"
        case 51...80: return "battery.75"
        default: return "battery.100"
        }
    }
    
    private var batteryColor: Color {
        switch level {
        case 0...20: return .red
        case 21...50: return .yellow
        default: return .green
        }
    }
}

struct DebugLog: Identifiable {
    let id = UUID()
    let timestamp: Date
    let message: String
}

#Preview {
    let config = ModelConfiguration(isStoredInMemoryOnly: true)
    let container = try! ModelContainer(for: Item.self, configurations: config)
    
    // Create preview with container
    ContentView()
        .modelContainer(container)
        .onAppear {
            // Add sample data
            let context = container.mainContext
            let sampleItem = Item(timestamp: Date(), deviceId: "Mock Device", sensorData: [1.0, 2.0, 3.0])
            context.insert(sampleItem)
        }
}
