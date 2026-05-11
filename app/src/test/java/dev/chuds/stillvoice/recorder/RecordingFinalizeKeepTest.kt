package dev.chuds.stillvoice.recorder

import dev.chuds.stillvoice.data.AudioFormat
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingFinalizeKeepTest {

    @Test fun headerOnlyWavIsNotKept() {
        val dir = createTempDirectory("still-voice-keep-wav").toFile()
        try {
            val headerOnly = File(dir, "h.wav").apply { writeBytes(ByteArray(44)) }
            val withPayload = File(dir, "p.wav").apply { writeBytes(ByteArray(44 + 1)) }

            assertFalse(hasAudioPayload(headerOnly, AudioFormat.WAV_PCM))
            assertTrue(hasAudioPayload(withPayload, AudioFormat.WAV_PCM))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun tinyM4aIsNotKept() {
        val dir = createTempDirectory("still-voice-keep-m4a").toFile()
        try {
            val tiny = File(dir, "tiny.m4a").apply { writeBytes(ByteArray(1024)) }
            val realistic = File(dir, "real.m4a").apply { writeBytes(ByteArray(1025)) }

            assertFalse(hasAudioPayload(tiny, AudioFormat.M4A_AAC))
            assertTrue(hasAudioPayload(realistic, AudioFormat.M4A_AAC))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun missingFileIsNotKept() {
        val dir = createTempDirectory("still-voice-keep-missing").toFile()
        try {
            val missing = File(dir, "missing.wav")
            assertFalse(hasAudioPayload(missing, AudioFormat.WAV_PCM))
            assertFalse(hasAudioPayload(missing, AudioFormat.M4A_AAC))
        } finally {
            dir.deleteRecursively()
        }
    }
}
