package com.example.quickconnect.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_data")
data class ProfileData(
    @PrimaryKey val userId: String, // UUID or MAC address
    val displayName: String,
    val deviceName: String="",
    val discoverable: Boolean=true,
    val soundNotification:Boolean=true,
    val vibrationNotification:Boolean=true,
    val readReceipts:Boolean=true,
    val showLastSeen:Boolean=true,
    val profileImagePath: String? = null  // Store the image file path
)