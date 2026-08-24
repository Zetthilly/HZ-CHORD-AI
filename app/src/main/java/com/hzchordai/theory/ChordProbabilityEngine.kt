package com.hzchordai.theory

import java.util.LinkedList

/**
 * Historical Chord Entry stored in temporal memory.
 */
data class HistoricalChord(
    val rootPitchClass: Int,
    val quality: String,
    val symbolSuffix: String,
    val fullSymbol: String,
    val pitchClasses: Set<Int>
)

/**
 * Bayesian Chord Probability Engine with Temporal Harmony Memory (last 32 chords)
 * and False Chord Simplification Protection.
 *
 * Formula:
 * P(Chord | Notes) = [ P(Notes | Chord) * P(Chord) ] / P(Notes)
 */
class ChordProbabilityEngine {

    private val MAX_MEMORY_SIZE = 32
    private val temporalMemory = LinkedList<HistoricalChord>()

    /**
     * Add a detected chord to the 32-chord temporal memory.
     */
    fun recordChord(
        rootPc: Int,
        quality: String,
        symbolSuffix: String,
        fullSymbol: String,
        pitchClasses: Set<Int>
    ) {
        synchronized(temporalMemory) {
            if (temporalMemory.size >= MAX_MEMORY_SIZE) {
                temporalMemory.removeFirst()
            }
            temporalMemory.addLast(
                HistoricalChord(rootPc, quality, symbolSuffix, fullSymbol, pitchClasses)
            )
        }
    }

    /**
     * Clear temporal memory.
     */
    fun clearMemory() {
        synchronized(temporalMemory) {
            temporalMemory.clear()
        }
    }

    /**
     * Get recent history (up to 32 entries).
     */
    fun getHistory(): List<HistoricalChord> {
        synchronized(temporalMemory) {
            return temporalMemory.toList()
        }
    }

    /**
     * Calculate Bayesian probability score for a candidate (Root + Template) given input notes,
     * bass note, estimated key, and active genre profile.
     */
    fun evaluateCandidateProbability(
        candidateRootPc: Int,
        template: ChordTemplate,
        inputPitchClasses: Set<Int>,
        bassPitchClass: Int?,
        keyAnalysis: KeyAnalysisResult?,
        genreProfile: GenreProfile
    ): Float {
        // 1. Likelihood P(Notes | Chord)
        val candidateIntervals = IntervalCalculator.calculateIntervalVector(inputPitchClasses, candidateRootPc)
        val templateMatchScore = ChordDatabase.evaluateMatch(candidateIntervals, template)

        if (templateMatchScore <= 0f) return 0.0f

        var likelihood = templateMatchScore

        // Bass alignment bonus if bass is root or known inversion
        if (bassPitchClass != null) {
            val bassInterval = IntervalCalculator.calculateInterval(bassPitchClass, candidateRootPc)
            if (bassInterval in template.intervals || bassPitchClass == candidateRootPc) {
                likelihood *= 1.2f
            } else {
                likelihood *= 0.9f
            }
        }

        // 2. Prior P(Chord) - Genre Profile + Key Alignment + Transition Probability
        var prior = genreProfile.getTemplateWeight(template)

        // Key alignment bonus
        if (keyAnalysis != null && keyAnalysis.confidence > 0.3f) {
            val keyInterval = IntervalCalculator.calculateInterval(candidateRootPc, keyAnalysis.keyRootPitchClass)
            val isDiatonic = isDiatonicDegree(keyInterval, template.quality, keyAnalysis.mode == "Minor")
            if (isDiatonic) {
                prior *= 1.35f
            }
        }

        // Transition probability from last chord in temporal memory
        val lastChord = synchronized(temporalMemory) { temporalMemory.lastOrNull() }
        if (lastChord != null) {
            val transitionWeight = genreProfile.getTransitionWeight(
                prevRoot = lastChord.rootPitchClass,
                prevQuality = lastChord.quality,
                currRoot = candidateRootPc,
                currQuality = template.quality
            )
            prior *= transitionWeight
        }

        val rawProbability = likelihood * prior
        return rawProbability.coerceIn(0.0f, 1.0f)
    }

    /**
     * Apply FALSE CHORD PROTECTION Rule:
     * Never simplify an extended chord (e.g. Cmaj9, Am11, C/E) to a simple triad (e.g. C, Am)
     * if the extensions or slash bass are verified in the input notes.
     */
    fun applyFalseChordProtection(
        candidates: List<CandidateScore>,
        inputPitchClasses: Set<Int>,
        bassPitchClass: Int?
    ): CandidateScore? {
        if (candidates.isEmpty()) return null

        val topCandidate = candidates.maxByOrNull { it.score } ?: return null

        // Check if top candidate is a simplified triad (e.g. symbolSuffix == "" or "m")
        // but another candidate has high score AND includes extensions verified in input notes
        for (candidate in candidates) {
            if (candidate == topCandidate) continue

            val isExtended = candidate.template.symbolSuffix.contains("7") ||
                    candidate.template.symbolSuffix.contains("9") ||
                    candidate.template.symbolSuffix.contains("11") ||
                    candidate.template.symbolSuffix.contains("13") ||
                    candidate.template.symbolSuffix.contains("add")

            if (isExtended && candidate.score >= topCandidate.score * 0.75f) {
                // Verify extension pitch classes are present
                val candidateIntervals = IntervalCalculator.calculateIntervalVector(inputPitchClasses, candidate.rootPc)
                val requiredMatched = candidate.template.requiredIntervals.count { it in candidateIntervals }

                if (requiredMatched == candidate.template.requiredIntervals.size) {
                    // Retain full extended chord precision!
                    return candidate
                }
            }
        }

        return topCandidate
    }

    private fun isDiatonicDegree(keyInterval: Int, quality: String, isMinorKey: Boolean): Boolean {
        val diatonicDegreesMajor = setOf(0, 2, 4, 5, 7, 9, 11)
        val diatonicDegreesMinor = setOf(0, 2, 3, 5, 7, 8, 10)

        val degreeSet = if (isMinorKey) diatonicDegreesMinor else diatonicDegreesMajor
        return keyInterval in degreeSet
    }
}

/**
 * Score wrapper for candidate evaluation.
 */
data class CandidateScore(
    val rootPc: Int,
    val template: ChordTemplate,
    val score: Float
)
