package com.example.audio

import android.content.Context
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertTrue
import org.mockito.Mockito.mock

class AudioAnalysisPipelineTest {
    private lateinit var pipeline: AudioAnalysisPipeline
    private val mockContext: Context = mock(Context::class.java)
    
    @Before
    fun setUp() {
        // Disable TFLite for unit tests (no model file available)
        pipeline = AudioAnalysisPipeline(mockContext, enableTFLite = false)
    }
    
    @Test
    fun testAnalyzeFrameOutputStructure() {
        val samples = FloatArray(2048) { 0.1f }
        val result = pipeline.analyzeFrame(samples)
        
        assertTrue(result.chordPredictions.isNotEmpty(), "Should produce chord predictions")
        assertTrue(result.keyInfo != null, "Should produce key detection")
        assertTrue(result.processingTimeMs >= 0, "Should have valid processing time")
    }
    
    @Test
    fun testAnalyzeFrameWithSineWave() {
        // Generate 440 Hz sine wave (A4 note)
        val sampleRate = 44100
        val freq = 440f
        val samples = FloatArray(2048) { i ->
            sin(2f * PI * freq * i / sampleRate).toFloat()
        }
        
        val result = pipeline.analyzeFrame(samples)
        
        // Should detect some chord (even if not perfectly accurate)
        assertTrue(result.chordPredictions.isNotEmpty())
        assertTrue(result.chordPredictions[0].confidence > 0f)
    }
    
    @Test
    fun testBpmAccumulation() {
        val samples = FloatArray(2048) { 0.1f }
        
        // Process multiple frames
        repeat(50) {
            pipeline.analyzeFrame(samples)
        }
        
        val bpmEstimate = pipeline.estimateBpm()
        
        // BPM should be in valid range
        assertTrue(bpmEstimate.bpm in 40f..300f, "BPM ${bpmEstimate.bpm} out of range")
        assertTrue(bpmEstimate.confidence in 0f..100f, "Confidence out of range")
    }
    
    @Test
    fun testReset() {
        val samples = FloatArray(2048) { 0.1f }
        repeat(10) {
            pipeline.analyzeFrame(samples)
        }
        
        // Reset and check BPM returns to default
        pipeline.reset()
        val bpmAfterReset = pipeline.estimateBpm()
        
        assertTrue(bpmAfterReset.bpm == 120f, "BPM should reset to 120")
        assertTrue(bpmAfterReset.confidence == 0f, "Confidence should reset to 0")
    }
    
    @Test
    fun testProcessingTimeValid() {
        val samples = FloatArray(2048) { 0.1f }
        val result = pipeline.analyzeFrame(samples)
        
        // Processing should complete in reasonable time (<100ms on Android)
        assertTrue(result.processingTimeMs in 0..100, 
            "Processing time ${result.processingTimeMs}ms seems unrealistic")
    }
}
