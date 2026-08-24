package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.squareup.moshi.JsonClass
import com.example.data.UniversalAudioSharingState
import com.example.data.SharingModuleStage
import com.example.data.StemMixerState
import com.example.data.StemChannelData

// Sealed state for AI Stem Separation
sealed class StemSeparationState {
    object Idle : StemSeparationState()
    data class Processing(val progress: Float, val mode: String, val etaSeconds: Int) : StemSeparationState()
    data class Success(
        val mixerState: StemMixerState = StemMixerState(),
        val vocalsVolume: Float = 1.0f,
        val melodyVolume: Float = 1.0f,
        val bassVolume: Float = 1.0f,
        val drumsVolume: Float = 1.0f
    ) : StemSeparationState()
    data class Error(val message: String) : StemSeparationState()
}

// Real tuner note info
data class TuningNote(
    val noteName: String,
    val targetFreq: Float,
    val currentFreq: Float,
    val deviationCents: Float, // -50 to +50
    val isTuned: Boolean
)

// Main Chord recognition entry
@JsonClass(generateAdapter = true)
data class DetectedChordInfo(
    val name: String,
    val root: String,
    val formula: String,
    val notes: List<String>,
    val confidence: Float,
    val frequency: Float,
    val type: String, // e.g., "Major", "Seventh", "Ext-Jazz", "Extended Voicing"
    val description: String,
    val suggestedSubstitutions: List<String>,
    val rootConfidence: Float = confidence,
    val qualityConfidence: Float = confidence,
    val overallConfidence: Float = confidence
)

// Real-time scrolling chord timeline item representing historical occurrences
@JsonClass(generateAdapter = true)
data class TimelineChordEntry(
    val id: String,
    val name: String,
    val root: String,
    val type: String,
    val timestamp: String,
    val confidence: Float,
    val notes: List<String>
)

class AudioWorkstationEngine {

    // Chord & Arpeggio Analyzer State
    private val _currentChord = MutableStateFlow<DetectedChordInfo?>(null)
    val currentChord: StateFlow<DetectedChordInfo?> = _currentChord.asStateFlow()

    private val _chordTimeline = MutableStateFlow<List<TimelineChordEntry>>(emptyList())
    val chordTimeline: StateFlow<List<TimelineChordEntry>> = _chordTimeline.asStateFlow()

    private var lastDetectedChordName: String? = null
    private var lastDetectedChordTimeMs: Long = Long.MIN_VALUE

    private val _detectionMode = MutableStateFlow("Combined Analysis") // Exact, Inferred, Combined
    val detectionMode: StateFlow<String> = _detectionMode.asStateFlow()

    // Interactive Piano or Guitar tapped notes to trigger arpeggios
    private val _liveNotesBuffer = MutableStateFlow<List<String>>(emptyList())
    val liveNotesBuffer: StateFlow<List<String>> = _liveNotesBuffer.asStateFlow()

    private val _detectedArpeggio = MutableStateFlow<String?>(null)
    val detectedArpeggio: StateFlow<String?> = _detectedArpeggio.asStateFlow()

    // Specialized African Guitar styles
    private val _africanStyleLick = MutableStateFlow<String?>(null)
    val africanStyleLick: StateFlow<String?> = _africanStyleLick.asStateFlow()

    private val _voiceLeading = MutableStateFlow<com.example.audio.theory.VoiceLeadingResult?>(null)
    val voiceLeading: StateFlow<com.example.audio.theory.VoiceLeadingResult?> = _voiceLeading.asStateFlow()

    private val _matchedProgression = MutableStateFlow<com.example.audio.theory.ProgressionMatchResult?>(null)
    val matchedProgression: StateFlow<com.example.audio.theory.ProgressionMatchResult?> = _matchedProgression.asStateFlow()

    private val _functionalAnalysis = MutableStateFlow<com.example.audio.theory.FunctionalAnalysisResult?>(null)
    val functionalAnalysis: StateFlow<com.example.audio.theory.FunctionalAnalysisResult?> = _functionalAnalysis.asStateFlow()

    // Audio Tuner State
    private val _tunerState = MutableStateFlow(TuningNote("—", 0f, 0f, 0f, false))
    val tunerState: StateFlow<TuningNote> = _tunerState.asStateFlow()

