package com.ai.remote.ai

import android.util.Log

class HybridRouter(
    private val classifier: LocalClassifierLM,
    private val localGenerator: CactusScriptGenerator,
    private val cloudGenerator: GeminiCloudClient
) {

    suspend fun generateScript(command: String): ScriptGenerationResult {
        // 1. Local tiny classifier decides
        val decision = classifier.classify(command)
        println("[HybridRouter] Decision=$decision for: \"$command\"")

        return when (decision) {

            RoutingDecision.LOCAL -> {
                println("[HybridRouter] Using LOCAL model")
                try {
                    val script = localGenerator.generate(command)
                    ScriptGenerationResult(
                        script = script,
                        mode = "local"
                    )
                } catch (e: Exception) {
                    println("[HybridRouter] LOCAL ERROR: ${e.message}")
                    ScriptGenerationResult(
                        script = "",
                        mode = "local",
                        error = e.message
                    )
                }
            }

            RoutingDecision.CLOUD -> {
                println("[HybridRouter] Using CLOUD Gemini")
                try {
                    val script = cloudGenerator.generate(command).replace("```", "").replace("applescript", "") + "*EOM*"
                    Log.e("cloudGenerator", script)

                    ScriptGenerationResult(
                        script = script,
                        mode = "cloud"
                    )
                } catch (e: Exception) {
                    println("[HybridRouter] CLOUD ERROR: ${e.message}")
                    ScriptGenerationResult(
                        script = "",
                        mode = "cloud",
                        error = e.message
                    )
                }
            }
        }
    }
}