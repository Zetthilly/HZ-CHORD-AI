package com.example.audio.dsp

import kotlin.math.*

enum class MusicalKey(val rootNote: String) {
    C("C"), Csharp("C#"), D("D"), Dsharp("D#"), E("E"), F("F"),
    Fsharp("F#"), G("G"), Gsharp("G#"), A("A"), Asharp("A#"), B("B")
}

enum class KeyMode {
    MAJOR, MINOR
}

data class DetectedKeyInfo(
    val key: MusicalKey,
    val mode: KeyMode,
    val confidence: Float // 0–100
)

/**
 * Key detection using Krumhansl-Schmuckler algorithm.
 * Compares chroma vector to major/minor pitch profiles.
 */
class KeyDetector {
    // Krumhansl major profile (relative strength of each pitch class in major keys)
    private val majorProfile = floatArrayOf(
        0.1524f, 0.0429f, 0.1225f, 0.0408f, 0.0931f, 0.1049f, 0.0548f, 0.1957f,
        0.0519f, 0.1309f, 0.0500f, 0.0934f
    )
    
    // Krumhansl minor profile
    private val minorProfile = floatArrayOf(
        0.1411f, 0.0500f, 0.1113f, 0.0927f, 0.0760f, 0.0982f, 0.0789f, 0.1670f,
        0.0725f, 0.0949f, 0.1490f, 0.0821f
    )
    
    /**
     * Detects musical key from chroma vector.
     * @param chromaVector 12-element pitch class distribution
     * @return (key, mode, confidence 0–100)
     */
    fun detectKey(chromaVector: FloatArray): DetectedKeyInfo {
        require(chromaVector.size == 12) { "Chroma vector must have 12 elements" }
        
        var bestScore = -Float.MAX_VALUE
        var bestKeyIdx = 0
        var isMajor = true
        
        // Test all 12 keys × 2 modes = 24 hypothesis
        for (mode in listOf(KeyMode.MAJOR, KeyMode.MINOR)) {
            val profile = if (mode == KeyMode.MAJOR) majorProfile else minorProfile
            
            for (keyIdx in 0..11) {
                // Rotate profile to match potential key root
                val rotatedProfile = FloatArray(12)
                for (i in 0..11) {
                    rotatedProfile[i] = profile[(i + keyIdx) % 12]
                }
                
                // Compute correlation (cosine similarity)
                val score = cosineSimilarity(chromaVector, rotatedProfile)
                
                if (score > bestScore) {
                    bestScore = score
                    bestKeyIdx = keyIdx
                    isMajor = (mode == KeyMode.MAJOR)
                }
            }
        }
        
        val confidence = ((bestScore + 1f) / 2f * 100f).coerceIn(0f, 100f)
        return DetectedKeyInfo(
            key = MusicalKey.values()[bestKeyIdx],
            mode = if (isMajor) KeyMode.MAJOR else KeyMode.MINOR,
            confidence = confidence
        )
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        return if (normA > 0 && normB > 0) {
            dotProduct / (sqrt(normA) * sqrt(normB))
        } else 0f
    }
}
