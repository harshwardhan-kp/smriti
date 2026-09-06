package com.smriti.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOnlyCaptureTest {

    @Test
    fun `isWorthSaving returns false for empty transcript`() {
        assertFalse(CapturePipeline.isWorthSaving(""))
    }

    @Test
    fun `isWorthSaving returns false for spaces only`() {
        assertFalse(CapturePipeline.isWorthSaving("   "))
    }

    @Test
    fun `isWorthSaving returns false for whitespace with newlines and tabs`() {
        assertFalse(CapturePipeline.isWorthSaving("\n\t "))
    }

    @Test
    fun `isWorthSaving returns true for a real sentence`() {
        assertTrue(CapturePipeline.isWorthSaving("remember that Sharma Traders wants two hundred more units"))
    }

    @Test
    fun `isWorthSaving returns true for a sentence with leading and trailing spaces`() {
        assertTrue(CapturePipeline.isWorthSaving("   remember that Sharma Traders wants two hundred more units   "))
    }
}
