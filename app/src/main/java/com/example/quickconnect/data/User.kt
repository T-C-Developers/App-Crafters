package com.example.quickconnect.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String, // UUID or MAC address
    val displayName: String,
    val deviceName: String,
    val isOnline: Boolean,       //
    val lastSeen: String
)
