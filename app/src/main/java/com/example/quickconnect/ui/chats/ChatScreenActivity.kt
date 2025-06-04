package com.example.quickconnect.ui.chats

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.Packet.MessagePacket
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.DirectMessage
import com.example.quickconnect.databinding.ActivityChatScreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

class ChatScreenActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatScreenBinding
    private lateinit var adapter: ChatMessageAdapter

    private val localUserId: String by lazy { BluetoothService.localDisplayName }
    private val peerId:       String by lazy { intent.getStringExtra("EXTRA_PEER_ID")!! }
    private val peerName:     String by lazy { intent.getStringExtra("EXTRA_DISPLAY_NAME")!! }

    private val msgDao by lazy { AppDatabase.getInstance(this).directMessageDAO() }

    companion object {
        private const val REQUEST_CODE_PICK_FILE = 102
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = peerName
            setDisplayHomeAsUpEnabled(true)
        }

        if (BluetoothService.isConnected(peerId)) {
            binding.connected.visibility = View.VISIBLE
            binding.btnConnect.visibility = View.GONE
        } else {
            binding.connected.visibility = View.GONE
            binding.btnConnect.visibility = View.VISIBLE
        }
        binding.btnConnect.setOnClickListener {
            BluetoothService.connectFromChat(peerId)
        }

        // RecyclerView + adapter
        adapter = ChatMessageAdapter(peerId)
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter    = adapter

        // 1:1 incoming packet listener only to scroll when active
        lifecycleScope.launch {
            BluetoothService.incoming
                .filterIsInstance<MessagePacket>()
                .filter { it.senderId == peerId }
                .collect {
                    // force scroll-to-bottom on new message
                    binding.rvMessages.post {
                        adapter.itemCount.takeIf { it>0 }?.let {
                            binding.rvMessages.scrollToPosition(it-1)
                        }
                    }
                }
        }

        // **Observe the DB** — updates in real time
        msgDao.getConversationLive(peerId, localUserId)
            .observe(this) { msgs: List<DirectMessage> ->
                adapter.updateData(msgs)
                if (msgs.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(msgs.size - 1)
                }
            }

        // Send button + IME send
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etMessage.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) {
                sendMessage(); true
            } else false
        }

        binding.btnAttach.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/plain"))
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
//            ImageSendDialogFragment(peerId).show(supportFragmentManager, "ImageSendDialog")
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val dm = DirectMessage(
            senderId   = localUserId,
            receiverId = peerId,
            timestamp  = System.currentTimeMillis(),
            content    = text,
            fileUri    = null,
            fileName   = null,
            isRead     = false
        )

        // 1) write to DB
        lifecycleScope.launch(Dispatchers.IO) {
            msgDao.insert(dm)
        }

        // 2) send over Bluetooth
        BluetoothService.sendPacket(
            MessagePacket(
                senderId   = localUserId,
                receiverId = peerId,
                timestamp  = dm.timestamp,
                content    = dm.content.orEmpty(),
                imageBase64 = null
            )
        )

        // 3) clear input
        binding.etMessage.text?.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val fileBytes = inputStream.readBytes()
                    val fileBase64 = Base64.encodeToString(fileBytes, Base64.DEFAULT)
                    val fileName = getFileName(uri)
                    val fileType = contentResolver.getType(uri) ?: "application/octet-stream"

                    val timestamp = System.currentTimeMillis()

                    val dm = DirectMessage(
                        senderId = localUserId,
                        receiverId = peerId,
                        timestamp = timestamp,
                        content = null,
                        fileUri = uri.toString(),
                        fileName = "temp",
                        isRead = false
                    )

                    lifecycleScope.launch(Dispatchers.IO) {
                        msgDao.insert(dm)
                    }

                    BluetoothService.sendPacket(
                        MessagePacket(
                            senderId = localUserId,
                            receiverId = peerId,
                            timestamp = timestamp,
                            fileName = fileName,
                            fileMimeType = fileType,
                            fileBase64 = fileBase64
                        )
                    )
                }
            }
        }
    }

    @SuppressLint("Range")
    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        }
        return name
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (item.itemId == android.R.id.home) { finish(); true }
        else super.onOptionsItemSelected(item)
}
