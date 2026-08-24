package com.example.audio.dsp

import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertTrue

class ChromaAnalyzerTest {
    private lateinit var analyzer: ChromaAnalyzer
    private lateinit var fftAnalyzer: FFTAnalyzer
    
    @Before
    fun setUp() {
        analyzer = ChromaAnalyzer(sampleRateHz = 44100, fftSize = 2048)
        fftAnalyzer = FFTAnalyzer(2048)
    }
    
    @Test
    fun testChromaVectorSize() {
        val samples = FloatArray(2048) { 0.1f }
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(samples)
        val chroma = analyzer.computeChroma(spectrum)
        
        assertTrue(chroma.size == 12, "Chroma vector should have 12 elements, got ${chroma.size}")
    }
    
    @Test
    fun testChromaVectorNormalization() {
        // Generate a 440 Hz tone (A4)
        val sampleRate = 44100
        val freq = 440f
        val samples = FloatArray(2048) { i ->
            sin(2f * PI * freq * i / sampleRate).toFloat()
        }
        
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(samples)
        val chroma = analyzer.computeChroma(spectrum)
        
        // Chroma vector should be normalized (sum close to 1.0)
        val sum = chroma.sum()
        assertTrue(sum in 0.99f..1.01f, "Chroma sum $sum should be ~1.0")
    }
    
    @Test
    fun testChromaVectorRange() {
        val samples = FloatArray(2048) { 0.1f }
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(samples)
        val chroma = analyzer.computeChroma(spectrum)
        
        // All chroma values should be between 0 and 1
        for (value in chroma) {
            assertTrue(value in 0f..1f, "Chroma value $value outside [0, 1] range")
        }
    }
    
    @Test
    fun testGetChromaVectorGetter() {
        val samples = FloatArray(2048) { 0.1f }
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(samples)
        analyzer.computeChroma(spectrum)
        
        val chromaGetter = analyzer.getChromaVector()
        assertTrue(chromaGetter.size == 12, "Getter returned wrong size")
    }
}
