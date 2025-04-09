package com.example.appcrafters.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GroupDAO {

    // Insert new group
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    // Insert multiple group members
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(members: List<GroupMember>)

    // Add a single user to a group
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addUserToGroup(member: GroupMember)

    // Get group by ID
    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getGroupById(groupId: String): Group?

    // Get all groups
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    suspend fun getAllGroups(): List<Group>

    // Get all users in a group
    @Query("""
        SELECT u.* FROM users u
        INNER JOIN group_members gm ON u.userId = gm.userId
        WHERE gm.groupId = :groupId
    """)
    suspend fun getUsersInGroup(groupId: String): List<User>

    // Get all groups a user is part of
    @Query("""
        SELECT g.* FROM groups g
        INNER JOIN group_members gm ON g.groupId = gm.groupId
        WHERE gm.userId = :userId
    """)
    suspend fun getGroupsForUser(userId: String): List<Group>
}
