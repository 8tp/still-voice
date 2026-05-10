package dev.chuds.stillvoice.recorder

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide bridge between RecordingService and RecorderController. The
 * service owns the MediaRecorder; the controller owns the StateFlow that the
 * Compose layer subscribes to. This object is the seam between them so we
 * don't pay the cost of binding / IBinder for an in-process service.
 *
 * Why a singleton object and not a bound service: the spec wants the service
 * to "communicate via a MutableSharedFlow". A bound service would buy the same
 * thing at a heavier cost. Same posture as still-notes' NotesRepository —
 * one instance per process, no DI framework.
 */
object RecorderBus {
    val state: MutableStateFlow<RecorderState> = MutableStateFlow(RecorderState.Idle)

    /**
     * Emitted by the service after a recording is finalized successfully. The
     * controller observes this to fire a callback the Compose layer cares
     * about (e.g. scrolling the list to the new row).
     */
    val finalized: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 8)
}
