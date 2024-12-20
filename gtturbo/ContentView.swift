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

// Fix UIColor/NSColor reference for cross-platform
#if os(iOS)
let systemBackground = Color(UIColor.systemBackground)
#else
let systemBackground = Color(NSColor.windowBackgroundColor)
#endif

struct MeasurementDetailView: View {
    let dataFile: GTTurboManager.DataFile
    @State private var rawData: String = ""
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Group {
                    Text("File: \(dataFile.filePath.lastPathComponent)")
                        .font(.headline)
                    Text("Device: \(dataFile.deviceId)")
                    Text("Timestamp: \(dataFile.timestamp.formatted())")
                    
                    if let data = try? Data(contentsOf: dataFile.filePath) {
                        Text("Size: \(data.count) bytes")
                        Text("Raw Data (Hex):")
                            .font(.headline)
                            .padding(.top)
                        Text(data.map { String(format: "%02x", $0) }
                            .enumerated()
                            .map { index, hex in
                                index > 0 && index % 16 == 0 ? "\n\(hex)" : "\(hex)"
                            }
                            .joined(separator: " "))
                            .font(.system(.body, design: .monospaced))
                            .textSelection(.enabled)
                            .padding()
                            .background(Color.secondary.opacity(0.1))
                            .cornerRadius(8)
                    } else {
                        Text("Error loading file data")
                            .foregroundColor(.red)
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
        .navigationTitle("Measurement Details")
    }
}

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: [SortDescriptor(\Item.timestamp, order: .reverse)]) private var items: [Item]
    @StateObject private var bleManager = GTTurboManager()
    @State private var showingDeviceList = false
    @State private var debugLogs: [DebugLog] = []
    @State private var isCollectingData = false
    @State private var showStartConfirmation = false
    @State private var showStopConfirmation = false
    @State private var buttonsDisabled = false
    
    var body: some View {
        NavigationSplitView {
            List {
                // Connection Status Section
                Section {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(statusColor)
                            .frame(width: 12, height: 12)
                        Text(connectionStatusText)
                            .font(.subheadline)
                        Spacer()
                    }
                    .padding(.vertical, 4)
                    
                    // Only show scan button and available devices when disconnected
                    if bleManager.connectionState == .disconnected {
                        Button(action: {
                            toggleScan()
                            logAction(bleManager.isScanning ? "Started scanning" : "Stopped scanning")
                        }) {
                            Text(bleManager.isScanning ? "Stop Scan" : "Scan for GT TURBO")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .frame(height: 44)
                                .background(bleManager.isScanning ? Color.red.opacity(0.1) : Color.blue)
                                .foregroundColor(bleManager.isScanning ? .red : .white)
                                .cornerRadius(12)
                        }
                        
                        // Only show available devices when disconnected
                        if !bleManager.discoveredDevices.isEmpty {
                            ForEach(bleManager.discoveredDevices) { device in
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(device.name)
                                            .font(.headline)
                                        Text("RSSI: \(device.rssi) dBm")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .foregroundColor(.gray)
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    bleManager.connect(to: device)
                                    logAction("Connecting to \(device.name)")
                                }
                            }
                        }
                    }
                } header: {
                    Text("Connection")
                }
                
                // Connected Device Section - Only show when connected
                if case .connected = bleManager.connectionState {
                    Section {
                        HStack {
                            Text("GT TURBO")
                                .font(.headline)
                            Spacer()
                            if let battery = bleManager.batteryLevel {
                                BatteryIndicator(level: battery)
                            }
                        }
                    } header: {
                        Text("Connected Device")
                    }
                    
                    // Measurement Section - Separate section for measurement controls
                    Section {
                        VStack(spacing: 24) {  // Increased spacing between buttons
                            // Start Button - Sends timestamp
                            Button(action: {
                                buttonsDisabled = true
                                bleManager.startMeasurement()
                                
                                // Re-enable after 10 seconds
                                DispatchQueue.main.asyncAfter(deadline: .now() + 10) {
                                    buttonsDisabled = false
                                }
                            }) {
                                HStack {
                                    Image(systemName: "play.circle.fill")
                                        .font(.system(size: 24))
                                    Text("Start New Tracking")
                                        .font(.headline)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.green)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                            }
                            .buttonStyle(BorderlessButtonStyle())  // Prevents tap area expansion
                            .contentShape(Rectangle())  // Defines precise tap area
                            .disabled(bleManager.connectionState != .connected || buttonsDisabled)
                            .opacity(buttonsDisabled ? 0.5 : 1.0)

                            // Stop Button - Sends 0x02
                            Button(action: {
                                buttonsDisabled = true
                                bleManager.stopMeasurement()
                                
                                // Re-enable after 10 seconds
                                DispatchQueue.main.asyncAfter(deadline: .now() + 10) {
                                    buttonsDisabled = false
                                }
                            }) {
                                HStack {
                                    Image(systemName: "stop.circle.fill")
                                        .font(.system(size: 24))
                                    Text("Stop Current Tracking")
                                        .font(.headline)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.red)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                            }
                            .buttonStyle(BorderlessButtonStyle())  // Prevents tap area expansion
                            .contentShape(Rectangle())  // Defines precise tap area
                            .disabled(bleManager.connectionState != .connected || buttonsDisabled)
                            .opacity(buttonsDisabled ? 0.5 : 1.0)
                        }
                        .padding(.vertical, 8)
                    } header: {
                        Text("Tracking Controls")
                    }
                }
                
                // Measurements Section
                Section {
                    ForEach(bleManager.dataFiles) { file in
                        NavigationLink(destination: MeasurementDetailView(dataFile: file)) {
                            HStack {
                                Circle()
                                    .fill(statusColor(for: file.status))
                                    .frame(width: 8, height: 8)
                                
                                VStack(alignment: .leading) {
                                    Text(file.timestamp, format: .dateTime)
                                        .font(.headline)
                                    Text(file.deviceId)
                                        .font(.caption)
                                }
                                
                                Spacer()
                                
                                // Add file size
                                if let size = try? FileManager.default.attributesOfItem(atPath: file.filePath.path)[.size] as? Int64 {
                                    Text("\(size) bytes")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                } header: {
                    Text("Measurement Files")
                }
                
                // Debug Console Section
                Section {
                    VStack(spacing: 12) {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(debugLogs.reversed()) { log in
                                    HStack(spacing: 8) {
                                        // Icon based on log type
                                        Image(systemName: logIcon(for: log.message))
                                            .foregroundColor(logColor(for: log.message))
                                            .frame(width: 24)
                                        
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(log.timestamp.formatted(.dateTime.hour().minute().second()))
                                                .font(.system(.caption2, design: .monospaced))
                                                .foregroundColor(.secondary)
                                            Text(log.message)
                                                .font(.system(.callout, design: .monospaced))
                                                .foregroundColor(logColor(for: log.message))
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                        }
                                    }
                                    .padding(12)
                                    .background(systemBackground)
                                    .cornerRadius(8)
                                    .shadow(color: .black.opacity(0.05), radius: 2, x: 0, y: 1)
                                }
                            }
                            .padding(8)
                        }
                        .frame(maxHeight: 300)
                    }
                } header: {
                    HStack {
                        Text("Debug Console")
                        Spacer()
                        Button(action: clearLogs) {
                            Image(systemName: "trash")
                                .foregroundColor(.red)
                        }
                        .buttonStyle(BorderlessButtonStyle())
                    }
                }
            }
        } detail: {
            Text("Select a measurement")
        }
        .onAppear {
            // Pass ModelContext to GTTurboManager for data storage
            bleManager.setModelContext(modelContext)
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
    
    private func logAction(_ message: String) {
        let log = DebugLog(timestamp: Date(), message: message)
        if debugLogs.count >= 100 { // Limit log entries
            debugLogs.removeFirst()
        }
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
        switch bleManager.connectionState {
        case .connected:
            return .yellow
        case .connecting:
            return .blue
        case .error:
            return .red
        case .disconnected:
            return bleManager.isScanning ? .blue : .red
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
    
    private func logIcon(for message: String) -> String {
        if message.contains("timestamp") {
            return "arrow.up.circle.fill"
        } else if message.contains("Received") {
            return "arrow.down.circle.fill"
        } else if message.contains("Error") || message.contains("Failed") {
            return "exclamationmark.circle.fill"
        } else if message.contains("Connected") {
            return "link.circle.fill"
        } else if message.contains("Started") {
            return "play.circle.fill"
        } else if message.contains("Stopped") {
            return "stop.circle.fill"
        }
        return "info.circle.fill"
    }
    
    private func statusColor(for status: GTTurboManager.FileStatus) -> Color {
        switch status {
        case .receiving: return .red
        case .uploading: return .yellow
        case .uploaded: return .green
        case .none: return .gray
        }
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
    
    return ContentView()
        .modelContainer(container)
        .onAppear {
            let context = container.mainContext
            let sampleItem = Item(timestamp: Date(), deviceId: "Mock Device", sensorData: [1.0, 2.0, 3.0])
            context.insert(sampleItem)
        }
}
