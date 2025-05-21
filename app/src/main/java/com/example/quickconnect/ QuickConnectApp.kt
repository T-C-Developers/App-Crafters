
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class QuickConnectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BluetoothService.init(this)
        BluetoothService.startServer()
        subscribeToBroadcasts()
    }

    private fun subscribeToBroadcasts() {
        CoroutineScope(Dispatchers.IO).launch {
            BluetoothService.incoming
                .filterIsInstance<Packet.BroadcastPacket>()
                .collect { pkt ->
                    val dao = AppDatabase.getInstance(applicationContext).broadcastMessageDAO()
                    val now = System.currentTimeMillis()

                    // only keep those within the last hour
                    if (pkt.timestamp + 60*60_000 > now) {
                        // 1) decode the image, if present
                        val fileUri: String? = pkt.imageBase64?.let { b64 ->
                            // write into a temp file in cache
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                            val f = File(applicationContext.cacheDir,
                                "broadcast_${pkt.timestamp}.jpg")
                            f.outputStream().use { it.write(bytes) }
                            Uri.fromFile(f).toString()
                        }

                        // 2) insert into DB so your UI will pick it up
                        val newId = dao.insert(
                            BroadcastMessage(
                                senderName = pkt.senderName,
                                content = pkt.content,
                                fileUri = fileUri,
                                timestamp = pkt.timestamp
                            )
                        )

                        // 3) schedule the cleanup (same as you do for local posts)
                        val delay = pkt.timestamp + 60*60_000 - now
                        OneTimeWorkRequestBuilder<CleanupBroadcastWorker>()
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                            .setInputData(workDataOf("id" to newId))
                            .build()
                            .also { WorkManager.getInstance(applicationContext).enqueue(it) }
                    }
                }

        }
    }
}
