package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.audio.AudioAnalysisPipeline
import com.example.audio.ChordPrediction
import android.util.Log
import android.content.Context

/**
 * Data class for workstation UI state.
 * Represents the current state of the chord detector and analysis displays.
 */
data class WorkstationState(
    val detectedChords: List<ChordPrediction> = emptyList(),
    val primaryChord: String = "--",
    val chordConfidence: Float = 0f,
    val keySignature: String = "--",
    val keyConfidence: Float = 0f,
    val tempo: Float = 0f,
    val tempoConfidence: Float = 0f,
    val isLive: Boolean = false,
    val isRecording: Boolean = false,
    val displayMode: DisplayMode = DisplayMode.CHORDS,
    val errorMessage: String? = null
)

enum class DisplayMode {
    CHORDS,      // Show chord detection
    KEY,         // Show key detection
    TEMPO,       // Show BPM estimation
    FULL,        // Show all three
    SPECTRUM     // Show frequency spectrum
}

/**
 * Main ViewModel for HZ-CHORD-AI workstation.
 * 
 * Manages:
 * 1. Audio analysis pipeline (FFT → HPSS → Chroma → ML)
 * 2. Display mode and visualization
 * 3. Recording and playback state
 * 4. User interactions (start, stop, reset)
 * 
 * Exposes state as StateFlow for reactive Compose UI updates.
 */
class WorkstationViewModel(
    private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "WorkstationVM"
    }
    
    // UI State
    private val _workstationState = MutableStateFlow(WorkstationState())
    val workstationState: StateFlow<WorkstationState> = _workstationState.asStateFlow()
    
    // Analysis Pipeline
    private val analysisViewModel: AudioAnalysisViewModel
    private val pipeline: AudioAnalysisPipeline
    
    init {
        pipeline = AudioAnalysisPipeline(context, enableTFLite = true)
        analysisViewModel = AudioAnalysisViewModel(pipeline)
        
        // Subscribe to analysis updates
        viewModelScope.launch {
            analysisViewModel.analysisState.collect { analysisState ->
                updateWorkstationState(analysisState)
            }
        }
        
        Log.i(TAG, "WorkstationViewModel initialized")
    }
    
    /**
     * Processes incoming audio frame from recorder/playback.
     * 
     * @param audioFrame PCM samples (float array, typically 2048 samples)
     */
    fun processAudioFrame(audioFrame: FloatArray) {
        analysisViewModel.analyzeAudioFrame(audioFrame)
    }
    
    /**
     * Updates workstation state from analysis updates.
     */
    private fun updateWorkstationState(analysisState: AudioAnalysisState) {
        val keyStr = analysisState.keyInfo?.let {
            "${it.key.rootNote} ${it.mode.name}"
        } ?: "--"
        
        val keyConf = analysisState.keyInfo?.confidence ?: 0f
        
        val tempo = analysisState.bpmEstimate?.bpm ?: 0f
        val tempoConf = analysisState.bpmEstimate?.confidence ?: 0f
        
        _workstationState.value = WorkstationState(
            detectedChords = analysisState.chordPredictions,
            primaryChord = analysisState.currentChord,
            chordConfidence = analysisState.chordConfidence,
            keySignature = keyStr,
            keyConfidence = keyConf,
            tempo = tempo,
            tempoConfidence = tempoConf,
            isLive = analysisState.isAnalyzing,
            errorMessage = analysisState.errorMessage
        )
    }
    
    /**
     * Starts live chord detection.
     */
    fun startLiveAnalysis() {
        analysisViewModel.startAnalysis()
        _workstationState.value = _workstationState.value.copy(isLive = true)
        Log.i(TAG, "Live analysis started")
    }
    
    /**
     * Stops live chord detection.
     */
    fun stopLiveAnalysis() {
        analysisViewModel.stopAnalysis()
        _workstationState.value = _workstationState.value.copy(isLive = false)
        Log.i(TAG, "Live analysis stopped")
    }
    
    /**
     * Starts recording audio for offline analysis.
     */
    fun startRecording() {
        _workstationState.value = _workstationState.value.copy(isRecording = true)
        analysisViewModel.startAnalysis()
        Log.i(TAG, "Recording started")
    }
    
    /**
     * Stops recording.
     */
    fun stopRecording() {
        _workstationState.value = _workstationState.value.copy(isRecording = false)
        analysisViewModel.stopAnalysis()
        Log.i(TAG, "Recording stopped")
    }
    
    /**
     * Switches display mode.
     */
    fun setDisplayMode(mode: DisplayMode) {
        _workstationState.value = _workstationState.value.copy(displayMode = mode)
        Log.i(TAG, "Display mode changed to: $mode")
    }
    
    /**
     * Resets analysis state.
     */
    fun reset() {
        analysisViewModel.reset()
        _workstationState.value = WorkstationState()
        Log.i(TAG, "Workstation reset")
    }
    
    /**
     * Gets debug information.
     */
    fun getDebugInfo(): String {
        return """
            Pipeline: ${pipeline.getStatus()}
            Frame Count: ${_workstationState.value.detectedChords.size}
            Current Chord: ${_workstationState.value.primaryChord}
        """.trimIndent()
    }
    
    /**
     * Cleanup on destroy.
     */
    override fun onCleared() {
        super.onCleared()
        pipeline.close()
        Log.i(TAG, "WorkstationViewModel cleared")
    }
}
