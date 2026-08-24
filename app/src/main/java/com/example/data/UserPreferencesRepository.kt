package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val globalKeySignature: String = "",
    val bpm: Int = 0,
    val pitchShiftSemitones: Int = 0,
    val playbackSpeedMultiplier: Float = 1.0f,
    val detectionMode: String = "Polyphonic DSP Chord Detection",
    val tuningStandard: String = "A440 (Standard Pitch)",
    val audioSampleRate: Int = 48000,
    val stemSeparationMode: String = "High Quality (4-Stems)",
    val activeModuleId: String = "workstation",
    val selectedNavigationTab: String = "dashboard",
    val lastActiveProjectTitle: String = "",
    val hasUnsavedSession: Boolean = false,
    val shouldShowResumeDialog: Boolean = false,
    val unsavedSessionTitle: String = "",
    val unsavedSessionBpm: Int = 0,
    val unsavedSessionKey: String = "",
    val unsavedSessionChords: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val GLOBAL_KEY_SIGNATURE = stringPreferencesKey("global_key_signature")
        val BPM = intPreferencesKey("bpm")
        val PITCH_SHIFT_SEMITONES = intPreferencesKey("pitch_shift_semitones")
        val PLAYBACK_SPEED_MULTIPLIER = floatPreferencesKey("playback_speed_multiplier")
        val DETECTION_MODE = stringPreferencesKey("detection_mode")
        val TUNING_STANDARD = stringPreferencesKey("tuning_standard")
        val AUDIO_SAMPLE_RATE = intPreferencesKey("audio_sample_rate")
        val STEM_SEPARATION_MODE = stringPreferencesKey("stem_separation_mode")

        val ACTIVE_MODULE_ID = stringPreferencesKey("active_module_id")
        val SELECTED_NAVIGATION_TAB = stringPreferencesKey("selected_navigation_tab")
        val LAST_ACTIVE_PROJECT_TITLE = stringPreferencesKey("last_active_project_title")

        val HAS_UNSAVED_SESSION = booleanPreferencesKey("has_unsaved_session")
        val SHOULD_SHOW_RESUME_DIALOG = booleanPreferencesKey("should_show_resume_dialog")
        val UNSAVED_SESSION_TITLE = stringPreferencesKey("unsaved_session_title")
        val UNSAVED_SESSION_BPM = intPreferencesKey("unsaved_session_bpm")
        val UNSAVED_SESSION_KEY = stringPreferencesKey("unsaved_session_key")
        val UNSAVED_SESSION_CHORDS = stringPreferencesKey("unsaved_session_chords")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            UserPreferences(
                globalKeySignature = preferences[PreferencesKeys.GLOBAL_KEY_SIGNATURE] ?: "",
                bpm = preferences[PreferencesKeys.BPM] ?: 0,
                pitchShiftSemitones = preferences[PreferencesKeys.PITCH_SHIFT_SEMITONES] ?: 0,
                playbackSpeedMultiplier = preferences[PreferencesKeys.PLAYBACK_SPEED_MULTIPLIER] ?: 1.0f,
                detectionMode = preferences[PreferencesKeys.DETECTION_MODE] ?: "Polyphonic DSP Chord Detection",
                tuningStandard = preferences[PreferencesKeys.TUNING_STANDARD] ?: "A440 (Standard Pitch)",
                audioSampleRate = preferences[PreferencesKeys.AUDIO_SAMPLE_RATE] ?: 48000,
                stemSeparationMode = preferences[PreferencesKeys.STEM_SEPARATION_MODE] ?: "High Quality (4-Stems)",
                activeModuleId = preferences[PreferencesKeys.ACTIVE_MODULE_ID] ?: "workstation",
                selectedNavigationTab = preferences[PreferencesKeys.SELECTED_NAVIGATION_TAB] ?: "dashboard",
                lastActiveProjectTitle = preferences[PreferencesKeys.LAST_ACTIVE_PROJECT_TITLE] ?: "",
                hasUnsavedSession = preferences[PreferencesKeys.HAS_UNSAVED_SESSION] ?: false,
                shouldShowResumeDialog = preferences[PreferencesKeys.SHOULD_SHOW_RESUME_DIALOG] ?: false,
                unsavedSessionTitle = preferences[PreferencesKeys.UNSAVED_SESSION_TITLE] ?: "",
                unsavedSessionBpm = preferences[PreferencesKeys.UNSAVED_SESSION_BPM] ?: 0,
                unsavedSessionKey = preferences[PreferencesKeys.UNSAVED_SESSION_KEY] ?: "",
                unsavedSessionChords = preferences[PreferencesKeys.UNSAVED_SESSION_CHORDS] ?: ""
            )
        }

    suspend fun updatePitchShiftSemitones(semitones: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PITCH_SHIFT_SEMITONES] = semitones
        }
    }

    suspend fun updatePlaybackSpeedMultiplier(multiplier: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYBACK_SPEED_MULTIPLIER] = multiplier
        }
    }

    suspend fun updateGlobalKeySignature(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GLOBAL_KEY_SIGNATURE] = key
        }
    }

    suspend fun updateBpm(bpm: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BPM] = bpm
        }
    }

    suspend fun updateDetectionMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DETECTION_MODE] = mode
        }
    }

    suspend fun updateTuningStandard(tuning: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TUNING_STANDARD] = tuning
        }
    }

    suspend fun updateAudioSampleRate(sampleRate: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUDIO_SAMPLE_RATE] = sampleRate
        }
    }

    suspend fun updateStemSeparationMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STEM_SEPARATION_MODE] = mode
        }
    }

    suspend fun updateActiveModuleId(moduleId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_MODULE_ID] = moduleId
        }
    }

    suspend fun updateSelectedNavigationTab(tab: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_NAVIGATION_TAB] = tab
        }
    }

    suspend fun updateLastActiveProjectTitle(title: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_ACTIVE_PROJECT_TITLE] = title
        }
    }

    suspend fun updateResumeDialogState(show: Boolean, hasUnsaved: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOULD_SHOW_RESUME_DIALOG] = show
            preferences[PreferencesKeys.HAS_UNSAVED_SESSION] = hasUnsaved
        }
    }

    suspend fun setUnsavedSessionData(
        title: String,
        bpm: Int,
        key: String,
        chords: String,
        hasUnsaved: Boolean = true,
        showDialog: Boolean = true
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNSAVED_SESSION_TITLE] = title
            preferences[PreferencesKeys.UNSAVED_SESSION_BPM] = bpm
            preferences[PreferencesKeys.UNSAVED_SESSION_KEY] = key
            preferences[PreferencesKeys.UNSAVED_SESSION_CHORDS] = chords
            preferences[PreferencesKeys.HAS_UNSAVED_SESSION] = hasUnsaved
            preferences[PreferencesKeys.SHOULD_SHOW_RESUME_DIALOG] = showDialog
        }
    }

    suspend fun clearResumeDialogTrigger() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOULD_SHOW_RESUME_DIALOG] = false
            preferences[PreferencesKeys.HAS_UNSAVED_SESSION] = false
        }
    }
}
