package com.ai.remote.ai

data class ScriptGenerationResult(
    val script: String,
    val mode: String,   // "local" or "cloud"
    val error: String? = null
)