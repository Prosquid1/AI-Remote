//
//  BluetoothPeripheral.swift
//  AI Remote App
//
//  Created by Oyeleke Okiki on 11/28/25.
//

import Combine
import CoreBluetooth
import SwiftUI

class BLEPeripheralManager: NSObject, ObservableObject, CBPeripheralManagerDelegate {

    // MARK: - Published Properties
    @Published var receivedMessage = ""
    @Published var isAdvertising = false
    @Published var connectedCentralCount: Int = 0
    @Published var currentBLEState: CBManagerState = .unknown

    // MARK: - Configuration
    private var peripheralManager: CBPeripheralManager!
    private var characteristic: CBMutableCharacteristic?

    var serviceUUID: CBUUID {
        return CBUUID(string: "12345678-\(broadcastKey)-1234-1234-1234567890AB")
    }

    // The key used for the local advertising name (the "password")
    var broadcastKey = "1234" // default is 1234

    override init() {
        super.init()
        // Initialize the manager; delegate methods will handle the state changes
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }

    // MARK: - Peripheral Delegate Methods

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        currentBLEState = peripheral.state
        print("Peripheral state updated: \(peripheral.state.rawValue)")

        if peripheral.state == .poweredOn {
            // Only attempt to set up when powered on
            setupPeripheral()
        } else if peripheral.state == .poweredOff {
            // Stop advertising flags when off
            isAdvertising = false
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: (any Error)?) {
        if let error = error {
            print("❌ Failed to add service: \(error.localizedDescription)")
        } else {
            print("✅ Service added successfully. Starting advertising...")
            startAdvertising()
        }
    }


    // Called when a central subscribes to the characteristic (connects)
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        connectedCentralCount += 1
        print("➡️ Central subscribed. Current connections: \(connectedCentralCount)")
    }

    // Called when a central unsubscribes from the characteristic (disconnects)
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        connectedCentralCount = max(0, connectedCentralCount - 1)
        print("⬅️ Central unsubscribed. Current connections: \(connectedCentralCount)")
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: (any Error)?) {
        if let error = error {
            print("❌ Advertising failed: \(error.localizedDescription)")
            isAdvertising = false
        } else {
            print("✅ Advertising started successfully with key: \(broadcastKey)")
            isAdvertising = true
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager,
                           didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if let value = request.value {
                receivedMessage = String(data: value, encoding: .utf8) ?? "Received unreadable data"
                print("Received message: \(receivedMessage)")

                if (receivedMessage != "PING!") { runAppleScript(receivedMessage.replacingOccurrences(of: "```", with: ""), completion: {a, b in}) }

                // Respond to the request to acknowledge the write
                peripheral.respond(to: request, withResult: .success)
            }
        }
    }

    // MARK: - Control Methods

    // Public method to set the key and refresh the advertising state
    // Now cleaner, relying on the encapsulated restart logic.
    func setKeyAndStartAdvertising(key: String) {
        // 1. Update the key
        self.broadcastKey = key

        // 2. Automatically restart the service with the new key
        restartAdvertisingService()
    }

    // Internal method to handle stopping and restarting the advertising process
    private func restartAdvertisingService() {
        // 1. Stop old advertising
        connectedCentralCount = 0
        peripheralManager.stopAdvertising()
        peripheralManager.removeAllServices()
        isAdvertising = false

        // 2. Restart setup which will re-advertise with the new key
        setupPeripheral()
    }

    // Internal setup to add service and characteristic
    private func setupPeripheral() {


        characteristic = CBMutableCharacteristic(
            type: CBUUID(string: "87654321-4321-4321-4321-BA0987654321"),
            properties: [.read, .write, .notify],
            value: nil,
            permissions: [.readable, .writeable]
        )

        let newService = CBMutableService(type: serviceUUID, primary: true)
        newService.characteristics = [characteristic!]

        peripheralManager.add(newService)
    }

    // Helper function for advertising
    private func startAdvertising() {
        // Check if we are already advertising to avoid errors
        guard !peripheralManager.isAdvertising else { return }

        peripheralManager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [serviceUUID],
            CBAdvertisementDataLocalNameKey: broadcastKey // Broadcasts the key for identification/verification
        ])
    }

    // Note: The sendMessage method from the original code remains available if needed
    func sendMessage(_ message: String) {
        guard let characteristic = characteristic else { return }
        let data = message.data(using: .utf8)!
        let sent = peripheralManager.updateValue(data,
                                        for: characteristic,
                                        onSubscribedCentrals: nil)
        if sent {
            print("Message sent: \(message)")
        } else {
            print("Failed to send message: \(message) (No subscribers or buffer full)")
        }
    }
}

//Deprecated
func cbuuid(fromFourDigit code: String) -> CBUUID? {
    // Ensure the string is exactly 4 hex characters
    guard code.count == 4, Int(code, radix: 16) != nil else {
        return nil
    }

    let baseUUID = "0000\(code.uppercased())-0000-1000-8000-00805F9B34FB"
    return CBUUID(string: baseUUID)
}
