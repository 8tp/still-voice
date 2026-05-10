// Formatters used throughout the UI. Keep them in one place so the row
// caption, the foreground notification body, and the live counter all read
// the same way.
package dev.chuds.stillvoice.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * mm:ss for any non-negative millisecond duration, including durations longer
 * than an hour. 0 → "0:00", 59000 → "0:59", 60000 → "1:00", 3599000 → "59:59",
 * 3600000 → "60:00". Tested in dev.chuds.stillvoice.RoundTripCheck.
 */
fun formatMmSs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * "416 KB" / "1.2 MB" / "612 B" — short human size suitable for a row caption.
 */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    return if (mb < 10) "%.1f MB".format(mb) else "%.0f MB".format(mb)
}

/**
 * "Wed May 13 · 09:14" — the timestamp fallback for an unnamed recording row.
 */
fun formatTimestampRow(epochMs: Long): String {
    val day = SimpleDateFormat("EEE MMM d", Locale.getDefault()).format(Date(epochMs))
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
    return "$day · $clock"
}
