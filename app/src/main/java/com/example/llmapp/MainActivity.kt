package com.example.llmapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.llmapp.R
import com.example.llmapp.data.ChatMessage
import com.example.llmapp.data.ChatSession
import com.example.llmapp.databinding.ActivityMainBinding
import com.example.llmapp.databinding.DrawerLayoutSidebarBinding
import com.example.llmapp.ui.ChatHistoryManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val TAG = "LLMAPP"
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY
        OkHttpClient.Builder().addInterceptor(logging).build()
    }
    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessagesAdapter
    // Queue and processor for animating incoming tokens one-by-one
    private val tokenQueue = ArrayDeque<Pair<String, Int>>()
    private var processingQueue = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private var isStreaming = false
    private var isPaused = false
    private var currentCall: Call? = null
    private lateinit var chatHistoryManager: ChatHistoryManager
    private var currentAssistantIndex: Int = -1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply saved theme preference (simple on/off dark mode)
        val prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val darkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Ensure custom send icon fills the AppCompatImageButton
        binding.buttonSend.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        // Generate, store, and log device ID
        com.example.llmapp.DeviceIdManager.getOrCreateDeviceId(this)

        adapter = MessagesAdapter(messages)
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapter

        // Create drawer binding from the included view - navigationDrawer is the root of the included layout
        val navigationDrawerView = findViewById<View>(R.id.navigationDrawer)
        val drawerBinding = DrawerLayoutSidebarBinding.bind(navigationDrawerView)

        // Initialize chat history manager
        chatHistoryManager = ChatHistoryManager(
            context = this,
            drawerLayout = binding.drawerLayout,
            binding = drawerBinding,
            onSessionSelected = { session -> loadChatSession(session) },
            onNewChatRequested = { startNewChat() }
        )

        // Setup menu button to open drawer
        binding.buttonMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(navigationDrawerView)
        }

        // Load or create initial session
        chatHistoryManager.initializeOrLoadSession()

        // Reflect empty state when the app starts
        updateEmptyState()

        binding.buttonSend.setOnClickListener {
            if (isStreaming || processingQueue) {
                // Stop streaming and typing animation
                isStreaming = false
                isPaused = false
                processingQueue = false
                synchronized(tokenQueue) { tokenQueue.clear() }
                currentCall?.cancel() // Cancel HTTP request
                currentCall = null
                // Reset icon back to send
                binding.buttonSend.setImageResource(android.R.drawable.ic_menu_send)
                return@setOnClickListener
            }

            val prompt = binding.editTextPrompt.text.toString().trim()
            if (prompt.isEmpty()) return@setOnClickListener

            // Add user message
            val userMessage = Message(prompt, isUser = true)
            messages.add(userMessage)
            adapter.notifyItemInserted(messages.size - 1)
            updateEmptyState()
            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
            
            // Save user message to history
            chatHistoryManager.addMessage(ChatMessage(prompt, true))
            
            binding.editTextPrompt.setText("")

            // Add assistant placeholder
            messages.add(Message("", isUser = false))
            currentAssistantIndex = messages.size - 1
            adapter.notifyItemInserted(currentAssistantIndex)
            updateEmptyState()
            binding.recyclerViewMessages.scrollToPosition(currentAssistantIndex)

            // Start streaming
            isStreaming = true
            isPaused = false
            // Change icon to stop while streaming
            binding.buttonSend.setImageResource(R.drawable.ic_stop)

            callApiStreaming(prompt, currentAssistantIndex) { finalText ->
                runOnUiThread {
                    // Only mark streaming as false, keep stop icon until animation finishes
                    isStreaming = false
                    isPaused = false
                    // Save assistant message to history
                    if (finalText != null && finalText.isNotEmpty()) {
                        chatHistoryManager.updateLastMessage(ChatMessage(finalText, false))
                    }
                    // Icon will be reset when processNextToken finishes all animations
                }
            }
        }

        // Swapped behavior: top-right button now starts a New Chat (icon changed to plus)
        binding.buttonSettings.setOnClickListener {
            startNewChat()
        }
    }

    private fun loadChatSession(session: ChatSession) {
        // Clear current messages
        messages.clear()
        
        // Load messages from session
        for (chatMessage in session.messages) {
            messages.add(Message(chatMessage.text, chatMessage.isUser))
        }
        
        adapter.notifyDataSetChanged()
        updateEmptyState()
        
        if (messages.isNotEmpty()) {
            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
        }
    }

    private fun startNewChat() {
        messages.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()
        binding.editTextPrompt.setText("")
    }

    private fun updateEmptyState() {
        if (messages.isEmpty()) {
            binding.textViewEmptyState.visibility = View.VISIBLE
            binding.recyclerViewMessages.visibility = View.GONE
        } else {
            binding.textViewEmptyState.visibility = View.GONE
            binding.recyclerViewMessages.visibility = View.VISIBLE
        }
    }

    private fun showSettings() {
        val items = arrayOf("Light mode", "Dark mode", "Follow system")
        val current = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            else -> 2
        }

        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(items, current) { dialog, which ->
                when (which) {
                    0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Streaming call: read the HTTP response as bytes, split into whitespace-separated tokens,
     * and enqueue tokens to be animated into the assistant placeholder at assistantIndex.
     */


    private fun callApiStreaming(prompt: String, assistantIndex: Int, callback: (String?) -> Unit) {
    val accessToken = DeviceIdManager.getAccessToken(this)
    val refreshToken = DeviceIdManager.getRefreshToken(this)

    Log.d(TAG, "Access Token From MainActivity: $accessToken")
    Log.d(TAG, "Refresh Token From MainActivity: $refreshToken")

    val url = "http://192.168.29.230:8000/api/text/segment"

    val json = """
        {
          "prompt": "${prompt.replace("\"", "\\\"")}"
        }
    """.trimIndent()
    val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

    val req = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $accessToken")
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build()

    currentCall = client.newCall(req)
    currentCall?.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e(TAG, "Request failed: ${e.message}")
            callback("Network error: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { resp ->
                if (!resp.isSuccessful) {
                    callback("Server error: ${resp.code}")
                    return
                }

                val respBody = resp.body?.string()
                if (respBody.isNullOrEmpty()) {
                    callback(null)
                    return
                }

                try {
                    // Parse JSON and extract the "response" text
                    val jsonObj = org.json.JSONObject(respBody)
                    val text = jsonObj.optString("response", respBody)

                    // Stream or display token by token if needed
                    val words = text.split(Regex("\\s+"))
                    val assembled = StringBuilder()

                    for (word in words) {
                        if (!isStreaming) break
                        val token = "$word "
                        assembled.append(token)
                        enqueueTokenForAnimation(token, assistantIndex)
                    }

                    callback(assembled.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Response parse error: ${e.message}")
                    callback(respBody)
                }
            }
        }
    })
}




    data class Message(val text: String, val isUser: Boolean)

    // Enqueue a token (string) to be animated into messages[assistantIndex] char-by-char.
    private fun enqueueTokenForAnimation(token: String, assistantIndex: Int, charDelayMillis: Long = 20L) {
        synchronized(tokenQueue) { tokenQueue.add(token to assistantIndex) }
        if (!processingQueue) processNextToken(charDelayMillis)
    }

    private fun processNextToken(charDelayMillis: Long = 20L) {
        val item = synchronized(tokenQueue) { if (tokenQueue.isEmpty()) null else tokenQueue.removeFirst() }
        if (item == null) {
            processingQueue = false
            // Animation finished, reset button icon to send only if not streaming and queue is empty
            runOnUiThread {
                if (!isStreaming && synchronized(tokenQueue) { tokenQueue.isEmpty() }) {
                    binding.buttonSend.setImageResource(android.R.drawable.ic_menu_send)
                }
            }
            return
        }
        processingQueue = true
        val (token, assistantIndex) = item

        uiHandler.post(object : Runnable {
            var pos = 0
            override fun run() {
                // Stop if processing was cancelled (processingQueue set to false)
                if (!processingQueue) {
                    return
                }
                if (pos < token.length) {
                    val ch = token[pos++]
                    if (assistantIndex in messages.indices) {
                        messages[assistantIndex] = Message(messages[assistantIndex].text + ch, isUser = false)
                        adapter.notifyItemChanged(assistantIndex)

                        // ✅ Auto-scroll only if user is near bottom
                        val layoutManager = binding.recyclerViewMessages.layoutManager as LinearLayoutManager
                        val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()

                        val shouldScroll = lastVisible >= messages.size - 2
                        if (shouldScroll) {
                            binding.recyclerViewMessages.smoothScrollToPosition(assistantIndex)
                        }
                    }
                    // Continue only if still processing
                    if (processingQueue) {
                        uiHandler.postDelayed(this, charDelayMillis)
                    }
                } else processNextToken(charDelayMillis)
            }
        })
    }
    class MessagesAdapter(private val items: List<Message>) : RecyclerView.Adapter<MessagesAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = items[position]
            holder.textView.text = if (msg.isUser) "You: ${msg.text}" else msg.text
        }

        override fun getItemCount(): Int = items.size

        class VH(val textView: TextView) : RecyclerView.ViewHolder(textView)} }


