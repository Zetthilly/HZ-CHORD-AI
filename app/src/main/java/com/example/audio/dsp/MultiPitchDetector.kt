package com.example.audio.dsp

import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Polyphonic Multi-Pitch Detector implementing:
 * 1. YIN algorithm
 * 2. pYIN probabilistic refinement (Hidden Markov Model thresholds)
 * 3. Harmonic Product Spectrum (HPS)
 * 4. Harmonic Summation & Partials Grouping
 * 5. Parabolic Spectral Peak Interpolation
 * 6. Harmonic Partials Elimination (overtone deduplication)
 */
object MultiPitchDetector {

    data class DetectedNote(
        val pitchName: String,         // e.g. "A4"
        val frequency: Float,          // e.g. 440.0 Hz
        val midiNumber: Int,           // e.g. 69
        val octave: Int,               // e.g. 4
        val pitchClass: Int,           // 0..11 (C..B)
        val amplitude: Float,          // 0.0 .. 1.0
        val velocity: Int,             // 1 .. 127
        val confidence: Float,         // 0.0 .. 1.0
        val attackTimeMs: Long,
        val releaseTimeMs: Long,
        val uniqueNoteId: String = UUID.randomUUID().toString(),
        val isGraceNote: Boolean = false,
        val isSustained: Boolean = false
    )

    data class PeakInfo(
        val bin: Int,
        val frequency: Float,
        val amplitude: Float
    )

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Computes YIN difference function d(tau) and cumulative mean normalized difference d'(tau).
     */
    fun computeYinDifference(pcm: FloatArray, maxLag: Int): FloatArray {
        val w = pcm.size / 2
        val d = FloatArray(maxLag)

        for (tau in 1 until maxLag) {
            var sum = 0.0f
            for (j in 0 until w) {
                val diff = pcm[j] - pcm[j + tau]
                sum += diff * diff
            }
            d[tau] = sum
        }

        // Cumulative mean normalized difference d'(tau)
        val dPrime = FloatArray(maxLag)
        dPrime[0] = 1.0f
        var runningSum = 0.0f
        for (tau in 1 until maxLag) {
            runningSum += d[tau]
            val mean = runningSum / tau
            dPrime[tau] = if (mean > 1e-6f) d[tau] / mean else 1.0f
        }
        return dPrime
    }

    /**
     * pYIN Probabilistic YIN Pitch Estimation across multiple candidate dip thresholds.
     */
    fun detectPYinPitch(
        pcm: FloatArray,
        sampleRate: Int = 48000
    ): DetectedNote? {
        val minFreq = 27.5f  // A0
        val maxFreq = 4186.0f // C8
        val minLag = (sampleRate / maxFreq).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minFreq).toInt().coerceAtMost(pcm.size / 2 - 1)

        if (maxLag <= minLag) return null

        val dPrime = computeYinDifference(pcm, maxLag + 2)

        // Multiple YIN thresholds (pYIN probabilistic observation distribution)
        val thresholds = floatArrayOf(0.10f, 0.15f, 0.20f, 0.25f, 0.30f)
        val candidateLags = mutableListOf<Int>()

        for (thresh in thresholds) {
            for (tau in minLag until maxLag) {
                if (dPrime[tau] < thresh) {
                    var localMin = tau
                    while (localMin + 1 < maxLag && dPrime[localMin + 1] < dPrime[localMin]) {
                        localMin++
                    }
                    candidateLags.add(localMin)
                    break
                }
            }
        }

        if (candidateLags.isEmpty()) {
            var minVal = Float.MAX_VALUE
            var bestTau = -1
            for (tau in minLag until maxLag) {
                if (dPrime[tau] < minVal) {
                    minVal = dPrime[tau]
                    bestTau = tau
                }
            }
            if (bestTau != -1 && minVal < 0.45f) {
                candidateLags.add(bestTau)
            }
        }

        if (candidateLags.isEmpty()) return null

        val tauEstimate = candidateLags.groupBy { it }.maxByOrNull { it.value.size }?.key ?: candidateLags[0]

        // Parabolic interpolation around tauEstimate
        val s0 = dPrime[tauEstimate - 1]
        val s1 = dPrime[tauEstimate]
        val s2 = dPrime[tauEstimate + 1]
        val delta = (s2 - s0) / (2.0f * (2.0f * s1 - s2 - s0) + 1e-6f)
        val refinedTau = tauEstimate + delta.coerceIn(-0.5f, 0.5f)

        val freq = sampleRate / refinedTau
        val midi = (12.0 * Math.log(freq / 440.0) / Math.log(2.0) + 69.0).roundToInt().coerceIn(21, 108)
        val octave = (midi / 12) - 1
        val pc = ((midi % 12) + 12) % 12
        val pitchName = "${NOTE_NAMES[pc]}$octave"
        val confidence = (1.0f - dPrime[tauEstimate]).coerceIn(0.1f, 0.99f)
        val velocity = (confidence * 120 + 7).toInt().coerceIn(1, 127)

