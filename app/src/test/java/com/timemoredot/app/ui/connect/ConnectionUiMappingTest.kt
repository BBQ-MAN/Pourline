package com.timemoredot.app.ui.connect

import com.timemoredot.app.PermissionStatus
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FailureReason
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScanCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/** architecture.md 9.2 매핑 표의 행마다 한 케이스. */
class ConnectionUiMappingTest {
    private val a = ScanCandidate("AA:BB:CC:DD:EE:01", "TIMEMORE_Dot", -50, 0)
    private val device = ScaleDevice("AA:BB:CC:DD:EE:01", "TIMEMORE_Dot", "TES017", "1.0.4", 247, true)
    @Test fun `같은 이름 2개는 주소 꼬리가 붙고 중복 없으면 그대로`() {
        val b = ScanCandidate("AA:BB:CC:DD:EE:02", "TIMEMORE_Dot", -60, 0)
        val c = ScanCandidate("AA:BB:CC:DD:EE:03", "Kitchen", -60, 0)
        assertEquals("TIMEMORE_Dot · EE:01", listName(a, listOf(a, b, c)))
        assertEquals("TIMEMORE_Dot · EE:02", listName(b, listOf(a, b, c)))
        assertEquals("Kitchen", listName(c, listOf(a, b, c)))
        assertEquals("TIMEMORE_Dot", listName(a, listOf(a, c)))
        assertEquals("TIMEMORE_Dot, 주소 끝 EE 01", spokenListName(a, listOf(a, b)))
    }

    private val seen = mutableSetOf<KClass<out ConnectionUi>>()

    private fun map(
        state: ConnectionState, scan: List<ScanCandidate> = emptyList(),
        perm: PermissionStatus = PermissionStatus.Granted, saved: Boolean = false,
    ): ConnectionUi = connectionUi(state, scan, perm, saved).also { seen += it::class }

    @Test fun `PermissionMissing - PermanentlyDenied`() =
        assertEquals(ConnectionUi.PermissionPermanentlyDenied, map(ConnectionState.PermissionMissing, perm = PermissionStatus.PermanentlyDenied))
    @Test fun `PermissionMissing - 그 밖`() {
        assertEquals(ConnectionUi.PermissionMissing, map(ConnectionState.PermissionMissing, perm = PermissionStatus.Denied))
        assertEquals(ConnectionUi.PermissionMissing, map(ConnectionState.PermissionMissing, perm = PermissionStatus.Granted))
    }
    @Test fun BluetoothOff() = assertEquals(ConnectionUi.BluetoothOff, map(ConnectionState.BluetoothOff))
    @Test fun LocationOff() = assertEquals(ConnectionUi.LocationOff, map(ConnectionState.LocationOff))
    @Test fun `Idle - 스캔 결과 있음`() =
        assertEquals(ConnectionUi.Scanning(listOf(a), stopped = true), map(ConnectionState.Idle, scan = listOf(a), saved = true))
    @Test fun `Idle - 결과 없음 저장 기기 있음`() = assertEquals(ConnectionUi.Disconnected, map(ConnectionState.Idle, saved = true))
    @Test fun `Idle - 결과 없음 저장 기기 없음`() = assertEquals(ConnectionUi.NoDevice, map(ConnectionState.Idle))
    @Test fun Scanning() = assertEquals(ConnectionUi.Scanning(listOf(a), stopped = false), map(ConnectionState.Scanning, scan = listOf(a)))
    @Test fun `Failed NoDeviceFound`() = assertEquals(ConnectionUi.ScanEmpty, map(ConnectionState.Failed(FailureReason.NoDeviceFound)))
    @Test fun `Failed ScanFailed`() = assertEquals(ConnectionUi.ScanFailed, map(ConnectionState.Failed(FailureReason.ScanFailed)))
    @Test fun `Failed ServiceNotFound`() =
        assertEquals(ConnectionUi.Failed(ConnectFailure.ServiceNotFound), map(ConnectionState.Failed(FailureReason.ServiceNotFound)))
    @Test fun `Failed ConnectTimeout`() =
        assertEquals(ConnectionUi.Failed(ConnectFailure.Timeout), map(ConnectionState.Failed(FailureReason.ConnectTimeout)))
    @Test fun `Failed GattError`() =
        assertEquals(ConnectionUi.Failed(ConnectFailure.ConnectFailed), map(ConnectionState.Failed(FailureReason.GattError(133))))

