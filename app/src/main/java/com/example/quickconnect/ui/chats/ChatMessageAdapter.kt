package com.example.quickconnect.ui.chats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.R
import com.example.quickconnect.data.DirectMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatMessageAdapter(
    private val peerId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_RECEIVED = 0
        private const val TYPE_SENT = 1
    }

    private val messages = mutableListOf<DirectMessage>()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun updateData(newMessages: List<DirectMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == peerId) TYPE_RECEIVED else TYPE_SENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val timeText = timeFormat.format(Date(msg.timestamp))

        when (holder) {
            is SentViewHolder -> holder.bind(msg, timeText)
            is ReceivedViewHolder -> holder.bind(msg, timeText)
        }
    }

    override fun getItemCount(): Int = messages.size

    private inner class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMsg: TextView = view.findViewById(R.id.tvMsg)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val ivTicks: ImageView = view.findViewById(R.id.ivTicks)

        fun bind(msg: DirectMessage, timeText: String) {
            tvMsg.text = msg.content
            tvTime.text = timeText
            // TODO: change tick icon based on read-status
            ivTicks.setImageResource(
                if (msg.isRead) R.drawable.icon_2_ticks else R.drawable.icon_1_tick
            )
        }
    }

    private inner class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMsg: TextView = view.findViewById(R.id.tvMsg)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)

        fun bind(msg: DirectMessage, timeText: String) {
            tvMsg.text = msg.content
            tvTime.text = timeText
        }
    }
}
