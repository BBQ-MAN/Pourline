package com.timemoredot.ble

import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FailureReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 5.4 표시 이름 규칙, 6절 스캔. */
@OptIn(ExperimentalCoroutinesApi::class)
class BleScanTest {
    private val whitelist = byteArrayOf(0x01, 0x01)   // A-2: 전원 재투입 뒤 화이트리스트 방송, 이름 없음

    @Test fun namelessWhitelistAdvertIsCandidate() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_A, name = null, mfr = whitelist)
        val c = h.client.scanResults.value.single()
        assertEquals(ADDR_A, c.address)
        assertNull(c.name)
        assertTrue(h.logs.contains("EV ADV $ADDR_A name=null mfr=0101 uuids="))
    }

    @Test fun namelessAdvertOfSavedAddressUsesLastName() = runTest {
        val h = Harness(this)
        h.store.lastAddress = ADDR_A
        h.store.lastName = "My Dot"
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_A, name = null, mfr = whitelist)
        h.scanner.hit(ADDR_B, name = null, mfr = whitelist)
        assertEquals(listOf("My Dot", null), h.client.scanResults.value.map { it.name })
    }

    @Test fun seenNameIsKeptForLaterNamelessResult() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_A, name = "TIMEMORE-DOT", mfr = byteArrayOf(0x02, 0x01))
        h.scanner.hit(ADDR_A, name = null, mfr = whitelist, rssi = -40)
        val c = h.client.scanResults.value.single()
        assertEquals("TIMEMORE-DOT", c.name)
        assertEquals(-40, c.rssi)
    }

    @Test fun nonCandidateIsIgnored() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_A, name = "Other", mfr = byteArrayOf(0x01, 0x02), uuids = listOf(G7_SERVICE))
        assertTrue(h.client.scanResults.value.isEmpty())
    }

    @Test fun connectedDeviceNameIsSaved() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_A, name = "TIMEMORE-DOT")
        h.client.connect(ADDR_A)
        runCurrent()
        assertEquals("TIMEMORE-DOT", (h.state as ConnectionState.Connected).device.name)
        assertEquals(ADDR_A, h.store.lastAddress)
        assertEquals("TIMEMORE-DOT", h.store.lastName)
        assertEquals(ADDR_A, h.client.lastDeviceAddress.value)
        assertTrue(!h.scanner.running)
    }

    @Test fun secondStartWithin6sIsDelayed() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.advance(2_000)
        h.client.startScan()
        runCurrent()
        h.advance(3_999)
        assertEquals(listOf(0L), h.scanner.startTimesMs)
        assertEquals(ConnectionState.Scanning, h.state)
        h.advance(1)
        assertEquals(listOf(0L, 6_000L), h.scanner.startTimesMs)
    }

    @Test fun noCandidateIn10sIsNoDeviceFound() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.advance(9_999)
        assertEquals(ConnectionState.Scanning, h.state)
        h.advance(1)
        assertEquals(ConnectionState.Failed(FailureReason.NoDeviceFound), h.state)
        assertTrue(!h.scanner.running)
    }

    @Test fun scanEndsIdleWithCandidates() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_A, null)
        h.advance(10_000)
        assertEquals(ConnectionState.Idle, h.state)
        assertEquals(1, h.client.scanResults.value.size)
    }

    @Test fun scanFailure() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.scanner.fail(2)
        assertEquals(ConnectionState.Failed(FailureReason.ScanFailed), h.state)
    }

    @Test fun locationOffBlocksScan() = runTest {
        val h = Harness(this)
        h.env.locationOff = true
        h.client.startScan()
        assertEquals(ConnectionState.LocationOff, h.state)
    }

    @Test fun scanResultsKeptUntilConnectedAndClearedOnNextScan() = runTest {
        val h = Harness(this)
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_A, null)
        h.scanner.hit(ADDR_B, null)
        h.onCreate = {}
        h.client.connect(ADDR_A)
        runCurrent()
        assertTrue(h.state is ConnectionState.Connecting)
        assertEquals(2, h.client.scanResults.value.size)
        h.last.drop(133)
        runCurrent()
        assertEquals(ConnectionState.Failed(FailureReason.GattError(133)), h.state)
        assertEquals(2, h.client.scanResults.value.size)
        h.onCreate = { it.connected() }
        h.client.connect(ADDR_A)
        runCurrent()
        assertTrue(h.state is ConnectionState.Connected)
        assertTrue(h.client.scanResults.value.isEmpty())

        h.client.disconnect()
        h.advance(7_000)
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_B, null)
        h.client.stopScan()
        assertEquals(1, h.client.scanResults.value.size)
        h.advance(7_000)
        h.client.startScan()
        assertTrue(h.client.scanResults.value.isEmpty())
    }
}
