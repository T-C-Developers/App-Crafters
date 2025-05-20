package com.example.quickconnect.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.quickconnect.data.AppDatabase
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import kotlinx.serialization.Serializable as KSerializable
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.*

private typealias SrvSocket = BluetoothServerSocket
private typealias RfSocket  = BluetoothSocket

@KSerializable
sealed class Packet {
    @KSerializable @SerialName("intro")
    data class IntroPacket( val displayName: String) : Packet()

    @KSerializable @SerialName("msg")
    data class MessagePacket(
        val senderId:   String,
        val receiverId: String,
        val timestamp:  Long,
        val content:    String
    ) : Packet()
}

object BluetoothService {
    private const val TAG    = "BluetoothService"
    private val SPP_UUID    = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val adapter     = BluetoothAdapter.getDefaultAdapter()
    private lateinit var appContext: Context
    lateinit var macAddress :String   //most recent mac adderss
    lateinit var appDatabase: AppDatabase

    /** Stable identity, set in init() */
    @Volatile var localDisplayName: String = ""

    /** Must be called once in Application.onCreate() */
    fun init(context: Context) {
        appContext        = context.applicationContext
        appDatabase = AppDatabase.getInstance(context)
        saveOrGetPersonaData()
        Log.d(TAG, "init: userId= MyPhone displayName=$localDisplayName")
    }

    private val json = Json {
        ignoreUnknownKeys  = true
        classDiscriminator = "type"
        serializersModule  = SerializersModule {
            polymorphic(Packet::class) {
                subclass(Packet.IntroPacket::class,   serializer<Packet.IntroPacket>())
                subclass(Packet.MessagePacket::class, serializer<Packet.MessagePacket>())
            }
        }
    }

    private val _incoming = MutableSharedFlow<Packet>(extraBufferCapacity = 50)
    val incoming: SharedFlow<Packet> = _incoming

    private val ioScope    = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sockets    = mutableMapOf<String, RfSocket>()
    private val writers    = mutableMapOf<String, BufferedWriter>()
    private val userWriters= mutableMapOf<String, BufferedWriter>()

    private var serverSocket: SrvSocket? = null
    private var serverJob: Job?          = null

    /** Start RFCOMM server (call once at app startup). */
    @SuppressLint("MissingPermission")
    fun startServer() {
        if (serverJob != null) return
        serverJob = ioScope.launch {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                    "QuickConnectServer", SPP_UUID
                )
                Log.d(TAG, "Server socket listening…")
                while (isActive) {
                    val sock = serverSocket?.accept() ?: break
                    Log.d(TAG, "Accepted connection from ${sock.remoteDevice.address}")
                    handleSocket(sock)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
            }
        }
    }

    /** Tear down server + all connections. */
    fun shutdown() {
        ioScope.launch {
            serverJob?.cancelAndJoin()
            runCatching { serverSocket?.close() }
            writers.values.forEach    { runCatching { it.close() } }
            sockets.values.forEach    { runCatching { it.close() } }
            writers.clear(); sockets.clear(); userWriters.clear()
            ioScope.coroutineContext.cancelChildren()
            Log.d(TAG, "shutdown complete")
        }
    }

    /** Shared logic for both incoming and outgoing sockets. */
    private fun handleSocket(sock: RfSocket) {
        ioScope.launch {
            val addr = sock.remoteDevice.address
            sockets[addr] = sock
            val w = BufferedWriter(OutputStreamWriter(sock.outputStream))
            writers[addr] = w

            // 1) Send our IntroPacket immediately
            val introJson = json.encodeToString(
                kotlinx.serialization.PolymorphicSerializer(Packet::class),
                Packet.IntroPacket( localDisplayName)
            )
            runCatching {
                w.write(introJson); w.newLine(); w.flush()
                Log.d(TAG, "→ Intro sent to $addr")
            }.onFailure {
                Log.e(TAG, "Intro send failed to $addr", it)
                cleanupWriter(w)
                return@launch
            }

            // 2) Start reading incoming lines
            val reader = BufferedReader(InputStreamReader(sock.inputStream))
            try {
                reader.useLines { lines ->
                    lines.forEach { line ->
                        try {
                            val pkt = json.decodeFromString<Packet>(line)
                            if (pkt is Packet.IntroPacket) {
                                userWriters[addr] = w
                                macAddress = addr
                                Log.d(TAG, "← registered writer for ${pkt.displayName}")
                            }
                            _incoming.emit(pkt)
                        } catch (ser: SerializationException) {
                            Log.e(TAG, "Malformed packet from $addr: $line", ser)
                        }
                    }
                }
            } catch (io: IOException) {
                Log.d(TAG, "Socket closed, stopping read loop for $addr", io)
                cleanupWriter(w)
            }
        }
    }

    /** Outbound RFCOMM client to a peer. */
    @SuppressLint("MissingPermission")
    fun connectTo(device: BluetoothDevice, displayName: String) {
        localDisplayName = displayName


        ioScope.launch {
            try {
                adapter.cancelDiscovery()
                val sock = device
                    .createRfcommSocketToServiceRecord(SPP_UUID)
                    .also { it.connect() }

                Log.d(TAG, "Outgoing connect OK to ${device.address}")
                handleSocket(sock)
            } catch (e: Exception) {
                Log.e(TAG, "connectTo(${device.address}) failed", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectFromChat(macAddress: String) {
        val device = adapter.getRemoteDevice(macAddress)
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "Device already bonded → connect directly")
            connectTo(device, localDisplayName)
        }
        else {
            Log.d(TAG, "Device not bonded → initiating bond")

            val bondReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: android.content.Intent?) {
                    val action = intent?.action
                    if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                        val bondedDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (bondedDevice?.address == macAddress) {
                            when (bondedDevice.bondState) {
                                BluetoothDevice.BOND_BONDED -> {
                                    Log.d(TAG, "Bond successful → connecting")
                                    runCatching { appContext.unregisterReceiver(this) }
                                    connectTo(bondedDevice, localDisplayName)

                                }
                                BluetoothDevice.BOND_NONE -> {
                                    Log.e(TAG, "Bond failed")
                                    runCatching { appContext.unregisterReceiver(this) }
                                }
                            }
                        }
                    }
                }
            }

            // Register receiver and start bonding
            val filter = android.content.IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            appContext.registerReceiver(bondReceiver, filter)
            device.createBond()
        }
    }

