package com.example.quickconnect.ui.chats

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.databinding.FragmentChatsBinding
import com.example.quickconnect.ui.bluetooth.BluetoothDiscoveryActivity

class ChatsFragment : Fragment(), BluetoothService.Callback {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var service: BluetoothService
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var fullChatList: List<ChatItem>

    private val devicePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val device: BluetoothDevice? = res.data?.getParcelableExtra("device")
        device?.let { service.connect(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)

        fullChatList = getSampleChats()
        chatAdapter = ChatAdapter()
        chatAdapter.submitList(fullChatList)
        updateEmptyState(fullChatList)

        binding.chatList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatList.adapter = chatAdapter

        chatAdapter.setOnChatClick { chat ->
            val intent = Intent(requireContext(), ChatScreenActivity::class.java)
            intent.putExtra("currentUserId", "me")          // TODO replace with real ID
            intent.putExtra("peerId", chat.name)              // TODO map name -> userId in DB
            intent.putExtra("peerName", chat.name)
            startActivity(intent)
        }

        binding.btnConnect.setOnClickListener {
            val intent = Intent(requireContext(), BluetoothDiscoveryActivity::class.java)
            startActivity(intent)
        }

        // Search logic - Check Names and last message
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase()
                val filtered = fullChatList.filter {
                    it.name.lowercase().contains(query) || it.message.lowercase().contains(query)
                }
                chatAdapter.submitList(filtered)
                updateEmptyState(filtered)
                binding.btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text.clear()
        }

        return binding.root
    }

    private fun updateEmptyState(list: List<ChatItem>) {
        binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = BluetoothService(requireContext(), this)
        service.startServer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        service.stop()
    }

    /* -------- BluetoothService.Callback -------- */
    override fun onConnected(device: BluetoothDevice) {
        Toast.makeText(requireContext(), "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onConnectionFailed() {
        Toast.makeText(requireContext(), "Connection failed", Toast.LENGTH_SHORT).show()
    }

    override fun onMessageRead(message: String) {}
    override fun onMessageWritten(message: String) {}

    private fun getSampleChats(): List<ChatItem> {
        return listOf(
            ChatItem("Cameron", "Sure! That sounds good.", "Yesterday"),
            ChatItem("Jenny", "Are you coming today?", "Yesterday"),
            ChatItem("Kristin", "See you soon!", "Yesterday"),
            ChatItem("Esther Howard", "I'll send the report", "Yesterday"),
            ChatItem("Jenny Wilson", "Are you coming today?", "Yesterday"),
            ChatItem("Kristin Watson", "See you soon!", "Yesterday"),
            ChatItem("Esther Howard", "I'll send the report", "Yesterday"),
            ChatItem("Jenny Wilson", "Are you coming today?", "10:30 AM"),
            ChatItem("Kristin Watson", "See you soon!", "Yesterday"),
            ChatItem("Esther Howard", "I'll send the report", "Yesterday"),
            ChatItem("Wade Warren", "Please call me when...", "Sunday")
        )
    }
}
