
package com.example.quickconnect

import android.app.Application
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
                    val dao = AppDatabase.getInstance(applicationContext)
                        .broadcastMessageDAO()
                    val now = System.currentTimeMillis()
                    if (pkt.timestamp + 60*60*1000 > now) {
                        val id = dao.insert(BroadcastMessage(
                            content = pkt.content,
                            fileUri = pkt.fileUri,
                            timestamp = pkt.timestamp
                        ))
                        val delay = (pkt.timestamp + 60*60*1000) - now
                        val work = OneTimeWorkRequestBuilder<CleanupBroadcastWorker>()
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                            .setInputData(workDataOf("id" to id))
                            .build()
                        WorkManager.getInstance(applicationContext).enqueue(work)
                    }
                }
        }
    }
}
