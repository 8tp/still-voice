package dev.chuds.stillvoice.data

import org.junit.Assert.assertEquals
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
}
