package com.example.audio.dsp

import kotlin.math.*

/**
 * Lightweight chord recognition model suitable for on-device training/inference.
 * 
 * Architecture:
 * - Input: 12-element chroma vector (pitch class distribution)
 * - Hidden: Optional temporal context (previous frames)
 * - Output: Probability distribution over 108 chord classes (12 roots × 9 types)
 * 
 * This class is designed to be:
 * 1. Trainable with Keras and convertible to TFLite
 * 2. Fast enough for real-time on-device inference
 * 3. Memory-efficient for Android
 * 4. Extensible for future ML models
 */
data class ChordPrediction(
    val chordName: String,
    val rootNote: String,
    val chordType: String,
    val probability: Float,  // 0.0–1.0 (softmax output)
    val confidence: Float    // 0–100 (for UI display)
)

data class MLChordRecognitionInput(
    val chromaVector: FloatArray,           // 12 elements (current frame)
    val previousChromaVectors: List<FloatArray> = emptyList()  // Optional: 1-4 previous frames for temporal context
)

/**
 * ML-ready chord recognition engine.
 * 
 * This is a placeholder for a TensorFlow Lite model that will be trained on real audio data.
 * For now, it provides the interface and data pipeline that ML models will use.
 * 
 * Training workflow:
 * 1. Collect chord-annotated audio dataset (ground truth labels)
 * 2. Extract chroma vectors from each clip
 * 3. Train Keras model: Dense(64) → ReLU → Dense(32) → ReLU → Dense(108, softmax)
 * 4. Export to TFLite with dynamic shape support
 * 5. Replace this with TFLiteInterpreter-based inference
 */
class MLChordRecognizer {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val chordTypes = arrayOf(
        "Major", "Minor", "Dim", "Aug",
        "Major7", "Minor7", "7", "Sus2", "Sus4"
    )
    
    // Placeholder: Will be replaced with TFLiteInterpreter
    private var tfLiteInterpreter: Any? = null  // Type: Interpreter (avoid compile-time dependency)
    private val modelLoaded = false
    
    /**
     * Recognizes chord from chroma vector using ML model.
     * 
     * @param input MLChordRecognitionInput containing chroma vector and optional temporal context
     * @param topN Number of top predictions to return (default 3)
     * @return List of ChordPrediction ranked by probability
     */
    fun recognizeChord(input: MLChordRecognitionInput, topN: Int = 3): List<ChordPrediction> {
        require(input.chromaVector.size == 12) { "Chroma vector must have 12 elements" }
        require(topN in 1..108) { "topN must be in [1, 108]" }
        
        // TODO: Replace with actual TFLite inference
        // return inferWithTFLite(input, topN)
        
        // For now, return fallback predictions
        return fallbackRecognition(input.chromaVector, topN)
    }
    
    /**
     * Loads a pre-trained TFLite model for chord recognition.
     * 
     * Model should have:
     * - Input: float32[1, 12] (batch_size=1, chroma_vector_size=12)
     * - Output: float32[1, 108] (batch_size=1, num_chord_classes=108)
     * 
     * @param modelPath Path to .tflite file (e.g., "models/chord_recognizer.tflite")
     * @return true if model loaded successfully
     */
    fun loadTFLiteModel(modelPath: String): Boolean {
        // TODO: Implement TFLiteInterpreter loading
        // val model = loadModelFile(modelPath)
        // tfLiteInterpreter = Interpreter(model)
        // return true
        return false
    }
    
    /**
     * Performs inference with TFLite model (once loaded).
     * 
     * @param input MLChordRecognitionInput
     * @param topN Number of top predictions
     * @return Chord predictions ranked by probability
     */
    private fun inferWithTFLite(input: MLChordRecognitionInput, topN: Int): List<ChordPrediction> {
        // TODO: Implement when TFLiteInterpreter is available
        // val chromaInput = arrayOf(input.chromaVector)
        // val outputProbs = Array(1) { FloatArray(108) }  // [1, 108] output shape
        // tfLiteInterpreter?.run(chromaInput, outputProbs)
        // return rankPredictions(outputProbs[0], topN)
        return emptyList()
    }
    
