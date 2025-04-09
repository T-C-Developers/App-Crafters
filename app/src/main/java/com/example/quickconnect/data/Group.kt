package com.example.appcrafters.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey val groupId: String,
    val groupName: String,
    val createdAt: Long
)
