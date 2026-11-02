package com.timemoredot.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.timemoredot.app.ui.theme.TimemoreTheme
import java.io.File

/** 상태마다 라이트·다크 PNG를 projectRoot/screenshots/에 쓴다(ui-design-review 3절). 한 테스트 안에서 내용만 바꿔 가며 찍는다. */
class Shooter(private val rule: ComposeContentTestRule) {
    private class Frame(val id: Int, val dark: Boolean, val fontScale: Float, val content: @Composable () -> Unit)

    private var frame by mutableStateOf(Frame(0, false, 1f) {})
    private var n = 0
    private val dir = File(requireNotNull(System.getProperty("screenshots.dir")) { "screenshots.dir 시스템 속성 없음" }).apply { mkdirs() }

    fun start() {
        rule.mainClock.autoAdvance = false   // 진행 표시 프레임을 직접 고른다
        rule.setContent {
            val f = frame
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, f.fontScale),
                LocalAnnouncer provides Announcer { _, _ -> },
            ) { TimemoreTheme(darkTheme = f.dark) { androidx.compose.runtime.key(f.id) { f.content() } } }
        }
    }

    /**
     * `<name>_light.png`, `<name>_dark.png` 두 장. mid가 있으면 midMs 진행 → mid()(테스트 쪽 상태 바꿈) → advanceMs 진행 뒤 찍는다:
     * 전환 프레임을 효과 안 delay의 프레임 맞춤에 기대지 않고 고른다.
     */
    fun both(name: String, prepare: () -> Unit = {}, advanceMs: Long = 900, screen: Boolean = false, midMs: Long = 0, mid: (() -> Unit)? = null, content: @Composable () -> Unit) {
        one("${name}_light", dark = false, prepare = prepare, advanceMs = advanceMs, screen = screen, midMs = midMs, mid = mid, content = content)
        one("${name}_dark", dark = true, prepare = prepare, advanceMs = advanceMs, screen = screen, midMs = midMs, mid = mid, content = content)
    }

    /** 글꼴 2.0배 한 장(`<name>_light_font200.png`). */
    fun font200(name: String, advanceMs: Long = 900, prepare: () -> Unit = {}, screen: Boolean = false, midMs: Long = 0, mid: (() -> Unit)? = null, content: @Composable () -> Unit) =
        one("${name}_light_font200", dark = false, fontScale = 2f, prepare = prepare, advanceMs = advanceMs, screen = screen, midMs = midMs, mid = mid, content = content)

    /** 이름 그대로 한 장(라이트, 글꼴 배율 지정). */
    fun single(file: String, fontScale: Float = 1f, advanceMs: Long = 900, content: @Composable () -> Unit) =
        one(file, dark = false, fontScale = fontScale, advanceMs = advanceMs, content = content)

    /** screen = true면 대화상자 창까지 화면 전체를 찍는다. */
    private fun one(
        file: String, dark: Boolean, fontScale: Float = 1f, prepare: () -> Unit = {}, advanceMs: Long = 900, screen: Boolean = false,
        midMs: Long = 0, mid: (() -> Unit)? = null, content: @Composable () -> Unit,
    ) {
        frame = Frame(++n, dark, fontScale, content)
        rule.waitForIdle()
        prepare()
        rule.waitForIdle()
        if (mid != null) {
            rule.mainClock.advanceTimeBy(midMs)
            mid()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()   // 테스트 스레드의 쓰기를 다음 프레임이 보게
            rule.waitForIdle()
        }
        rule.mainClock.advanceTimeBy(advanceMs)   // 정지 장면은 모양 겹이 정착한 프레임, _frame 장면은 계기 뒤 적힌 ms
        if (screen) com.github.takahirom.roborazzi.captureScreenRoboImage(File(dir, "$file.png").path)
        else rule.onRoot().captureRoboImage(File(dir, "$file.png").path)
    }
}
