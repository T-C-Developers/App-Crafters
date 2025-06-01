package com.example.quickconnect.ui.blogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.quickconnect.databinding.ItemBroadcastBinding
import com.example.quickconnect.data.BroadcastMessage
import java.text.DateFormat
import java.util.*
import androidx.core.net.toUri

class BroadcastsAdapter(
    private var items: List<BroadcastMessage>
) : RecyclerView.Adapter<BroadcastsAdapter.ViewHolder>() {

    fun update(newItems: List<BroadcastMessage>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBroadcastBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class ViewHolder(private val b: ItemBroadcastBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: BroadcastMessage) {
            b.tvContent.text = msg.content ?: ""
            b.tvSender.text = msg.senderName
            b.tvContent.text = msg.content.orEmpty()

            if (!msg.fileUri.isNullOrEmpty()) {
                b.ivMedia.visibility = View.VISIBLE
                Glide.with(b.ivMedia.context)
                    .load(msg.fileUri.toUri())
                    .into(b.ivMedia)
            } else {
                b.ivMedia.visibility = View.GONE
            }
            val df = DateFormat.getDateTimeInstance()
            b.tvTimestamp.text = df.format(Date(msg.timestamp))
        }
    }
}