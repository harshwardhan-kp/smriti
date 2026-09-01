package com.smriti.app.capture

/**
 * Interface for Automated Speech Recognition (ASR).
 *
 * Note: The production implementation will be backed by whisper.cpp for high-accuracy,
 * fully offline speech recognition. This interface exists so that swapping out the
 * platform-based speech engine for whisper.cpp is a one-line change.
 */
interface Asr {
    suspend fun transcribe(maxMillis: Long = 15_000): String
    fun cancel()
}