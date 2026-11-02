package com.timemoredot.app.ui.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 코드 대조 조건(architecture.md 9.1·9.6, ui-design 3절·3.5·6.4)을 소스 문자열로 확인한다. */
class RecipeCodeContractTest {
    private fun src(name: String) = File("src/main/java/com/timemoredot/app/ui/recipes/$name").readText()

    @Test fun `toDoubleOrNull과 toIntOrNull은 파싱 함수 두 개 안에만 있다`() {
        for (f in listOf("RecipeEditViewModel.kt", "RecipeEditScreen.kt")) {
            val lines = src(f).lines().filter { !it.trimStart().startsWith("//") } .filter { "toDoubleOrNull" in it || "toIntOrNull" in it }
            assertTrue("$f: $lines", lines.all { "trim()" in it })
            assertEquals(f, if (f.startsWith("RecipeEditViewModel")) 3 else 0, lines.size)   // parseStepGrams 1줄 + parseStepTimeSec 2줄
        }
    }

    @Test fun `S8는 imePadding을 쓰지 않고 safeDrawing 인셋을 받는다`() {
        val s = src("RecipeEditScreen.kt")
        assertFalse(Regex("""imePadding\(""").containsMatchIn(s))
        assertTrue("contentWindowInsets = WindowInsets.safeDrawing" in s)
        assertEquals(1, Regex("""NavigationBackHandler\(""").findAll(s).count())
        assertTrue("isBackEnabled = ui.hasUnsavedChanges || ui.state == RecipeEditState.Saving" in s)
        assertFalse(Regex("""(TextField|semantics)[^\n]*contentDescription = "[^"]*(누적 목표|목표 시각|끝 시각)""").containsMatchIn(s))
    }

    @Test fun `S8 알림 이벤트는 VM에 없고 모양 토큰은 명세와 같다`() {
        assertFalse("Announce" in src("RecipeEditViewModel.kt"))
        val s = src("RecipeEditScreen.kt")
        assertTrue("shape = MaterialTheme.shapes.large" in s)
        assertTrue("val shape = MaterialTheme.shapes.medium" in s && "val small = MaterialTheme.shapes.small" in s)
        val list = src("RecipeListScreen.kt")
        val order = listOf(".padding(horizontal = Space.l, vertical = Space.xs)", ".focusRing(shape)", ".clip(shape)", ".clickable(")
        assertTrue(order.map { list.indexOf(it) }.let { idx -> idx.all { it > 0 } && idx == idx.sorted() })
        assertTrue("containerColor = MaterialTheme.colorScheme.surfaceContainerHigh" in list && "val shape = MaterialTheme.shapes.large" in list)
        assertTrue("EmptyState(" in list)
    }
}
