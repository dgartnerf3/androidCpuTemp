package com.example.cpuTemp

import java.util.*

object BleUuids {
    // TODO: replace with your Raspberry Pi GATT service/characteristic UUIDs
    val SERVICE_UUID: UUID = UUID.fromString("00000001-710e-4a5b-8d75-3e5b444bc3cf")
    val TEMP_CHAR_UUID: UUID = UUID.fromString("00000002-710e-4a5b-8d75-3e5b444bc3cf")
    val UNIT_CHAR_UUID: UUID = UUID.fromString("00000003-710e-4a5b-8d75-3e5b444bc3cf")

    // Optional: filter by advertised service to find your Pi quicker
    val SCAN_FILTER_SERVICE: UUID = SERVICE_UUID
}
