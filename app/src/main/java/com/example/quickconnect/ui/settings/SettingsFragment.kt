package com.example.quickconnect.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.quickconnect.core.ImageService
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.ProfileData
import com.example.quickconnect.data.ProfileDataDAO
import com.example.quickconnect.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private lateinit var imageService: ImageService
    private lateinit var profileImageView: ImageView
    private lateinit var userId: String
    private lateinit var profileDao:ProfileDataDAO

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val database = AppDatabase.getInstance(requireContext())
        profileDao = database.profileDataDAO()

        imageService = ImageService(requireContext())
        userId = "user_id_12345"

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        profileImageView = binding.profileImageView

        loadProfileData()
        // Load Profile Image when fragment opens
        loadProfileImage()

        binding.changeProfilePictureButton.setOnClickListener {
            openFilePicker()
        }
        binding.saveNameButton.setOnClickListener{
            val newName = binding.userNameEditText.text.toString()
            updateUserName(newName)
        }
        binding.discoverableSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateDiscoverableStatus(isChecked)
        }
        binding.soundNotificationToggle.setOnCheckedChangeListener { _, isChecked ->
            updateSoundNotificationStatus(isChecked)
        }
        binding.vibrationToggle.setOnCheckedChangeListener { _, isChecked ->
            updateVibrationNotificationStatus(isChecked)
        }
        binding.readReceiptsToggle.setOnCheckedChangeListener { _, isChecked ->
            updateReadReceiptsStatus(isChecked)
        }
        binding.lastSeenToggle.setOnCheckedChangeListener { _, isChecked ->
            updateShowLastSeenStatus(isChecked)
        }

        return root
    }

    private fun loadProfileData() {
        lifecycleScope.launch {
            val profileData = profileDao.getProfileData()
            profileData?.let {
                binding.userNameEditText.setText(it.displayName)
                binding.discoverableSwitch.isChecked = it.discoverable
                binding.soundNotificationToggle.isChecked = it.soundNotification
                binding.vibrationToggle.isChecked= it.vibrationNotification
                binding.readReceiptsToggle.isChecked = it.readReceipts
                binding.lastSeenToggle.isChecked = it.showLastSeen
            }

            //remove this part later, Need to save the profileData at the first time logging
            if(profileData==null) {
                profileDao.insertOrUpdateProfileData(ProfileData(userId, "", "null", true,true,true,true,true, null))
            }
        }
    }
    private fun updateUserName(newName: String) {
        lifecycleScope.launch {
            profileDao.updateUserName(newName)
            Toast.makeText(requireContext(), "Name Updated Successfully", Toast.LENGTH_SHORT).show()
            loadProfileData() // Refresh UI
        }
    }

    // Update Discoverable Status Only
    private fun updateDiscoverableStatus(isDiscoverable: Boolean) {
        lifecycleScope.launch {
            profileDao.updateDiscoverableStatus(isDiscoverable)
        }
    }

    private fun updateSoundNotificationStatus(soundNotification: Boolean) {
        lifecycleScope.launch {
            profileDao.updateSoundNotificationStatus(soundNotification)
        }
    }

    private fun updateVibrationNotificationStatus(vibrationNotification: Boolean) {
        lifecycleScope.launch {
            profileDao.updateVibrationNotificationStatus(vibrationNotification)
        }
    }

    private fun updateReadReceiptsStatus(readReceipts: Boolean) {
        lifecycleScope.launch {
            profileDao.updateReadReceiptsStatus(readReceipts)
        }
    }

    private fun updateShowLastSeenStatus(showLastSeen: Boolean) {
        lifecycleScope.launch {
            profileDao.updateShowLastSeenStatus(showLastSeen)
        }
    }

    private fun loadProfileImage() {
        lifecycleScope.launch {
            val uri = imageService.loadProfileImage(userId)
            uri?.let {
                profileImageView.setImageURI(it)
            } ?: run {
                Toast.makeText(requireContext(), "No profile image found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE_PICKER_REQUEST_CODE)
    }

    // Handle the result of image selection
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null) {
                lifecycleScope.launch {
                    val filePath = imageService.saveProfileImage(userId, imageUri)
                    if (filePath != null) {
                        profileImageView.setImageURI(Uri.parse(filePath))
                        Toast.makeText(requireContext(), "Profile picture updated.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    companion object {
        private const val IMAGE_PICKER_REQUEST_CODE = 100
    }
}