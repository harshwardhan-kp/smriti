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
    }
}
