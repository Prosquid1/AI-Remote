package com.example.airemote.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class GeminiCloudClient(private val apiKey: String) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    prettyPrint = false
                }
            )
        }
    }

    suspend fun generate(command: String): String {
        val prompt = """
                Given a request, convert it into an executable AppleScript.
                You must output ONLY valid AppleScript for macOS, no comments.

                Request: "$command"
            """.trimIndent()
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"


        val payload = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        putJsonArray("parts") {
                            add(
                                buildJsonObject {
                                    put("text", prompt)
                                }
                            )
                        }
                    }
                )
            }
        }

        // ❗ MUST request JsonObject
        val response: JsonObject = client.post(url) {
            header("X-Goog-Api-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

        return extractText(response)
    }

    private fun extractText(json: JsonObject): String {
        return try {
            val candidates = json["candidates"]!!.jsonArray
            val content = candidates[0].jsonObject["content"]!!.jsonObject
            val parts = content["parts"]!!.jsonArray
            parts[0].jsonObject["text"]!!.jsonPrimitive.content
        } catch (e: Exception) {
            "Cloud parsing error"
        }
    }
}