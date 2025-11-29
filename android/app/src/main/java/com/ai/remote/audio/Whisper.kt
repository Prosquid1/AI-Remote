package com.ai.remote.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cactus.CactusInitParams
import com.cactus.CactusSTT
import com.cactus.CactusTranscriptionParams
import com.cactus.CactusTranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Whisper {
    private val stt = CactusSTT()
    private val modelName = "whisper-small"
    private var isModelInitialized = false
    private var isTranscribing = false
    private var lastResponse: CactusTranscriptionResult? = null
    private var streamedText = ""
    private var outputText = ""

    /**
     * Initializes the CactusSTT model (downloads if necessary).
     * This should be called once, ideally early in the application lifecycle.
     */
    suspend fun initializeModel() = withContext(Dispatchers.IO) {
        if (isModelInitialized) return@withContext

        try {
            // 1. Download the model (whisper-small)
            println("CactusSTT: Downloading model...")
            stt.downloadModel(modelName)

            // 2. Initialize the model
            println("CactusSTT: Initializing model...")
            stt.initializeModel(CactusInitParams(model = modelName))
            isModelInitialized = true
            println("CactusSTT: Model initialized successfully.")
        } catch (e: Exception) {
            println("CactusSTT: Model initialization failed: ${e.message}")
        }
    }

    suspend fun transcribe(filePath: String, onResult: (Boolean, String, String?) -> Unit) = withContext(Dispatchers.IO) {
        isTranscribing = true
        val params = CactusTranscriptionParams(
            model = modelName,
            maxTokens = 512
        )

        val result = withContext(Dispatchers.Default) {
            stt.transcribe(
                filePath = filePath,
                params = params,
                onToken = { token, _ ->
                    streamedText += token
                }
            )
        }

        isTranscribing = false
        if (result != null && result.success) {
            lastResponse = result
            outputText = "File transcription completed successfully!"
            onResult(false, outputText, result.text.orEmpty())
        } else {
            outputText = result?.text ?: "Failed to transcribe audio file."
            streamedText = ""
            lastResponse = null
            onResult(false, outputText + result?.errorMessage.toString(), result?.text.orEmpty())
        }
    }
}
