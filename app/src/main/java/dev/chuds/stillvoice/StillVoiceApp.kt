package dev.chuds.stillvoice

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chuds.stillvoice.data.AudioFormat
import dev.chuds.stillvoice.data.PreferencesRepository
import dev.chuds.stillvoice.data.RecordingsRepository
import dev.chuds.stillvoice.data.VoiceSettings
import dev.chuds.stillvoice.data.buildShareIntent
import dev.chuds.stillvoice.data.timestampLabel
import dev.chuds.stillvoice.data.writeFileToUri
import dev.chuds.stillvoice.data.writeZipToUri
import dev.chuds.stillvoice.player.LocalPlayerController
import dev.chuds.stillvoice.player.PlayerController
import dev.chuds.stillvoice.recorder.LocalRecorderController
import dev.chuds.stillvoice.recorder.RecorderController
import dev.chuds.stillvoice.recorder.RecorderState
import dev.chuds.stillvoice.ui.list.RecordingsListScreen
import dev.chuds.stillvoice.ui.rename.RenameScreen
import dev.chuds.stillvoice.ui.settings.SettingsScreen
import dev.chuds.stillvoice.ui.settings.cycleFont
import dev.chuds.stillvoice.ui.settings.cycleFormat
import dev.chuds.stillvoice.ui.settings.cycleSampleRate
import dev.chuds.stillvoice.ui.theme.LocalStillTypography
import dev.chuds.stillvoice.ui.theme.stillTypographyFor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Top-level composable. Hand-rolled router: list / rename / settings.
 * Mirrors StillNotesApp.kt; owns the SAF launchers for export and bulk export.
 */
