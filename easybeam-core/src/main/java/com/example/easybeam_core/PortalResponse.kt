package com.example.easybeam_core

import org.json.JSONObject

data class PortalResponse(
    val newMessage: ChatMessage,
    val chatId: String,
    val streamFinished: Boolean?
) {
    companion object {
        fun fromJson(json: JSONObject): PortalResponse = PortalResponse(
            newMessage = ChatMessage.fromJson(json.getJSONObject("newMessage")),
            chatId = json.getString("chatId"),
            streamFinished = json.optBoolean("streamFinished")
        )
    }
}