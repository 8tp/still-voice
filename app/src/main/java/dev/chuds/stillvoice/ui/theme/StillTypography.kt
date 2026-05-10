package dev.chuds.stillvoice.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.chuds.stillvoice.data.FontPreset

/**
 * Concrete typography values for the active font preset. Read via [StillTypography] inside
 * a Composable; provide via [LocalStillTypography] at the composition root.
 *
 * Roles tuned for a recorder surface:
 *   Kicker   — uppercase mono labels ("recordings", a section header above the list)
 *   Counter  — the huge serif elapsed-time clock (mm:ss) shown above the verb during a
 *              recording. ~64sp; the screen's only "loud" type.
 *   Title    — derived label of a recording row (timestamp + optional user name)
 *   Menu     — settings rows, the lowercase verbs in the recording bar (`record`, `stop`)
 *   Caption  — duration `mm:ss` and file size on each row, in mono
 *   Small    — secondary metadata (sample-rate / format hints in settings, status lines)
 */
data class StillTypographyValues(
    val Kicker: TextStyle,
    val Counter: TextStyle,
    val Title: TextStyle,
    val Menu: TextStyle,
    val Caption: TextStyle,
    val Small: TextStyle,
)

fun stillTypographyValues(
    serifFont: FontFamily,
    sansFont: FontFamily,
    monoFont: FontFamily,
): StillTypographyValues = StillTypographyValues(
    Kicker = TextStyle(
        fontFamily = monoFont,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.8.sp,
        fontWeight = FontWeight.Normal,
    ),
    Counter = TextStyle(
        fontFamily = serifFont,
        fontSize = 64.sp,
        lineHeight = 72.sp,
        letterSpacing = (-1.0).sp,
        fontWeight = FontWeight.Light,
    ),
    Title = TextStyle(
        fontFamily = sansFont,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Medium,
    ),
    Menu = TextStyle(
        fontFamily = sansFont,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Light,
    ),
    Caption = TextStyle(
        fontFamily = monoFont,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.7.sp,
        fontWeight = FontWeight.Normal,
    ),
    Small = TextStyle(
        fontFamily = sansFont,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
        fontWeight = FontWeight.Light,
    ),
)

fun stillTypographyFor(preset: FontPreset): StillTypographyValues = when (preset) {
    FontPreset.System -> stillTypographyValues(
        serifFont = FontFamily.Serif,
        sansFont = FontFamily.SansSerif,
        monoFont = FontFamily.Monospace,
    )
    FontPreset.Editorial -> stillTypographyValues(
        serifFont = StillFontFamilies.CormorantGaramond,
        sansFont = StillFontFamilies.Inter,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
    FontPreset.Terminal -> stillTypographyValues(
        serifFont = StillFontFamilies.IbmPlexMono,
        sansFont = StillFontFamilies.IbmPlexMono,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
    FontPreset.Grotesk -> stillTypographyValues(
        serifFont = StillFontFamilies.InstrumentSerif,
        sansFont = StillFontFamilies.SpaceGrotesk,
        monoFont = StillFontFamilies.IbmPlexMono,
    )
}

val LocalStillTypography = staticCompositionLocalOf {
    stillTypographyFor(FontPreset.System)
}

object StillTypography {
    val Kicker: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Kicker

    val Counter: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Counter

    val Title: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Title

    val Menu: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Menu

    val Caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Caption

    val Small: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStillTypography.current.Small
}
