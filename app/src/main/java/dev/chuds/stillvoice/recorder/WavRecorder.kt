// WavRecorder — minimal AudioRecord-driven 16-bit PCM WAV writer.
//
// Why this file exists: MediaRecorder cannot produce PCM/WAV. Spec §15.4 left
// the WAV path conditional on whether it could land cleanly in v0.1; this
// implementation is small enough (one background thread, a 44-byte RIFF
// header, copy buffers) to keep WAV in v0.1 without pulling a third-party
// audio library — the still pact's "no DSP library" line is honored.
package dev.chuds.stillvoice.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class WavRecorder(
    private val outputFile: File,
    private val sampleRateHz: Int,
) {
    private val channels = 1
    private val bitsPerSample = 16
    private val byteRate: Int = sampleRateHz * channels * bitsPerSample / 8
    private val blockAlign: Int = channels * bitsPerSample / 8

    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private var raf: RandomAccessFile? = null
    private var bytesWritten: Long = 0L

    @SuppressLint("MissingPermission") // Caller has already gated on RECORD_AUDIO.
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = if (minBuf <= 0) 4096 else minBuf * 2

        val ar = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }
        val file = RandomAccessFile(outputFile, "rw")
        file.setLength(0)
        writeHeader(file, dataSize = 0)
        bytesWritten = 0L

        ar.startRecording()
        running = true
        recorder = ar
        raf = file

        thread = thread(name = "still-voice-wav", isDaemon = true) {
            val buffer = ByteArray(bufferSize)
            while (running) {
                val read = ar.read(buffer, 0, buffer.size)
                if (read > 0) {
                    file.write(buffer, 0, read)
                    bytesWritten += read
                }
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        val ar = recorder
        runCatching { ar?.stop() }
        // Drain the writer thread *before* touching the RandomAccessFile. A
        // bare join(1_000) timeout would let the writer thread keep calling
        // file.write while the main thread does file.seek(0) +
        // writeHeader, corrupting the RIFF header. ar.stop() above breaks
        // the writer out of ar.read() promptly; the bounded poll below is
        // only a defensive ceiling in case the loop is wedged.
        val t = thread
        if (t != null) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (t.isAlive && System.nanoTime() < deadline) {
                runCatching { t.join(100) }
            }
            if (t.isAlive) {
                // Writer is wedged — leave the file untouched rather than race
                // a header patch against an in-flight write. Caller can still
                // recover via recoverWavWithEmptyDataChunk on the next scan.
                recorder = null
                thread = null
                raf = null
                runCatching { ar?.release() }
                return
            }
        }
        runCatching { ar?.release() }
        recorder = null
        thread = null
        // Patch the RIFF header with the final sizes.
        val file = raf
        if (file != null) {
            runCatching {
                file.seek(0)
                writeHeader(file, dataSize = bytesWritten)
            }
            runCatching { file.close() }
            raf = null
        }
    }

    fun cancel() {
        running = false
        val ar = recorder
        runCatching { ar?.stop() }
        runCatching { thread?.join(500) }
        runCatching { ar?.release() }
        recorder = null
        thread = null
        runCatching { raf?.close() }
        raf = null
        outputFile.delete()
    }

    private fun writeHeader(file: RandomAccessFile, dataSize: Long) {
        val totalSize = (dataSize + 36).coerceAtLeast(36)
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalSize.toInt())
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)             // fmt chunk size
        buf.putShort(1)            // PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRateHz)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize.toInt())
        file.write(buf.array())
    }
}
