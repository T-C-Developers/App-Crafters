package com.example.quickconnect.ui.chats

import android.Manifest
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.Packet.MessagePacket
import com.example.quickconnect.core.UserPrefs
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.DirectMessage
import com.example.quickconnect.databinding.ActivityChatScreenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatScreenActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatScreenBinding
    private lateinit var adapter: ChatMessageAdapter

    // always the same stable per-device ID
    private val localUserId: String by lazy {
        UserPrefs.getUserId(this)
    }
    private val peerId: String by lazy {
        intent.getStringExtra("EXTRA_USER_ID")!!
    }
    private val peerName: String by lazy {
        intent.getStringExtra("EXTRA_DISPLAY_NAME")!!
    }

    private val db    by lazy { AppDatabase.getInstance(this) }
    private val msgDao by lazy { db.directMessageDAO() }

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

        val isConnected = BluetoothService.isConnected(peerId)
        if (isConnected){
            binding.connected.visibility = View.VISIBLE
            binding.btnConnect.visibility = View.GONE
        }
        else{
            binding.connected.visibility = View.GONE
            binding.btnConnect.visibility = View.VISIBLE
        }
        binding.btnConnect.setOnClickListener {
            BluetoothService.connectFromChat(peerId)
        }

        adapter = ChatMessageAdapter(localUserId)
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = adapter

        loadMessages()

        lifecycleScope.launch {
            BluetoothService.incoming
                .filterIsInstance<MessagePacket>()
                .collect { pkt ->
                    if (BluetoothService.macAddress == peerId) {
                        val dm = DirectMessage(
                            senderId   = peerId,
                            receiverId = localUserId,
                            timestamp  = pkt.timestamp,
                            content    = pkt.content,
                            isRead     = true
                        )
                        withContext(Dispatchers.IO) { msgDao.insert(dm) }
                        loadMessages()
                    }
                }
        }

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish(); true
        } else super.onOptionsItemSelected(item)
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val dm = DirectMessage(
            senderId   = localUserId,
            receiverId = peerId,
            timestamp  = System.currentTimeMillis(),
            content    = text,
            isRead     = false
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { msgDao.insert(dm) }
            BluetoothService.sendPacket(
                MessagePacket(
                    senderId   = localUserId,
                    receiverId = peerId,
                    timestamp  = dm.timestamp,
                    content    = dm.content
                )
            )
            binding.etMessage.text?.clear()
            loadMessages()
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val msgs = withContext(Dispatchers.IO) {
                msgDao.getMessagesForUser(peerId)
            }
            adapter.updateData(msgs.sortedBy { it.timestamp })
            if (adapter.itemCount > 0) {
                binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }
}
