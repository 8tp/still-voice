package dev.chuds.stillvoice.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
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
            val indexed = readIndex()
            val loaded = reconcileRecordings(indexed, scanRecordingsFromDisk(indexed))
            writeIndex(loaded)
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
            val indexed = readIndex()
            val onDisk = reconcileRecordings(indexed, scanRecordingsFromDisk(indexed))
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
        writeRecordingsZip(_recordings.value, output, ::recordingFile)
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

    private fun scanRecordingsFromDisk(indexed: List<Recording>?): List<Recording> {
        val indexedByFileName = indexed.orEmpty().associateBy { it.fileNameKey() }
        val files = recordingsDir.listFiles { f ->
            f.extension == "m4a" || f.extension == "wav"
        } ?: emptyArray()

        return files.mapNotNull { file ->
            val indexedRecording = indexedByFileName[file.name]
            if (indexedRecording != null) {
                recordingFromIndexedFile(file, indexedRecording)
            } else {
                recordingFromOrphanFile(file, ::readDurationMsFromFile)
            }
        }
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
}

internal fun reconcileRecordings(
    indexed: List<Recording>?,
    disk: List<Recording>,
): List<Recording> {
    val diskByFileName = disk.associateBy { it.fileNameKey() }
    val keptIndexed = indexed.orEmpty().mapNotNull { recording ->
        val file = diskByFileName[recording.fileNameKey()] ?: return@mapNotNull null
        recording.copy(
            durationMs = recording.durationMs.takeIf { it > 0 } ?: file.durationMs,
            sizeBytes = file.sizeBytes,
        )
    }
    val keptKeys = keptIndexed.mapTo(mutableSetOf()) { it.fileNameKey() }
    val orphans = disk.filterNot { it.fileNameKey() in keptKeys }
    return (keptIndexed + orphans).sortedByDescending { it.recordedAt }
}

private fun Recording.fileNameKey(): String = "$id.${format.extension}"

internal fun recordingFromIndexedFile(
    file: File,
    indexed: Recording,
): Recording? {
    val sizeBytes = file.length().takeIf { it > 0 } ?: return null
    return indexed.copy(sizeBytes = sizeBytes)
}

internal fun recordingFromOrphanFile(
    file: File,
    durationReader: (File) -> Long?,
): Recording? {
    val format = when (file.extension.lowercase(Locale.US)) {
        "m4a" -> AudioFormat.M4A_AAC
        "wav" -> AudioFormat.WAV_PCM
        else -> return null
    }
    val sizeBytes = file.length().takeIf { it > 0 } ?: return null
    val durationMs = when (format) {
        AudioFormat.M4A_AAC -> durationReader(file)?.takeIf { it > 0 } ?: return null
        AudioFormat.WAV_PCM -> readWavPcmDurationMs(file)?.takeIf { it > 0 }
            ?: durationReader(file)?.takeIf { it > 0 }
            ?: return null
    }
    val timestamp = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
    return Recording(
        id = file.nameWithoutExtension,
        label = null,
        recordedAt = timestamp,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        format = format,
        sampleRateHz = 44_100,
    )
}

internal fun readWavPcmDurationMs(file: File): Long? = runCatching {
    RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 44) return@runCatching null
        if (raf.readAscii(4) != "RIFF") return@runCatching null
        raf.readLeInt()
        if (raf.readAscii(4) != "WAVE") return@runCatching null

        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var dataBytes = 0L

        while (raf.filePointer + 8 <= raf.length()) {
            val chunkId = raf.readAscii(4)
            val chunkSize = raf.readLeInt()
            val chunkStart = raf.filePointer
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16) return@runCatching null
                    val audioFormat = raf.readLeShort()
                    channels = raf.readLeShort()
                    sampleRate = raf.readLeInt().toInt()
                    raf.readLeInt() // byte rate
                    raf.readLeShort() // block align
                    bitsPerSample = raf.readLeShort()
                    if (audioFormat != 1) return@runCatching null
                }
                "data" -> {
                    dataBytes = chunkSize
                }
            }
            val next = chunkStart + chunkSize + (chunkSize % 2)
            if (next > raf.length()) return@runCatching null
            raf.seek(next)
            if (dataBytes > 0) break
        }

        if (sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0 || dataBytes <= 0) {
            return@runCatching null
        }
        val bytesPerSecond = sampleRate.toLong() * channels * bitsPerSample / 8
        if (bytesPerSecond <= 0) null else dataBytes * 1_000L / bytesPerSecond
    }
}.getOrNull()

private fun RandomAccessFile.readAscii(length: Int): String {
    val bytes = ByteArray(length)
    readFully(bytes)
    return String(bytes, Charsets.US_ASCII)
}

private fun RandomAccessFile.readLeInt(): Long {
    val bytes = ByteArray(4)
    readFully(bytes)
    return Integer.toUnsignedLong(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int)
}

private fun RandomAccessFile.readLeShort(): Int {
    val bytes = ByteArray(2)
    readFully(bytes)
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
}

/**
 * Default label fallback for an unnamed recording — `Wed-May-13-09-14`.
 * Used by exportZip and the default filename for single export.
 */
fun timestampLabel(epochMs: Long): String {
    val fmt = java.text.SimpleDateFormat("EEE MMM d HH-mm", java.util.Locale.US)
    return fmt.format(java.util.Date(epochMs))
}
