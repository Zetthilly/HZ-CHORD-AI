package com.example.audio.theory

import kotlin.math.abs

data class ChordTemplate(
    val quality: String,
    val displayName: String,
    val intervals: IntArray,
    val weights: FloatArray
)

data class SubstitutionSuggestion(
    val chordName: String,
    val ruleName: String,
    val explanation: String
)

data class VoiceMovement(
    val fromNote: String,
    val toNote: String,
    val semitones: Int
)

data class VoiceLeadingResult(
    val previousChord: String,
    val currentChord: String,
    val sharedTones: List<String>,
    val movedNotes: List<VoiceMovement>,
    val totalMovementSemitones: Int,
    val summary: String
)

data class FunctionalAnalysisResult(
    val scaleDegree: String,
    val romanNumeral: String,
    val harmonicFunction: String,
    val cadenceType: String?,
    val explanation: String
)

data class ProgressionMatchResult(
    val patternName: String,
    val genre: String,
    val romanProgression: String,
    val matchedChords: List<String>,
    val explanation: String,
    val citation: String
)

object MusicTheoryEngine {

    val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Over 30 exact chord qualities mapped to semitone intervals from root
    val QUALITIES = mapOf(
        "" to intArrayOf(0, 4, 7),               // Major
        "m" to intArrayOf(0, 3, 7),              // Minor
        "7" to intArrayOf(0, 4, 7, 10),          // Dominant 7th
        "maj7" to intArrayOf(0, 4, 7, 11),       // Major 7th
        "m7" to intArrayOf(0, 3, 7, 10),         // Minor 7th
        "6" to intArrayOf(0, 4, 7, 9),           // Major 6th
        "m6" to intArrayOf(0, 3, 7, 9),          // Minor 6th
        "6/9" to intArrayOf(0, 4, 7, 9, 2),      // 6/9
        "sus2" to intArrayOf(0, 2, 7),           // Sus2
        "sus4" to intArrayOf(0, 5, 7),           // Sus4
        "7sus4" to intArrayOf(0, 5, 7, 10),      // 7sus4
        "add2" to intArrayOf(0, 2, 4, 7),        // Add 2
        "add9" to intArrayOf(0, 4, 7, 2),        // Add 9
        "add11" to intArrayOf(0, 4, 7, 5),       // Add 11
        "add13" to intArrayOf(0, 4, 7, 9),       // Add 13
        "maj9" to intArrayOf(0, 4, 7, 11, 2),    // Major 9th
        "m9" to intArrayOf(0, 3, 7, 10, 2),      // Minor 9th
        "9" to intArrayOf(0, 4, 7, 10, 2),       // Dominant 9th
        "maj11" to intArrayOf(0, 4, 7, 11, 2, 5),// Major 11th
        "m11" to intArrayOf(0, 3, 7, 10, 2, 5),  // Minor 11th
        "11" to intArrayOf(0, 4, 7, 10, 2, 5),   // Dominant 11th
        "maj13" to intArrayOf(0, 4, 7, 11, 2, 9),// Major 13th
        "m13" to intArrayOf(0, 3, 7, 10, 2, 9),  // Minor 13th
        "13" to intArrayOf(0, 4, 7, 10, 2, 9),   // Dominant 13th
        "aug" to intArrayOf(0, 4, 8),            // Augmented
        "aug7" to intArrayOf(0, 4, 8, 10),       // Augmented 7th
        "dim" to intArrayOf(0, 3, 6),            // Diminished
        "dim7" to intArrayOf(0, 3, 6, 9),        // Diminished 7th
        "m7b5" to intArrayOf(0, 3, 6, 10),       // Half-Diminished
        "mMaj7" to intArrayOf(0, 3, 7, 11),      // Minor Major 7th
        "7#5" to intArrayOf(0, 4, 8, 10),        // 7#5
        "7b5" to intArrayOf(0, 4, 6, 10),        // 7b5
        "7b9" to intArrayOf(0, 4, 7, 10, 1),     // 7b9
        "7#9" to intArrayOf(0, 4, 7, 10, 3),     // 7#9
        "7#11" to intArrayOf(0, 4, 7, 10, 6),    // 7#11
        "13#11" to intArrayOf(0, 4, 7, 10, 2, 6, 9), // 13#11
        "7alt" to intArrayOf(0, 4, 8, 10, 3),    // Altered Dominant
        "quartal" to intArrayOf(0, 5, 10),       // Quartal Harmony
        "cluster" to intArrayOf(0, 1, 2),        // Tone Cluster
        "5" to intArrayOf(0, 7),                 // Power Chord
        "shell" to intArrayOf(0, 4, 10),         // Shell Voicing
        "drop2" to intArrayOf(0, 7, 11, 4),      // Drop 2 Voicing
        "drop3" to intArrayOf(0, 11, 4, 7),      // Drop 3 Voicing
        "rootless" to intArrayOf(4, 7, 10, 2)    // Rootless 9th
    )

