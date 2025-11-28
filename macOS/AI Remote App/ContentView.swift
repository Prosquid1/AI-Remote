//
//  ContentView.swift
//  AI Remote App
//
//  Created by Oyeleke Okiki on 11/28/25.
//

import SwiftUI

struct ContentView: View {

    let script = """
        tell application "Finder"
            empty the trash
        end tell

        """
    @StateObject private var bleManager = BLEPeripheralManager()

    var body: some View {
         VStack {
            Text("Received: \(bleManager.receivedMessage)")
            Button("Send to Android") {
//                runAppleScript(script, completion: { a, b in
//                    debugPrint(a)
//
//                })
                //bleManager.sendMessage("Hello from macOS")
            }
        }
    }
}
