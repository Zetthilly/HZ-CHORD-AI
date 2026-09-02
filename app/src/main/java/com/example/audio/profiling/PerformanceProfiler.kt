package com.example.audio.profiling

import android.os.Debug
import android.util.Log
import kotlin.system.measureTimeMillis

/**
 * Performance metrics for a single operation.
 */
data class PerformanceMetrics(
    val operationName: String,
    val executionTimeMs: Long,
    val memoryUsedKb: Long,
    val cpuUsagePercent: Float,
    val allocatedHeapMb: Long
)

/**
 * On-device performance profiler for DSP components.
 * 
 * Measures:
 * 1. Execution latency (milliseconds)
 * 2. Memory allocation (KB)
 * 3. CPU usage (estimated)
 * 4. Heap size before/after
 * 
 * Usage:
 * ```kotlin
 * val profiler = PerformanceProfiler()
 * val metrics = profiler.profileFFT(audioFrame)
 * Log.i("Profile", "FFT: ${metrics.executionTimeMs}ms, ${metrics.memoryUsedKb}KB")
 * ```
 */
class PerformanceProfiler {
    companion object {
        private const val TAG = "PerformanceProfiler"
    }
    
    /**
     * Profiles FFT analysis on a single audio frame.
     */
    fun profileFFT(audioFrame: FloatArray): PerformanceMetrics {
        val heapBefore = Debug.getNativeHeap().size
        val timeBefore = System.currentTimeMillis()
        
        val executionTime = measureTimeMillis {
            val fftAnalyzer = com.example.audio.dsp.FFTAnalyzer(2048)
            fftAnalyzer.computeMagnitudeSpectrum(audioFrame)
        }
        
        val heapAfter = Debug.getNativeHeap().size
        val memoryUsed = (heapAfter - heapBefore) / 1024  // Convert to KB
        
        return PerformanceMetrics(
            operationName = "FFT Analysis",
            executionTimeMs = executionTime,
            memoryUsedKb = memoryUsed,
            cpuUsagePercent = estimateCpuUsage(executionTime),
            allocatedHeapMb = Runtime.getRuntime().totalMemory() / 1024 / 1024
        )
    }
    
    /**
     * Profiles HPSS separation on a single audio frame.
     */
    fun profileHPSS(audioFrame: FloatArray): PerformanceMetrics {
        val heapBefore = Runtime.getRuntime().totalMemory()
        
        val executionTime = measureTimeMillis {
            val hpssProcessor = com.example.audio.dsp.HPSSProcessor()
            repeat(5) {  // Need history for HPSS
                hpssProcessor.separateFrame(audioFrame)
            }
        }
        
        val heapAfter = Runtime.getRuntime().totalMemory()
        val memoryUsed = (heapAfter - heapBefore) / 1024
        
        return PerformanceMetrics(
            operationName = "HPSS Separation",
            executionTimeMs = executionTime,
            memoryUsedKb = memoryUsed,
            cpuUsagePercent = estimateCpuUsage(executionTime),
            allocatedHeapMb = heapAfter / 1024 / 1024
        )
    }
    
    /**
     * Profiles Chroma extraction.
     */
    fun profileChroma(spectrum: FloatArray): PerformanceMetrics {
        val heapBefore = Runtime.getRuntime().totalMemory()
        
        val executionTime = measureTimeMillis {
            val chromaAnalyzer = com.example.audio.dsp.ChromaAnalyzer()
            chromaAnalyzer.computeChroma(spectrum)
        }
        
        val heapAfter = Runtime.getRuntime().totalMemory()
        val memoryUsed = (heapAfter - heapBefore) / 1024
        
        return PerformanceMetrics(
            operationName = "Chroma Analysis",
            executionTimeMs = executionTime,
            memoryUsedKb = memoryUsed,
            cpuUsagePercent = estimateCpuUsage(executionTime),
            allocatedHeapMb = heapAfter / 1024 / 1024
        )
    }
    
