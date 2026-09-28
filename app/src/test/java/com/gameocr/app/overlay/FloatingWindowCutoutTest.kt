package com.gameocr.app.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FloatingWindowCutoutTest {

    @Test
    fun `API 30 and newer use ALWAYS`() {
        assertEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            floatingWindowCutoutMode(30),
        )
        assertEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            floatingWindowCutoutMode(35),
        )
    }

    @Test
    fun `API 28 and 29 stay inside the platform supported values`() {
        assertEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
            floatingWindowCutoutMode(28),
        )
        assertEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
            floatingWindowCutoutMode(29),
        )
        assertNotEquals(
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            floatingWindowCutoutMode(29),
        )
    }
}
