package com.hzchordai.theory

/**
 * Structured Output Result of the HZ Chord AI Professional Harmonic Intelligence Engine.
 */
data class HarmonyAnalysisResult(
    val chordName: String,          // e.g. "Cmaj9/E"
    val root: String,               // e.g. "C"
    val bass: String,               // e.g. "E"
    val quality: String,            // e.g. "Major"
    val extensions: List<String>,   // e.g. ["maj7", "9"]
    val alterations: List<String>,  // e.g. ["#11"]
    val detectedNotes: List<String>,// e.g. ["C", "E", "G", "B", "D"]
    val missingNotes: List<String>, // e.g. ["G"]
    val confidence: Float,          // 0.0 to 1.0
    val keyFunction: String,        // e.g. "Imaj9" or "V7/V"
    val keyName: String,            // e.g. "C Major"
    val inversionName: String,      // e.g. "1st Inversion", "Root Position"
    val voiceLeadingDistance: Float // Total semitone voice leading motion from previous chord
)

/**
 * Main Analyzer orchestrating interval calculations, chord template matching, inversion detection,
 * key estimation, voice leading, and Bayesian scoring.
 */
class HarmonyAnalyzer(
    private val probabilityEngine: ChordProbabilityEngine = ChordProbabilityEngine()
) {

    private var currentGenreProfile: GenreProfile = StandardGenreProfile
    private var lastAnalysisResult: HarmonyAnalysisResult? = null
    private var lastPitchClasses: Set<Int> = emptySet()

    /**
     * Set the active Genre Profile.
     */
    fun setGenreProfile(profileName: String) {
        currentGenreProfile = GenreProfileRegistry.getProfile(profileName)
    }

    /**
     * Get active Genre Profile.
     */
    fun getGenreProfile(): GenreProfile = currentGenreProfile

    /**
     * Clear historical state and temporal memory.
     */
    fun clearState() {
        probabilityEngine.clearMemory()
        lastAnalysisResult = null
        lastPitchClasses = emptySet()
    }

    /**
     * Core Analysis Method.
     * Accepts input pitch classes (0..11) and optional bass pitch class.
     */
    fun analyzeHarmonies(
        pitchClasses: Collection<Int>,
        explicitBassPc: Int? = null,
        forcedGenre: String? = null,
        useFlats: Boolean = false
    ): HarmonyAnalysisResult {
        val activeProfile = if (forcedGenre != null) {
            GenreProfileRegistry.getProfile(forcedGenre)
        } else {
            currentGenreProfile
        }

        val uniquePcs = pitchClasses.map { ((it % 12) + 12) % 12 }.toSet()

        if (uniquePcs.isEmpty()) {
            return createEmptyResult("N.C.")
        }

        // Determine bass pitch class (explicitly passed or first/lowest note)
        val bassPc = explicitBassPc ?: uniquePcs.first()

        // 1. Key Estimation
        val keyAnalysis = KeyAnalyzer.detectKeyFromPitchClasses(uniquePcs, useFlats)

        // 2. Evaluate all 12 candidate roots against all templates
        val candidateScores = mutableListOf<CandidateScore>()

        for (candidateRoot in 0 until 12) {
            for (template in ChordDatabase.TEMPLATES) {
                val prob = probabilityEngine.evaluateCandidateProbability(
                    candidateRootPc = candidateRoot,
                    template = template,
                    inputPitchClasses = uniquePcs,
                    bassPitchClass = bassPc,
                    keyAnalysis = keyAnalysis,
                    genreProfile = activeProfile
                )

                if (prob > 0.05f) {
                    candidateScores.add(CandidateScore(candidateRoot, template, prob))
                }
            }
        }

        if (candidateScores.isEmpty()) {
            return createEmptyResult("Unknown")
        }

        // 3. Apply False Chord Protection (never simplify Cmaj9 to C if extensions are present)
        val winner = probabilityEngine.applyFalseChordProtection(candidateScores, uniquePcs, bassPc)
            ?: candidateScores.maxByOrNull { it.score }!!

        // 4. Inversion and Slash Chord Analysis
        val inversion = InversionDetector.analyzeInversion(
            rootPitchClass = winner.rootPc,
            bassPitchClass = bassPc,
            template = winner.template,
            useFlats = useFlats
        )

        // 5. Format Note and Chord Names
        val rootName = IntervalCalculator.pitchClassToNoteName(winner.rootPc, useFlats)
        val bassName = IntervalCalculator.pitchClassToNoteName(bassPc, useFlats)
        val fullChordName = "$rootName${winner.template.symbolSuffix}${inversion.slashSymbolSuffix}"

        val detectedNoteNames = uniquePcs.map { IntervalCalculator.pitchClassToNoteName(it, useFlats) }

        // Determine missing expected intervals
        val expectedIntervals = winner.template.intervals
        val inputIntervals = IntervalCalculator.calculateIntervalVector(uniquePcs, winner.rootPc)
        val missingPcs = expectedIntervals.filter { it !in inputIntervals }
        val missingNoteNames = missingPcs.map {
            IntervalCalculator.pitchClassToNoteName((winner.rootPc + it) % 12, useFlats)
        }

        // 6. Roman Numeral Functional Analysis
        val keyFunction = RomanNumeralAnalyzer.analyze(
            chordRootPc = winner.rootPc,
            template = winner.template,
            keyRootPc = keyAnalysis.keyRootPitchClass,
            isMinorKey = keyAnalysis.mode == "Minor",
            slashBassPc = if (inversion.isSlashChord) bassPc else null,
            useFlats = useFlats
        )

        // 7. Voice Leading Analysis
        val prevChordName = lastAnalysisResult?.chordName ?: "N.C."
        val voiceLeading = VoiceLeadingAnalyzer.analyzeTransition(
            previousChordName = prevChordName,
            prevPitchClasses = lastPitchClasses,
            currentChordName = fullChordName,
            currPitchClasses = uniquePcs,
            useFlats = useFlats
        )

        // Record in temporal memory and state
        probabilityEngine.recordChord(
            rootPc = winner.rootPc,
            quality = winner.template.quality,
            symbolSuffix = winner.template.symbolSuffix,
            fullSymbol = fullChordName,
            pitchClasses = uniquePcs
        )

        val result = HarmonyAnalysisResult(
            chordName = fullChordName,
            root = rootName,
            bass = bassName,
            quality = winner.template.quality,
            extensions = winner.template.extensions,
            alterations = winner.template.alterations,
            detectedNotes = detectedNoteNames,
            missingNotes = missingNoteNames,
            confidence = winner.score,
            keyFunction = keyFunction,
            keyName = keyAnalysis.keyName,
            inversionName = inversion.inversionName,
            voiceLeadingDistance = voiceLeading.totalMotionSemitones.toFloat()
        )

        lastAnalysisResult = result
        lastPitchClasses = uniquePcs

        return result
    }

    private fun createEmptyResult(name: String): HarmonyAnalysisResult {
        return HarmonyAnalysisResult(
            chordName = name,
            root = "-",
            bass = "-",
            quality = "None",
            extensions = emptyList(),
            alterations = emptyList(),
            detectedNotes = emptyList(),
            missingNotes = emptyList(),
            confidence = 0.0f,
            keyFunction = "-",
            keyName = "-",
            inversionName = "None",
            voiceLeadingDistance = 0.0f
        )
    }
}
