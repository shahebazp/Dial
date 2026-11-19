package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiIntelligenceService {

    companion object {
        private const val TAG = "GeminiIntelligence"
    }

    private val fastReplyModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-2.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.5f
                maxOutputTokens = 160
            },
            systemInstruction = content {
                text(
                    "You are DIAL, a concise phone call assistant on a Nothing OS phone. " +
                        "Write natural, polite, ready-to-send SMS quick-reply messages under 160 characters. " +
                        "Output ONLY the message text without quotes or commentary."
                )
            }
        )
    }

    suspend fun generateSmartQuickReply(
        callerNameOrNumber: String,
        userContextPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildString {
                append("Write a single concise SMS text message to send to caller ")
                append(callerNameOrNumber.ifBlank { "the caller" })
                append(" explaining why I cannot answer their call right now.\n")
                if (userContextPrompt.isNotBlank()) {
                    append("My situation / instructions: ").append(userContextPrompt.trim())
                } else {
                    append("My situation: In a meeting or busy right now, will call back shortly.")
                }
            }
            val response = fastReplyModel.generateContent(prompt)
            val reply = response.text?.trim()?.removeSurrounding("\"")
                ?: throw IllegalStateException("Gemini returned an empty quick reply.")
            reply
        }.onFailure { error ->
            Log.e(TAG, "Failed to generate smart quick reply", error)
        }
    }
}
