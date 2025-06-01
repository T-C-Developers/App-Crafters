package com.example.quickconnect.ui.chats

import android.Manifest
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
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
import kotlinx.coroutines.withContext

class ChatScreenActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatScreenBinding
    private lateinit var adapter: ChatMessageAdapter

    private val localUserId: String by lazy { BluetoothService.localDisplayName }
    private val peerId:       String by lazy { intent.getStringExtra("EXTRA_PEER_ID")!! }
    private val peerName:     String by lazy { intent.getStringExtra("EXTRA_DISPLAY_NAME")!! }

    private val msgDao by lazy { AppDatabase.getInstance(this).directMessageDAO() }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = peerName
            setDisplayHomeAsUpEnabled(true)
        }

        // Connection indicator
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
                content    = dm.content
            )
        )

        // 3) clear input
        binding.etMessage.text?.clear()
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (item.itemId == android.R.id.home) { finish(); true }
        else super.onOptionsItemSelected(item)
}
