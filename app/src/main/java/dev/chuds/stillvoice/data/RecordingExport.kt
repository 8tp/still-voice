package dev.chuds.stillvoice.data

import java.io.File
import java.io.FileNotFoundException
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
    val files = recordings.map { recording ->
        val file = fileForRecording(recording)
        if (!file.isFile) {
            throw FileNotFoundException("Missing recording file for ${recording.id}: ${file.path}")
        }
        recording to file
    }

    ZipOutputStream(output).use { zip ->
        val seen = HashSet<String>()
        files.forEach { (recording, file) ->
            file.inputStream().use { input ->
                zip.putNextEntry(ZipEntry(uniqueExportFilenameFor(recording, seen)))
                input.copyTo(zip)
            }
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
