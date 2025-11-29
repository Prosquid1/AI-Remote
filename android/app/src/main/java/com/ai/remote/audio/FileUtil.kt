package com.ai.remote.audio

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

internal const val RECORDING_PREFIX = "recording_"
internal const val RECORDING_EXTENSION = ".wav"

fun Context.generateWavFile(prefix: String = RECORDING_PREFIX): File {
    val fileName = "$prefix${System.currentTimeMillis()}$RECORDING_EXTENSION"
    val outputFile = File(this.getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
    return outputFile
}

fun Context.savePickedAudioToAppStorage(uri: Uri): File? {
    val file = generateWavFile(prefix = RECORDING_PREFIX)
    return try {
        this.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun deleteFile(filePath: String): Boolean {
    Log.d("DEL:", "Deleting file: $filePath")
    return try {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}