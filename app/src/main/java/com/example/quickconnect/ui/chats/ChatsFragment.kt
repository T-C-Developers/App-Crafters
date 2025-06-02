package com.example.quickconnect.ui.chats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.databinding.FragmentChatsBinding
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.DirectMessage
import com.example.quickconnect.data.User
import com.example.quickconnect.ui.bluetooth.BluetoothDiscoveryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsFragment : Fragment() {
    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ChatAdapter
    private val db          by lazy { AppDatabase.getInstance(requireContext()) }
    private val userDao     by lazy { db.userDAO() }
    private val msgDao      by lazy { db.directMessageDAO() }
    private val profileDao  by lazy { db.profileDataDAO() }

    // Full data lists for filtering
    private var allUsers: List<User> = emptyList()
    private var allLastMessages: Map<String, DirectMessage?> = emptyMap()

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

        // Greeting
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) {
                profileDao.getProfileData()
            }
            binding.welcomeText.text = "Hello, ${profile?.displayName.orEmpty()}"
        }

        // RecyclerView + adapter
        adapter = ChatAdapter(emptyList(), emptyMap()) { user ->
            Intent(requireContext(), ChatScreenActivity::class.java).also { intent ->
                intent.putExtra("EXTRA_PEER_ID", user.userId)
                intent.putExtra("EXTRA_DISPLAY_NAME", user.displayName)
                startActivity(intent)
            }
        }
        binding.chatList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatList.adapter = adapter

        // Connect button
        binding.btnConnect.setOnClickListener {
            startActivity(Intent(requireContext(), BluetoothDiscoveryActivity::class.java))
        }

        // Search setup
        binding.etSearch.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty().trim()
            filterChats(query)
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
        }
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        loadChats()
    }

    private fun loadChats() {
        lifecycleScope.launch {
            // 1) fetch actual users
            allUsers = withContext(Dispatchers.IO) {
                userDao.getAllUsers()
            }

            // 2) build last‐message map
            val lastMsgs = mutableMapOf<String, DirectMessage?>()
            allUsers.forEach { u ->
                val msgs = withContext(Dispatchers.IO) {
                    msgDao.getMessagesForUser(u.userId) // suspend fun returning List<DirectMessage>
                }
                lastMsgs[u.userId] = msgs.firstOrNull()
            }

            // Cache full data
            allLastMessages = lastMsgs

            // Initial display (apply any active search)
            filterChats(binding.etSearch.text.toString().trim())
        }
    }

    private fun filterChats(query: String) {
        val filtered = if (query.isEmpty()) allUsers
        else allUsers.filter { it.displayName.contains(query, ignoreCase = true) }

        val filteredMessages = allLastMessages.filterKeys { userId ->
            filtered.any { it.userId == userId }
        }

        adapter.updateData(filtered, filteredMessages)
        // Show empty view if no matches
        binding.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