    /**
     * Profiles full audio analysis pipeline.
     */
    fun profileFullPipeline(audioFrame: FloatArray): PerformanceMetrics {
        val heapBefore = Runtime.getRuntime().totalMemory()
        
        val executionTime = measureTimeMillis {
            // Simulate full pipeline
            val fftAnalyzer = com.example.audio.dsp.FFTAnalyzer(2048)
            val spectrum = fftAnalyzer.computeMagnitudeSpectrum(audioFrame)
            
            val hpssProcessor = com.example.audio.dsp.HPSSProcessor()
            val stems = hpssProcessor.separateFrame(audioFrame)
            
            val chromaAnalyzer = com.example.audio.dsp.ChromaAnalyzer()
            chromaAnalyzer.computeChroma(stems.harmonic)
            
            val keyDetector = com.example.audio.dsp.KeyDetector()
            keyDetector.detectKey(chromaAnalyzer.getChromaVector())
        }
        
        val heapAfter = Runtime.getRuntime().totalMemory()
        val memoryUsed = (heapAfter - heapBefore) / 1024
        
        return PerformanceMetrics(
            operationName = "Full Pipeline",
            executionTimeMs = executionTime,
            memoryUsedKb = memoryUsed,
            cpuUsagePercent = estimateCpuUsage(executionTime),
            allocatedHeapMb = heapAfter / 1024 / 1024
        )
    }
    
    /**
     * Runs comprehensive benchmark suite.
     */
    fun runFullBenchmark(audioFrame: FloatArray): BenchmarkResults {
        Log.i(TAG, "Starting comprehensive benchmark...")
        
        val results = mutableListOf<PerformanceMetrics>()
        
        // FFT benchmark
        repeat(10) {
            results.add(profileFFT(audioFrame))
        }
        val fftAvg = results.filter { it.operationName == "FFT Analysis" }.average { it.executionTimeMs }
        
        // HPSS benchmark
        results.add(profileHPSS(audioFrame))
        
        // Chroma benchmark
        val spectrum = FloatArray(1024) { 0.1f }
        repeat(10) {
            results.add(profileChroma(spectrum))
        }
        val chromaAvg = results.filter { it.operationName == "Chroma Analysis" }.average { it.executionTimeMs }
        
        // Full pipeline benchmark
        repeat(5) {
            results.add(profileFullPipeline(audioFrame))
        }
        val pipelineAvg = results.filter { it.operationName == "Full Pipeline" }.average { it.executionTimeMs }
        
        return BenchmarkResults(
            totalFramesProcessed = results.size,
            fftAverageMs = fftAvg,
            chromaAverageMs = chromaAvg,
            pipelineAverageMs = pipelineAvg,
            maxMemoryMb = results.maxOf { it.allocatedHeapMb },
            allMetrics = results
        )
    }
    
    /**
     * Estimates CPU usage as percentage of frame time budget.
     * At 44.1kHz with 2048 samples, each frame = 46.4ms.
     */
    private fun estimateCpuUsage(executionTimeMs: Long): Float {
        val frameTimeMs = 46.4f  // 2048 samples @ 44.1kHz
        return (executionTimeMs.toFloat() / frameTimeMs) * 100f
    }
}

/**
 * Aggregated benchmark results.
 */
data class BenchmarkResults(
    val totalFramesProcessed: Int,
    val fftAverageMs: Double,
    val chromaAverageMs: Double,
    val pipelineAverageMs: Double,
    val maxMemoryMb: Long,
    val allMetrics: List<PerformanceMetrics>
) {
    fun getSummary(): String {
        return """
            ╔════════════════════════════════════════════════════╗
            ║          PERFORMANCE BENCHMARK RESULTS             ║
            ╠════════════════════════════════════════════════════╣
            ║ Frames Processed:     $totalFramesProcessed
            ║ FFT Average:          $fftAverageMs ms
            ║ Chroma Average:       $chromaAverageMs ms
            ║ Full Pipeline Avg:    $pipelineAverageMs ms
            ║ Peak Memory:          $maxMemoryMb MB
            ║ Status:               ${getStatus()}
            ╚════════════════════════════════════════════════════╝
        """.trimIndent()
    }
    
    private fun getStatus(): String {
        return when {
            pipelineAverageMs > 50 -> "⚠️  SLOW (>50ms)"
            pipelineAverageMs > 46 -> "✓ MARGINAL (46-50ms)"
            pipelineAverageMs > 20 -> "✓ GOOD (<46ms)"
            else -> "✓ EXCELLENT (<20ms)"
        }
    }
}
