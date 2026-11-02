package com.timemoredot.app.ui.nav

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTabsTest {
    @Test fun `너비 600 dp에서 레일로 바뀐다`() {
        assertEquals(NavigationSuiteType.NavigationBar, tabTypeFor(599))
        assertEquals(NavigationSuiteType.NavigationRail, tabTypeFor(600))
    }

    @Test fun `탭 보임 규칙`() {
        assertTrue(tabsVisible(Dest.Recipes, showBottomBar = false))
        assertTrue(tabsVisible(Dest.History, showBottomBar = false))
        assertTrue(tabsVisible(Dest.Brew, showBottomBar = true))
        assertFalse(tabsVisible(Dest.Brew, showBottomBar = false))
        listOf(Dest.Connect, Dest.Summary, Dest.RecipeEdit, Dest.SessionDetail).forEach {
            assertFalse("$it", tabsVisible(it, showBottomBar = true))
        }
        assertFalse(tabsVisible(null, showBottomBar = true))
    }
}