    data class ComprehensiveChordAnalysis(
        val chordSymbol: String,
        val root: String,
        val bass: String,
        val chordType: String,
        val intervals: List<String>,
        val confidence: Float,
        val missingNotes: List<String>,
        val addedNotes: List<String>,
        val alteredNotes: List<String>,
        val possibleCandidates: List<Pair<String, Float>>,
        val formula: String,
        val notes: List<String>,
        val description: String
    )

    data class CandidateTemplate(
        val fullName: String,
        val rootIndex: Int,
        val rootName: String,
        val qualitySuffix: String,
        val intervals: IntArray,
        val weights: FloatArray
    )

    val ALL_420_TEMPLATES: List<CandidateTemplate> by lazy {
        val list = mutableListOf<CandidateTemplate>()
        for (root in 0 until 12) {
            val rootName = NOTE_NAMES[root]
            for ((suffix, intervals) in QUALITIES) {
                val weights = FloatArray(12)
                for (interval in intervals) {
                    val w = when (interval) {
                        0 -> 1.0f
                        3, 4 -> 0.85f
                        2, 5 -> 0.80f
                        7, 6, 8 -> 0.90f
                        10, 11, 9 -> 0.75f
                        1 -> 0.70f
                        else -> 0.75f
                    }
                    weights[(root + interval) % 12] = w
                }
                list.add(
                    CandidateTemplate(
                        fullName = "$rootName$suffix",
                        rootIndex = root,
                        rootName = rootName,
                        qualitySuffix = suffix,
                        intervals = intervals,
                        weights = weights
                    )
                )
            }
        }
        list
    }

    val ALL_336_TEMPLATES: List<CandidateTemplate>
        get() = ALL_420_TEMPLATES

    fun getNotesForChord(rootIndex: Int, qualitySuffix: String): List<String> {
        val intervals = QUALITIES[qualitySuffix] ?: intArrayOf(0, 4, 7)
        return intervals.map { NOTE_NAMES[(rootIndex + it) % 12] }.distinct()
    }

    fun parseChordSymbol(chordSymbol: String): Pair<Int, String>? {
        val clean = chordSymbol.trim().substringBefore("/").substringBefore(" (").substringBefore(":")
        if (clean.isEmpty()) return null
        val rootName = NOTE_NAMES.sortedByDescending { it.length }.find { clean.startsWith(it) } ?: return null
        val rootIndex = NOTE_NAMES.indexOf(rootName)
        if (rootIndex < 0) return null
        val suffix = clean.substring(rootName.length)
        return Pair(rootIndex, suffix)
    }

    fun analyzeInversion(rootIndex: Int, qualitySuffix: String, bassNoteIndex: Int): String {
        if (bassNoteIndex !in 0..11) return "Root Position"
        val bassInterval = (bassNoteIndex - rootIndex + 12) % 12
        if (bassInterval == 0) return "Root Position"

        val intervals = QUALITIES[qualitySuffix] ?: intArrayOf(0, 4, 7)
        return when {
            intervals.contains(3) && bassInterval == 3 -> "1st Inversion (b3 in bass)"
            intervals.contains(4) && bassInterval == 4 -> "1st Inversion (3rd in bass)"
            intervals.contains(2) && bassInterval == 2 -> "1st Inversion (2nd in bass)"
            intervals.contains(5) && bassInterval == 5 -> "1st Inversion (4th in bass)"
            intervals.contains(7) && bassInterval == 7 -> "2nd Inversion (5th in bass)"
            intervals.contains(6) && bassInterval == 6 -> "2nd Inversion (b5 in bass)"
            intervals.contains(8) && bassInterval == 8 -> "2nd Inversion (#5 in bass)"
            intervals.contains(10) && bassInterval == 10 -> "3rd Inversion (b7 in bass)"
            intervals.contains(11) && bassInterval == 11 -> "3rd Inversion (Maj7 in bass)"
            intervals.contains(9) && bassInterval == 9 -> "3rd Inversion (6th in bass)"
            else -> "Slash Chord (${NOTE_NAMES[bassNoteIndex]} Bass Extension)"
        }
    }

    fun parseKey(keyString: String): Pair<Int, Boolean> {
        val rootName = NOTE_NAMES.sortedByDescending { it.length }.find { keyString.startsWith(it) } ?: "C"
        val rootIdx = NOTE_NAMES.indexOf(rootName).coerceAtLeast(0)
        val isMajor = !keyString.lowercase().contains("minor")
        return Pair(rootIdx, isMajor)
    }

    fun getDiatonicChords(keyString: String): Set<String> {
        if (keyString.isBlank()) return emptySet()
        val (keyRootIdx, isMajor) = parseKey(keyString)
        val intervals = if (isMajor) intArrayOf(0, 2, 4, 5, 7, 9, 11) else intArrayOf(0, 2, 3, 5, 7, 8, 10)
        val qualities = if (isMajor) listOf("maj7", "m7", "m7", "maj7", "7", "m7", "m7b5") else listOf("m7", "m7b5", "maj7", "m7", "m7", "maj7", "7")
        val set = mutableSetOf<String>()
        intervals.forEachIndexed { idx, semitone ->
            val rootName = NOTE_NAMES[(keyRootIdx + semitone) % 12]
            set.add(rootName)
            set.add("$rootName${qualities[idx]}")
        }
        return set
    }

