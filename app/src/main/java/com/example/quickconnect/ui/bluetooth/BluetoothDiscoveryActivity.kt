package com.example.quickconnect.ui.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.core.BluetoothService
import com.example.quickconnect.core.IntroPacket
import com.example.quickconnect.data.AppDatabase
import com.example.quickconnect.data.User
import com.example.quickconnect.databinding.ActivityBluetoothDiscoveryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class BluetoothDiscoveryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothDiscoveryBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val newDevices    = mutableListOf<BluetoothDevice>()

    private lateinit var pairedAdapter: DeviceAdapter
    private lateinit var newAdapter: DeviceAdapter

    private val db     by lazy { AppDatabase.getInstance(this) }
    private val userDao by lazy { db.userDAO() }

    private val myUserId   = UUID.randomUUID().toString()
    private val myUserName = "Johan"

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.takeIf {
                        val n = it.name
                        it.bondState != BOND_BONDED &&
                                !newDevices.contains(it) &&
                                !n.isNullOrBlank() &&
                                n != "Unknown Device"
                    }?.also {
                        newDevices.add(it)
                        newAdapter.notifyItemInserted(newDevices.size - 1)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                    binding.progressBar.visibility = View.GONE
            }
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    when(it.bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            unregisterReceiver(this)
                            connectNow(it)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            unregisterReceiver(this)
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@BluetoothDiscoveryActivity,
                                "Pairing failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val REQ_PERMS   = 1001
        private const val REQ_ENABLE  = 1002
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        binding = ActivityBluetoothDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.bluetoothToolbar)
        supportActionBar?.apply {
            title = "Bluetooth"
            setDisplayHomeAsUpEnabled(true)
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        setupAdapters()

        if (hasPermissions()) {
            ensureBluetoothOn()
        } else {
            requestPermissionsCompat()
        }
    }

    private fun setupAdapters() {
        pairedAdapter = DeviceAdapter(
            devices       = pairedDevices,
            isPaired      = true,
            onDeviceClick = ::connectToDevice,
            onUnpairClick = ::unpairDevice,
            isConnected   = { device ->
                BluetoothService.isConnected(device.address)
            }
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
        if (!bluetoothAdapter.isEnabled) {
            startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                REQ_ENABLE
            )
        } else {
            initDiscovery()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_ENABLE && res == RESULT_OK) {
            initDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initDiscovery() {
        // make discoverable
        if (bluetoothAdapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .apply { putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) }
            )
        }

        // show paired
        pairedDevices.clear()
        bluetoothAdapter.bondedDevices?.let { pairedDevices.addAll(it) }
        pairedAdapter.notifyDataSetChanged()

        // start scan & receiver
        registerReceiver(discoveryReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(discoveryReceiver,
            IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        startDiscovery()

        // handle incoming IntroPackets
        lifecycleScope.launch {
            BluetoothService.incoming
                .filterIsInstance<IntroPacket>()
                .collect { pkt ->
                    withContext(Dispatchers.IO) {
                        userDao.insertUser(User(
                            userId      = pkt.userId,
                            displayName = pkt.displayName,
                            deviceName  = pkt.displayName,
                            isOnline    = true,
                            lastSeen    = System.currentTimeMillis().toString()
                        ))
                    }
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        binding.progressBar.visibility = View.VISIBLE
        newDevices.clear()
        newAdapter.notifyDataSetChanged()
        bluetoothAdapter.cancelDiscovery()
        bluetoothAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        binding.progressBar.visibility = View.VISIBLE
        bluetoothAdapter.cancelDiscovery()
        if (device.bondState != BOND_BONDED) {
            registerReceiver(bondReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            device.createBond()
        } else {
            connectNow(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectNow(device: BluetoothDevice) {
        BluetoothService.connectTo(device, myUserId, myUserName)
    }

    @SuppressLint("MissingPermission")
    private fun unpairDevice(device: BluetoothDevice) {
        try {
            device.javaClass.getMethod("removeBond").invoke(device)
            pairedDevices.remove(device)
            pairedAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Unpaired", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Unpair failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ).all { perm ->
                ContextCompat.checkSelfPermission(this, perm) ==
                        PackageManager.PERMISSION_GRANTED
            }
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsCompat() {
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
        if (requestCode == REQ_PERMS &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            ensureBluetoothOn()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when(item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(discoveryReceiver)
            unregisterReceiver(bondReceiver)
        } catch (_: Exception) { }
        BluetoothService.shutdown()
    }
}
