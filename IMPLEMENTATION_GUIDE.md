# HZ CHORD AI — Implementation Roadmap

## Overview
This document guides implementation of features promised in README.md but not yet complete in code. Organized by priority, complexity, and dependencies.

---

## Phase 1: Core DSP Algorithms (Critical Foundation)

### 1.1 FFT & Frequency Domain Analysis
**Location:** `app/src/main/java/com/example/audio/dsp/FFTAnalyzer.kt`
**Dependency:** Apache Commons Math or custom FFT

```kotlin
package com.example.audio.dsp

import kotlin.math.*

/**
 * Real-time FFT analysis using Cooley-Tukey algorithm.
 * Input: float audio samples (2048–4096 samples @ 44.1kHz)
 * Output: Magnitude spectrum (0–22.05 kHz)
 */
class FFTAnalyzer(private val fftSize: Int = 2048) {
    private val magnitude = FloatArray(fftSize / 2)
    private val phase = FloatArray(fftSize / 2)
    
    init {
        require(fftSize and (fftSize - 1) == 0) { "FFT size must be power of 2" }
    }
    
    /**
     * Computes FFT magnitude spectrum.
     * @param samples Input PCM float samples (assumed windowed)
     * @return Magnitude spectrum in dB (0–180 dB range)
     */
    fun computeMagnitudeSpectrum(samples: FloatArray): FloatArray {
        require(samples.size >= fftSize) { "Input must be >= $fftSize samples" }
        
        // Radix-2 Cooley-Tukey FFT
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        
        // Copy + window with Hann
        for (i in 0 until fftSize) {
            val window = 0.5f * (1f - cos(2f * PI * i / (fftSize - 1)))
            real[i] = samples[i] * window
        }
        
        fft(real, imag)
        
        // Convert to magnitude in dB
        for (i in 0 until fftSize / 2) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            magnitude[i] = 20f * log10(mag.coerceAtLeast(1e-6f))
            phase[i] = atan2(imag[i], real[i])
        }
        
        return magnitude
    }
    
    /**
     * Radix-2 Cooley-Tukey FFT (in-place)
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return
        
        // Bit-reversal permutation
        for (i in 0 until n) {
            var j = 0
            var m = n
            var ii = i
            while (m > 1) {
                j = j * 2 + (ii and 1)
                ii = ii shr 1
                m = m shr 1
            }
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }
        
        // FFT butterflies
        var step = 2
        while (step <= n) {
            val halfStep = step / 2
            val theta = -2f * PI / step
            
            for (k in 0 until halfStep) {
                val w_real = cos(k * theta)
                val w_imag = sin(k * theta)
                
                var j = k
                while (j < n) {
                    val t_real = w_real * real[j + halfStep] - w_imag * imag[j + halfStep]
                    val t_imag = w_real * imag[j + halfStep] + w_imag * real[j + halfStep]
                    
                    real[j + halfStep] = real[j] - t_real
                    imag[j + halfStep] = imag[j] - t_imag
                    real[j] += t_real
                    imag[j] += t_imag
                    
                    j += step
                }
            }
            step = step shl 1
        }
    }
    
    fun getMagnitude(): FloatArray = magnitude
    fun getPhase(): FloatArray = phase
}
```

**Usage in HZAudioEngine.kt (lines 381–390):**
```kotlin
// Replace: harmonicTranscriber.analyzePcmBuffer(pcmSlice)
private val fftAnalyzer = FFTAnalyzer(2048)

// In position ticker:
val spectrum = fftAnalyzer.computeMagnitudeSpectrum(pcmSlice)
updatePeakFrequency(spectrum)  // Update diagnostics
```

---

### 1.2 Chroma Vectors & Pitch Class Distribution
**Location:** `app/src/main/java/com/example/audio/dsp/ChromaAnalyzer.kt`