//    @SuppressLint("MissingPermission")
//    fun connectFromChat(macAddress:String) {
//        val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
//
//
//        ioScope.launch {
//            try {
//                adapter.cancelDiscovery()
//                val sock = device
//                    .createRfcommSocketToServiceRecord(SPP_UUID)
//                    .also { it.connect() }
//
//                Log.d(TAG, "Outgoing connect OK to ${device.address}")
//                handleSocket(sock)
//            } catch (e: Exception) {
//                Log.e(TAG, "connectTo(${device.address}) failed", e)
//            }
//        }
//    }

    /** Broadcast an Intro or unicast a MessagePacket. */
    fun sendPacket(packet: Packet) {
        ioScope.launch {
            when (packet) {
                is Packet.IntroPacket -> {
                    val txt = json.encodeToString(
                        kotlinx.serialization.PolymorphicSerializer(Packet::class), packet
                    )
                    writers.values.forEach { w ->
                        runCatching {
                            w.write(txt); w.newLine(); w.flush()
                        }.onFailure {
                            Log.e(TAG, "send intro failed", it)
                            cleanupWriter(w)
                        }
                    }
                }
                is Packet.MessagePacket -> {
                    userWriters[packet.receiverId]?.let { w ->
                        val txt = json.encodeToString(
                            kotlinx.serialization.PolymorphicSerializer(Packet::class), packet
                        )
                        runCatching {
                            w.write(txt); w.newLine(); w.flush()
                        }.onFailure {
                            Log.e(TAG, "send msg failed", it)
                            cleanupWriter(w)
                        }
                    } ?: Log.w(TAG, "no connection for user ${packet.receiverId}")
                }
            }
        }
    }

    private fun cleanupWriter(writer: BufferedWriter) {
//        try {
//            writer.close()
//        } catch (_: IOException) {}
//        // Remove from all maps (safe cleanup)
//        writers.entries.removeIf { it.value == writer }
//        userWriters.entries.removeIf { it.value == writer }
//        sockets.entries.removeIf { it.value.outputStream == writer }
    }

    private fun saveOrGetPersonaData(){

        ioScope.launch {
            val profile = appDatabase.profileDataDAO().getProfileData()
            if (profile != null) {
                localDisplayName = profile.displayName
            }
        }
    }

    /** Check whether we still have an open socket to that MAC address. */
    fun isConnected(address: String): Boolean =
        sockets[address]?.isConnected == true
}