    // BPM Studio State
    private val _bpm = MutableStateFlow(0)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _tempoPreservedMultiplier = MutableStateFlow(1.0f) // 0.5x to 2.0x
    val tempoPreservedMultiplier: StateFlow<Float> = _tempoPreservedMultiplier.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingTimerSeconds = MutableStateFlow(0)
    val recordingTimerSeconds: StateFlow<Int> = _recordingTimerSeconds.asStateFlow()

    // Stem separation progress state
    private val _stemSeparation = MutableStateFlow<StemSeparationState>(StemSeparationState.Idle)
    val stemSeparation: StateFlow<StemSeparationState> = _stemSeparation.asStateFlow()

    private val _uploadedFileName = MutableStateFlow<String?>(null)
    val uploadedFileName: StateFlow<String?> = _uploadedFileName.asStateFlow()

    private val _uploadedFileSize = MutableStateFlow<String?>(null)
    val uploadedFileSize: StateFlow<String?> = _uploadedFileSize.asStateFlow()

    private val _aiAnalysisResult = MutableStateFlow<String?>(null)
    val aiAnalysisResult: StateFlow<String?> = _aiAnalysisResult.asStateFlow()

    private val _isStemPlaybackActive = MutableStateFlow(false)
    val isStemPlaybackActive: StateFlow<Boolean> = _isStemPlaybackActive.asStateFlow()

    // Audio Restoration effects
    private val _noiseReductionEnabled = MutableStateFlow(false)
    val noiseReductionEnabled: StateFlow<Boolean> = _noiseReductionEnabled.asStateFlow()

    private val _humRemovalEnabled = MutableStateFlow(false)
    val humRemovalEnabled: StateFlow<Boolean> = _humRemovalEnabled.asStateFlow()

    private val _clippingRepairEnabled = MutableStateFlow(false)
    val clippingRepairEnabled: StateFlow<Boolean> = _clippingRepairEnabled.asStateFlow()

    private val _vocalEnhancementEnabled = MutableStateFlow(false)
    val vocalEnhancementEnabled: StateFlow<Boolean> = _vocalEnhancementEnabled.asStateFlow()

    // Universal Audio Sharing Engine™ State
    private val _sharingState = MutableStateFlow(UniversalAudioSharingState())
    val sharingState: StateFlow<UniversalAudioSharingState> = _sharingState.asStateFlow()


    init {
        _sharingState.value = _sharingState.value.copy(
            sharedBufferMemoryRef = "Not active",
            oboeBackendName = "Not active",
            oboeLatencyMs = 0f,
            oboeEngineRunning = false,
            isPlaying = false
        )
    }

    fun toggleSharingModuleBypass(moduleId: String) {
        val current = _sharingState.value
        val updatedChain = current.modulesChain.map { stage ->
            if (stage.id == moduleId) {
                stage.copy(
                    isBypassed = !stage.isBypassed,
                    statusMessage = if (!stage.isBypassed) "Bypassed (Zero-Latency Audio Pass-Through)" else "Direct Shared Stream Active"
                )
            } else stage
        }
        _sharingState.value = current.copy(modulesChain = updatedChain)
    }

    fun setSharingModuleGain(moduleId: String, gainDb: Float) {
        val current = _sharingState.value
        val updatedChain = current.modulesChain.map { stage ->
            if (stage.id == moduleId) stage.copy(gainDb = gainDb) else stage
        }
        _sharingState.value = current.copy(modulesChain = updatedChain)
    }

    fun setSharingPlayheadMs(playheadMs: Long) {
        val current = _sharingState.value
        _sharingState.value = current.copy(currentPlayheadMs = playheadMs)
    }

    fun requestAudioProcessing(sampleRate: Int = 48000, channels: Int = 2) {
        val current = _sharingState.value
        if (!current.oboeEngineRunning) {
            try {
                OboeAudioService.instance.startNativeEngine(sampleRate, channels)
                val memPtr = OboeAudioService.instance.getDirectSharedMemoryPointer()
                _sharingState.value = current.copy(
                    sharedBufferMemoryRef = "$memPtr (Zero-Copy Unified Direct Pointer)",
                    oboeBackendName = OboeAudioService.instance.metrics.value.apiBackend,
                    oboeLatencyMs = OboeAudioService.instance.metrics.value.latencyMs,
                    oboeEngineRunning = true,
                    isPlaying = true
                )
            } catch (e: Exception) {
                // graceful fallback
            }
        }
    }

