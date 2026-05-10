package dev.chuds.stillvoice.recorder

/**
 * The state of the recording pipeline as observed by the Compose layer.
 *
 * Only three states. Failures don't surface as a fourth `Error` — they appear as
 * a Toast and a transition back to Idle (the user retries). This is the same
 * posture as still-notes' debounced autosave: simple states, recoverable
 * failures.
 */
sealed interface RecorderState {
    data object Idle : RecorderState

    data class Recording(
        val startedAtMs: Long,
        val currentSizeBytes: Long,
    ) : RecorderState

    data object Finalizing : RecorderState
}
