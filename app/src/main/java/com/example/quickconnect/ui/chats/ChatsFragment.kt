package com.example.quickconnect.ui.chats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.BluetoothHelper
import com.example.quickconnect.R

class ChatsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var connectButton: ImageButton
    private val chatAdapter = ChatAdapter(getSampleChats())
    private lateinit var bluetoothHelper: BluetoothHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_chats, container, false)

        bluetoothHelper = BluetoothHelper(requireContext())
        recyclerView = view.findViewById(R.id.chatList)
        connectButton = view.findViewById(R.id.btnConnect)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = chatAdapter

        connectButton.setOnClickListener {
            bluetoothHelper.makeDiscoverable(requireActivity())
            bluetoothHelper.startDiscovery()
            Toast.makeText(requireContext(), "Searching for nearby devices...", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun getSampleChats(): List<ChatItem> {
//        [TODO] - Get data from DB
        return listOf(
            ChatItem("Cameron Williamson", "Sure! That sounds good.", "11:45 AM"),
            ChatItem("Jenny Wilson", "Are you coming today?", "10:30 AM"),
            ChatItem("Kristin Watson", "See you soon!", "Yesterday"),
            ChatItem("Esther Howard", "I'll send the report", "Yesterday"),
            ChatItem("Jenny Wilson", "Are you coming today?", "10:30 AM"),
            ChatItem("Kristin Watson", "See you soon!", "Yesterday"),
            ChatItem("Esther Howard", "I'll send the report", "Yesterday"),
            ChatItem("Jenny Wilson", "Are you coming today?", "10:30 AM"),
            ChatItem("Kristin Watson", "See you soon!", "Yesterday"),
            ChatItem("Esther Howard", "I'll send the report", "Yesterday"),
            ChatItem("Wade Warren", "Please call me when...", "Sunday")
        )
    }
}
