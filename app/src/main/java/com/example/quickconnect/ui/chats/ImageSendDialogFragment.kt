package com.example.quickconnect.ui.chats

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.Packet
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.databinding.DialogBroadcastBinding
import com.example.quickconnect.data.DirectMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ImageSendDialogFragment(private val peerId: String) : DialogFragment() {

    private var _binding: DialogBroadcastBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null
    private var selectedMimeType: String? = null
    private var selectedFileName: String? = null
    private var selectedBase64: String? = null

    private val pickContent = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 1) Save the URI, MIME type, and filename
            selectedUri = it
            selectedMimeType = requireContext().contentResolver.getType(it) ?: "application/octet-stream"
            selectedFileName = queryFileName(it)

            // 2) Read bytes, convert to Base64
            val bytes = requireContext().contentResolver.openInputStream(it)!!.use { stream ->
                stream.readBytes()
            }
            selectedBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

            // 3) If it’s an image, show a preview. Otherwise, hide preview and place filename in message field.
            if (selectedMimeType?.startsWith("image/") == true) {
                binding.ivPreview.visibility = View.VISIBLE
                binding.ivPreview.setImageURI(it)
                binding.etMessage.setText("") // clear any earlier filename hint
            } else {
                // Not an image: hide the ImageView preview
                binding.ivPreview.visibility = View.GONE

                // Show the chosen filename in the EditText (so user knows what’s selected)
                binding.selectedFileName.text = buildString {
                    append("📎")
                    append(selectedFileName)
                }
//                binding.etMessage.setText()
                binding.etMessage.setSelection(binding.etMessage.text?.length ?: 0)
            }
        }
    }

    @SuppressLint("UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBroadcastBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        // When “Select” is tapped, launch the system picker for PDF/TXT or images
        binding.btnSelectMedia.setOnClickListener {
            // We allow any type, but filter to PDF or TXT or images:
            pickContent.launch("*/*")
        }

        // Close button simply dismisses
        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // When “Send” is tapped:
        binding.btnSend.setOnClickListener {
            val userText = binding.etMessage.text.toString().takeIf { it.isNotBlank() }
            val uri = selectedUri
            val mime = selectedMimeType
            val fileName = selectedFileName
            val base64 = selectedBase64
            val ts = System.currentTimeMillis()

            // 1) Build a local cache copy (if a URI was chosen)
            val localCacheUriString: String? = uri?.let {
                // Use the original filename (safe) for caching, so extension is correct
                val safeName = fileName
                    ?.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    ?: "file_$ts"

                val cacheFile = File(requireContext().cacheDir, safeName)
                // Decode the Base64 we already created to bytes again:
                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                cacheFile.outputStream().use { out ->
                    out.write(bytes)
                }
                Uri.fromFile(cacheFile).toString()
            }

            // 2) Insert into Room (DirectMessage)
            lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext())
                    .directMessageDAO()
                    .insert(
                        DirectMessage(
                            senderId = BluetoothService.localDisplayName,
                            receiverId = peerId,
                            timestamp = ts,
                            content = userText.orEmpty(),
                            fileUri = localCacheUriString,
                            fileName = if (uri != null) fileName else null,
                            isRead = false
                        )
                    )
            }

            // 3) Send Packet over Bluetooth
            //    If it was an image, put it into imageBase64. Otherwise, fill fileBase64 + fileName + fileMimeType.
            val packet = if (mime?.startsWith("image/") == true) {
                Packet.MessagePacket(
                    senderId = BluetoothService.localDisplayName,
                    receiverId = peerId,
                    timestamp = ts,
                    content = userText.orEmpty(),
                    imageBase64 = base64,
                    fileName = null,
                    fileBase64 = null,
                    fileMimeType = null
                )
            } else if (uri != null && fileName != null && base64 != null) {
                Packet.MessagePacket(
                    senderId = BluetoothService.localDisplayName,
                    receiverId = peerId,
                    timestamp = ts,
                    content = userText.orEmpty(),
                    imageBase64 = null,
                    fileName = fileName,
                    fileBase64 = base64,
                    fileMimeType = mime
                )
            } else {
                // No file/image picked—just a text‐only message
                Packet.MessagePacket(
                    senderId = BluetoothService.localDisplayName,
                    receiverId = peerId,
                    timestamp = ts,
                    content = userText.orEmpty(),
                    imageBase64 = null,
                    fileName = null,
                    fileBase64 = null,
                    fileMimeType = null
                )
            }
            BluetoothService.sendPacket(packet)

            dialog.dismiss()
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Helper: Query DISPLAY_NAME for a content:// URI so we can get the “real” file name.
     */
    @Suppress("Range")
    private fun queryFileName(uri: Uri): String {
        var name = "unknown"
        val cursor = requireContext().contentResolver
            .query(uri, null, null, null, null)
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    name = c.getString(idx)
                }
            }
        }
        return name
    }
}
