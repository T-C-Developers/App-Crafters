package com.example.quickconnect

import android.app.Application
import android.util.Log
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.Packet.IntroPacket
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

class QuickConnectApp : Application() {
    companion object {
        private const val TAG = "QuickConnectApp"
    }

    override fun onCreate() {
        super.onCreate()

        // 1) Initialize our local ID + name
        BluetoothService.init(this)
        Log.d(TAG, "BluetoothService initialized")

        // 2) Start listening for incoming RFCOMM
        BluetoothService.startServer()
        Log.d(TAG, "BluetoothService.startServer()")

        // 3) Persist any incoming IntroPacket into the DB
        val db     = AppDatabase.getInstance(this)
        val userDao= db.userDAO()

        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Listening for IntroPackets…")
            BluetoothService.incoming
                .filterIsInstance<IntroPacket>()
                .collect { intro ->
                    Log.d(TAG, "→ Received Intro from ${intro.displayName}")
                    userDao.insertUser(
                        User(
                            userId      = intro.userId,
                            displayName = intro.displayName,
                            deviceName  = intro.displayName,
                            isOnline    = true,
                            lastSeen    = System.currentTimeMillis().toString()
                        )
                    )
                    Log.d(TAG, "✓ User saved: ${intro.displayName}")
                }
        }
    }
}