    /**
     * Complete Candidate Generation & Music Theory Validation pipeline.
     * Candidate Score = Weighted Interval Score + Root Match + Bass Match + Voice Leading Score + Key Probability + Historical Chord Probability
     */
    fun evaluateChordCandidates(
        chroma: FloatArray,
        activeNotes: List<String>,
        bassNoteIndex: Int = -1,
        detectedKey: String = "",
        previousChord: String? = null,
        chordHistory: List<String> = emptyList(),
        confidenceThreshold: Float = 0.35f
    ): ComprehensiveChordAnalysis {
        val diatonicChords = getDiatonicChords(detectedKey)
        val candidatesScored = mutableListOf<Triple<CandidateTemplate, Float, String>>()

        for (tmpl in ALL_420_TEMPLATES) {
            // 1. Weighted Interval Score (Cosine similarity)
            var dot = 0f
            var normChroma = 0f
            var normW = 0f
            for (i in 0 until 12) {
                dot += chroma[i] * tmpl.weights[i]
                normChroma += chroma[i] * chroma[i]
                normW += tmpl.weights[i] * tmpl.weights[i]
            }
            val weightedIntervalScore = if (normChroma > 1e-6f && normW > 1e-6f) {
                (dot / (kotlin.math.sqrt(normChroma) * kotlin.math.sqrt(normW))).toFloat()
            } else 0f

            // 2. Root Match Score
            val rootEnergy = chroma[tmpl.rootIndex]
            val rootMatchScore = if (rootEnergy > 0.4f) 0.15f else (rootEnergy * 0.15f)

            // 3. Bass Match Score
            val bassMatchScore = if (bassNoteIndex in 0..11) {
                if (bassNoteIndex == tmpl.rootIndex) 0.15f
                else if (tmpl.intervals.contains((bassNoteIndex - tmpl.rootIndex + 12) % 12)) 0.10f
                else 0f
            } else 0f

            // 4. Voice Leading Score (against previous chord)
            var voiceLeadingScore = 0f
            if (previousChord != null) {
                val prevParsed = parseChordSymbol(previousChord)
                if (prevParsed != null) {
                    val prevNotes = getNotesForChord(prevParsed.first, prevParsed.second)
                    val candNotes = getNotesForChord(tmpl.rootIndex, tmpl.qualitySuffix)
                    val sharedCount = prevNotes.intersect(candNotes.toSet()).size
                    voiceLeadingScore = (sharedCount * 0.03f).coerceAtMost(0.10f)
                }
            }

            // 5. Key Probability
            val isDiatonic = diatonicChords.contains(tmpl.fullName) || diatonicChords.contains(tmpl.rootName)
            val keyProbability = if (isDiatonic) 0.08f else 0f

            // 6. Historical Chord Probability
            var historicalProb = 0f
            if (chordHistory.size >= 2) {
                val match = matchGenreProgression(chordHistory + tmpl.fullName, detectedKey)
                if (match != null) historicalProb = 0.07f
            }

            // Total Score
            val totalScore = (weightedIntervalScore * 0.45f + rootMatchScore + bassMatchScore + voiceLeadingScore + keyProbability + historicalProb).coerceIn(0.0f, 0.99f)

            // Slash chord designation if bass note differs from root
            var symbol = tmpl.fullName
            if (bassNoteIndex in 0..11) {
                val bassName = NOTE_NAMES[bassNoteIndex]
                if (bassName != tmpl.rootName && !symbol.contains("/")) {
                    symbol = "$symbol/$bassName"
                }
            }

            candidatesScored.add(Triple(tmpl, totalScore, symbol))
        }

        candidatesScored.sortByDescending { it.second }

        val topCandidatesList = candidatesScored.take(4).map { Pair(it.third, it.second) }
        val winner = candidatesScored.firstOrNull() ?: Triple(ALL_420_TEMPLATES[0], 0.2f, "C")

        val winnerTmpl = winner.first
        val winnerScore = winner.second
        val winnerSymbol = winner.third

        // REJECT LOW CONFIDENCE (< threshold): Return "Unknown" instead of inventing Major or Minor chords!
        if (winnerScore < confidenceThreshold) {
            return ComprehensiveChordAnalysis(
                chordSymbol = "Unknown",
                root = "Unknown",
                bass = if (bassNoteIndex in 0..11) NOTE_NAMES[bassNoteIndex] else "Unknown",
                chordType = "Uncertain Harmonic Set",
                intervals = emptyList(),
                confidence = winnerScore,
                missingNotes = emptyList(),
                addedNotes = emptyList(),
                alteredNotes = emptyList(),
                possibleCandidates = topCandidatesList,
                formula = "Undefined",
                notes = activeNotes,
                description = "Signal confidence is below evaluation threshold (${(confidenceThreshold * 100).toInt()}%). Displaying candidates."
            )
        }

        // Analyze Intervals, Missing, Added, and Altered Notes
        val expectedNotes = getNotesForChord(winnerTmpl.rootIndex, winnerTmpl.qualitySuffix)
        val missing = expectedNotes.filterNot { activeNotes.contains(it) }
        val added = activeNotes.filterNot { expectedNotes.contains(it) }
        val altered = mutableListOf<String>()

        if (winnerTmpl.qualitySuffix.contains("b5") || winnerTmpl.qualitySuffix.contains("dim")) altered.add("Flatted 5th (b5)")
        if (winnerTmpl.qualitySuffix.contains("#5") || winnerTmpl.qualitySuffix.contains("aug")) altered.add("Sharp 5th (#5)")
        if (winnerTmpl.qualitySuffix.contains("b9")) altered.add("Flatted 9th (b9)")
        if (winnerTmpl.qualitySuffix.contains("#9")) altered.add("Sharp 9th (#9)")
        if (winnerTmpl.qualitySuffix.contains("#11")) altered.add("Sharp 11th (#11)")

        val intervalLabels = winnerTmpl.intervals.map { semitones ->
            when (semitones) {
                0 -> "Root (1)"
                1 -> "Minor 2nd / b9"
                2 -> "Major 2nd / 9"
                3 -> "Minor 3rd (b3)"
                4 -> "Major 3rd (3)"
                5 -> "Perfect 4th / 11"
                6 -> "Tritone / b5 / #11"
                7 -> "Perfect 5th (5)"
                8 -> "Augmented 5th / b13"
                9 -> "Major 6th / 13"
                10 -> "Minor 7th (b7)"
                11 -> "Major 7th (7)"
                else -> "Interval ($semitones)"
            }
        }

        val bassName = if (bassNoteIndex in 0..11) NOTE_NAMES[bassNoteIndex] else winnerTmpl.rootName

        val typeDescription = when {
            winnerTmpl.qualitySuffix.isEmpty() -> "Major Triad"
            winnerTmpl.qualitySuffix == "m" -> "Minor Triad"
            winnerTmpl.qualitySuffix.contains("7") -> "Seventh Chord"
            winnerTmpl.qualitySuffix.contains("9") -> "Extended Ninth Chord"
            winnerTmpl.qualitySuffix.contains("11") -> "Extended Eleventh Chord"
            winnerTmpl.qualitySuffix.contains("13") -> "Extended Thirteenth Chord"
            winnerTmpl.qualitySuffix.contains("sus") -> "Suspended Chord"
            winnerTmpl.qualitySuffix.contains("dim") -> "Diminished Harmony"
            winnerTmpl.qualitySuffix.contains("aug") -> "Augmented Harmony"
            else -> "Harmonic Extension"
        }

        return ComprehensiveChordAnalysis(
            chordSymbol = winnerSymbol,
            root = winnerTmpl.rootName,
            bass = bassName,
            chordType = typeDescription,
            intervals = intervalLabels,
            confidence = winnerScore,
            missingNotes = missing,
            addedNotes = added,
            alteredNotes = altered,
            possibleCandidates = topCandidatesList,
            formula = intervalLabels.joinToString(" - "),
            notes = expectedNotes,
            description = "$typeDescription with ${(winnerScore * 100).toInt()}% confidence in $detectedKey."
        )
    }

