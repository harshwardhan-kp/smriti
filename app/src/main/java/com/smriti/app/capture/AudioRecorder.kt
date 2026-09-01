package com.smriti.app.capture

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var outputFile: File? = null
    private var outputStream: FileOutputStream? = null

    @Volatile
    private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun start() {
        synchronized(this) {
            if (isRecording) return
            isRecording = true
        }

        val voiceDir = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(voiceDir, "${System.currentTimeMillis()}.wav")
        outputFile = file

        val fos = FileOutputStream(file)
        outputStream = fos
        writePlaceholderHeader(fos)

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = if (minBuf > 0) minBuf * 2 else 4096

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        audioRecord = record
        record.startRecording()

        val thread = Thread {
            val buffer = ByteArray(bufferSize)
            try {
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        try {
                            fos.write(buffer, 0, read)
                        } catch (_: Exception) {
                            break
                        }
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                        break
                    }
                }
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {}
                try {
                    record.release()
                } catch (_: Exception) {}
                try {
                    fos.flush()
                    fos.close()
                } catch (_: Exception) {}
                // Patch header if we stopped normally; if cancel() deletes file, this is no-op
                try {
                    val f = outputFile
                    if (f != null && f.exists()) {
                        patchHeader(f)
                    }
                } catch (_: Exception) {}
            }
        }
        thread.start()
        recordingThread = thread
    }

    suspend fun stop(): File? = withContext(Dispatchers.IO) {
        val thread: Thread?
        synchronized(this@AudioRecorder) {
            if (!isRecording && outputFile == null) return@withContext null
            isRecording = false
            thread = recordingThread
        }
        try {
            thread?.join()
        } catch (_: InterruptedException) {}
        recordingThread = null
        audioRecord = null
        outputStream = null

        val file = outputFile
        outputFile = null
        if (file == null || !file.exists()) return@withContext null
        // If file has only header (44 bytes) then nothing recorded
        if (file.length() <= 44L) {
            try { file.delete() } catch (_: Exception) {}
            return@withContext null
        }
        // Ensure header is patched (thread finally already did, but double-check)
        try { patchHeader(file) } catch (_: Exception) {}
        file
    }

    fun cancel() {
        val thread: Thread?
        val file: File?
        synchronized(this) {
            isRecording = false
            thread = recordingThread
            file = outputFile
            // clear reference so stop() won't try to return it
            outputFile = null
        }
        try {
            audioRecord?.let {
                try { it.stop() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        audioRecord = null
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null
        try {
            thread?.join(500)
        } catch (_: Exception) {}
        recordingThread = null
        if (file != null && file.exists()) {
            try { file.delete() } catch (_: Exception) {}
        }
    }

    private fun writePlaceholderHeader(out: FileOutputStream) {
        val header = ByteArray(44)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(0) // ChunkSize placeholder
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16) // Subchunk1Size for PCM
        bb.putShort(1) // AudioFormat PCM
        bb.putShort(1) // NumChannels mono
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * 2) // ByteRate = SampleRate * NumChannels * BitsPerSample/8
        bb.putShort(2) // BlockAlign = NumChannels * BitsPerSample/8
        bb.putShort(16) // BitsPerSample
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(0) // Subchunk2Size placeholder
        out.write(header)
    }

    private fun patchHeader(file: File) {
        val fileLength = file.length()
        val dataSize = (fileLength - 44).coerceAtLeast(0L).toInt()
        val chunkSize = 36 + dataSize
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            writeIntLE(raf, chunkSize)
            raf.seek(40)
            writeIntLE(raf, dataSize)
        }
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value shr 8) and 0xFF)
        raf.write((value shr 16) and 0xFF)
        raf.write((value shr 24) and 0xFF)
    }
}
