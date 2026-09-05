package com.smriti.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SherpaWhisperAsrTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `readWavSamples returns empty on truncated header`() {
        val emptyWav = tempFolder.newFile("empty.wav")
        emptyWav.writeBytes(ByteArray(40))
        val samples = SherpaWhisperAsr.readWavSamples(emptyWav)
        assertEquals(0, samples.size)
    }

    @Test
    fun `readWavSamples correctly parses little-endian signed 16-bit PCM and normalizes by 32768`() {
        val wavFile = tempFolder.newFile("test.wav")
        val sampleValues = shortArrayOf(
            0,
            16384,
            32767,
            -16384,
            -32768
        )

        val header = ByteArray(44)
        val pcmBytes = ByteArray(sampleValues.size * 2)
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in sampleValues) {
            bb.putShort(s)
        }

        wavFile.writeBytes(header + pcmBytes)

        val samples = SherpaWhisperAsr.readWavSamples(wavFile)
        assertEquals(sampleValues.size, samples.size)

        assertEquals(0.0f, samples[0], 1e-6f)
        assertEquals(0.5f, samples[1], 1e-6f)
        assertEquals(32767f / 32768.0f, samples[2], 1e-6f)
        assertEquals(-0.5f, samples[3], 1e-6f)
        assertEquals(-1.0f, samples[4], 1e-6f)
    }

    @Test
    fun `provisioner returns null when any component is missing`() {
        val dir = tempFolder.newFolder("asr_incomplete")
        File(dir, "small-encoder.int8.onnx").writeText("dummy")
        File(dir, "small-decoder.int8.onnx").writeText("dummy")
        // tokens.txt is missing

        val paths = WhisperModelProvisioner.resolvePaths(dir)
        assertNull(paths)
    }

    @Test
    fun `provisioner prefers int8 variants when both int8 and non-int8 are present`() {
        val dir = tempFolder.newFolder("asr_models")
        val encStandard = File(dir, "small-encoder.onnx").apply { writeText("dummy") }
        val encInt8 = File(dir, "small-encoder.int8.onnx").apply { writeText("dummy") }
        val decStandard = File(dir, "small-decoder.onnx").apply { writeText("dummy") }
        val decInt8 = File(dir, "small-decoder.int8.onnx").apply { writeText("dummy") }
        val tokens = File(dir, "small-tokens.txt").apply { writeText("dummy") }

        val paths = WhisperModelProvisioner.resolvePaths(dir)
        assertNotNull(paths)
        assertEquals(encInt8.absolutePath, paths!!.encoder.absolutePath)
        assertEquals(decInt8.absolutePath, paths.decoder.absolutePath)
        assertEquals(tokens.absolutePath, paths.tokens.absolutePath)
    }

    @Test
    fun `provisioner falls back to non-int8 if int8 is absent`() {
        val dir = tempFolder.newFolder("asr_non_int8")
        val encStandard = File(dir, "encoder.onnx").apply { writeText("dummy") }
        val decStandard = File(dir, "decoder.onnx").apply { writeText("dummy") }
        val tokens = File(dir, "tokens.txt").apply { writeText("dummy") }

        val paths = WhisperModelProvisioner.resolvePaths(dir)
        assertNotNull(paths)
        assertEquals(encStandard.absolutePath, paths!!.encoder.absolutePath)
        assertEquals(decStandard.absolutePath, paths.decoder.absolutePath)
        assertEquals(tokens.absolutePath, paths.tokens.absolutePath)
    }

    @Test
    fun `missingMessage contains expected guidance text`() {
        val msg = WhisperModelProvisioner.missingMessage()
        assertTrue(msg.contains("/data/local/tmp/asr"))
        assertTrue(msg.contains("*encoder*.onnx"))
        assertTrue(msg.contains("*decoder*.onnx"))
        assertTrue(msg.contains("*tokens*.txt"))
    }
}
