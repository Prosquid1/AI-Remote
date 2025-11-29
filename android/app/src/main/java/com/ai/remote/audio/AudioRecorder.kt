package com.ai.remote.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import androidx.core.content.ContextCompat
import com.github.squti.androidwaverecorder.WaveRecorder
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private const val DEFAULT = "recording.wav"
private var selectedEncoding = AudioFormat.ENCODING_PCM_16BIT

class AudioRecorder(
    private val context: Context,
    private val launcherHolder: LauncherHolder
) {
    private var recorder: WaveRecorder? = null
    private var isCurrentlyRecording = false
    private var isCurrentlyPaused = false
    private var permissionContinuation: ((Boolean) -> Unit)? = null
    private var currentRecordingPath: String? = null

    fun startRecording() {
        val file = context.generateWavFile()
        currentRecordingPath = file.absolutePath

        recorder = WaveRecorder(file.absolutePath)
            .configureWaveSettings {
                sampleRate = 16000
                channels = AudioFormat.CHANNEL_IN_MONO
                audioEncoding = selectedEncoding
            }.configureSilenceDetection {
                minAmplitudeThreshold = 2000
                bufferDurationInMillis = 1500
                preSilenceDurationInMillis = 1500
            }

        try {
            recorder?.startRecording()
            isCurrentlyRecording = true
            isCurrentlyPaused = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        try {
            recorder?.stopRecording()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder = null
            isCurrentlyRecording = false
            isCurrentlyPaused = false
        }
    }

    fun isRecording(): Boolean {
        return isCurrentlyRecording
    }

    fun hasRecordingPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun requestRecordingPermission():Boolean {
        if (hasRecordingPermission()) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            permissionContinuation = { isGranted ->
                continuation.resume(isGranted)
            }

            if (launcherHolder.permissionLauncher != null) {
                launcherHolder.permissionLauncher?.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            } else {
                continuation.resume(false)
            }

            continuation.invokeOnCancellation {
                permissionContinuation = null
            }
        }
    }

    fun getRecordingFilePath(): String {
        return currentRecordingPath ?: File(context.cacheDir, DEFAULT).absolutePath
    }

    fun handlePermissionResult(granted: Boolean) {
        permissionContinuation?.invoke(granted)
        permissionContinuation = null
    }
}