package com.smriti.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScreenCaptureServiceTest {

    @Test
    fun `isArmed is initially false`() {
        assertFalse(ScreenCaptureService.isArmed())
    }

    @Test
    fun `constants are defined as expected`() {
        assertNotNull(ScreenCaptureService.ACTION_START)
        assertNotNull(ScreenCaptureService.ACTION_CAPTURE)
        assertNotNull(ScreenCaptureService.ACTION_STOP)
        assertNotNull(ScreenCaptureService.ACTION_CAPTURED)
        assertNotNull(ScreenCaptureService.TAG)
        assert(ScreenCaptureService.IDLE_TIMEOUT_MS == 5 * 60 * 1000L)
    }
}
