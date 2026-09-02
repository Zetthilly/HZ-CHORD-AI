# Performance Profiling Guide

## Overview

This guide explains how to run performance benchmarks and device compatibility tests for HZ-CHORD-AI.

## Test Suites

### 1. AudioAnalysisPerformanceTest
**Location:** `app/src/androidTest/java/com/example/audio/profiling/AudioAnalysisPerformanceTest.kt`

Tests individual DSP component performance:
- FFT latency (<25ms)
- Chroma extraction (<10ms)
- HPSS separation (<30ms)
- Full pipeline (<50ms)
- Memory usage (<500MB)
- No memory leaks over 100 frames
- CPU usage reasonable

**Run:**
```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.audio.profiling.AudioAnalysisPerformanceTest
```

**Expected Output:**
```
╔════════════════════════════════════════════════════╗
║          PERFORMANCE BENCHMARK RESULTS             ║
╠════════════════════════════════════════════════════╣
║ Frames Processed:     26
║ FFT Average:          12.5 ms
║ Chroma Average:       3.2 ms
║ Full Pipeline Avg:    28.1 ms
║ Peak Memory:          145 MB
║ Status:               ✓ GOOD (<46ms)
╚════════════════════════════════════════════════════╝
```

### 2. DeviceCompatibilityTest
**Location:** `app/src/androidTest/java/com/example/audio/profiling/DeviceCompatibilityTest.kt`

Tests device compatibility:
- Minimum API level (26+)
- Available RAM (512MB+)
- No ANRs during benchmarking
- Device-specific optimizations

**Run:**
```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.audio.profiling.DeviceCompatibilityTest
```

**Expected Output:**
```
Device: Samsung SM-G991B (Galaxy S21)
Android: 33
CPU ABI: arm64-v8a
RAM: 8192MB

✓ High-end device detected. GPU/NNAPI acceleration available.
```

### 3. E2EAudioAnalysisTest
**Location:** `app/src/androidTest/java/com/example/audio/profiling/E2EAudioAnalysisTest.kt`

End-to-end integration tests:
- Single frame analysis
- Multi-frame analysis (50 frames = 2.3 seconds)
- Chord stability (>60% consistency)
- Recording mode
- Display mode switching
- Error recovery
- Real-time constraints
- State consistency

**Run:**
```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.audio.profiling.E2EAudioAnalysisTest
```

**Expected Output:**
```
Frame timing: avg=28.5ms, max=42ms
✓ Chord detection stable: 75% consistency
✓ All confidence values in [0, 100]
✓ Realistic tempo: 95.2 BPM
```

## Running All Tests

```bash
# Run all instrumented tests
./gradlew connectedAndroidTest

# Run all unit + instrumented tests
./gradlew test connectedAndroidTest

# Run with specific device
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunner.device=emulator-5554
```

## Profiling PerformanceProfiler

The `PerformanceProfiler` class provides on-device benchmarking:

```kotlin
val profiler = PerformanceProfiler()
val audioFrame = FloatArray(2048) { /* audio data */ }

// Profile individual components
val fftMetrics = profiler.profileFFT(audioFrame)
Log.i("Profile", "FFT: ${fftMetrics.executionTimeMs}ms")

val chromaMetrics = profiler.profileChroma(spectrum)
Log.i("Profile", "Chroma: ${chromaMetrics.executionTimeMs}ms")

// Profile full pipeline
val pipelineMetrics = profiler.profileFullPipeline(audioFrame)
Log.i("Profile", "Pipeline: ${pipelineMetrics.executionTimeMs}ms")

// Run comprehensive benchmark
val results = profiler.runFullBenchmark(audioFrame)
Log.i("Profile", results.getSummary())
```

## Interpreting Results

### Latency Targets

| Component | Target | Good | Acceptable | Poor |
|-----------|--------|------|------------|------|
| FFT | <25ms | <20ms | <25ms | >30ms |
| Chroma | <10ms | <5ms | <10ms | >15ms |
| HPSS | <30ms | <20ms | <30ms | >40ms |
| Full Pipeline | <50ms | <35ms | <50ms | >60ms |

**Note:** At 44.1kHz with 2048 samples, one frame = 46.4ms duration.
Processing must complete within this window for real-time analysis.

### Memory Targets

