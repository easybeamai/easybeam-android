package com.example.easybeamsdk

import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.easybeam_core.ChatMessage
import com.example.easybeam_core.ChatRole
import com.example.easybeam_core.EasyBeamConfig
import com.example.easybeam_core.Easybeam
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private var cancelStream: (() -> Unit)? = null
    private lateinit var messagesAdapter: ChatAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: FloatingActionButton
    private lateinit var recyclerView: RecyclerView
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var easybeam: Easybeam

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        recyclerView = findViewById(R.id.messagesRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        // Setup RecyclerView
        messagesAdapter = ChatAdapter(messages)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = messagesAdapter
        }

        // Initialize Easybeam
        val config = EasyBeamConfig("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0ZWFtSWQiOiJ5anNHUjg4NWlXNDlDMk5SY2dtYiIsInRva2VuSWQiOiJlNjRlN2E0Yy0wZjNiLTQ4ODctYTRiNS0yYWY4MjU1YjI4NzMiLCJ0ZWFtSnd0VG9rZW4iOiJleUpoYkdjaU9pSklVekkxTmlJc0luUjVjQ0k2SWtwWFZDSjkuZXlKMWMyVnlTV1FpT2lKeVNURjZSbGRMV25GVVpYVmtiR0pHUlROa01VcFFlRUp0V0hFeElpd2lhV0YwSWpveE56TXpNak01TVRBemZRLlh4eUNISnBFOHRmY2NOdHlhbUQ5ZkJVWnNqeWFEd0tKZjNtUkl3OVVDdUkiLCJpYXQiOjE3MzMyMzkxMDN9.-B15npsUbwGjLiwvj1Fla28o_VWcL7-dz1jib4zzzVY")
        easybeam = Easybeam(config)

        // Setup send button
        sendButton.setOnClickListener {
            sendMessage()
        }

        // Setup keyboard send action
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isNotEmpty()) {
            // Add user message to the chat
            val userMessage = ChatMessage(role = ChatRole.USER, content = messageText, createdAt = ChatMessage.getCurrentTimestamp(), id = UUID.randomUUID().toString())
            messages.add(userMessage)
            messagesAdapter.notifyItemInserted(messages.size - 1)
            recyclerView.scrollToPosition(messages.size - 1)

            // Clear input
            messageInput.text.clear()

            // Cancel any existing stream
            cancelStream?.invoke()

            // Start new stream
            cancelStream = easybeam.streamPrompt(
                promptId = "lMID3",
                userId = "test-user",
                filledVariables = mapOf("test" to "value"),
                messages = messages.toList(),
                onNewResponse = { response ->
                    Log.d("Stream", "New response: $response")
                    runOnUiThread {
                        println("Added message ${messages.size}")
                        val existingMessageIndex = messages.indexOfFirst {
                            it.id == response.newMessage.id
                        }

                        if (existingMessageIndex != -1) {
                            // Update existing message
                            messages[existingMessageIndex] = response.newMessage
                            messagesAdapter.notifyItemChanged(existingMessageIndex)
                        } else {
                            // Add new message
                            messages.add(response.newMessage)
                            messagesAdapter.notifyItemInserted(messages.size - 1)
                        }
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                },
                onClose = {
                    Log.d("Stream", "Stream closed")
                },
                onError = { error ->
                    Log.e("Stream", "Error: ", error)
                    runOnUiThread {
                        messagesAdapter.notifyItemInserted(messages.size - 1)
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelStream?.invoke()
    }
}

class ChatAdapter(private val messages: MutableList<ChatMessage>) : RecyclerView.Adapter<ChatViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size
}

class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val messageText: TextView = view.findViewById(R.id.messageText)
    private val userBubble: View = view.findViewById(R.id.userBubble)
    private val assistantBubble: View = view.findViewById(R.id.assistantBubble)

    fun bind(message: ChatMessage) {
        messageText.text = message.content
        when (message.role) {
            ChatRole.USER -> {
                userBubble.isVisible = true
                assistantBubble.isVisible = false
                messageText.setTextColor(itemView.context.getColor(android.R.color.white))
            }

            ChatRole.AI -> {
                userBubble.isVisible = false
                assistantBubble.isVisible = true
                messageText.setTextColor(itemView.context.getColor(android.R.color.black))
            }

            else -> {
                userBubble.isVisible = false
                assistantBubble.isVisible = true
                messageText.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
            }
        }
    }
}