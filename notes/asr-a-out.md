FILE: app/src/main/java/com/smriti/app/capture/PushToTalk.kt
```kotlin
package com.smriti.app.capture

/**
 * Implemented by Asr implementations that record while a button is held, so the UI can
 * end recording on release without referencing a flavor-specific class by name.
 */
interface PushToTalk {
    fun stopListening()
}
```

FILE: app/src/main/java/com/smriti/app/capture/AudioRecorder.kt
```kotlin
package com.smriti.app.capture

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class AudioRecorder(private val context: Context) {

    @Volatile
    private var _isRecording = false

    val isRecording: Boolean
        get() = _isRecording

    private var recordingThread: Thread? = null
    private var outputFile: File? = null
    private var bufferedOutput: BufferedOutputStream? = null

    @Volatile
    private var dataSize: Int = 0

    fun start() {
        if (_isRecording) return

        val dir = File(context.cacheDir, "voice").also { it.mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.wav")
        outputFile = file
        dataSize = 0

        val minBufferSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            4096
        } else {
            minBufferSize
        }
        val buffer = ByteArray(bufferSize)

        try {
            val fos = FileOutputStream(file)
            bufferedOutput = BufferedOutputStream(fos)
            writeWavHeader(bufferedOutput!!, 0)
            bufferedOutput!!.flush()
        } catch (_: Exception) {
            try {
                bufferedOutput?.close()
            } catch (_: Exception) {
            }
            bufferedOutput = null
            outputFile = null
            return
        }

        val audioRecord: AudioRecord
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            try {
                bufferedOutput?.close()
            } catch (_: Exception) {
            }
            bufferedOutput = null
            file.delete()
            outputFile = null
            _isRecording = false
            return
        }

        try {
            audioRecord.startRecording()
        } catch (e: SecurityException) {
            try {
                bufferedOutput?.close()
            } catch (_: Exception) {
            }
            bufferedOutput = null
            try {
                audioRecord.release()
            } catch (_: Exception) {
            }
            file.delete()
            outputFile = null
            _isRecording = false
            return
        }

        _isRecording = true

        recordingThread = Thread {
            try {
                while (_isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        bufferedOutput?.write(buffer, 0, read)
                        dataSize += read
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                        break
                    }
                }
            } finally {
                try {
                    audioRecord.stop()
                } catch (_: Exception) {
                }
                audioRecord.release()
            }
        }
        recordingThread!!.start()
    }

    fun stop(): File? {
        if (!_isRecording && outputFile == null) {
            return null
        }
        _isRecording = false
        try {
            recordingThread?.join()
        } catch (_: InterruptedException) {
        }
        recordingThread = null

        try {
            bufferedOutput?.close()
        } catch (_: Exception) {
        }
        bufferedOutput = null

        val file = outputFile
        outputFile = null

        if (file == null || !file.exists()) {
            dataSize = 0
            return null
        }

        if (dataSize <= 0 || file.length() <= 44) {
            file.delete()
            dataSize = 0
            return null
        }

        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(4)
                writeIntLERaf(raf, 36 + dataSize)
                raf.seek(40)
                writeIntLERaf(raf, dataSize)
            }
        } catch (_: Exception) {
            file.delete()
            dataSize = 0
            return null
        }

        val resultDataSize = dataSize
        dataSize = 0

        if (resultDataSize <= 0 || file.length() <= 44 || !file.exists()) {
            file.delete()
            return null
        }

        return file
    }

    fun cancel() {
        val file = stop()
        file?.delete()
    }

    private fun writeWavHeader(out: BufferedOutputStream, dataSize: Int) {
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 16)
        writeShortLE(out, 1)
        writeShortLE(out, 1)
        writeIntLE(out, 16000)
        writeIntLE(out, 32000)
        writeShortLE(out, 2)
        writeShortLE(out, 16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, dataSize)
    }

    private fun writeIntLE(out: BufferedOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeShortLE(out: BufferedOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun writeIntLERaf(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value shr 8) and 0xFF)
        raf.write((value shr 16) and 0xFF)
        raf.write((value shr 24) and 0xFF)
    }
}
```
