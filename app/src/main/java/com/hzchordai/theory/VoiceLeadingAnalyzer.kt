package com.hzchordai.theory

import kotlin.math.exp

/**
 * Result of Voice Leading Analysis between consecutive chords.
 */
data class VoiceLeadingResult(
    val previousChordName: String,
    val currentChordName: String,
    val totalMotionSemitones: Int,
    val smoothnessScore: Float, // 0.0 to 1.0 (1.0 = smoothest minimal voice motion)
    val commonTones: List<String>,
    val description: String
)

/**
 * Voice Leading Analysis Engine.
 * Calculates minimal chromatic voice motion between consecutive chord pitch sets:
 * Movement = Σ |newNote - oldNote|
 */
object VoiceLeadingAnalyzer {

    /**
     * Analyze voice leading transition from previous pitch set to current pitch set.
     */
    fun analyzeTransition(
        previousChordName: String,
        prevPitchClasses: Set<Int>,
        currentChordName: String,
        currPitchClasses: Set<Int>,
        useFlats: Boolean = false
    ): VoiceLeadingResult {
        if (prevPitchClasses.isEmpty() || currPitchClasses.isEmpty()) {
            return VoiceLeadingResult(
                previousChordName = previousChordName,
                currentChordName = currentChordName,
                totalMotionSemitones = 0,
                smoothnessScore = 1.0f,
                commonTones = emptyList(),
                description = "Initial chord or no voice data"
            )
        }

        // Common tones
        val commonPcs = prevPitchClasses.intersect(currPitchClasses)
        val commonNoteNames = commonPcs.map { IntervalCalculator.pitchClassToNoteName(it, useFlats) }

        // Calculate minimal voice motion by mapping each current pitch to nearest previous pitch
        var totalMotion = 0
        val currList = currPitchClasses.toList()
        val prevList = prevPitchClasses.toList()

        for (currPc in currList) {
            val minDistanceToPrev = prevList.minOf { prevPc ->
                IntervalCalculator.shortestDistance(currPc, prevPc)
            }
            totalMotion += minDistanceToPrev
        }

        // Smoothness exponential decay score: score = exp(-0.15 * totalMotion)
        val score = exp(-0.15f * totalMotion.toFloat()).coerceIn(0.0f, 1.0f)

        val desc = when {
            totalMotion == 0 -> "Static harmony / same pitch set"
            totalMotion <= 2 -> "Ultra-smooth voice leading (${commonNoteNames.size} common tones)"
            totalMotion <= 5 -> "Smooth step-wise voice leading"
            totalMotion <= 8 -> "Moderate vocal movement"
            else -> "Wide leap in voice leading"
        }

        return VoiceLeadingResult(
            previousChordName = previousChordName,
            currentChordName = currentChordName,
            totalMotionSemitones = totalMotion,
            smoothnessScore = score,
            commonTones = commonNoteNames,
            description = desc
        )
    }
}
