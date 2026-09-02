package com.example.viewmodel

import android.content.Context
import com.example.audio.AudioAnalysisPipeline
import com.example.audio.ChordPrediction
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import kotlinx.coroutines.test.runTest

class ViewModelIntegrationTest {
    private lateinit var viewModel: AudioAnalysisViewModel
    private lateinit var workstationViewModel: WorkstationViewModel
    private val mockContext: Context = mock(Context::class.java)
    
    @Before
    fun setUp() {
        val pipeline = AudioAnalysisPipeline(mockContext, enableTFLite = false)
        viewModel = AudioAnalysisViewModel(pipeline)
        workstationViewModel = WorkstationViewModel(mockContext)
    }
    
    @Test
    fun testAnalyzeAudioFrameUpdatesState() = runTest {
        val samples = FloatArray(2048) { 0.1f }
        viewModel.startAnalysis()
        viewModel.analyzeAudioFrame(samples)
        
        // State should be updated
        val state = viewModel.analysisState.value
        assertTrue(state.isAnalyzing, "Should be analyzing")
    }
    
    @Test
    fun testChordPredictionSmoothing() = runTest {
        val samples = FloatArray(2048) { 0.1f }
        viewModel.startAnalysis()
        
        // Process multiple frames
        repeat(5) {
            viewModel.analyzeAudioFrame(samples)
        }
        
        val state = viewModel.analysisState.value
        
        // Should have chord predictions
        assertTrue(state.chordPredictions.isNotEmpty(), "Should have predictions")
        // Confidence should be smoothed
        assertTrue(state.chordConfidence in 0f..100f)
    }
    
    @Test
    fun testStartStopAnalysis() = runTest {
        viewModel.startAnalysis()
        assertTrue(viewModel.analysisState.value.isAnalyzing)
        
        viewModel.stopAnalysis()
        val finalState = viewModel.analysisState.value
        assertTrue(!finalState.isAnalyzing)
    }
    
    @Test
    fun testWorkstationStateUpdates() = runTest {
        val samples = FloatArray(2048) { 0.1f }
        workstationViewModel.startLiveAnalysis()
        workstationViewModel.processAudioFrame(samples)
        
        val state = workstationViewModel.workstationState.value
        assertTrue(state.isLive, "Should be live")
    }
    
    @Test
    fun testDisplayModeSwitch() = runTest {
        workstationViewModel.setDisplayMode(DisplayMode.KEY)
        assertEquals(DisplayMode.KEY, workstationViewModel.workstationState.value.displayMode)
        
        workstationViewModel.setDisplayMode(DisplayMode.TEMPO)
        assertEquals(DisplayMode.TEMPO, workstationViewModel.workstationState.value.displayMode)
    }
    
    @Test
    fun testReset() = runTest {
        val samples = FloatArray(2048) { 0.1f }
        viewModel.startAnalysis()
        viewModel.analyzeAudioFrame(samples)
        
        assertTrue(viewModel.analysisState.value.isAnalyzing)
        
        viewModel.reset()
        
        val resetState = viewModel.analysisState.value
        assertTrue(!resetState.isAnalyzing)
        assertTrue(resetState.chordPredictions.isEmpty())
    }
}
