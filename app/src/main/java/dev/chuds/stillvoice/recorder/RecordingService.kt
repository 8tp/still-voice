// RecordingService — the foreground service that owns the MediaRecorder for
// the lifetime of an active recording. Posts the only notification this app
// ever posts: a sticky `recording` notification with a live mm:ss body and a
// `stop` action.
//
// Resolution of spec §15 open questions, recorded here per the build prompt:
//   §15.1 pause-and-resume:    DEFERRED to v0.2 — keeps the state surface to
//                              three states and avoids the partial-file
//                              question on process kill while paused.
//   §15.2 inline waveform:     DEFERRED — getMaxAmplitude() polling adds a
//                              ticker we don't otherwise need; the scrubber
//                              during playback is enough texture.
//   §15.3 auto-delete-after:   DEFERRED — silently deleting voice would
//                              violate the still pact's posture.
//   §15.4 WAV path:            SHIPPED via AudioRecord + a small WAV writer
//                              (see WavRecorder.kt). Both formats are
//                              available in v0.1.
package dev.chuds.stillvoice.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.chuds.stillvoice.MainActivity
import dev.chuds.stillvoice.R
import dev.chuds.stillvoice.data.AudioFormat
import dev.chuds.stillvoice.data.Recording
import dev.chuds.stillvoice.data.RecordingsRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private var mediaRecorder: MediaRecorder? = null
    private var wavRecorder: WavRecorder? = null

    private var currentId: String? = null
    private var currentFile: File? = null
    private var currentFormat: AudioFormat = AudioFormat.M4A_AAC
    private var currentSampleRate: Int = 44_100
    private var startedAtMs: Long = 0L

    private lateinit var repository: RecordingsRepository

    override fun onCreate() {
        super.onCreate()
        repository = RecordingsRepository(applicationContext)
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                if (mediaRecorder == null && wavRecorder == null) {
                    val format = runCatching {
                        AudioFormat.valueOf(
                            intent.getStringExtra(EXTRA_FORMAT) ?: AudioFormat.M4A_AAC.name,
                        )
                    }.getOrDefault(AudioFormat.M4A_AAC)
                    val sampleRateHz = intent.getIntExtra(EXTRA_SAMPLE_RATE_HZ, 44_100)
                    startRecording(format, sampleRateHz)
                }
            }
            ACTION_STOP_RECORDING -> {
                finalizeAndStop()
            }
            else -> {
                if (mediaRecorder == null && wavRecorder == null) {
                    // Service started without an action and nothing in flight.
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(format: AudioFormat, sampleRateHz: Int) {
        val id = UUID.randomUUID().toString()
        val file = repository.fileFor(id, format)
        currentId = id
        currentFile = file
        currentFormat = format
        currentSampleRate = sampleRateHz
        startedAtMs = System.currentTimeMillis()

        startForeground(NOTIF_ID, buildNotification(elapsedSeconds = 0))

        try {
            when (format) {
                AudioFormat.M4A_AAC -> {
                    val recorder = newMediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(96_000)
                        setAudioSamplingRate(sampleRateHz)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    mediaRecorder = recorder
                }
                AudioFormat.WAV_PCM -> {
                    val recorder = WavRecorder(file, sampleRateHz)
                    recorder.start()
                    wavRecorder = recorder
                }
            }
        } catch (t: Throwable) {
            // Couldn't start the recorder — release everything and bail.
            releaseAndDeletePartial()
            RecorderBus.state.value = RecorderState.Idle
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        RecorderBus.state.value = RecorderState.Recording(
            startedAtMs = startedAtMs,
            currentSizeBytes = 0,
        )
        startTicker()
    }

    private fun finalizeAndStop() {
        val id = currentId
        val file = currentFile
        val format = currentFormat
        val sampleRate = currentSampleRate

        // Stop ticker first so the state flow doesn't keep emitting Recording.
        tickerJob?.cancel()
        tickerJob = null

        RecorderBus.state.value = RecorderState.Finalizing

        var keep = false
        try {
            mediaRecorder?.let { rec ->
                runCatching {
                    rec.stop()
                    keep = true
                }
                runCatching { rec.release() }
            }
            wavRecorder?.let { rec ->
                runCatching {
                    rec.stop()
                    keep = true
                }
            }
        } finally {
            mediaRecorder = null
            wavRecorder = null
        }

        if (id != null && file != null && keep && file.exists() && file.length() > 0) {
            val durationMs = readDurationMs(file) ?: (System.currentTimeMillis() - startedAtMs)
            val recording = Recording(
                id = id,
                label = null,
                recordedAt = startedAtMs,
                durationMs = durationMs,
                sizeBytes = file.length(),
                format = format,
                sampleRateHz = sampleRate,
            )
            persistFinalized(recording)
        } else {
            // stop() too soon after start, or a write failure — drop the partial file.
            file?.delete()
            RecorderBus.state.value = RecorderState.Idle
        }

        currentId = null
        currentFile = null
        startedAtMs = 0L

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun persistFinalized(recording: Recording) {
        runCatching {
            // Must complete before stopSelf(): onDestroy() cancels the service scope.
            runBlocking {
                repository.adopt(recording)
                RecorderBus.finalized.emit(recording.id)
            }
        }
        RecorderBus.state.value = RecorderState.Idle
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                val elapsedMs = System.currentTimeMillis() - startedAtMs
                val sizeBytes = currentFile?.takeIf { it.exists() }?.length() ?: 0L
                RecorderBus.state.value = RecorderState.Recording(
                    startedAtMs = startedAtMs,
                    currentSizeBytes = sizeBytes,
                )
                updateNotification((elapsedMs / 1000).toInt())
                delay(1_000)
            }
        }
    }

    private fun updateNotification(elapsedSeconds: Int) {
        val notification = buildNotification(elapsedSeconds)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    private fun buildNotification(elapsedSeconds: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = ACTION_OPEN_FROM_NOTIFICATION
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_still_voice_notification)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(formatMmSs(elapsedSeconds))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setContentIntent(openPending)
            .addAction(
                0,
                getString(R.string.recording_notification_action_stop),
                stopPending,
            )
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.recording_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun newMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun readDurationMs(file: File): Long? {
        // For WAV the metadata retriever can fail; fall back to wall-clock if so.
        return runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                mmr.release()
            }
        }.getOrNull()
    }

    private fun releaseAndDeletePartial() {
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        runCatching { wavRecorder?.cancel() }
        wavRecorder = null
        currentFile?.delete()
        currentFile = null
        currentId = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Spec §7.4: keep recording when the user swipes the app from recents.
    }

    override fun onDestroy() {
        // Process-kill path: best-effort finalize so the next app open shows the recording.
        if (mediaRecorder != null || wavRecorder != null) {
            finalizeAndStop()
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "still_voice_recording"
        const val NOTIF_ID = 8201

        const val ACTION_START_RECORDING = "dev.chuds.stillvoice.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "dev.chuds.stillvoice.action.STOP_RECORDING"
        const val ACTION_OPEN_FROM_NOTIFICATION = "dev.chuds.stillvoice.action.OPEN_FROM_NOTIFICATION"

        const val EXTRA_FORMAT = "format"
        const val EXTRA_SAMPLE_RATE_HZ = "sample_rate_hz"

        fun formatMmSs(totalSeconds: Int): String {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
