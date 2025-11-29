package com.ai.remote.ai

import com.cactus.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class CactusScriptGenerator(
    private val lm: CactusLM,
    private val modelName: String,
) {

    private var initialized = false
    suspend fun generate(command: String): String =
        withContext(Dispatchers.Default) {

            if (!initialized) {
                try {
                    lm.initializeModel(
                        CactusInitParams(
                            model = modelName,
                            contextSize = 2048
                        )
                    )
                } catch (_: Exception) {
                    lm.downloadModel(modelName)
                    lm.initializeModel(
                        CactusInitParams(
                            model = modelName,
                            contextSize = 2048
                        )
                    )
                }

                initialized = true
            }

            val prompt =  """"$promptDelimiter $command"    
            """.trimIndent()

            val result = lm.generateCompletion(
                messages = listOf(ChatMessage(prompt, "user")),
                params = CactusCompletionParams(
                    mode = InferenceMode.LOCAL, //local
                )
            )

            result?.response?.trim() ?: ""
        }
}