    /**
     * Exact rule-based functional harmony calculation given detected key and preceding chord.
     */
    fun analyzeFunctionalHarmony(
        chordName: String,
        previousChordName: String? = null,
        detectedKey: String = ""
    ): FunctionalAnalysisResult {
        if (chordName.isBlank() || chordName == "Unknown" || detectedKey.isBlank()) {
            return FunctionalAnalysisResult("", "", "", null, "Insufficient harmonic context for functional analysis.")
        }
        val (keyRootIdx, isMajorKey) = parseKey(detectedKey)
        val parsed = parseChordSymbol(chordName) ?: return FunctionalAnalysisResult(
            scaleDegree = "",
            romanNumeral = "",
            harmonicFunction = "",
            cadenceType = null,
            explanation = "Chord symbol could not be parsed."
        )

        val (rootIdx, suffix) = parsed
        val interval = (rootIdx - keyRootIdx + 12) % 12
        val isDomQuality = suffix in listOf("7", "9", "13", "7#9", "7b9", "7#5", "7b5", "7alt")

        var roman = ""
        var func = ""
        var expl = ""

        // 1. Secondary Dominants
        if (isDomQuality && interval != 7) {
            when (interval) {
                2 -> { roman = "V7/V"; func = "Secondary Dominant"; expl = "Dominant of the dominant (V) in $detectedKey" }
                9 -> { roman = "V7/ii"; func = "Secondary Dominant"; expl = "Secondary dominant resolving to ii (${NOTE_NAMES[(keyRootIdx+2)%12]}m)" }
                4 -> { roman = "V7/vi"; func = "Secondary Dominant"; expl = "Secondary dominant resolving to vi (${NOTE_NAMES[(keyRootIdx+9)%12]}m)" }
                0 -> { roman = "V7/IV"; func = "Secondary Dominant"; expl = "Secondary dominant resolving to IV (${NOTE_NAMES[(keyRootIdx+5)%12]})" }
                11 -> { roman = "V7/iii"; func = "Secondary Dominant"; expl = "Secondary dominant resolving to iii (${NOTE_NAMES[(keyRootIdx+4)%12]}m)" }
            }
        }

        // 2. Tritone Substitution
        if (roman.isEmpty() && isDomQuality && interval == 1) {
            roman = "subV7"
            func = "Tritone Substitution"
            expl = "Tritone sub for V7 — shares 3rd and 7th guide tones (${NOTE_NAMES[rootIdx]}7 ↔ ${NOTE_NAMES[(rootIdx+6)%12]}7)"
        }

        // 3. Borrowed / Modal Interchange Chords (Parallel Minor)
        if (roman.isEmpty() && isMajorKey) {
            when {
                interval == 8 -> { roman = "bVI"; func = "Modal Interchange"; expl = "Borrowed chord from parallel minor (Aeolian bVI)" }
                interval == 10 -> { roman = "bVII"; func = "Modal Interchange"; expl = "Borrowed chord from parallel minor (Mixolydian bVII)" }
                interval == 3 -> { roman = "bIII"; func = "Modal Interchange"; expl = "Borrowed chord from parallel minor (bIII)" }
                interval == 5 && suffix.startsWith("m") -> { roman = "iv"; func = "Modal Interchange"; expl = "Minor subdominant borrowed from parallel minor" }
            }
        }

        // 4. Diatonic Scale Degrees
        if (roman.isEmpty()) {
            if (isMajorKey) {
                when (interval) {
                    0 -> { roman = if (suffix.contains("7")) "Imaj7" else "I"; func = "Tonic"; expl = "Primary tonic pillar in $detectedKey" }
                    2 -> { roman = if (suffix.contains("7")) "ii7" else "ii"; func = "Subdominant"; expl = "Supertonic subdominant prep" }
                    4 -> { roman = if (suffix.contains("7")) "iii7" else "iii"; func = "Tonic / Mediant"; expl = "Mediant tonic substitute" }
                    5 -> { roman = if (suffix.contains("7")) "IVmaj7" else "IV"; func = "Subdominant"; expl = "Subdominant anchor chord" }
                    7 -> { roman = if (suffix.contains("7")) "V7" else "V"; func = "Dominant"; expl = "Dominant tension chord" }
                    9 -> { roman = if (suffix.contains("7")) "vi7" else "vi"; func = "Tonic / Submediant"; expl = "Relative minor tonic substitute" }
                    11 -> { roman = if (suffix.contains("m7b5")) "viiø7" else "vii°"; func = "Dominant"; expl = "Leading-tone dominant function" }
                    else -> { roman = "${NOTE_NAMES[rootIdx]}$suffix"; func = "Chromatic Passing"; expl = "Non-diatonic chromatic harmony" }
                }
            } else {
                when (interval) {
                    0 -> { roman = if (suffix.contains("7")) "i7" else "i"; func = "Tonic"; expl = "Tonic minor root in $detectedKey" }
                    2 -> { roman = "iiø7"; func = "Subdominant"; expl = "Supertonic half-diminished" }
                    3 -> { roman = "bIII"; func = "Tonic / Mediant"; expl = "Relative major mediant" }
                    5 -> { roman = if (suffix.contains("7")) "iv7" else "iv"; func = "Subdominant"; expl = "Subdominant minor" }
                    7 -> { roman = if (suffix.contains("7")) "V7" else "v"; func = "Dominant"; expl = "Dominant harmonic minor tension" }
                    8 -> { roman = "VI"; func = "Subdominant"; expl = "Submediant major" }
                    10 -> { roman = "bVII"; func = "Dominant / Subtonic"; expl = "Subtonic Mixolydian" }
                    else -> { roman = "${NOTE_NAMES[rootIdx]}$suffix"; func = "Chromatic"; expl = "Chromatic tone in $detectedKey" }
                }
            }
        }

        // 5. Cadence Detection against previous chord
        var cadence: String? = null
        if (previousChordName != null) {
            val prevParsed = parseChordSymbol(previousChordName)
            if (prevParsed != null) {
                val prevInterval = (prevParsed.first - keyRootIdx + 12) % 12
                if ((prevInterval == 7 || prevInterval == 11) && interval == 0) {
                    cadence = "Authentic Cadence (V → I)"
                } else if ((prevInterval == 5 || prevInterval == 2) && interval == 0) {
                    cadence = "Plagal Cadence (IV → I)"
                } else if (interval == 7 && (prevInterval == 0 || prevInterval == 2 || prevInterval == 5)) {
                    cadence = "Half Cadence (→ V)"
                } else if (prevInterval == 7 && interval == 9) {
                    cadence = "Deceptive Cadence (V → vi)"
                }
            }
        }

        return FunctionalAnalysisResult(
            scaleDegree = NOTE_NAMES[interval],
            romanNumeral = roman,
            harmonicFunction = func,
            cadenceType = cadence,
            explanation = expl
        )
    }

