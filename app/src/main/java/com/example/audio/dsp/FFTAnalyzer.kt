package com.example.audio.dsp

import kotlin.math.*

/**
 * Real-time FFT analysis using Cooley-Tukey algorithm.
 * Input: float audio samples (2048–4096 samples @ 44.1kHz)
 * Output: Magnitude spectrum (0–22.05 kHz)
 */
class FFTAnalyzer(private val fftSize: Int = 2048) {
    private val magnitude = FloatArray(fftSize / 2)
    private val phase = FloatArray(fftSize / 2)
    
    init {
        require(fftSize and (fftSize - 1) == 0) { "FFT size must be power of 2" }
    }
    
    /**
     * Computes FFT magnitude spectrum.
     * @param samples Input PCM float samples (assumed windowed)
     * @return Magnitude spectrum in dB (0–180 dB range)
     */
    fun computeMagnitudeSpectrum(samples: FloatArray): FloatArray {
        require(samples.size >= fftSize) { "Input must be >= $fftSize samples" }
        
        // Radix-2 Cooley-Tukey FFT
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        
        // Copy + window with Hann
        for (i in 0 until fftSize) {
            val window = 0.5f * (1f - cos(2f * PI * i / (fftSize - 1)))
            real[i] = samples[i] * window
        }
        
        fft(real, imag)
        
        // Convert to magnitude in dB
        for (i in 0 until fftSize / 2) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            magnitude[i] = 20f * log10(mag.coerceAtLeast(1e-6f))
            phase[i] = atan2(imag[i], real[i])
        }
        
        return magnitude
    }
    
    /**
     * Radix-2 Cooley-Tukey FFT (in-place)
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return
        
        // Bit-reversal permutation
        for (i in 0 until n) {
            var j = 0
            var m = n
            var ii = i
            while (m > 1) {
                j = j * 2 + (ii and 1)
                ii = ii shr 1
                m = m shr 1
            }
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }
        
        // FFT butterflies
        var step = 2
        while (step <= n) {
            val halfStep = step / 2
            val theta = -2f * PI / step
            
            for (k in 0 until halfStep) {
                val w_real = cos(k * theta)
                val w_imag = sin(k * theta)
                
                var j = k
                while (j < n) {
                    val t_real = w_real * real[j + halfStep] - w_imag * imag[j + halfStep]
                    val t_imag = w_real * imag[j + halfStep] + w_imag * real[j + halfStep]
                    
                    real[j + halfStep] = real[j] - t_real
                    imag[j + halfStep] = imag[j] - t_imag
                    real[j] += t_real
                    imag[j] += t_imag
                    
                    j += step
                }
            }
            step = step shl 1
        }
    }
    
    fun getMagnitude(): FloatArray = magnitude
    fun getPhase(): FloatArray = phase
}
