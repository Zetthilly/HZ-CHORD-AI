package com.example.audio

import android.content.Context
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

import com.example.audio.theory.MusicTheoryEngine

/**
 * Helper class for real-time on-device chord classification using spectral chroma and harmonic templates.
 * Uses exact music theory interval definitions and Harmonic-aware cosine similarity.
 */
class AudioClassifier(private val context: Context? = null) {


    data class ChordClassificationResult(
        val chordName: String,
        val confidence: Float,
        val rootNote: String,
        val chordQuality: String,
        val chromaProfile: FloatArray,
        val alternativeCandidates: List<Pair<String, Float>>,
        val rootConfidence: Float = confidence,
        val qualityConfidence: Float = confidence,
        val overallConfidence: Float = confidence
    )

    private val rollingChromaBuffer = java.util.Collections.synchronizedList(mutableListOf<FloatArray>())
    private val ROLLING_WINDOW_SIZE = 6 // ~300-500ms rolling window of audio frames
    /** Initializes the real on-device DSP chord classifier. */
    fun initialize(): Boolean {
        Log.i(TAG, "On-device DSP chord classifier ready: FFT chroma + bass pitch + 420-template scoring.")
        return true
    }

    private val chordHistoryBuffer = java.util.Collections.synchronizedList(mutableListOf<ChordClassificationResult>())

    /**
     * Classifies an audio PCM buffer (float samples between -1.0 and 1.0) for chord recognition.
     */
    fun classifyAudioBuffer(
        audioSamples: FloatArray,
        sampleRate: Int = 44100,
        detectedKey: String? = null
    ): ChordClassificationResult {
        if (audioSamples.isEmpty()) return formatClassificationResult("No signal", 0f, FloatArray(12), overallConfidence = 0f)

        // Apply Hann window to input audio buffer to prevent spectral leakage
        val windowed = FloatArray(audioSamples.size)
        val n = audioSamples.size
        for (i in 0 until n) {
            val hann = 0.5f * (1.0f - cos(2.0 * Math.PI * i / (n - 1)).toFloat())
            windowed[i] = audioSamples[i] * hann
        }

        val instantChroma = extractChromaFeatures(windowed, sampleRate)
        val bassNoteIndex = detectBassPitchIndex(windowed, sampleRate)

        // Rolling Window Chroma Accumulation (~300-500ms accumulated evidence)
        val accumulatedChroma = FloatArray(12)
        synchronized(rollingChromaBuffer) {
            rollingChromaBuffer.add(instantChroma)
            if (rollingChromaBuffer.size > ROLLING_WINDOW_SIZE) {
                rollingChromaBuffer.removeAt(0)
            }
            val frameCount = rollingChromaBuffer.size
            for (frame in rollingChromaBuffer) {
                for (i in 0 until 12) {
                    accumulatedChroma[i] += frame[i] / frameCount
                }
            }
        }
        val rawResult = classifyChromaCosineSimilarity(accumulatedChroma, bassNoteIndex, detectedKey)

        // Apply temporal smoothing across last 3 accumulated results
        synchronized(chordHistoryBuffer) {
            chordHistoryBuffer.add(rawResult)
            if (chordHistoryBuffer.size > 3) {
                chordHistoryBuffer.removeAt(0)
            }
            val dominantName = chordHistoryBuffer.groupBy { it.chordName }
                .maxByOrNull { it.value.size }?.key ?: rawResult.chordName
            return chordHistoryBuffer.find { it.chordName == dominantName } ?: rawResult
        }
    }

