package com.example.airemote.ai

data class ScriptGenerationResult(
    val script: String,
    val mode: String,   // "local" or "cloud"
    val error: String? = null
)