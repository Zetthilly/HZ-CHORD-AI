package com.example.audio.dsp

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.log2

/**
 * Constant-Q Transform (CQT) Engine.
 * Logarithmic frequency transform with 36 bins per octave (3 bins per semitone).
 * Frequency range: 27.5 Hz (A0) to 4186 Hz (C8).
 */
object CQT {

    data class CQTResult(
        val magnitudes: FloatArray,
        val phases: FloatArray,
        val harmonicEnergies: FloatArray,
        val frequencies: FloatArray,
        val numBins: Int
    )

    private const val MIN_FREQ = 27.5f   // A0
    private const val MAX_FREQ = 4186.0f  // C8
    private const val BINS_PER_OCTAVE = 36 // 3 bins per semitone

    /**
     * Computes CQT magnitudes, phases, and harmonic energies for a frame of PCM audio.
     */
    fun computeCqt(pcmInput: FloatArray, sampleRate: Int = 48000): CQTResult {
        val totalOctaves = log2(MAX_FREQ / MIN_FREQ)
        val numBins = (totalOctaves * BINS_PER_OCTAVE).toInt()
        val magnitudes = FloatArray(numBins)
        val phases = FloatArray(numBins)
        val harmonicEnergies = FloatArray(numBins)
        val freqs = FloatArray(numBins)

        val q = 1.0f / (2.0f.pow(1.0f / BINS_PER_OCTAVE) - 1.0f) // ~51.856
        val n = pcmInput.size

        for (b in 0 until numBins) {
            val fB = MIN_FREQ * 2.0f.pow(b.toFloat() / BINS_PER_OCTAVE)
            freqs[b] = fB

            val windowLen = ((q * sampleRate) / fB).toInt().coerceIn(32, n)
            val startIdx = ((n - windowLen) / 2).coerceIn(0, n - windowLen)

            var realSum = 0.0f
            var imagSum = 0.0f

            val factor = 2.0 * Math.PI * fB / sampleRate
            val winFactor = 2.0 * Math.PI / (windowLen - 1)

            for (i in 0 until windowLen) {
                val sample = pcmInput[startIdx + i]
                val win = 0.5f * (1.0f - cos(winFactor * i).toFloat()) // Hann window
                val angle = factor * i
                realSum += sample * win * cos(angle).toFloat()
                imagSum -= sample * win * sin(angle).toFloat()
            }

            val mag = sqrt(realSum * realSum + imagSum * imagSum) / windowLen
            magnitudes[b] = mag
            phases[b] = kotlin.math.atan2(imagSum, realSum)
        }

        // Compute Harmonic Energies across 2nd, 3rd, 4th, 5th harmonic bins
        for (b in 0 until numBins) {
            var harmonicSum = magnitudes[b]
            for (h in 2..5) {
                val hBin = b + (BINS_PER_OCTAVE * log2(h.toDouble())).toInt()
                if (hBin < numBins) {
                    harmonicSum += magnitudes[hBin] / h
                }
            }
            harmonicEnergies[b] = harmonicSum
        }

        // Normalize CQT magnitude vector
        var maxMag = 0f
        for (m in magnitudes) if (m > maxMag) maxMag = m
        if (maxMag > 1e-6f) {
            for (i in magnitudes.indices) magnitudes[i] /= maxMag
        }

        return CQTResult(magnitudes, phases, harmonicEnergies, freqs, numBins)
    }

    /**
     * Folds 36-bin/octave CQT into 12 pitch classes (chroma profile).
     */
    fun foldCqtToChroma(cqtResult: CQTResult): FloatArray {
        val chroma = FloatArray(12)
        val numBins = cqtResult.numBins
        for (b in 0 until numBins) {
            val semitoneIdx = (b / 3) % 12
            // Pitch class offset starting from A0 (9th pitch class relative to C)
            val pitchClass = (semitoneIdx + 9) % 12
            chroma[pitchClass] += cqtResult.magnitudes[b]
        }

        var maxVal = 0f
        for (v in chroma) if (v > maxVal) maxVal = v
        if (maxVal > 1e-6f) {
            for (i in 0 until 12) chroma[i] /= maxVal
        }
        return chroma
    }
}
