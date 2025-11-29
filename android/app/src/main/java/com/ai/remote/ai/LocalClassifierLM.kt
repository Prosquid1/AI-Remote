package com.ai.remote.ai

import com.cactus.CactusLM
import com.cactus.ChatMessage
import com.cactus.CactusCompletionParams
import com.cactus.CactusInitParams
import com.cactus.InferenceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalClassifierLM(
    private val lm: CactusLM,
    private val modelName: String,
) {

    private var initialized = false

    suspend fun classify(input: String): RoutingDecision =
        withContext(Dispatchers.Default) {

            // Initialize ONCE only
            if (!initialized) {
                try {
                    lm.initializeModel(
                        CactusInitParams(
                            model = modelName,
                            contextSize = 256
                        )
                    )
                } catch (_: Exception) {
                    lm.downloadModel(modelName)
                    lm.initializeModel(
                        CactusInitParams(
                            model = modelName,
                            contextSize = 256
                        )
                    )
                }
                initialized = true
            }

            val prompt = """
                Decide if this request needs LOCAL or CLOUD reasoning.

                LOCAL = simple single-step macOS action.
                CLOUD = multi-step workflow, searching, looping, or complex logic.

                Request: "$input"

                Answer with only: LOCAL or CLOUD.
            """.trimIndent()

            val result = lm.generateCompletion(
                messages = listOf(ChatMessage(prompt, "user")),
                params = CactusCompletionParams(
                    mode = InferenceMode.LOCAL, // always local
                    cactusToken = null,
                    maxTokens = 4,
                    temperature = 0.0
                )
            )

            val reply = result?.response?.trim()?.uppercase() ?: ""

            if (reply.contains("LOCAL")) RoutingDecision.LOCAL else RoutingDecision.CLOUD
        }
}