```kotlin
package com.example.audio.dsp

import kotlin.math.*

/**
 * Chroma vector (pitch class energy) from FFT spectrum.
 * Maps 12 pitch classes (C, C#, D, ..., B) to energy.
 * Used for chord detection.
 */
class ChromaAnalyzer(
    private val sampleRateHz: Int = 44100,
    private val fftSize: Int = 2048
) {
    private val chromaVector = FloatArray(12) // C, C#, D, D#, E, F, F#, G, G#, A, A#, B
    private val noteFrequencies = floatArrayOf(
        16.35f, 17.32f, 18.35f, 19.45f, 20.60f, 21.83f, 23.12f, 24.50f, 25.96f, 27.50f, 29.14f, 30.87f // Octave -1
    )
    
    /**
     * Computes chroma vector from FFT magnitude spectrum.
     * @param magnitudeSpectrum FFT output in dB (fftSize/2 bins)
     * @return 12-element chroma vector (pitch class distribution)
     */
    fun computeChroma(magnitudeSpectrum: FloatArray): FloatArray {
        chromaVector.fill(0f)
        
        val binFreq = sampleRateHz.toFloat() / fftSize
        
        // Iterate over FFT bins and map to chroma
        for (bin in 0 until magnitudeSpectrum.size) {
            val freq = bin * binFreq
            if (freq < 20f || freq > 4000f) continue // Focus on audible pitch range
            
            // Convert frequency to MIDI note number
            val midiNote = 12f * log2(freq / noteFrequencies[0])
            val chromaClass = (midiNote % 12f).toInt().coerceIn(0, 11)
            val linearMag = 10f.pow(magnitudeSpectrum[bin] / 20f) // Convert dB to linear
            
            chromaVector[chromaClass] += linearMag
        }
        
        // Normalize to unit vector
        val sum = chromaVector.sum()
        if (sum > 0f) {
            for (i in chromaVector.indices) {
                chromaVector[i] /= sum
            }
        }
        
        return chromaVector
    }
    
    fun getChromaVector(): FloatArray = chromaVector
}
```

---

### 1.3 Key Detection (Krumhansl Profiles)
**Location:** `app/src/main/java/com/example/audio/dsp/KeyDetector.kt`

```kotlin
package com.example.audio.dsp

import kotlin.math.*

enum class MusicalKey(val rootNote: String) {
    C("C"), Csharp("C#"), D("D"), Dsharp("D#"), E("E"), F("F"),
    Fsharp("F#"), G("G"), Gsharp("G#"), A("A"), Asharp("A#"), B("B")
}

enum class KeyMode {
    MAJOR, MINOR
}

data class DetectedKeyInfo(
    val key: MusicalKey,
    val mode: KeyMode,
    val confidence: Float // 0–100
)

/**
 * Key detection using Krumhansl-Schmuckler algorithm.
 * Compares chroma vector to major/minor pitch profiles.
 */
class KeyDetector {
    // Krumhansl major profile (relative strength of each pitch class in major keys)
    private val majorProfile = floatArrayOf(
        0.1524f, 0.0429f, 0.1225f, 0.0408f, 0.0931f, 0.1049f, 0.0548f, 0.1957f,
        0.0519f, 0.1309f, 0.0500f, 0.0934f
    )
    
    // Krumhansl minor profile
    private val minorProfile = floatArrayOf(
        0.1411f, 0.0500f, 0.1113f, 0.0927f, 0.0760f, 0.0982f, 0.0789f, 0.1670f,
        0.0725f, 0.0949f, 0.1490f, 0.0821f
    )
    
    /**
     * Detects musical key from chroma vector.
     * @param chromaVector 12-element pitch class distribution
     * @return (key, mode, confidence 0–100)
     */
    fun detectKey(chromaVector: FloatArray): DetectedKeyInfo {
        require(chromaVector.size == 12) { "Chroma vector must have 12 elements" }
        
        var bestScore = -Float.MAX_VALUE
        var bestKeyIdx = 0
        var isMajor = true
        
        // Test all 12 keys × 2 modes = 24 hypothesis
        for (mode in listOf(KeyMode.MAJOR, KeyMode.MINOR)) {
            val profile = if (mode == KeyMode.MAJOR) majorProfile else minorProfile
            
            for (keyIdx in 0..11) {
                // Rotate profile to match potential key root
                val rotatedProfile = FloatArray(12)
                for (i in 0..11) {
                    rotatedProfile[i] = profile[(i + keyIdx) % 12]
                }
                
                // Compute correlation (cosine similarity)
                val score = cosineSimilarity(chromaVector, rotatedProfile)
                
                if (score > bestScore) {
                    bestScore = score
                    bestKeyIdx = keyIdx
                    isMajor = (mode == KeyMode.MAJOR)
                }
            }
        }
        
        val confidence = ((bestScore + 1f) / 2f * 100f).coerceIn(0f, 100f)
        return DetectedKeyInfo(
            key = MusicalKey.values()[bestKeyIdx],
            mode = if (isMajor) KeyMode.MAJOR else KeyMode.MINOR,
            confidence = confidence
        )
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        return if (normA > 0 && normB > 0) {
            dotProduct / (sqrt(normA) * sqrt(normB))
        } else 0f
    }
}
```

