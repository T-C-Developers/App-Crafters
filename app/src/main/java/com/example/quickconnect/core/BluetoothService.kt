package com.example.quickconnect.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.*

@Serializable
sealed class Packet

@Serializable
@SerialName("intro")
data class IntroPacket(
    val userId: String,
    val displayName: String
) : Packet()

@Serializable
@SerialName("msg")
data class MessagePacket(
    val senderId: String,
    val receiverId: String,
    val timestamp: Long,
    val content: String
) : Packet()

object BluetoothService {
    private const val TAG = "BluetoothService"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    @Volatile lateinit var localUserId: String
    @Volatile lateinit var localDisplayName: String

    // JSON + polymorphic support
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        serializersModule = SerializersModule {
            polymorphic(Packet::class) {
                subclass(IntroPacket::class, serializer<IntroPacket>())
                subclass(MessagePacket::class, serializer<MessagePacket>())
            }
        }
    }

    private val _incoming = MutableSharedFlow<Packet>(extraBufferCapacity = 50)
    val incoming: SharedFlow<Packet> = _incoming

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // for each device-address: socket & writer
    private val sockets = mutableMapOf<String, BluetoothSocket>()
    private val writers = mutableMapOf<String, BufferedWriter>()
    // once we see intro from peer, map peer-userId→writer
    private val userWriters = mutableMapOf<String, BufferedWriter>()

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        adapter?.apply {
            if (isDiscovering) cancelDiscovery()
            startDiscovery()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopDiscovery() {
        adapter?.cancelDiscovery()
    }

    /**
     * Kick off a new RFComm connection to `device`.  You can call this
     * multiple times (one per device).  Each socket gets its own reader loop.
     */
    @SuppressLint("MissingPermission")
    fun connectTo(
        device: BluetoothDevice,
        localUserId: String,
        localDisplayName: String
    ) {
        this.localUserId = localUserId
        this.localDisplayName = localDisplayName

        ioScope.launch {
            try {
                adapter?.cancelDiscovery()

                // 1) open socket
                val sock = device
                    .createRfcommSocketToServiceRecord(SPP_UUID)
                    .also { it.connect() }

                sockets[device.address] = sock

                // 2) set up writer
                val w = BufferedWriter(OutputStreamWriter(sock.outputStream))
                writers[device.address] = w

                // 3) send our intro
                val introJson = json.encodeToString(
                    PolymorphicSerializer(Packet::class),
                    IntroPacket(localUserId, localDisplayName)
                )
                w.write(introJson)
                w.write("\n")
                w.flush()

                // 4) start reader loop for this socket
                val reader = BufferedReader(InputStreamReader(sock.inputStream))
                ioScope.launch {
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            try {
                                val pkt = json.decodeFromString<Packet>(line)
                                // if it's their intro, register writer under their userId
                                if (pkt is IntroPacket) {
                                    userWriters[pkt.userId] = w
                                }
                                _incoming.emit(pkt)
                            } catch (e: Exception) {
                                Log.e(TAG, "malformed packet: $line", e)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection to ${device.address} failed", e)
            }
        }
    }

    /**
     * Send any packet.  For MessagePacket, we look up the correct peer-writer by receiverId.
     * For IntroPacket, we broadcast it to all open writers.
     */
    fun sendPacket(packet: Packet) {
        ioScope.launch {
            when (packet) {
                is IntroPacket -> {
                    val text = json.encodeToString(
                        PolymorphicSerializer(Packet::class),
                        packet
                    )
                    writers.values.forEach { w ->
                        try {
                            w.write(text); w.write("\n"); w.flush()
                        } catch (e: Exception) {
                            Log.e(TAG, "send intro failed", e)
                        }
                    }
                }

                is MessagePacket -> {
                    val w = userWriters[packet.receiverId]
                    if (w != null) {
                        try {
                            val text = json.encodeToString(
                                PolymorphicSerializer(Packet::class),
                                packet
                            )
                            w.write(text); w.write("\n"); w.flush()
                        } catch (e: Exception) {
                            Log.e(TAG, "send msg failed", e)
                        }
                    } else {
                        Log.w(TAG, "no connection for user ${packet.receiverId}")
                    }
                }

                else -> { /* handle other Packet subtypes */ }
            }
        }
    }

    fun isConnected(address: String): Boolean {
        return sockets[address]?.isConnected == true
    }

    // add to BluetoothService:
    fun sendGroupMessage(content: String) {
        val pkt = MessagePacket(
            senderId   = localUserId,
            receiverId = "",                // empty or “group” sentinel
            timestamp  = System.currentTimeMillis(),
            content    = content
        )
        ioScope.launch {
            val text = json.encodeToString(PolymorphicSerializer(Packet::class), pkt)
            writers.values.forEach { w ->
                w.write(text); w.write("\n"); w.flush()
            }
        }
    }


    /**
     * Tear down *all* live connections.
     */
    fun shutdown() {
        ioScope.launch {
            writers.values.forEach { runCatching { it.close() } }
            sockets.values.forEach { runCatching { it.close() } }
            writers.clear()
            sockets.clear()
            userWriters.clear()
            ioScope.coroutineContext.cancelChildren()
        }
    }
}
