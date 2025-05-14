package com.example.quickconnect.ui.chats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.databinding.FragmentChatsBinding
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.DirectMessage
import com.example.quickconnect.data.User
import com.example.quickconnect.ui.bluetooth.BluetoothDiscoveryActivity
import com.example.quickconnect.ui.chats.ChatScreenActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsFragment : Fragment() {
    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ChatAdapter
    private val db by lazy { AppDatabase.getInstance(requireContext()) }
    private val userDao by lazy { db.userDAO() }
    private val messageDao by lazy { db.directMessageDAO() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ChatAdapter(emptyList(), emptyMap()) { user ->
            // Launch ChatScreenActivity, passing userId & displayName
            Intent(requireContext(), ChatScreenActivity::class.java).also { intent ->
                intent.putExtra("EXTRA_USER_ID", user.userId)
                intent.putExtra("EXTRA_DISPLAY_NAME", user.displayName)
                startActivity(intent)
            }
        }
        binding.chatList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatList.adapter = adapter

        binding.btnConnect.setOnClickListener {
            // Launch Bluetooth Discovery
            startActivity(Intent(requireContext(), BluetoothDiscoveryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadChats()
    }

    private fun loadChats() {
        lifecycleScope.launch {
            // Fetch all users on IO thread
            val users: List<User> = withContext(Dispatchers.IO) {
                userDao.getAllUsers()
            }

            // Build map of last messages per user
            val lastMsgs = mutableMapOf<String, DirectMessage?>()
            for (user in users) {
                val msgs: List<DirectMessage> = withContext(Dispatchers.IO) {
                    messageDao.getMessagesForUser(user.userId)
                }
                lastMsgs[user.userId] = msgs.firstOrNull()
            }

            // Update UI
            adapter.updateData(users, lastMsgs)
            binding.emptyView.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
