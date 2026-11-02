package com.timemoredot.ble

import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.ScaleCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 5.4 "환경 막힘과 풀림". 가짜 권한·블루투스 상태, 가상 시간. */
@OptIn(ExperimentalCoroutinesApi::class)
class BleEnvironmentTest {

    @Test fun creationStateFollowsPriority() = runTest {
        assertEquals(ConnectionState.BluetoothOff, Harness(this).apply { env.btOn = false; env.permissions = false; env.locationOff = true }.state)
        assertEquals(ConnectionState.PermissionMissing, Harness(this).apply { env.permissions = false; env.locationOff = true }.state)
        assertEquals(ConnectionState.LocationOff, Harness(this).apply { env.locationOff = true }.state)
        assertEquals(ConnectionState.Idle, Harness(this).state)
    }

    @Test fun permissionGrantedReconnectsFromAttempt1NotFirstConnectRule() = runTest {
        val h = Harness(this)
        h.env.permissions = false
        h.store.lastAddress = ADDR_A
        h.record()
        assertEquals(ConnectionState.PermissionMissing, h.states.first())
        assertTrue(h.client.connectLastDevice())
        runCurrent()
        assertEquals(ConnectionState.PermissionMissing, h.state)
        assertTrue(h.transports.isEmpty())

        h.advance(3_000)
        h.onCreate = { it.drop(133) }   // 풀린 뒤 첫 시도가 실패
        h.env.permissions = true
        h.client.refreshEnvironment()
        runCurrent()
        assertEquals(1, h.transports.size)   // 대기 없이 바로 시도
        val r1 = ConnectionState.Reconnecting(ADDR_A, 1, 3_000 * MS)
        assertTrue(r1 in h.states)
        assertEquals(ConnectionState.Reconnecting(ADDR_A, 2, 5_000 * MS), h.state)
        assertTrue(h.states.none { it is ConnectionState.Failed || it is ConnectionState.Connecting })
    }

    @Test fun noSavedAddressStaysIdleAfterUnblock() = runTest {
        val h = Harness(this)
        h.env.permissions = false
        h.client.connectLastDevice()
        h.env.permissions = true
        h.client.refreshEnvironment()
        h.advance(60_000)
        assertEquals(ConnectionState.Idle, h.state)
        assertTrue(h.transports.isEmpty())
    }

    @Test fun afterUserDisconnectPermissionRegrantStaysIdle() = runTest {
        val h = Harness(this)
        h.connectA()
        h.client.disconnect()
        h.env.permissions = false
        h.client.refreshEnvironment()
        assertEquals(ConnectionState.PermissionMissing, h.state)
        h.env.permissions = true
        h.client.refreshEnvironment()
        h.advance(60_000)
        assertEquals(ConnectionState.Idle, h.state)
        assertEquals(1, h.transports.size)
    }

    @Test fun securityExceptionWhileConnectedThenRegrantReconnects() = runTest {
        val h = Harness(this)
        h.record()
        val t = h.connectA()
        h.env.permissions = false
        t.securityOn = "write"
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        r.await()
        assertEquals(ConnectionState.PermissionMissing, h.state)
        assertEquals(1, t.releases)

        h.advance(2_000)
        h.env.permissions = true
        h.client.refreshEnvironment()
        runCurrent()
        val now = testScheduler.currentTime * MS
        assertTrue(ConnectionState.Reconnecting(ADDR_A, 1, now) in h.states)
        assertEquals(2, h.transports.size)
        assertTrue(h.state is ConnectionState.Connected)
    }

    @Test fun bluetoothOnAtStartUsesReconnectPath() = runTest {
        val h = Harness(this)
        h.env.btOn = false
        h.store.lastAddress = ADDR_A
        h.record()
        assertTrue(h.client.connectLastDevice())
        runCurrent()
        assertEquals(ConnectionState.BluetoothOff, h.state)
        assertTrue(h.transports.isEmpty())
        h.advance(1_000)
        h.onCreate = { it.drop(133) }
        h.env.setBluetooth(true)
        runCurrent()
        assertTrue(ConnectionState.Reconnecting(ADDR_A, 1, 1_000 * MS) in h.states)
        assertEquals(ConnectionState.Reconnecting(ADDR_A, 2, 3_000 * MS), h.state)
    }
}
