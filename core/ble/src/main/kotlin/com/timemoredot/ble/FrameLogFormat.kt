package com.timemoredot.ble

import java.util.UUID

internal const val LOG_TAG = "TimemoreBle"

/** 디버그 프레임 로그 형식(device-verification 로그 형식, crc_probe.py가 읽는다). */
internal object FrameLogFormat {
    fun rx(value: ByteArray): String = "RX FFF1 " + value.toHexString(HexFormat.UpperCase)   // 재조립 전 알림 원본
    fun tx(value: ByteArray): String = "TX FFF2 " + value.toHexString(HexFormat.UpperCase)
    fun ev(text: String): String = "EV $text"

    /** A-1 Android 반환 확인용(architecture.md 4.1). */
    fun adv(address: String, name: String?, manufacturerData: ByteArray?, uuids: List<UUID>): String =
        ev("ADV $address name=$name mfr=${manufacturerData?.toHexString(HexFormat.UpperCase)} uuids=${uuids.joinToString(",")}")
}
