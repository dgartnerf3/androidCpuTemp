package com.example.aiplayground

import android.bluetooth.BluetoothDevice

data class DeviceItem(
    val device: BluetoothDevice,
    var name: String,
    var address: String,
    var rssi: Int
)