---

### 1.4 BPM Detection (Spectral Flux + Autocorrelation)
**Location:** `app/src/main/java/com/example/audio/dsp/BpmDetector.kt`

```kotlin
package com.example.audio.dsp

import kotlin.math.*

data class BpmEstimate(
    val bpm: Float,
    val confidence: Float  // 0–100
)

/**
 * BPM detection using spectral flux onset detection + autocorrelation.
 * Range: 40–300 BPM, target accuracy ±1 BPM
 */
class BpmDetector(private val sampleRateHz: Int = 44100) {
    private val onsetEnergy = mutableListOf<Float>()
    private val fftAnalyzer = FFTAnalyzer(2048)
    private val chromaAnalyzer = ChromaAnalyzer(sampleRateHz)
    
    private var lastSpectrum = FloatArray(1024)
    
    /**
     * Processes audio frame and accumulates onset energy.
     * Call this repeatedly as audio arrives.
     */
    fun processFrame(pcmSamples: FloatArray) {
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(pcmSamples)
        
        // Compute spectral flux (sum of positive changes)
        var flux = 0f
        for (i in spectrum.indices) {
            val delta = spectrum[i] - lastSpectrum.getOrElse(i) { 0f }
            if (delta > 0) flux += delta
        }
        
        onsetEnergy.add(flux)
        lastSpectrum = spectrum
    }
    
    /**
     * Estimates BPM from accumulated onset energy.
     * Must call processFrame() at least 2–3 seconds of audio first.
     */
    fun estimateBpm(): BpmEstimate {
        if (onsetEnergy.size < 100) {
            return BpmEstimate(120f, 0f)  // Default fallback
        }
        
        // Compute autocorrelation at different lags (tempo hypotheses)
        var bestBpm = 120f
        var bestScore = 0f
        
        for (lagMs in 200..1500 step 10) {  // 40–300 BPM
            val lagFrames = (lagMs * sampleRateHz) / (1000 * 2048)
            val correlation = autoCorrelate(onsetEnergy, lagFrames.toInt())
            
            if (correlation > bestScore) {
                bestScore = correlation
                bestBpm = (60000f / lagMs)
            }
        }
        
        val confidence = (bestScore.coerceIn(0f, 1f) * 100f)
        return BpmEstimate(bestBpm, confidence)
    }
    
    private fun autoCorrelate(signal: List<Float>, lag: Int): Float {
        if (lag >= signal.size) return 0f
        
        var sum = 0f
        var count = 0
        
        for (i in 0 until signal.size - lag) {
            sum += signal[i] * signal[i + lag]
            count++
        }
        
        return if (count > 0) sum / count else 0f
    }
    
    fun reset() {
        onsetEnergy.clear()
        lastSpectrum = FloatArray(1024)
    }
}
```

