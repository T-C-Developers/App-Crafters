package com.example.quickconnect.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "broadcast_messages")
data class BroadcastMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String?,
    val fileUri: String?,
    val timestamp: Long
)