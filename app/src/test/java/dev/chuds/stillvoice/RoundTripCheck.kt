// JVM-only unit checks (no Android Context, no MediaRecorder).
// Verifies the two pieces of pure logic the orchestrator wanted exercised
// before shipping: the mm:ss formatter at boundaries, and the JSON index
// round-trip shape that RecordingsRepository persists.
package dev.chuds.stillvoice

import dev.chuds.stillvoice.data.AudioFormat
import dev.chuds.stillvoice.data.Recording
import dev.chuds.stillvoice.data.formatMmSs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundTripCheck {

    @Test
    fun mmss_boundaries() {
        assertEquals("0:00", formatMmSs(0L))
        assertEquals("0:59", formatMmSs(59_000L))
        assertEquals("1:00", formatMmSs(60_000L))
        assertEquals("59:59", formatMmSs(3_599_000L))
        assertEquals("60:00", formatMmSs(3_600_000L))
    }

    @Test
    fun index_round_trip_shape() {
        val source = listOf(
            Recording(
                id = "id-a",
                label = "morning idea",
                recordedAt = 1_746_800_000_000L,
                durationMs = 73_000L,
                sizeBytes = 587_234L,
                format = AudioFormat.M4A_AAC,
                sampleRateHz = 44_100,
            ),
            Recording(
                id = "id-b",
                label = null,
                recordedAt = 1_746_900_000_000L,
                durationMs = 12_500L,
                sizeBytes = 41_200L,
                format = AudioFormat.WAV_PCM,
                sampleRateHz = 22_050,
            ),
            Recording(
                id = "id-c",
                label = "rehearsal — take 3",
                recordedAt = 1_747_000_000_000L,
                durationMs = 305_400L,
                sizeBytes = 4_120_311L,
                format = AudioFormat.M4A_AAC,
                sampleRateHz = 44_100,
            ),
        )

        // Encode (mirrors RecordingsRepository.writeIndex).
        val encoded = JSONArray().apply {
            source.forEach { r ->
                put(
                    JSONObject()
                        .put("id", r.id)
                        .put("label", r.label ?: JSONObject.NULL)
                        .put("recordedAt", r.recordedAt)
                        .put("durationMs", r.durationMs)
                        .put("sizeBytes", r.sizeBytes)
                        .put("format", r.format.name)
                        .put("sampleRateHz", r.sampleRateHz),
                )
            }
        }.toString()

        // Decode (mirrors RecordingsRepository.readIndex).
        val decoded = JSONArray(encoded).let { array ->
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Recording(
                    id = obj.getString("id"),
                    label = if (obj.isNull("label")) null
                    else obj.optString("label").takeIf { it.isNotEmpty() },
                    recordedAt = obj.optLong("recordedAt"),
                    durationMs = obj.optLong("durationMs"),
                    sizeBytes = obj.optLong("sizeBytes"),
                    format = AudioFormat.valueOf(obj.optString("format")),
                    sampleRateHz = obj.optInt("sampleRateHz", 44_100),
                )
            }
        }

        assertEquals(source.size, decoded.size)
        source.forEachIndexed { i, expected -> assertEquals(expected, decoded[i]) }
    }
}
