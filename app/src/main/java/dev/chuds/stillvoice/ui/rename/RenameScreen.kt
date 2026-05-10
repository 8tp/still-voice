package dev.chuds.stillvoice.ui.rename

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chuds.stillvoice.data.Recording
import dev.chuds.stillvoice.data.formatTimestampRow
import dev.chuds.stillvoice.ui.components.StillVerb
import dev.chuds.stillvoice.ui.theme.StillColors
import dev.chuds.stillvoice.ui.theme.StillTypography

/**
 * Single BasicTextField for the user-typed label. Empty = null = restore the
 * timestamp fallback.
 */
@Composable
fun RenameScreen(
    recording: Recording,
    onSave: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember { mutableStateOf(recording.label.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StillColors.OledBlack)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 36.dp)) {
            Text(
                text = "RENAME",
                style = StillTypography.Kicker,
                color = StillColors.Gray,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = recording.label?.takeIf { it.isNotBlank() }
                    ?: formatTimestampRow(recording.recordedAt),
                style = StillTypography.Caption,
                color = StillColors.DimGray,
            )
            Spacer(Modifier.height(28.dp))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = StillTypography.Title.copy(color = StillColors.SoftWhite),
                cursorBrush = SolidColor(StillColors.SoftWhite),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                text = formatTimestampRow(recording.recordedAt),
                                style = StillTypography.Title,
                                color = StillColors.DimGray,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        Row(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StillVerb(
                text = "cancel",
                onClick = onCancel,
                color = StillColors.MutedWhite,
                bordered = true,
            )
            StillVerb(
                text = "save",
                onClick = { onSave(draft.trim().takeIf { it.isNotEmpty() }) },
                bordered = true,
            )
        }
    }
}