| Device Class | Max Heap | Good | Acceptable | Poor |
|--------------|----------|------|------------|------|
| Low-end (<2GB RAM) | 128MB | <50MB | <80MB | >100MB |
| Mid-range (2-4GB) | 256MB | <100MB | <150MB | >200MB |
| High-end (>4GB) | 512MB | <200MB | <300MB | >400MB |

### CPU Usage

- **CPU Usage < 30%:** Excellent (plenty of headroom)
- **CPU Usage 30-70%:** Good (comfortable real-time)
- **CPU Usage 70-100%:** Acceptable (tight but workable)
- **CPU Usage > 100%:** Poor (real-time is impossible)

## Device Profiling

### Supported Devices

**Minimum Requirements:**
- Android 8.0 (API 26)
- 512MB free RAM
- Snapdragon 400 or equivalent

**Recommended:**
- Android 10+ (API 29+)
- 2GB+ RAM
- Snapdragon 800 or equivalent

### Test on Multiple Devices

```bash
# List connected devices
adb devices

# Run on specific device
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunner.device=emulator-5554

# Run on all connected devices
./gradlew connectedAndroidTest
```

### Common Device Issues

#### Issue: Tests timeout on low-end device
**Solution:** Increase timeout in build.gradle.kts:
```kotlin
android {
    testOptions {
        execution 'ANDROIDX_TEST_ORCHESTRATOR'
        animationsDisabled = true  // Speed up emulator
    }
}
```

#### Issue: GPU/NNAPI not available
**Cause:** Device doesn't support acceleration  
**Impact:** 10-20% slower inference  
**Mitigation:** Fallback to CPU inference (automatic in TFLiteChordModel)

#### Issue: High jitter in latency
**Cause:** Garbage collection, thermal throttling, or background apps  
**Solution:**
1. Close other apps before testing
2. Use `System.gc()` between measurements
3. Let device cool before intensive benchmarking
4. Use airplane mode to reduce OS noise

## Continuous Profiling

To monitor performance in production (analytics):

```kotlin
class AnalyticsProfiler {
    fun logFrameMetrics(metrics: PerformanceMetrics) {
        FirebaseAnalytics.getInstance().logEvent("audio_frame_processed") {
            param("latency_ms", metrics.executionTimeMs.toString())
            param("memory_kb", metrics.memoryUsedKb.toString())
            param("cpu_percent", metrics.cpuUsagePercent.toString())
        }
    }
}
```

## Performance Optimization Tips

### 1. Reduce FFT Size
```kotlin
// Use 1024-point FFT instead of 2048 (faster but less frequency resolution)
val fftAnalyzer = FFTAnalyzer(1024)  // 2x faster
```

### 2. Enable GPU Acceleration
```kotlin
val options = Interpreter.Options()
options.setUseGpuDelegate(true)
interpreter = Interpreter(model, options)
```

### 3. Reduce Temporal Smoothing
```kotlin
val FRAME_SMOOTHING_WINDOW = 1  // Process immediately (vs default 3)
```

### 4. Disable Spectrum Collection
```kotlin
val result = pipeline.analyzeFrame(audioFrame, includeSpectrum = false)  // Faster
```

### 5. Skip BPM Estimation
```kotlin
// Only compute BPM every N frames instead of continuously
if (frameCount % 100 == 0) {
    val bpm = bpmDetector.estimateBpm()
}
```

## Regression Testing

Run benchmarks before/after optimization:

```bash
# Baseline
./gradlew connectedAndroidTest > baseline.txt

# Make changes
# ... optimize code ...

# Compare
./gradlew connectedAndroidTest > optimized.txt
diff baseline.txt optimized.txt
```

## CI/CD Integration

Run performance tests in GitHub Actions:

```yaml
# .github/workflows/performance.yml
name: Performance Tests
on: [push, pull_request]

jobs:
  benchmark:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: ./gradlew connectedAndroidTest
      - run: cat build/reports/profile/performance.txt
      - uses: actions/upload-artifact@v3
        with:
          name: benchmark-results
          path: build/reports/profile/
```

## Further Reading

- [Android Performance Documentation](https://developer.android.com/topic/performance)
- [Jetpack Benchmark Library](https://developer.android.com/training/testing/benchmark)
- [TensorFlow Lite Performance Guide](https://www.tensorflow.org/lite/performance/optimization)
