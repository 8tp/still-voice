package dev.chuds.stillvoice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.chuds.stillvoice.data.AudioFormat
import dev.chuds.stillvoice.data.FontPreset
import dev.chuds.stillvoice.data.VoiceSettings
import dev.chuds.stillvoice.ui.components.StillDivider
import dev.chuds.stillvoice.ui.components.StillMenuItem
import dev.chuds.stillvoice.ui.components.StillVerb
import dev.chuds.stillvoice.ui.theme.StillColors
import dev.chuds.stillvoice.ui.theme.StillTypography
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    settings: VoiceSettings,
    recordingCount: Int,
    onCycleFontPreset: () -> Unit,
    onCycleFormat: () -> Unit,
    onCycleSampleRate: () -> Unit,
    onBulkExport: () -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    var deleteArmed by remember { mutableStateOf(false) }
    LaunchedEffect(deleteArmed) {
        if (deleteArmed) {
            delay(4_000)
            deleteArmed = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
        ) {
            Text(
                text = "SETTINGS",
                style = StillTypography.Kicker,
                color = StillColors.Gray,
            )
            Text(
                text = if (recordingCount == 0) "library is empty" else
                    "$recordingCount " + if (recordingCount == 1) "recording" else "recordings",
                style = StillTypography.Caption,
                color = StillColors.DimGray,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(28.dp))
            StillMenuItem(
                title = "font",
                subtitle = settings.fontPreset.name.lowercase(),
                onClick = onCycleFontPreset,
            )
            StillDivider()
            StillMenuItem(
                title = "format",
                subtitle = formatLabel(settings.format) +
                    "  ·  aac is ~10× smaller; wav is uncompressed",
                onClick = onCycleFormat,
            )
            StillDivider()
            StillMenuItem(
                title = "sample rate",
                subtitle = sampleRateLabel(settings.sampleRateHz) +
                    "  ·  22.05 khz halves file size; voice is fine, music isn't",
                onClick = onCycleSampleRate,
            )
            StillDivider()
            StillMenuItem(
                title = "bulk export",
                subtitle = if (recordingCount == 0) "no recordings yet" else
                    "save every recording as one zip",
                enabled = recordingCount > 0,
                onClick = onBulkExport,
            )
            StillDivider()
            StillMenuItem(
                title = "delete all",
                subtitle = if (deleteArmed) "tap again to confirm" else
                    "remove every recording on disk",
                onClick = {
                    if (deleteArmed) {
                        deleteArmed = false
                        onDeleteAll()
                    } else {
                        deleteArmed = true
                    }
                },
                enabled = recordingCount > 0,
                titleColor = if (deleteArmed) StillColors.SoftWhite else StillColors.SoftWhite,
            )

            Spacer(Modifier.height(36.dp))
            Text(
                text = "PRIVACY POSTURE, IN CODE",
                style = StillTypography.Kicker,
                color = StillColors.Gray,
            )
            Spacer(Modifier.height(12.dp))
            PrivacyLine("AndroidManifest.xml", "four permissions, none for the network")
            PrivacyLine("xml/data_extraction_rules.xml", "excludes everything from cloud backup")
            PrivacyLine("xml/file_paths.xml", "FileProvider exposes only recordings/")
            PrivacyLine("app/build.gradle.kts", "AndroidX + Compose + DataStore only")

            Spacer(Modifier.height(36.dp))
            Text(
                text = "STILL ECOSYSTEM",
                style = StillTypography.Kicker,
                color = StillColors.Gray,
            )
            Spacer(Modifier.height(12.dp))
            EcosystemLine("launcher")
            EcosystemLine("notes")
            EcosystemLine("cal")

            Spacer(Modifier.height(64.dp))
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(StillColors.OledBlack)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            StillVerb(text = "back", onClick = onBack, color = StillColors.MutedWhite)
        }
    }
}

@Composable
private fun PrivacyLine(file: String, what: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = file,
            style = StillTypography.Caption,
            color = StillColors.MutedWhite,
        )
        Text(
            text = what,
            style = StillTypography.Caption,
            color = StillColors.DimGray,
        )
    }
}

@Composable
private fun EcosystemLine(name: String) {
    Text(
        text = name,
        style = StillTypography.Caption,
        color = StillColors.DimGray,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

private fun formatLabel(format: AudioFormat): String = when (format) {
    AudioFormat.M4A_AAC -> "m4a aac"
    AudioFormat.WAV_PCM -> "wav pcm"
}

private fun sampleRateLabel(hz: Int): String = when (hz) {
    22_050 -> "22.05 khz"
    else -> "44.1 khz"
}

fun cycleFont(current: FontPreset): FontPreset = when (current) {
    FontPreset.System -> FontPreset.Editorial
    FontPreset.Editorial -> FontPreset.Terminal
    FontPreset.Terminal -> FontPreset.Grotesk
    FontPreset.Grotesk -> FontPreset.System
}

fun cycleFormat(current: AudioFormat): AudioFormat = when (current) {
    AudioFormat.M4A_AAC -> AudioFormat.WAV_PCM
    AudioFormat.WAV_PCM -> AudioFormat.M4A_AAC
}

fun cycleSampleRate(current: Int): Int = when (current) {
    44_100 -> 22_050
    else -> 44_100
}
