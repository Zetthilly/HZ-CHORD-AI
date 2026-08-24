package com.example.audio.dsp

import kotlin.math.*

/**
 * Chroma vector (pitch class energy) from FFT spectrum.
 * Maps 12 pitch classes (C, C#, D, ..., B) to energy.
 * Used for chord detection.
 */
class ChromaAnalyzer(
    private val sampleRateHz: Int = 44100,
    private val fftSize: Int = 2048
) {
    private val chromaVector = FloatArray(12) // C, C#, D, D#, E, F, F#, G, G#, A, A#, B
    private val noteFrequencies = floatArrayOf(
        16.35f, 17.32f, 18.35f, 19.45f, 20.60f, 21.83f, 23.12f, 24.50f, 25.96f, 27.50f, 29.14f, 30.87f // Octave -1
    )
    
    /**
     * Computes chroma vector from FFT magnitude spectrum.
     * @param magnitudeSpectrum FFT output in dB (fftSize/2 bins)
     * @return 12-element chroma vector (pitch class distribution)
     */
    fun computeChroma(magnitudeSpectrum: FloatArray): FloatArray {
        chromaVector.fill(0f)
        
        val binFreq = sampleRateHz.toFloat() / fftSize
        
        // Iterate over FFT bins and map to chroma
        for (bin in 0 until magnitudeSpectrum.size) {
            val freq = bin * binFreq
            if (freq < 20f || freq > 4000f) continue // Focus on audible pitch range
            
            // Convert frequency to MIDI note number
            val midiNote = 12f * log2(freq / noteFrequencies[0])
            val chromaClass = (midiNote % 12f).toInt().coerceIn(0, 11)
            val linearMag = 10f.pow(magnitudeSpectrum[bin] / 20f) // Convert dB to linear
            
            chromaVector[chromaClass] += linearMag
        }
        
        // Normalize to unit vector
        val sum = chromaVector.sum()
        if (sum > 0f) {
            for (i in chromaVector.indices) {
                chromaVector[i] /= sum
            }
        }
        
        return chromaVector
    }
    
    fun getChromaVector(): FloatArray = chromaVector
}
