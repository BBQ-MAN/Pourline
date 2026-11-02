package com.timemoredot.app.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.theme.TimemoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmptyStateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `의미 트리에 제목 설명 버튼만 있다`() {
        rule.setContent { TimemoreTheme { EmptyState(AppIcons.Bluetooth, "제목", description = "설명", actionLabel = "버튼", onAction = {}) } }
        val tree = rule.onRoot(useUnmergedTree = true).printToString()
        assertEquals(3, Regex("Text = '\\[").findAll(tree).count())
        assertEquals(false, tree.contains("ContentDescription"))
    }
}