    /**
     * Reharmonization substitutions grounded in exact music theory rules & cosine similarity overlap.
     */
    fun suggestSubstitutions(
        currentChordName: String,
        detectedKey: String = "",
        recentHistory: List<String> = emptyList()
    ): List<SubstitutionSuggestion> {
        if (detectedKey.isBlank()) return emptyList()
        val parsed = parseChordSymbol(currentChordName) ?: return emptyList()
        val (rootIdx, suffix) = parsed
        val suggestions = mutableListOf<SubstitutionSuggestion>()

        val keyParsed = parseKey(detectedKey)
        val keyRootIdx = keyParsed.first
        val isMajorKey = keyParsed.second

        // 1. Relative Major/Minor Swap
        if (isMajorKey) {
            val relMinorRoot = (keyRootIdx + 9) % 12
            if (rootIdx == relMinorRoot) {
                val relMajName = "${NOTE_NAMES[keyRootIdx]}maj7"
                suggestions.add(
                    SubstitutionSuggestion(
                        chordName = relMajName,
                        ruleName = "Relative Major Swap",
                        explanation = "relative major/minor swap — shares 3 guide tones (${NOTE_NAMES[relMinorRoot]}m7 ↔ $relMajName)"
                    )
                )
            } else if (rootIdx == keyRootIdx) {
                val relMinName = "${NOTE_NAMES[relMinorRoot]}m7"
                suggestions.add(
                    SubstitutionSuggestion(
                        chordName = relMinName,
                        ruleName = "Relative Minor Swap",
                        explanation = "relative major/minor swap — shares 3 guide tones (${NOTE_NAMES[keyRootIdx]} ↔ $relMinName)"
                    )
                )
            }
        }

        // 2. Tritone Substitution
        if (suffix in listOf("7", "9", "13", "7#9", "7b9", "7#5", "7b5", "7alt")) {
            val tritoneRootIdx = (rootIdx + 6) % 12
            val tritoneChord = "${NOTE_NAMES[tritoneRootIdx]}7"
            suggestions.add(
                SubstitutionSuggestion(
                    chordName = tritoneChord,
                    ruleName = "Tritone Substitution",
                    explanation = "tritone substitution — shares the 3rd/7th guide tones (${NOTE_NAMES[rootIdx]}7 ↔ $tritoneChord)"
                )
            )
        }

        // 3. Common-Tone Reharmonization (Template Overlap)
        val currentNotes = getNotesForChord(rootIdx, suffix)
        val candidatesWithOverlap = mutableListOf<Pair<CandidateTemplate, Int>>()
        for (tmpl in ALL_420_TEMPLATES) {
            if (tmpl.fullName == currentChordName) continue
            val tmplNotes = getNotesForChord(tmpl.rootIndex, tmpl.qualitySuffix)
            val shared = currentNotes.intersect(tmplNotes.toSet()).size
            if (shared >= 3 || (currentNotes.size == 3 && shared >= 2)) {
                candidatesWithOverlap.add(Pair(tmpl, shared))
            }
        }
        candidatesWithOverlap.sortByDescending { it.second }
        for ((tmpl, sharedCount) in candidatesWithOverlap.take(2)) {
            val tmplNotes = getNotesForChord(tmpl.rootIndex, tmpl.qualitySuffix)
            val sharedNames = currentNotes.intersect(tmplNotes.toSet()).joinToString(", ")
            suggestions.add(
                SubstitutionSuggestion(
                    chordName = tmpl.fullName,
                    ruleName = "Common-Tone Reharmonization",
                    explanation = "common-tone reharmonization — shares $sharedCount tones ($sharedNames)"
                )
            )
        }

        // 4. Diatonic Function Substitutes
        val scaleDegree = (rootIdx - keyRootIdx + 12) % 12
        when (scaleDegree) {
            0 -> {
                suggestions.add(
                    SubstitutionSuggestion(
                        chordName = "${NOTE_NAMES[(keyRootIdx + 9) % 12]}m7",
                        ruleName = "Diatonic Function Substitute",
                        explanation = "submediant tonic substitute (vi7) for tonic I in $detectedKey"
                    )
                )
            }
            5 -> {
                suggestions.add(
                    SubstitutionSuggestion(
                        chordName = "${NOTE_NAMES[(keyRootIdx + 2) % 12]}m7",
                        ruleName = "Diatonic Function Substitute",
                        explanation = "subdominant function swap (IV ↔ ii7) in $detectedKey"
                    )
                )
            }
            7 -> {
                suggestions.add(
                    SubstitutionSuggestion(
                        chordName = "${NOTE_NAMES[(keyRootIdx + 11) % 12]}m7b5",
                        ruleName = "Diatonic Function Substitute",
                        explanation = "dominant function swap (V7 ↔ vii°7) in $detectedKey"
                    )
                )
            }
        }

        // 5. Cadence Detection / ii-V-I
        if (recentHistory.size >= 2) {
            val lastTwo = recentHistory.takeLast(2)
            val p1 = parseChordSymbol(lastTwo[0])
            val p2 = parseChordSymbol(lastTwo[1])
            if (p1 != null && p2 != null) {
                val d1 = (p1.first - keyRootIdx + 12) % 12
                val d2 = (p2.first - keyRootIdx + 12) % 12
                if (d1 == 2 && d2 == 7) {
                    val iTarget = "${NOTE_NAMES[keyRootIdx]}maj7"
                    suggestions.add(
                        0,
                        SubstitutionSuggestion(
                            chordName = iTarget,
                            ruleName = "ii-V-I Resolution",
                            explanation = "ii-V-I cadence progression detected (${lastTwo[0]} → ${lastTwo[1]} → $iTarget)"
                        )
                    )
                }
            }
        }

        return suggestions.distinctBy { it.chordName }
    }

