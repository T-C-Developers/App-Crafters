package com.example.quickconnect.ui.chats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.data.User
import com.example.quickconnect.data.DirectMessage
import com.example.quickconnect.databinding.ChatItemBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private var users: List<User>,
    private var lastMessages: Map<String, DirectMessage?>,
    private val onChatClick: (User) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(private val binding: ChatItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.chatName.text = user.displayName
            val msg = lastMessages[user.userId]
            if (msg != null) {
                binding.chatMessage.text = msg.content
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                binding.chatTime.text = sdf.format(Date(msg.timestamp))
            } else {
                binding.chatMessage.text = ""
                binding.chatTime.text = ""
            }
            binding.root.setOnClickListener { onChatClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ChatItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    fun updateData(newUsers: List<User>, newLastMessages: Map<String, DirectMessage?>) {
        users = newUsers
        lastMessages = newLastMessages
        notifyDataSetChanged()
    }
}