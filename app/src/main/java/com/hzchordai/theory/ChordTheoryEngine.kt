package com.hzchordai.theory

/**
 * HZ CHORD AI Professional Harmonic Intelligence Engine.
 * Thread-safe master facade and primary engine entry point.
 */
class ChordTheoryEngine {

    private val probabilityEngine = ChordProbabilityEngine()
    private val analyzer = HarmonyAnalyzer(probabilityEngine)

    /**
     * Analyze harmonic structure from a list of pitch classes (0..11).
     */
    @Synchronized
    fun analyzePitchClasses(
        pitchClasses: List<Int>,
        bassPc: Int? = null,
        genre: String = "Standard",
        useFlats: Boolean = false
    ): HarmonyAnalysisResult {
        return analyzer.analyzeHarmonies(
            pitchClasses = pitchClasses,
            explicitBassPc = bassPc,
            forcedGenre = genre,
            useFlats = useFlats
        )
    }

    /**
     * Analyze harmonic structure from raw detected frequencies and magnitudes from DSP / AI audio engine.
     */
    @Synchronized
    fun analyzeFrequencies(
        frequencies: List<Double>,
        magnitudes: List<Double>,
        genre: String = "Standard",
        useFlats: Boolean = false
    ): HarmonyAnalysisResult {
        if (frequencies.isEmpty() || magnitudes.isEmpty()) {
            return createEmptyResult("N.C.")
        }

        // Aggregate chromagram vector
        val chroma = IntervalCalculator.chromagramFromFrequencies(frequencies, magnitudes)

        // Select pitch classes with significant energy (> 10% of max peak)
        val maxPeak = chroma.maxOrNull() ?: 0.0f
        val pitchClasses = mutableListOf<Int>()

        if (maxPeak > 0.01f) {
            val threshold = maxPeak * 0.15f
            for (i in 0 until 12) {
                if (chroma[i] >= threshold) {
                    pitchClasses.add(i)
                }
            }
        }

        // Lowest frequency as bass note candidate
        val lowestFreqIndex = frequencies.indices.minByOrNull { frequencies[it] }
        val bassPc = if (lowestFreqIndex != null && frequencies[lowestFreqIndex] > 20.0) {
            IntervalCalculator.frequencyToPitchClass(frequencies[lowestFreqIndex])
        } else {
            pitchClasses.firstOrNull()
        }

        return analyzer.analyzeHarmonies(
            pitchClasses = pitchClasses,
            explicitBassPc = bassPc,
            forcedGenre = genre,
            useFlats = useFlats
        )
    }

    /**
     * Analyze harmonic structure from note name strings (e.g. ["C", "E", "G", "B", "D"]).
     */
    @Synchronized
    fun analyzeNoteNames(
        noteNames: List<String>,
        bassNote: String? = null,
        genre: String = "Standard",
        useFlats: Boolean = false
    ): HarmonyAnalysisResult {
        val pcs = noteNames.map { IntervalCalculator.noteNameToPitchClass(it) }
        val bassPc = bassNote?.let { IntervalCalculator.noteNameToPitchClass(it) }

        return analyzer.analyzeHarmonies(
            pitchClasses = pcs,
            explicitBassPc = bassPc,
            forcedGenre = genre,
            useFlats = useFlats
        )
    }

    /**
     * Set active global Genre Profile ("Standard", "Worship", "Gospel", "Jazz", "Sungura").
     */
    fun setGenreProfile(genre: String) {
        analyzer.setGenreProfile(genre)
    }

    /**
     * Get active Genre Profile name.
     */
    fun getActiveGenreName(): String = analyzer.getGenreProfile().name

    /**
     * Reset engine history and temporal memory.
     */
    fun clearHistory() {
        analyzer.clearState()
    }

    /**
     * Get up to 32 recent chords from temporal memory.
     */
    fun getRecentHistory(): List<HistoricalChord> {
        return probabilityEngine.getHistory()
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
