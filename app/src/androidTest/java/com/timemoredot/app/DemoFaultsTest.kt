package com.timemoredot.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** ui-design 10.3 배너 문구, 5.1 재연결 문구, 5.3 연결 실패 문구. 각 시나리오는 영점 뒤 자유 추출로 본다. */
class DemoFaultsTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Before fun setUp() = rule.startFresh()
    @After fun tearDown() = resetDemoState()

    @Test fun overloadBannerBetween15And20sOfPour() {
        rule.connectDemo(OVERLOAD)
        rule.tare()
        val t0 = System.nanoTime()   // 첫 0 g 직후, 붓기는 그 3 s 뒤 시작 → 0 g 낭독 지연 d(0~2 s) 때문에 배너는 t0 + 18~23 s − d
        rule.awaitText("과부하: 저울이 잴 수 있는 무게를 넘었습니다", 30_000, substring = true)
        val sec = (System.nanoTime() - t0) / 1e9
        assertTrue("배너 첫 관측 $sec s", sec in 15.0..24.0)
    }

    @Test fun noDataThenReconnectKeepsRunning() {
        rule.connectDemo(RECONNECT)
        rule.tare()
        rule.awaitText("저울에서 무게가 오지 않습니다", 40_000, substring = true)
        rule.awaitText("연결이 끊겨 다시 연결하는 중입니다", 60_000, substring = true)
        rule.awaitText("연결됨", 30_000)
        assertTrue("추출 중 유지", rule.isRunning())
        assertFalse("S6으로 가지 않음", rule.seenText("추출 요약"))
    }

    @Test fun bluetoothOffThenBack() {
        rule.connectDemo(BT_OFF)
        rule.awaitText("블루투스가 꺼", 25_000, substring = true)
        rule.awaitText("연결됨", 15_000)
    }

    @Test fun connectFailShowsGattErrorText() {
        rule.openFinderAndPick(FAIL)
        rule.awaitText("저울에 연결하지 못했습니다. 저울을 켜고 가까이 둔 뒤 다시 시도하세요.", 15_000)
    }
}
