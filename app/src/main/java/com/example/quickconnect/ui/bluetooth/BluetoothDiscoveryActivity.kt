package com.example.quickconnect.ui.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quickconnect.R
import com.example.quickconnect.databinding.ActivityBluetoothDiscoveryBinding

class BluetoothDiscoveryActivity : AppCompatActivity(), DeviceAdapter.OnDeviceClickListener {

    private lateinit var binding: ActivityBluetoothDiscoveryBinding

    private val btAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val paired    = mutableListOf<BluetoothDevice>()
    private val available = mutableListOf<BluetoothDevice>()

    private lateinit var pairedAdapter: DeviceAdapter
    private lateinit var availAdapter: DeviceAdapter

    /* ---------- permission helpers ---------- */
    private val permsS = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val permsLegacy = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val runtimePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permsS else permsLegacy

    private fun has(p: String) = ActivityCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun canScan()    = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || has(Manifest.permission.BLUETOOTH_SCAN)
    private fun canConnect() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || has(Manifest.permission.BLUETOOTH_CONNECT)

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        if (map.all { it.value }) ensureLocationEnabled() else Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
    }

    /* ---------- location helper ---------- */
    private fun ensureLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                AlertDialog.Builder(this)
                    .setTitle("Location required")
                    .setMessage("Turn on location services to discover Bluetooth devices.")
                    .setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    .setNegativeButton("Cancel", null)
                    .show(); return
            }
        }
        initDiscovery()
    }

    /* ---------- receiver ---------- */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    d?.let { addDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED  -> binding.progressBar.visibility = View.VISIBLE
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.progressBar.visibility = View.GONE
                    binding.root.postDelayed({ restartDiscovery() }, 12_000)
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                    if (d.bondState == BluetoothDevice.BOND_BONDED) {
                        available.remove(d); availAdapter.notifyDataSetChanged()
                        if (!paired.contains(d)) { paired += d; pairedAdapter.notifyItemInserted(paired.lastIndex) }
                    }
                }
            }
        }
    }

    /* ---------- lifecycle ---------- */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /* Toolbar */
        setSupportActionBar(findViewById(R.id.bluetooth_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Bluetooth"

        pairedAdapter = DeviceAdapter(paired, this)
        availAdapter  = DeviceAdapter(available, this)

        binding.pairedList.layoutManager = LinearLayoutManager(this)
        binding.pairedList.adapter      = pairedAdapter
        binding.newList.layoutManager   = LinearLayoutManager(this)
        binding.newList.adapter         = availAdapter

        IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            registerReceiver(receiver, this)
        }

        if (!runtimePerms.all { has(it) }) permLauncher.launch(runtimePerms) else ensureLocationEnabled()
    }

    /* ---------- discovery helpers ---------- */
    private fun initDiscovery() {
        paired.clear(); available.clear()
        if (canConnect()) paired += btAdapter.bondedDevices
        pairedAdapter.notifyDataSetChanged(); availAdapter.notifyDataSetChanged()
        if (!btAdapter.isEnabled) startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        restartDiscovery()
    }

    private fun restartDiscovery() {
        if (!canScan()) { Toast.makeText(this, "Missing scan permission", Toast.LENGTH_SHORT).show(); return }
        if (btAdapter.isDiscovering) btAdapter.cancelDiscovery()
        val ok = btAdapter.startDiscovery();
        android.util.Log.d("BT_Discovery", "startDiscovery() returned $ok")
    }

    private fun addDevice(d: BluetoothDevice) {
        val name = d.name

        // Filter unnamed devices
        if (name.isNullOrBlank()) {
            android.util.Log.d("BT_Discovery", "Ignored unnamed device: ${d.address}")
            return
        }

        if (d.bondState == BluetoothDevice.BOND_BONDED) {
            if (!paired.contains(d)) {
                paired += d
                pairedAdapter.notifyItemInserted(paired.lastIndex)
            }
        } else if (!available.contains(d)) {
            available += d
            availAdapter.notifyItemInserted(available.lastIndex)
        }
    }

    private fun refreshPairedDevices() {
        if (!canConnect()) return
        paired.clear()
        paired += btAdapter.bondedDevices
        pairedAdapter.notifyDataSetChanged()
    }

    /* ---------- click ---------- */
    override fun onDeviceClick(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            if (!canConnect()) { Toast.makeText(this, "Need BLUETOOTH_CONNECT", Toast.LENGTH_SHORT).show(); return }
            device.createBond() // triggers system pairing dialog
        } else {
            setResult(RESULT_OK, Intent().putExtra("device", device)); finish()
        }
    }

    override fun onUnpairClick(device: BluetoothDevice) {
        AlertDialog.Builder(this)
            .setTitle("Unpair device")
            .setMessage("Are you sure you want to unpair ${device.name}?")
            .setPositiveButton("Yes") { _, _ ->
                try {
                    val method = device.javaClass.getMethod("removeBond")
                    method.invoke(device)
                    Toast.makeText(this, "Unpairing ${device.name}", Toast.LENGTH_SHORT).show()

                    // Delay refresh slightly to let system update
                    binding.root.postDelayed({ refreshPairedDevices() }, 1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to unpair", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // toolbar - back button functionality.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    /* ---------- cleanup ---------- */
    override fun onDestroy() {
        super.onDestroy(); try { unregisterReceiver(receiver) } catch (_: Exception) {}
        if (canScan()) btAdapter.cancelDiscovery()
    }
}