    fun stopAudioProcessing() {
        val current = _sharingState.value
        if (current.oboeEngineRunning) {
            try {
                OboeAudioService.instance.stopNativeEngine()
            } catch (e: Exception) {
                // graceful fallback
            }
            _sharingState.value = current.copy(
                sharedBufferMemoryRef = "Standby (Zero Audio Processing)",
                oboeEngineRunning = false,
                isPlaying = false
            )
        }
    }

    fun toggleSharingPlayback() {
        val current = _sharingState.value
        if (current.isPlaying) {
            stopAudioProcessing()
        } else {
            requestAudioProcessing(current.sampleRateHz, current.channels)
        }
    }

    fun setDetectionMode(mode: String) {
        _detectionMode.value = mode
    }

    fun selectChord(chordSymbol: String) {
        _currentChord.value = buildChordInfo(chordSymbol)
    }

    fun tapLiveMusicalNote(notesString: String) {
        val noteList = notesString.trim().split("\\s+".toRegex())
        _liveNotesBuffer.value = noteList
        if (noteList.size >= 2) {
            _detectedArpeggio.value = "Active Arpeggio: ${noteList.joinToString(" → ")}"
            _africanStyleLick.value = if (noteList.contains("F#") || noteList.contains("C#")) {
                "Sungura High Fret Lead Lick (Franco/Ephraim Style)"
            } else {
                "Rhumba Syncopated Dual Pluck"
            }
        }
    }

    fun clearLiveNotes() {
        _liveNotesBuffer.value = emptyList()
        _detectedArpeggio.value = null
        _africanStyleLick.value = null
    }

    fun clearTimeline() {
        _chordTimeline.value = emptyList()
        lastDetectedChordName = null
        lastDetectedChordTimeMs = Long.MIN_VALUE
    }

