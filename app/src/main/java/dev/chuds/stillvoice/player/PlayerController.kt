// PlayerController — owns a single MediaPlayer instance and emits 60Hz
// position updates while playing.
//
// Mutually exclusive with the recorder: starting a recording stops a current
// playback (enforced in RecorderController.start). Only one row in the list
// can play at a time, enforced here by re-using one MediaPlayer across plays.
package dev.chuds.stillvoice.player

import android.media.MediaPlayer
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chuds.stillvoice.data.Recording
import dev.chuds.stillvoice.data.RecordingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackState(
    val playingId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

class PlayerController(private val repository: RecordingsRepository) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope: CoroutineScope = MainScope()
    private var ticker: Job? = null
    private var player: MediaPlayer? = null

    fun play(recording: Recording) {
        // Stop anything currently playing before starting the next one.
        stop()
        val file = repository.recordingFile(recording)
        if (!file.exists()) return

        val mp = MediaPlayer()
        mp.setOnCompletionListener {
            // Reset to resting state when playback finishes naturally.
            stop()
        }
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.start()
        } catch (t: Throwable) {
            runCatching { mp.release() }
            return
        }
        player = mp
        _state.value = PlaybackState(
            playingId = recording.id,
            positionMs = 0L,
            durationMs = mp.duration.toLong().coerceAtLeast(recording.durationMs),
        )

        ticker = scope.launch {
            while (true) {
                val current = player ?: break
                val pos = runCatching { current.currentPosition }.getOrDefault(0).toLong()
                val dur = runCatching { current.duration }.getOrDefault(0).toLong()
                _state.value = _state.value.copy(
                    positionMs = pos,
                    durationMs = if (dur > 0) dur else _state.value.durationMs,
                )
                delay(60)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        _state.value = PlaybackState()
    }

    fun seek(positionMs: Long) {
        val mp = player ?: return
        runCatching { mp.seekTo(positionMs.toInt()) }
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun release() {
        stop()
        scope.cancel()
    }
}

val LocalPlayerController = staticCompositionLocalOf<PlayerController> {
    error("PlayerController not provided")
}
