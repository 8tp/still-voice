package dev.chuds.stillvoice.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chuds.stillvoice.ui.theme.StillColors
import dev.chuds.stillvoice.ui.theme.StillTypography

/**
 * Process-wide haptic performer. Bound at the app root to either a real
 * HapticFeedback call or a no-op based on the user's preference. Centralizing
 * it here means individual verbs don't need to thread the preference through.
 */
val LocalHaptics = staticCompositionLocalOf<() -> Unit> { { /* no-op default */ } }

/**
 * A lowercase verb in the still vocabulary (`record`, `stop`, `cancel`, `back`).
 * No ripple, no underline, no icon. Just a tappable Text.
 *
 * `bordered = true` draws a 1dp Hairline rectangle around the verb so it reads
 * as a button — used for persistent bottom-bar verbs (settings/back). The
 * record/stop focal action and verbs nested inside row taps stay borderless.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StillVerb(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = StillTypography.Menu,
    color: Color = StillColors.SoftWhite,
    enabled: Boolean = true,
    bordered: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHaptics.current
    Text(
        text = text,
        style = style,
        color = if (enabled) color else StillColors.DimGray,
        modifier = modifier
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics()
                    onClick()
                },
            )
            .then(
                if (bordered) Modifier.border(1.dp, StillColors.Hairline, RectangleShape)
                else Modifier
            )
            .padding(
                horizontal = if (bordered) 14.dp else 0.dp,
                vertical = if (bordered) 10.dp else 6.dp,
            ),
    )
}
