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
import com.example.quickconnect.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private lateinit var imageService: ImageService
    private lateinit var profileImageView: ImageView
    private lateinit var userId: String


    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        imageService = ImageService(requireContext())
        userId = "user_id_12345"

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        profileImageView = binding.profileImageView
        val changeProfilePictureButton = binding.changeProfilePictureButton
        val saveNameButton = binding.saveNameButton

        // Load Profile Image when fragment opens
        loadProfileImage()

        changeProfilePictureButton.setOnClickListener {
            openFilePicker()
        }
        saveNameButton.setOnClickListener{
            saveDisplayName()
        }

        return root
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

    private fun saveDisplayName(){

    }

    companion object {
        private const val IMAGE_PICKER_REQUEST_CODE = 100
    }
}