package dev.chuds.stillvoice.data

/**
 * A single recording. Audio bytes live on disk at filesDir/recordings/<id>.<ext>;
 * everything else is metadata persisted in the JSON index for fast list rendering.
 *
 * The index's `format` field is the source of truth for playback — the file
 * extension is a courtesy, not a contract. (Same posture as still-notes treating
 * the index as authoritative metadata.)
 */
data class Recording(
    val id: String,
    val label: String?,
    val recordedAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: AudioFormat,
    val sampleRateHz: Int,
)

enum class AudioFormat(val mime: String, val extension: String) {
    M4A_AAC("audio/mp4", "m4a"),
    WAV_PCM("audio/wav", "wav"),
}

/**
 * Font preset shared with the rest of the still family.
 */
enum class FontPreset { System, Editorial, Terminal, Grotesk }
