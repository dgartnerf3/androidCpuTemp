package com.example.aiplayground

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import java.nio.charset.Charset
import java.util.*

class BleManager(
    private val context: Context,
    private val targetName: String? = null // optional: filter by device name
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var unitChar: BluetoothGattCharacteristic? = null
    private var tempChar: BluetoothGattCharacteristic? = null
    private var scanning = false

    var onLog: (String) -> Unit = { Log.d("BleManager", it) }
    var onConnected: () -> Unit = {}
    var onDisconnected: () -> Unit = {}
    var onStringReceived: (String) -> Unit = {}

    var onDeviceFound: (ScanResult) -> Unit = {}

    private fun scanFilters(): List<ScanFilter> {
        val builder = ScanFilter.Builder()
        // Filter by service UUID to reduce noise
        // builder.setServiceUuid(ParcelUuid(BleUuids.SCAN_FILTER_SERVICE))
        if (!targetName.isNullOrBlank()) {
            // Note: Android framework lacks direct name filter; we’ll check in callback
        }
        return listOf(builder.build())
    }

    private fun scanSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning) return
        if (adapter?.isEnabled != true) {
            onLog("Bluetooth is off")
            return
        }
        scanning = true
        onLog("Starting BLE scan…")
        scanner?.startScan(scanFilters(), scanSettings(), scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        scanner?.stopScan(scanCallback)
        onLog("Scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (!targetName.isNullOrBlank()
                && !name.orEmpty().contains(targetName!!, ignoreCase = true)
            ) return
            onDeviceFound(result)  // REPORT device
        }
        override fun onScanFailed(errorCode: Int) { onLog("Scan failed: $errorCode") }
    }

    // Call this when user taps a device row
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        onLog("Connecting to ${device.address}…")
        gatt = if (Build.VERSION.SDK_INT >= 23)
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        else
            device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLog("GATT error: $status")
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onLog("Connected, discovering services…")
                onConnected()
                // Request larger MTU for string payloads
                g.requestMtu(247)
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onLog("Disconnected")
                onDisconnected()
                cleanup()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            onLog("MTU changed: $mtu (status=$status)")
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLog("Service discovery failed: $status")
                return
            }
            val service = g.getService(BleUuids.SERVICE_UUID)
            if (service == null) {
                onLog("Service not found: ${BleUuids.SERVICE_UUID}")
                return
            }
            tempChar = service.getCharacteristic(BleUuids.TEMP_CHAR_UUID)
            unitChar = service.getCharacteristic(BleUuids.UNIT_CHAR_UUID)

            if (tempChar == null) {
                onLog("temperature characteristic not found: ${BleUuids.TEMP_CHAR_UUID}")
            } else {
                enableNotifications(g, tempChar!!)
            }
            if (unitChar == null) {
                onLog("Unit characteristic not found: ${BleUuids.UNIT_CHAR_UUID}")
            } else {
                enableNotifications(g, unitChar!!)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleUuids.TEMP_CHAR_UUID) {
                val str = characteristic.value?.toString(Charset.forName("UTF-8")) ?: ""
                onStringReceived(str)
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            onLog("Write ${if (status == BluetoothGatt.GATT_SUCCESS) "OK" else "failed($status)"}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        val ok = g.setCharacteristicNotification(ch, true)
        onLog("Enable notifications: $ok")
        val cccd = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (cccd != null) {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(cccd)
        }
    }

    @SuppressLint("MissingPermission")
    fun writeUnits(text: String): Boolean {
        val g = gatt ?: return false.also { onLog("Not connected") }
        val c = unitChar ?: return false.also { onLog("Unit characteristic missing") }
        if (text !in listOf("c", "C", "f", "F")) {
            onLog("Invalid unit: $text (must be one of c, C, f, F)")
            return false
        }
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // request response; use NO_RESPONSE if desired
        c.value = text.toByteArray(Charsets.UTF_8)
        return g.writeCharacteristic(c)
    }

    @SuppressLint("MissingPermission")
    fun readUnits(): Boolean {
        val g = gatt ?: return false.also { onLog("Not connected") }
        val c = unitChar ?: return false.also { onLog("Unit characteristic missing") }
        return g.readCharacteristic(c)
    }

    @SuppressLint("MissingPermission")
    fun readTemperature(): Boolean {
        val g = gatt ?: return false.also { onLog("Not connected") }
        val c = tempChar ?: return false.also { onLog("Temperature characteristic missing") }
        return g.readCharacteristic(c)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun cleanup() {
        gatt?.close()
        gatt = null
        unitChar = null
        tempChar = null
    }
}
