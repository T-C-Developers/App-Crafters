package com.example.quickconnect

import android.app.Application
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.CleanupBroadcastWorker
import com.example.quickconnect.core.Packet
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.BroadcastMessage
import com.example.quickconnect.data.DirectMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class QuickConnectApp : Application() {
    companion object {
        private const val TAG = "QuickConnectApp"
    }

    override fun onCreate() {
        super.onCreate()
        BluetoothService.init(this)
        BluetoothService.startServer()
        subscribeToBroadcasts()
        subscribeToMessages()
    }

    private fun subscribeToBroadcasts() {
        CoroutineScope(Dispatchers.IO).launch {
            BluetoothService.incoming
                .filterIsInstance<Packet.BroadcastPacket>()
                .collect { pkt ->
                    val dao = AppDatabase.getInstance(applicationContext).broadcastMessageDAO()
                    val now = System.currentTimeMillis()

                    if (pkt.timestamp + 60*60_000 > now) {
                        val fileUri: String? = pkt.imageBase64?.let { b64 ->
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                            val f = File(applicationContext.cacheDir, "broadcast_${pkt.timestamp}.jpg")
                            f.outputStream().use { it.write(bytes) }
                            Uri.fromFile(f).toString()
                        }

                        val newId = dao.insert(
                            BroadcastMessage(
                                senderName = pkt.senderName,
                                content = pkt.content,
                                fileUri = fileUri,
                                timestamp = pkt.timestamp
                            )
                        )

                        val delay = pkt.timestamp + 60*60_000 - now
                        OneTimeWorkRequestBuilder<CleanupBroadcastWorker>()
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                            .setInputData(workDataOf("id" to newId))
                            .build()
                            .also {
                                WorkManager.getInstance(applicationContext).enqueue(it)
                            }
                    }
                }
        }
    }

    /** capture all incoming 1:1 messages and insert into Room */
    private fun subscribeToMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            BluetoothService.incoming
                .filterIsInstance<Packet.MessagePacket>()
                .collect { pkt ->
                    val dao = AppDatabase.getInstance(applicationContext).directMessageDAO()
                    val NewImageUri: String? = pkt.imageBase64?.let { b64 ->
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        val f = File(applicationContext.cacheDir, "sentImage_${pkt.timestamp}.jpg")
                        f.outputStream().use { it.write(bytes) }
                        Uri.fromFile(f).toString()
                    }
                    dao.insert(
                        DirectMessage(
                            senderId   = BluetoothService.macAddress,
                            receiverId = BluetoothService.localDisplayName,
                            timestamp  = pkt.timestamp,
                            content    = pkt.content,
                            fileUri    = NewImageUri,
                            isRead     = false
                        )
                    )
                }
        }
    }
}
