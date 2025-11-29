//
//  StatusView.swift
//  AI Remote App
//
//  Created by Oyeleke Okiki on 11/28/25.
//

import SwiftUI
import CoreBluetooth

struct StatusView: View {
    @StateObject var bleManager: BLEPeripheralManager
    @Binding var isKeySet: Bool // To trigger navigation back to CodeInputView

    @AppStorage("accessKey") private var currentKey: String = ""

    var body: some View {
        VStack(spacing: 30) {

            // MARK: - Header and Connection Indicator

            HStack(spacing: 15) {

                // Status Light
                Circle()
                    .fill(connectionColor)
                    .frame(width: 20, height: 20)
                    .shadow(color: connectionColor.opacity(0.8), radius: 5)

                Text(connectionStatusText)
                    .font(.title2.bold())
                    .foregroundColor(.primary)

            }
            .padding(.bottom, 20)

            // MARK: - Key and Service Information

            VStack(alignment: .leading, spacing: 15) {
                StatusRow(
                    title: "Access Key",
                    value: currentKey,
                    iconName: "key.horizontal.fill"
                )

                StatusRow(
                    title: "BLE Status",
                    value: bleStateText,
                    iconName: "antenna.radiowaves.left.and.right"
                )

                StatusRow(
                    title: "Advertising",
                    value: bleManager.isAdvertising ? "Active" : "Inactive",
                    iconName: bleManager.isAdvertising ? "waveform.path.ecg" : "waveform.path.ecg.slash",
                    color: bleManager.isAdvertising ? .green : .orange
                ).onAppear(perform: {
                    if (!bleManager.isAdvertising && currentKey.count == 4) {
                        bleManager.setKeyAndStartAdvertising(key: currentKey)
                    }

                })

                StatusRow(
                    title: "Active Remotes",
                    value: "\(bleManager.connectedCentralCount)",
                    iconName: "app.connected.to.app.below.fill",
                    color: bleManager.connectedCentralCount > 0 ? .blue : .gray
                )
            }
            .padding()
            .background(Color(NSColor.windowBackgroundColor))
            .cornerRadius(10)
            .frame(maxWidth: 400)

            // MARK: - Received Message Display

//            VStack(alignment: .leading) {
//                Text("Last Received Message:")
//                    .font(.caption)
//                    .foregroundColor(.secondary)
//
//                Text(bleManager.receivedMessage.isEmpty ? "Awaiting message..." : bleManager.receivedMessage)
//                    .font(.callout)
//                    .padding(8)
//                    .frame(maxWidth: .infinity, alignment: .leading)
//                    .background(Color(NSColor.textBackgroundColor).opacity(0.1))
//                    .cornerRadius(5)
//            }
//            .frame(maxWidth: 400)
//            .padding(.top, 20)

            // MARK: - Action Button

            Button {
                // Triggers navigation back to CodeInputView
                isKeySet = false
            } label: {
                Text("Re-initialize Key")
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .frame(minWidth: 200)
            }
            .buttonStyle(.bordered)
            .controlSize(.regular)
            .padding(.top, 20)
        }
        .padding(50)
        .frame(minWidth: 600, minHeight: 400)
    }

    // MARK: - Computed Properties for UI

    private var connectionColor: Color {
        if bleManager.connectedCentralCount > 0 {
            return .green // Connected
        }
        if bleManager.isAdvertising {
            return .yellow // Advertising but not connected
        }
        return .red // Not advertising or powered off
    }

    private var connectionStatusText: String {
        if bleManager.connectedCentralCount > 0 {
            return "Connected to \(bleManager.connectedCentralCount) Device\(bleManager.connectedCentralCount > 1 ? "s" : "")"
        }
        if bleManager.isAdvertising {
            return "Awaiting Connection..."
        }
        return "Service Inactive"
    }

    private var bleStateText: String {
        switch bleManager.currentBLEState {
        case .poweredOn: return "Powered On"
        case .poweredOff: return "Powered Off"
        case .resetting: return "Resetting"
        case .unauthorized: return "Unauthorized"
        case .unsupported: return "Unsupported"
        case .unknown: return "Unknown"
        @unknown default: return "Unknown State"
        }
    }
}

// MARK: - Status Row Helper View

struct StatusRow: View {
    let title: String
    let value: String
    let iconName: String
    var color: Color = .secondary

    var body: some View {
        HStack {
            Image(systemName: iconName)
                .foregroundColor(color)
                .frame(width: 20)

            Text(title)
                .font(.callout)

            Spacer()

            Text(value)
                .font(.body.monospaced())
                .fontWeight(.medium)
        }
    }
}
