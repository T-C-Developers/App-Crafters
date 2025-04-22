package com.example.quickconnect.ui.chats

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.R
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.DirectMessage
import com.example.quickconnect.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatScreenActivity : AppCompatActivity(), BluetoothService.Callback {

    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var bluetoothService: BluetoothService
    private var connectedDevice: BluetoothDevice? = null

    /** The id of *this* user and the peer, passed in the Intent */
    private val currentUserId by lazy { intent.getStringExtra("currentUserId") ?: "" }      // currentUserId = me,  for now
    private val peerId        by lazy { intent.getStringExtra("peerId") ?: "" }             // peerId = peerName, for now
    private val peerName      by lazy { intent.getStringExtra("peerName") ?: "Unknown" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_screen)

        /* Toolbar */
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = peerName

        /* Views */
        rvMessages = findViewById(R.id.rvMessages)
        etMessage  = findViewById(R.id.etMessage)
        val btnSend: ImageButton = findViewById(R.id.btnSend)

        messageAdapter = ChatMessageAdapter(mutableListOf())
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = messageAdapter

        bluetoothService = BluetoothService(this, this) // already connected

        btnSend.setOnClickListener { sendMessage() }

        loadHistory()
    }

    /** Load previous direct messages with this peer from RoomDB */
    private fun loadHistory() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@ChatScreenActivity).directMessageDAO()
            val history = withContext(Dispatchers.IO) {
                dao.getMessagesForUser(currentUserId)
                    .filter { it.senderId == peerId || it.receiverId == peerId }
                    .sortedBy { it.timestamp }
            }
            history.forEach { dm ->
                messageAdapter.add(
                    ChatMessage(
                        id         = dm.id,
                        text       = dm.content,
                        timestamp  = dm.timestamp,
                        isSentByMe = dm.senderId == currentUserId,
                        isRead     = dm.isRead
                    )
                )
            }
            rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
        }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return
        val now = System.currentTimeMillis()

        /* UI update */
        messageAdapter.add(ChatMessage(text = text, timestamp = now, isSentByMe = true, isRead = false))
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
        etMessage.setText("")       // clear msg typing box

        /* Save locally + send */
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(this@ChatScreenActivity)
                .directMessageDAO()
                .insert(
                    DirectMessage(
                        senderId = currentUserId,
                        receiverId = peerId,
                        timestamp = now,
                        content = text,
                        isRead = false
                    )
                )
        }

        connectedDevice?.let {
            bluetoothService.write(it, text.toByteArray())
        } ?:
            Toast.makeText(this, "Not connected to peer", Toast.LENGTH_SHORT).show()
    }

    /* ---------- BluetoothService.Callback ---------- */
    override fun onConnected(device: BluetoothDevice) {
        connectedDevice = device
    }

    override fun onConnectionFailed() {
        Toast.makeText(this, "Bluetooth connection failed", Toast.LENGTH_SHORT).show()
    }

    override fun onMessageRead(message: String) {
        val now = System.currentTimeMillis()
        runOnUiThread {
            messageAdapter.add(ChatMessage(text = message, timestamp = now, isSentByMe = false))
            rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(this@ChatScreenActivity)
                .directMessageDAO()
                .insert(
                    DirectMessage(
                        senderId = peerId,
                        receiverId = currentUserId,
                        timestamp = now,
                        content = message,
                        isRead = true
                    )
                )
        }
        // TODO - have to send receipts to other user
    }

    override fun onMessageWritten(message: String) {}

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
