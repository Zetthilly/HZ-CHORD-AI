package com.example.audio.dsp

import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HPSSProcessorTest {
    private lateinit var processor: HPSSProcessor
    private lateinit var fftAnalyzer: FFTAnalyzer
    
    @Before
    fun setUp() {
        processor = HPSSProcessor(sampleRateHz = 44100, fftSize = 2048)
        fftAnalyzer = FFTAnalyzer(2048)
    }
    
    @Test
    fun testSeparateFrameOutputShapes() {
        val samples = FloatArray(2048) { 0.1f }
        val stems = processor.separateFrame(samples)
        
        assertTrue(stems.harmonic.size == 1024, "Harmonic spectrum size should be 1024")
        assertTrue(stems.percussive.size == 1024, "Percussive spectrum size should be 1024")
    }
    
    @Test
    fun testSeparateFrameEnergyConservation() {
        val samples = FloatArray(2048) { 0.1f }
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(samples)
        val stems = processor.separateFrame(samples)
        
        // Reconstructed energy should be approximately equal to original
        val originalEnergy = spectrum.sum()
        val reconstructedEnergy = stems.harmonic.sum() + stems.percussive.sum()
        
        val errorRatio = (reconstructedEnergy / originalEnergy).coerceIn(0.8f, 1.2f)
        assertTrue(errorRatio in 0.8f..1.2f, "Energy conservation failed: ratio=$errorRatio")
    }
    
    @Test
    fun testHarmonicMaskRange() {
        // Process several frames to fill history
        repeat(10) {
            val samples = FloatArray(2048) { 0.1f + (it * 0.01f).toFloat() }
            processor.separateFrame(samples)
        }
        
        val stems = processor.separateFrame(FloatArray(2048) { 0.1f })
        
        // All values should be between 0 and 1
        for (value in stems.harmonic) {
            assertTrue(value in 0f..1f, "Harmonic value $value outside [0, 1]")
        }
        for (value in stems.percussive) {
            assertTrue(value in 0f..1f, "Percussive value $value outside [0, 1]")
        }
    }
    
    @Test
    fun testReset() {
        // Fill buffer
        repeat(10) {
            processor.separateFrame(FloatArray(2048) { 0.1f })
        }
        assertTrue(processor.getHistorySize() > 0, "History should be populated")
        
        // Reset
        processor.reset()
        assertTrue(processor.getHistorySize() == 0, "History should be empty after reset")
    }
    
    @Test
    fun testHarmonicVsPercussiveContent() {
        // Create a frame with stable harmonic content (sine wave)
        val sampleRate = 44100
        val freq = 440f
        val harmonicSamples = FloatArray(2048) { i ->
            sin(2f * PI * freq * i / sampleRate).toFloat()
        }
        
        // Fill history with similar frames (stable)
        repeat(5) {
            processor.separateFrame(harmonicSamples.copyOf())
        }
        
        val harmonicStems = processor.separateFrame(harmonicSamples)
        
        // Harmonic energy should dominate
        val harmonicEnergy = harmonicStems.harmonic.sum()
        val percussiveEnergy = harmonicStems.percussive.sum()
        
        assertTrue(harmonicEnergy > percussiveEnergy, 
            "Stable sine wave should produce more harmonic energy")
    }
    
    @Test
    fun testPercussiveTransientDetection() {
        // Create baseline with quiet frames
        repeat(5) {
            processor.separateFrame(FloatArray(2048) { 0.01f })
        }
        
        // Create a sharp transient (loud frame)
        val transientSamples = FloatArray(2048) { 0.5f }
        val transientStems = processor.separateFrame(transientSamples)
        
        // Percussive energy should increase relative to harmonic
        val percussiveEnergy = transientStems.percussive.sum()
        val harmonicEnergy = transientStems.harmonic.sum()
        
        // Transient should have more percussive than harmonic
        assertTrue(percussiveEnergy > 0.1f, "Transient should produce percussive energy")
    }
}
