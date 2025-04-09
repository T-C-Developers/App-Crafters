package com.example.appcrafters.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_messages")
data class GroupMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: String,
    val senderId: String,
    val timestamp: Long,
    val content: String
)
