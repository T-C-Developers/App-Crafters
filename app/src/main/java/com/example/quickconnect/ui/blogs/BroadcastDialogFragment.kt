
package com.example.quickconnect.ui.blogs

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.Packet
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.BroadcastMessage
import com.example.quickconnect.databinding.DialogBroadcastBinding
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.quickconnect.core.CleanupBroadcastWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

class BroadcastDialogFragment : DialogFragment() {
    private var _binding: DialogBroadcastBinding? = null
    private val binding get() = _binding!!
    private var selectedUri: Uri? = null

    private val pickContent = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            binding.ivPreview.setImageURI(it)
            binding.ivPreview.visibility = View.VISIBLE
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBroadcastBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        binding.btnSelectMedia.setOnClickListener {
            pickContent.launch("*/*")
        }
        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSend.setOnClickListener {
            val text   = binding.etMessage.text.toString().takeIf(String::isNotBlank)
            val uri    = selectedUri
            val ts     = System.currentTimeMillis()

            // 1) If you want to keep a stable local copy (so you can show it even after the
            //    original content:// URI might go away), read & write it into cache:
            val localCacheUri: String? = uri?.let {
                val bytes = requireContext().contentResolver.openInputStream(it)!!.use { it.readBytes() }
                val f = File(requireContext().cacheDir, "broadcast_$ts.jpg")
                f.outputStream().use { it.write(bytes) }
                Uri.fromFile(f).toString()
            }

            // 2) save into Room *with* that cache-file URI
            lifecycleScope.launch(Dispatchers.IO) {
                val id = AppDatabase.getInstance(requireContext())
                    .broadcastMessageDAO()
                    .insert(BroadcastMessage(
                        content = text,
                        fileUri = localCacheUri,    // <-- store the real path here
                        timestamp = ts
                    ))

                // schedule cleanup exactly as before…
                val work = OneTimeWorkRequestBuilder<CleanupBroadcastWorker>()
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .setInputData(workDataOf("id" to id))
                    .build()
                WorkManager.getInstance(requireContext()).enqueue(work)
            }

            // 3) send packet (we still Base64-serialize for the wire)
            val base64 = uri?.let {
                requireContext().contentResolver.openInputStream(it)!!.use { it.readBytes() }
                    .let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
            }
            BluetoothService.sendPacket(Packet.BroadcastPacket(
                senderName    = BluetoothService.localDisplayName,
                timestamp   = ts,
                content     = text,
                imageBase64 = base64
            ))

            dialog.dismiss()
        }


        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
