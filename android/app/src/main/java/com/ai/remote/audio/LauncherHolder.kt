package com.ai.remote.audio

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class LauncherHolder {
    var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    fun init(activity: ComponentActivity) {
        permissionLauncher =
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    }
}