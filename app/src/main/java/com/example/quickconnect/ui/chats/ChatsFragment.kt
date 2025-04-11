package com.example.quickconnect.ui.chats

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.R
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.ui.bluetooth.BluetoothDiscoveryActivity

class ChatsFragment : Fragment(), BluetoothService.Callback {

    private lateinit var recyclerView: RecyclerView
    private lateinit var connectButton: ImageButton
    private val chatAdapter = ChatAdapter(getSampleChats())

    private lateinit var service: BluetoothService
    private val devicePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val device: BluetoothDevice? = res.data?.getParcelableExtra("device")
        device?.let { service.connect(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_chats, container, false)

        recyclerView = view.findViewById(R.id.chatList)
        connectButton = view.findViewById(R.id.btnConnect)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = chatAdapter

        connectButton.setOnClickListener {
            val intent = Intent(requireContext(), BluetoothDiscoveryActivity::class.java)
            startActivity(intent)
        }

        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = BluetoothService(requireContext(), this)
        service.startServer()          // always listen for incoming
    }

    /*  Button "+" pressed  */
    private fun openDiscovery() {
        devicePicker.launch(Intent(requireContext(), BluetoothDiscoveryActivity::class.java))
    }

    /* -------- BluetoothService.Callback -------- */
    override fun onConnected(device: BluetoothDevice) {
        Toast.makeText(requireContext(), "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
    }
    override fun onConnectionFailed() { Toast.makeText(requireContext(), "Connection failed", Toast.LENGTH_SHORT).show() }
    override fun onMessageRead(message: String) { /* update UI */ }
    override fun onMessageWritten(message: String) { /* update UI */ }

    override fun onDestroy() { super.onDestroy(); service.stop() }



    private fun getSampleChats(): List<ChatItem> {
        // [TODO] - Get data from DB
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
