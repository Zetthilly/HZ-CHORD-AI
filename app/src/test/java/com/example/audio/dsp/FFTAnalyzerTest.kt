package com.example.audio.dsp

import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertTrue

class FFTAnalyzerTest {
    private lateinit var analyzer: FFTAnalyzer
    
    @Before
    fun setUp() {
        analyzer = FFTAnalyzer(2048)
    }
    
    @Test
    fun testSineWaveFft() {
        // Generate 440 Hz sine wave (A4)
        val sampleRate = 44100
        val freq = 440f
        val samples = FloatArray(2048) { i ->
            sin(2f * PI * freq * i / sampleRate).toFloat()
        }
        
        val spectrum = analyzer.computeMagnitudeSpectrum(samples)
        
        // Peak should be near 440 Hz bin
        val peakBin = spectrum.indices.maxByOrNull { spectrum[it] } ?: -1
        val peakFreq = peakBin * sampleRate / 2048
        
        assertTrue(peakFreq in 420f..460f, "Peak frequency $peakFreq not in expected range [420-460]")
    }
    
    @Test
    fun testFftSize() {
        val samples = FloatArray(2048) { 0.1f }
        val spectrum = analyzer.computeMagnitudeSpectrum(samples)
        
        // Output should be fftSize/2 bins
        assertTrue(spectrum.size == 1024, "Spectrum size ${spectrum.size} should be 1024")
    }
    
    @Test
    fun testMagnitudeGetters() {
        val samples = FloatArray(2048) { 0.1f }
        analyzer.computeMagnitudeSpectrum(samples)
        
        val magnitude = analyzer.getMagnitude()
        val phase = analyzer.getPhase()
        
        assertTrue(magnitude.size == 1024, "Magnitude array size mismatch")
        assertTrue(phase.size == 1024, "Phase array size mismatch")
    }
}
