package com.timemoredot.app.ui.connect

import com.timemoredot.protocol.ScanCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectAnnouncementTest {
    private fun c(n: Int) = ScanCandidate("A$n", null, -50, 0)
    private fun scanning(n: Int, stopped: Boolean = false) = ConnectionUi.Scanning((1..n).map(::c), stopped)

    @Test fun `전이마다 문구`() {
        assertEquals("저울 찾는 중", connectAnnouncement(ConnectionUi.NoDevice, scanning(0)))
        assertEquals("저울 1대 찾음", connectAnnouncement(scanning(0), scanning(1)))
        assertEquals("찾기 끝, 저울 3대", connectAnnouncement(scanning(3), scanning(3, stopped = true)))
        assertEquals("저울을 찾지 못했습니다", connectAnnouncement(scanning(0), ConnectionUi.ScanEmpty))
        assertEquals("찾기를 시작하지 못했습니다", connectAnnouncement(scanning(0), ConnectionUi.ScanFailed))
        assertEquals("연결 중", connectAnnouncement(scanning(2), ConnectionUi.Connecting("A1", null)))
        assertEquals("연결 실패, 응답 없음", connectAnnouncement(ConnectionUi.Connecting("A1", null), ConnectionUi.Failed(ConnectFailure.Timeout)))
        assertEquals("연결 실패, 저울 서비스 없음", connectAnnouncement(null, ConnectionUi.Failed(ConnectFailure.ServiceNotFound)))
        assertEquals("블루투스가 꺼져 있습니다", connectAnnouncement(scanning(0), ConnectionUi.BluetoothOff))
        assertEquals("근처 기기 권한이 필요합니다", connectAnnouncement(null, ConnectionUi.PermissionMissing))
        assertEquals("위치 권한이 필요합니다", connectAnnouncement(null, ConnectionUi.PermissionMissing, locationPermission = true))
        assertEquals("권한이 꺼져 있습니다", connectAnnouncement(ConnectionUi.PermissionMissing, ConnectionUi.PermissionPermanentlyDenied))
        assertEquals("위치 서비스를 켜야 저울을 찾을 수 있습니다", connectAnnouncement(scanning(0), ConnectionUi.LocationOff))
    }

    @Test fun `같은 종류 안의 변화는 후보 0에서 n과 멈춤뿐이다`() {
        assertNull(connectAnnouncement(scanning(1), scanning(2)))
        assertNull(connectAnnouncement(ConnectionUi.NoDevice, ConnectionUi.NoDevice))
        assertNull(connectAnnouncement(scanning(0), scanning(0)))
        assertEquals("저울 찾는 중", connectAnnouncement(scanning(2, stopped = true), scanning(0)))   // 다시 찾기
    }
}
