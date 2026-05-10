package dev.chuds.stillvoice.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chuds.stillvoice.data.Recording
import dev.chuds.stillvoice.data.formatMmSs
import dev.chuds.stillvoice.data.formatSize
import dev.chuds.stillvoice.data.formatTimestampRow
import dev.chuds.stillvoice.player.PlaybackState
import dev.chuds.stillvoice.recorder.RecorderState
import dev.chuds.stillvoice.ui.components.StillDivider
import dev.chuds.stillvoice.ui.components.StillScrubber
import dev.chuds.stillvoice.ui.components.StillVerb
import dev.chuds.stillvoice.ui.theme.StillColors
import dev.chuds.stillvoice.ui.theme.StillTypography
import kotlinx.coroutines.delay

/**
 * Reverse-chronological list of recordings + the bottom-pinned RecordingBar.
 * Tap a row to play it inline; long-press a row for the action sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsListScreen(
    recordings: List<Recording>,
    recorderState: RecorderState,
    playback: PlaybackState,
    micPermissionGranted: Boolean,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onPlay: (Recording) -> Unit,
    onStopPlayback: () -> Unit,
    onSeek: (Recording, Float) -> Unit,
    onRename: (String) -> Unit,
    onShare: (String) -> Unit,
    onExport: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var actionTarget by remember { mutableStateOf<String?>(null) }
    var deletePendingId by remember { mutableStateOf<String?>(null) }

    // Reset the delete-confirm timer if the user backs off.
    LaunchedEffect(deletePendingId) {
        if (deletePendingId != null) {
            delay(4_000)
            deletePendingId = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            ListHeader(count = recordings.size)

            if (recordings.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = true),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    items(items = recordings, key = { it.id }) { recording ->
                        RecordingRow(
                            recording = recording,
                            isPlaying = playback.playingId == recording.id,
                            playback = playback,
                            deletePending = deletePendingId == recording.id,
                            onTap = {
                                if (playback.playingId == recording.id) {
                                    onStopPlayback()
                                } else {
                                    onPlay(recording)
                                }
                            },
                            onLongPress = {
                                actionTarget = recording.id
                            },
                            onSeek = { fraction -> onSeek(recording, fraction) },
                        )
                        StillDivider()
                    }
                }
            }

            RecordingBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                state = recorderState,
                micGranted = micPermissionGranted,
                onRecord = onRecord,
                onStop = onStopRecording,
                onSettings = onSettings,
            )
        }

        actionTarget?.let { id ->
            val recording = recordings.firstOrNull { it.id == id }
            if (recording == null) {
                actionTarget = null
            } else {
                ActionSheet(
                    recording = recording,
                    deletePending = deletePendingId == id,
                    onRename = {
                        actionTarget = null
                        onRename(id)
                    },
                    onShare = {
                        actionTarget = null
                        onShare(id)
                    },
                    onExport = {
                        actionTarget = null
                        onExport(id)
                    },
                    onDelete = {
                        if (deletePendingId == id) {
                            deletePendingId = null
                            actionTarget = null
                            onDelete(id)
                        } else {
                            deletePendingId = id
                        }
                    },
                    onDismiss = { actionTarget = null },
                )
            }
        }
    }
}

@Composable
private fun ListHeader(count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 12.dp),
    ) {
        Text(
            text = "RECORDINGS",
            style = StillTypography.Kicker,
            color = StillColors.Gray,
        )
        Text(
            text = if (count == 0) "nothing recorded yet" else
                "$count " + if (count == 1) "in your library" else "in your library",
            style = StillTypography.Caption,
            color = StillColors.DimGray,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp, bottom = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "nothing recorded",
            style = StillTypography.Caption,
            color = StillColors.DimGray,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    recording: Recording,
    isPlaying: Boolean,
    playback: PlaybackState,
    deletePending: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .padding(vertical = 14.dp),
    ) {
        // Top line: marker + derived label.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(StillColors.SoftWhite),
                )
                Spacer(Modifier.width(8.dp))
            }
            val label = recording.label?.takeIf { it.isNotBlank() }
                ?: formatTimestampRow(recording.recordedAt)
            Text(
                text = label,
                style = StillTypography.Title,
                color = StillColors.SoftWhite,
            )
        }

        if (isPlaying) {
            Spacer(Modifier.height(8.dp))
            val total = playback.durationMs.takeIf { it > 0 }
                ?: recording.durationMs.takeIf { it > 0 }
                ?: 1L
            val progress = (playback.positionMs.toFloat() / total.toFloat())
                .coerceIn(0f, 1f)
            StillScrubber(
                progress = progress,
                onSeek = onSeek,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${formatMmSs(playback.positionMs)} / ${formatMmSs(total)}   ·   stop",
                style = StillTypography.Caption,
                color = StillColors.MutedWhite,
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (deletePending) "tap delete again to confirm" else
                    "${formatMmSs(recording.durationMs)}   ·   ${formatSize(recording.sizeBytes)}",
                style = StillTypography.Caption,
                color = if (deletePending) StillColors.SoftWhite else StillColors.DimGray,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionSheet(
    recording: Recording,
    deletePending: Boolean,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack.copy(alpha = 0.94f))
            .combinedClickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            val label = recording.label?.takeIf { it.isNotBlank() }
                ?: formatTimestampRow(recording.recordedAt)
            Text(
                text = label,
                style = StillTypography.Caption,
                color = StillColors.DimGray,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            ActionRow("rename", onRename)
            ActionRow("share", onShare)
            ActionRow("export", onExport)
            ActionRow(
                label = if (deletePending) "delete · tap again to confirm" else "delete",
                onClick = onDelete,
            )
            Spacer(Modifier.height(6.dp))
            StillVerb(
                text = "cancel",
                onClick = onDismiss,
                color = StillColors.Gray,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = label,
        style = StillTypography.Menu,
        color = StillColors.SoftWhite,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
    )
}
