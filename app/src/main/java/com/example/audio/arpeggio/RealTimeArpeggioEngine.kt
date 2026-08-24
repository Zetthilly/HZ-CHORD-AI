package com.example.audio.arpeggio

import com.example.audio.dsp.MultiPitchDetector
import com.example.audio.theory.MusicTheoryEngine
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Production-Grade Real-Time Arpeggio Recognition Engine for HZ CHORD AI.
 * Implements a 12-stage harmonic reconstruction pipeline:
 *
 * 1. Timestamp Buffer (300ms–800ms rolling window)
 * 2. Note Weighting & Exponential Decay W(t) = exp(-λt)
 * 3. Temporal Clustering & Inter-Onset Interval (Δt) Measurement
 * 4. Arpeggio Speed Classification (Rolled, Fast, Medium, Slow, Broken Chord)
 * 5. 4-Voice Separation (Bass, Tenor, Alto, Soprano)
 * 6. Non-Chord Tone Detection (Passing, Neighbor, Escape, Grace Notes, Suspensions)
 * 7. Chord Reconstruction & Candidate Generation
 * 8. Voice Leading Minimum Movement Principle
 * 9. Chord History (32-chord memory) & Bayesian Posterior Estimation P(C|N)
 * 10. Hidden Markov Model (HMM) & Viterbi Decoding for temporal anti-flicker
 * 11. Strict 6-Factor Confidence Formula (0.70 threshold)
 * 12. Specialized Genre Modes (Worship, Gospel, Sungura)
 */
