package com.example.appcrafters.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DirectMessageDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: DirectMessage)

    @Query("SELECT * FROM direct_messages WHERE senderId = :userId OR receiverId = :userId ORDER BY timestamp DESC")
    suspend fun getMessagesForUser(userId: String): List<DirectMessage>
}
