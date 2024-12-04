package com.example.easybeam_core

import android.annotation.SuppressLint
import java.time.Instant
import java.time.format.DateTimeFormatter
import org.json.JSONObject

data class ChatMessage(
    val content: String,
    val role: ChatRole,
    val createdAt: Instant,
    val providerId: String? = null,
    val id: String,
    val inputTokens: Double? = null,
    val outputTokens: Double?= null,
    val cost: Double? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("content", content)
        put("role", role.name)
        put("createdAt", DateTimeFormatter.ISO_INSTANT.format(createdAt))
        put("providerId", providerId)
        put("id", id)
        put("inputTokens", inputTokens)
        put("outputTokens", outputTokens)
        put("cost", cost)
    }

    companion object {
        fun fromJson(json: JSONObject): ChatMessage = ChatMessage(
            content = json.getString("content"),
            role = ChatRole.valueOf(json.getString("role")),
            createdAt = Instant.parse(json.getString("createdAt")),
            providerId = json.optString("providerId").takeIf { it.isNotEmpty() },
            id = json.getString("id"),
            inputTokens = json.optDouble("inputTokens").takeIf { !it.isNaN() },
            outputTokens = json.optDouble("outputTokens").takeIf { !it.isNaN() },
            cost = json.optDouble("cost").takeIf { !it.isNaN() }
        )

        @SuppressLint("NewApi")
        fun getCurrentTimestamp(): Instant = Instant.now()
    }
}