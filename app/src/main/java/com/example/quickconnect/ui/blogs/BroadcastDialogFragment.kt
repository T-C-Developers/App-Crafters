
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
            val text = binding.etMessage.text.toString().takeIf(String::isNotBlank)
            val uriStr = selectedUri?.toString()
            val timestamp = Date().time

            // Insert into DB and schedule deletion
            lifecycleScope.launch(Dispatchers.IO) {
                val id = AppDatabase.getInstance(requireContext())
                    .broadcastMessageDAO()
                    .insert(BroadcastMessage(content = text, fileUri = uriStr, timestamp = timestamp))

                val work = OneTimeWorkRequestBuilder<CleanupBroadcastWorker>()
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .setInputData(workDataOf("id" to id))
                    .build()
                WorkManager.getInstance(requireContext()).enqueue(work)
            }

            // Broadcast via Bluetooth
            BluetoothService.sendPacket(
                Packet.BroadcastPacket(
                    senderName  = BluetoothService.localDisplayName,
                    timestamp = timestamp,
                    content   = text,
                    fileUri   = uriStr
                )
            )

            dialog.dismiss()
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
