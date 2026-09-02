package com.example.audio.profiling

import android.content.Context
import com.example.audio.AudioAnalysisPipeline
import com.example.viewmodel.AudioAnalysisViewModel
import com.example.viewmodel.WorkstationViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import kotlinx.coroutines.test.runTest

/**
 * End-to-end integration tests for complete audio analysis pipeline.
 * 
 * Tests:
 * 1. Full frame processing (audio → analysis → UI state)
 * 2. Real-time constraints (50ms latency)
 * 3. State consistency across components
 * 4. Multi-frame analysis (key detection, BPM estimation)
 * 5. Error recovery and edge cases
 */
@RunWith(AndroidJUnit4::class)
class E2EAudioAnalysisTest {
    private lateinit var context: Context
    private lateinit var workstationViewModel: WorkstationViewModel
    private lateinit var testAudio: FloatArray
    
    @Before
    fun setUp() {
        context = mock(Context::class.java)
        workstationViewModel = WorkstationViewModel(context)
        
        // Generate test audio
        val sampleRate = 44100
        val freq = 440f
        testAudio = FloatArray(2048) { i ->
            sin(2f * PI * freq * i / sampleRate).toFloat()
        }
    }
    
    @Test
    fun testSingleFrameAnalysis() = runTest {
        workstationViewModel.startLiveAnalysis()
        workstationViewModel.processAudioFrame(testAudio)
        
        val state = workstationViewModel.workstationState.value
        assertTrue(state.isLive, "Should be in live mode")
        assertTrue(state.primaryChord != "--", "Should detect a chord")
    }
    
    @Test
    fun testMultiFrameAnalysis() = runTest {
        workstationViewModel.startLiveAnalysis()
        
        // Process 50 frames (2.3 seconds at 44.1kHz)
        repeat(50) {
            workstationViewModel.processAudioFrame(testAudio)
            Thread.sleep(10)  // Simulate real-time spacing
        }
        
        val state = workstationViewModel.workstationState.value
        assertTrue(state.primaryChord != "--", "Should detect chord")
        // BPM might be available after many frames
        assertTrue(state.tempo >= 0f, "Tempo should be non-negative")
    }
    
    @Test
    fun testChordStability() = runTest {
        workstationViewModel.startLiveAnalysis()
        
        val detectedChords = mutableListOf<String>()
        repeat(10) {
            workstationViewModel.processAudioFrame(testAudio)
            val state = workstationViewModel.workstationState.value
            detectedChords.add(state.primaryChord)
        }
        
        // Primary chord should be relatively stable (same for most frames)
        val mostCommon = detectedChords.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val stability = (detectedChords.count { it == mostCommon } / detectedChords.size.toFloat()) * 100
        
        assertTrue(
            stability > 60f,
            "Chord detection too unstable: $stability% consistency"
        )
    }
    
    @Test
    fun testRecordingMode() = runTest {
        workstationViewModel.startRecording()
        assertTrue(workstationViewModel.workstationState.value.isRecording)
        
        repeat(5) {
            workstationViewModel.processAudioFrame(testAudio)
        }
        
        workstationViewModel.stopRecording()
        assertTrue(!workstationViewModel.workstationState.value.isRecording)
    }
    
    @Test
    fun testDisplayModeSwitching() = runTest {
        workstationViewModel.startLiveAnalysis()
        workstationViewModel.processAudioFrame(testAudio)
        
        // Switch modes
        com.example.viewmodel.DisplayMode.values().forEach { mode ->
            workstationViewModel.setDisplayMode(mode)
            val state = workstationViewModel.workstationState.value
            assertTrue(state.displayMode == mode, "Display mode not updated")
        }
    }
    
    @Test
    fun testErrorRecovery() = runTest {
        workstationViewModel.startLiveAnalysis()
        
        // Process valid frame
        workstationViewModel.processAudioFrame(testAudio)
        
        // Try to process invalid frame (should not crash)
        val invalidFrame = FloatArray(0)  // Empty
        try {
            // This might be caught by ViewModel, or might throw - both are acceptable
            workstationViewModel.processAudioFrame(invalidFrame)
        } catch (e: Exception) {
            println("Caught expected error: ${e.message}")
        }
        
        // Should still be able to recover
        workstationViewModel.processAudioFrame(testAudio)
        assertTrue(workstationViewModel.workstationState.value.isLive)
    }
    
    @Test
    fun testRealTimeConstraints() = runTest {
        workstationViewModel.startLiveAnalysis()
        
        val frameTimes = mutableListOf<Long>()
        
        repeat(20) {
            val startTime = System.currentTimeMillis()
            workstationViewModel.processAudioFrame(testAudio)
            val frameTime = System.currentTimeMillis() - startTime
            frameTimes.add(frameTime)
        }
        
        val avgFrameTime = frameTimes.average()
        val maxFrameTime = frameTimes.maxOrNull() ?: 0L
        
        println("Frame timing: avg=${avgFrameTime}ms, max=${maxFrameTime}ms")
        
        // All frames should process within 50ms
        assertTrue(
            maxFrameTime < 50,
            "Frame processing time ${maxFrameTime}ms exceeds real-time budget (50ms)"
        )
    }
    
    @Test
    fun testStateConsistency() = runTest {
        workstationViewModel.startLiveAnalysis()
        
        repeat(5) {
            workstationViewModel.processAudioFrame(testAudio)
        }
        
        val state = workstationViewModel.workstationState.value
        
        // All confidence values should be in [0, 100]
        assertTrue(state.chordConfidence in 0f..100f)
        assertTrue(state.keyConfidence in 0f..100f)
        assertTrue(state.tempoConfidence in 0f..100f)
        
        // Tempo should be reasonable (40-300 BPM)
        if (state.tempo > 0f) {
            assertTrue(state.tempo in 40f..300f, "Unrealistic BPM: ${state.tempo}")
        }
    }
}
