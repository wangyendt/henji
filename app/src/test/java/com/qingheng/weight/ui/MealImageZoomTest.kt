package com.qingheng.weight.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class MealImageZoomTest {
    @Test
    fun `unzoomed image always stays centered`() {
        assertEquals(
            Offset.Zero,
            constrainImageOffset(Offset(200f, -300f), scale = 1f, viewportSize = IntSize(1_000, 800)),
        )
    }

    @Test
    fun `zoomed image pan stays within visible bounds`() {
        assertEquals(
            Offset(500f, -400f),
            constrainImageOffset(Offset(900f, -700f), scale = 2f, viewportSize = IntSize(1_000, 800)),
        )
    }
}
