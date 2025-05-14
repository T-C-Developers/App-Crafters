package com.example.quickconnect.ui.chats

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.MessagePacket
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
    private val db by lazy { AppDatabase.getInstance(this) }
    private val messageDao by lazy { db.directMessageDAO() }

    private val peerId: String by lazy { intent.getStringExtra("EXTRA_USER_ID")!! }
    private val peerName: String by lazy { intent.getStringExtra("EXTRA_DISPLAY_NAME")!! }
    private val localUserId: String by lazy { BluetoothService.localUserId }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = peerName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = ChatMessageAdapter(localUserId)
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = adapter

        loadMessages()

        lifecycleScope.launch {
            BluetoothService.incoming
                .filterIsInstance<MessagePacket>()
                .collect { pkt ->
                    if (pkt.senderId == peerId) {
                        val dm = DirectMessage(
                            senderId = pkt.senderId,
                            receiverId = localUserId,
                            timestamp = pkt.timestamp,
                            content = pkt.content,
                            isRead = true
                        )
                        withContext(Dispatchers.IO) { messageDao.insert(dm) }
                        loadMessages()
                    }
                }
        }

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(); true
            } else false
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val dm = DirectMessage(
            senderId = localUserId,
            receiverId = peerId,
            timestamp = System.currentTimeMillis(),
            content = text,
            isRead = false
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { messageDao.insert(dm) }
            BluetoothService.sendPacket(
                MessagePacket(
                    senderId = localUserId,
                    receiverId = peerId,
                    timestamp = dm.timestamp,
                    content = dm.content
                )
            )
            binding.etMessage.text?.clear()
            loadMessages()
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val msgs = withContext(Dispatchers.IO) { messageDao.getMessagesForUser(peerId) }
            adapter.updateData(msgs.sortedBy { it.timestamp })
            binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
        }
    }
}
