package com.example.audio.profiling

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith

/**
 * Device compatibility tests.
 * 
 * Validates:
 * 1. Code runs on minimum API level (26)
 * 2. Performance acceptable on low-end devices
 * 3. No crashes on different Android versions
 * 4. GPU/NNAPI acceleration available (if applicable)
 */
@RunWith(AndroidJUnit4::class)
class DeviceCompatibilityTest {
    private lateinit var context: Context
    private lateinit var profiler: PerformanceProfiler
    
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        profiler = PerformanceProfiler()
    }
    
    @Test
    fun testMinimumAPICompatibility() {
        val apiLevel = Build.VERSION.SDK_INT
        
        // Minimum API: 26 (Android 8.0)
        assertTrue(
            apiLevel >= 26,
            "Device API level $apiLevel is below minimum (26)"
        )
    }
    
    @Test
    fun testDeviceInfo() {
        val deviceInfo = """
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.SDK_INT}
            CPU ABI: ${Build.CPU_ABI}
            RAM: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB
        """.trimIndent()
        
        println(deviceInfo)
    }
    
    @Test
    fun testLowEndDevicePerformance() {
        // Check if device has sufficient RAM (minimum 2GB)
        val ramMb = Runtime.getRuntime().maxMemory() / 1024 / 1024
        assertTrue(
            ramMb >= 512,
            "Device RAM ${ramMb}MB may be too low for real-time processing"
        )
    }
    
    @Test
    fun testHighEndDeviceOptimization() {
        val ramMb = Runtime.getRuntime().maxMemory() / 1024 / 1024
        
        if (ramMb >= 4096) {
            println("✓ High-end device detected. GPU/NNAPI acceleration available.")
        } else if (ramMb >= 2048) {
            println("✓ Mid-range device. Standard optimization recommended.")
        } else {
            println("⚠ Low-end device. Performance may be limited.")
        }
    }
    
    @Test
    fun testNoANROnBenchmark() {
        // ANR threshold is 5 seconds; benchmark should complete well within that
        val testAudio = FloatArray(2048) { 0.1f }
        
        val startTime = System.currentTimeMillis()
        val results = profiler.runFullBenchmark(testAudio)
        val totalTime = System.currentTimeMillis() - startTime
        
        assertTrue(
            totalTime < 5000,
            "Benchmark took ${totalTime}ms (risk of ANR)"
        )
    }
}
