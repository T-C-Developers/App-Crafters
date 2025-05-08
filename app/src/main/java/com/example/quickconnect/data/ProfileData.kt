package com.example.quickconnect.data

import android.graphics.Picture
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_data")
data class ProfileData(
    @PrimaryKey val userId: String, // UUID or MAC address
    val displayName: String,
    val deviceName: String,
    val discoverable: Boolean,
    val profileImagePath: String? = null  // Store the image file path
)