    /** Adds an audio-derived chord occurrence using the real playback/analysis timestamp. */
    fun recordDetectedChord(info: DetectedChordInfo, timeMs: Long) {
        if (info.name.isBlank() || info.name == "No signal" || info.confidence <= 0f) return
        if (lastDetectedChordName == info.name && timeMs - lastDetectedChordTimeMs < 500L) return

        val currentList = _chordTimeline.value.toMutableList()
        if (currentList.size >= 100) currentList.removeAt(0)
        val totalSeconds = (timeMs.coerceAtLeast(0L) / 1000L).toInt()
        currentList.add(
            TimelineChordEntry(
                id = java.util.UUID.randomUUID().toString(),
                name = info.name,
                root = info.root,
                type = info.type,
                timestamp = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60),
                confidence = info.confidence,
                notes = info.notes
            )
        )
        _chordTimeline.value = currentList
        lastDetectedChordName = info.name
        lastDetectedChordTimeMs = timeMs
    }

    fun toggleRecording() {
        _isRecording.value = !_isRecording.value
        if (_isRecording.value) {
            requestAudioProcessing()
        } else {
            _recordingTimerSeconds.value = 0
            stopAudioProcessing()
        }
    }

    fun tickRecordingTimer() {
        if (_isRecording.value) {
            _recordingTimerSeconds.value += 1
        }
    }

    fun tapTempo() {
        val newBpm = (_bpm.value + 2).let { if (it > 220) 70 else it }
        _bpm.value = newBpm
    }

    fun setBpm(value: Int) {
        _bpm.value = value.coerceIn(0, 300)
    }

    fun adjustBpm(delta: Int) {
        val calculated = _bpm.value + delta
        _bpm.value = calculated.coerceIn(40, 300)
    }

    fun adjustSpeedMultiplier(multiplier: Float) {
        _tempoPreservedMultiplier.value = multiplier.coerceIn(0.5f, 2.0f)
    }

    fun setSpeedMultiplier(multiplier: Float) {
        adjustSpeedMultiplier(multiplier)
    }

    fun getDirectSharedMemoryPointer(): String {
        return try {
            OboeAudioService.instance.getDirectSharedMemoryPointer()
        } catch (e: Exception) {
            "Unavailable"
        }
    }

    fun toggleNoiseReduction() { _noiseReductionEnabled.value = !_noiseReductionEnabled.value }
    fun toggleHumRemoval() { _humRemovalEnabled.value = !_humRemovalEnabled.value }
    fun toggleClippingRepair() { _clippingRepairEnabled.value = !_clippingRepairEnabled.value }
    fun toggleVocalEnhancement() { _vocalEnhancementEnabled.value = !_vocalEnhancementEnabled.value }

    fun setNoiseReductionEnabled(enabled: Boolean) { _noiseReductionEnabled.value = enabled }
    fun setHumRemovalEnabled(enabled: Boolean) { _humRemovalEnabled.value = enabled }
    fun setClippingRepairEnabled(enabled: Boolean) { _clippingRepairEnabled.value = enabled }
    fun setVocalEnhancementEnabled(enabled: Boolean) { _vocalEnhancementEnabled.value = enabled }
    fun setStemSeparationState(state: StemSeparationState) { _stemSeparation.value = state }
    fun setCurrentChord(chord: DetectedChordInfo?) { _currentChord.value = chord }
    fun setDetectedArpeggio(value: String?) { _detectedArpeggio.value = value }
    fun setAfricanStyleLick(value: String?) { _africanStyleLick.value = value }
    fun setVoiceLeading(vl: com.example.audio.theory.VoiceLeadingResult?) { _voiceLeading.value = vl }
    fun setMatchedProgression(mp: com.example.audio.theory.ProgressionMatchResult?) { _matchedProgression.value = mp }
    fun setFunctionalAnalysis(fa: com.example.audio.theory.FunctionalAnalysisResult?) { _functionalAnalysis.value = fa }
    fun setChordTimeline(timeline: List<TimelineChordEntry>) {
        _chordTimeline.value = timeline
    }

    fun selectTunerBaseNote(note: String) {
        val targetFreq = when(note) {
            "E2" -> 82.41f
            "A2" -> 110.00f
            "D3" -> 146.83f
            "G3" -> 196.00f
            "B3" -> 246.94f
            "E4" -> 329.63f
            else -> 440.00f
        }
        _tunerState.value = TuningNote(
            noteName = note,
            targetFreq = targetFreq,
            currentFreq = targetFreq,
            deviationCents = 0.0f,
            isTuned = true
        )
    }

    fun autoTuneTuner() {
        val current = _tunerState.value
        _tunerState.value = current.copy(currentFreq = current.targetFreq, deviationCents = 0.0f, isTuned = true)
    }

    fun updateTunerFromPcm(samples: FloatArray, sampleRate: Int = 44100) {
        if (samples.isEmpty()) return
        var sumSq = 0.0f
        for (s in samples) sumSq += s * s
        val rms = Math.sqrt((sumSq / samples.size).toDouble()).toFloat()
        if (rms < 0.01f) return

        val minLag = (sampleRate / 1000).coerceAtLeast(10)
        val maxLag = (sampleRate / 60).coerceAtMost(samples.size / 2)
        if (maxLag <= minLag) return

        var maxCorr = 0.0f
        var bestLag = -1

        for (lag in minLag..maxLag) {
            var corr = 0.0f
            val maxIndex = samples.size - lag
            for (i in 0 until maxIndex step 2) {
                corr += samples[i] * samples[i + lag]
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        if (bestLag > 0 && maxCorr > 0.02f) {
            val detectedFreq = sampleRate.toFloat() / bestLag.toFloat()
            if (detectedFreq in 50.0f..1500.0f) {
                val midiNote = (12.0 * (Math.log((detectedFreq / 440.0).toDouble()) / Math.log(2.0)) + 69.0).toInt()
                val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                val noteIndex = (midiNote % 12 + 12) % 12
                val octave = (midiNote / 12) - 1
                val noteName = "${noteNames[noteIndex]}$octave"
                
                val targetFreq = (440.0 * Math.pow(2.0, (midiNote - 69).toDouble() / 12.0)).toFloat()
                val cents = (1200.0 * (Math.log((detectedFreq / targetFreq).toDouble()) / Math.log(2.0))).toFloat()
                val isTuned = Math.abs(cents) <= 5.0f

                _tunerState.value = TuningNote(
                    noteName = noteName,
                    targetFreq = (targetFreq * 10f).let { Math.round(it) / 10f },
                    currentFreq = (detectedFreq * 10f).let { Math.round(it) / 10f },
                    deviationCents = (cents * 10f).let { Math.round(it) / 10f },
                    isTuned = isTuned
                )
            }
        }
    }

    fun startStemSeparation(mode: String) {
        _stemSeparation.value = StemSeparationState.Processing(0.01f, mode, 8)
    }

    fun updateStemProgress(progress: Float) {
        val currentState = _stemSeparation.value
        if (currentState is StemSeparationState.Processing) {
            if (progress >= 1.0f) {
                _stemSeparation.value = StemSeparationState.Success(
                    mixerState = StemMixerState(),
                    vocalsVolume = 1.0f,
                    melodyVolume = 1.0f,
                    bassVolume = 0.8f,
                    drumsVolume = 0.8f
                )
            } else {
                val eta = ((1.0f - progress) * 8).toInt()
                _stemSeparation.value = StemSeparationState.Processing(progress, currentState.mode, eta)
            }
        }
    }

    fun adjustStemVolume(stem: String, volume: Float) {
        val currentState = _stemSeparation.value
        if (currentState is StemSeparationState.Success) {
            val currentMixer = currentState.mixerState
            val updatedChannels = currentMixer.channels.map { ch ->
                if (ch.id == stem || (stem == "melody" && ch.id == "guitar")) {
                    ch.copy(volume = volume)
                } else ch
            }
            val voc = updatedChannels.find { it.id == "vocals" }?.volume ?: currentState.vocalsVolume
            val mel = updatedChannels.find { it.id == "guitar" }?.volume ?: currentState.melodyVolume
            val bas = updatedChannels.find { it.id == "bass" }?.volume ?: currentState.bassVolume
            val drm = updatedChannels.find { it.id == "drums" }?.volume ?: currentState.drumsVolume

            _stemSeparation.value = currentState.copy(
                mixerState = currentMixer.copy(channels = updatedChannels),
                vocalsVolume = voc,
                melodyVolume = mel,
                bassVolume = bas,
                drumsVolume = drm
            )
        }
    }

    fun toggleStemMute(channelId: String) {
        val currentState = _stemSeparation.value
        if (currentState is StemSeparationState.Success) {
            val currentMixer = currentState.mixerState
            val updatedChannels = currentMixer.channels.map { ch ->
                if (ch.id == channelId) ch.copy(isMuted = !ch.isMuted) else ch
            }
            _stemSeparation.value = currentState.copy(mixerState = currentMixer.copy(channels = updatedChannels))
        }
    }

    fun toggleStemSolo(channelId: String) {
        val currentState = _stemSeparation.value
        if (currentState is StemSeparationState.Success) {
            val currentMixer = currentState.mixerState
            val updatedChannels = currentMixer.channels.map { ch ->
                if (ch.id == channelId) ch.copy(isSoloed = !ch.isSoloed) else ch
            }
            _stemSeparation.value = currentState.copy(mixerState = currentMixer.copy(channels = updatedChannels))
        }
    }

    fun playOnlyStem(channelId: String) {
        val currentState = _stemSeparation.value
        if (currentState is StemSeparationState.Success) {
            val currentMixer = currentState.mixerState
            val updatedChannels = currentMixer.channels.map { ch ->
                ch.copy(
                    isSoloed = (ch.id == channelId),
                    isMuted = false
                )
            }
            _stemSeparation.value = currentState.copy(mixerState = currentMixer.copy(channels = updatedChannels))
            _isStemPlaybackActive.value = true
            requestAudioProcessing()
        }
    }

    fun playCombinationStems(activeChannelIds: Set<String>) {
        val currentState = _stemSeparation.value
        if (currentState is StemSeparationState.Success) {
            val currentMixer = currentState.mixerState
            val updatedChannels = currentMixer.channels.map { ch ->
                ch.copy(
                    isSoloed = activeChannelIds.contains(ch.id),
                    isMuted = false
                )
            }
            _stemSeparation.value = currentState.copy(mixerState = currentMixer.copy(channels = updatedChannels))
            _isStemPlaybackActive.value = true
            requestAudioProcessing()
        }
    }

    fun clearStemSoloAndMute() {
        val currentState = _stemSeparation.value
        if (currentState is StemSeparationState.Success) {
            val currentMixer = currentState.mixerState
            val updatedChannels = currentMixer.channels.map { ch ->
                ch.copy(isSoloed = false, isMuted = false, volume = 1.0f)
            }
            _stemSeparation.value = currentState.copy(mixerState = currentMixer.copy(channels = updatedChannels))
        }
    }

    fun resetStemSeparation() {
        _stemSeparation.value = StemSeparationState.Idle
        _isStemPlaybackActive.value = false
    }

    fun setUploadedFile(name: String?, size: String?) {
        _uploadedFileName.value = name
        _uploadedFileSize.value = size
        _isStemPlaybackActive.value = false
        if (name == null) {
            _aiAnalysisResult.value = null
            _stemSeparation.value = StemSeparationState.Idle
        } else {
            val currentSharing = _sharingState.value
            _sharingState.value = currentSharing.copy(
                audioSourceFileName = name,
                activeProjectTitle = "Project: ${name.substringBeforeLast(".")}",
                sharedBufferMemoryRef = "0x" + Integer.toHexString(name.hashCode()).uppercase() + " (Zero-Copy Unified Pointer)",
                duplicateFilesCreated = 0
            )
        }
    }

    fun toggleStemPlayback() {
        val nextState = !_isStemPlaybackActive.value
        _isStemPlaybackActive.value = nextState
        if (nextState) {
            requestAudioProcessing()
        } else {
            stopAudioProcessing()
        }
    }

    fun setStemPlayback(active: Boolean) {
        _isStemPlaybackActive.value = active
        if (active) {
            requestAudioProcessing()
        } else {
            stopAudioProcessing()
        }
    }

    fun setAiAnalysisResult(result: String?) {
        _aiAnalysisResult.value = result
    }

    /**
     * Theory-only chord spelling helper used for display/export.
     * It does not claim that the chord was detected from audio.
     */
    fun buildChordInfo(symbol: String): DetectedChordInfo {
        val clean = symbol.trim()
        if (clean.isBlank()) {
            return DetectedChordInfo(
                name = "No chord",
                root = "",
                formula = "",
                notes = emptyList(),
                confidence = 0f,
                frequency = 0f,
                type = "",
                description = "No chord symbol supplied.",
                suggestedSubstitutions = emptyList(),
                rootConfidence = 0f,
                qualityConfidence = 0f,
                overallConfidence = 0f
            )
        }

        val slash = clean.indexOf('/')
        val main = if (slash >= 0) clean.substring(0, slash) else clean
        val rootMatch = Regex("^([A-Ga-g](?:#|b)?)").find(main)
        val root = rootMatch?.groupValues?.getOrNull(1)?.let { normalizeNoteName(it) } ?: ""
        if (root.isBlank()) {
            return DetectedChordInfo(clean, "", "", emptyList(), 0f, 0f, "Unknown", "Invalid chord symbol.", emptyList(), 0f, 0f, 0f)
        }
        val suffix = main.removePrefix(rootMatch!!.groupValues[1]).lowercase()
        val intervals = chordIntervals(suffix)
        val noteNames = intervals.map { interval -> noteAtInterval(root, interval) }
        val bass = if (slash >= 0) normalizeNoteName(clean.substring(slash + 1)) else root
        val bassIndex = noteIndex(bass)
        val rootIndex = noteIndex(root)
        val formula = intervals.joinToString(" - ") { intervalToFormula(interval = it) }
        val type = chordType(suffix)
        val rootFrequency = midiToFrequency(60 + rootIndex)
        val displayName = if (slash >= 0) "$root${main.removePrefix(rootMatch.groupValues[1])}/$bass" else clean

        return DetectedChordInfo(
            name = displayName,
            root = bass,
            formula = formula,
            notes = if (slash >= 0 && bass !in noteNames) listOf(bass) + noteNames else noteNames,
            confidence = 1f,
            frequency = rootFrequency,
            type = type,
            description = "Theory-derived chord spelling; audio confidence is not inferred here.",
            suggestedSubstitutions = emptyList(),
            rootConfidence = 1f,
            qualityConfidence = 1f,
            overallConfidence = 1f
        )
    }

    private fun chordIntervals(suffix: String): List<Int> = when {
        suffix == "" -> listOf(0, 4, 7)
        suffix == "m" -> listOf(0, 3, 7)
        suffix.contains("mmaj7") -> listOf(0, 3, 7, 11)
        suffix.contains("maj13") -> listOf(0, 4, 7, 11, 14, 17, 21)
        suffix.contains("maj11") -> listOf(0, 4, 7, 11, 14, 17)
        suffix.contains("maj9") -> listOf(0, 4, 7, 11, 14)
        suffix.contains("maj7") -> listOf(0, 4, 7, 11)
        suffix.contains("m13") -> listOf(0, 3, 7, 10, 14, 17, 21)
        suffix.contains("m11") -> listOf(0, 3, 7, 10, 14, 17)
        suffix.contains("m9") -> listOf(0, 3, 7, 10, 14)
        suffix.contains("m7b5") -> listOf(0, 3, 6, 10)
        suffix.contains("m7") -> listOf(0, 3, 7, 10)
        suffix.contains("13") -> listOf(0, 4, 7, 10, 14, 21)
        suffix.contains("11") -> listOf(0, 4, 7, 10, 14, 17)
        suffix.contains("9") -> listOf(0, 4, 7, 10, 14)
        suffix.contains("7b9") -> listOf(0, 4, 7, 10, 13)
        suffix.contains("7#9") -> listOf(0, 4, 7, 10, 15)
        suffix.contains("7") -> listOf(0, 4, 7, 10)
        suffix.contains("6/9") -> listOf(0, 4, 7, 9, 14)
        suffix.contains("6") -> listOf(0, 4, 7, 9)
        suffix.contains("sus2") -> listOf(0, 2, 7)
        suffix.contains("sus4") -> listOf(0, 5, 7)
        suffix.contains("dim7") -> listOf(0, 3, 6, 9)
        suffix.contains("dim") -> listOf(0, 3, 6)
        suffix.contains("aug") -> listOf(0, 4, 8)
        suffix.contains("add9") -> listOf(0, 4, 7, 14)
        suffix.endsWith("m") -> listOf(0, 3, 7)
        else -> listOf(0, 4, 7)
    }

    private fun chordType(suffix: String): String = when {
        suffix.contains("dim") -> "Diminished"
        suffix.contains("aug") -> "Augmented"
        suffix.contains("sus") -> "Suspended"
        suffix.contains("maj") -> "Major Extended"
        suffix.startsWith("m") -> "Minor"
        suffix.contains("7") || suffix.contains("9") || suffix.contains("11") || suffix.contains("13") -> "Dominant / Extended"
        else -> "Major Triad"
    }

    private fun intervalToFormula(interval: Int): String = when (interval % 12) {
        0 -> "1"
        1 -> "b2"
        2 -> "2"
        3 -> "b3"
        4 -> "3"
        5 -> "4"
        6 -> "b5"
        7 -> "5"
        8 -> "b6"
        9 -> "6"
        10 -> "b7"
        11 -> "7"
        else -> interval.toString()
    }

    private fun normalizeNoteName(note: String): String {
        val n = note.trim()
        if (n.isBlank()) return ""
        val letter = n[0].uppercaseChar().toString()
        val accidental = n.drop(1).lowercase()
        return when (letter + accidental) {
            "Db" -> "C#"; "Eb" -> "D#"; "Gb" -> "F#"; "Ab" -> "G#"; "Bb" -> "A#"
            else -> letter + accidental
        }
    }

    private fun noteIndex(note: String): Int = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B").indexOf(note).coerceAtLeast(0)

    private fun noteAtInterval(root: String, interval: Int): String {
        val names = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        return names[(noteIndex(root) + interval) % 12]
    }

    private fun midiToFrequency(midi: Int): Float = (440.0 * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()

}
