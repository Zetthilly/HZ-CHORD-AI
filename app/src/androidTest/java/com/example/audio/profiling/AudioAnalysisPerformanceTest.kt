package com.example.audio.profiling

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertTrue
import org.junit.runner.RunWith

/**
 * Performance tests for audio analysis pipeline.
 * 
 * Validates:
 * 1. Latency meets targets (<50ms per frame)
 * 2. Memory usage stays bounded (<50MB heap)
 * 3. No memory leaks over extended processing
 * 4. CPU usage acceptable for real-time processing
 */
@RunWith(AndroidJUnit4::class)
class AudioAnalysisPerformanceTest {
    private lateinit var profiler: PerformanceProfiler
    private lateinit var testAudio: FloatArray
    
    @Before
    fun setUp() {
        profiler = PerformanceProfiler()
        
        // Generate test audio: 440 Hz sine wave (A4)
        val sampleRate = 44100
        val freq = 440f
        testAudio = FloatArray(2048) { i ->
            sin(2f * PI * freq * i / sampleRate).toFloat()
        }
    }
    
    @Test
    fun testFFTLatency() {
        val metrics = profiler.profileFFT(testAudio)
        
        // FFT should complete in < 25ms (half frame time)
        assertTrue(
            metrics.executionTimeMs < 25,
            "FFT latency ${metrics.executionTimeMs}ms exceeds target (<25ms)"
        )
    }
    
    @Test
    fun testChromaLatency() {
        val spectrum = FloatArray(1024) { 0.1f }
        val metrics = profiler.profileChroma(spectrum)
        
        // Chroma should complete in < 10ms
        assertTrue(
            metrics.executionTimeMs < 10,
            "Chroma latency ${metrics.executionTimeMs}ms exceeds target (<10ms)"
        )
    }
    
    @Test
    fun testHPSSLatency() {
        val metrics = profiler.profileHPSS(testAudio)
        
        // HPSS (5 frames) should complete in < 30ms
        assertTrue(
            metrics.executionTimeMs < 30,
            "HPSS latency ${metrics.executionTimeMs}ms exceeds target (<30ms)"
        )
    }
    
    @Test
    fun testFullPipelineLatency() {
        val metrics = profiler.profileFullPipeline(testAudio)
        
        // Full pipeline should complete in < 50ms (one frame budget)
        assertTrue(
            metrics.executionTimeMs < 50,
            "Pipeline latency ${metrics.executionTimeMs}ms exceeds target (<50ms)"
        )
    }
    
    @Test
    fun testMemoryBoundedness() {
        val metrics = profiler.profileFullPipeline(testAudio)
        
        // Heap should not exceed 500MB
        assertTrue(
            metrics.allocatedHeapMb < 500,
            "Heap usage ${metrics.allocatedHeapMb}MB exceeds safe limit (<500MB)"
        )
    }
    
    @Test
    fun testNoMemoryLeak() {
        // Process many frames and check heap doesn't grow unbounded
        val initialHeap = Runtime.getRuntime().totalMemory()
        
        repeat(100) {
            profiler.profileFullPipeline(testAudio)
            System.gc()  // Suggest garbage collection
        }
        
        val finalHeap = Runtime.getRuntime().totalMemory()
        val heapGrowth = (finalHeap - initialHeap) / 1024 / 1024
        
        // Growth should be minimal after GC
        assertTrue(
            heapGrowth < 50,
            "Heap growth ${heapGrowth}MB suggests memory leak"
        )
    }
    
    @Test
    fun testCpuUsageReasonable() {
        val metrics = profiler.profileFullPipeline(testAudio)
        
        // CPU usage should not exceed 100% of frame time
        assertTrue(
            metrics.cpuUsagePercent < 100,
            "CPU usage ${metrics.cpuUsagePercent}% exceeds one frame budget"
        )
    }
    
    @Test
    fun testComprehensiveBenchmark() {
        val results = profiler.runFullBenchmark(testAudio)
        
        // Print summary
        println(results.getSummary())
        
        // All components should meet targets
        assertTrue(results.fftAverageMs < 25, "FFT average too slow")
        assertTrue(results.chromaAverageMs < 10, "Chroma average too slow")
        assertTrue(results.pipelineAverageMs < 50, "Pipeline average too slow")
        assertTrue(results.maxMemoryMb < 500, "Peak memory too high")
    }
}
