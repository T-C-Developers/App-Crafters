package com.example.quickconnect.ui.chats

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.R
import com.example.quickconnect.model.ChatMessage

class ChatMessageAdapter(private val items: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 0
        private const val TYPE_RECEIVED = 1
    }

    override fun getItemViewType(position: Int) =
        if (items[position].isSentByMe) TYPE_SENT else TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            SentVH(inflater.inflate(R.layout.item_message_sent, parent, false))
        } else {
            RecVH(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        if (holder is SentVH) holder.bind(msg) else (holder as RecVH).bind(msg)
    }

    fun add(msg: ChatMessage) {
        items.add(msg)
        notifyItemInserted(items.lastIndex)
    }

    /* ---------- ViewHolders ---------- */
    private class SentVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMsg: TextView = v.findViewById(R.id.tvMsg)
        private val tvTime: TextView = v.findViewById(R.id.tvTime)
        private val ivTicks: ImageView = v.findViewById(R.id.ivTicks)
        fun bind(m: ChatMessage) {
            tvMsg.text = m.text
            tvTime.text = DateFormat.format("hh:mm a", m.timestamp)
            ivTicks.setImageResource(if (m.isRead) R.drawable.icon_2_ticks else R.drawable.icon_1_tick)
        }
    }

    private class RecVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMsg: TextView = v.findViewById(R.id.tvMsg)
        private val tvTime: TextView = v.findViewById(R.id.tvTime)
        fun bind(m: ChatMessage) {
            tvMsg.text = m.text
            tvTime.text = DateFormat.format("hh:mm a", m.timestamp)
        }
    }
}