    /**
     * Fallback recognition using cosine similarity (rule-based).
     * Used until ML model is trained and deployed.
     * 
     * @param chromaVector 12-element chroma vector from audio
     * @param topN Number of top predictions
     * @return Ranked chord predictions
     */
    private fun fallbackRecognition(chromaVector: FloatArray, topN: Int): List<ChordPrediction> {
        // Define canonical chord templates (pitch class sets)
        val templates = mapOf(
            "Major" to booleanArrayOf(true, false, false, false, true, false, false, true, false, false, false, false),
            "Minor" to booleanArrayOf(true, false, false, true, false, false, false, true, false, false, false, false),
            "Dim" to booleanArrayOf(true, false, false, true, false, false, false, false, true, false, false, false),
            "Aug" to booleanArrayOf(true, false, false, false, true, false, false, false, true, false, false, false),
            "Major7" to booleanArrayOf(true, false, false, false, true, false, false, true, false, false, false, true),
            "Minor7" to booleanArrayOf(true, false, false, true, false, false, false, true, false, false, true, false),
            "7" to booleanArrayOf(true, false, false, false, true, false, false, true, false, false, true, false),
            "Sus2" to booleanArrayOf(true, false, true, false, false, false, false, true, false, false, false, false),
            "Sus4" to booleanArrayOf(true, false, false, false, false, true, false, true, false, false, false, false)
        )
        
        val predictions = mutableListOf<Pair<String, Float>>()
        
        // Generate all 108 hypotheses (12 roots × 9 types)
        for (rootIdx in 0..11) {
            for ((chordType, template) in templates) {
                val rotatedTemplate = FloatArray(12)
                for (i in 0..11) {
                    rotatedTemplate[i] = if (template[(i + rootIdx) % 12]) 1f else 0.1f
                }
                
                val similarity = cosineSimilarity(chromaVector, rotatedTemplate)
                val chordName = "${noteNames[rootIdx]}$chordType"
                predictions.add(chordName to similarity)
            }
        }
        
        // Sort by similarity and convert to probabilities
        val sorted = predictions.sortedByDescending { it.second }
        val maxSimilarity = sorted.maxOf { it.second }
        val minSimilarity = sorted.minOf { it.second }
        val range = (maxSimilarity - minSimilarity).coerceAtLeast(1e-6f)
        
        return sorted.take(topN).map { (chordName, similarity) ->
            val rootNote = chordName.takeWhile { it.isLetter() || it == '#' }
            val chordTypeStr = chordName.substring(rootNote.length)
            // Normalize similarity to [0, 1] probability
            val probability = ((similarity - minSimilarity) / range).coerceIn(0f, 1f)
            val confidence = (probability * 100f).coerceIn(0f, 100f)
            
            ChordPrediction(
                chordName = chordName,
                rootNote = rootNote,
                chordType = chordTypeStr,
                probability = probability,
                confidence = confidence
            )
        }
    }
    
    /**
     * Computes cosine similarity between two vectors.
     * High score (close to 1.0) = similar; low score = dissimilar.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA > 0 && normB > 0) dot / (sqrt(normA) * sqrt(normB)) else 0f
    }
    
    /**
     * Converts raw TFLite output probabilities to ranked predictions.
     * 
     * @param outputProbs 108-element array of class probabilities (softmax output)
     * @param topN Number of top predictions to return
     * @return Ranked ChordPrediction list
     */
    private fun rankPredictions(outputProbs: FloatArray, topN: Int): List<ChordPrediction> {
        require(outputProbs.size == 108) { "Output must have 108 elements (12 roots × 9 types)" }
        
        val predictions = outputProbs.mapIndexed { idx, prob ->
            val rootIdx = idx % 12
            val typeIdx = idx / 12
            val rootNote = noteNames[rootIdx]
            val chordType = chordTypes[typeIdx]
            val chordName = "$rootNote$chordType"
            val confidence = (prob * 100f).coerceIn(0f, 100f)
            
            ChordPrediction(
                chordName = chordName,
                rootNote = rootNote,
                chordType = chordType,
                probability = prob,
                confidence = confidence
            )
        }
        
        return predictions.sortedByDescending { it.probability }.take(topN)
    }
}
