//
//  ScriptRunner.swift
//  AI Remote App
//
//  Created by Oyeleke Okiki on 11/28/25.
//

import Foundation

public func runAppleScript(_ source: String, completion: @escaping (String, Bool) -> Void) {
    DispatchQueue.global(qos: .userInitiated).async {
        let appleScript = NSAppleScript(source: source)
        var errorDict: NSDictionary? = nil
        let failureError = "Failed to complete task"

        if let response = appleScript?.executeAndReturnError(&errorDict).stringValue {
            completion(response, false)
        } else if let dict = errorDict {
            if let terminalError = dict.value(forKey: "NSAppleScriptErrorMessage") as? String {
                completion(terminalError, true)
                return
            }
            completion(failureError, true)
        } else {
            completion("Completed successfully", false)
        }
    }
}
