package com.example.aiplayground

import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var permsLauncher: ActivityResultLauncher<Array<String>>

    private val devices = mutableListOf<DeviceItem>()
    private val indexByAddr = HashMap<String, Int>()
    private lateinit var adapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Register BEFORE the Activity is started
        permsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { res ->
            val granted = res.values.all { it }
            findViewById<TextView>(R.id.logView)
                .append("\nPermissions: ${if (granted) "granted" else "denied"}")
            if (granted) startScan()
        }

        val rv = findViewById<RecyclerView>(R.id.rvDevices)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(devices) { item ->
            appendLog("Connecting to ${item.address}…")
            bleManager.connect(item.device)
        }
        rv.adapter = adapter

        bleManager = BleManager(this, targetName = "Thermometer").apply {
            onLog = { appendLog(it) }
            onConnected = { appendLog("Connected") }
            onDisconnected = { appendLog("Disconnected") }
            onStringReceived = { s -> appendLog("RX: $s") }
            onDeviceFound = { r -> handleScanResult(r) }
        }

        //Hook up UI
        findViewById<Button>(R.id.btnScan).setOnClickListener { ensurePermissionsAndScan() }
        findViewById<Button>(R.id.btnWriteUnit).setOnClickListener {
            val s = findViewById<EditText>(R.id.unitText).text.toString()
            if (s.isNotEmpty()) bleManager.writeUnits(s)
        }
        findViewById<Button>(R.id.btnReadUnit).setOnClickListener {
            bleManager.readUnits()
        }
        findViewById<Button>(R.id.btnReadTemperature).setOnClickListener {
            bleManager.readTemperature()
        }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener { bleManager.disconnect() }
    }

    private fun handleScanResult(r: ScanResult) {
        val name = r.scanRecord?.deviceName ?: r.device.name ?: ""
        val addr = r.device.address ?: return
        runOnUiThread {
            val idx = indexByAddr[addr]
            if (idx == null) {
                val item = DeviceItem(r.device, name, addr, r.rssi)
                devices.add(item)
                indexByAddr[addr] = devices.lastIndex
                adapter.notifyItemInserted(devices.lastIndex)
            } else {
                val item = devices[idx]
                item.name = name
                item.rssi = r.rssi
                adapter.notifyItemChanged(idx)
            }
        }
    }

    private fun ensurePermissionsAndScan() {
        if (BlePermissions.hasAll(this)) {
            // clear old results for a fresh list
            devices.clear(); indexByAddr.clear(); adapter.notifyDataSetChanged()
            bleManager.startScan()
        } else {
            permsLauncher.launch(BlePermissions.requiredPermissions())
        }
    }

    private fun startScan() = bleManager.startScan()

    private fun appendLog(msg: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.logView).append("\n$msg")
        }
    }
}
