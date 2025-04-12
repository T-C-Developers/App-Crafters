package com.example.quickconnect.model

/** UI‑level model representing a single direct message */
data class ChatMessage(
    val id: Int = 0,          // DB primary key (optional for UI)
    val text: String,
    val timestamp: Long,        // epoch millis
    val isSentByMe: Boolean,    // true = outgoing, false = incoming
    val isRead: Boolean         // only used for outgoing msgs
)