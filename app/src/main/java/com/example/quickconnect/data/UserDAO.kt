package com.example.appcrafters.data

import androidx.room.*

@Dao
interface UserDAO {

    // Insert a new user
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // Insert multiple users
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    // Update an existing user (e.g., online status or name)
    @Update
    suspend fun updateUser(user: User)

    // Delete a user
    @Delete
    suspend fun deleteUser(user: User)

    // Get a user by ID
    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): User?

    // Get all users
    @Query("SELECT * FROM users ORDER BY displayName ASC")
    suspend fun getAllUsers(): List<User>

    // Get only online users
    @Query("SELECT * FROM users WHERE isOnline = 1")
    suspend fun getOnlineUsers(): List<User>
}