---

## Phase 2: Advanced Chord Recognition

### 2.1 Chord Template Matching (PCP-based)
**Location:** `app/src/main/java/com/example/audio/ChordTemplateEngine.kt`
**Enhance existing:** `AudioClassifier.kt`

```kotlin
package com.example.audio

import kotlin.math.*

data class ChordHypothesis(
    val chordName: String,
    val rootNote: String,
    val chordType: String,  // "Major", "Minor", "7th", "Sus2", etc.
    val confidence: Float,
    val detectedNotes: List<String>
)

/**
 * Chord recognition via template matching on chroma vectors.
 * Uses pre-defined chord templates (pitch class sets) and scores against input chroma.
 */
class ChordTemplateEngine {
    // Chord templates (which pitch classes are "active")
    private val chordTemplates = mapOf(
        "Major" to booleanArrayOf(true, false, false, false, true, false, false, true, false, false, false, false),      // C,E,G
        "Minor" to booleanArrayOf(true, false, false, true, false, false, false, true, false, false, false, false),      // C,Eb,G
        "Dim" to booleanArrayOf(true, false, false, true, false, false, false, false, true, false, false, false),        // C,Eb,Gb
        "Aug" to booleanArrayOf(true, false, false, false, true, false, false, false, true, false, false, false),        // C,E,G#
        "Major7" to booleanArrayOf(true, false, false, false, true, false, false, true, false, false, false, true),      // C,E,G,B
        "Minor7" to booleanArrayOf(true, false, false, true, false, false, false, true, false, false, true, false),      // C,Eb,G,Bb
        "7" to booleanArrayOf(true, false, false, false, true, false, false, true, false, false, true, false),           // C,E,G,Bb
        "Sus2" to booleanArrayOf(true, false, true, false, false, false, false, true, false, false, false, false),       // C,D,G
        "Sus4" to booleanArrayOf(true, false, false, false, false, true, false, true, false, false, false, false),       // C,F,G
    )
    
    /**
     * Matches chroma vector to chord templates.
     * Returns top 3 chord candidates with confidence scores.
     */
    fun matchChords(chromaVector: FloatArray, topN: Int = 3): List<ChordHypothesis> {
        require(chromaVector.size == 12)
        
        val candidates = mutableListOf<Pair<String, Float>>()
        
        // Test all roots × all chord types = 12 × 9 = 108 hypotheses
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        
        for (rootIdx in 0..11) {
            for ((chordType, template) in chordTemplates) {
                val rotatedTemplate = FloatArray(12)
                for (i in 0..11) {
                    rotatedTemplate[i] = if (template[(i + rootIdx) % 12]) 1f else 0.1f  // Light penalize for missing notes
                }
                
                val score = cosineSimilarity(chromaVector, rotatedTemplate)
                candidates.add(Pair("${noteNames[rootIdx]}$chordType", score))
            }
        }
        
        val sorted = candidates.sortedByDescending { it.second }
        
        return sorted.take(topN).mapIndexed { idx, (name, score) ->
            val rootNote = name.takeWhile { it.isLetter() || it == '#' }
            val chordType = name.substring(rootNote.length)
            val confidence = ((score + 1f) / 2f * 100f).coerceIn(0f, 100f)
            
            ChordHypothesis(
                chordName = name,
                rootNote = rootNote,
                chordType = chordType,
                confidence = confidence,
                detectedNotes = extractNotesFromChroma(chromaVector)
            )
        }
    }
    
    private fun extractNotesFromChroma(chroma: FloatArray): List<String> {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return chroma.mapIndexed { idx, energy ->
            if (energy > 0.2f) noteNames[idx] else null
        }.filterNotNull()
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA > 0 && normB > 0) dot / (sqrt(normA) * sqrt(normB)) else 0f
    }
}
```

---

## Phase 3: HPSS & Stem Separation Setup

