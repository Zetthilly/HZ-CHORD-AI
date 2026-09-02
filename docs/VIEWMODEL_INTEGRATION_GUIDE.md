# ViewModel Integration Guide

## Overview

This guide covers integrating the DSP analysis pipeline with Jetpack Compose UI using ViewModels.

## Architecture

### Data Flow

```
HZAudioEngine (Recorder/Playback)
    ↓ PCM frames
WorkstationViewModel.processAudioFrame()
    ↓
AudioAnalysisViewModel.analyzeAudioFrame()
    ↓
AudioAnalysisPipeline (FFT → HPSS → Chroma → ML)
    ↓
AudioAnalysisState (via StateFlow)
    ↓
WorkstationState (aggregated UI state)
    ↓
Compose UI (reactive updates)
```

## Components

### 1. AudioAnalysisViewModel

**Responsibility:** Manage audio frame processing and temporal smoothing.

```kotlin
class AudioAnalysisViewModel(pipeline: AudioAnalysisPipeline) : ViewModel()
```

**Key Methods:**
- `analyzeAudioFrame(audioFrame: FloatArray)` — Process PCM frame
- `startAnalysis()` — Begin analysis session
- `stopAnalysis()` — End session, finalize BPM
- `reset()` — Clear all state

**State:**
```kotlin
data class AudioAnalysisState(
    val isAnalyzing: Boolean,
    val chordPredictions: List<ChordPrediction>,  // Top 3
    val currentChord: String,                      // Primary chord
    val chordConfidence: Float,                    // 0-100
    val keyInfo: DetectedKeyInfo?,
    val bpmEstimate: BpmEstimate?,
    val processingTimeMs: Long,
    val errorMessage: String?
)
```

### 2. WorkstationViewModel

**Responsibility:** Aggregate analysis state and manage UI interactions.

```kotlin
class WorkstationViewModel(context: Context) : ViewModel()
```

**Key Methods:**
- `processAudioFrame(audioFrame: FloatArray)` — Pass frame to analysis
- `startLiveAnalysis()` — Start real-time analysis
- `stopLiveAnalysis()` — Stop real-time analysis
- `startRecording()` — Record for offline analysis
- `stopRecording()` — Stop recording
- `setDisplayMode(mode: DisplayMode)` — Switch UI display
- `reset()` — Clear state

**State:**
```kotlin
data class WorkstationState(
    val detectedChords: List<ChordPrediction>,
    val primaryChord: String,                  // Main display
    val chordConfidence: Float,
    val keySignature: String,                  // e.g., "C Major"
    val keyConfidence: Float,
    val tempo: Float,                          // BPM
    val tempoConfidence: Float,
    val isLive: Boolean,
    val isRecording: Boolean,
    val displayMode: DisplayMode,              // What to show
    val errorMessage: String?
)

enum class DisplayMode {
    CHORDS, KEY, TEMPO, FULL, SPECTRUM
}
```

## Integration with Compose UI

### Step 1: Create ViewModel in Activity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            HZChordAITheme {
                val viewModel = viewModel<WorkstationViewModel>()
                WorkstationScreen(viewModel)
            }
        }
    }
}
```

### Step 2: Observe State in Compose

```kotlin
@Composable
fun WorkstationScreen(viewModel: WorkstationViewModel) {
    val state by viewModel.workstationState.collectAsState()
    
    Column {
        // Primary chord display
        Text(
            text = state.primaryChord,
            fontSize = 60.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Confidence meter
        LinearProgressIndicator(
            progress = state.chordConfidence / 100f,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Alternative predictions
        state.detectedChords.drop(1).forEach { chord ->
            ChordOption(
                chord.chordName,
                chord.confidence
            )
        }
        
        // Key signature
        Row {
            Text("Key: ${state.keySignature}")
            Text("(${String.format("%.0f", state.keyConfidence)}%)")
        }
        
        // Tempo
        Row {
            Text("Tempo: ${String.format("%.1f", state.tempo)} BPM")
            Text("(${String.format("%.0f", state.tempoConfidence)}%)")
        }
        
        // Controls
        Row {
            Button(onClick = { viewModel.startLiveAnalysis() }) {
                Text("Start")
            }
            Button(onClick = { viewModel.stopLiveAnalysis() }) {
                Text("Stop")
            }
            Button(onClick = { viewModel.reset() }) {
                Text("Reset")
            }
        }
        
        // Error display
        state.errorMessage?.let { error ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.Red)) {
                Text(error, color = Color.White)
            }
        }
    }
}
```

### Step 3: Connect Audio Engine

```kotlin
class HZAudioEngine(private val viewModel: WorkstationViewModel) {
    
