package dev.chuds.stillvoice.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test fun labelToFilenameMappingUsesSafeAudioExtension() {
        assertEquals(
            "Morning-idea--take-1.m4a",
            exportFilenameFor(recording(label = " Morning idea / take #1? ")),
        )
        assertEquals(
            "recording.wav",
            exportFilenameFor(recording(label = "?!", format = AudioFormat.WAV_PCM)),
        )
    }

    @Test fun unicodeOnlyLabelsUseRecordingFallbackFilename() {
        assertEquals(
            "recording.m4a",
            exportFilenameFor(recording(label = "東京")),
        )
        assertEquals(
            "recording.wav",
            exportFilenameFor(recording(label = "🎙️", format = AudioFormat.WAV_PCM)),
        )
    }

    @Test fun uniqueFilenameMappingKeepsDuplicateLabelsDistinct() {
        val seen = mutableSetOf<String>()
        val first = recording(id = "a", label = "Daily Standup", format = AudioFormat.M4A_AAC)
        val second = recording(id = "b", label = "Daily Standup", format = AudioFormat.M4A_AAC)

        assertEquals("Daily-Standup.m4a", uniqueExportFilenameFor(first, seen))
        assertEquals("Daily-Standup-1.m4a", uniqueExportFilenameFor(second, seen))
    }

    @Test fun uniqueFilenameMappingKeepsUnicodeOnlyLabelsDistinct() {
        val seen = mutableSetOf<String>()
        val first = recording(id = "a", label = "東京", format = AudioFormat.M4A_AAC)
        val second = recording(id = "b", label = "🎙️", format = AudioFormat.M4A_AAC)

        assertEquals("recording.m4a", uniqueExportFilenameFor(first, seen))
        assertEquals("recording-1.m4a", uniqueExportFilenameFor(second, seen))
    }

    @Test fun bulkExportZipHasRootEntriesWithUniqueFilenamesAndBytes() {
        val recordingsDir = temp.newFolder("recordings")
        val recordings = listOf(
            recording(id = "a", label = "Daily Standup", format = AudioFormat.M4A_AAC),
            recording(id = "b", label = "Daily Standup", format = AudioFormat.M4A_AAC),
            recording(id = "c", label = "WAV mix", format = AudioFormat.WAV_PCM),
        )
        val payloads = mapOf(
            "a" to byteArrayOf(1, 2, 3),
            "b" to byteArrayOf(4, 5),
            "c" to byteArrayOf(6, 7, 8, 9),
        )
        recordings.forEach { recording ->
            recordingFile(recordingsDir, recording).writeBytes(payloads.getValue(recording.id))
        }

        val out = ByteArrayOutputStream()
        writeRecordingsZip(recordings, out) { recordingFile(recordingsDir, it) }

        val entries = unzip(out.toByteArray())
        assertEquals(
            listOf("Daily-Standup.m4a", "Daily-Standup-1.m4a", "WAV-mix.wav"),
            entries.map { it.name },
        )
        assertArrayEquals(payloads.getValue("a"), entries[0].bytes)
        assertArrayEquals(payloads.getValue("b"), entries[1].bytes)
        assertArrayEquals(payloads.getValue("c"), entries[2].bytes)
    }

    @Test fun bulkExportFailsForMissingFilesBeforeCreatingZipEntry() {
        val recordingsDir = temp.newFolder("recordings")
        val missing = recording(id = "missing", label = "Missing file")
        val missingFile = recordingFile(recordingsDir, missing)
        val out = ByteArrayOutputStream()

        val error = assertThrows(FileNotFoundException::class.java) {
            writeRecordingsZip(listOf(missing), out) { recordingFile(recordingsDir, it) }
        }

        assertEquals("Missing recording file for missing: ${missingFile.path}", error.message)
        assertEquals(0, out.size())
    }

    @Test fun bulkExportPreflightsAllFilesBeforeWritingAnyZipEntry() {
        val recordingsDir = temp.newFolder("recordings")
        val first = recording(id = "first", label = "Present file")
        val missing = recording(id = "missing", label = "Missing file")
        val missingFile = recordingFile(recordingsDir, missing)
        recordingFile(recordingsDir, first).writeBytes(byteArrayOf(1, 2, 3))
        val out = ByteArrayOutputStream()

        val error = assertThrows(FileNotFoundException::class.java) {
            writeRecordingsZip(listOf(first, missing), out) { recordingFile(recordingsDir, it) }
        }

        assertEquals("Missing recording file for missing: ${missingFile.path}", error.message)
        assertEquals(0, out.size())
    }

    private fun recording(
        id: String = "id",
        label: String?,
        format: AudioFormat = AudioFormat.M4A_AAC,
    ): Recording = Recording(
        id = id,
        label = label,
        recordedAt = 1_746_800_000_000L,
        durationMs = 1_000L,
        sizeBytes = 0L,
        format = format,
        sampleRateHz = 44_100,
    )

    private fun recordingFile(dir: java.io.File, recording: Recording): java.io.File =
        java.io.File(dir, "${recording.id}.${recording.format.extension}")

    private fun unzip(bytes: ByteArray): List<ZipEntrySnapshot> {
        val entries = mutableListOf<ZipEntrySnapshot>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += ZipEntrySnapshot(entry.name, zip.readBytes())
                zip.closeEntry()
            }
        }
        return entries
    }

    private data class ZipEntrySnapshot(
        val name: String,
        val bytes: ByteArray,
    )
}
