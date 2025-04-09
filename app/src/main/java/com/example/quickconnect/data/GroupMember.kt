package com.example.appcrafters.data

import androidx.room.Entity

@Entity(tableName = "group_members", primaryKeys = ["groupId", "userId"])
data class GroupMember(
    val groupId: String,
    val userId: String
)