    fun onAudioFrameReady(audioFrame: FloatArray) {
        // Called from audio callback (e.g., AudioRecord or ExoPlayer)
        viewModel.processAudioFrame(audioFrame)
    }
}
```

Or with Media3/ExoPlayer:

```kotlin
val audioProcessor = object : AudioProcessor {
    override fun onOutputFrameAvailable(presentationTimeUs: Long) {}
    
    override fun getOutput(): ByteBuffer? {
        // Extract PCM samples
        val pcmFrame = extractPcmFrame()  // Your extraction logic
        viewModel.processAudioFrame(pcmFrame)
        return null
    }
}
```

## Display Modes

### CHORDS Mode
Shows top 3 chord predictions with confidence.

```kotlin
DisplayMode.CHORDS
```

**UI:**
```
┌─────────────────────┐
│     C Major (95%)   │  ← Primary
├─────────────────────┤
│  G Major (4%)       │  ← Alternative 1
│  F Major (1%)       │  ← Alternative 2
└─────────────────────┘
```

### KEY Mode
Shows detected key signature.

```kotlin
DisplayMode.KEY
```

**UI:**
```
┌─────────────────────┐
│  C Major (92%)      │
│  (or A Minor)       │
└─────────────────────┘
```

### TEMPO Mode
Shows BPM with confidence.

```kotlin
DisplayMode.TEMPO
```

**UI:**
```
┌─────────────────────┐
│   124 BPM (88%)     │
│   ♩ = 124           │
└─────────────────────┘
```

### FULL Mode
Shows all three (chords, key, tempo).

### SPECTRUM Mode
Debug: Shows FFT spectrum and HPSS separation.

## Performance Considerations

### Frame Processing Latency
- Target: < 50ms per frame
- At 44.1 kHz, 2048 samples = 46.4ms duration
- Total latency = FFT (20ms) + HPSS (5ms) + Chroma (5ms) + ML (10ms) + UI (5ms) = ~45ms ✓

### Temporal Smoothing
- Default: Average over 3 frames (138ms window)
- Reduces false positives and flicker
- Increases latency but improves UX

### Memory Usage
- Pipeline: ~10MB (FFT buffers + history)
- ViewModel state: ~1MB
- UI: ~5MB (Compose recomposition)
- Total: ~16MB (acceptable for modern phones)

## Testing

### Unit Tests
```kotlin
@Test
fun testAnalyzeAudioFrameUpdatesState() = runTest {
    val viewModel = AudioAnalysisViewModel(mockPipeline)
    viewModel.startAnalysis()
    viewModel.analyzeAudioFrame(testSamples)
    
    val state = viewModel.analysisState.value
    assertTrue(state.isAnalyzing)
    assertTrue(state.chordPredictions.isNotEmpty())
}
```

### Integration Tests
```kotlin
@Test
fun testEndToEndAnalysis() = runTest {
    val viewModel = WorkstationViewModel(context)
    
    viewModel.startLiveAnalysis()
    repeat(100) { viewModel.processAudioFrame(testAudio) }
    viewModel.stopLiveAnalysis()
    
    val state = viewModel.workstationState.value
    assertTrue(state.primaryChord != "--")
    assertTrue(state.tempo > 0f)
}
```

## Debugging

### Get Pipeline Status
```kotlin
val status = viewModel.analysisViewModel.getPipelineStatus()
Log.i("Debug", status)
// Output: "AudioAnalysisPipeline: TFLite ready ([1, 12] -> [1, 108])"
```

### Enable Spectrum Display
```kotlin
val result = pipeline.analyzeFrame(audioFrame, includeSpectrum = true)
Log.d("Spectrum", result.harmonicSpectrum?.take(10)?.toString())
Log.d("Spectrum", result.chromaVector?.contentToString())
```

### Monitor Processing Time
```kotlin
val state = viewModel.analysisState.value
if (state.processingTimeMs > 50) {
    Log.w("Performance", "Slow frame: ${state.processingTimeMs}ms")
}
```

## Common Issues

### Chord predictions always "--"
**Cause:** Analysis not started or no audio input  
**Fix:** Call `viewModel.startLiveAnalysis()` and verify audio is flowing

### High processing latency (>100ms)
**Cause:** ML model inference is slow  
**Fix:** Enable GPU/NNAPI in TFLiteChordModel, or disable TFLite and use fallback

### Chords flicker between different predictions
**Cause:** Temporal smoothing window too small  
**Fix:** Increase FRAME_SMOOTHING_WINDOW (from 3 to 5)

### OutOfMemory errors
**Cause:** Too many frames in smoothing buffer  
**Fix:** Reduce buffer size or increase cleanup frequency

## Future Enhancements

1. **Chord Transitions:** Detect when chord changes and animate transitions
2. **Harmonic Movement:** Show chord progression (e.g., "I → V → vi → IV")
3. **Scale Detection:** Show available notes based on detected key
4. **Playback Sync:** Sync chord display with playhead position
5. **Recording Markers:** Mark sections (verse, chorus, bridge)
6. **Metronome:** Generate click track at detected BPM
