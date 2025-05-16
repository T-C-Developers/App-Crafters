package com.example.quickconnect.ui.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.Packet.IntroPacket
import com.example.quickconnect.core.UserPrefs
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.User
import com.example.quickconnect.databinding.ActivityBluetoothDiscoveryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BluetoothDiscoveryActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BluetoothDiscAct"
        private const val REQ_PERMS  = 1001
        private const val REQ_ENABLE = 1002
    }

    private lateinit var binding: ActivityBluetoothDiscoveryBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val newDevices    = mutableListOf<BluetoothDevice>()

    private lateinit var pairedAdapter: DeviceAdapter
    private lateinit var newAdapter: DeviceAdapter

    private val db      by lazy { AppDatabase.getInstance(this) }
    private val userDao by lazy { db.userDAO() }

    // persistent per‐device
    private val myUserId   by lazy { UserPrefs.getUserId(this) }
    private val myUserName by lazy { UserPrefs.getUserName(this) }

    // Discovery callback
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            Log.d(TAG, "discoveryReceiver.onReceive: action=${intent.action}")
            when (intent.action) {

                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    Log.d(TAG, "  ACTION_FOUND: ${device?.name}/${device?.address}")
                    device?.takeIf {
                        val n = it.name
                        it.bondState != BluetoothDevice.BOND_BONDED &&
                                !newDevices.contains(it) &&
                                !n.isNullOrBlank() &&
                                n != "Unknown Device"
                    }?.also {
                        newDevices.add(it)
                        newAdapter.notifyItemInserted(newDevices.size - 1)
                        Log.d(TAG, "  Added new device: ${it.name}")
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    Log.d(TAG, "  ACTION_DISCOVERY_FINISHED")
                }
            }
        }
    }

    // Bonding callback
    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            Log.d(TAG, "bondReceiver.onReceive: action=${intent.action}")
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                Log.d(TAG, "  BOND_STATE_CHANGED for ${device?.name}: state=${device?.bondState}")
                device?.let {
                    when (it.bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            Log.d(TAG, "  BOND_BONDED → connecting now")
                            unregisterReceiver(this)
                            connectNow(it)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            Log.d(TAG, "  BOND_NONE → pairing failed")
                            unregisterReceiver(this)
                            binding.progressBar.visibility = android.view.View.GONE
                            Toast.makeText(
                                this@BluetoothDiscoveryActivity,
                                "Pairing failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        else -> { /* in progress */ }
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        Log.d(TAG, "onCreate")
        binding = ActivityBluetoothDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.bluetoothToolbar)
        supportActionBar?.apply {
            title = "Bluetooth"
            setDisplayHomeAsUpEnabled(true)

        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        Log.d(TAG, "  adapter enabled=${bluetoothAdapter.isEnabled}")
        setupAdapters()

        if (hasPermissions()) {
            Log.d(TAG, "  permissions already granted")
            ensureBluetoothOn()
        } else {
            Log.d(TAG, "  requesting permissions")
            requestPermissionsCompat()
        }
    }

    private fun setupAdapters() {
        Log.d(TAG, "setupAdapters")
        pairedAdapter = DeviceAdapter(
            devices       = pairedDevices,
            isPaired      = true,
            onDeviceClick = ::connectToDevice,
            onUnpairClick = ::unpairDevice,
            isConnected   = { dev -> BluetoothService.isConnected(dev.address).also {
                Log.d(TAG, "  isConnected(${dev.address}) → $it")
            } }
        ).also {
            binding.pairedList.layoutManager = LinearLayoutManager(this)
            binding.pairedList.adapter = it
        }

        newAdapter = DeviceAdapter(
            devices       = newDevices,
            isPaired      = false,
            onDeviceClick = ::connectToDevice
        ).also {
            binding.newList.layoutManager = LinearLayoutManager(this)
            binding.newList.adapter = it
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun ensureBluetoothOn() {
        Log.d(TAG, "ensureBluetoothOn")
        if (!bluetoothAdapter.isEnabled) {
            Log.d(TAG, "  requesting ACTION_REQUEST_ENABLE")
            startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                REQ_ENABLE
            )
        } else {
            Log.d(TAG, "  already enabled → initDiscovery")
            initDiscovery()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        Log.d(TAG, "onActivityResult req=$req res=$res")
        if (req == REQ_ENABLE && res == RESULT_OK) {
            Log.d(TAG, "  Bluetooth enabled → initDiscovery")
            initDiscovery()
        }
    }


    @SuppressLint("MissingPermission")
    private fun initDiscovery() {
        Log.d(TAG, "initDiscovery")

        // make discoverable
        if (bluetoothAdapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Log.d(TAG, "  requesting discoverable")
            startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
            )
        }

        // load paired list
        Log.d(TAG, "  loading paired devices")
        pairedDevices.clear()
        bluetoothAdapter.bondedDevices?.also { pairedDevices.addAll(it) }
        pairedAdapter.notifyDataSetChanged()
        Log.d(TAG, "  paired: ${pairedDevices.map { it.name }}")

        // start scanning
        Log.d(TAG, "  registering discoveryReceiver and startDiscovery")
        registerReceiver(discoveryReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(discoveryReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        startDiscovery()

        // listen for IntroPackets
        lifecycleScope.launch {
            Log.d(TAG, "  subscribing to IntroPacket stream")
            BluetoothService.incoming
                .filterIsInstance<IntroPacket>()
                .collect { pkt ->
                    Log.d(TAG, "  got IntroPacket from ${pkt.displayName}")
                    val u = User(
                        userId      = pkt.userId,
                        displayName = pkt.displayName,
                        deviceName  = pkt.displayName,
                        isOnline    = true,
                        lastSeen    = System.currentTimeMillis().toString()
                    )
                    withContext(Dispatchers.IO) {
                        userDao.insertUser(u)
                        Log.d(TAG, "    inserted user ${u.displayName}")
                    }
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        Log.d(TAG, "startDiscovery()")
        binding.progressBar.visibility = android.view.View.VISIBLE
        newDevices.clear()
        newAdapter.notifyDataSetChanged()
        bluetoothAdapter.cancelDiscovery()
        bluetoothAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "connectToDevice: ${device.name}/${device.address}, bonded=${device.bondState}")
        binding.progressBar.visibility = android.view.View.VISIBLE
        bluetoothAdapter.cancelDiscovery()
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "  not bonded → creating bond")
            registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            device.createBond()
        } else {
            Log.d(TAG, "  already bonded → connectNow")
            connectNow(device)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectNow(device: BluetoothDevice) {
        Log.d(TAG, "connectNow → ${device.name}/${device.address}")
        // insert immediately so your Chats screen will pick it up
        val placeholder = User(
            userId      = device.address,
            displayName = device.name ?: device.address,
            deviceName  = device.name ?: device.address,
            isOnline    = true,
            lastSeen    = System.currentTimeMillis().toString()
        )
        lifecycleScope.launch(Dispatchers.IO) {
            userDao.insertUser(placeholder)
        }
        // now do the RFCOMM connect
        BluetoothService.connectTo(device, myUserId, myUserName)
    }


    @SuppressLint("MissingPermission")
    private fun unpairDevice(device: BluetoothDevice) {
        Log.d(TAG, "unpairDevice ${device.name}/${device.address}")
        try {
            device.javaClass.getMethod("removeBond").invoke(device)
            pairedDevices.remove(device)
            pairedAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Unpaired", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "  unpaired successfully")
        } catch (e: Exception) {
            Toast.makeText(this, "Unpair failed", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "  unpair failed", e)
        }
    }

    private fun hasPermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ).all { perm ->
                ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestPermissionsCompat() {
        Log.d(TAG, "requestPermissionsCompat()")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                REQ_PERMS
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQ_PERMS
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult requestCode=$requestCode results=${grantResults.toList()}")
        if (requestCode == REQ_PERMS &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            Log.d(TAG, "  permissions granted → ensureBluetoothOn()")
            ensureBluetoothOn()
        } else {
            Log.d(TAG, "  permissions denied")
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onOptionsItemSelected(item: MenuItem) =
        if (item.itemId == android.R.id.home) {
            Log.d(TAG, "home/up pressed → finish()")
            finish()
            true
        } else super.onOptionsItemSelected(item)

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy → unregister receivers & shutdown")
        runCatching { unregisterReceiver(discoveryReceiver) }
        runCatching { unregisterReceiver(bondReceiver) }
        BluetoothService.shutdown()
    }
}