        val nowMs = System.currentTimeMillis()
        return DetectedNote(
            pitchName = pitchName,
            frequency = freq,
            midiNumber = midi,
            octave = octave,
            pitchClass = pc,
            amplitude = confidence,
            velocity = velocity,
            confidence = confidence,
            attackTimeMs = nowMs,
            releaseTimeMs = nowMs + 300L,
            isGraceNote = confidence < 0.35f
        )
    }

    /**
     * Parabolic spectral peak interpolation.
     */
    fun interpolateSpectralPeak(
        magnitudes: FloatArray,
        bin: Int,
        sampleRate: Int = 48000
    ): PeakInfo {
        val fftSize = magnitudes.size * 2
        if (bin <= 0 || bin >= magnitudes.size - 1) {
            val f = bin.toFloat() * sampleRate / fftSize
            return PeakInfo(bin, f, magnitudes.getOrElse(bin) { 0f })
        }

        val eps = 1e-9f
        val alpha = ln(magnitudes[bin - 1].coerceAtLeast(eps))
        val beta = ln(magnitudes[bin].coerceAtLeast(eps))
        val gamma = ln(magnitudes[bin + 1].coerceAtLeast(eps))

        val denom = alpha - 2.0f * beta + gamma
        val delta = if (abs(denom) > 1e-6f) {
            0.5f * (alpha - gamma) / denom
        } else {
            0.0f
        }

        val refinedBin = bin + delta.coerceIn(-0.5f, 0.5f)
        val refinedFreq = refinedBin * sampleRate / fftSize
        val refinedMag = exp(beta - 0.25f * (alpha - gamma) * delta)

        return PeakInfo(bin, refinedFreq, refinedMag)
    }

    /**
     * Complete Polyphonic Multi-Pitch Detection engine.
     * Features Harmonic Partials Grouping & Suppression (merges 2f, 3f, 4f, 5f overtones into fundamental note).
     */
    fun detectMultiplePitches(
        pcm: FloatArray,
        sampleRate: Int = 48000
    ): List<DetectedNote> {
        if (pcm.size < 512) return emptyList()

        val detected = mutableListOf<DetectedNote>()

        // 1. Time-domain pYIN candidate
        val pYinNote = detectPYinPitch(pcm, sampleRate)
        if (pYinNote != null && pYinNote.confidence > 0.28f) {
            detected.add(pYinNote)
        }

        // 2. Frequency-domain HPS & Harmonic Summation with Peak Interpolation
        val windowed = FFT.applyHannWindow(pcm)
        val fftRes = FFT.fft(windowed)
        val mags = fftRes.magnitude()
        val fftSize = mags.size
        val hpsMags = HPS.computeHps(mags, maxHarmonics = 5)

        // Noise floor calculation
        var sumMags = 0f
        val maxBin = fftSize / 8
        for (i in 1 until maxBin) sumMags += hpsMags[i]
        val noiseFloor = (sumMags / maxBin).coerceAtLeast(1e-6f)

        // Find spectral peaks
        val rawCandidatePeaks = mutableListOf<PeakInfo>()
        for (b in 2 until maxBin - 1) {
            val m = hpsMags[b]
            if (m > hpsMags[b - 1] && m > hpsMags[b + 1] && m > noiseFloor * 2.2f) {
                val peak = interpolateSpectralPeak(mags, b, sampleRate)
                if (peak.frequency in 27.5f..4186.0f) {
                    rawCandidatePeaks.add(peak)
                }
            }
        }

        // HARMONIC GROUPING & PARTIALS SUPPRESSION:
        // Sort candidate peaks by frequency (lowest fundamental first)
        rawCandidatePeaks.sortBy { it.frequency }
        val activeFundamentals = mutableListOf<PeakInfo>()

        for (candidate in rawCandidatePeaks) {
            var isHarmonicOvertone = false
            for (fund in activeFundamentals) {
                // Check if candidate frequency is an integer multiple (2x, 3x, 4x, 5x) of an existing fundamental
                val ratio = candidate.frequency / fund.frequency
                val nearestHarmonic = ratio.roundToInt()
                if (nearestHarmonic in 2..6) {
                    val expectedFreq = fund.frequency * nearestHarmonic
                    val freqDiff = abs(candidate.frequency - expectedFreq)
                    if (freqDiff / expectedFreq < 0.04f) { // Within 4% relative tolerance
                        isHarmonicOvertone = true
                        break
                    }
                }
            }
            if (!isHarmonicOvertone) {
                activeFundamentals.add(candidate)
            }
        }

        val maxAmp = activeFundamentals.maxOfOrNull { it.amplitude } ?: 1.0f
        val nowMs = System.currentTimeMillis()

        for (peak in activeFundamentals) {
            val relAmp = if (maxAmp > 1e-6f) (peak.amplitude / maxAmp).coerceIn(0f, 1f) else 0f
            if (relAmp > 0.15f) {
                val freq = peak.frequency
                val midi = (12.0 * Math.log(freq / 440.0) / Math.log(2.0) + 69.0).roundToInt().coerceIn(21, 108)
                val octave = (midi / 12) - 1
                val pc = ((midi % 12) + 12) % 12
                val pitchName = "${NOTE_NAMES[pc]}$octave"

                if (!detected.any { it.midiNumber == midi }) {
                    val confidence = relAmp.coerceIn(0.20f, 0.99f)
                    val velocity = (relAmp * 120 + 7).toInt().coerceIn(1, 127)
                    detected.add(
                        DetectedNote(
                            pitchName = pitchName,
                            frequency = freq,
                            midiNumber = midi,
                            octave = octave,
                            pitchClass = pc,
                            amplitude = relAmp,
                            velocity = velocity,
                            confidence = confidence,
                            attackTimeMs = nowMs,
                            releaseTimeMs = nowMs + 300L,
                            isGraceNote = confidence < 0.35f
                        )
                    )
                }
            }
        }

        return detected.sortedByDescending { it.amplitude }
    }
}
