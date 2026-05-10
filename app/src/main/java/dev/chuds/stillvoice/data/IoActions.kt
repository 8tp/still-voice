package dev.chuds.stillvoice.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helpers for the SAF flows wired up in StillVoiceApp. Same shape as still-notes'
 * IoActions — keep the Compose layer free of ContentResolver mechanics.
 */

suspend fun writeFileToUri(
    context: Context,
    uri: Uri,
    source: File,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out ->
            source.inputStream().use { input ->
                input.copyTo(out)
            }
        } ?: return@runCatching false
        true
    }.getOrElse {
        toastOnMain(context, "export failed")
        false
    }
}

suspend fun writeZipToUri(
    context: Context,
    uri: Uri,
    repository: RecordingsRepository,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            repository.exportZip(stream)
        } ?: return@runCatching false
        true
    }.getOrElse {
        toastOnMain(context, "bulk export failed")
        false
    }
}

private fun toastOnMain(context: Context, message: String) {
    android.os.Handler(context.mainLooper).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Build a share intent for a recording. The FileProvider authority is
 * `${applicationId}.fileprovider` and exposes only the recordings/ subdir.
 * Caller wraps with Intent.createChooser.
 */
fun buildShareIntent(context: Context, recording: Recording, file: File): Intent {
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    return Intent(Intent.ACTION_SEND).apply {
        type = recording.format.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
