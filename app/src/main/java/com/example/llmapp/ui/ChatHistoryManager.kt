package com.example.llmapp.ui

import android.app.AlertDialog
import android.content.Context
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.llmapp.R
import com.example.llmapp.adapters.ChatHistoryListAdapter
import com.example.llmapp.data.ChatHistoryRepository
import com.example.llmapp.data.ChatMessage
import com.example.llmapp.data.ChatSession
import com.example.llmapp.databinding.DrawerChatHistoryHeaderBinding
import com.example.llmapp.databinding.DrawerLayoutSidebarBinding

/**
 * Manages the chat history drawer and its interactions
 */
class ChatHistoryManager(
    private val context: Context,
    private val drawerLayout: DrawerLayout,
    private val binding: DrawerLayoutSidebarBinding,
    private val onSessionSelected: (ChatSession) -> Unit,
    private val onNewChatRequested: () -> Unit
) {
    private val repository = ChatHistoryRepository(context)
    private lateinit var historyAdapter: ChatHistoryListAdapter
    private var currentSession: ChatSession? = null

    init {
        setupHistoryList()
        setupHeader()
        loadHistory()
    }

    private fun setupHistoryList() {
        historyAdapter = ChatHistoryListAdapter(
            sessions = emptyList(),
            onSessionClick = { session ->
                loadSession(session)
                drawerLayout.closeDrawer(binding.root)
            },
            onSessionDelete = { session ->
                showDeleteConfirmation(session)
            }
        )
        binding.recyclerViewChatHistory.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewChatHistory.adapter = historyAdapter
    }

    private fun setupHeader() {
        val headerBinding = DrawerChatHistoryHeaderBinding.bind(binding.root.findViewById(R.id.headerLayout))
        headerBinding.buttonNewChat.setOnClickListener {
            // Swapped behavior: drawer header icon now opens SettingsActivity
            try {
                val intent = android.content.Intent(context, com.example.llmapp.SettingsActivity::class.java)
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback: if starting activity fails, fall back to creating a new chat
                createNewChat()
            }
            drawerLayout.closeDrawer(binding.root)
        }
    }

    private fun loadHistory() {
        val sessions = repository.getAllSessions()
        historyAdapter.updateSessions(sessions)
        
        if (sessions.isEmpty()) {
            binding.textViewEmptyHistory.visibility = View.VISIBLE
            binding.recyclerViewChatHistory.visibility = View.GONE
        } else {
            binding.textViewEmptyHistory.visibility = View.GONE
            binding.recyclerViewChatHistory.visibility = View.VISIBLE
        }
    }

    fun createNewChat() {
        // Start a transient (unsaved) new chat. We do NOT create or persist a
        // ChatSession here. The session will be created only when the user
        // actually sends the first message (see addMessage).
        // If we're already in a transient new chat (currentSession == null),
        // do nothing.
        if (currentSession == null) {
            // already in a new transient chat
            onNewChatRequested()
            return
        }

        // If the currently selected session exists but has no messages, reuse it
        // (it's effectively an empty saved session). Otherwise enter a transient
        // unsaved session by clearing currentSession.
        if (currentSession!!.messages.isEmpty()) {
            onSessionSelected(currentSession!!)
            loadHistory()
            return
        }

        // Clear currentSession to indicate a transient (unsaved) chat is active
        currentSession = null
        onNewChatRequested()
    }

    fun loadSession(session: ChatSession) {
        currentSession = session
        onSessionSelected(session)
    }

    fun getCurrentSession(): ChatSession? = currentSession

    fun addMessage(message: ChatMessage) {
        // If there's no currentSession it means we're in a transient (unsaved)
        // new chat. Create and persist a new session now and add the message to
        // it. If there is a current saved session, append to it.
        val session = currentSession ?: repository.createNewSession().also { currentSession = it }
        repository.addMessage(session.id, message)
        loadHistory()
    }

    fun updateLastMessage(updatedMessage: ChatMessage) {
        currentSession?.let { session ->
            repository.updateLastMessage(session.id, updatedMessage)
        }
    }

    fun initializeOrLoadSession() {
        // At startup, select the most recent saved session if one exists.
        // If there are no saved sessions, start with a transient (unsaved)
        // chat (currentSession == null) so we don't create empty persisted
        // sessions until the user actually sends a message.
        val sessions = repository.getAllSessions()
        if (sessions.isNotEmpty()) {
            currentSession = sessions.first()
            onSessionSelected(currentSession!!)
        } else {
            currentSession = null
            onNewChatRequested()
        }
    }

    private fun showDeleteConfirmation(session: ChatSession) {
        AlertDialog.Builder(context)
            .setTitle("Delete Chat")
            .setMessage("Are you sure you want to delete this chat?")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteSession(session.id)
                
                // If we deleted the current session, start a new one
                if (currentSession?.id == session.id) {
                    // Start a transient unsaved chat after deleting the current
                    // persisted session so the user can begin typing without
                    // creating another saved empty session.
                    currentSession = null
                    onNewChatRequested()
                } else {
                    loadHistory()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

