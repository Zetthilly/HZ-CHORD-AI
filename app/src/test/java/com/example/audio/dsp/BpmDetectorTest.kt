package com.example.audio.dsp

import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertTrue

class BpmDetectorTest {
    private lateinit var detector: BpmDetector
    
    @Before
    fun setUp() {
        detector = BpmDetector(sampleRateHz = 44100)
    }
    
    @Test
    fun testBpmDetectorInitialization() {
        // Should return default 120 BPM with 0 confidence on empty data
        val estimate = detector.estimateBpm()
        
        assertTrue(estimate.bpm == 120f, "Initial BPM should be 120")
        assertTrue(estimate.confidence == 0f, "Initial confidence should be 0")
    }
    
    @Test
    fun testBpmRange() {
        // Process some dummy frames
        val samples = FloatArray(2048) { 0.1f }
        repeat(100) {
            detector.processFrame(samples)
        }
        
        val estimate = detector.estimateBpm()
        
        // BPM should be in valid range (40-300)
        assertTrue(estimate.bpm in 40f..300f, "BPM ${estimate.bpm} should be in [40, 300]")
    }
    
    @Test
    fun testConfidenceRange() {
        val samples = FloatArray(2048) { 0.1f }
        repeat(100) {
            detector.processFrame(samples)
        }
        
        val estimate = detector.estimateBpm()
        
        // Confidence should always be 0-100
        assertTrue(estimate.confidence in 0f..100f, "Confidence ${estimate.confidence} should be in [0, 100]")
    }
    
    @Test
    fun testReset() {
        val samples = FloatArray(2048) { 0.1f }
        repeat(100) {
            detector.processFrame(samples)
        }
        
        detector.reset()
        val estimate = detector.estimateBpm()
        
        // After reset, should return to default
        assertTrue(estimate.bpm == 120f, "BPM should be 120 after reset")
        assertTrue(estimate.confidence == 0f, "Confidence should be 0 after reset")
    }
}
