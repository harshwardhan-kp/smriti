package com.smriti.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class BubbleServiceTest {

    @Test
    fun `isRunning is initially false`() {
        assertFalse(BubbleService.isRunning())
    }

    @Test
    fun `constants are defined as expected`() {
        assertNotNull(BubbleService.ACTION_START)
        assertNotNull(BubbleService.ACTION_STOP)
        assertEquals("SmritiBubble", BubbleService.TAG)
        assertEquals("com.smriti.app.capture.action.START_BUBBLE", BubbleService.ACTION_START)
        assertEquals("com.smriti.app.capture.action.STOP_BUBBLE", BubbleService.ACTION_STOP)
        assertEquals("smriti_bubble", BubbleService.CHANNEL_ID)
        assertEquals("mic_bubble_x", BubbleService.PREF_MIC_BUBBLE_X)
        assertEquals("mic_bubble_y", BubbleService.PREF_MIC_BUBBLE_Y)
    }

    @Test
    fun `default mic bubble offset arithmetic computes capture height plus 8dp gap`() {
        // Density 1.0f: captureHeight = 60px, 8dp = 8px -> offset = 68px
        val offset1 = BubbleService.defaultMicBubbleOffset(captureHeight = 60, density = 1.0f)
        assertEquals(68, offset1)

        // Density 2.0f: captureHeight = 120px, 8dp = 16px -> offset = 136px
        val offset2 = BubbleService.defaultMicBubbleOffset(captureHeight = 120, density = 2.0f)
        assertEquals(136, offset2)

        // Density 2.5f: captureHeight = 150px, 8dp = 20px -> offset = 170px
        val offset25 = BubbleService.defaultMicBubbleOffset(captureHeight = 150, density = 2.5f)
        assertEquals(170, offset25)

        // Density 3.0f: captureHeight = 180px, 8dp = 24px -> offset = 204px
        val offset3 = BubbleService.defaultMicBubbleOffset(captureHeight = 180, density = 3.0f)
        assertEquals(204, offset3)
    }

    @Test
    fun `default mic bubble y positions directly below capture bubble with offset`() {
        // captureY = 100, captureHeight = 60, density = 1.0f -> 100 + 60 + 8 = 168
        val defaultY1 = BubbleService.defaultMicBubbleY(captureY = 100, captureHeight = 60, density = 1.0f)
        assertEquals(168, defaultY1)

        // captureY = 500, captureHeight = 150, density = 2.5f -> 500 + 150 + 20 = 670
        val defaultY2 = BubbleService.defaultMicBubbleY(captureY = 500, captureHeight = 150, density = 2.5f)
        assertEquals(670, defaultY2)
    }

    @Test
    fun `clampPosition keeps in-bounds coordinates unchanged`() {
        val (x, y) = BubbleService.clampPosition(
            x = 200,
            y = 400,
            width = 44,
            height = 44,
            screenWidth = 1080,
            screenHeight = 1920
        )
        assertEquals(200, x)
        assertEquals(400, y)
    }

    @Test
    fun `clampPosition coerces negative coordinates to zero`() {
        val (x, y) = BubbleService.clampPosition(
            x = -50,
            y = -10,
            width = 60,
            height = 60,
            screenWidth = 1080,
            screenHeight = 1920
        )
        assertEquals(0, x)
        assertEquals(0, y)
    }

    @Test
    fun `clampPosition coerces coordinates exceeding screen bounds to maxX and maxY`() {
        val (x, y) = BubbleService.clampPosition(
            x = 1200,
            y = 2000,
            width = 60,
            height = 60,
            screenWidth = 1080,
            screenHeight = 1920
        )
        // maxX = 1080 - 60 = 1020, maxY = 1920 - 60 = 1860
        assertEquals(1020, x)
        assertEquals(1860, y)
    }

    @Test
    fun `clampPosition handles sizes larger than screen`() {
        val (x, y) = BubbleService.clampPosition(
            x = 100,
            y = 100,
            width = 1200,
            height = 2000,
            screenWidth = 1080,
            screenHeight = 1920
        )
        // maxX < 0 -> maxOf(0, maxX) = 0 -> coerced to 0
        assertEquals(0, x)
        assertEquals(0, y)
    }
}
