package com.example.quickconnect

import android.annotation.SuppressLint
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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.withContext

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

                        val delay = pkt.timestamp + 180*60_000 - now        // 3h
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
                    val fileUri: String? = when {
                        // Handle image
                        pkt.imageBase64 != null -> {
                            val bytes = android.util.Base64.decode(pkt.imageBase64, android.util.Base64.NO_WRAP)
                            val file = File(applicationContext.filesDir, "image_${pkt.timestamp}.jpg")
                            file.outputStream().use { it.write(bytes) }
                            Uri.fromFile(file).toString()
                        }

                        // Handle other files
                        pkt.fileBase64 != null && pkt.fileName != null -> {
                            val rawExt = pkt.fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                            val safeExt = rawExt?.lowercase()?.takeIf {
                                it.matches(Regex("^[a-z0-9]{1,10}$"))
                            } ?: "pdf"

                            val bytes = android.util.Base64.decode(pkt.fileBase64, android.util.Base64.NO_WRAP)
                            val file = File(applicationContext.filesDir, "file_${pkt.timestamp}.$safeExt")
                            file.outputStream().use { it.write(bytes) }
                            Uri.fromFile(file).toString()
                        }

                        else -> null
                    }
                    dao.insert(
                        DirectMessage(
                            senderId   = BluetoothService.macAddress,
                            receiverId = BluetoothService.localDisplayName,
                            timestamp  = pkt.timestamp,
                            content    = pkt.content,
                            fileUri    = fileUri,
                            fileName   = pkt.fileName,
                            isRead     = false,

                        )
                    )
                    var senderName = AppDatabase.getInstance(applicationContext).userDAO().getUserById(BluetoothService.macAddress)?.displayName
                    if(senderName.isNullOrBlank()) {senderName =""}

                    val profileData = AppDatabase.getInstance(applicationContext).profileDataDAO().getProfileData()
                    var soundsOn = profileData?.soundNotification
                    var vibrationOn = profileData?.vibrationNotification

                    if(soundsOn == null) {
                        soundsOn = false
                    }
                    if(vibrationOn == null) {
                        vibrationOn = false
                    }

                    withContext(Dispatchers.Main) {
                        pkt.content?.let { sendNewMessageNotification(applicationContext, pkt.content,senderName,soundsOn,vibrationOn) }
                    }
                }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendNewMessageNotification(context: Context, messageContent: String, senderName:String,soundsOn:Boolean,vibrationOn:Boolean) {
        val channelId = "messages_channel"
        val notificationId = 1

        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        var vibrationPattern = longArrayOf(0, 300, 200, 300)

        // Create Notification Channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Messages"
            val descriptionText = "Notifications for new chat messages"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                if (soundsOn) {
                    setSound(
                        defaultSoundUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    setSound(null, null)
                }
                enableVibration(vibrationOn)
                if (vibrationOn) {
                    vibrationPattern = vibrationPattern
                }
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Build notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.icon_chats) // Replace with your own icon
            .setContentTitle(senderName)
            .setContentText(messageContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (soundsOn) {
            builder.setSound(defaultSoundUri)
        }
        else{
            builder.setSilent(true)
        }

        if (vibrationOn) {
            builder.setVibrate(vibrationPattern)
        }

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        notificationManagerCompat.notify(notificationId, builder.build())
    }
}
