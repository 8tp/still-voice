package dev.chuds.stillvoice.data

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun exportFilenameFor(recording: Recording): String =
    "${exportBaseNameFor(recording)}.${recording.format.extension}"

internal fun uniqueExportFilenameFor(recording: Recording, seen: MutableSet<String>): String {
    val baseName = exportBaseNameFor(recording)
    val ext = recording.format.extension
    var name = "$baseName.$ext"
    var counter = 1
    while (!seen.add(name)) {
        name = "$baseName-${counter++}.$ext"
    }
    return name
}

internal fun writeRecordingsZip(
    recordings: List<Recording>,
    output: OutputStream,
    fileForRecording: (Recording) -> File,
) {
    ZipOutputStream(output).use { zip ->
        val seen = HashSet<String>()
        recordings.forEach { recording ->
            zip.putNextEntry(ZipEntry(uniqueExportFilenameFor(recording, seen)))
            val file = fileForRecording(recording)
            if (file.exists()) zip.write(file.readBytes())
            zip.closeEntry()
        }
    }
}

private fun exportBaseNameFor(recording: Recording): String =
    safeExportBaseName(
        recording.label?.takeIf { it.isNotBlank() }
            ?: timestampLabel(recording.recordedAt),
    )

private fun safeExportBaseName(input: String): String {
    val cleaned = input.replace(Regex("[^A-Za-z0-9._\\- ]"), "").trim()
    return (if (cleaned.isEmpty()) "recording" else cleaned).take(80).replace(' ', '-')
}
