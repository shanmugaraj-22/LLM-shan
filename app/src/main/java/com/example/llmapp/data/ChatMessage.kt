package com.example.llmapp.data

import java.io.Serializable

/**
 * Data class representing a single chat message
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
