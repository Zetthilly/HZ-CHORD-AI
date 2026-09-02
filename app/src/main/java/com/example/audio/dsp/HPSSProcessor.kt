package com.example.audio.dsp

import kotlin.math.*

/**
 * Harmonic/Percussive Source Separation (HPSS) via spectral processing.
 * 
 * Separates audio into two components:
 * - **Harmonic:** Stable, pitched content (vocals, instruments)
 * - **Percussive:** Transient, rhythm content (drums, clicks, attack transients)
 * 
 * Algorithm: Median filtering in time/frequency domain
 * - Harmonics remain stable across frames → high score in time-median filter
 * - Percussive spikes are transient → high score in frequency-median filter
 * 
 * References:
 * - Fitzgerald, D. (2010). "Harmonic/Percussive Source Separation Using Median Filtering", ISMIR
 * - Driedger, J., Müller, M., & Disch, S. (2014). "Improving Source Separation by Synthesizing Training Data", ISMIR
 */
data class StemComponents(
    val harmonic: FloatArray,    // Melodic/harmonic content (float magnitudes or spectral data)
    val percussive: FloatArray   // Drum/transient content (float magnitudes or spectral data)
)

data class HPSSConfig(
    val harmonicMedianFilterLength: Int = 17,  // Odd number for stable time filtering
    val percussiveMedianFilterLength: Int = 17,
    val betaHarmonic: Float = 0.9f,            // Blending factor (0=fully percussive, 1=fully harmonic)
    val betaPercussive: Float = 0.9f
)

/**
 * HPSS processor for separating harmonic and percussive sources.
 * 
 * Input: Short-time Fourier transform (STFT) magnitude or chroma vectors
 * Output: Two separated spectral streams
 * 
 * Use cases:
 * 1. **Chord detection:** Use harmonic stem to reduce drum noise
 * 2. **Beat tracking:** Use percussive stem for more accurate onset detection
 * 3. **Stem separation:** As preprocessing before TensorFlow Lite model
 * 4. **Real-time analysis:** Process each frame independently or with buffer
 */
class HPSSProcessor(
    private val sampleRateHz: Int = 44100,
    private val fftSize: Int = 2048,
    private val hopSize: Int = 512,
    private val config: HPSSConfig = HPSSConfig()
) {
    private val fftAnalyzer = FFTAnalyzer(fftSize)
    
    // Buffers for temporal median filtering
    private val spectralHistory = mutableListOf<FloatArray>()  // Rolling window of spectra
    private val maxHistorySize = config.harmonicMedianFilterLength
    
    /**
     * Separates a single audio frame into harmonic and percussive components.
     * 
     * @param audioFrame PCM samples (typically 2048-4096 samples)
     * @return StemComponents with separated magnitude spectra
     */
    fun separateFrame(audioFrame: FloatArray): StemComponents {
        // Compute FFT magnitude spectrum
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(audioFrame)
        
        // Add to history buffer
        spectralHistory.add(spectrum.copyOf())
        if (spectralHistory.size > maxHistorySize) {
            spectralHistory.removeAt(0)  // Keep rolling window
        }
        
        // Compute harmonic and percussive masks
        val harmonicMask = computeHarmonicMask()
        val percussiveMask = FloatArray(spectrum.size) { i ->
            1f - harmonicMask[i]  // Complementary masks
        }
        
        // Apply masks to spectrum
        val harmonicSpec = FloatArray(spectrum.size) { i ->
            spectrum[i] * harmonicMask[i]
        }
        
        val percussiveSpec = FloatArray(spectrum.size) { i ->
            spectrum[i] * percussiveMask[i]
        }
        
        return StemComponents(
            harmonic = harmonicSpec,
            percussive = percussiveSpec
        )
    }
    
    /**
     * Computes harmonic mask using median filtering across time.
     * 
     * Harmonics are stable/smooth across frames.
     * Percussive events are sharp transients.
     * 
     * Mask closer to 1.0 → more harmonic
     * Mask closer to 0.0 → more percussive
     * 
     * @return Mask array [0, 1] per frequency bin
     */
    private fun computeHarmonicMask(): FloatArray {
        if (spectralHistory.isEmpty()) {
            return FloatArray(fftSize / 2) { 0.5f }  // Default: balanced
        }
        
        val numBins = spectralHistory[0].size
        val mask = FloatArray(numBins)
        
        // For each frequency bin, compute time-median
        for (bin in 0 until numBins) {
            val binEnergies = spectralHistory.map { it[bin] }.sorted()
            val medianEnergy = binEnergies[binEnergies.size / 2]
            
            // Current frame energy at this bin
            val currentEnergy = spectralHistory.last()[bin]
            
            // If current energy is close to median → harmonic (stable)
            // If current energy >> median → percussive (transient spike)
            val deviation = if (medianEnergy > 1e-6f) {
                currentEnergy / medianEnergy
            } else {
                1f
            }
            
            // Soft thresholding: smooth transition around threshold
            val threshold = 1.5f  // Typical transient is >1.5x median
            mask[bin] = when {
                deviation < threshold -> config.betaHarmonic       // Likely harmonic
                deviation > threshold * 2 -> 1f - config.betaPercussive  // Likely percussive
                else -> 0.5f  // Ambiguous
            }
        }
        
        return mask
    }
    
    /**
     * Applies frequency-domain median filtering to separate harmonics from percussive.
     * 
     * Alternative to time-domain approach:
     * - Harmonics are stable in frequency (narrow peaks)
     * - Percussive content spreads across frequencies (broad peaks)
     * 
     * @param spectrum Current frame spectrum
     * @return Harmonic mask based on frequency stability
     */
    fun computeHarmonicMaskFrequency(spectrum: FloatArray): FloatArray {
        val mask = FloatArray(spectrum.size)
        val filterWidth = 5  // Median filter width in bins
        
        for (bin in 0 until spectrum.size) {
            // Collect neighboring bins
            val neighborhood = mutableListOf<Float>()
            for (offset in -filterWidth..filterWidth) {
                val neighborBin = (bin + offset).coerceIn(0, spectrum.size - 1)
                neighborhood.add(spectrum[neighborBin])
            }
            
            val medianNeighbor = neighborhood.sorted()[neighborhood.size / 2]
            val currentBin = spectrum[bin]
            
            // Frequency-domain: stable peaks are harmonic
            // Sharp peaks relative to neighbors are percussive
            val stability = if (medianNeighbor > 1e-6f) {
                currentBin / medianNeighbor
            } else {
                1f
            }
            
            // High stability → harmonic, Low stability → percussive
            mask[bin] = (1f / (1f + exp(-stability + 2f)))  // Sigmoid smooth transition
        }
        
        return mask
    }
    
    /**
     * Reset internal history buffer.
     * Call this when starting analysis of a new audio segment.
     */
    fun reset() {
        spectralHistory.clear()
    }
    
    /**
     * Gets current history size (for debugging/testing).
     */
    fun getHistorySize(): Int = spectralHistory.size
}
