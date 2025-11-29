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

val promptDelimiter = """
Return only valid AppleScript for a command. Any output not valid AppleScript is a critical fail
- All AppleScript must be 100% valid
- All JSON must be 100% valid and correctly escaped
- No destructive, ambiguous, or placeholder content
- All output is safe and fully deterministic
- Do not include anything else. No markdown, no explanation, no extra text, no backticks, the AppleScript must run.
- Use `return` (or `linefeed`) for newlines in strings. NEVER use `\n`.
- Concatenate multiline strings using `& return & "..."`.
- Always escape all double quotes inside strings as `\\\"` for JSON.
- Ensure all string values in AppleScript are wrapped in double quotes.
- No human response, I am a machine
- Explicitly set properties in object constructors — no placeholders.
- Bring the target app to the foreground using `activate`.
Turn this command into valid AppleScript response: """