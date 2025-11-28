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
    private var peripheralManager: CBPeripheralManager!
    private var characteristic: CBMutableCharacteristic?

    var broadcastKey = ""

    let serviceUUID = CBUUID(string: "12345678-1234-1234-1234-1234567890AB")
    let characteristicUUID = CBUUID(string: "87654321-4321-4321-4321-BA0987654321")

    @Published var receivedMessage = ""

    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print("Peripheral state updated: \(peripheral.state.rawValue)")
        if peripheral.state == .poweredOn {
            // Check the flag before attempting setup
            setupPeripheral()
        } else {
            print("❌ Cannot setup peripheral. State is not poweredOn.")
        }
    }

    func setupPeripheral() {

        startAdvertising()

        characteristic = CBMutableCharacteristic(
            type: characteristicUUID,
            properties: [.read, .write, .notify],
            value: nil,
            permissions: [.readable, .writeable]
        )

        let newService = CBMutableService(type: serviceUUID, primary: true)
        newService.characteristics = [characteristic!]

        // Start the asynchronous process of adding the service
        peripheralManager.add(newService)
        print("Awaiting service addition confirmation...")
    }

    // CRITICAL DELEGATE METHOD: Confirmation that service was added
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: (any Error)?) {
        if let error = error {
            print("❌ Failed to add service: \(error.localizedDescription)")

        } else {
            print("✅ Service added successfully.")
            // ONLY start advertising AFTER the service is confirmed to be added
            startAdvertising()
        }
    }

    // Helper function for advertising
    func startAdvertising() {
        peripheralManager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [serviceUUID],
            CBAdvertisementDataLocalNameKey: broadcastKey
        ])
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: (any Error)?) {
        if let error = error {
            print("❌ Advertising failed: \(error.localizedDescription)")
            } else {
                print("✅ Advertising started successfully")
            }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager,
                           didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if let value = request.value {

                receivedMessage = String(data: value, encoding: .utf8) ?? ""
                if (receivedMessage != "PING!") {
                    //     runAppleScript(receivedMessage) { [weak self] message, isError in
                    //     self?.sendMessage(message)
                    //  }
                }

                peripheral.respond(to: request, withResult: .success)
            }
        }
    }

    func sendMessage(_ message: String) {
        guard let characteristic = characteristic else { return }
        let data = message.data(using: .utf8)!
        peripheralManager.updateValue(data,
                                      for: characteristic,
                                      onSubscribedCentrals: nil)
    }


}
