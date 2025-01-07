package com.example.easybeam_core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID


class EasybeamTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var easybeam: Easybeam
    private val testToken = "test-token"

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val config = EasyBeamConfig(token = testToken)
        easybeam = Easybeam(config)
        // Override base URL for testing
        easybeam.javaClass.getDeclaredField("baseUrl").apply {
            isAccessible = true
            set(easybeam, mockWebServer.url("/v1").toString())
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun createMockChatMessage() = ChatMessage(
        content = "Test content",
        role = ChatRole.USER,
        createdAt = Instant.now(),
        id = UUID.randomUUID().toString(),
        providerId = "test-provider",
        inputTokens = 10.0,
        outputTokens = 20.0,
        cost = 0.001
    )

    private fun createMockResponseJson(streamFinished: Boolean = false): String {
        val messageJson = JSONObject().apply {
            put("content", "Test response")
            put("role", "ASSISTANT")
            put("createdAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
            put("id", UUID.randomUUID().toString())
            put("providerId", "test-provider")
            put("inputTokens", 15.0)
            put("outputTokens", 25.0)
            put("cost", 0.002)
        }

        return JSONObject().apply {
            put("newMessage", messageJson)
            put("chatId", "test-chat-id")
            put("streamFinished", streamFinished)
        }.toString()
    }

    @Test
    fun `test getPrompt success`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(createMockResponseJson(true))
                .addHeader("Content-Type", "application/json")
        )

        val messages = listOf(createMockChatMessage())
        val variables = mapOf("var1" to "value1")

        val response = easybeam.getPrompt(
            promptId = "test-prompt",
            userId = "test-user",
            filledVariables = variables,
            messages = messages
        )

        assertEquals("test-chat-id", response.chatId)
        assertEquals(ChatRole.AI, response.newMessage.role)
        assertTrue(response.streamFinished == true)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/prompt/test-prompt", request.path)
        assertEquals("Bearer $testToken", request.getHeader("Authorization"))
    }

    @Test
    fun `test streamPrompt success`() {
        val latch = CountDownLatch(2) // Expect 2 events: response and close
        var receivedResponse: ChatResponse? = null
        var streamClosed = false

        val cancelStream = easybeam.streamPrompt(
            promptId = "test-prompt",
            userId = "test-user",
            filledVariables = mapOf("var1" to "value1"),
            messages = listOf(createMockChatMessage()),
            onNewResponse = { response ->
                receivedResponse = response
                latch.countDown()
            },
            onClose = {
                streamClosed = true
                latch.countDown()
            },
            onError = { error ->
                fail("Should not receive error: ${error.message}")
            }
        )

        // Simulate SSE response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                    data: ${createMockResponseJson(false)}
                    
                    data: [DONE]
                    
                """.trimIndent())
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(receivedResponse)
        assertEquals("test-chat-id", receivedResponse?.chatId)
        assertTrue(streamClosed)

        cancelStream()
    }

    @Test
    fun `test review success`() {
        val latch = CountDownLatch(1)
        var success = false

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
        )

        easybeam.review(
            chatId = "test-chat-id",
            userId = "test-user",
            reviewScore = 5,
            reviewText = "Great response!",
            onSuccess = {
                success = true
                latch.countDown()
            },
            onError = { error ->
                fail("Should not receive error: ${error.message}")
            }
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/review", request.path)

        val requestBody = JSONObject(request.body.readUtf8())
        assertEquals("test-chat-id", requestBody.getString("chatId"))
        assertEquals("test-user", requestBody.getString("userId"))
        assertEquals(5, requestBody.getInt("reviewScore"))
        assertEquals("Great response!", requestBody.getString("reviewText"))
    }

    @Test
    fun `test error handling`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        try {
            easybeam.getPrompt(
                promptId = "test-prompt",
                userId = "test-user",
                filledVariables = emptyMap(),
                messages = emptyList()
            )
            fail("Should throw exception")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("401") == true)
        }
    }

    @Test
    fun `test getAgent success`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(createMockResponseJson(true))
                .addHeader("Content-Type", "application/json")
        )

        val response = easybeam.getAgent(
            agentId = "test-agent",
            userId = "test-user",
            filledVariables = emptyMap(),
            messages = listOf(createMockChatMessage())
        )

        assertEquals("test-chat-id", response.chatId)
        assertEquals(ChatRole.AI, response.newMessage.role)
        assertTrue(response.streamFinished == true)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/agent/test-agent", request.path)
    }

    @Test
    fun `test stream cancellation`() {
        val latch = CountDownLatch(1)
        var streamClosed = false

        val cancelStream = easybeam.streamAgent(
            agentId = "test-agent",
            userId = "test-user",
            filledVariables = emptyMap(),
            messages = emptyList(),
            onNewResponse = { },
            onClose = {
                streamClosed = true
                latch.countDown()
            },
            onError = { error ->
                fail("Should not receive error: ${error.message}")
            }
        )

        // Cancel the stream immediately
        cancelStream()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(streamClosed)
    }

    @Test
    fun `test chat message serialization`() {
        val originalMessage = createMockChatMessage()
        val json = originalMessage.toJson()
        val deserializedMessage = ChatMessage.fromJson(json)

        assertEquals(originalMessage.content, deserializedMessage.content)
        assertEquals(originalMessage.role, deserializedMessage.role)
        assertEquals(originalMessage.id, deserializedMessage.id)
        assertEquals(originalMessage.providerId, deserializedMessage.providerId)
        assertEquals(originalMessage.inputTokens, deserializedMessage.inputTokens)
        assertEquals(originalMessage.outputTokens, deserializedMessage.outputTokens)
        assertEquals(originalMessage.cost, deserializedMessage.cost)
    }
}