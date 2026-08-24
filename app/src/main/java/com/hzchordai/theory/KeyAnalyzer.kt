package com.hzchordai.theory

import kotlin.math.sqrt

/**
 * Result of Key Analysis.
 */
data class KeyAnalysisResult(
    val keyRootPitchClass: Int,
    val keyName: String,     // e.g. "C Major", "A Minor"
    val mode: String,        // "Major" or "Minor"
    val confidence: Float    // 0.0 to 1.0 Cosine similarity score
)

/**
 * Key Detection Engine using Krumhansl-Schmuckler Chromagram Key Profiles and Cosine Similarity:
 * similarity = (A · B) / (|A| * |B|)
 * Evaluates all 24 candidate keys (12 Major + 12 Minor).
 */
object KeyAnalyzer {

    // Standard Krumhansl-Schmuckler Key Profiles (12 pitch classes relative to key root)
    private val MAJOR_PROFILE = floatArrayOf(
        6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f
    )

    private val MINOR_PROFILE = floatArrayOf(
        6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 2.69f, 3.34f, 3.17f, 3.28f
    )

    /**
     * Analyze key from 12-element chromagram array.
     */
    fun detectKey(chroma: FloatArray, useFlats: Boolean = false): KeyAnalysisResult {
        if (chroma.size < 12) {
            return KeyAnalysisResult(0, "", "", 0f)
        }

        var bestKeyRoot = 0
        var bestMode = "Major"
        var maxSimilarity = -1.0f

        // Check 12 Major keys
        for (root in 0 until 12) {
            val rotatedProfile = rotateProfile(MAJOR_PROFILE, root)
            val sim = cosineSimilarity(chroma, rotatedProfile)
            if (sim > maxSimilarity) {
                maxSimilarity = sim
                bestKeyRoot = root
                bestMode = "Major"
            }
        }

        // Check 12 Minor keys
        for (root in 0 until 12) {
            val rotatedProfile = rotateProfile(MINOR_PROFILE, root)
            val sim = cosineSimilarity(chroma, rotatedProfile)
            if (sim > maxSimilarity) {
                maxSimilarity = sim
                bestKeyRoot = root
                bestMode = "Minor"
            }
        }

        val rootName = IntervalCalculator.pitchClassToNoteName(bestKeyRoot, useFlats)
        val keyName = "$rootName $bestMode"

        return KeyAnalysisResult(
            keyRootPitchClass = bestKeyRoot,
            keyName = keyName,
            mode = bestMode,
            confidence = maxSimilarity.coerceIn(0.0f, 1.0f)
        )
    }

    /**
     * Estimate key from a collection of recently detected chord roots or notes.
     */
    fun detectKeyFromPitchClasses(pitchClasses: Collection<Int>, useFlats: Boolean = false): KeyAnalysisResult {
        val chroma = FloatArray(12)
        for (pc in pitchClasses) {
            val normalizedPc = ((pc % 12) + 12) % 12
            chroma[normalizedPc] += 1.0f
        }
        return detectKey(chroma, useFlats)
    }

    /**
     * Rotate a 12-element profile array by shift.
     */
    private fun rotateProfile(profile: FloatArray, shift: Int): FloatArray {
        val rotated = FloatArray(12)
        for (i in 0 until 12) {
            rotated[(i + shift) % 12] = profile[i]
        }
        return rotated
    }

    /**
     * Cosine similarity formula: (A · B) / (|A| * |B|)
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in 0 until 12) {
            val valA = a[i]
            val valB = b[i]
            dot += valA * valB
            normA += valA * valA
            normB += valB * valB
        }

        if (normA <= 0f || normB <= 0f) return 0.0f
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
