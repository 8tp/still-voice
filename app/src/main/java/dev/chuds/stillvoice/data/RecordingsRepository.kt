package dev.chuds.stillvoice.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * File-backed recordings store. Mirrors NotesRepository byte-for-byte in shape:
 * a MutableStateFlow<List<Recording>> exposed as StateFlow, ioMutex-guarded
 * mutations, rebuild-from-disk fallback when the index is corrupt or missing.
 *
 * Layout under filesDir:
 *   recordings/<id>.m4a   or   recordings/<id>.wav   — one audio file per recording
 *   index.json                                       — ordered metadata for fast list rendering
 */
class RecordingsRepository(context: Context) {

    private val recordingsDir: File =
        File(context.filesDir, "recordings").apply { if (!exists()) mkdirs() }
    private val indexFile: File = File(context.filesDir, "index.json")
    private val ioMutex = Mutex()

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val loaded = readIndex() ?: rebuildIndexFromDisk()
            _recordings.value = loaded.sortedDescending()
        }
    }

    /**
     * Build the target file for a fresh recording id. Used by RecordingService
     * before MediaRecorder.setOutputFile.
     */
    fun fileFor(id: String, format: AudioFormat): File =
        File(recordingsDir, "$id.${format.extension}")

    /**
     * Called by RecordingService after MediaRecorder.stop() succeeds. Adds the
     * new recording to the index and emits the new list.
     *
     * Re-reads index.json from disk under the mutex before merging: the service
     * holds its own RecordingsRepository instance whose in-memory _recordings
     * is empty on a cold start, so trusting the in-memory value would clobber
     * the index with just the new recording.
     */
    suspend fun adopt(recording: Recording): Recording = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val onDisk = readIndex() ?: emptyList()
            val next = listOf(recording) + onDisk.filterNot { it.id == recording.id }
            writeIndex(next)
            _recordings.value = next.sortedDescending()
            recording
        }
    }

    suspend fun rename(id: String, label: String?): Recording? = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val existing = _recordings.value.firstOrNull { it.id == id } ?: return@withLock null
            val cleaned = label?.trim()?.takeIf { it.isNotEmpty() }
            val updated = existing.copy(label = cleaned)
            val next = _recordings.value.map { if (it.id == id) updated else it }
            writeIndex(next)
            _recordings.value = next.sortedDescending()
            updated
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val existing = _recordings.value.firstOrNull { it.id == id }
            if (existing != null) recordingFile(existing).delete()
            val next = _recordings.value.filterNot { it.id == id }
            writeIndex(next)
            _recordings.value = next.sortedDescending()
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            recordingsDir.listFiles()?.forEach { it.delete() }
            indexFile.delete()
            _recordings.value = emptyList()
        }
    }

    /**
     * The audio file on disk for a recording. Honors the index's `format`
     * field as the source of truth.
     */
    fun recordingFile(recording: Recording): File =
        File(recordingsDir, "${recording.id}.${recording.format.extension}")

    /**
     * Bulk export — write every recording into a zip stream as
     * <safe-label-or-timestamp>.<extension>. Caller supplies the
     * SAF-provided OutputStream.
     */
    suspend fun exportZip(output: OutputStream) = withContext(Dispatchers.IO) {
        ZipOutputStream(output).use { zip ->
            val seen = HashSet<String>()
            _recordings.value.forEach { recording ->
                val baseName = safeFilename(deriveBaseName(recording))
                val ext = recording.format.extension
                var name = "$baseName.$ext"
                var counter = 1
                while (!seen.add(name)) {
                    name = "$baseName-${counter++}.$ext"
                }
                zip.putNextEntry(ZipEntry(name))
                val file = recordingFile(recording)
                if (file.exists()) zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
    }

    fun deriveBaseName(recording: Recording): String =
        recording.label?.takeIf { it.isNotBlank() }
            ?: timestampLabel(recording.recordedAt)

    private fun readIndex(): List<Recording>? {
        if (!indexFile.exists()) return null
        return runCatching {
            val text = indexFile.readText()
            if (text.isBlank()) return null
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Recording(
                    id = obj.getString("id"),
                    label = obj.optString("label").takeIf { it.isNotEmpty() && !obj.isNull("label") },
                    recordedAt = obj.optLong("recordedAt"),
                    durationMs = obj.optLong("durationMs"),
                    sizeBytes = obj.optLong("sizeBytes"),
                    format = runCatching { AudioFormat.valueOf(obj.optString("format")) }
                        .getOrDefault(AudioFormat.M4A_AAC),
                    sampleRateHz = obj.optInt("sampleRateHz", 44_100),
                )
            }
        }.getOrNull()
    }

    private fun rebuildIndexFromDisk(): List<Recording> {
        val files = recordingsDir.listFiles { f ->
            f.extension == "m4a" || f.extension == "wav"
        } ?: emptyArray()

        val recordings = files.mapNotNull { file ->
            val id = file.nameWithoutExtension
            val format = when (file.extension) {
                "wav" -> AudioFormat.WAV_PCM
                else -> AudioFormat.M4A_AAC
            }
            val timestamp = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
            val durationMs = readDurationMsFromFile(file) ?: 0L
            Recording(
                id = id,
                label = null,
                recordedAt = timestamp,
                durationMs = durationMs,
                sizeBytes = file.length(),
                format = format,
                sampleRateHz = 44_100,
            )
        }
        writeIndex(recordings)
        return recordings
    }

    private fun readDurationMsFromFile(file: File): Long? = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            mmr.release()
        }
    }.getOrNull()

    private fun writeIndex(recordings: List<Recording>) {
        val array = JSONArray()
        recordings.forEach { r ->
            val obj = JSONObject()
                .put("id", r.id)
                .put("label", r.label ?: JSONObject.NULL)
                .put("recordedAt", r.recordedAt)
                .put("durationMs", r.durationMs)
                .put("sizeBytes", r.sizeBytes)
                .put("format", r.format.name)
                .put("sampleRateHz", r.sampleRateHz)
            array.put(obj)
        }
        indexFile.writeText(array.toString())
    }

    private fun List<Recording>.sortedDescending(): List<Recording> =
        sortedByDescending { it.recordedAt }

    private fun safeFilename(input: String): String {
        val cleaned = input.replace(Regex("[^A-Za-z0-9._\\- ]"), "").trim()
        return (if (cleaned.isEmpty()) "recording" else cleaned).take(80).replace(' ', '-')
    }
}

/**
 * Default label fallback for an unnamed recording — `Wed-May-13-09-14`.
 * Used by exportZip and the default filename for single export.
 */
fun timestampLabel(epochMs: Long): String {
    val fmt = java.text.SimpleDateFormat("EEE MMM d HH-mm", java.util.Locale.US)
    return fmt.format(java.util.Date(epochMs))
}