### 3.1 Harmonic/Percussive Source Separation (HPSS)
**Location:** `app/src/main/java/com/example/audio/dsp/HPSSProcessor.kt`

```kotlin
package com.example.audio.dsp

/**
 * Harmonic/Percussive Source Separation via spectral processing.
 * Separates melodic (stable harmonics) from percussive (transient) components.
 */
class HPSSProcessor(
    private val sampleRateHz: Int = 44100,
    private val fftSize: Int = 2048,
    private val hopSize: Int = 512
) {
    private val fftAnalyzer = FFTAnalyzer(fftSize)
    
    data class StemComponents(
        val harmonic: FloatArray,    // Melodic content
        val percussive: FloatArray   // Drum/transient content
    )
    
    /**
     * Applies median filtering in time/frequency to separate sources.
     * @param audioFrame Short-time FFT frame
     * @param harmonicMask Computed harmonic binary mask (0–1)
     * @return Separated harmonic and percussive audio
     */
    fun separateFrame(
        audioFrame: FloatArray,
        harmonicMask: FloatArray = FloatArray(fftSize / 2) { 1f }
    ): StemComponents {
        val spectrum = fftAnalyzer.computeMagnitudeSpectrum(audioFrame)
        
        // Simple mask application (advanced: use median filtering)
        val harmonicSpec = FloatArray(spectrum.size) { i ->
            spectrum[i] * harmonicMask[i]
        }
        
        val percussiveSpec = FloatArray(spectrum.size) { i ->
            spectrum[i] * (1f - harmonicMask[i])
        }
        
        // Inverse FFT would convert back to time-domain (requires FFT library)
        // Placeholder: return magnitude (implement full iFFT for production)
        return StemComponents(
            harmonic = harmonicSpec,
            percussive = percussiveSpec
        )
    }
    
    /**
     * Median filtering mask for harmonic detection.
     * Harmonics are stable across time; percussive spikes are transient.
     */
    fun computeHarmonicMask(
        currentSpectrum: FloatArray,
        prevSpectrum: FloatArray,
        nextSpectrum: FloatArray
    ): FloatArray {
        val mask = FloatArray(currentSpectrum.size)
        
        for (i in mask.indices) {
            val prev = prevSpectrum.getOrElse(i) { 0f }
            val curr = currentSpectrum[i]
            val next = nextSpectrum.getOrElse(i) { 0f }
            
            // Median: stable over time → harmonic
            val median = listOf(prev, curr, next).sorted()[1]
            mask[i] = if (curr > median * 0.8f) 1f else 0f
        }
        
        return mask
    }
}
```

---

## Phase 4: ML Integration (TensorFlow Lite)

### 4.1 Add TensorFlow Lite Dependency
**File:** `app/build.gradle.kts`

```gradle
dependencies {
    // TensorFlow Lite for on-device inference
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-nnapi:2.14.0")
    
    // Optional: TensorFlow Lite Audio Support
    // implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}
```

### 4.2 Stem Separation with TFLite Model
**Location:** `app/src/main/java/com/example/audio/TFLiteStemSeparator.kt`

