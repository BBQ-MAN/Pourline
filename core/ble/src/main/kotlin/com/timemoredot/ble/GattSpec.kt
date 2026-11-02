package com.timemoredot.ble

import java.util.UUID

/** GATT 특성 UUID(protocol.md 1절). 서비스 UUID는 :core:protocol의 SCALE_SERVICE_UUID, 모델 값은 EXPECTED_MODEL을 쓴다. */
internal object GattSpec {
    val UPLINK: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")       // G-1 Notify, 저울 → 앱
    val DOWNLINK: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")     // G-1 Write without response, 앱 → 저울
    val DIS_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")  // G-2
    val MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb") // G-2
    val FIRMWARE_REVISION: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb") // G-2
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")         // G-1b
    val CCCD_ENABLE_NOTIFY: ByteArray get() = byteArrayOf(0x01, 0x00)   // TODO(device): protocol.md G-1b (Android) — B1에서 확인

    const val REQUEST_MTU = 247
    const val DEFAULT_MTU = 23
    const val ATT_HEADER = 3
}
