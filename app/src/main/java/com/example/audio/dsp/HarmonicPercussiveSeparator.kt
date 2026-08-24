package com.example.audio.dsp

import java.util.Arrays

/**
 * Harmonic-Percussive Source Separation (HPSS) using median-filtering (Fitzgerald 2010).
 * Genuinely separates percussive (drums) from harmonic (instruments/vocals) content via STFT soft-masking.
 */
object HarmonicPercussiveSeparator {

    data class HPSSResult(
        val harmonicWaveform: FloatArray,
        val percussiveWaveform: FloatArray
    )

    /**
     * Executes HPSS separation on an audio PCM chunk.
     */
    fun separate(
        pcmInput: FloatArray,
        windowSize: Int = 2048,
        hopSize: Int = 512,
        medianTimeWindow: Int = 17,
        medianFreqWindow: Int = 17
    ): HPSSResult {
        if (pcmInput.isEmpty()) {
            return HPSSResult(FloatArray(0), FloatArray(0))
        }

        // 1. Forward STFT
        val spec = STFT.forwardSTFT(pcmInput, windowSize, hopSize)
        val T = spec.numFrames
        val F = spec.numBins
        val mag = spec.computeMagnitude()

        // 2. Harmonic matrix H[t][f]: median filter along time axis t
        val H = Array(T) { FloatArray(F) }
        val halfT = medianTimeWindow / 2
        val tBuf = FloatArray(medianTimeWindow)

        for (f in 0 until F) {
            for (t in 0 until T) {
                var count = 0
                for (dt in -halfT..halfT) {
                    val frameIdx = (t + dt).coerceIn(0, T - 1)
                    tBuf[count++] = mag[frameIdx][f]
                }
                Arrays.sort(tBuf, 0, count)
                H[t][f] = tBuf[count / 2]
            }
        }

        // 3. Percussive matrix P[t][f]: median filter along frequency axis f
        val P = Array(T) { FloatArray(F) }
        val halfF = medianFreqWindow / 2
        val fBuf = FloatArray(medianFreqWindow)

        for (t in 0 until T) {
            for (f in 0 until F) {
                var count = 0
                for (df in -halfF..halfF) {
                    val binIdx = (f + df).coerceIn(0, F - 1)
                    fBuf[count++] = mag[t][binIdx]
                }
                Arrays.sort(fBuf, 0, count)
                P[t][f] = fBuf[count / 2]
            }
        }

        // 4. Soft Masking & Complex Spectrogram Filtering
        val eps = 1e-6f
        val realH = Array(T) { FloatArray(F) }
        val imagH = Array(T) { FloatArray(F) }
        val realP = Array(T) { FloatArray(F) }
        val imagP = Array(T) { FloatArray(F) }

        for (t in 0 until T) {
            for (f in 0 until F) {
                val hSq = H[t][f] * H[t][f]
                val pSq = P[t][f] * P[t][f]
                val maskH = hSq / (hSq + pSq + eps)
                val maskP = 1.0f - maskH

                val rOrig = spec.real[t][f]
                val iOrig = spec.imag[t][f]

                realH[t][f] = rOrig * maskH
                imagH[t][f] = iOrig * maskH

                realP[t][f] = rOrig * maskP
                imagP[t][f] = iOrig * maskP
            }
        }

        // 5. Inverse STFT to waveforms
        val specH = STFT.ComplexSpectrogram(realH, imagH, windowSize, hopSize, pcmInput.size)
        val specP = STFT.ComplexSpectrogram(realP, imagP, windowSize, hopSize, pcmInput.size)

        val waveH = STFT.inverseSTFT(specH)
        val waveP = STFT.inverseSTFT(specP)

        return HPSSResult(
            harmonicWaveform = waveH,
            percussiveWaveform = waveP
        )
    }
}
