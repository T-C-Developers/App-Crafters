package com.example.quickconnect.core

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageService(private val context: Context) {

    private val _imageLiveData = MutableLiveData<Uri?>()
    val imageLiveData: LiveData<Uri?> get() = _imageLiveData

    // Save Profile Image to Internal Storage
    suspend fun saveProfileImage(userId: String, uri: Uri?): String? {
        return withContext(Dispatchers.IO) {
            if (uri == null) return@withContext null

            val directory = File(context.filesDir, "ProfilePictures")
            if (!directory.exists()) directory.mkdirs()

            val profileFile = File(directory, "$userId.jpg")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                profileFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            profileFile.absolutePath
        }
    }

    // Load Profile Image
    suspend fun loadProfileImage(userId: String): Uri? {
        return withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "ProfilePictures")
            val profileFile = File(directory, "$userId.jpg")

            if (profileFile.exists()) {
                Uri.fromFile(profileFile)
            } else {
                null
            }
        }
    }

    // Delete Profile Image
    suspend fun deleteProfileImage(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "ProfilePictures")
            val profileFile = File(directory, "$userId.jpg")
            if (profileFile.exists()) {
                profileFile.delete()
            } else {
                false
            }
        }
    }

    // Set the LiveData for UI update
    suspend fun updateImageLiveData(userId: String) {
        val uri = loadProfileImage(userId)
        _imageLiveData.postValue(uri)
    }
}
