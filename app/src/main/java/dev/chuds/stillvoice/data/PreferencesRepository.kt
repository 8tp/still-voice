package dev.chuds.stillvoice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "still_voice_settings",
)

private val FONT_PRESET_KEY = stringPreferencesKey("font_preset")
private val FORMAT_KEY = stringPreferencesKey("format")
private val SAMPLE_RATE_KEY = intPreferencesKey("sample_rate_hz")

data class VoiceSettings(
    val fontPreset: FontPreset = FontPreset.System,
    val format: AudioFormat = AudioFormat.M4A_AAC,
    val sampleRateHz: Int = 44_100,
)

class PreferencesRepository(private val context: Context) {

    val settings: Flow<VoiceSettings> = context.preferencesDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            VoiceSettings(
                fontPreset = prefs[FONT_PRESET_KEY]
                    ?.let { runCatching { FontPreset.valueOf(it) }.getOrNull() }
                    ?: FontPreset.System,
                format = prefs[FORMAT_KEY]
                    ?.let { runCatching { AudioFormat.valueOf(it) }.getOrNull() }
                    ?: AudioFormat.M4A_AAC,
                sampleRateHz = prefs[SAMPLE_RATE_KEY] ?: 44_100,
            )
        }

    suspend fun setFontPreset(preset: FontPreset) {
        context.preferencesDataStore.edit { it[FONT_PRESET_KEY] = preset.name }
    }

    suspend fun setFormat(format: AudioFormat) {
        context.preferencesDataStore.edit { it[FORMAT_KEY] = format.name }
    }

    suspend fun setSampleRate(hz: Int) {
        context.preferencesDataStore.edit { it[SAMPLE_RATE_KEY] = hz }
    }
}
