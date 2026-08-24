package com.example.audio.dsp

import kotlin.math.*

data class BpmEstimate(
    val bpm: Float,
    val confidence: Float  // 0–100
)

/**
 * BPM detection using spectral flux onset detection + autocorrelation.
 * Range: 40–300 BPM, target accuracy ±1 BPM
 */
class BpmDetector(private val sampleRateHz: Int = 44100) {
    private val onsetEnergy = mutableListOf<Float>()
    private val fftAnalyzer = FFTAnalyzer(2048)
    private val chromaAnalyzer = ChromaAnalyzer(sampleRateHz)
    
    private var lastSpectrum = FloatArray(1024)
    
    /**
     * Processes audio frame and accumulates onset energy.
     * Call this repeatedly as audio arrives.
     */
    fun processFrame(pcmSamples: FloatArray) {
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(pcmSamples)
        
        // Compute spectral flux (sum of positive changes)
        var flux = 0f
        for (i in spectrum.indices) {
            val delta = spectrum[i] - lastSpectrum.getOrElse(i) { 0f }
            if (delta > 0) flux += delta
        }
        
        onsetEnergy.add(flux)
        lastSpectrum = spectrum
    }
    
    /**
     * Estimates BPM from accumulated onset energy.
     * Must call processFrame() at least 2–3 seconds of audio first.
     */
    fun estimateBpm(): BpmEstimate {
        if (onsetEnergy.size < 100) {
            return BpmEstimate(120f, 0f)  // Default fallback
        }
        
        // Compute autocorrelation at different lags (tempo hypotheses)
        var bestBpm = 120f
        var bestScore = 0f
        
        for (lagMs in 200..1500 step 10) {  // 40–300 BPM
            val lagFrames = (lagMs * sampleRateHz) / (1000 * 2048)
            val correlation = autoCorrelate(onsetEnergy, lagFrames.toInt())
            
            if (correlation > bestScore) {
                bestScore = correlation
                bestBpm = (60000f / lagMs)
            }
        }
        
        val confidence = (bestScore.coerceIn(0f, 1f) * 100f)
        return BpmEstimate(bestBpm, confidence)
    }
    
    private fun autoCorrelate(signal: List<Float>, lag: Int): Float {
        if (lag >= signal.size) return 0f
        
        var sum = 0f
        var count = 0
        
        for (i in 0 until signal.size - lag) {
            sum += signal[i] * signal[i + lag]
            count++
        }
        
        return if (count > 0) sum / count else 0f
    }
    
    fun reset() {
        onsetEnergy.clear()
        lastSpectrum = FloatArray(1024)
    }
}
