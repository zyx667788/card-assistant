package com.gameocr.app.game.session

import org.junit.Assert.assertEquals
import org.junit.Test

class GameTurnCoordinatorTest {

    @Test
    fun `vlmTargetSize caps the longer side at max`() {
        assertEquals(768 to 576, GameTurnCoordinator.vlmTargetSize(1600, 1200, 768))
        assertEquals(768 to 384, GameTurnCoordinator.vlmTargetSize(1600, 800, 768))
    }

    @Test
    fun `vlmTargetSize does not upscale smaller images`() {
        assertEquals(500 to 400, GameTurnCoordinator.vlmTargetSize(500, 400, 768))
        assertEquals(768 to 768, GameTurnCoordinator.vlmTargetSize(768, 768, 768))
    }

    @Test
    fun `vlmTargetSize uses the production default when no max given`() {
        val (w, h) = GameTurnCoordinator.vlmTargetSize(2560, 1440)
        assertEquals(1152, maxOf(w, h))
    }
}