    /**
     * Autocorrelation on lowpass-filtered PCM to detect true fundamental bass note (40Hz to 250Hz).
     */
    private fun detectBassPitchIndex(audioSamples: FloatArray, sampleRate: Int): Int {
        val numSamples = audioSamples.size
        if (numSamples < 512) return -1

        val minLag = (sampleRate / 250.0).toInt().coerceAtLeast(1)
        val maxLag = (sampleRate / 40.0).toInt().coerceAtMost(numSamples - 1)
        if (maxLag <= minLag) return -1

        var maxCorr = 0.0f
        var bestLag = -1

        for (lag in minLag..maxLag) {
            var corr = 0.0f
            val maxI = numSamples - lag
            val step = (maxI / 256).coerceAtLeast(1)
            for (i in 0 until maxI step step) {
                corr += audioSamples[i] * audioSamples[i + lag]
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        if (bestLag <= 0 || maxCorr < 0.05f) return -1

        val fundFreq = sampleRate.toFloat() / bestLag
        val midiPitch = Math.round(12.0 * Math.log(fundFreq / 440.0) / Math.log(2.0) + 69).toInt()
        val pitchIndex = ((midiPitch % 12) + 12) % 12
        return pitchIndex
    }

    /**
     * Computes a 12-bin Chromagram profile from windowed audio PCM data using Cooley-Tukey FFT.
     */
    private fun extractChromaFeatures(audioSamples: FloatArray, sampleRate: Int): FloatArray {
        val chroma = FloatArray(12)
        if (audioSamples.isEmpty()) return chroma

        // Apply Hann window & compute radix-2 Cooley-Tukey FFT
        val windowed = com.example.audio.dsp.FFT.applyHannWindow(audioSamples)
        val fftResult = com.example.audio.dsp.FFT.fft(windowed)
        val magnitudes = fftResult.magnitude()
        val fftSize = magnitudes.size

        val pitchFrequencies = floatArrayOf(
            16.35f, 17.32f, 18.35f, 19.45f, 20.60f, 21.83f, 23.12f, 24.50f, 25.96f, 27.50f, 29.14f, 30.87f
        )

        for (pitchIdx in 0 until 12) {
            var energy = 0f
            for (octave in 2..5) {
                val freq = pitchFrequencies[pitchIdx] * (1 shl octave)
                val bin = (freq * fftSize / sampleRate).toInt()
                if (bin in 1 until fftSize / 2) {
                    // Sum energy from target bin and adjacent bins
                    energy += magnitudes[bin]
                    if (bin - 1 >= 0) energy += magnitudes[bin - 1] * 0.5f
                    if (bin + 1 < fftSize / 2) energy += magnitudes[bin + 1] * 0.5f
                }
            }
            chroma[pitchIdx] = energy
        }

        val maxVal = chroma.maxOrNull() ?: 1.0f
        if (maxVal > 0.0001f) {
            for (i in 0 until 12) {
                chroma[i] /= maxVal
            }
        }
        return chroma
    }

    /**
     * Cosine similarity matching of 12-bin chroma vector against 420 weighted chord templates.
     * Applies key-aware diatonic re-ranking, 3 separate confidence component calculations,
     * low-confidence candidate formatting (< 0.55), and exact slash chord inversion analysis.
     */
    private fun classifyChromaCosineSimilarity(
        chroma: FloatArray,
        bassNoteIndex: Int,
        detectedKey: String? = null
    ): ChordClassificationResult {
        val diatonicChords = MusicTheoryEngine.getDiatonicChords(detectedKey ?: "")

        val rawScores = mutableListOf<Pair<MusicTheoryEngine.CandidateTemplate, Float>>()
        val adjustedScores = mutableListOf<Pair<MusicTheoryEngine.CandidateTemplate, Float>>()

        for (tmpl in MusicTheoryEngine.ALL_420_TEMPLATES) {
            val sim = cosineSimilarity(chroma, tmpl.weights)
            rawScores.add(Pair(tmpl, sim))

            val isDiatonic = diatonicChords.contains(tmpl.fullName) || diatonicChords.contains(tmpl.rootName)
            val boost = if (isDiatonic) 0.08f else 0.0f
            adjustedScores.add(Pair(tmpl, sim + boost))
        }

        adjustedScores.sortByDescending { it.second }
        rawScores.sortByDescending { it.second }

        val topAdjusted = adjustedScores.firstOrNull()?.first ?: MusicTheoryEngine.ALL_420_TEMPLATES[0]
        val topRaw = rawScores.firstOrNull()?.first ?: MusicTheoryEngine.ALL_420_TEMPLATES[0]
        val topRawScore = rawScores.firstOrNull()?.second ?: 0f

        // Key-aware override rule: only choose topRaw if its raw score wins over topAdjusted's raw score by > 0.15 margin
        val topAdjustedRawScore = rawScores.find { it.first == topAdjusted }?.second ?: 0f
        val selectedWinner = if (topRawScore - topAdjustedRawScore > 0.15f) topRaw else topAdjusted

        val topSimScore = rawScores.find { it.first == selectedWinner }?.second ?: 0.5f
        val secondSimScore = rawScores.getOrNull(1)?.second ?: 0.3f

        // 1. Quality Confidence: Margin-based calibration between winning template and runners-up
        val margin = (topSimScore - secondSimScore).coerceIn(0.0f, 1.0f)
        val qualityConfidence = (topSimScore * 0.45f + margin * 0.54f).coerceIn(0.10f, 0.99f)

        // 2. Root Confidence: Evaluation of bass pitch class and root chroma energy
        val rootIdx = selectedWinner.rootIndex
        val maxEnergy = chroma.maxOrNull() ?: 1.0f
        val rootEnergyRatio = if (maxEnergy > 0f) (chroma[rootIdx] / maxEnergy).coerceIn(0.0f, 1.0f) else 0f
        val isBassExactRoot = (bassNoteIndex == rootIdx)
        val rootConfidence = when {
            isBassExactRoot -> (0.85f + rootEnergyRatio * 0.14f).coerceIn(0.10f, 0.99f)
            bassNoteIndex in 0..11 -> (0.65f + rootEnergyRatio * 0.30f).coerceIn(0.10f, 0.95f)
            else -> (rootEnergyRatio * 0.75f + 0.15f).coerceIn(0.10f, 0.90f)
        }

        // 3. Overall Combined Confidence
        val overallConfidence = (0.35f * rootConfidence + 0.65f * qualityConfidence).coerceIn(0.10f, 0.99f)

        // Low confidence threshold handling: stop forcing wrong basic chords!
        // Display candidate percentages e.g. "Cmaj9 (62%) / Am11 (58%)" or "Partial Chord" / "Unknown"
        val topCandidates = adjustedScores.take(4).map { Pair(it.first.fullName, it.second.coerceIn(0.0f, 1.0f)) }

        val finalChordName = if (overallConfidence < 0.55f) {
            val top2 = adjustedScores.take(2).filter { it.second > 0.15f }
            if (top2.size >= 2) {
                val c1Name = top2[0].first.fullName
                val c1Pct = (top2[0].second * 100).toInt().coerceIn(10, 99)
                val c2Name = top2[1].first.fullName
                val c2Pct = (top2[1].second * 100).toInt().coerceIn(10, 99)
                "$c1Name ($c1Pct%) / $c2Name ($c2Pct%)"
            } else if (top2.size == 1) {
                val c1Name = top2[0].first.fullName
                val c1Pct = (top2[0].second * 100).toInt().coerceIn(10, 99)
                "Partial Chord: $c1Name ($c1Pct%)"
            } else {
                "Unknown"
            }
        } else {
            var symbol = selectedWinner.fullName
            if (bassNoteIndex in 0..11) {
                val bassName = MusicTheoryEngine.NOTE_NAMES[bassNoteIndex]
                if (bassName != selectedWinner.rootName && !symbol.contains("/")) {
                    symbol = "$symbol/$bassName"
                }
            }
            symbol
        }

        return formatClassificationResult(
            predictedChord = finalChordName,
            confidence = overallConfidence,
            chroma = chroma,
            candidates = topCandidates,
            bassNoteIndex = bassNoteIndex,
            rootConfidence = rootConfidence,
            qualityConfidence = qualityConfidence,
            overallConfidence = overallConfidence
        )
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        if (norm1 < 0.00001f || norm2 < 0.00001f) return 0f
        return (dot / (sqrt(norm1) * sqrt(norm2))).toFloat()
    }

    private fun formatClassificationResult(
        predictedChord: String,
        confidence: Float,
        chroma: FloatArray,
        candidates: List<Pair<String, Float>> = emptyList(),
        bassNoteIndex: Int = -1,
        rootConfidence: Float = confidence,
        qualityConfidence: Float = confidence,
        overallConfidence: Float = confidence
    ): ChordClassificationResult {
        val baseName = predictedChord.substringBefore("/").substringBefore(" (").substringBefore(":")
        val rootNote = MusicTheoryEngine.NOTE_NAMES.sortedByDescending { it.length }.find { baseName.startsWith(it) } ?: "C"
        val quality = when {
            baseName.contains("maj13") -> "Major 13th"
            baseName.contains("m13") -> "Minor 13th"
            baseName.contains("13") -> "Dominant 13th"
            baseName.contains("maj11") -> "Major 11th"
            baseName.contains("m11") -> "Minor 11th"
            baseName.contains("11") -> "Dominant 11th"
            baseName.contains("maj9") -> "Major 9th"
            baseName.contains("m9") -> "Minor 9th"
            baseName.contains("9") -> "Dominant 9th"
            baseName.contains("maj7") -> "Major 7th"
            baseName.contains("m7b5") -> "Half-Diminished"
            baseName.contains("mMaj7") -> "Minor-Major 7th"
            baseName.contains("m7") -> "Minor 7th"
            baseName.contains("7alt") -> "Altered Dominant"
            baseName.contains("7#11") -> "7#11 Extension"
            baseName.contains("7#9") -> "7#9 Extension"
            baseName.contains("7b9") -> "7b9 Extension"
            baseName.contains("7#5") -> "Augmented 7th"
            baseName.contains("7b5") -> "Flatted 5th Dominant"
            baseName.contains("7sus4") -> "7th Suspended 4th"
            baseName.contains("7") -> "Dominant 7th"
            baseName.contains("add13") -> "Add 13"
            baseName.contains("add11") -> "Add 11"
            baseName.contains("add9") -> "Add 9"
            baseName.contains("add2") -> "Add 2"
            baseName.contains("6/9") -> "6/9 Extension"
            baseName.contains("6") -> "Sixth"
            baseName.contains("dim7") -> "Diminished 7th"
            baseName.contains("dim") -> "Diminished"
            baseName.contains("aug") -> "Augmented"
            baseName.contains("sus2") -> "Suspended 2nd"
            baseName.contains("sus4") -> "Suspended 4th"
            baseName.endsWith("m") -> "Minor"
            else -> "Major"
        }

        return ChordClassificationResult(
            chordName = predictedChord,
            confidence = overallConfidence,
            rootNote = rootNote,
            chordQuality = quality,
            chromaProfile = chroma,
            alternativeCandidates = candidates,
            rootConfidence = rootConfidence,
            qualityConfidence = qualityConfidence,
            overallConfidence = overallConfidence
        )
    }
    fun close() {
        synchronized(chordHistoryBuffer) { chordHistoryBuffer.clear() }
        synchronized(rollingChromaBuffer) { rollingChromaBuffer.clear() }
    }

    companion object {
        private const val TAG = "AudioClassifier"
    }
}