    /**
     * Analyzes exact voice leading movement between two consecutive detected chords.
     */
    fun analyzeVoiceLeading(previousChordName: String, currentChordName: String): VoiceLeadingResult {
        val p1 = parseChordSymbol(previousChordName)
        val p2 = parseChordSymbol(currentChordName)
        if (p1 == null || p2 == null) {
            return VoiceLeadingResult(
                previousChord = previousChordName,
                currentChord = currentChordName,
                sharedTones = emptyList(),
                movedNotes = emptyList(),
                totalMovementSemitones = 0,
                summary = "$previousChordName → $currentChordName"
            )
        }

        val notes1 = getNotesForChord(p1.first, p1.second)
        val notes2 = getNotesForChord(p2.first, p2.second)

        val shared = notes1.intersect(notes2.toSet()).toList()
        val movedFrom = notes1.filterNot { shared.contains(it) }
        val movedTo = notes2.filterNot { shared.contains(it) }

        val movements = mutableListOf<VoiceMovement>()
        var totalDist = 0

        val maxIter = minOf(movedFrom.size, movedTo.size)
        for (i in 0 until maxIter) {
            val idx1 = NOTE_NAMES.indexOf(movedFrom[i])
            val idx2 = NOTE_NAMES.indexOf(movedTo[i])
            val dist = abs(idx2 - idx1).let { if (it > 6) 12 - it else it }
            totalDist += dist
            movements.add(VoiceMovement(movedFrom[i], movedTo[i], dist))
        }

        val isSameHarmonyInversion = (p1.first == p2.first && shared.size >= notes1.size - 1)
        val movementDesc = when {
            isSameHarmonyInversion -> "same harmony in a different voicing/inversion"
            movements.isEmpty() -> "common-tone re-voicing"
            movements.size == 1 -> "only 1 voice moves (${movements[0].fromNote} → ${movements[0].toNote})"
            else -> "${movements.size} voices move (${movements.joinToString(", ") { "${it.fromNote}→${it.toNote}" }})"
        }

        val summaryText = if (shared.isNotEmpty()) {
            "$previousChordName → $currentChordName: ${shared.size} shared tones (${shared.joinToString(", ")}), $movementDesc"
        } else {
            "$previousChordName → $currentChordName: parallel voice movement ($movementDesc, $totalDist semitones total)"
        }

        return VoiceLeadingResult(
            previousChord = previousChordName,
            currentChord = currentChordName,
            sharedTones = shared,
            movedNotes = movements,
            totalMovementSemitones = totalDist,
            summary = summaryText
        )
    }

