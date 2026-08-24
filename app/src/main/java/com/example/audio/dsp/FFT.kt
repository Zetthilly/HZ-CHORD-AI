package com.example.audio.dsp

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Iterative Radix-2 Cooley-Tukey Fast Fourier Transform (FFT) and Windowing DSP utilities.
 * Pure on-device signal processing with zero external dependencies.
 */
object FFT {

    data class FFTResult(
        val real: FloatArray,
        val imag: FloatArray
    ) {
        fun magnitude(): FloatArray {
            val mag = FloatArray(real.size)
            for (i in real.indices) {
                mag[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
            }
            return mag
        }
    }

    /**
     * Applies a Hann window: w[i] = 0.5 * (1 - cos(2π·i / (N-1)))
     */
    fun applyHannWindow(input: FloatArray): FloatArray {
        val n = input.size
        if (n <= 1) return input.clone()
        val windowed = FloatArray(n)
        val factor = 2.0 * Math.PI / (n - 1)
        for (i in 0 until n) {
            val win = 0.5f * (1.0f - cos(factor * i).toFloat())
            windowed[i] = input[i] * win
        }
        return windowed
    }

    /**
     * Computes next power of 2 >= n.
     */
    fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) {
            p = p shl 1
        }
        return p
    }

    /**
     * Performs iterative Radix-2 Cooley-Tukey Forward FFT on real-valued input array.
     */
    fun fft(input: FloatArray): FFTResult {
        val originalSize = input.size
        val n = nextPowerOfTwo(originalSize)

        val real = FloatArray(n)
        val imag = FloatArray(n)

        // Copy input and zero-pad if necessary
        for (i in 0 until originalSize) {
            real[i] = input[i]
        }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k in 1..j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Iterative Cooley-Tukey butterfly computation
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            for (i in 0 until n step len) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val pos1 = i + k
                    val pos2 = i + k + halfLen

                    val uR = real[pos1]
                    val uI = imag[pos1]

                    val vR = real[pos2] * wR - imag[pos2] * wI
                    val vI = real[pos2] * wI + imag[pos2] * wR

                    real[pos1] = uR + vR
                    imag[pos1] = uI + vI

                    real[pos2] = uR - vR
                    imag[pos2] = uI - vI

                    // Rotate twiddle factor
                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
            }
            len = len shl 1
        }

        return FFTResult(real, imag)
    }

    /**
     * Performs iterative Radix-2 Inverse FFT (IFFT) from complex spectrum back to real waveform.
     */
    fun ifft(real: FloatArray, imag: FloatArray): FloatArray {
        val n = real.size
        val conjImag = FloatArray(n) { i -> -imag[i] }

        val forwardResult = fftComplex(real, conjImag)

        val output = FloatArray(n)
        val invN = 1.0f / n
        for (i in 0 until n) {
            output[i] = forwardResult.real[i] * invN
        }
        return output
    }

    private fun fftComplex(inputR: FloatArray, inputI: FloatArray): FFTResult {
        val n = inputR.size
        val real = inputR.clone()
        val imag = inputI.clone()

        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k in 1..j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            for (i in 0 until n step len) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val pos1 = i + k
                    val pos2 = i + k + halfLen

                    val uR = real[pos1]
                    val uI = imag[pos1]

                    val vR = real[pos2] * wR - imag[pos2] * wI
                    val vI = real[pos2] * wI + imag[pos2] * wR

                    real[pos1] = uR + vR
                    imag[pos1] = uI + vI

                    real[pos2] = uR - vR
                    imag[pos2] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
            }
            len = len shl 1
        }
        return FFTResult(real, imag)
    }
}
