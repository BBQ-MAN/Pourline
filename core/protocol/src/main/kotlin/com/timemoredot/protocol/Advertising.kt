package com.timemoredot.protocol

import java.util.UUID

// Advertising.kt — 광고 판별 규칙과 그 상수는 이 파일에만 둔다 (protocol.md 4절 A-1~A-3 확정)

val SCALE_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")   // G-1
const val SCALE_MANUFACTURER_ID = 0xFFFF   // A-1
const val DEVICE_TYPE_DOT = 0x01           // A-1 값[1]

data class Advertisement(val name: String?, val serviceUuids: List<UUID>, val manufacturerData: Map<Int, ByteArray>)

/** (a) serviceUuids에 SCALE_SERVICE_UUID, 또는 (b) manufacturerData[0xFFFF]가 2바이트 이상이고 값[1] == DEVICE_TYPE_DOT.
 *  이름은 보지 않는다(A-2: 화이트리스트 방송은 이름 없음). 값[0](방송 유형 01/02)은 쓰지 않는다. G-7 서비스만 있는 광고는 후보가 아니다. */
fun isScaleCandidate(adv: Advertisement): Boolean =   // TODO(device): protocol.md A-1 — Android getManufacturerSpecificData(0xFFFF) 반환 형식은 B1에서 확인
    SCALE_SERVICE_UUID in adv.serviceUuids ||
        adv.manufacturerData[SCALE_MANUFACTURER_ID]?.let { it.size >= 2 && (it[1].toInt() and 0xFF) == DEVICE_TYPE_DOT } == true

/** ScanFilter (b)의 manufacturer data·mask(architecture.md 6절). isScaleCandidate (b)와 같은 길이·오프셋 가정이라 같이 고친다. 호출부는 copyOf()로 넘긴다. */
val MANUFACTURER_FILTER_DATA: ByteArray = byteArrayOf(0x00, DEVICE_TYPE_DOT.toByte())   // TODO(device): protocol.md A-1
val MANUFACTURER_FILTER_MASK: ByteArray = byteArrayOf(0x00, 0xFF.toByte())              // TODO(device): protocol.md A-1

const val EXPECTED_MODEL = "TES017"   // protocol.md A-4 앱 규칙(G-3 확정 값). 다른 파일은 리터럴로 갖지 않는다
