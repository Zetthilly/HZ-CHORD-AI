package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.audio.*
import com.example.audio.dsp.*
import android.util.Log

/**
 * Data class representing the current audio analysis state.
 * Exposed to UI via StateFlow for reactive updates.
 */
data class AudioAnalysisState(
    val isAnalyzing: Boolean = false,
    val chordPredictions: List<ChordPrediction> = emptyList(),
    val currentChord: String = "--",
    val chordConfidence: Float = 0f,
    val keyInfo: DetectedKeyInfo? = null,
    val bpmEstimate: BpmEstimate? = null,
    val processingTimeMs: Long = 0,
    val errorMessage: String? = null
)

/**
 * ViewModel for real-time audio analysis.
 * 
 * Responsibilities:
 * 1. Manage audio frame processing pipeline
 * 2. Update UI state reactively (via StateFlow)
 * 3. Coordinate between audio engine and UI
 * 4. Handle error states and recovery
 * 
 * Integration points:
 * - Receives PCM audio frames from HZAudioEngine
 * - Outputs AudioAnalysisState for Compose UI
 * - Manages lifecycle (start/stop analysis)
 */
class AudioAnalysisViewModel(
    private val pipeline: AudioAnalysisPipeline
) : ViewModel() {
    
    companion object {
        private const val TAG = "AudioAnalysisViewModel"
        private const val FRAME_SMOOTHING_WINDOW = 3  // Average predictions over N frames
    }
    
    // UI State (exposed as StateFlow for Compose)
    private val _analysisState = MutableStateFlow(AudioAnalysisState())
    val analysisState: StateFlow<AudioAnalysisState> = _analysisState.asStateFlow()
    
    // Internal tracking
    private var frameCount = 0
    private val smoothingBuffer = mutableListOf<List<ChordPrediction>>()
    private var isProcessing = false
    
    /**
     * Processes a single audio frame and updates UI state.
     * 
     * Called from HZAudioEngine on each frame (typically every 46ms at 44.1kHz).
     * Processing happens on Default dispatcher to avoid blocking UI thread.
     * 
     * @param audioFrame PCM audio samples (float, typically 2048 samples)
     */
    fun analyzeAudioFrame(audioFrame: FloatArray) {
        if (isProcessing) {
            Log.w(TAG, "Frame dropped: still processing previous frame")
            return
        }
        
        viewModelScope.launch(Dispatchers.Default) {
            isProcessing = true
            try {
                // Run full analysis pipeline
                val result = pipeline.analyzeFrame(audioFrame, includeSpectrum = false)
                
                // Apply temporal smoothing to chord predictions
                smoothingBuffer.add(result.chordPredictions)
                if (smoothingBuffer.size > FRAME_SMOOTHING_WINDOW) {
                    smoothingBuffer.removeAt(0)
                }
                
                val smoothedChords = smoothPredictions(smoothingBuffer)
                
                // Estimate BPM after sufficient frames
                val bpmEstimate = if (frameCount % 100 == 0) {
                    pipeline.estimateBpm()
                } else {
                    null
                }
                
                frameCount++
                
                // Update UI state
                val newState = AudioAnalysisState(
                    isAnalyzing = true,
                    chordPredictions = smoothedChords,
                    currentChord = smoothedChords.firstOrNull()?.chordName ?: "--",
                    chordConfidence = smoothedChords.firstOrNull()?.confidence ?: 0f,
                    keyInfo = result.keyInfo,
                    bpmEstimate = bpmEstimate,
                    processingTimeMs = result.processingTimeMs,
                    errorMessage = null
                )
                
                _analysisState.value = newState
                
            } catch (e: Exception) {
                Log.e(TAG, "Analysis frame failed: ${e.message}", e)
                _analysisState.value = _analysisState.value.copy(
                    errorMessage = "Analysis error: ${e.message}"
                )
            } finally {
                isProcessing = false
            }
        }
    }
    
    /**
     * Smooths chord predictions over multiple frames using majority voting.
     * 
     * Reduces false positives and creates more stable chord display.
     * 
     * @param predictionBuffer List of chord predictions from recent frames
     * @return Smoothed predictions (averaged probabilities)
     */
    private fun smoothPredictions(predictionBuffer: List<List<ChordPrediction>>): List<ChordPrediction> {
        if (predictionBuffer.isEmpty()) return emptyList()
        if (predictionBuffer.size == 1) return predictionBuffer[0]
        
        // Average confidence across all predictions in buffer
        val allChords = mutableMapOf<String, Float>()
        
        for (predictions in predictionBuffer) {
            for (chord in predictions) {
                val current = allChords[chord.chordName] ?: 0f
                allChords[chord.chordName] = current + chord.confidence
            }
        }
        
        // Normalize by number of frames and sort
        return allChords
            .map { (chordName, totalConfidence) ->
                val avgConfidence = totalConfidence / predictionBuffer.size
                val rootNote = chordName.takeWhile { it.isLetter() || it == '#' }
                val chordType = chordName.substring(rootNote.length)
                
                ChordPrediction(
                    chordName = chordName,
                    rootNote = rootNote,
                    chordType = chordType,
                    probability = avgConfidence / 100f,  // Convert back to [0,1]
                    confidence = avgConfidence
                )
            }
            .sortedByDescending { it.confidence }
            .take(3)
    }
    
    /**
     * Starts analysis session.
     * Call when user starts playback or recording.
     */
    fun startAnalysis() {
        _analysisState.value = AudioAnalysisState(isAnalyzing = true)
        frameCount = 0
        smoothingBuffer.clear()
        pipeline.reset()
        Log.i(TAG, "Analysis started")
    }
    
    /**
     * Stops analysis session and finalizes BPM estimate.
     * Call when user stops playback or exits analysis mode.
     */
    fun stopAnalysis() {
        val finalBpm = pipeline.estimateBpm()
        _analysisState.value = _analysisState.value.copy(
            isAnalyzing = false,
            bpmEstimate = finalBpm
        )
        Log.i(TAG, "Analysis stopped. Final BPM: ${finalBpm.bpm}±${finalBpm.confidence}%")
    }
    
    /**
     * Resets analysis state (for clearing between sessions).
     */
    fun reset() {
        _analysisState.value = AudioAnalysisState()
        frameCount = 0
        smoothingBuffer.clear()
        pipeline.reset()
        Log.i(TAG, "Analysis reset")
    }
    
    /**
     * Gets pipeline status for debugging.
     */
    fun getPipelineStatus(): String {
        return pipeline.getStatus()
    }
    
    /**
     * Cleanup on ViewModel destruction.
     */
    override fun onCleared() {
        super.onCleared()
        pipeline.close()
        Log.i(TAG, "ViewModel cleared, pipeline closed")
    }
}
