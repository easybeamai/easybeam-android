package com.example.easybeam_core

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Easybeam(private val config: EasyBeamConfig) {
    private val baseUrl = "https://api.easybeam.ai/v1"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Public methods for streaming
    fun streamPortal(
        portalId: String,
        userId: String?,
        filledVariables: Map<String, String>,
        messages: List<ChatMessage>,
        onNewResponse: (PortalResponse) -> Unit,
        onClose: () -> Unit,
        onError: (Throwable) -> Unit
    ): () -> Unit {
        return streamEndpoint(
            endpoint = "portal",
            id = portalId,
            userId = userId,
            filledVariables = filledVariables,
            messages = messages,
            onNewResponse = onNewResponse,
            onClose = onClose,
            onError = onError
        )
    }

    fun streamWorkflow(
        workflowId: String,
        userId: String?,
        filledVariables: Map<String, String>,
        messages: List<ChatMessage>,
        onNewResponse: (PortalResponse) -> Unit,
        onClose: () -> Unit,
        onError: (Throwable) -> Unit
    ): () -> Unit {
        return streamEndpoint(
            endpoint = "workflow",
            id = workflowId,
            userId = userId,
            filledVariables = filledVariables,
            messages = messages,
            onNewResponse = onNewResponse,
            onClose = onClose,
            onError = onError
        )
    }

    // Private streaming implementation
    private fun streamEndpoint(
        endpoint: String,
        id: String,
        userId: String?,
        filledVariables: Map<String, String>,
        messages: List<ChatMessage>,
        onNewResponse: (PortalResponse) -> Unit,
        onClose: () -> Unit,
        onError: (Throwable) -> Unit
    ): () -> Unit {
        val url = "$baseUrl/$endpoint/$id"

        try {
            val bodyJson = JSONObject().apply {
                put("variables", JSONObject(filledVariables))
                put("messages", JSONArray(messages.map { it.toJson() }))
                put("stream", true) // Changed to boolean
                userId?.let { put("userId", it) }
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                ?: throw IllegalStateException("Invalid media type")

            val requestBody = RequestBody.create(mediaType, bodyJson.toString())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .addHeader("Authorization", "Bearer ${config.token}")
                .build()

            val eventSourceFactory = EventSources.createFactory(client)
            val eventSource = eventSourceFactory.newEventSource(
                request,
                object : EventSourceListener() {
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String
                    ) {
                        if (data.trim() == "[DONE]") {
                            onClose()
                            eventSource.cancel()
                        } else {
                            try {
                                val jsonResponse = JSONObject(data)
                                val portalResponse = PortalResponse.fromJson(jsonResponse)
                                onNewResponse(portalResponse)
                            } catch (e: Exception) {
                                onError(Exception("Failed to parse response: ${e.message}", e))
                            }
                        }
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?
                    ) {
                        val errorMessage = when {
                            t != null -> "Stream error: ${t.message}"
                            response != null -> "Server error: ${response.code} ${response.message}"
                            else -> "Unknown error encountered"
                        }
                        onError(Exception(errorMessage, t))
                        eventSource.cancel()
                    }

                    override fun onOpen(eventSource: EventSource, response: Response) {
                        if (!response.isSuccessful) {
                            onError(Exception("Failed to open stream: ${response.code} ${response.message}"))
                            eventSource.cancel()
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        onClose()
                    }
                }
            )

            return { eventSource.cancel() }
        } catch (e: Exception) {
            onError(Exception("Failed to initialize stream: ${e.message}", e))
            return {}
        }
    }

    suspend fun getPortal(
        portalId: String,
        userId: String?,
        filledVariables: Map<String, String>,
        messages: List<ChatMessage>
    ): PortalResponse = getEndpoint(
        endpoint = "portal",
        id = portalId,
        userId = userId,
        filledVariables = filledVariables,
        messages = messages
    )

    suspend fun getWorkflow(
        workflowId: String,
        userId: String?,
        filledVariables: Map<String, String>,
        messages: List<ChatMessage>
    ): PortalResponse = getEndpoint(
        endpoint = "workflow",
        id = workflowId,
        userId = userId,
        filledVariables = filledVariables,
        messages = messages
    )

    private suspend fun getEndpoint(
        endpoint: String,
        id: String,
        userId: String?,
        filledVariables: Map<String, String>,
        messages: List<ChatMessage>
    ): PortalResponse = suspendCancellableCoroutine { continuation ->
        val url = "$baseUrl/$endpoint/$id"

        val bodyJson = JSONObject().apply {
            put("variables", JSONObject(filledVariables))
            put("messages", JSONArray(messages.map { it.toJson() }))
            put("stream", false)
            userId?.let { put("userId", it) }
        }

        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            bodyJson.toString()
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${config.token}")
            .build()

        val call = client.newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isCancelled) return

                response.use {
                    if (!response.isSuccessful) {
                        continuation.resumeWithException(IOException("Unexpected code $response"))
                    } else {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                val portalResponse = PortalResponse.fromJson(jsonResponse)
                                continuation.resume(portalResponse)
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        } else {
                            continuation.resumeWithException(IOException("Empty response body"))
                        }
                    }
                }
            }
        })
    }

    fun review(
        chatId: String,
        userId: String?,
        reviewScore: Int?,
        reviewText: String?,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val url = "$baseUrl/review"

        val bodyJson = JSONObject().apply {
            put("chatId", chatId)
            userId?.let { put("userId", it) }
            reviewScore?.let { put("reviewScore", it) }
            reviewText?.let { put("reviewText", it) }
        }

        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            bodyJson.toString()
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${config.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onError(IOException("Failed to submit review: ${response.body?.string()}"))
                }
            }
        })
    }
}