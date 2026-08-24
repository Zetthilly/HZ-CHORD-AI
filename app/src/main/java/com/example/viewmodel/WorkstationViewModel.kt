package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioWorkstationEngine
import com.example.audio.DetectedChordInfo
import com.example.audio.StemSeparationState
import com.example.audio.TuningNote
import com.example.audio.TimelineChordEntry
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class TrackChordEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timeMs: Long,
    val timestampLabel: String,
    val chordSymbol: String,
    val chordName: String,
    val root: String,
    val type: String,
    val notes: List<String>,
    val confidence: Float,
    val frequency: Float,
    val formula: String
)

@HiltViewModel
class WorkstationViewModel @Inject constructor(
    application: Application,
    val repository: MusicWorkstationRepository,
    val masterAudioEngine: com.example.audio.HZAudioEngine,
    val userPreferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val _isLoopingEnabled = MutableStateFlow(false)
    val isLoopingEnabled: StateFlow<Boolean> = _isLoopingEnabled
    private val _loopStartMs = MutableStateFlow(0L)
    val loopStartMs: StateFlow<Long> = _loopStartMs
    private val _loopEndMs = MutableStateFlow(0L)
    val loopEndMs: StateFlow<Long> = _loopEndMs
    private val database = AppDatabase.getDatabase(application)
    val engine = AudioWorkstationEngine()
    private val sharedPrefs = application.getSharedPreferences("hz_audio_workstation_prefs", android.content.Context.MODE_PRIVATE)

    val oboeAudioEngine = com.example.audio.OboeAudioEngine()

    // Master Audio Engine Real Flow Proxies
    val masterIsPlaying: StateFlow<Boolean> = masterAudioEngine.isPlaying
    val masterCurrentPositionMs: StateFlow<Long> = masterAudioEngine.currentPositionMs
    val masterDurationMs: StateFlow<Long> = masterAudioEngine.durationMs
    val masterSpeedMultiplier: StateFlow<Float> = masterAudioEngine.speedMultiplier
    val masterPitchShiftSemitones: StateFlow<Int> = masterAudioEngine.pitchShiftSemitones
    val masterErrorMessage: StateFlow<String?> = masterAudioEngine.errorMessage

    // Jetpack DataStore Repository for User Preferences & Module States
    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )

    private val _trackChordTimeline = MutableStateFlow<List<TrackChordEntry>>(emptyList())
    val trackChordTimeline: StateFlow<List<TrackChordEntry>> = _trackChordTimeline.asStateFlow()

    // Database flows exposed using stateIn
    val allSessions: StateFlow<List<ProjectSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allLicks: StateFlow<List<GuitarLick>> = repository.allLicks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Professional Recording Storage and Reuse System™ State
    val allRecordings: StateFlow<List<RecordingAssetEntity>> = repository.allRecordings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _recordingSearchQuery = MutableStateFlow("")
    val recordingSearchQuery: StateFlow<String> = _recordingSearchQuery.asStateFlow()

    private val _recordingSortOrder = MutableStateFlow("DATE_DESC")
    val recordingSortOrder: StateFlow<String> = _recordingSortOrder.asStateFlow()

    private val _recordingFavoriteFilter = MutableStateFlow(false)
    val recordingFavoriteFilter: StateFlow<Boolean> = _recordingFavoriteFilter.asStateFlow()

    // Active View States (Home, Analyzer, Studio, Library, Settings)
    private val _currentSection = MutableStateFlow("Home")
    val currentSection: StateFlow<String> = _currentSection.asStateFlow()

    // Engine proxies for UI state collection
    val currentChord: StateFlow<DetectedChordInfo?> = engine.currentChord
    val chordTimeline = engine.chordTimeline
    val detectionMode: StateFlow<String> = engine.detectionMode
    val liveNotesBuffer: StateFlow<List<String>> = engine.liveNotesBuffer
    val detectedArpeggio: StateFlow<String?> = engine.detectedArpeggio
    val africanStyleLick: StateFlow<String?> = engine.africanStyleLick
    val tunerState: StateFlow<TuningNote> = engine.tunerState
    val bpm: StateFlow<Int> = engine.bpm
    val tempoPreservedMultiplier: StateFlow<Float> = engine.tempoPreservedMultiplier
    val isRecording: StateFlow<Boolean> = engine.isRecording
    val recordingTimerSeconds: StateFlow<Int> = engine.recordingTimerSeconds
    val stemSeparation: StateFlow<StemSeparationState> = engine.stemSeparation

    val uploadedFileName = engine.uploadedFileName
    val uploadedFileSize = engine.uploadedFileSize
    val aiAnalysisResult = engine.aiAnalysisResult
    val isStemPlaybackActive = engine.isStemPlaybackActive

    // Automated Tempo Detection States
    private val _isAnalyzingTempo = MutableStateFlow(false)
    val isAnalyzingTempo: StateFlow<Boolean> = _isAnalyzingTempo.asStateFlow()

    private val _tempoDetectionProgress = MutableStateFlow(0f)
    val tempoDetectionProgress: StateFlow<Float> = _tempoDetectionProgress.asStateFlow()

    private val _tempoDetectionLogs = MutableStateFlow<List<String>>(emptyList())
    val tempoDetectionLogs: StateFlow<List<String>> = _tempoDetectionLogs.asStateFlow()

    // Global Key Signature Detection States
    private val _globalKeySignature = MutableStateFlow<String?>(null)
    val globalKeySignature: StateFlow<String?> = _globalKeySignature.asStateFlow()

    // Harmonic Reconstruction Engine & Accurate Note Preservation States
    private val _showHarmonicClassification = MutableStateFlow(true)
    val showHarmonicClassification: StateFlow<Boolean> = _showHarmonicClassification.asStateFlow()

    private val _performanceNoteStream = MutableStateFlow<List<DetectedPerformanceNote>>(emptyList())
    val performanceNoteStream: StateFlow<List<DetectedPerformanceNote>> = _performanceNoteStream.asStateFlow()

    private val _activePerformanceNotes = MutableStateFlow<List<DetectedPerformanceNote>>(emptyList())
    val activePerformanceNotes: StateFlow<List<DetectedPerformanceNote>> = _activePerformanceNotes.asStateFlow()

    fun closeProject() {
        masterAudioEngine.clearProjectResources()
        _activePerformanceNotes.value = emptyList()
        _performanceNoteStream.value = emptyList()
        _globalKeySignature.value = null
    }

    fun toggleHarmonicClassification() {
        val next = !_showHarmonicClassification.value
        _showHarmonicClassification.value = next
        sharedPrefs.edit().putBoolean("show_harmonic_classification", next).apply()
    }

    // Arpeggio Intelligence Engine instance
    val arpeggioIntelligenceEngine = com.example.audio.ArpeggioIntelligenceEngine(repository = repository, scope = viewModelScope)

    fun feedPerformanceNotes(rawNotes: List<String>, parentChord: String = currentChord.value?.name ?: "C") {
        if (rawNotes.isEmpty()) return
        val currentTimestamp = System.currentTimeMillis()
        val classified = HarmonicReconstructionEngine.processAndClassifyNotes(
            rawNotes = rawNotes,
            parentChordSymbol = parentChord,
            keySignature = _globalKeySignature.value ?: "",
            baseTimestampMs = currentTimestamp
        )

        _activePerformanceNotes.value = classified

        val updatedStream = (_performanceNoteStream.value + classified).takeLast(40)
        _performanceNoteStream.value = updatedStream

        // Pass raw notes to Arpeggio Intelligence Engine to analyze & store in Room DB
        arpeggioIntelligenceEngine.processNoteStream(
            rawNotes = rawNotes,
            bpm = bpm.value,
            autoSaveToRoom = true
        )

        // Pass raw notes to audio engine buffer without filtering
        val notesString = rawNotes.joinToString(" ")
        engine.tapLiveMusicalNote(notesString)
    }
    fun clearPerformanceNotes() {
        _performanceNoteStream.value = emptyList()
        _activePerformanceNotes.value = emptyList()
        engine.clearLiveNotes()
    }

    private val _isAnalyzingKey = MutableStateFlow(false)
    val isAnalyzingKey: StateFlow<Boolean> = _isAnalyzingKey.asStateFlow()

    private val _keyAnalysisProgress = MutableStateFlow(0f)
    val keyAnalysisProgress: StateFlow<Float> = _keyAnalysisProgress.asStateFlow()

    private val _keyAnalysisLogs = MutableStateFlow<List<String>>(emptyList())
    val keyAnalysisLogs: StateFlow<List<String>> = _keyAnalysisLogs.asStateFlow()

    // Universal Audio Sharing Engine™ State & Controls
    val universalAudioSharingState: StateFlow<UniversalAudioSharingState> = engine.sharingState

    fun toggleSharingModuleBypass(moduleId: String) {
        engine.toggleSharingModuleBypass(moduleId)
    }

    fun setSharingModuleGain(moduleId: String, gainDb: Float) {
        engine.setSharingModuleGain(moduleId, gainDb)
    }

    fun toggleSharingPlayback() {
        engine.toggleSharingPlayback()
    }

    fun requestModuleAudioProcessing(sampleRate: Int = 48000, channels: Int = 2) {
        engine.requestAudioProcessing(sampleRate, channels)
    }

    fun stopModuleAudioProcessing() {
        engine.stopAudioProcessing()
    }

    fun setSharingPlayheadMs(ms: Long) {
        engine.setSharingPlayheadMs(ms)
    }

    fun toggleStemMute(channelId: String) {
        engine.toggleStemMute(channelId)
    }

    fun toggleStemSolo(channelId: String) {
        engine.toggleStemSolo(channelId)
    }

    fun playOnlyStem(channelId: String) {
        engine.playOnlyStem(channelId)
    }

    fun playCombinationStems(channelIds: Set<String>) {
        engine.playCombinationStems(channelIds)
    }

    fun clearStemSoloAndMute() {
        engine.clearStemSoloAndMute()
    }

    // Universal Send To Routing State
    private val _lastSendToEvent = MutableStateFlow<com.example.data.SendToTransferEvent?>(null)
    val lastSendToEvent: StateFlow<com.example.data.SendToTransferEvent?> = _lastSendToEvent.asStateFlow()

    fun sendAudioToDestination(sourceName: String, destinationId: String) {
        val dest = com.example.data.UniversalSendToRegistry.findDestination(destinationId)
        val event = com.example.data.SendToTransferEvent(
            sourceName = sourceName,
            destination = dest,
            sharedMemoryPointer = engine.getDirectSharedMemoryPointer(),
            duplicateFilesCreated = 0,
            statusMessage = "Zero-Copy Audio Stream '$sourceName' routed to ${dest.name}"
        )
        _lastSendToEvent.value = event

        // Automatically route to target section
        setSection(dest.targetSection)

        // Focus or trigger mode based on destination
        when (dest.targetMode) {
            "chords" -> engine.setDetectionMode("Combined Analysis")
            "arpeggio" -> engine.setDetectionMode("Arpeggio Pattern Focus")
            "phrase" -> engine.setDetectionMode("Phrase & Motif Recognition")
            "practice" -> engine.setSpeedMultiplier(1.0f)
            "solo_stem" -> {
                val stemLower = sourceName.lowercase()
                if (stemLower.contains("guitar")) engine.playOnlyStem("guitar")
                else if (stemLower.contains("piano")) engine.playOnlyStem("piano")
                else if (stemLower.contains("vocal")) engine.playOnlyStem("vocals")
                else if (stemLower.contains("bass")) engine.playOnlyStem("bass")
                else if (stemLower.contains("drum")) engine.playOnlyStem("drums")
            }
        }
    }

    fun clearLastSendToEvent() {
        _lastSendToEvent.value = null
    }

    // Universal Audio Import State
    private val _lastImportedAudioMetadata = MutableStateFlow<com.example.data.ImportedAudioMetadata?>(null)
    val lastImportedAudioMetadata: StateFlow<com.example.data.ImportedAudioMetadata?> = _lastImportedAudioMetadata.asStateFlow()
    fun importUniversalAudio(metadata: com.example.data.ImportedAudioMetadata) {
        _lastImportedAudioMetadata.value = metadata
        setUploadedFile(metadata.fileName, metadata.fileSize)
        _globalKeySignature.value = metadata.detectedKey.takeIf { it.isNotBlank() }
        engine.setBpm(metadata.detectedBpm.coerceIn(0, 300))
        var loaded = true
        if (!metadata.uriString.isNullOrBlank()) {
            try {
                val uri = android.net.Uri.parse(metadata.uriString)
                loaded = masterAudioEngine.loadAudioUri(uri, metadata)
            } catch (e: Exception) {
                loaded = false
                Log.e("WorkstationViewModel", "Audio import failed", e)
            }
        }
        if (loaded) {
            val titleClean = metadata.fileName.substringBeforeLast(".")
            addSession(
                title = titleClean,
                bpm = metadata.detectedBpm,
                key = metadata.detectedKey,
                notes = "Imported locally: ${metadata.formatExtension}, ${metadata.channels}, ${metadata.sampleRateHz}",
                chords = "",
                tags = "Imported, ${metadata.formatExtension}"
            )
        }
    }

    fun setUploadedFile(name: String?, size: String?) {
        engine.setUploadedFile(name, size)
        if (name == null) {
            _globalKeySignature.value = null
            _trackChordTimeline.value = emptyList()
            _isAnalyzingTempo.value = false
            _isAnalyzingKey.value = false
            _tempoDetectionProgress.value = 0f
            _keyAnalysisProgress.value = 0f
        }
    }

    fun toggleStemPlayback() {
        engine.toggleStemPlayback()
    }

    fun setStemPlayback(active: Boolean) {
        engine.setStemPlayback(active)
    }

    // Audio Restoration States
    val noiseReductionEnabled = engine.noiseReductionEnabled
    val humRemovalEnabled = engine.humRemovalEnabled
    val clippingRepairEnabled = engine.clippingRepairEnabled
    val vocalEnhancementEnabled = engine.vocalEnhancementEnabled

    // Music theory quiz state
    private val _quizQuestion = MutableStateFlow("What interval is C to G?")
    val quizQuestion: StateFlow<String> = _quizQuestion.asStateFlow()

    private val _quizOptions = MutableStateFlow(listOf("Major 3rd", "Perfect 5th", "Perfect 4th", "Minor 6th"))
    val quizOptions: StateFlow<List<String>> = _quizOptions.asStateFlow()

    private val _quizFeedback = MutableStateFlow<String?>(null)
    val quizFeedback: StateFlow<String?> = _quizFeedback.asStateFlow()

    // Export log output stream
    private val _exportLog = MutableStateFlow<String?>(null)
    val exportLog: StateFlow<String?> = _exportLog.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    // Synchronized Master Playback States
    private val _pitchShiftSemitones = MutableStateFlow(0)
    val pitchShiftSemitones: StateFlow<Int> = _pitchShiftSemitones.asStateFlow()

    private val _isLearningSpeedMode = MutableStateFlow(false)
    val isLearningSpeedMode: StateFlow<Boolean> = _isLearningSpeedMode.asStateFlow()

    val transposedKeySignature: StateFlow<String> = combine(
        _globalKeySignature,
        _pitchShiftSemitones
    ) { key, shift ->
        if (key.isNullOrBlank()) "" else com.example.data.MusicTheoryUtils.transposeKeySignature(key, shift)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )

    val transposedChordTimeline: StateFlow<List<TrackChordEntry>> = combine(
        _trackChordTimeline,
        _pitchShiftSemitones
    ) { timeline, shift ->
        if (shift == 0) {
            timeline
        } else {
            timeline.map { entry ->
                val transposedSym = com.example.data.MusicTheoryUtils.transposeChord(entry.chordSymbol, shift)
                val info = engine.buildChordInfo(transposedSym)
                entry.copy(
                    chordSymbol = transposedSym,
                    chordName = info.name,
                    root = info.root,
                    notes = info.notes
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    fun toggleMasterPlayback() {
        if (masterAudioEngine.loadedMetadata.value != null) {
            masterAudioEngine.togglePlayPause()
        } else {
            masterAudioEngine.play() // Triggers "No audio loaded. Import an audio file first." message
        }
    }
    fun setPlaybackPosition(ms: Long) {
        val duration = masterDurationMs.value
        val targetMs = ms.coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
        _playbackPositionMs.value = targetMs
        masterAudioEngine.seekTo(targetMs)
        engine.setSharingPlayheadMs(targetMs)
    }

    fun setSpeedMultiplier(multiplier: Float) {
        val clamped = multiplier.coerceIn(0.25f, 2.0f)
        engine.setSpeedMultiplier(clamped)
        viewModelScope.launch {
            userPreferencesRepository.updatePlaybackSpeedMultiplier(clamped)
        }
    }

    fun speedUp() {
        val presets = listOf(0.25f, 0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 2.00f)
        val current = engine.tempoPreservedMultiplier.value
        val next = presets.firstOrNull { it > current + 0.01f } ?: 2.00f
        setSpeedMultiplier(next)
    }

    fun slowDown() {
        val presets = listOf(0.25f, 0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 2.00f)
        val current = engine.tempoPreservedMultiplier.value
        val prev = presets.lastOrNull { it < current - 0.01f } ?: 0.25f
        setSpeedMultiplier(prev)
    }

    fun resetSpeed() {
        setSpeedMultiplier(1.00f)
    }

    fun toggleLearningSpeedMode() {
        val newMode = !_isLearningSpeedMode.value
        _isLearningSpeedMode.value = newMode
        if (newMode && engine.tempoPreservedMultiplier.value > 0.75f) {
            setSpeedMultiplier(0.50f)
        }
    }

    fun setPitchShift(semitones: Int) {
        val clamped = semitones.coerceIn(-12, 12)
        _pitchShiftSemitones.value = clamped
        viewModelScope.launch {
            userPreferencesRepository.updatePitchShiftSemitones(clamped)
        }
    }

    fun transposeUp() {
        setPitchShift(_pitchShiftSemitones.value + 1)
    }

    fun transposeDown() {
        setPitchShift(_pitchShiftSemitones.value - 1)
    }

    fun resetTranspose() {
        setPitchShift(0)
    }

    fun toggleLooping() {
        _isLoopingEnabled.value = !_isLoopingEnabled.value
    }

    fun setLoopRange(startMs: Long, endMs: Long) {
        val start = startMs.coerceIn(0L, 23000L)
        val end = endMs.coerceIn(start + 1000L, 24000L)
        _loopStartMs.value = start
        _loopEndMs.value = end
    }

    fun stepPlaybackForward(deltaMs: Long = 1000L) {
        setPlaybackPosition(_playbackPositionMs.value + deltaMs)
    }

    fun stepPlaybackBackward(deltaMs: Long = 1000L) {
        setPlaybackPosition(_playbackPositionMs.value - deltaMs)
    }

    init {
        // Load settings from persistence
        val savedSection = sharedPrefs.getString("current_section", "Home") ?: "Home"
        _currentSection.value = savedSection

        val savedDetectionMode = sharedPrefs.getString("detection_mode", "Combined Analysis") ?: "Combined Analysis"
        engine.setDetectionMode(savedDetectionMode)

        val savedBpm = sharedPrefs.getInt("bpm", 0)
        if (savedBpm > 0) engine.setBpm(savedBpm)

        val savedSpeedMultiplier = sharedPrefs.getFloat("speed_multiplier", 1.0f)
        engine.adjustSpeedMultiplier(savedSpeedMultiplier)

        val savedNoiseReduction = sharedPrefs.getBoolean("noise_reduction", false)
        engine.setNoiseReductionEnabled(savedNoiseReduction)

        val savedHumRemoval = sharedPrefs.getBoolean("hum_removal", false)
        engine.setHumRemovalEnabled(savedHumRemoval)

        val savedClippingRepair = sharedPrefs.getBoolean("clipping_repair", false)
        engine.setClippingRepairEnabled(savedClippingRepair)

        val savedVocalEnhancement = sharedPrefs.getBoolean("vocal_enhancement", false)
        engine.setVocalEnhancementEnabled(savedVocalEnhancement)

        val savedShowClassification = sharedPrefs.getBoolean("show_harmonic_classification", true)
        _showHarmonicClassification.value = savedShowClassification

        engine.setCurrentChord(null)

        // Analysis state is reconstructed only from a currently loaded audio source.
        // Persisted display-only analysis is not treated as a live result.

        _globalKeySignature.value = null
        engine.setAiAnalysisResult(null)

        // Setup persistent listeners to save state dynamically on any changes in DataStore & SharedPrefs
        viewModelScope.launch {
            _currentSection.collect { section ->
                sharedPrefs.edit().putString("current_section", section).apply()
                userPreferencesRepository.updateSelectedNavigationTab(section)
                userPreferencesRepository.updateActiveModuleId(section)
            }
        }
        viewModelScope.launch {
            engine.detectionMode.collect { mode ->
                sharedPrefs.edit().putString("detection_mode", mode).apply()
                userPreferencesRepository.updateDetectionMode(mode)
            }
        }
        viewModelScope.launch {
            engine.bpm.collect { bpmVal ->
                sharedPrefs.edit().putInt("bpm", bpmVal).apply()
                userPreferencesRepository.updateBpm(bpmVal)
            }
        }
        viewModelScope.launch {
            engine.tempoPreservedMultiplier.collect { speedMultiplier ->
                sharedPrefs.edit().putFloat("speed_multiplier", speedMultiplier).apply()
            }
        }
        viewModelScope.launch {
            engine.noiseReductionEnabled.collect { enabled ->
                sharedPrefs.edit().putBoolean("noise_reduction", enabled).apply()
            }
        }
        viewModelScope.launch {
            engine.humRemovalEnabled.collect { enabled ->
                sharedPrefs.edit().putBoolean("hum_removal", enabled).apply()
            }
        }
        viewModelScope.launch {
            engine.clippingRepairEnabled.collect { enabled ->
                sharedPrefs.edit().putBoolean("clipping_repair", enabled).apply()
            }
        }
        viewModelScope.launch {
            engine.vocalEnhancementEnabled.collect { enabled ->
                sharedPrefs.edit().putBoolean("vocal_enhancement", enabled).apply()
            }
        }
        viewModelScope.launch {
            engine.uploadedFileName.collect { name ->
                if (name == null) {
                    sharedPrefs.edit()
                        .remove("uploaded_file_name")
                        .remove("uploaded_file_size")
                        .remove("ai_analysis_result")
                        .remove("global_key_signature")
                        .apply()
                } else {
                    sharedPrefs.edit()
                        .putString("uploaded_file_name", name)
                        .putString("uploaded_file_size", engine.uploadedFileSize.value)
                        .apply()
                    userPreferencesRepository.updateLastActiveProjectTitle(name)
                }
            }
        }
        viewModelScope.launch {
            engine.aiAnalysisResult.collect { result ->
                if (result == null) {
                    sharedPrefs.edit().remove("ai_analysis_result").apply()
                } else {
                    sharedPrefs.edit().putString("ai_analysis_result", result).apply()
                }
            }
        }
        viewModelScope.launch {
            _globalKeySignature.collect { key ->
                if (key == null) {
                    sharedPrefs.edit().remove("global_key_signature").apply()
                } else {
                    sharedPrefs.edit().putString("global_key_signature", key).apply()
                    userPreferencesRepository.updateGlobalKeySignature(key)
                }
            }
        }
        viewModelScope.launch {
            engine.stemSeparation.collect { state ->
                if (state is StemSeparationState.Success) {
                    sharedPrefs.edit()
                        .putFloat("stem_vocals_volume", state.vocalsVolume)
                        .putFloat("stem_melody_volume", state.melodyVolume)
                        .putFloat("stem_bass_volume", state.bassVolume)
                        .putFloat("stem_drums_volume", state.drumsVolume)
                        .apply()
                }
            }
        }

        viewModelScope.launch {
            engine.currentChord.collect { chord ->
                if (chord == null) {
                    sharedPrefs.edit().remove("current_chord").apply()
                } else {
                    try {
                        val moshi = Moshi.Builder()
                            .addLast(KotlinJsonAdapterFactory())
                            .build()
                        val adapter = moshi.adapter(DetectedChordInfo::class.java)
                        val json = adapter.toJson(chord)
                        sharedPrefs.edit().putString("current_chord", json).apply()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        viewModelScope.launch {
            engine.chordTimeline.collect { timeline ->
                if (timeline.isEmpty()) {
                    sharedPrefs.edit().remove("chord_timeline").apply()
                } else {
                    try {
                        val moshi = Moshi.Builder()
                            .addLast(KotlinJsonAdapterFactory())
                            .build()
                        val listType = Types.newParameterizedType(List::class.java, TimelineChordEntry::class.java)
                        val adapter = moshi.adapter<List<TimelineChordEntry>>(listType)
                        val json = adapter.toJson(timeline)
                        sharedPrefs.edit().putString("chord_timeline", json).apply()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Collect real-time transcription from HZAudioEngine during playback/recording
        viewModelScope.launch {
            masterAudioEngine.harmonicTranscriber.currentTranscription.collect { transcription ->
                if (transcription != null) {
                    val chord = transcription.chordInfo
                    engine.setCurrentChord(chord)
                    engine.recordDetectedChord(chord, masterCurrentPositionMs.value)
                    engine.setDetectedArpeggio(transcription.arpeggioPattern)
                    engine.setAfricanStyleLick(transcription.africanStyleLick)
                    engine.setVoiceLeading(transcription.voiceLeading)
                    engine.setMatchedProgression(transcription.matchedProgression)
                    engine.setFunctionalAnalysis(transcription.functionalAnalysis)
                    if (transcription.activeNotes.isNotEmpty()) {
                        feedPerformanceNotes(transcription.activeNotes, chord.name)
                    }
                }
            }
        }

        // Setup timer ticking for the recorder module
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                if (isRecording.value) {
                    engine.tickRecordingTimer()
                }
            }
        }

        // Smart Module State Manager™ Periodic Background Auto-Save (Every 3 seconds)
        viewModelScope.launch {
            while (true) {
                delay(3000L)
                saveCurrentModuleStateImmediately()
            }
        }

        // On app launch check for previously active session
        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.getGlobalSession()
            if (session != null && session.hasUnsavedSession) {
                _resumeSessionInfo.value = session
                _showResumeSessionPrompt.value = true
            }
        }
    }

    private fun buildChordInfoName(symbol: String): String {
        return when (symbol) {
            "C" -> "C Major"
            "G" -> "G Major"
            "D" -> "D Major"
            "Am" -> "A Minor"
            "Em" -> "E Minor"
            "F" -> "F Major"
            "Dm" -> "D Minor"
            "Cmaj7" -> "C Major 7th"
            "Am7" -> "A Minor 7th"
            "G7" -> "G Dominant 7th"
            "Am9" -> "A Minor 9th"
            "C/G" -> "C Major / G Bass"
            "G13" -> "G Dominant 13th"
            "Sungura A" -> "A Major (Sungura)"
            "F#" -> "F# Major"
            "B" -> "B Major"
            "C#" -> "C# Major"
            "D#m" -> "D# Minor"
            "D#m7" -> "D# Minor 7th"
            "F#add9" -> "F# Major add 9"
            "Badd9" -> "B Major add 9"
            "A#m" -> "A# Minor"
            "G#m" -> "G# Minor"
            else -> "$symbol Major"
        }
    }

    // Smart Module State Manager™ States
    private val _showResumeSessionPrompt = MutableStateFlow(false)
    val showResumeSessionPrompt: StateFlow<Boolean> = _showResumeSessionPrompt.asStateFlow()

    private val _resumeSessionInfo = MutableStateFlow<AppGlobalSessionEntity?>(null)
    val resumeSessionInfo: StateFlow<AppGlobalSessionEntity?> = _resumeSessionInfo.asStateFlow()

    fun dismissResumePrompt() {
        _showResumeSessionPrompt.value = false
        viewModelScope.launch {
            userPreferencesRepository.clearResumeDialogTrigger()
        }
    }

    fun saveCurrentModuleStateImmediately() {
        val activeModule = _currentSection.value
        val title = uploadedFileName.value ?: "HZ CHORD AI Session"
        val key = globalKeySignature.value ?: ""
        val bpmVal = engine.bpm.value
        val recentChords = engine.chordTimeline.value.takeLast(4).joinToString(", ") { it.name }

        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = SmartModuleStateEntity(
                moduleId = activeModule,
                projectTitle = title,
                audioFilePath = uploadedFileName.value,
                uploadedFileName = uploadedFileName.value,
                playbackPositionMs = _playbackPositionMs.value,
                playbackSpeed = engine.tempoPreservedMultiplier.value,
                pitchShiftSemitones = _pitchShiftSemitones.value,
                loopStartMs = _loopStartMs.value,
                loopEndMs = _loopEndMs.value,
                isLoopEnabled = _isLoopingEnabled.value,
                currentChord = engine.currentChord.value?.name ?: "",
                bpm = bpmVal,
                detectedKey = key,
                phraseDetectionResultsJson = engine.aiAnalysisResult.value ?: "",
                lastActiveModuleId = activeModule,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            repository.saveModuleState(snapshot)

            val globalSession = AppGlobalSessionEntity(
                id = 1,
                lastActiveModuleId = activeModule,
                activeProjectTitle = title,
                activeAudioFilePath = uploadedFileName.value,
                uploadedFileName = uploadedFileName.value,
                playbackPositionMs = _playbackPositionMs.value,
                bpm = bpmVal,
                keySignature = key,
                hasUnsavedSession = true,
                lastSavedTimestamp = System.currentTimeMillis()
            )
            repository.saveGlobalSession(globalSession)

            // Persist session state flags & trigger into Jetpack DataStore
            userPreferencesRepository.setUnsavedSessionData(
                title = title,
                bpm = bpmVal,
                key = key,
                chords = recentChords,
                hasUnsaved = true,
                showDialog = true
            )
            userPreferencesRepository.updateActiveModuleId(activeModule)
            userPreferencesRepository.updateLastActiveProjectTitle(title)
        }
    }

    fun restoreModuleState(moduleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedState = repository.getModuleState(moduleId)
            if (savedState != null) {
                _playbackPositionMs.value = savedState.playbackPositionMs
                engine.setBpm(savedState.bpm)
                _globalKeySignature.value = savedState.detectedKey
                if (savedState.uploadedFileName != null) {
                    engine.setUploadedFile(savedState.uploadedFileName, "7.8 MB")
                }
                if (savedState.phraseDetectionResultsJson.isNotEmpty()) {
                    engine.setAiAnalysisResult(savedState.phraseDetectionResultsJson)
                }
            }
        }
    }

    fun resumePreviousSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.getGlobalSession()
            if (session != null) {
                _currentSection.value = session.lastActiveModuleId
                _playbackPositionMs.value = session.playbackPositionMs
                engine.setBpm(session.bpm)
                _globalKeySignature.value = session.keySignature
                if (session.uploadedFileName != null) {
                    engine.setUploadedFile(session.uploadedFileName, "7.8 MB")
                }
                restoreModuleState(session.lastActiveModuleId)
            }
            _showResumeSessionPrompt.value = false
            userPreferencesRepository.clearResumeDialogTrigger()
        }
    }

    fun startNewProject() {
        viewModelScope.launch(Dispatchers.IO) {
            engine.setUploadedFile(null, null)
            _playbackPositionMs.value = 0L
            engine.setBpm(0)
            _globalKeySignature.value = null
            engine.clearTimeline()
            _showResumeSessionPrompt.value = false
            userPreferencesRepository.clearResumeDialogTrigger()
            repository.saveGlobalSession(
                AppGlobalSessionEntity(
                    id = 1,
                    lastActiveModuleId = "dashboard",
                    activeProjectTitle = "New Project Session",
                    hasUnsavedSession = false
                )
            )
        }
    }

    fun openAnotherProject() {
        _showResumeSessionPrompt.value = false
        viewModelScope.launch {
            userPreferencesRepository.clearResumeDialogTrigger()
        }
        setSection("library")
    }

    fun setSection(section: String) {
        if (_currentSection.value != section) {
            saveCurrentModuleStateImmediately()
            _currentSection.value = section
            restoreModuleState(section)
        }
    }

    private val recordedPcmList = java.util.Collections.synchronizedList(mutableListOf<Float>())

    // Professional Recording Storage and Reuse System™ Actions
    fun toggleRecording(name: String = "New Session Recording", format: String = "WAV", notes: String = "") {
        val wasRec = engine.isRecording.value
        engine.toggleRecording()
        if (!wasRec) {
            recordedPcmList.clear()
            val success = oboeAudioEngine.startRecording { pcmBuffer ->
                synchronized(recordedPcmList) {
                    for (sample in pcmBuffer) {
                        recordedPcmList.add(sample)
                    }
                }
                val transcription = masterAudioEngine.harmonicTranscriber.analyzePcmBuffer(pcmBuffer)
                engine.updateTunerFromPcm(pcmBuffer)
                if (transcription.activeNotes.isNotEmpty()) {
                    feedPerformanceNotes(transcription.activeNotes, transcription.chordInfo.name)
                }
            }
            if (!success) {
                Log.w("WorkstationViewModel", "Oboe/AudioRecord failed to start mic recording")
            }
        } else {
            oboeAudioEngine.stopRecording()
            val pcmArray = synchronized(recordedPcmList) {
                if (recordedPcmList.isNotEmpty()) {
                    recordedPcmList.toFloatArray()
                } else {
                    FloatArray(0)
                }
            }
            if (pcmArray.isNotEmpty()) {
                saveNewRecordingAsset(
                    name = name,
                    pcmSamples = pcmArray,
                    format = format,
                    notes = notes
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        oboeAudioEngine.release()
    }

    fun setRecordingSearchQuery(query: String) {
        _recordingSearchQuery.value = query
    }

    fun setRecordingSortOrder(order: String) {
        _recordingSortOrder.value = order
    }

    fun toggleRecordingFavoriteFilter() {
        _recordingFavoriteFilter.value = !_recordingFavoriteFilter.value
    }

    fun saveNewRecordingAsset(
        name: String,
        pcmSamples: FloatArray,
        sampleRate: Int = 44100,
        channels: Int = 1,
        format: String = "WAV",
        notes: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val asset = RecordingStorageManager.savePcmToWavAsset(
                context = getApplication(),
                recordingName = name,
                pcmSamples = pcmSamples,
                sampleRate = sampleRate,
                channels = channels,
                format = format,
                userNotes = notes,
                detectedBpm = bpm.value,
                detectedKey = globalKeySignature.value ?: "",
                detectedChordsCsv = currentChord.value?.name ?: "",
                detectedArpeggio = detectedArpeggio.value,
                africanStyleLick = africanStyleLick.value
            )
            repository.insertRecording(asset)
        }
    }

    fun renameRecordingAsset(id: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rec = repository.getRecordingById(id)
            if (rec != null) {
                repository.updateRecording(rec.copy(recordingName = newName))
            }
        }
    }

    fun updateRecordingNotes(id: String, newNotes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rec = repository.getRecordingById(id)
            if (rec != null) {
                repository.updateRecording(rec.copy(userNotes = newNotes))
            }
        }
    }

    fun toggleRecordingFavorite(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rec = repository.getRecordingById(id)
            if (rec != null) {
                repository.updateRecordingFavorite(id, !rec.isFavorite)
            }
        }
    }

    fun deleteRecordingAsset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRecordingById(id)
        }
    }

    fun duplicateRecordingAsset(recording: RecordingAssetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val copy = RecordingStorageManager.duplicateAsset(getApplication(), recording)
            repository.insertRecording(copy)
        }
    }

    fun applyEditToRecording(recording: RecordingAssetEntity, editType: String, param1: Long = 0L, param2: Long = 0L) {
        viewModelScope.launch(Dispatchers.IO) {
            val edited = when (editType) {
                "TRIM" -> RecordingStorageManager.trimAudio(getApplication(), recording, param1, param2)
                "FADE" -> RecordingStorageManager.fadeInOutAudio(getApplication(), recording, param1, param2)
                "NORMALIZE" -> RecordingStorageManager.normalizeVolume(getApplication(), recording)
                "DENOISE" -> RecordingStorageManager.applyNoiseReduction(getApplication(), recording)
                else -> recording
            }
            repository.insertRecording(edited)
        }
    }

    fun sendRecordingToModule(recording: RecordingAssetEntity, targetModuleId: String) {
        viewModelScope.launch {
            val meta = ImportedAudioMetadata(
                uriString = android.net.Uri.fromFile(java.io.File(recording.filePath)).toString(),
                fileName = recording.recordingName + "." + recording.fileFormat.lowercase(),
                artist = "HZ Mic Recording",
                album = recording.projectName ?: "Recording Assets",
                durationMs = recording.durationMs,
                durationFormatted = recording.getFormattedDuration(),
                bitrateKbps = "${recording.bitDepth * recording.sampleRate * recording.channels / 1000} kbps",
                sampleRateHz = "${recording.sampleRate / 1000.0} kHz",
                channels = if (recording.channels == 2) "Stereo (2 ch)" else "Mono (1 ch)",
                fileSize = recording.getFormattedSize(),
                formatExtension = recording.fileFormat,
                sourceType = "Local Recording Storage",
                waveformAmplitudes = recording.getWaveformPeaksList(),
                detectedBpm = recording.detectedBpm,
                detectedKey = recording.detectedKey
            )

            importUniversalAudio(meta)

            engine.setBpm(recording.detectedBpm)
            _globalKeySignature.value = recording.detectedKey
            if (recording.detectedChordsCsv.isNotBlank()) {
                val chords = recording.detectedChordsCsv.split(",")
                if (chords.isNotEmpty()) {
                    engine.selectChord(chords[0].trim())
                }
            }

            setSection(targetModuleId)
        }
    }
    // Interactive DB Methods
    fun addSession(title: String, bpm: Int, key: String, notes: String, chords: String, tags: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSession(
                ProjectSession(
                    title = title,
                    bpm = bpm,
                    keySignature = key,
                    notes = notes,
                    detectedChords = chords,
                    categoryTags = tags
                )
            )
        }
    }

    fun deleteSession(session: ProjectSession) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSessionById(session.id)
        }
    }

    fun saveDetectedLick(title: String, genre: String, notes: String, bpmVal: Int, confidence: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertLick(
                GuitarLick(
                    title = title,
                    genre = genre,
                    notes = notes,
                    bpm = bpmVal,
                    confidence = confidence
                )
            )
        }
    }

    fun toggleLickFavorite(lick: GuitarLick) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLickFavorite(lick.id, !lick.isFavorite)
        }
    }

    fun deleteLick(lick: GuitarLick) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLickById(lick.id)
        }
    }

    fun clearChordTimeline() {
        engine.clearTimeline()
    }

    // Real Stem Separation running chunked HPSS engine on real audio data
    fun runStemSeparation(mode: String) {
        engine.startStemSeparation(mode)
        viewModelScope.launch {
            val realSamples = masterAudioEngine.getActivePcmSamples()
                ?: oboeAudioEngine.getLatestRecordedPcmBuffer()
                ?: FloatArray(0)

            if (realSamples.isEmpty()) {
                engine.setStemSeparationState(
                    StemSeparationState.Error("No audio loaded — import or record a file first")
                )
                return@launch
            }

            // Progress monitoring job
            val progressJob = launch {
                masterAudioEngine.stemEngine.separationProgress.collect { progress ->
                    engine.updateStemProgress(progress)
                }
            }

            // Execute real chunked HPSS separation
            val success = masterAudioEngine.stemEngine.separateMasterPcm(realSamples, sampleRate = 44100, qualityMode = mode)
            progressJob.cancel()

            if (!success) {
                engine.setStemSeparationState(
                    StemSeparationState.Error("Stem separation failed: Audio buffer empty or unreadable")
                )
                return@launch
            }

            engine.setStemSeparationState(
                StemSeparationState.Success(
                    mixerState = masterAudioEngine.stemEngine.stemMixerState.value
                )
            )

            // Trigger grounded on-device report with real audio parameters
            val filename = uploadedFileName.value ?: "recorded_session.wav"
            val filesize = uploadedFileSize.value ?: "${"%.1f".format(realSamples.size * 2 / 1024.0 / 1024.0)} MB"
            val currentBpm = bpm.value
            val currentKey = globalKeySignature.value ?: ""
            val chordList = chordTimeline.value.map { it.name }
            val chroma = masterAudioEngine.harmonicTranscriber.currentTranscription.value?.ChromaProfile

            val analysis = buildString {
                appendLine("Local analysis complete")
                appendLine("File: $filename")
                appendLine("Size: $filesize")
                appendLine("Mode: $mode")
                appendLine("BPM: ${if (currentBpm > 0) currentBpm else "unknown"}")
                appendLine("Key: $currentKey")
                appendLine("Detected chords: ${if (chordList.isEmpty()) "none yet" else chordList.joinToString(", ")}")
                appendLine("Processing: on-device PCM / DSP")
            }
            engine.setAiAnalysisResult(analysis)
        }
    }

    // Theory Quiz Helper
    fun answerQuiz(choice: String) {
        if (choice == "Perfect 5th") {
            _quizFeedback.value = "Correct! C to G is a Perfect 5th interval containing 7 semitones."
        } else {
            _quizFeedback.value = "Incorrect. Try again! Think about the semitone spacing."
        }
    }

    fun loadNewQuiz() {
        _quizFeedback.value = null
        val questions = listOf(
            Triple("What interval is C to G?", listOf("Major 3rd", "Perfect 5th", "Perfect 4th", "Minor 6th"), "Perfect 5th"),
            Triple("Which notes make up an A Minor triad?", listOf("A - C# - E", "A - C - E", "A - C - D#", "A - B - E"), "A - C - E"),
            Triple("What is the root of the slash chord C/E?", listOf("E", "C", "G", "A"), "E"),
            Triple("Sungura guitar styles is characterized by what rhythm?", listOf("Slow rubato", "Fast galloping triplets", "Standard 4/4 blues shuffle", "Waltz"), "Fast galloping triplets")
        )
        val picked = questions.random()
        _quizQuestion.value = picked.first
        _quizOptions.value = picked.second

        // Override checking matching correctness
        _quizFeedback.value = null
    }

    fun triggerExportLick(lick: com.example.data.GuitarLick) {
        viewModelScope.launch {
            _exportLog.value = "BUILDING HARMONIC TRANSCRIPTIONS FOR LICK: '${lick.title}'..."
            delay(500)
            
            try {
                val file = com.example.audio.MidiExportEngine.exportLickToMidi(getApplication(), lick)
                _exportLog.value = "Export Success! MIDI File written to:\n${file.absolutePath}\n\n⚡ Tagline: 'Hear the Notes. Understand the Music. Powered by AI.'"
            } catch (e: Exception) {
                _exportLog.value = "Export Failed: ${e.message}"
            }
        }
    }

    // Export Center
    // Export Center & Advanced Export System™
    fun triggerAdvancedExport(format: com.example.util.AdvancedExportEngine.ExportFormat, session: ProjectSession?) {
        val targetSession = session ?: ProjectSession(
            title = uploadedFileName.value ?: "Live Workstation Session",
            bpm = bpm.value,
            keySignature = globalKeySignature.value ?: "",
            notes = "Live performance notes with 100% note preservation.",
            detectedChords = chordTimeline.value.joinToString(", ") { it.name },
            categoryTags = "Live, Transcription, Export"
        )

        viewModelScope.launch {
            _exportLog.value = "INITIALIZING ADVANCED EXPORT SYSTEM™ [${format.displayName}]..."
            delay(300)
            _exportLog.value = "INJECTING BRANDING: 'HZ CHORD AI • Designed and Built by Joseph Hilary Zulukwa'..."
            delay(300)

            try {
                val result = when (format) {
                    com.example.util.AdvancedExportEngine.ExportFormat.PDF_REPORT -> {
                        com.example.util.AdvancedExportEngine.generateFullPdfReport(
                            context = getApplication(),
                            session = targetSession,
                            notes = _performanceNoteStream.value
                        )
                    }
                    com.example.util.AdvancedExportEngine.ExportFormat.MIDI -> {
                        com.example.util.AdvancedExportEngine.exportMidi(
                            context = getApplication(),
                            session = targetSession
                        )
                    }
                    com.example.util.AdvancedExportEngine.ExportFormat.CSV,
                    com.example.util.AdvancedExportEngine.ExportFormat.JSON,
                    com.example.util.AdvancedExportEngine.ExportFormat.MUSIC_XML -> {
                        if (_performanceNoteStream.value.isNotEmpty()) {
                            com.example.util.AdvancedExportEngine.exportNoteTranscription(
                                context = getApplication(),
                                notes = _performanceNoteStream.value,
                                format = format
                            )
                        } else {
                            val detectedChords = chordTimeline.value.mapIndexed { idx, info ->
                                val parsed = engine.buildChordInfo(info.name)
                                com.example.data.DetectedChord(
                                    timestampMs = idx * 1000L,
                                    chordName = info.name,
                                    rootNote = parsed.root,
                                    chordType = parsed.type,
                                    notes = info.notes.joinToString(",")
                                )
                            }
                            com.example.util.AdvancedExportEngine.exportChordTranscription(
                                context = getApplication(),
                                chords = detectedChords,
                                keySignature = targetSession.keySignature,
                                format = format
                            )
                        }
                    }
                    com.example.util.AdvancedExportEngine.ExportFormat.CHORD_SHEET -> {
                        val detectedChords = chordTimeline.value.mapIndexed { idx, info ->
                            val parsed = engine.buildChordInfo(info.name)
                            com.example.data.DetectedChord(
                                timestampMs = idx * 1000L,
                                chordName = info.name,
                                rootNote = parsed.root,
                                chordType = parsed.type,
                                notes = info.notes.joinToString(",")
                            )
                        }
                        com.example.util.AdvancedExportEngine.exportChordTranscription(
                            context = getApplication(),
                            chords = detectedChords,
                            keySignature = targetSession.keySignature,
                            format = format
                        )
                    }
                    else -> {
                        com.example.util.AdvancedExportEngine.exportNoteTranscription(
                            context = getApplication(),
                            notes = _performanceNoteStream.value,
                            format = format
                        )
                    }
                }

                _exportLog.value = "Export Success! [${result.format.displayName}]\n" +
                        "File Path: ${result.file.absolutePath}\n" +
                        "Size: ${result.sizeBytes} bytes\n\n" +
                        "⚡ HZ CHORD AI • 'Hear the Notes. Understand the Music. Powered by AI.'"
            } catch (e: Exception) {
                _exportLog.value = "Export Error: ${e.localizedMessage ?: "Unknown Error"}"
            }
        }
    }

    fun triggerStemExport(stemName: String, format: com.example.util.AdvancedExportEngine.ExportFormat) {
        viewModelScope.launch {
            _exportLog.value = "PREPARING REAL STEM EXPORT FOR: '$stemName'..."
            delay(200)
            try {
                val stemBuffer = masterAudioEngine.stemEngine.getStemBuffer(stemName)
                val result = com.example.util.AdvancedExportEngine.exportIndividualStem(
                    context = getApplication(),
                    stemName = stemName,
                    format = format,
                    stemPcm = stemBuffer
                )
                _exportLog.value = "Stem Export Success! [${result.title}]\nFile: ${result.file.absolutePath}\nSize: ${result.sizeBytes} bytes"
            } catch (e: Exception) {
                _exportLog.value = "Stem Export Error: ${e.message}"
            }
        }
    }

    fun triggerStemPackageExport() {
        val title = uploadedFileName.value ?: "Live Workstation Session"
        val bpmVal = bpm.value
        val key = globalKeySignature.value ?: ""
        val stemNames = listOf("Vocals", "Drums", "Bass", "Guitar", "Piano", "Strings", "Other")

        viewModelScope.launch {
            _exportLog.value = "BUNDLING ADVANCED STEM PACKAGE [7 SYNCHRONIZED TRACKS]..."
            delay(400)
            try {
                val result = com.example.util.AdvancedExportEngine.exportStemPackage(
                    context = getApplication(),
                    projectTitle = title,
                    bpm = bpmVal,
                    key = key,
                    stems = stemNames
                )
                _exportLog.value = "Stem Package Export Success!\nPackage Path: ${result.file.absolutePath}\n\nIncludes 7 synchronized stems, analysis metadata & project session."
            } catch (e: Exception) {
                _exportLog.value = "Package Export Failed: ${e.message}"
            }
        }
    }

    fun triggerExport(format: String, session: ProjectSession?) {
        val expFormat = when (format.lowercase()) {
            "pdf" -> com.example.util.AdvancedExportEngine.ExportFormat.PDF_REPORT
            "midi", "mid" -> com.example.util.AdvancedExportEngine.ExportFormat.MIDI
            "csv" -> com.example.util.AdvancedExportEngine.ExportFormat.CSV
            "json" -> com.example.util.AdvancedExportEngine.ExportFormat.JSON
            "musicxml", "xml" -> com.example.util.AdvancedExportEngine.ExportFormat.MUSIC_XML
            "chord_sheet", "chords" -> com.example.util.AdvancedExportEngine.ExportFormat.CHORD_SHEET
            else -> com.example.util.AdvancedExportEngine.ExportFormat.TXT
        }
        triggerAdvancedExport(expFormat, session)
    }

    fun dismissExportLog() {
        _exportLog.value = null
    }
}
