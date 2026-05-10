package dev.chuds.stillvoice.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chuds.stillvoice.data.formatMmSs
import dev.chuds.stillvoice.data.formatSize
import dev.chuds.stillvoice.recorder.RecorderState
import dev.chuds.stillvoice.ui.components.StillVerb
import dev.chuds.stillvoice.ui.theme.StillColors
import dev.chuds.stillvoice.ui.theme.StillTypography
import kotlinx.coroutines.delay

/**
 * Bottom bar bound to RecorderController.state. Idle = `record` + `settings`;
 * Recording = serif mm:ss counter + `stop`; Finalizing = dimmed `saving`.
 */
@Composable
fun RecordingBar(
    state: RecorderState,
    micGranted: Boolean,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is RecorderState.Recording -> {
                // 1Hz ticker for the live counter.
                var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(state.startedAtMs) {
                    while (true) {
                        nowMs = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                val elapsedMs = (nowMs - state.startedAtMs).coerceAtLeast(0)
                Text(
                    text = formatMmSs(elapsedMs),
                    style = StillTypography.Counter,
                    color = StillColors.SoftWhite,
                )
                Text(
                    text = formatSize(state.currentSizeBytes),
                    style = StillTypography.Caption,
                    color = StillColors.DimGray,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                StillVerb(text = "stop", onClick = onStop)
            }
            RecorderState.Finalizing -> {
                Text(
                    text = "saving",
                    style = StillTypography.Menu,
                    color = StillColors.DimGray,
                )
            }
            RecorderState.Idle -> {
                // record is the focal action and stays truly centered via Box;
                // settings sits on the right as a bordered secondary verb so
                // it reads as a button without competing with record.
                Box(modifier = Modifier.fillMaxWidth()) {
                    StillVerb(
                        text = "record",
                        onClick = onRecord,
                        color = StillColors.SoftWhite,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    StillVerb(
                        text = "settings",
                        onClick = onSettings,
                        style = StillTypography.Caption,
                        color = StillColors.MutedWhite,
                        bordered = true,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                if (!micGranted) {
                    Text(
                        text = "mic permission required",
                        style = StillTypography.Caption,
                        color = StillColors.DimGray,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
