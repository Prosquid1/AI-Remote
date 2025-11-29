//
//  CodeInputView.swift
//  AI Remote App
//
//  Created by Oyeleke Okiki on 11/28/25.
//

import SwiftUI

struct CodeInputView: View {
    @State private var codeInput: String = ""
    @Binding var isKeySet: Bool // Bound to the main view's state
    @StateObject var bleManager: BLEPeripheralManager

    // Determines if the key is being initialized or re-initialized
    let isInitialization: Bool

    @State private var inputError: String?
    @AppStorage("accessKey") private var savedKey: String = ""

    // Computed property to check if the input is exactly 4 digits
    private var isInputValid: Bool {
        codeInput.count == 4 && codeInput.allSatisfy { $0.isNumber }
    }

    var body: some View {
        VStack(spacing: 30) {

            // Title and Description
            Text(isInitialization ? "Set Up Remote Access Key" : "Change Access Key")
                .font(.largeTitle.bold())
                .foregroundColor(.primary)

            Text("This 4-digit numeric code will be broadcasted to connect your remote device. Please keep it secure.")
                .font(.headline)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 400)
                .padding(.bottom, 20)

            // Secure Input Field
            VStack(alignment: .leading, spacing: 8) {
                // Limit input to 4 characters and only numbers
                TextField("Enter 4-Digit Code", text: $codeInput)
                    .font(.system(size: 10, weight: .semibold, design: .monospaced))
                    .textFieldStyle(.roundedBorder)
                    .frame(maxWidth: 150)
                    .multilineTextAlignment(.center)
                    .onChange(of: codeInput) { newValue in
                        // Enforce 4-digit limit
                        if newValue.count > 4 {
                            codeInput = String(newValue.prefix(4))
                        }
                        // Clear error on change
                        inputError = nil
                    }

                if let error = inputError {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.callout)
                }
            }
            .padding()

            // Action Button
            Button {
                if isInputValid {
                    // 1. Save to UserDefaults
                    savedKey = codeInput

                    // 2. Update BLE Manager and start advertising
                    bleManager.setKeyAndStartAdvertising(key: codeInput)

                    // 3. Trigger navigation to StatusView
                    isKeySet = true

                } else {
                    inputError = "Code must be exactly 4 digits."
                }
            } label: {
                Text(isInitialization ? "Save Key & Start Service" : "Update Key")
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .frame(minWidth: 200)
            }
            // Apple-style primary button styling
            .buttonStyle(.borderedProminent)
            .tint(isInputValid ? .blue : .gray)
            .controlSize(.large)
            .disabled(!isInputValid)
        }
        .padding(50)
        .frame(minWidth: 600, minHeight: 400)
        // Reset code input when the view appears (e.g., when re-initializing)
        .onAppear {
            codeInput = ""
            inputError = nil
        }
    }
}
