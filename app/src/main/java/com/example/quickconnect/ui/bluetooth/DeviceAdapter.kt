package com.example.quickconnect.ui.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.R
import com.example.quickconnect.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val deviceList: List<BluetoothDevice>,
    private val listener: OnDeviceClickListener,
    private val isConnected: (BluetoothDevice) -> Boolean
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    interface OnDeviceClickListener {
        fun onDeviceClick(device: BluetoothDevice)
        fun onUnpairClick(device: BluetoothDevice)
    }

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BluetoothDevice) {
            val ctx = binding.root.context

            val hasConnectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true // < Android 12 does not need BLUETOOTH_CONNECT

            val name = if (hasConnectPerm) device.name ?: "Unnamed" else "Need permission"
            val address = if (hasConnectPerm) device.address else "–"
            val bonded = if (device.bondState == BluetoothDevice.BOND_BONDED) " (paired)" else ""

            binding.deviceName.text = buildString {
                append(name)
                append(bonded)
            }
            binding.deviceAddress.text = address
            binding.btnUnpair.visibility = if (device.bondState == BluetoothDevice.BOND_BONDED) View.VISIBLE else View.GONE

            if (device.bondState == BluetoothDevice.BOND_NONE) {
                // if not paired connect them.
                binding.root.setOnClickListener { listener.onDeviceClick(device) }
            } else {
                // if paired, there should be unpair functionality.
//                binding.root.setOnClickListener(null) // disable click for paired
                binding.btnUnpair.setOnClickListener { listener.onUnpairClick(device) }
                binding.deviceStatus.visibility = View.VISIBLE
                binding.deviceStatus.text = if (isConnected(device)) "Connected" else "Not connected"
                binding.deviceStatus.setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        if (isConnected(device)) R.color.green_600 else R.color.red_600
                    )
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) = holder.bind(deviceList[position])
    override fun getItemCount(): Int = deviceList.size
}