package com.example.quickconnect.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProfileDataDAO {

    // Insert or Update Profile Data (Single User Only)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfileData(profileData: ProfileData)

    // Get the Single Profile Data
    @Query("SELECT * FROM profile_data LIMIT 1")
    suspend fun getProfileData(): ProfileData?

    // Update User Name Only
    @Query("UPDATE profile_data SET displayName = :newName")
    suspend fun updateUserName(newName: String)

    // Update Device Name Only
    @Query("UPDATE profile_data SET deviceName = :newDeviceName")
    suspend fun updateDeviceName(newDeviceName: String)

    // Update Discoverable Status Only
    @Query("UPDATE profile_data SET discoverable = :isDiscoverable")
    suspend fun updateDiscoverableStatus(isDiscoverable: Boolean)

    // Update Profile Image Path Only
    @Query("UPDATE profile_data SET profileImagePath = :imagePath")
    suspend fun updateProfileImage(imagePath: String)

    // Delete Profile Data (Not usually needed)
    @Query("DELETE FROM profile_data")
    suspend fun deleteProfileData()
}
