package dev.chuds.stillvoice.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingIndexReconcileTest {

    @Test fun keepsIndexedMetadataAndAddsOrphanDiskFiles() {
        val indexed = listOf(recording(id = "a", label = "keeper", durationMs = 10, sizeBytes = 1))
        val disk = listOf(
            recording(id = "a", label = null, durationMs = 99, sizeBytes = 5),
            recording(id = "b", label = null, durationMs = 20, sizeBytes = 7, recordedAt = 2_000),
        )

        val reconciled = reconcileRecordings(indexed, disk)

        assertEquals(listOf("b", "a"), reconciled.map { it.id })
        assertEquals("keeper", reconciled.first { it.id == "a" }.label)
        assertEquals(5L, reconciled.first { it.id == "a" }.sizeBytes)
    }

    @Test fun corruptOrMissingIndexFallsBackToDiskRecordings() {
        val disk = listOf(recording(id = "orphan", recordedAt = 1_000))

        assertEquals(listOf("orphan"), reconcileRecordings(indexed = null, disk = disk).map { it.id })
    }

    @Test fun dropsIndexedRowsWhoseFilesAreGone() {
        val indexed = listOf(recording(id = "gone"), recording(id = "present"))
        val disk = listOf(recording(id = "present"))

        assertEquals(listOf("present"), reconcileRecordings(indexed, disk).map { it.id })
    }

    @Test fun diskScanIgnoresZeroByteM4aButKeepsReadableM4a() {
        val dir = createTempDirectory("still-voice-m4a").toFile()
        try {
            val empty = File(dir, "empty.m4a").apply { writeBytes(ByteArray(0)) }
            val valid = File(dir, "valid.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

            assertNull(recordingFromOrphanFile(empty) { 1_000 })
            assertEquals(
                "valid",
                recordingFromOrphanFile(valid) { 1_000 }?.id,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun diskScanIgnoresHeaderOnlyWavButKeepsWavWithPcmData() {
        val dir = createTempDirectory("still-voice-wav").toFile()
        try {
            val headerOnly = File(dir, "header-only.wav").apply {
                writeBytes(wavBytes(dataBytes = 0))
            }
            val valid = File(dir, "valid.wav").apply {
                writeBytes(wavBytes(dataBytes = 88_200))
            }

            assertNull(recordingFromOrphanFile(headerOnly) { null })
            val recording = recordingFromOrphanFile(valid) { null }

            assertEquals("valid", recording?.id)
            assertEquals(1_000L, recording?.durationMs)
            assertEquals(AudioFormat.WAV_PCM, recording?.format)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun diskScanKeepsIndexedFileWhenDurationProbeFails() {
        val dir = createTempDirectory("still-voice-indexed").toFile()
        try {
            val file = File(dir, "indexed.m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val indexed = recording(
                id = "indexed",
                label = "saved",
                durationMs = 12_345,
                sizeBytes = 1,
            )

            val recording = recordingFromIndexedFile(file, indexed)

            assertEquals("indexed", recording?.id)
            assertEquals("saved", recording?.label)
            assertEquals(12_345L, recording?.durationMs)
            assertEquals(4L, recording?.sizeBytes)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun recording(
        id: String,
        label: String? = null,
        recordedAt: Long = 1_000,
        durationMs: Long = 1,
        sizeBytes: Long = 1,
        format: AudioFormat = AudioFormat.M4A_AAC,
    ) = Recording(
        id = id,
        label = label,
        recordedAt = recordedAt,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        format = format,
        sampleRateHz = 44_100,
    )

    private fun wavBytes(dataBytes: Int): ByteArray {
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())
        buf.putInt(44_100)
        buf.putInt(44_100 * 2)
        buf.putShort(2.toShort())
        buf.putShort(16.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
        repeat(dataBytes) { buf.put(0) }
        return buf.array()
    }
}
