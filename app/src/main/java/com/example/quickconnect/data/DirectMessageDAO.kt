package com.example.quickconnect.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DirectMessageDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: DirectMessage)

    // old query - used in chats fragment
    @Query("SELECT * FROM direct_messages WHERE senderId = :userId OR receiverId = :userId ORDER BY timestamp DESC")
    suspend fun getMessagesForUser(userId: String): List<DirectMessage>

    // new - used in chat message screen
    @Query("""
      SELECT * FROM direct_messages 
       WHERE (senderId   = :peerId AND receiverId = :me)
          OR (senderId   = :me     AND receiverId = :peerId)
      ORDER BY timestamp ASC
    """)
    fun getConversationLive(peerId: String, me: String): LiveData<List<DirectMessage>>
}
