package com.timemoredot.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AdvertisingTest {
    private fun adv(name: String? = null, uuids: List<String> = emptyList(), mfr: Map<Int, String> = emptyMap()) =
        Advertisement(name, uuids.map(UUID::fromString), mfr.mapValues { it.value.hex() })

    @Test
    fun `서비스 UUID FFF0이면 후보`() {
        assertTrue(isScaleCandidate(adv(uuids = listOf("0000fff0-0000-1000-8000-00805f9b34fb"))))
    }

    @Test
    fun `manufacturerData 0xFFFF 기기 유형으로 판별`() {
        assertTrue(isScaleCandidate(adv(mfr = mapOf(0xFFFF to "0101"))))   // 화이트리스트 방송
        assertTrue(isScaleCandidate(adv(mfr = mapOf(0xFFFF to "0201"))))   // 일반 방송
        assertFalse(isScaleCandidate(adv(mfr = mapOf(0xFFFF to "0202"))))
        assertFalse(isScaleCandidate(adv(mfr = mapOf(0xFFFF to "01"))))     // 1바이트
    }

    @Test
    fun `이름만 있으면 후보 아님`() {
        assertFalse(isScaleCandidate(adv(name = "TIMEMORE_Dot")))
    }

    @Test
    fun `G-7 서비스만 있으면 후보 아님`() {
        assertFalse(isScaleCandidate(adv(uuids = listOf("5833ff01-9b8b-5191-6142-22a4536ef123"))))
    }

    @Test
    fun `ScanFilter data·mask는 isScaleCandidate (b)와 같은 판정`() {
        assertArrayEquals("0001".hex(), MANUFACTURER_FILTER_DATA)
        assertArrayEquals("00FF".hex(), MANUFACTURER_FILTER_MASK)
        val masked = { b: ByteArray -> ByteArray(MANUFACTURER_FILTER_MASK.size) { (b[it].toInt() and MANUFACTURER_FILTER_MASK[it].toInt()).toByte() } }
        for (value in listOf("0101", "0201", "0202")) {
            val filterPasses = masked(value.hex()).contentEquals(masked(MANUFACTURER_FILTER_DATA))
            assertEquals(value, isScaleCandidate(adv(mfr = mapOf(SCALE_MANUFACTURER_ID to value))), filterPasses)
        }
    }
}
