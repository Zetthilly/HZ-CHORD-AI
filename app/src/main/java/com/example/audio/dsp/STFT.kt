package com.example.audio.dsp

/**
 * Short-Time Fourier Transform (STFT) and Inverse STFT with Overlap-Add (OLA) reconstruction.
 * Uses window size 2048 and hop size 512 (75% overlap) with Hann windowing.
 */
object STFT {

    data class ComplexSpectrogram(
        val real: Array<FloatArray>,
        val imag: Array<FloatArray>,
        val windowSize: Int,
        val hopSize: Int,
        val originalSampleCount: Int
    ) {
        val numFrames: Int get() = real.size
        val numBins: Int get() = if (numFrames > 0) real[0].size else 0

        fun computeMagnitude(): Array<FloatArray> {
            return Array(numFrames) { t ->
                val mag = FloatArray(numBins)
                for (f in 0 until numBins) {
                    val r = real[t][f]
                    val i = imag[t][f]
                    mag[f] = kotlin.math.sqrt(r * r + i * i)
                }
                mag
            }
        }
    }

    /**
     * Computes forward STFT with window size 2048 and hop size 512.
     */
    fun forwardSTFT(
        input: FloatArray,
        windowSize: Int = 2048,
        hopSize: Int = 512
    ): ComplexSpectrogram {
        val n = input.size
        val fftSize = FFT.nextPowerOfTwo(windowSize)
        val numFrames = if (n >= windowSize) (n - windowSize) / hopSize + 1 else 1

        val realSpec = Array(numFrames) { FloatArray(fftSize) }
        val imagSpec = Array(numFrames) { FloatArray(fftSize) }

        val frameBuf = FloatArray(windowSize)

        for (t in 0 until numFrames) {
            val offset = t * hopSize
            for (i in 0 until windowSize) {
                val idx = offset + i
                frameBuf[i] = if (idx < n) input[idx] else 0.0f
            }

            // Apply Hann window
            val windowed = FFT.applyHannWindow(frameBuf)

            // Compute FFT
            val fftRes = FFT.fft(windowed)

            realSpec[t] = fftRes.real
            imagSpec[t] = fftRes.imag
        }

        return ComplexSpectrogram(
            real = realSpec,
            imag = imagSpec,
            windowSize = windowSize,
            hopSize = hopSize,
            originalSampleCount = n
        )
    }

    /**
     * Performs inverse STFT back to time domain waveform using Overlap-Add (OLA).
     */
    fun inverseSTFT(spectrogram: ComplexSpectrogram): FloatArray {
        val numFrames = spectrogram.numFrames
        val windowSize = spectrogram.windowSize
        val hopSize = spectrogram.hopSize
        val targetSize = spectrogram.originalSampleCount.coerceAtLeast((numFrames - 1) * hopSize + windowSize)

        val outputWave = FloatArray(targetSize)
        val windowSum = FloatArray(targetSize)

        // Precompute Hann window for normalization
        val hannWindow = FloatArray(windowSize) { i ->
            0.5f * (1.0f - kotlin.math.cos(2.0 * Math.PI * i / (windowSize - 1)).toFloat())
        }

        for (t in 0 until numFrames) {
            val rFrame = spectrogram.real[t]
            val iFrame = spectrogram.imag[t]

            // IFFT to frame
            val timeFrame = FFT.ifft(rFrame, iFrame)

            val offset = t * hopSize
            for (i in 0 until windowSize) {
                val outIdx = offset + i
                if (outIdx < targetSize) {
                    val w = hannWindow[i]
                    outputWave[outIdx] += timeFrame[i] * w
                    windowSum[outIdx] += w * w
                }
            }
        }

        // Normalize by window sum
        for (i in 0 until targetSize) {
            if (windowSum[i] > 1e-6f) {
                outputWave[i] /= windowSum[i]
            }
        }

        return outputWave
    }
}
