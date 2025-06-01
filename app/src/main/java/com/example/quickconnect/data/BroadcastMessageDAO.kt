package com.example.quickconnect.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BroadcastMessageDAO {
    @Query("SELECT * FROM broadcast_messages ORDER BY timestamp DESC")
    fun getAll(): LiveData<List<BroadcastMessage>>

    /** Inserts a broadcast and returns the new row id. */
    @Insert
    suspend fun insert(message: BroadcastMessage): Long

    @Query("DELETE FROM broadcast_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Fetch broadcasts newer than the cutoff timestamp */
    @Query("SELECT * FROM broadcast_messages WHERE timestamp >= :cutoff")
    suspend fun getRecent(cutoff: Long): List<BroadcastMessage>
}
