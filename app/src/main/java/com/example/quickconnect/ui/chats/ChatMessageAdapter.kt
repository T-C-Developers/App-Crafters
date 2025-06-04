package com.example.quickconnect.ui.chats

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.quickconnect.R
import com.example.quickconnect.data.DirectMessage
import java.io.File
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
        val layoutId = if (viewType == TYPE_SENT)
            R.layout.item_message_sent else R.layout.item_message_received

        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val timeText = timeFormat.format(Date(msg.timestamp))
        (holder as MessageViewHolder).bind(msg, timeText)
    }

    override fun getItemCount(): Int = messages.size

    private inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMsg: TextView = view.findViewById(R.id.tvMsg)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val ivTicks: ImageView? = view.findViewById(R.id.ivTicks) // only for sent messages
        private val ivImage: ImageView = view.findViewById(R.id.ivImage)
        private val tvFile: TextView? = view.findViewById(R.id.tvFile) // added to support file messages

        fun bind(msg: DirectMessage, timeText: String) {
            if (msg.content?.isEmpty() == false) {
                tvMsg.visibility = View.VISIBLE
                tvMsg.text = msg.content
            } else {
                tvMsg.visibility = View.GONE
            }

            tvTime.text = timeText

            // Set ticks only for sent messages
            ivTicks?.setImageResource(
                if (msg.isRead) R.drawable.icon_2_ticks else R.drawable.icon_1_tick
            )

            // Reset views
            ivImage.visibility = View.GONE
            tvFile?.visibility = View.GONE

            msg.fileUri?.let { uriStr ->
                val uri = Uri.parse(uriStr)
                val ext = uriStr.substringAfterLast('.', "").lowercase(Locale.ROOT)

                when {
                    ext in listOf("jpg", "jpeg", "png") -> {
                        ivImage.visibility = View.VISIBLE
                        Glide.with(itemView.context).load(uri).into(ivImage)
                    }

                    ext in listOf("pdf", "txt") -> {
                        tvFile?.visibility = View.VISIBLE
                        tvFile?.text = "📎 ${msg.fileName ?: uri.lastPathSegment}"
                        tvFile?.setOnClickListener {
                            val context = itemView.context
                            val file = File(uri.path ?: return@setOnClickListener)
                            val fileUri = FileProvider.getUriForFile(
                                context,
                                context.packageName + ".provider",
                                file
                            )
                            val mime = when (ext) {
                                "pdf" -> "application/pdf"
                                "txt" -> "text/plain"
                                else -> "*/*"
                            }

                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, mime)
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}
