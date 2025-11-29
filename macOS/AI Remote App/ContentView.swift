//
//  ContentView.swift
//  AI Remote App
//
//  Created by Oyeleke Okiki on 11/28/25.
//

import SwiftUI

let script = """
    tell application "Finder"
        empty the trash
    end tell

    """

struct ContentView: View {

    // 1. Check if an access key has been saved previously
    // This value drives the main navigation
    @AppStorage("accessKey") private var savedKey: String = ""
    @State private var isKeySet: Bool

    // 2. Initialize the BLE manager as a StateObject to persist it across views
    @StateObject private var bleManager = BLEPeripheralManager()

    init() {
        // Set the initial state based on AppStorage
        let initialKeySet = UserDefaults.standard.string(forKey: "accessKey")?.count == 4
        _isKeySet = State(initialValue: initialKeySet)

        // If the key is already set, initialize the BLE Manager with it and start service
        if initialKeySet {
            if let key = UserDefaults.standard.string(forKey: "accessKey") {
                bleManager.setKeyAndStartAdvertising(key: key)
            }
        }
    }

    var body: some View {
        // Use a simple switch based on the state to manage the two screens
        Group {
            if isKeySet {
                StatusView(bleManager: bleManager, isKeySet: $isKeySet)
            } else {
                CodeInputView(isKeySet: $isKeySet, bleManager: bleManager, isInitialization: savedKey.isEmpty)
            }
        }
        // Ensure the ContentView has a consistent look
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(NSColor.windowBackgroundColor))
    }
}
