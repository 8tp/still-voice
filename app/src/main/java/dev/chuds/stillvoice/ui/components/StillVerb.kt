package dev.chuds.stillvoice.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chuds.stillvoice.ui.theme.StillColors
import dev.chuds.stillvoice.ui.theme.StillTypography

/**
 * A lowercase verb in the still vocabulary (`record`, `stop`, `cancel`, `back`).
 * No ripple, no underline, no icon. Just a tappable Text.
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = style,
        color = if (enabled) color else StillColors.DimGray,
        modifier = modifier
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
    )
}