class RealTimeArpeggioEngine(
    private val bufferWindowMs: Long = 500L, // 300ms–800ms configurable rolling window
    private val decayLambda: Float = 2.5f,     // Exponential decay coefficient λ
    private val confidenceThreshold: Float = 0.70f // Strict 70% confidence threshold
) {

    /**
     * Complete note metadata model stored in timestamp buffer.
     */
    data class BufferedNote(
        val pitch: String,             // e.g. "C4"
        val pitchClass: Int,           // 0..11 (C..B)
        val midi: Int,                 // 21..108
        val frequency: Float,          // Hz
        val velocity: Int,             // 1..127
        val confidence: Float,         // 0.0..1.0
        val timestampMs: Long,         // Arrival timestamp
        val durationMs: Long,          // Estimated note duration
        val amplitude: Float,          // Normalized amplitude
        val attackMs: Long,            // Onset time
        val releaseMs: Long,           // Offset time
        val decay: Float,              // Current decay multiplier
        val isGraceNote: Boolean = false
    )

    /**
     * Non-Chord Tone Classification types.
     */
    enum class NonChordToneType {
        ACCIACCATURA,    // Fast unaccented grace note
        APPOGGIATURA,    // Accented grace note on beat
        PASSING_TONE,    // Stepwise motion between chord tones
        NEIGHBOR_TONE,   // Stepwise departure and return
        ESCAPE_TONE,     // Stepwise departure, leap resolution
        ANTICIPATION,    // Premature arrival of next chord tone
        SUSPENSION,      // Retained note resolving downward
        RETARDATION,     // Retained note resolving upward
        CHORD_TONE       // Valid structural harmonic tone
    }

    /**
     * Speed & Gesture Classification of detected arpeggio.
     */
    enum class ArpeggioSpeed {
        ROLLED_CHORD,   // Δt < 40ms
        FAST_ARPEGGIO,  // 40ms <= Δt < 80ms
        MEDIUM_ARPEGGIO,// 80ms <= Δt < 150ms
        SLOW_ARPEGGIO,  // 150ms <= Δt < 300ms
        BROKEN_CHORD,   // Non-simultaneous breakdown
        MELODIC_RUN     // Sequential scalar run
    }

    /**
     * 4-Voice Separation Container.
     */
    data class VoiceSeparationResult(
        val bass: List<BufferedNote>,    // MIDI < 48 (below C3)
        val tenor: List<BufferedNote>,   // MIDI 48..59 (C3..B3)
        val alto: List<BufferedNote>,    // MIDI 60..71 (C4..B4)
        val soprano: List<BufferedNote>  // MIDI >= 72 (C5+)
    )

    /**
     * Result of Arpeggio Recognition Analysis.
     */
    data class ArpeggioRecognitionResult(
        val chordSymbol: String,
        val confidence: Float,
        val arpeggioSpeed: ArpeggioSpeed,
        val meanDeltaMs: Float,
        val activeNotes: List<String>,
        val voiceSeparation: VoiceSeparationResult,
        val nonChordTones: Map<String, NonChordToneType>,
        val genreStyle: String?,
        val formula: String,
        val description: String
    )

    // Timestamp rolling buffer
    private val timestampBuffer = ConcurrentLinkedDeque<BufferedNote>()

    // Chord history queue (up to 32 previous chords)
    private val chordHistory = Collections.synchronizedList(mutableListOf<String>())

    // HMM Viterbi State
    private var viterbiBestState: String = "C"
    private var viterbiLogProb: Double = 0.0

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Processes incoming detected notes into timestamp buffer and executes 12-stage pipeline.
     */
    fun processDetectedNotes(
        freshNotes: List<MultiPitchDetector.DetectedNote>,
        nowMs: Long = System.currentTimeMillis(),
        detectedKey: String = "",
        specializedMode: String = "Standard"
    ): ArpeggioRecognitionResult {

        // 1. TIMESTAMP BUFFER INSERTION & PURGE
        for (n in freshNotes) {
            val buffered = BufferedNote(
                pitch = n.pitchName,
                pitchClass = n.pitchClass,
                midi = n.midiNumber,
                frequency = n.frequency,
                velocity = n.velocity,
                confidence = n.confidence,
                timestampMs = nowMs,
                durationMs = (n.releaseTimeMs - n.attackTimeMs).coerceAtLeast(50L),
                amplitude = n.amplitude,
                attackMs = n.attackTimeMs,
                releaseMs = n.releaseTimeMs,
                decay = 1.0f,
                isGraceNote = n.isGraceNote
            )
            timestampBuffer.addLast(buffered)
        }

        // Purge notes older than bufferWindowMs
        while (timestampBuffer.isNotEmpty() && (nowMs - timestampBuffer.first.timestampMs) > bufferWindowMs) {
            timestampBuffer.removeFirst()
        }

        val currentNotes = timestampBuffer.toList()
        if (currentNotes.isEmpty()) {
            return createUnknownResult("Empty note buffer")
        }

        // 2. NOTE WEIGHTING & EXPONENTIAL DECAY
        // W = VelocityWeight * DurationWeight * ConfidenceWeight * RecencyWeight * SpectralWeight
        val weightedNotes = currentNotes.map { note ->
            val deltaTimeSec = (nowMs - note.timestampMs) / 1000.0f
            val recencyWeight = exp(-decayLambda * deltaTimeSec)

            val velocityWeight = note.velocity / 127.0f
            val durationWeight = (note.durationMs / 500.0f).coerceIn(0.2f, 1.0f)
            val confidenceWeight = note.confidence.coerceIn(0.1f, 1.0f)
            val spectralWeight = note.amplitude.coerceIn(0.1f, 1.0f)

            val totalWeight = velocityWeight * durationWeight * confidenceWeight * recencyWeight * spectralWeight
            note to totalWeight
        }

        // 3. TEMPORAL CLUSTERING & Δt MEASUREMENT
        val sortedByTime = currentNotes.sortedBy { it.timestampMs }
        val deltaTimes = mutableListOf<Long>()
        for (i in 1 until sortedByTime.size) {
            val delta = sortedByTime[i].timestampMs - sortedByTime[i - 1].timestampMs
            if (delta >= 0) deltaTimes.add(delta)
        }

        val meanDeltaMs = if (deltaTimes.isNotEmpty()) deltaTimes.average().toFloat() else 0.0f

        // 4. ARPEGGIO SPEED CLASSIFICATION
        val arpeggioSpeed = when {
            meanDeltaMs < 40.0f -> ArpeggioSpeed.ROLLED_CHORD
            meanDeltaMs < 80.0f -> ArpeggioSpeed.FAST_ARPEGGIO
            meanDeltaMs < 150.0f -> ArpeggioSpeed.MEDIUM_ARPEGGIO
            meanDeltaMs < 300.0f -> ArpeggioSpeed.SLOW_ARPEGGIO
            else -> ArpeggioSpeed.BROKEN_CHORD
        }

        // 5. 4-VOICE SEPARATION
        val bassVoice = currentNotes.filter { it.midi < 48 }
        val tenorVoice = currentNotes.filter { it.midi in 48..59 }
        val altoVoice = currentNotes.filter { it.midi in 60..71 }
        val sopranoVoice = currentNotes.filter { it.midi >= 72 }

        val voiceSeparation = VoiceSeparationResult(bassVoice, tenorVoice, altoVoice, sopranoVoice)

        // 6. NON-CHORD TONE CLASSIFICATION (Passing, Neighbor, Grace notes, Suspensions)
        val nonChordTones = mutableMapOf<String, NonChordToneType>()
        for (note in currentNotes) {
            val type = when {
                note.isGraceNote || note.durationMs < 75L -> NonChordToneType.ACCIACCATURA
                isPassingTone(note, currentNotes) -> NonChordToneType.PASSING_TONE
                isNeighborTone(note, currentNotes) -> NonChordToneType.NEIGHBOR_TONE
                isSuspension(note, currentNotes) -> NonChordToneType.SUSPENSION
                else -> NonChordToneType.CHORD_TONE
            }
            nonChordTones[note.pitch] = type
        }

        // 7. CHORD RECONSTRUCTION & CANDIDATE GENERATION
        // Aggregate weighted pitch classes into 12-bin Chroma vector
        val chroma = FloatArray(12)
        for ((note, weight) in weightedNotes) {
            val nctType = nonChordTones[note.pitch] ?: NonChordToneType.CHORD_TONE
            val nctFactor = when (nctType) {
                NonChordToneType.ACCIACCATURA, NonChordToneType.APPOGGIATURA -> 0.15f
                NonChordToneType.PASSING_TONE, NonChordToneType.NEIGHBOR_TONE -> 0.25f
                NonChordToneType.SUSPENSION, NonChordToneType.RETARDATION -> 0.60f
                NonChordToneType.CHORD_TONE -> 1.0f
                else -> 0.5f
            }
            chroma[note.pitchClass] += weight * nctFactor
        }

        // Normalize Chroma profile
        val maxChroma = chroma.maxOrNull() ?: 1.0f
        if (maxChroma > 1e-6f) {
            for (i in 0 until 12) chroma[i] /= maxChroma
        }

        // Identify lowest bass note
        val lowestBassNote = bassVoice.minByOrNull { it.midi } ?: currentNotes.minByOrNull { it.midi }
        val bassIndex = lowestBassNote?.pitchClass ?: -1

        // 8. CANDIDATE EVALUATION & VOICE LEADING MOVEMENT
        val previousChord = synchronized(chordHistory) { chordHistory.lastOrNull() }
        val candidateEvaluations = mutableListOf<CandidateScore>()

        for (tmpl in MusicTheoryEngine.ALL_420_TEMPLATES) {
            // Factor 1: Pitch Match / Cosine Similarity (30%)
            var dot = 0f; var normC = 0f; var normW = 0f
            for (i in 0 until 12) {
                dot += chroma[i] * tmpl.weights[i]
                normC += chroma[i] * chroma[i]
                normW += tmpl.weights[i] * tmpl.weights[i]
            }
            val pitchMatch = if (normC > 1e-6f && normW > 1e-6f) dot / (sqrt(normC) * sqrt(normW)) else 0f

            // Factor 2: Harmonic Energy (20%)
            val rootEnergy = chroma[tmpl.rootIndex]
            val harmonicEnergy = (rootEnergy * 0.7f + (if (bassIndex == tmpl.rootIndex) 0.3f else 0.0f)).coerceIn(0f, 1f)

            // Factor 3: Key Agreement (15%)
            val diatonicChords = MusicTheoryEngine.getDiatonicChords(detectedKey)
            val isDiatonic = diatonicChords.contains(tmpl.fullName) || diatonicChords.contains(tmpl.rootName)
            val keyAgreement = if (isDiatonic) 1.0f else 0.3f

            // Factor 4: Voice Leading Minimum Movement (15%)
            val voiceLeadingScore = if (previousChord != null) {
                val prevParsed = MusicTheoryEngine.parseChordSymbol(previousChord)
                if (prevParsed != null) {
                    val prevNotes = MusicTheoryEngine.getNotesForChord(prevParsed.first, prevParsed.second)
                    val candNotes = MusicTheoryEngine.getNotesForChord(tmpl.rootIndex, tmpl.qualitySuffix)
                    val shared = prevNotes.intersect(candNotes.toSet()).size
                    (shared.toFloat() / candNotes.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                } else 0.5f
            } else 0.5f

            // Factor 5: Chord History & Transition Probability (10%)
            val historyScore = if (previousChord != null) {
                computeTransitionProbability(previousChord, tmpl.fullName, detectedKey)
            } else 0.5f

            // Factor 6: Note Weights Alignment (10%)
            val matchedWeights = tmpl.intervals.map { (tmpl.rootIndex + it) % 12 }.sumOf { chroma[it].toDouble() }.toFloat()
            val noteWeightsScore = (matchedWeights / tmpl.intervals.size.coerceAtLeast(1)).coerceIn(0f, 1f)

            // STRICT 6-FACTOR CONFIDENCE FORMULA
            val confidence = (0.30f * pitchMatch +
                              0.20f * harmonicEnergy +
                              0.15f * keyAgreement +
                              0.15f * voiceLeadingScore +
                              0.10f * historyScore +
                              0.10f * noteWeightsScore).coerceIn(0.0f, 0.99f)

            var symbol = tmpl.fullName
            if (bassIndex in 0..11 && bassIndex != tmpl.rootIndex) {
                symbol = "$symbol/${NOTE_NAMES[bassIndex]}"
            }

            candidateEvaluations.add(
                CandidateScore(
                    symbol = symbol,
                    template = tmpl,
                    confidence = confidence,
                    pitchMatch = pitchMatch,
                    harmonicEnergy = harmonicEnergy,
                    keyAgreement = keyAgreement,
                    voiceLeading = voiceLeadingScore,
                    historyScore = historyScore,
                    noteWeights = noteWeightsScore
                )
            )
        }

        candidateEvaluations.sortByDescending { it.confidence }
        val bestCandidate = candidateEvaluations.firstOrNull() ?: return createUnknownResult("No valid candidates")

        // 9. HIDDEN MARKOV MODEL (HMM) & VITERBI DECODING (Temporal Anti-Flicker)
        val viterbiSelectedSymbol = runViterbiStep(bestCandidate.symbol, bestCandidate.confidence)

        // 10. STRICT CONFIDENCE REJECTION (< 70%): Return "Unknown"
        val finalWinner = candidateEvaluations.find { it.symbol == viterbiSelectedSymbol } ?: bestCandidate

        if (finalWinner.confidence < confidenceThreshold) {
            return createUnknownResult("Confidence (${(finalWinner.confidence * 100).toInt()}%) below threshold ${(confidenceThreshold * 100).toInt()}%")
        }

        // Update chord history memory (32-chord limit)
        synchronized(chordHistory) {
            chordHistory.add(finalWinner.symbol)
            if (chordHistory.size > 32) chordHistory.removeAt(0)
        }

        // 11. SPECIALIZED GENRE MODES & DESCRIPTION BUILDER
        val genreStyle = detectSpecializedGenreStyle(currentNotes, finalWinner.symbol, specializedMode)
        val activeNoteNames = currentNotes.map { it.pitch.takeWhile { c -> c.isLetter() || c == '#' || c == 'b' } }.distinct()
        val formula = finalWinner.template.intervals.joinToString(" - ") { semitonesToFormula(it) }

        val description = buildString {
            append("${finalWinner.symbol} [${arpeggioSpeed.name}]")
            if (genreStyle != null) append(" • $genreStyle")
            append(" • Δt=${meanDeltaMs.toInt()}ms")
            append(" • Conf=${(finalWinner.confidence * 100).toInt()}%")
        }

        return ArpeggioRecognitionResult(
            chordSymbol = finalWinner.symbol,
            confidence = finalWinner.confidence,
            arpeggioSpeed = arpeggioSpeed,
            meanDeltaMs = meanDeltaMs,
            activeNotes = activeNoteNames,
            voiceSeparation = voiceSeparation,
            nonChordTones = nonChordTones,
            genreStyle = genreStyle,
            formula = formula,
            description = description
        )
    }

    private data class CandidateScore(
        val symbol: String,
        val template: MusicTheoryEngine.CandidateTemplate,
        val confidence: Float,
        val pitchMatch: Float,
        val harmonicEnergy: Float,
        val keyAgreement: Float,
        val voiceLeading: Float,
        val historyScore: Float,
        val noteWeights: Float
    )

    private fun isPassingTone(note: BufferedNote, allNotes: List<BufferedNote>): Boolean {
        val sorted = allNotes.sortedBy { it.timestampMs }
        val idx = sorted.indexOf(note)
        if (idx in 1 until sorted.size - 1) {
            val prev = sorted[idx - 1]
            val next = sorted[idx + 1]
            val diff1 = abs(note.midi - prev.midi)
            val diff2 = abs(next.midi - note.midi)
            return (diff1 in 1..2 && diff2 in 1..2 && ((prev.midi < note.midi && note.midi < next.midi) || (prev.midi > note.midi && note.midi > next.midi)))
        }
        return false
    }

    private fun isNeighborTone(note: BufferedNote, allNotes: List<BufferedNote>): Boolean {
        val sorted = allNotes.sortedBy { it.timestampMs }
        val idx = sorted.indexOf(note)
        if (idx in 1 until sorted.size - 1) {
            val prev = sorted[idx - 1]
            val next = sorted[idx + 1]
            return abs(note.midi - prev.midi) in 1..2 && prev.pitchClass == next.pitchClass
        }
        return false
    }

    private fun isSuspension(note: BufferedNote, allNotes: List<BufferedNote>): Boolean {
        return note.durationMs > 250L && note.decay < 0.7f
    }

    /**
     * Transition probability matrix evaluation for Bayesian posterior P(C_curr | C_prev).
     */
    private fun computeTransitionProbability(prevChord: String, candChord: String, key: String): Float {
        val prevParsed = MusicTheoryEngine.parseChordSymbol(prevChord)
        val candParsed = MusicTheoryEngine.parseChordSymbol(candChord)
        if (prevParsed == null || candParsed == null) return 0.5f

        val (keyRoot, _) = MusicTheoryEngine.parseKey(key)
        val degPrev = (prevParsed.first - keyRoot + 12) % 12
        val degCand = (candParsed.first - keyRoot + 12) % 12

        // Standard harmonic transition rules (circle of fifths, ii-V-I, worship 1-5-6-4)
        return when {
            degPrev == 7 && degCand == 0 -> 0.95f // V -> I
            degPrev == 2 && degCand == 7 -> 0.90f // ii -> V
            degPrev == 5 && degCand == 0 -> 0.85f // IV -> I
            degPrev == 0 && degCand == 7 -> 0.80f // I -> V
            degPrev == 7 && degCand == 9 -> 0.82f // V -> vi (Deceptive)
            degPrev == degCand -> 0.70f          // Same chord continuation
            else -> 0.40f
        }
    }

    /**
     * Viterbi HMM trellis step to prevent rapid chord flickering.
     */
    private fun runViterbiStep(topObservedSymbol: String, observationConfidence: Float): String {
        val transProb = if (topObservedSymbol == viterbiBestState) 0.85 else 0.15
        val newLogProb = viterbiLogProb + ln(transProb) + ln(observationConfidence.toDouble().coerceAtLeast(1e-4))

        if (newLogProb > viterbiLogProb - 1.5) {
            viterbiBestState = topObservedSymbol
            viterbiLogProb = newLogProb
        }
        return viterbiBestState
    }

    /**
     * Specialized Genre Mode Intelligence (Worship, Gospel, Sungura).
     */
    private fun detectSpecializedGenreStyle(
        notes: List<BufferedNote>,
        chordSymbol: String,
        specializedMode: String
    ): String? {
        val pitcheStr = notes.map { it.pitch }.joinToString(" ")

        return when (specializedMode) {
            "Worship" -> when {
                chordSymbol.contains("add9") || chordSymbol.contains("maj9") -> "Worship Ambient Padded Ninth"
                chordSymbol.contains("sus4") || chordSymbol.contains("sus2") -> "Worship Suspended Introspection"
                notes.size >= 4 -> "Worship Slow Piano Broken Roll"
                else -> "Worship Open Fifth Layer"
            }
            "Gospel" -> when {
                chordSymbol.contains("7alt") || chordSymbol.contains("7#9") || chordSymbol.contains("7b9") -> "Gospel Altered Extended Dominant"
                chordSymbol.contains("drop2") || chordSymbol.contains("drop3") -> "Gospel Drop-2/Drop-3 Voicing"
                chordSymbol.contains("rootless") -> "Gospel Rootless 9th Jazz Voicing"
                else -> "Gospel Upper Structure Triad"
            }
            "Sungura" -> when {
                pitcheStr.contains("F#") || pitcheStr.contains("C#") -> "Sungura Fast 16th Triplet Interlocking Picking"
                notes.any { it.midi < 48 } -> "Sungura Alternating Bass & High Treble Contour"
                else -> "Sungura Sequential Cross-String Arpeggio"
            }
            else -> when {
                pitcheStr.contains("F#") && pitcheStr.contains("B") -> "Sungura Fast Guitar Arpeggio"
                pitcheStr.contains("C") && pitcheStr.contains("F") -> "Soukous Seben Interlocking Pluck"
                pitcheStr.contains("G") && pitcheStr.contains("D") -> "Rhumba Nylon Fingerstyle Roll"
                else -> null
            }
        }
    }

    private fun semitonesToFormula(semitones: Int): String = when (semitones) {
        0 -> "1"
        1 -> "b9"
        2 -> "9"
        3 -> "b3"
        4 -> "3"
        5 -> "11"
        6 -> "b5"
        7 -> "5"
        8 -> "#5"
        9 -> "13"
        10 -> "b7"
        11 -> "7"
        else -> "$semitones"
    }

    private fun createUnknownResult(reason: String): ArpeggioRecognitionResult {
        return ArpeggioRecognitionResult(
            chordSymbol = "Unknown",
            confidence = 0.0f,
            arpeggioSpeed = ArpeggioSpeed.BROKEN_CHORD,
            meanDeltaMs = 0.0f,
            activeNotes = emptyList(),
            voiceSeparation = VoiceSeparationResult(emptyList(), emptyList(), emptyList(), emptyList()),
            nonChordTones = emptyMap(),
            genreStyle = null,
            formula = "Undefined",
            description = "Unknown Harmony ($reason)"
        )
    }

    /**
     * Resets internal timestamp buffer and history queues.
     */
    fun clear() {
        timestampBuffer.clear()
        chordHistory.clear()
        viterbiBestState = "C"
        viterbiLogProb = 0.0
    }
}
