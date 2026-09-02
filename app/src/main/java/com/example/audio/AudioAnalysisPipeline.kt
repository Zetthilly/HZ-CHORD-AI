package com.example.audio

import android.content.Context
import android.util.Log
import com.example.audio.dsp.*

/**
 * Complete on-device music analysis pipeline integrating all DSP components.
 * 
 * Architecture:
 * 1. FFTAnalyzer — Compute frequency spectrum
 * 2. HPSSProcessor — Separate harmonic & percussive stems
 * 3. ChromaAnalyzer — Extract pitch class distribution
 * 4. MLChordRecognizer — Recognize chords (with TFLite model fallback)
 * 5. KeyDetector — Detect musical key
 * 6. BpmDetector — Estimate tempo
 * 
 * This is the core analysis engine that combines all Phase 1-4 components.
 */
data class AudioAnalysisResult(
    val chordPredictions: List<ChordPrediction> = emptyList(),
    val keyInfo: DetectedKeyInfo? = null,
    val bpmEstimate: BpmEstimate? = null,
    val harmonicSpectrum: FloatArray? = null,
    val percussiveSpectrum: FloatArray? = null,
    val chromaVector: FloatArray? = null,
    val processingTimeMs: Long = 0
)

class AudioAnalysisPipeline(
    context: Context,
    private val sampleRateHz: Int = 44100,
    private val enableTFLite: Boolean = true
) {
    companion object {
        private const val TAG = "AudioAnalysisPipeline"
    }
    
    // DSP Components
    private val fftAnalyzer = FFTAnalyzer(fftSize = 2048)
    private val hpssProcessor = HPSSProcessor(sampleRateHz = sampleRateHz)
    private val chromaAnalyzer = ChromaAnalyzer(sampleRateHz = sampleRateHz)
    private val mlChordRecognizer = MLChordRecognizer()
    private val keyDetector = KeyDetector()
    private val bpmDetector = BpmDetector(sampleRateHz = sampleRateHz)
    
    // TensorFlow Lite model (optional)
    private val tfLiteChordModel: TFLiteChordModel? = if (enableTFLite) {
        TFLiteChordModel(context, "models/chord_recognizer.tflite")
    } else {
        null
    }
    
    /**
     * Analyzes a single audio frame (e.g., 2048 samples).
     * 
     * Processing pipeline:
     * 1. Compute FFT spectrum
     * 2. Separate into harmonic & percussive components (HPSS)
     * 3. Extract chroma vector from harmonic stem
     * 4. Recognize chords using ML or rule-based fallback
     * 5. Detect key signature
     * 6. Accumulate for BPM estimation
     * 
     * @param audioFrame PCM audio samples (float, typically 2048 samples)
     * @param includeSpectrum Include raw spectral data in result (for debugging)
     * @return AudioAnalysisResult with all detected features
     */
    fun analyzeFrame(
        audioFrame: FloatArray,
        includeSpectrum: Boolean = false
    ): AudioAnalysisResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Step 1: FFT Analysis
            val spectrum = fftAnalyzer.computeMagnitudeSpectrum(audioFrame)
            
            // Step 2: HPSS Separation
            val stems = hpssProcessor.separateFrame(audioFrame)
            
            // Step 3: Chroma extraction from harmonic stem
            val harmonicSpectrum = stems.harmonic
            val chromaVector = chromaAnalyzer.computeChroma(harmonicSpectrum)
            
            // Step 4: Chord Recognition (TFLite or fallback)
            val chordPredictions = recognizeChordWithModel(chromaVector)
            
            // Step 5: Key Detection
            val keyInfo = keyDetector.detectKey(chromaVector)
            
            // Step 6: BPM Accumulation
            bpmDetector.processFrame(stems.percussive)  // Use percussive stem for onset detection
            
            val processingTime = System.currentTimeMillis() - startTime
            
            AudioAnalysisResult(
                chordPredictions = chordPredictions,
                keyInfo = keyInfo,
                bpmEstimate = null,  // Available after processFrame() calls
                harmonicSpectrum = if (includeSpectrum) stems.harmonic else null,
                percussiveSpectrum = if (includeSpectrum) stems.percussive else null,
                chromaVector = if (includeSpectrum) chromaVector else null,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Analysis frame failed: ${e.message}", e)
            AudioAnalysisResult(processingTimeMs = System.currentTimeMillis() - startTime)
        }
    }
    
    /**
     * Estimates BPM from accumulated percussive onsets.
     * 
     * Call this after processing 2-3 seconds of audio (100+ frames).
     * 
     * @return BpmEstimate with tempo and confidence
     */
    fun estimateBpm(): BpmEstimate {
        return bpmDetector.estimateBpm()
    }
    
    /**
     * Recognizes chord using TFLite model (if loaded) or fallback.
     * 
     * @param chromaVector 12-element chroma vector
     * @return Top 3 chord predictions ranked by confidence
     */
    private fun recognizeChordWithModel(chromaVector: FloatArray): List<ChordPrediction> {
        // Try TFLite model first
        if (tfLiteChordModel != null && tfLiteChordModel.isReady()) {
            val predictions = tfLiteChordModel.inferChord(chromaVector)
            if (predictions != null) {
                return rankPredictions(predictions)
            }
        }
        
        // Fallback: ML recognizer with rule-based matching
        val input = MLChordRecognitionInput(chromaVector)
        return mlChordRecognizer.recognizeChord(input, topN = 3)
    }
    
    /**
     * Converts raw TFLite output to ranked predictions.
     * 
     * @param predictions 108-element softmax array
     * @return Ranked ChordPrediction list (top 3)
     */
    private fun rankPredictions(predictions: FloatArray): List<ChordPrediction> {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val chordTypes = arrayOf(
            "Major", "Minor", "Dim", "Aug",
            "Major7", "Minor7", "7", "Sus2", "Sus4"
        )
        
        val results = predictions.mapIndexed { idx, prob ->
            val rootIdx = idx % 12
            val typeIdx = idx / 12
            val rootNote = noteNames[rootIdx]
            val chordType = chordTypes.getOrElse(typeIdx) { "Unknown" }
            
            ChordPrediction(
                chordName = "$rootNote$chordType",
                rootNote = rootNote,
                chordType = chordType,
                probability = prob,
                confidence = (prob * 100f).coerceIn(0f, 100f)
            )
        }
        
        return results.sortedByDescending { it.probability }.take(3)
    }
    
    /**
     * Resets all DSP components (for starting a new analysis session).
     */
    fun reset() {
        hpssProcessor.reset()
        bpmDetector.reset()
        Log.i(TAG, "Pipeline reset")
    }
    
    /**
     * Cleanup: Close TFLite model.
     * Call this on app shutdown.
     */
    fun close() {
        tfLiteChordModel?.close()
    }
    
    /**
     * Gets model status for debugging.
     */
    fun getStatus(): String {
        val modelStatus = if (tfLiteChordModel?.isReady() == true) {
            "TFLite ready (${tfLiteChordModel.getInputShape()?.contentToString()} -> ${tfLiteChordModel.getOutputShape()?.contentToString()})"
        } else {
            "Using fallback (rule-based)"
        }
        return "AudioAnalysisPipeline: $modelStatus"
    }
}