    /** F-F5-1: 첫 연결 133 + 화이트리스트 방송이면 페어링 모드 안내(S1 배너·S2·S3가 같은 값). */
    @Test fun `Failed GattError whitelist shows pairing mode hint`() {
        assertEquals(
            ConnectionUi.Failed(ConnectFailure.PairingModeMaybe),
            map(ConnectionState.Failed(FailureReason.GattError(133, whitelistAdvertising = true))),
        )
        assertTrue(failureText(ConnectFailure.PairingModeMaybe).contains("버튼을 길게 눌러 페어링 모드로"))
        assertTrue(failureText(ConnectFailure.PairingModeMaybe).contains("처음 연결한다면"))   // 133은 다른 이유일 수 있어 단정하지 않는다
        assertEquals(
            "연결 실패, 연결하지 못함, 페어링 모드가 필요할 수 있음",
            connectAnnouncement(ConnectionUi.Connecting("A", null), ConnectionUi.Failed(ConnectFailure.PairingModeMaybe)),
        )
    }
    @Test fun `Failed ReconnectExhausted`() =
        assertEquals(ConnectionUi.Failed(ConnectFailure.ConnectFailed), map(ConnectionState.Failed(FailureReason.ReconnectExhausted)))
    @Test fun Connecting() =
        assertEquals(ConnectionUi.Connecting(a.address, "x"), map(ConnectionState.Connecting(a.address, "x")))
    @Test fun Connected() = assertEquals(ConnectionUi.Connected(device), map(ConnectionState.Connected(device)))
    @Test fun Reconnecting() =
        assertEquals(ConnectionUi.Reconnecting(3, 99L), map(ConnectionState.Reconnecting(a.address, 3, 99L)))

    /** 13종이 모두 한 번 이상 나온다. 클래스 단위로 한 번에 돌려 확인한다(테스트 순서에 기대지 않는다). */
    @Test fun `ConnectionUi 13종이 모두 나온다`() {
        listOf(
            ConnectionState.PermissionMissing to PermissionStatus.PermanentlyDenied, ConnectionState.PermissionMissing to PermissionStatus.Denied,
            ConnectionState.BluetoothOff to PermissionStatus.Granted, ConnectionState.LocationOff to PermissionStatus.Granted,
            ConnectionState.Idle to PermissionStatus.Granted, ConnectionState.Scanning to PermissionStatus.Granted,
            ConnectionState.Failed(FailureReason.NoDeviceFound) to PermissionStatus.Granted,
            ConnectionState.Failed(FailureReason.ScanFailed) to PermissionStatus.Granted,
            ConnectionState.Failed(FailureReason.ConnectTimeout) to PermissionStatus.Granted,
            ConnectionState.Connecting(a.address, null) to PermissionStatus.Granted,
            ConnectionState.Connected(device) to PermissionStatus.Granted,
            ConnectionState.Reconnecting(a.address, 1, 0) to PermissionStatus.Granted,
        ).forEach { (s, p) -> map(s, perm = p) }
        map(ConnectionState.Idle, saved = true)
        assertEquals(
            setOf(
                ConnectionUi.NoDevice::class, ConnectionUi.Disconnected::class, ConnectionUi.BluetoothOff::class,
                ConnectionUi.PermissionMissing::class, ConnectionUi.PermissionPermanentlyDenied::class, ConnectionUi.LocationOff::class,
                ConnectionUi.Scanning::class, ConnectionUi.ScanEmpty::class, ConnectionUi.ScanFailed::class,
                ConnectionUi.Connecting::class, ConnectionUi.Connected::class, ConnectionUi.Reconnecting::class, ConnectionUi.Failed::class,
            ),
            seen,
        )
    }
}
