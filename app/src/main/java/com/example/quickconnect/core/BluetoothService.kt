package com.example.quickconnect.core

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private const val TAG_SERVICE = "BT_Service"

/**
 * One instance per process that handles **all** Bluetooth I/O.
 * All potentially‑restricted calls are wrapped with explicit permission checks so
 * Android‑Studio lint shows _zero_ "Call requires permission" warnings.
 */
class BluetoothService(private val context: Context, private val callback: Callback) {

    interface Callback {
        fun onConnected(device: BluetoothDevice)
        fun onConnectionFailed()
        fun onMessageRead(message: String)
        fun onMessageWritten(message: String)
    }

    /* ---------------- permission helpers ---------------- */
    private fun hasPerm(p: String) =
        ActivityCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPerm() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPerm(Manifest.permission.BLUETOOTH_CONNECT)
    private fun hasScanPerm()    = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPerm(Manifest.permission.BLUETOOTH_SCAN)

    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    /* ---------------- public API ---------------- */
    fun startServer() {
        if (!hasConnectPerm()) {
            Log.w(TAG_SERVICE, "Missing BLUETOOTH_CONNECT – cannot open server socket")
            return
        }
        connectThread?.cancel()
        if (acceptThread == null) {
            acceptThread = AcceptThread().also { it.start() }
        }
    }

    fun connect(device: BluetoothDevice) {
        if (!hasConnectPerm()) {
            Log.w(TAG_SERVICE, "Missing BLUETOOTH_CONNECT – cannot connect to remote")
            callback.onConnectionFailed(); return
        }
        acceptThread?.cancel(); acceptThread = null
        connectThread?.cancel()
        connectThread = ConnectThread(device).also { it.start() }
    }

    fun write(bytes: ByteArray) {
        connectedThread?.write(bytes)
    }

    fun stop() {
        acceptThread?.cancel(); acceptThread = null
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
    }

    /* ---------------- internal threads ---------------- */
    private inner class AcceptThread : Thread() {
        private var serverSocket: BluetoothServerSocket? = null
        init {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(BluetoothConstants.APP_NAME, BluetoothConstants.APP_UUID)
            } catch (e: SecurityException) {
                Log.e(TAG_SERVICE, "listenUsingRfcomm failed – permission?", e)
            }
        }
        override fun run() {
            serverSocket ?: return
            while (true) {
                try {
                    val socket = serverSocket!!.accept()
                    manageConnection(socket)
                    break
                } catch (e: IOException) {
                    Log.e(TAG_SERVICE, "Accept failed", e); break
                }
            }
        }
        fun cancel() { try { serverSocket?.close() } catch (_: IOException) {} }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private var socket: BluetoothSocket? = null
        init {
            try {
                socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.APP_UUID)
            } catch (e: SecurityException) {
                Log.e(TAG_SERVICE, "createRfcommSocket requires BLUETOOTH_CONNECT", e)
            }
        }
        override fun run() {
            if (socket == null) { callback.onConnectionFailed(); return }
            if (hasScanPerm() && adapter.isDiscovering) adapter.cancelDiscovery()
            try {
                socket!!.connect()
                manageConnection(socket!!)
            } catch (e: IOException) {
                Log.e(TAG_SERVICE, "Connection failed", e)
                callback.onConnectionFailed()
                try { socket!!.close() } catch (_: IOException) {}
            }
        }
        fun cancel() { try { socket?.close() } catch (_: IOException) {} }
    }

    private fun manageConnection(socket: BluetoothSocket) {
        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket).also { it.start() }
        callback.onConnected(socket.remoteDevice)
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inStream: InputStream = socket.inputStream
        private val outStream: OutputStream = socket.outputStream
        private val handler = Handler(Looper.getMainLooper())

        override fun run() {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytes = inStream.read(buffer)
                    val msg = String(buffer, 0, bytes)
                    handler.post { callback.onMessageRead(msg) }
                } catch (e: IOException) {
                    Log.e(TAG_SERVICE, "Disconnected", e)
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                outStream.write(bytes)
                handler.post { callback.onMessageWritten(String(bytes)) }
            } catch (e: IOException) {
                Log.e(TAG_SERVICE, "Write error", e)
            }
        }
        fun cancel() { try { socket.close() } catch (_: IOException) {} }
    }
}