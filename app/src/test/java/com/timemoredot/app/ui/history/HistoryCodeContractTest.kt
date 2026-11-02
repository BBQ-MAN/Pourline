package com.timemoredot.app.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 코드 대조 조건(architecture.md 8.4·9.1·9.7, ui-design 3.7·6.4)을 소스 문자열로 확인한다. */
class HistoryCodeContractTest {
    private fun src(path: String) = File("src/main/java/com/timemoredot/app/$path").readText()
    private fun code(path: String) = src(path).lines().filter { !it.trimStart().startsWith("//") && !it.trimStart().startsWith("*") && !it.trimStart().startsWith("/**") }.joinToString("\n")

    @Test fun `두 ViewModel 파일에는 Uri와 ContentResolver가 없다`() {
        for (f in listOf("ui/history/HistoryViewModel.kt", "ui/history/SessionDetailViewModel.kt")) assertFalse(f, Regex("Uri|ContentResolver").containsMatchIn(src(f)))
    }

    @Test fun `S10은 imePadding을 쓰지 않고 safeDrawing 인셋을 받으며 뒤로 가기 핸들러가 없다`() {
        val s = src("ui/history/SessionDetailScreen.kt")
        assertEquals(0, Regex("""imePadding\(""").findAll(s).count())
        assertTrue("contentWindowInsets = WindowInsets.safeDrawing" in s)
        assertFalse("NavigationBackHandler" in s)
    }

    @Test fun `화면에 직전 CsvTarget이나 Uri를 보관하는 필드가 없다`() {
        for (f in listOf("ui/history/HistoryScreen.kt", "ui/history/SessionDetailScreen.kt", "ui/history/HistoryParts.kt")) {
            val c = code(f)
            assertFalse(f, Regex("""(var|val)\s+\w*(Uri|Target)\w*\s*(:|=)\s*(remember|mutableStateOf|null)""").containsMatchIn(c))
            assertFalse(f, Regex("""CsvTarget\?|Uri\?""").containsMatchIn(c.replace("(Uri?) ->", "")) && f != "ui/history/HistoryParts.kt")
        }
        assertTrue("container::csvTarget" in src("ui/nav/Navigation.kt"))
    }

    @Test fun `S9 행은 S7과 같은 조약돌 행이고 날짜 머리글은 배경이 없다`() {
        val s = src("ui/history/HistoryScreen.kt")
        val order = listOf(".padding(horizontal = Space.l, vertical = Space.xs)", ".focusRing(shape)", ".clip(shape)", ".clickable(")
        assertTrue(order.map { s.indexOf(it) }.let { idx -> idx.all { it > 0 } && idx == idx.sorted() })
        assertTrue("containerColor = MaterialTheme.colorScheme.surfaceContainerHigh" in s && "val shape = MaterialTheme.shapes.large" in s)
        val header = s.substring(s.indexOf("private fun DateHeader"), s.indexOf("private fun g1"))
        assertFalse("background" in header || "Surface(" in header)
        assertTrue("EmptyState(" in s)
    }

    @Test fun `요약판 표식 도달 판정은 STEP_TOLERANCE_GRAMS를 쓴다`() {
        assertTrue("finalWeightGrams >= stepTargetGrams - STEP_TOLERANCE_GRAMS" in src("ui/brew/shape/ShapeMath.kt"))
        assertTrue("summaryStepReached(" in src("ui/summary/SummaryShapes.kt"))
        assertFalse(Regex("""1\.0\b""").containsMatchIn(code("ui/history/SessionDetailScreen.kt")))
    }

    @Test fun `S10 삭제 결과는 session_deleted로 S9가 읽고 지운다`() {
        val nav = src("ui/nav/Navigation.kt")
        assertTrue("""const val SESSION_DELETED = "session_deleted"""" in nav)
        assertTrue("previousBackStackEntry?.savedStateHandle?.set(SESSION_DELETED, true)" in nav)
        assertTrue("getStateFlow(SESSION_DELETED, false)" in nav && "savedStateHandle[SESSION_DELETED] = false" in nav)
    }

    @Test fun `스위치 일정 줄은 S8과 같은 함수 하나이고 S10은 recipe의 valveSchedule만 읽는다`() {
        val detail = src("ui/history/SessionDetailScreen.kt")
        assertTrue("ValveScheduleLine(" in detail && "valveSchedule" in detail)
        assertFalse("valveScheduleText(" in detail)
        assertTrue("ValveScheduleLine(ui.valvePreview)" in src("ui/recipes/RecipeEditScreen.kt"))
        assertFalse(".valveOpen" in code("ui/history/SessionDetailScreen.kt"))   // 단계를 직접 읽지 않는다
    }

    @Test fun `두 화면이 내보내는 중 알림을 같은 함수로 낸다`() {
        for (f in listOf("ui/history/HistoryScreen.kt", "ui/history/SessionDetailScreen.kt")) assertTrue(f, "AnnounceExporting(" in src(f))
        assertTrue("announcer.announce(EXPORTING_ANNOUNCEMENT, false)" in src("ui/history/HistoryParts.kt"))
    }

    @Test fun `매니페스트에 저장소 권한이 없다`() {
        val m = File("src/main/AndroidManifest.xml").readText()
        assertFalse(Regex("(READ|WRITE|MANAGE)_EXTERNAL_STORAGE").containsMatchIn(m))
    }
}
