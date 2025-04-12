package com.example.quickconnect.ui.chats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.R

data class ChatItem(val name: String, val message: String, val time: String)

class ChatAdapter(private val chatList: List<ChatItem>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    interface OnChatClick {
        fun onChat(item: ChatItem)
    }

    private var listener: OnChatClick? = null

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.chatName)
        val message: TextView = itemView.findViewById(R.id.chatMessage)
        val time: TextView = itemView.findViewById(R.id.chatTime)
        val image: ImageView = itemView.findViewById(R.id.chatImage)
    }

    fun setOnChatClick(l: (ChatItem) -> Unit) { listener = object : OnChatClick { override fun onChat(item: ChatItem) = l(item) } }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = chatList[position]
        holder.name.text = item.name
        holder.message.text = item.message
        holder.time.text = item.time
        // [TODO] - use placeholder image
        holder.image.setImageResource(R.drawable.img_profile)
        holder.itemView.setOnClickListener { listener?.onChat(item) }
    }

    override fun getItemCount(): Int = chatList.size
}
