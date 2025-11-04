package com.example.llmapp.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing chat history persistence and retrieval
 */
class ChatHistoryRepository(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("chat_history_prefs", Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    private val sessions = mutableListOf<ChatSession>()

    companion object {
        private const val KEY_CHAT_HISTORY = "chat_history"
    }

    init {
        loadSessions()
    }

    /**
     * Load all chat sessions from SharedPreferences
     */
    private fun loadSessions() {
        val json = prefs.getString(KEY_CHAT_HISTORY, null)
        if (json != null) {
            val type = object : TypeToken<List<ChatSession>>() {}.type
            try {
                val loadedSessions = gson.fromJson<List<ChatSession>>(json, type) as MutableList
                sessions.clear()
                sessions.addAll(loadedSessions)
            } catch (e: Exception) {
                sessions.clear()
            }
        }
    }

    /**
     * Save all chat sessions to SharedPreferences
     */
    private fun saveSessions() {
        val json = gson.toJson(sessions)
        prefs.edit().putString(KEY_CHAT_HISTORY, json).apply()
    }

    /**
     * Get all chat sessions
     */
    fun getAllSessions(): List<ChatSession> = sessions.toList()

    /**
     * Get a specific chat session by ID
     */
    fun getSession(sessionId: String): ChatSession? {
        return sessions.find { it.id == sessionId }
    }

    /**
     * Create a new chat session
     */
    fun createNewSession(): ChatSession {
        val sessionId = System.currentTimeMillis().toString()
        val newSession = ChatSession(
            id = sessionId,
            title = "New Chat",
            messages = mutableListOf(),
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )
        sessions.add(0, newSession) // Add to beginning
        saveSessions()
        return newSession
    }

    /**
     * Update an existing session with new messages
     */
    fun updateSession(session: ChatSession) {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index != -1) {
            // Update the existing session in-place
            val existingSession = sessions[index]
            // Since we can't modify data class fields directly, we replace with updated copy
            sessions[index] = ChatSession(
                id = existingSession.id,
                title = existingSession.title,
                messages = existingSession.messages,
                createdAt = existingSession.createdAt,
                lastUpdated = System.currentTimeMillis()
            )
            saveSessions()
        }
    }

    /**
     * Add a message to a session
     */
    fun addMessage(sessionId: String, message: ChatMessage) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index != -1) {
            val session = sessions[index]
            val updatedMessages = session.messages.toMutableList().apply { add(message) }
            
            // Update title if this is the first user message
            val newTitle = if (message.isUser && session.title == "New Chat") {
                ChatSession.generateTitle(updatedMessages)
            } else {
                session.title
            }
            
            sessions[index] = ChatSession(
                id = session.id,
                title = newTitle,
                messages = updatedMessages,
                createdAt = session.createdAt,
                lastUpdated = System.currentTimeMillis()
            )
            saveSessions()
        }
    }

    /**
     * Update the last message in a session (for streaming)
     */
    fun updateLastMessage(sessionId: String, updatedMessage: ChatMessage) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index != -1) {
            val session = sessions[index]
            val updatedMessages = session.messages.toMutableList()
            if (updatedMessages.isNotEmpty()) {
                // If the last saved message is an assistant placeholder (isUser == false),
                // replace it with the updated assistant text. Otherwise (e.g. if the
                // assistant placeholder wasn't saved), append the updated assistant
                // message so we don't overwrite the user's message.
                val lastIndex = updatedMessages.size - 1
                if (!updatedMessages[lastIndex].isUser) {
                    updatedMessages[lastIndex] = updatedMessage
                } else {
                    // Last saved message was a user message; append assistant reply
                    updatedMessages.add(updatedMessage)
                }

                sessions[index] = ChatSession(
                    id = session.id,
                    title = session.title,
                    messages = updatedMessages,
                    createdAt = session.createdAt,
                    lastUpdated = System.currentTimeMillis()
                )
                saveSessions()
            }
        }
    }

    /**
     * Delete a chat session
     */
    fun deleteSession(sessionId: String) {
        sessions.removeAll { it.id == sessionId }
        saveSessions()
    }

    /**
     * Get the most recent session (or create new one if none exists)
     */
    fun getCurrentOrNewSession(): ChatSession {
        return sessions.firstOrNull() ?: createNewSession()
    }
}

