package com.example.llmapp.data

import java.io.Serializable

/**
 * Data class representing a complete chat session/conversation
 */
data class ChatSession(
    val id: String,
    val title: String,
    val messages: MutableList<ChatMessage>,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
) : Serializable {
    companion object {
        /**
         * Generate a default title from the first user message
         */
        fun generateTitle(messages: List<ChatMessage>): String {
            val firstUserMessage = messages.firstOrNull { it.isUser }
            return if (firstUserMessage != null) {
                // Use first 50 characters as title
                firstUserMessage.text.take(50).ifEmpty { "New Chat" }
            } else {
                "New Chat"
            }
        }
    }
}