@Composable
fun StillVoiceApp(initialOpenRecordingState: Boolean = false) {
    val context = LocalContext.current.applicationContext
    val activityContext = LocalContext.current
    val scope = rememberCoroutineScope()

    val recordingsRepository = remember(context) { RecordingsRepository(context) }
    val preferencesRepository = remember(context) { PreferencesRepository(context) }
    val playerController = remember(context) { PlayerController(recordingsRepository) }
    val recorderController = remember(context, playerController) {
        RecorderController(context, playerController)
    }

    LaunchedEffect(Unit) { recordingsRepository.load() }

    val recordings by recordingsRepository.recordings.collectAsState()
    val recorderState by recorderController.state.collectAsStateWithLifecycle()
    val playback by playerController.state.collectAsStateWithLifecycle()

    val settingsState = remember(preferencesRepository) {
        preferencesRepository.settings.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = VoiceSettings(),
        )
    }
    val settings by settingsState.collectAsStateWithLifecycle()

    var route by remember { mutableStateOf<Route>(Route.List) }
    var pendingRecordTrigger by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(recorderController.hasMicPermission()) }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (granted && pendingRecordTrigger) {
            recorderController.start(settings.format, settings.sampleRateHz)
        } else if (!granted) {
            Toast.makeText(activityContext, "mic permission needed to record", Toast.LENGTH_SHORT).show()
        }
        pendingRecordTrigger = false
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* user can deny; service still posts foreground type, system may suppress */ }

    LaunchedEffect(Unit) {
        // Ask for notification permission on first launch (Android 13+).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // SAF launchers.
    var exportTarget by remember { mutableStateOf<ExportTarget?>(null) }

    val exportSingleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target is ExportTarget.Single) {
            scope.launch {
                val recording = recordingsRepository.recordings.value.firstOrNull { it.id == target.id }
                if (recording != null) {
                    val file = recordingsRepository.recordingFile(recording)
                    if (file.exists() && writeFileToUri(activityContext, uri, file)) {
                        Toast.makeText(activityContext, "exported", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target is ExportTarget.All) {
            scope.launch {
                if (writeZipToUri(activityContext, uri, recordingsRepository)) {
                    Toast.makeText(activityContext, "exported all recordings", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    fun startSingleExport(id: String) {
        val recording = recordings.firstOrNull { it.id == id } ?: return
        val baseName = recordingsRepository.deriveBaseName(recording)
        val safe = baseName.replace(Regex("[^A-Za-z0-9._\\- ]"), "").trim().replace(' ', '-')
            .ifEmpty { "recording" }
        exportTarget = ExportTarget.Single(id)
        exportSingleLauncher.launch("$safe.${recording.format.extension}")
    }

    fun startBulkExport() {
        exportTarget = ExportTarget.All
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        exportZipLauncher.launch("still-voice-$stamp.zip")
    }

    fun shareRecordingById(id: String) {
        val recording = recordings.firstOrNull { it.id == id } ?: return
        val file = recordingsRepository.recordingFile(recording)
        if (!file.exists()) return
        val intent = buildShareIntent(activityContext, recording, file)
        runCatching {
            activityContext.startActivity(Intent.createChooser(intent, "share recording"))
        }
    }

    BackHandler(enabled = route !is Route.List) {
        route = Route.List
    }

    LaunchedEffect(initialOpenRecordingState) {
        // Notification tap intent — nothing to do beyond ensuring we're on the list,
        // which is the default route.
        route = Route.List
    }

    val typography = remember(settings.fontPreset) { stillTypographyFor(settings.fontPreset) }

    CompositionLocalProvider(
        LocalStillTypography provides typography,
        LocalRecorderController provides recorderController,
        LocalPlayerController provides playerController,
    ) {
        when (val current = route) {
            Route.List -> {
                RecordingsListScreen(
                    recordings = recordings,
                    recorderState = recorderState,
                    playback = playback,
                    micPermissionGranted = micGranted,
                    onRecord = {
                        if (recorderController.hasMicPermission()) {
                            micGranted = true
                            recorderController.start(settings.format, settings.sampleRateHz)
                        } else {
                            pendingRecordTrigger = true
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopRecording = { recorderController.stop() },
                    onPlay = { recording -> playerController.play(recording) },
                    onStopPlayback = { playerController.stop() },
                    onSeek = { recording, fraction ->
                        val total = playback.durationMs.takeIf { it > 0 } ?: recording.durationMs
                        if (total > 0) playerController.seek((fraction * total).toLong())
                    },
                    onRename = { id -> route = Route.Rename(id) },
                    onShare = ::shareRecordingById,
                    onExport = ::startSingleExport,
                    onDelete = { id ->
                        scope.launch { recordingsRepository.delete(id) }
                    },
                    onSettings = { route = Route.Settings },
                )
            }

            is Route.Rename -> {
                val recording = recordings.firstOrNull { it.id == current.id }
                if (recording == null) {
                    LaunchedEffect(current.id) { route = Route.List }
                } else {
                    RenameScreen(
                        recording = recording,
                        onSave = { newLabel ->
                            scope.launch { recordingsRepository.rename(current.id, newLabel) }
                            route = Route.List
                        },
                        onCancel = { route = Route.List },
                    )
                }
            }

            Route.Settings -> {
                SettingsScreen(
                    settings = settings,
                    recordingCount = recordings.size,
                    onCycleFontPreset = {
                        scope.launch {
                            preferencesRepository.setFontPreset(cycleFont(settings.fontPreset))
                        }
                    },
                    onCycleFormat = {
                        scope.launch {
                            preferencesRepository.setFormat(cycleFormat(settings.format))
                        }
                    },
                    onCycleSampleRate = {
                        scope.launch {
                            preferencesRepository.setSampleRate(cycleSampleRate(settings.sampleRateHz))
                        }
                    },
                    onBulkExport = ::startBulkExport,
                    onDeleteAll = {
                        scope.launch {
                            recordingsRepository.deleteAll()
                            playerController.stop()
                        }
                    },
                    onBack = { route = Route.List },
                )
            }
        }
    }
}

private sealed interface Route {
    data object List : Route
    data class Rename(val id: String) : Route
    data object Settings : Route
}

private sealed interface ExportTarget {
    data class Single(val id: String) : ExportTarget
    data object All : ExportTarget
}
