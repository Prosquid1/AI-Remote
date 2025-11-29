package com.ai.remote.audio

import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ai.remote.R
import kotlinx.coroutines.launch

/**
 * An enum to represent the two states of the recorder UI.
 */
enum class RecordingState {
    IDLE,      // Ready to start recording
    RECORDING  // Recording in progress
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderUiScreen(
    audioRecorder: AudioRecorder,
    whisper: Whisper,
    isEnabled: Boolean,
    voiceMessage: (String) -> Unit,
    isTranscribing: (Boolean) -> Unit
) {
    var recordingState by remember { mutableStateOf(RecordingState.IDLE) }
    var transcriptionStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val buttonColor by animateColorAsState(
        targetValue = if (!isEnabled) {
            Color.Gray // Gray color when disabled
        } else if (recordingState == RecordingState.RECORDING) {
            Color(0xFFE53935) // Red color for recording state (Stop action)
        } else {
            Color(0xFF4CAF50) // Green color for idle state (Start action)
        },
        animationSpec = tween(durationMillis = 300), label = "Button Color Animation"
    )

    val icon: Painter = if (recordingState == RecordingState.RECORDING) {
        painterResource(R.drawable.ic_stop)
    } else {
        painterResource(R.drawable.ic_microphone)
    }

    val contentDescription: String = if (recordingState == RecordingState.RECORDING) {
        "Stop Recording Button"
    } else {
        "Start Recording Button"
    }

    val onButtonClick: () -> Unit = {
        if(!isEnabled) {
            // Do Nothing
        }
        else if (recordingState == RecordingState.IDLE) {
            scope.launch {
                // Check if we already have permission
                if (audioRecorder.hasRecordingPermission()) {
                    // Permission already granted - start immediately
                    println("--- START RECORDING (permission already granted) ---")
                    audioRecorder.startRecording()
                    recordingState = RecordingState.RECORDING
                    transcriptionStatus = ""
                } else {
                    // Request permission
                    val isGranted = audioRecorder.requestRecordingPermission()
                    if (isGranted) {
                        // Permission just granted - start immediately
                        println("--- START RECORDING (permission just granted) ---")
                        audioRecorder.startRecording()
                        recordingState = RecordingState.RECORDING
                        transcriptionStatus = ""
                    } else {
                        println("--- PERMISSION DENIED ---")
                        transcriptionStatus = "Permission denied"
                    }
                }
            }
        } else {
            println("--- STOP RECORDING ---")
            audioRecorder.stopRecording()
            val filePath = audioRecorder.getRecordingFilePath()
            println("--- RECORDING SAVED TO: $filePath ---")

            recordingState = RecordingState.IDLE

            // Start transcription in the background
            transcriptionStatus = "Transcribing..."
            isTranscribing(true)
            scope.launch {
                whisper.transcribe(filePath) { isError, message, transcribedText ->
                    transcriptionStatus = message ?: "Transcription completed"
                    println("--- TRANSCRIPTION RESULT: $message ---")
                    voiceMessage(transcribedText.orEmpty())
                    isTranscribing(false)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp) // Large size for the main button
                .clickable(onClick = onButtonClick)
                .background(color = buttonColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(60.dp) // Large icon size
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}