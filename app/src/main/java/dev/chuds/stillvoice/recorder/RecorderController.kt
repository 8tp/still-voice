package dev.chuds.stillvoice.recorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import dev.chuds.stillvoice.data.AudioFormat
import dev.chuds.stillvoice.player.PlayerController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton-ish facade for starting and stopping the foreground recording
 * service. Held in a CompositionLocal (LocalRecorderController) for Compose
 * access — same shape as how still-notes holds its NotesRepository.
 *
 * Communicates with the service via two channels:
 *   service → controller : RecorderBus (MutableStateFlow + MutableSharedFlow)
 *   controller → service : Intent extras
 */
class RecorderController(
    private val appContext: Context,
    private val playerController: PlayerController,
) {
    val state: StateFlow<RecorderState> = RecorderBus.state

    private val _denials = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val denials: SharedFlow<String> = _denials.asSharedFlow()

    val finalized: SharedFlow<String> = RecorderBus.finalized.asSharedFlow()

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Fire-and-forget start. The service does the heavy lifting; this just
     * builds the intent. The Compose layer is expected to have requested the
     * mic permission before reaching here.
     */
    fun start(format: AudioFormat, sampleRateHz: Int) {
        if (state.value !is RecorderState.Idle) return
        // Player and recorder are mutually exclusive.
        playerController.stop()

        val intent = Intent(appContext, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
            putExtra(RecordingService.EXTRA_FORMAT, format.name)
            putExtra(RecordingService.EXTRA_SAMPLE_RATE_HZ, sampleRateHz)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stop() {
        if (state.value !is RecorderState.Recording) return
        val intent = Intent(appContext, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        // Use startService for the stop action; the service will stopSelf.
        appContext.startService(intent)
    }

    /**
     * Toast plumbing — RecorderController doesn't have a UI of its own, so
     * the Compose layer subscribes to denials and surfaces them via Toast.
     */
    suspend fun emitDenial(message: String) {
        _denials.emit(message)
    }
}

/**
 * CompositionLocal for the process-wide RecorderController. Provided at the
 * StillVoiceApp root.
 */
val LocalRecorderController = staticCompositionLocalOf<RecorderController> {
    error("RecorderController not provided")
}
