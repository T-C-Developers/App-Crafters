package com.example.quickconnect.ui.chats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.R

data class ChatItem(val name: String, val message: String, val time: String)

class ChatAdapter : ListAdapter<ChatItem, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    interface OnChatClick {
        fun onChat(item: ChatItem)
    }

    private var listener: OnChatClick? = null

    fun setOnChatClick(l: (ChatItem) -> Unit) {
        listener = object : OnChatClick {
            override fun onChat(item: ChatItem) = l(item)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.message.text = item.message
        holder.time.text = item.time
        // [TODO] - use placeholder image
//        holder.image.setImageResource(R.drawable.img_profile)
        holder.itemView.setOnClickListener { listener?.onChat(item) }
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.chatName)
        val message: TextView = itemView.findViewById(R.id.chatMessage)
        val time: TextView = itemView.findViewById(R.id.chatTime)
        val image: ImageView = itemView.findViewById(R.id.chatImage)
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem.name == newItem.name // Or use unique ID if available
        }

        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem == newItem
        }
    }
}
