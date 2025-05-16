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
import androidx.navigation.fragment.findNavController
import com.example.quickconnect.R
import com.example.quickconnect.core.ImageService
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.ProfileData
import com.example.quickconnect.data.ProfileDataDAO
import com.example.quickconnect.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private lateinit var imageService: ImageService
    private lateinit var profileImageView:ImageView
    private lateinit var userId:String
    private lateinit var profileDao:ProfileDataDAO

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val database = AppDatabase.getInstance(requireContext())
        profileDao = database.profileDataDAO()

        imageService = ImageService(requireContext())
        //Todo update userId
        userId = "user_id_12345"

        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        val root: View = binding.root

        profileImageView = binding.imageProfile
        binding.tvSelectImage.setOnClickListener {
            openFilePicker()
        }
        binding.btnSaveProfile.setOnClickListener{
            val newName = binding.etDisplayName.text.toString()
            registerUser(userId,newName)
        }

        return root
    }

    private fun registerUser(userId:String,newName:String){
        lifecycleScope.launch {
            val profileImagePath = "ProfilePictures/$userId.jpg"
            val profileData = ProfileData(userId=userId, displayName =newName,profileImagePath=profileImagePath)
            profileDao.insertOrUpdateProfileData(profileData)
            findNavController().navigate(R.id.nav_chats)
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