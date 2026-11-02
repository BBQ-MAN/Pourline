package com.timemoredot.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeTest {
    @Test fun `AppShapes는 8, 12, 20, 28, 32 dp`() {
        val s = AppShapes
        listOf(s.extraSmall to 8, s.small to 12, s.medium to 20, s.large to 28, s.extraLarge to 32).forEach { (shape, dp) ->
            assertEquals(RoundedCornerShape(dp.dp), shape)
        }
    }

    @Test fun `blob은 결정적이고 반지름 1_09배 안이다`() {
        val d = Density(1f)
        val size = Size(96f, 96f)
        val o1 = AppShape.blob.createOutline(size, LayoutDirection.Ltr, d)
        val o2 = AppShape.blob.createOutline(size, LayoutDirection.Ltr, d)
        assertTrue(o1 is Outline.Generic && o2 is Outline.Generic)
        val b1 = o1.let { (it as Outline.Generic).path.getBounds() }
        assertEquals(b1, (o2 as Outline.Generic).path.getBounds())
        val lim = 48f * 1.09f
        assertTrue(b1.left >= 48f - lim - 0.01f && b1.right <= 48f + lim + 0.01f && b1.top >= 48f - lim - 0.01f && b1.bottom <= 48f + lim + 0.01f)
    }
}