```kotlin
package com.example.audio

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.audio.TensorAudio
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite-based stem separation (vocals, drums, bass, other).
 * Uses pre-trained model (e.g., Demucs quantized or Spleeter TFLite).
 * 
 * Model source: Download from TensorFlow Hub or export Demucs/Spleeter to TFLite.
 */
class TFLiteStemSeparator(
    private val context: Context,
    modelPath: String = "models/stem_separator.tflite"
) {
    private var interpreter: Interpreter? = null
    
    init {
        try {
            val model = loadModelFile(modelPath)
            interpreter = Interpreter(model)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Separates audio into 4 stems: vocals, drums, bass, other.
     * @param pcmAudio Input mono/stereo float PCM
     * @return 4 float arrays (vocals, drums, bass, other)
     */
    fun separateStems(pcmAudio: FloatArray): Stems {
        val interpreter = interpreter ?: return Stems(pcmAudio, FloatArray(0), FloatArray(0), FloatArray(0))
        
        // Reshape input to model's expected shape (e.g., [1, samples, 2] for stereo)
        val inputShape = intArrayOf(1, pcmAudio.size, 1)
        val input = Array(1) { Array(pcmAudio.size) { FloatArray(1) } }
        for (i in pcmAudio.indices) {
            input[0][i][0] = pcmAudio[i]
        }
        
        // Output shape depends on model (typically [1, samples, 4] for 4 stems)
        val output = Array(1) { Array(pcmAudio.size) { FloatArray(4) } }
        
        try {
            interpreter.run(input, output)
        } catch (e: Exception) {
            e.printStackTrace()
            return Stems(pcmAudio, FloatArray(0), FloatArray(0), FloatArray(0))
        }
        
        // Extract individual stems
        val vocals = FloatArray(pcmAudio.size) { i -> output[0][i][0] }
        val drums = FloatArray(pcmAudio.size) { i -> output[0][i][1] }
        val bass = FloatArray(pcmAudio.size) { i -> output[0][i][2] }
        val other = FloatArray(pcmAudio.size) { i -> output[0][i][3] }
        
        return Stems(vocals, drums, bass, other)
    }
    
    data class Stems(
        val vocals: FloatArray,
        val drums: FloatArray,
        val bass: FloatArray,
        val other: FloatArray
    )
    
    private fun loadModelFile(path: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(path)
        val fileInputStream = assetFileDescriptor.createInputStream()
        val fileChannel = (fileInputStream).channel
        
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
```

---

## Phase 5: Integration Points

### 5.1 Update WorkstationViewModel
**File:** `app/src/main/java/com/example/viewmodel/WorkstationViewModel.kt`

Add real analysis to the chord detector module:

```kotlin
private val chordTemplateEngine = ChordTemplateEngine()
private val chromaAnalyzer = ChromaAnalyzer(sampleRate = 44100)

fun analyzeForChords(pcmFrame: FloatArray) {
    viewModelScope.launch(Dispatchers.Default) {
        try {
            val chroma = chromaAnalyzer.computeChroma(fftAnalyzer.computeMagnitudeSpectrum(pcmFrame))
            val chordCandidates = chordTemplateEngine.matchChords(chroma, topN = 3)
            
            _detectedChords.value = chordCandidates
        } catch (e: Exception) {
            Log.e("WorkstationVM", "Chord analysis failed", e)
        }
    }
}
```

### 5.2 Update UI (StemSeparationScreen.kt)
Integrate TFLite model:

```kotlin
private val stemSeparator = remember { TFLiteStemSeparator(context) }

Button(onClick = {
    scope.launch {
        val stems = stemSeparator.separateStems(audioEngine.getActivePcmSamples() ?: return@launch)
        // Update UI with separated stems
        updateStemDisplay(stems)
    }
}) {
    Text("Separate Stems")
}
```

---

## Phase 6: Testing & Validation

### 6.1 Unit Tests
**Location:** `app/src/test/java/com/example/audio/dsp/`

