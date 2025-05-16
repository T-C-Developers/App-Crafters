package com.example.quickconnect.ui.bluetooth

import android.Manifest
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.recyclerview.widget.RecyclerView
import com.example.quickconnect.databinding.ItemDeviceBinding
import android.bluetooth.BluetoothDevice
import android.graphics.Color
import com.example.quickconnect.R

/**
 * @param devices         list of devices
 * @param isPaired        true if this list is the "paired" section
 * @param onDeviceClick   invoked when user taps the card
 * @param onUnpairClick   if paired, invoked when user taps Unpair
 * @param isConnected     for paired devices: returns true if that device is currently connected
 */
class DeviceAdapter(
    private val devices: List<BluetoothDevice>,
    private val isPaired: Boolean,
    private val onDeviceClick: (BluetoothDevice)->Unit,
    private val onUnpairClick: ((BluetoothDevice)->Unit)? = null,
    private val isConnected: (BluetoothDevice)->Boolean = { false }
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(val b: ItemDeviceBinding) :
        RecyclerView.ViewHolder(b.root) {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun bind(device: BluetoothDevice) {
            // name & MAC
            b.deviceName.text    = device.name ?: "Unknown Device"
            b.deviceAddress.text = device.address

            if (isPaired) {
                // show “Connected” vs “Not connected”
                b.deviceStatus.apply {
                    if (isConnected(device)){
                        text = context.getString(R.string.status_connected)
                        setTextColor(Color.GREEN)
                    } else {
                        setTextColor(Color.RED)
                        text = context.getString(R.string.status_not_connected)
                    }
                    visibility = View.VISIBLE
                }
                b.btnUnpair.apply {
                    visibility = View.VISIBLE
                    setOnClickListener { onUnpairClick?.invoke(device) }
                }
            } else {
                b.deviceStatus.visibility = View.GONE
                b.btnUnpair.visibility    = View.GONE
            }

            b.root.setOnClickListener { onDeviceClick(device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size
}
