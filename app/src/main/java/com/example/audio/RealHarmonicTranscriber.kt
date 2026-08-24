package com.example.audio

import android.content.Context
import android.util.Log
import com.example.audio.arpeggio.RealTimeArpeggioEngine
import com.example.audio.dsp.AudioPreprocessor
import com.example.audio.dsp.CQT
import com.example.audio.dsp.FFT
import com.example.audio.dsp.HPS
import com.example.audio.dsp.HarmonicPercussiveSeparator
import com.example.audio.dsp.MultiPitchDetector
import com.example.audio.dsp.TemporalNoteTracker
import com.example.audio.theory.MusicTheoryEngine
import com.example.audio.theory.VoiceLeadingResult
import com.example.audio.theory.ProgressionMatchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RealHarmonicTranscriber analyzes real audio PCM buffers using the complete 20-stage DSP & Music Theory pipeline.
 */
class RealHarmonicTranscriber(
    private val context: Context? = null
) {
    private val TAG = "RealHarmonicTranscriber"

    private val classifier = AudioClassifier(context)
    private val noteTracker = TemporalNoteTracker(windowMs = 300L)
    private val arpeggioEngine = RealTimeArpeggioEngine(bufferWindowMs = 600L, confidenceThreshold = 0.70f)
    private var previousChordName: String? = null
    private val recentChordHistory = java.util.Collections.synchronizedList(mutableListOf<String>())

    data class DetectedNoteEvent(
        val noteName: String, // e.g. "C", "F#"
        val octave: Int = 4,   // e.g. 4 -> "C4"
        val fullNoteName: String = "$noteName$octave",
        val onsetTimeMs: Long = System.currentTimeMillis(),
        val magnitude: Float = 1.0f, // Normalized 0.0-1.0 HPS confidence score
        val hpsConfidence: Float = magnitude
    )

    data class TranscriptionResult(
        val chordInfo: DetectedChordInfo,
        val activeNotes: List<String>,
        val noteEvents: List<DetectedNoteEvent> = emptyList(),
        val arpeggioPattern: String?,
        val africanStyleLick: String?,
        val ChromaProfile: FloatArray,
        val voiceLeading: VoiceLeadingResult? = null,
        val matchedProgression: ProgressionMatchResult? = null,
        val functionalAnalysis: com.example.audio.theory.FunctionalAnalysisResult? = null
    )

    private val _currentTranscription = MutableStateFlow<TranscriptionResult?>(null)
    val currentTranscription: StateFlow<TranscriptionResult?> = _currentTranscription.asStateFlow()

    private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val previousHpsEnergies = mutableMapOf<String, Float>()
    private val peakHpsEnergies = mutableMapOf<String, Float>()
    private val arpeggioNoteBuffer = java.util.ArrayDeque<Pair<String, Long>>()

    init {
        classifier.initialize()
    }

    /**
     * Analyzes a slice of real audio PCM samples using the full 20-stage harmonic pipeline.
     */
    fun analyzePcmBuffer(
        samples: FloatArray,
        sampleRate: Int = 44100,
        detectedKey: String = "",
        specializedMode: String = "Standard"
    ): TranscriptionResult {
        if (samples.isEmpty()) return createEmptyResult()

        // 1. Preprocessing Pipeline: 48kHz Mono -> DC Offset Removal -> 20Hz HPF -> Gain Normalisation
        val cleanPcm = AudioPreprocessor.preprocessSignal(samples, sampleRate)
        if (cleanPcm.isEmpty()) return createEmptyResult()

        // 2. Overlap Frame Extraction (75% Overlap with Hann Windowing)
        val frames = AudioPreprocessor.extract75PercentOverlapFrames(cleanPcm, frameSize = 2048)
        val targetFrame = frames.lastOrNull()?.windowedPcm ?: cleanPcm

        // 3. Constant-Q Transform (CQT) & Pitch Class Profiling (36 bins/octave, 27.5Hz - 4186Hz)
        val cqtRes = CQT.computeCqt(targetFrame, 48000)
        val cqtChroma = CQT.foldCqtToChroma(cqtRes)

        // 4. Harmonic-Percussive Source Separation (HPSS)
        val hpssRes = HarmonicPercussiveSeparator.separate(targetFrame, windowSize = 1024, hopSize = 256)
        val harmonicFrame = if (hpssRes.harmonicWaveform.isNotEmpty()) hpssRes.harmonicWaveform else targetFrame

        // 5. Multi-Pitch Detection (YIN + pYIN + HPS + Parabolic Spectral Peak Interpolation)
        val multiPitches = MultiPitchDetector.detectMultiplePitches(harmonicFrame, 48000)

        // 6. Temporal Note Tracking (300ms rolling window with grace note decay)
        val trackedNotes = noteTracker.update(multiPitches)
        val activeNoteNames = trackedNotes
            .map { it.pitchName.takeWhile { char -> char.isLetter() || char == '#' || char == 'b' } }
            .distinct()

        // 7. Bass Note Identification
        val lowestPitch = multiPitches.minByOrNull { it.frequency }
        val bassNoteIndex = lowestPitch?.pitchClass ?: -1

        // 8. Music Theory Validation & Candidate Evaluation
        val theoryAnalysis = MusicTheoryEngine.evaluateChordCandidates(
            chroma = cqtChroma,
            activeNotes = activeNoteNames,
            bassNoteIndex = bassNoteIndex,
            detectedKey = detectedKey,
            previousChord = previousChordName,
            chordHistory = recentChordHistory.toList(),
            confidenceThreshold = 0.35f
        )

        val currentName = theoryAnalysis.chordSymbol
        synchronized(recentChordHistory) {
            if (currentName != "Unknown") {
                recentChordHistory.add(currentName)
                if (recentChordHistory.size > 8) {
                    recentChordHistory.removeAt(0)
                }
            }
        }

        // 9. Voice Leading & Functional Harmony
        val subObjects = MusicTheoryEngine.suggestSubstitutions(currentName, detectedKey, recentChordHistory.toList())
        val subStrings = subObjects.map { "${it.chordName} (${it.ruleName})" }

        val voiceLeadingResult = previousChordName?.let { prev ->
            MusicTheoryEngine.analyzeVoiceLeading(prev, currentName)
        }
        val functionalAnalysis = MusicTheoryEngine.analyzeFunctionalHarmony(currentName, previousChordName, detectedKey)
        previousChordName = currentName

        val matchedProgression = MusicTheoryEngine.matchGenreProgression(recentChordHistory.toList(), detectedKey)

        // 10. Real-Time Arpeggio Engine & African guitar arpeggio matching
        val arpeggioRes = arpeggioEngine.processDetectedNotes(multiPitches, System.currentTimeMillis(), detectedKey, specializedMode)
        val arpeggio = if (arpeggioRes.chordSymbol != "Unknown") {
            "${arpeggioRes.description} (${arpeggioRes.formula})"
        } else {
            detectArpeggioPattern(activeNoteNames, currentName)
        }
        val africanLick = arpeggioRes.genreStyle ?: detectAfricanLickStyle(activeNoteNames, currentName)

        val noteEvents = detectActiveNoteEventsHPS(harmonicFrame, 48000)

        val fullDescription = buildString {
            append("${functionalAnalysis.romanNumeral} (${functionalAnalysis.harmonicFunction} in $detectedKey)")
            if (functionalAnalysis.cadenceType != null) {
                append(" • ").append(functionalAnalysis.cadenceType)
            }
            if (voiceLeadingResult != null) {
                append(" • ").append(voiceLeadingResult.summary)
            }
        }

        val chordInfo = DetectedChordInfo(
            name = currentName,
            root = theoryAnalysis.root,
            formula = theoryAnalysis.formula,
            notes = if (theoryAnalysis.notes.isNotEmpty()) theoryAnalysis.notes else activeNoteNames,
            confidence = theoryAnalysis.confidence,
            frequency = getRootFrequency(theoryAnalysis.root),
            type = theoryAnalysis.chordType,
            description = fullDescription,
            suggestedSubstitutions = subStrings,
            rootConfidence = theoryAnalysis.confidence,
            qualityConfidence = theoryAnalysis.confidence,
            overallConfidence = theoryAnalysis.confidence
        )

        val result = TranscriptionResult(
            chordInfo = chordInfo,
            activeNotes = activeNoteNames,
            noteEvents = noteEvents,
            arpeggioPattern = arpeggio,
            africanStyleLick = africanLick,
            ChromaProfile = cqtChroma,
            voiceLeading = voiceLeadingResult,
            matchedProgression = matchedProgression,
            functionalAnalysis = functionalAnalysis
        )

        _currentTranscription.value = result
        return result
    }

    /**
     * Pitch/note detection using Harmonic Product Spectrum (HPS) with Cooley-Tukey FFT.
     * Downsamples magnitude spectrum by integer factors 2..5 and multiplies bin-by-bin.
     * Eliminates harmonic overtones and octave ambiguity.
     */
    /**
     * Pitch/note detection using Harmonic Product Spectrum (HPS) with Cooley-Tukey FFT.
     * Downsamples magnitude spectrum by integer factors 2..5 and multiplies bin-by-bin.
     * Eliminates harmonic overtones and octave ambiguity.
     * Features peak sharpness confidence, noise floor rejection, attack onset detection, and decay tracking.
     */
    private fun detectActiveNoteEventsHPS(samples: FloatArray, sampleRate: Int): List<DetectedNoteEvent> {
        val detectedEvents = mutableListOf<DetectedNoteEvent>()
        if (samples.size < 256) return emptyList()

        val windowed = FFT.applyHannWindow(samples)
        val fftResult = FFT.fft(windowed)
        val magnitudes = fftResult.magnitude()
        val fftSize = magnitudes.size

        // Compute Harmonic Product Spectrum (HPS)
        val hps = HPS.computeHps(magnitudes, maxHarmonics = 5)

        val pitchFreqs = doubleArrayOf(
            130.81, 138.59, 146.83, 155.56, 164.81, 174.61, 185.00, 196.00, 207.65, 220.00, 233.08, 246.94
        )

        val nowMs = System.currentTimeMillis()

        // Calculate global noise floor on HPS spectrum
        var hpsSum = 0f
        var hpsCount = 0
        val maxHpsBin = fftSize / 10
        for (b in 1 until maxHpsBin) {
            hpsSum += hps[b]
            hpsCount++
        }
        val noiseFloor = if (hpsCount > 0) (hpsSum / hpsCount).coerceAtLeast(1e-6f) else 1e-6f

        for (i in 0 until 12) {
            val baseFreq = pitchFreqs[i]
            val name = noteNames[i]

            for (octaveIdx in 1..4) {
                val octaveNum = octaveIdx + 2 // C3 to B6
                val freq = baseFreq * (1 shl (octaveIdx - 1))
                val bin = (freq * fftSize / sampleRate).toInt()

                if (bin in 1 until maxHpsBin) {
                    val peakVal = hps[bin]
                    val noteKey = "$name$octaveNum"

                    // Peak sharpness & signal-to-noise ratio check
                    if (peakVal > noiseFloor * 3.0f) {
                        val snrRatio = peakVal / noiseFloor
                        val normalizedConfidence = (1.0f - (1.0f / (1.0f + snrRatio / 12.0f))).coerceIn(0.0f, 0.99f)

                        // Reject very low-confidence transient noise blips (<0.25 confidence)
                        if (normalizedConfidence >= 0.25f) {
                            val prevEnergy = previousHpsEnergies[noteKey] ?: 0f
                            val maxEnergy = (peakHpsEnergies[noteKey] ?: peakVal).coerceAtLeast(peakVal)
                            peakHpsEnergies[noteKey] = maxEnergy

                            val energyDelta = peakVal - prevEnergy
                            val isAttackOnset = energyDelta > 0.15f * noiseFloor || prevEnergy == 0f
                            val isDecay = peakVal < maxEnergy * 0.20f

                            previousHpsEnergies[noteKey] = peakVal

                            // Only include if not in deep decay offset
                            if (!isDecay) {
                                detectedEvents.add(
                                    DetectedNoteEvent(
                                        noteName = name,
                                        octave = octaveNum,
                                        fullNoteName = noteKey,
                                        onsetTimeMs = if (isAttackOnset) nowMs else (nowMs - 100),
                                        magnitude = normalizedConfidence,
                                        hpsConfidence = normalizedConfidence
                                    )
                                )
                            }
                        }
                    } else {
                        // Reset energy tracking when falling into noise floor
                        previousHpsEnergies[noteKey] = 0f
                        peakHpsEnergies[noteKey] = 0f
                    }
                }
            }
        }
        return detectedEvents.sortedByDescending { it.magnitude }
    }

    private fun detectArpeggioPattern(notes: List<String>, chordName: String): String? {
        val now = System.currentTimeMillis()
        // Accumulate newly detected notes into rolling arpeggio window
        for (n in notes) {
            if (!arpeggioNoteBuffer.any { it.first == n }) {
                arpeggioNoteBuffer.addLast(n to now)
            }
        }
        // Purge notes older than 1200ms
        while (arpeggioNoteBuffer.isNotEmpty() && (now - arpeggioNoteBuffer.first().second) > 1200) {
            arpeggioNoteBuffer.removeFirst()
        }

        if (arpeggioNoteBuffer.size >= 3) {
            val seq = arpeggioNoteBuffer.map { it.first }
            val cleanNotes = seq.map { it.takeWhile { char -> char.isLetter() || char == '#' || char == 'b' } }.distinct()
            return when {
                cleanNotes.contains("F#") && cleanNotes.contains("C#") -> "Sungura Arpeggio Run: ${seq.joinToString(" → ")}"
                cleanNotes.contains("A") && cleanNotes.contains("E") -> "Rhumba High-Register Arpeggio Roll: ${seq.joinToString(" → ")}"
                cleanNotes.contains("G") && cleanNotes.contains("D") -> "Soukous Seben Interlocking Pluck: ${seq.joinToString(" → ")}"
                else -> "Rolling $chordName Arpeggio (${seq.joinToString(" → ")})"
            }
        }
        return if (notes.size >= 2) "${chordName} Broken Arpeggio (${notes.joinToString(" - ")})" else null
    }

    private fun detectAfricanLickStyle(notes: List<String>, chordName: String): String? {
        if (notes.isEmpty()) return null
        return when {
            notes.contains("F#") -> "Sungura Fast Staccato Lead (Franco / Ephraim Karima Style)"
            notes.contains("C#") || notes.contains("G#") -> "Soukous Seben High Fret Interlocking Pluck"
            notes.contains("B") || notes.contains("F") -> "Kenyan Benga Interlocking Bass-Treble Counterpoint"
            else -> "Classic African Gospel Triad Voicing"
        }
    }

    private fun getChordFormula(quality: String): String = when (quality) {
        "Minor" -> "1 - b3 - 5"
        "Dominant 7th" -> "1 - 3 - 5 - b7"
        "Major 7th" -> "1 - 3 - 5 - 7"
        "Minor 7th" -> "1 - b3 - 5 - b7"
        else -> "1 - 3 - 5"
    }

    private fun getNotesForChord(chordName: String): List<String> = when {
        chordName.startsWith("C") -> listOf("C", "E", "G")
        chordName.startsWith("G") -> listOf("G", "B", "D")
        chordName.startsWith("D") -> listOf("D", "F#", "A")
        chordName.startsWith("A") -> listOf("A", "C#", "E")
        chordName.startsWith("F#") -> listOf("F#", "A#", "C#")
        chordName.startsWith("Am") -> listOf("A", "C", "E")
        chordName.startsWith("Em") -> listOf("E", "G", "B")
        else -> listOf("C", "E", "G")
    }

    private fun getRootFrequency(root: String): Float = when (root) {
        "C" -> 261.63f
        "D" -> 293.66f
        "E" -> 329.63f
        "F" -> 349.23f
        "G" -> 392.00f
        "A" -> 440.00f
        "B" -> 493.88f
        else -> 0f
    }
    private fun createEmptyResult(): TranscriptionResult {
        val noSignal = DetectedChordInfo(
            name = "No signal",
            root = "",
            formula = "",
            notes = emptyList(),
            confidence = 0f,
            frequency = 0f,
            type = "",
            description = "No usable audio frame is available for harmonic analysis.",
            suggestedSubstitutions = emptyList(),
            rootConfidence = 0f,
            qualityConfidence = 0f,
            overallConfidence = 0f
        )
        return TranscriptionResult(
            chordInfo = noSignal,
            activeNotes = emptyList(),
            arpeggioPattern = null,
            africanStyleLick = null,
            ChromaProfile = FloatArray(12)
        )
    }

    fun close() {
        classifier.close()
    }
}
