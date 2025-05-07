package com.example.quickconnect.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GroupMessageDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: GroupMessage)

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp DESC")
    suspend fun getMessagesForGroup(groupId: String): List<GroupMessage>
}