```kotlin
// FFTAnalyzerTest.kt
class FFTAnalyzerTest {
    private lateinit var analyzer: FFTAnalyzer
    
    @Before
    fun setUp() {
        analyzer = FFTAnalyzer(2048)
    }
    
    @Test
    fun testSineWaveFft() {
        // Generate 440 Hz sine wave (A4)
        val sampleRate = 44100
        val freq = 440f
        val samples = FloatArray(2048) { i ->
            sin(2f * PI * freq * i / sampleRate).toFloat()
        }
        
        val spectrum = analyzer.computeMagnitudeSpectrum(samples)
        
        // Peak should be near 440 Hz bin
        val peakBin = spectrum.indices.maxByOrNull { spectrum[it] } ?: -1
        val peakFreq = peakBin * sampleRate / 2048
        
        assertTrue(peakFreq in 420f..460f)
    }
}

// ChordTemplateEngineTest.kt
class ChordTemplateEngineTest {
    private lateinit var engine: ChordTemplateEngine
    
    @Before
    fun setUp() {
        engine = ChordTemplateEngine()
    }
    
    @Test
    fun testCMajorChordDetection() {
        // Ideal C Major chroma (C, E, G active)
        val chromaVector = floatArrayOf(
            1f,   // C
            0f,   // C#
            0f,   // D
            0f,   // D#
            1f,   // E
            0f,   // F
            0f,   // F#
            1f,   // G
            0f,   // G#
            0f,   // A
            0f,   // A#
            0f    // B
        )
        chromaVector.indices.forEach { chromaVector[it] /= 3f }  // Normalize
        
        val results = engine.matchChords(chromaVector, topN = 1)
        
        assertEquals("CMajor", results[0].chordName)
        assertTrue(results[0].confidence > 80f)
    }
}
```

---

## Phase 7: Build & Deploy

### 7.1 Prepare Assets
```
app/src/main/assets/
├── models/
│   ├── stem_separator.tflite       # Download from TensorFlow Hub
│   └── chord_detector.tflite       # Optional: end-to-end model
└── audio_samples/
    └── test_chord_c_major.wav      # Test fixtures
```

### 7.2 Gradle Build
```bash
./gradlew build
./gradlew installDebug
```

### 7.3 GitHub Actions Workflow
Already configured in `.github/workflows/android.yml` — no changes needed.

---

## Implementation Checklist

- [ ] **Phase 1 — DSP Algorithms**
  - [ ] FFTAnalyzer.kt
  - [ ] ChromaAnalyzer.kt
  - [ ] KeyDetector.kt
  - [ ] BpmDetector.kt

- [ ] **Phase 2 — Chord Recognition**
  - [ ] ChordTemplateEngine.kt
  - [ ] Integrate with AudioClassifier.kt
  - [ ] Update WorkstationViewModel

- [ ] **Phase 3 — HPSS**
  - [ ] HPSSProcessor.kt
  - [ ] Connect to RealStemSeparationEngine.kt

- [ ] **Phase 4 — TensorFlow Lite**
  - [ ] Add dependency in build.gradle.kts
  - [ ] TFLiteStemSeparator.kt
  - [ ] Download model files

- [ ] **Phase 5 — Integration**
  - [ ] Wire analysis engines into HZAudioEngine
  - [ ] Update UI screens
  - [ ] Test end-to-end

- [ ] **Phase 6 — Testing**
  - [ ] Unit tests for DSP
  - [ ] Integration tests for chord detection
  - [ ] Manual QA on real device

---

## Performance Targets

| Feature | Target | Metric |
|---------|--------|--------|
| FFT Latency | < 20ms | Real-time playback OK |
| Chord Detection | < 50ms | Perceptual responsiveness |
| BPM Accuracy | ±1 BPM | Per spec requirement |
| Stem Separation | < 500ms | Per frame (GPU accelerated) |
| Memory | < 200MB | On-device processing |

---

## References

- **FFT:** Cooley-Tukey Algorithm, *J. ACM*, 1965
- **Chroma:** Müller & Ewert, *MIR Overview*, 2011
- **Key Detection:** Krumhansl & Schmuckler, *Music Perception*, 1990
- **BPM:** Ellis, *Spectral Peak Picking*, 2007
- **HPSS:** Fitzgerald, *ISMIR*, 2010
- **TensorFlow Lite:** https://tensorflow.org/lite

---

## Next Steps

1. Start with **Phase 1** (DSP foundational algorithms).
2. Test each DSP component with unit tests using synthetic signals.
3. Integrate with existing `AudioClassifier.kt` and `WorkstationViewModel`.
4. Move to **Phase 4** (TensorFlow Lite) once DSP is solid.
5. Profile on real device; optimize hot paths (FFT, chroma compute).

