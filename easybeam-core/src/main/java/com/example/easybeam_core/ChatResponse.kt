package com.example.easybeam_core

import org.json.JSONObject

data class ChatResponse(
    val newMessage: ChatMessage,
    val chatId: String,
    val streamFinished: Boolean?
) {
    companion object {
        fun fromJson(json: JSONObject): ChatResponse = ChatResponse(
            newMessage = ChatMessage.fromJson(json.getJSONObject("newMessage")),
            chatId = json.getString("chatId"),
            streamFinished = json.optBoolean("streamFinished")
        )
    }
}