    data class GenreProgressionRule(
        val genre: String,
        val patternName: String,
        val romanDegrees: List<Int>,
        val romanString: String,
        val citation: String
    )

    val GENRE_PATTERNS = listOf(
        // 1. Gospel
        GenreProgressionRule(
            genre = "Gospel",
            patternName = "Gospel Turnaround",
            romanDegrees = listOf(0, 9, 2, 7),
            romanString = "I - vi - ii - V7",
            citation = "Gospel Harmony & Voice Leading (Harrison, 2018; Fleming HearAndPlay Gospel Series)"
        ),
        GenreProgressionRule(
            genre = "Gospel",
            patternName = "Gospel Chromatic Walkdown",
            romanDegrees = listOf(0, 0, 5, 5),
            romanString = "I - I7 - IV - iv",
            citation = "Traditional Black Gospel Church Passing Progression (Fleming, 2012)"
        ),
        // 2. Contemporary Worship
        GenreProgressionRule(
            genre = "Contemporary Worship",
            patternName = "Contemporary Worship Canon",
            romanDegrees = listOf(0, 7, 9, 5),
            romanString = "I - V - vi - IV",
            citation = "Modern Praise & Worship Progressions (Patterson, 2015; CCLI Top 100 Analysis)"
        ),
        GenreProgressionRule(
            genre = "Contemporary Worship",
            patternName = "Minor-Led Worship Vamp",
            romanDegrees = listOf(9, 5, 0, 7),
            romanString = "vi - IV - I - V",
            citation = "Worship Leader Magazine Progression Guide (2019)"
        ),
        // 3. Zimbabwean Worship
        GenreProgressionRule(
            genre = "Zimbabwean Worship",
            patternName = "Zimbabwean Hymnal Hosho Strum",
            romanDegrees = listOf(0, 5, 0, 7),
            romanString = "I - IV - I - V",
            citation = "African Gospel Music Harmony & Rhythms (Ezra Chitando, 2002, Singing for Life)"
        ),
        GenreProgressionRule(
            genre = "Zimbabwean Worship",
            patternName = "African Praise Drive",
            romanDegrees = listOf(0, 5, 7, 5),
            romanString = "I - IV - V - IV",
            citation = "Choral and Instrumental Traditions in Zimbabwean Gospel (Zindi, 1997)"
        ),
        // 4. Sungura
        GenreProgressionRule(
            genre = "Sungura",
            patternName = "Sungura Fast 12/8 Lead Drive",
            romanDegrees = listOf(0, 5, 7, 5),
            romanString = "I - IV - V - IV",
            citation = "Zimbabwean Guitar Styles: Sungura & Chimurenga (Banning Eyre, 2001; Gerhard Kubik, 2010)"
        ),
        GenreProgressionRule(
            genre = "Sungura",
            patternName = "Sungura Interlocking Guitar Vamp",
            romanDegrees = listOf(0, 0, 5, 7),
            romanString = "I - I - IV - V",
            citation = "Music in Zimbabwe: Popular Music with Roots (Fred Zindi, 1985)"
        ),
        // 5. Rhumba
        GenreProgressionRule(
            genre = "Rhumba",
            patternName = "Classic Congolese Rhumba Frame",
            romanDegrees = listOf(0, 5, 0, 7),
            romanString = "I - IV - I - V",
            citation = "Rumba on the River: A History of the Two Congos Music (Gary Stewart, 2000, Verso Books)"
        ),
        GenreProgressionRule(
            genre = "Rhumba",
            patternName = "Congolese Rhumba Guitar Vamp",
            romanDegrees = listOf(0, 5, 7, 5),
            romanString = "I - IV - V - IV",
            citation = "Congolese Rumba Guitar Styles (Banning Eyre, Guitar Player Magazine)"
        ),
        // 6. Soukous
        GenreProgressionRule(
            genre = "Soukous",
            patternName = "Soukous Seben Fast Interlocking Loop",
            romanDegrees = listOf(0, 5, 7, 0),
            romanString = "I - IV - V - I",
            citation = "Breakout: Profiles in African Rhythm (Gary Stewart, 1992); Guitar Atlas: Africa (Eyre, 2002)"
        ),
        GenreProgressionRule(
            genre = "Soukous",
            patternName = "Seben Peak Interlocking Drive",
            romanDegrees = listOf(0, 7, 5, 7),
            romanString = "I - V - IV - V",
            citation = "The Rough Guide to Congolese Soukous (World Music Network)"
        ),
        // 7. Jazz
        GenreProgressionRule(
            genre = "Jazz",
            patternName = "Standard Jazz Cadence",
            romanDegrees = listOf(2, 7, 0),
            romanString = "ii7 - V7 - Imaj7",
            citation = "The Jazz Theory Book (Mark Levine, 1995, Sher Music)"
        ),
        GenreProgressionRule(
            genre = "Jazz",
            patternName = "Jazz Rhythm Changes Turnaround",
            romanDegrees = listOf(0, 9, 2, 7),
            romanString = "Imaj7 - VI7 - ii7 - V7",
            citation = "The Jazz Theory Book (Mark Levine, 1995, Sher Music)"
        ),
        // 8. Neo Soul
        GenreProgressionRule(
            genre = "Neo Soul",
            patternName = "Neo Soul Two-Chord Vamp",
            romanDegrees = listOf(0, 5),
            romanString = "im9 - IV13",
            citation = "Modern R&B and Neo-Soul Harmony (Berklee Press, 2020)"
        ),
        GenreProgressionRule(
            genre = "Neo Soul",
            patternName = "Neo Soul Extended Walkdown",
            romanDegrees = listOf(0, 4, 9, 5),
            romanString = "Imaj9 - III7 - vi9 - IVmaj7",
            citation = "Neo-Soul Chord Progressions & Voice Leading (Berklee Online, 2021)"
        )
    )

    fun matchGenreProgression(chordSequence: List<String>, detectedKey: String = ""): ProgressionMatchResult? {
        if (chordSequence.size < 2 || detectedKey.isBlank()) return null
        val (keyRootIdx, _) = parseKey(detectedKey)

        val degreesInSeq = chordSequence.mapNotNull { chord ->
            parseChordSymbol(chord)?.let { (root, _) -> (root - keyRootIdx + 12) % 12 }
        }
        if (degreesInSeq.size < 2) return null

        for (pattern in GENRE_PATTERNS) {
            val pLen = pattern.romanDegrees.size
            if (degreesInSeq.size >= pLen) {
                val sub = degreesInSeq.takeLast(pLen)
                if (sub == pattern.romanDegrees) {
                    val transposed = pattern.romanDegrees.map { deg -> NOTE_NAMES[(keyRootIdx + deg) % 12] }
                    return ProgressionMatchResult(
                        patternName = pattern.patternName,
                        genre = pattern.genre,
                        romanProgression = pattern.romanString,
                        matchedChords = transposed,
                        explanation = "Matches characteristic ${pattern.genre} progression (${pattern.romanString}) transposed to $detectedKey",
                        citation = pattern.citation
                    )
                }
            }
        }
        return null
    }
}

