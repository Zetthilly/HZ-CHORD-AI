# TensorFlow Lite Integration Guide

## Overview

This guide covers integrating TensorFlow Lite for on-device chord recognition inference in HZ-CHORD-AI.

## Model Requirements

### Input Specification
- **Shape:** `[1, 12]` (batch size 1, chroma vector size 12)
- **Type:** `float32`
- **Range:** [0.0, 1.0] (normalized pitch class distribution)
- **Source:** Output from `ChromaAnalyzer.computeChroma()`

### Output Specification
- **Shape:** `[1, 108]` (batch size 1, 12 roots × 9 chord types)
- **Type:** `float32`
- **Range:** [0.0, 1.0] (softmax probability distribution)
- **Interpretation:** `output[i]` = probability of chord class `i`

### Class Mapping
```
Class Index = (root_idx % 12) + (chord_type_idx * 12)

Roots: 0-11 (C, C#, D, D#, E, F, F#, G, G#, A, A#, B)
Chord Types:
  0: Major
  1: Minor
  2: Dim
  3: Aug
  4: Major7
  5: Minor7
  6: 7
  7: Sus2
  8: Sus4
```

## Model Sources

### Option 1: Train Your Own (Recommended)
See `docs/ML_CHORD_RECOGNIZER_TRAINING.md` for complete workflow.

```bash
# 1. Prepare annotated chord dataset
# 2. Extract chroma features with librosa
# 3. Train Keras model
python train_chord_model.py

# 4. Export to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()
with open('chord_recognizer.tflite', 'wb') as f:
    f.write(tflite_model)
```

### Option 2: Download Pre-trained Model

**TensorFlow Hub:**
- Search: "music chord" or "chord recognition"
- https://tfhub.dev

**HuggingFace:**
- Models: https://huggingface.co/models?task=audio-classification
- Datasets: MAESTRO, GuitarSet, Chordify

**Research Papers with TFLite Models:**
- Prabhavalkar et al. (2018) - Music Information Retrieval
- Müller et al. (2014) - Chroma-Based Source Separation

## Deployment

### Step 1: Add Model to Assets

```bash
mkdir -p app/src/main/assets/models
cp chord_recognizer.tflite app/src/main/assets/models/
```

### Step 2: Update build.gradle.kts

Add TensorFlow Lite dependencies:

```kotlin
dependencies {
    // TensorFlow Lite core
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    
    // GPU acceleration (optional)
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    
    // NNAPI acceleration (optional)
    implementation("org.tensorflow:tensorflow-lite-nnapi:2.14.0")
    
    // GPU delegate v2 (if using newer GPU features)
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
}
```

### Step 3: Initialize Model in Code

```kotlin
// In your Activity or ViewModel
val tfLiteModel = TFLiteChordModel(context, "models/chord_recognizer.tflite")

if (tfLiteModel.isReady()) {
    Log.i("ChordApp", "TFLite model loaded: ${tfLiteModel.getInputShape()} -> ${tfLiteModel.getOutputShape()}")
} else {
    Log.w("ChordApp", "TFLite model failed to load, using fallback")
}
```

### Step 4: Run Inference

```kotlin
// In AudioAnalysisPipeline
val chromaVector = chromaAnalyzer.computeChroma(spectrum)  // FloatArray[12]
val predictions = tfLiteModel.inferChord(chromaVector)      // FloatArray[108]

if (predictions != null) {
    val topChord = predictions.mapIndexed { idx, prob ->
        Pair(chordIndexToName(idx), prob)
    }.maxByOrNull { it.second }
    
    Log.i("Chord", "Detected: ${topChord?.first} (${topChord?.second})")
}
```

## Performance Optimization

### GPU Acceleration

```kotlin
val options = Interpreter.Options()
options.setUseGpuDelegate(true)  // Use GPU for faster inference
interpreter = Interpreter(model, options)
```

**Benefits:**
- 2-5x faster on devices with GPU
- Useful for real-time processing
- May increase power consumption

### NNAPI Acceleration

```kotlin
val options = Interpreter.Options()
options.setUseNNAPI(true)  // Use Android NNAPI
interpreter = Interpreter(model, options)
```

**Benefits:**
- Works on most Android 8.1+ devices
- Optimized for each device's hardware
- Generally better battery life than GPU

### Quantization (Model Optimization)

Quantized models are smaller and faster:

```python
# During training export (Keras → TFLite)
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]  # Quantize to int8
tflite_quantized = converter.convert()
```

**Trade-off:** Slight accuracy loss (~2-5%) for ~4x smaller model size.

## Monitoring & Debugging

### Check Model Shape

```kotlin
val inputShape = tfLiteModel.getInputShape()
val outputShape = tfLiteModel.getOutputShape()
Log.i("TFLite", "Input: ${inputShape?.contentToString()}")
Log.i("TFLite", "Output: ${outputShape?.contentToString()}")
```

### Benchmark Inference Speed

```kotlin
val startTime = System.nanoTime()
val predictions = tfLiteModel.inferChord(chromaVector)
val latencyMs = (System.nanoTime() - startTime) / 1_000_000.0
Log.i("Performance", "Inference latency: ${latencyMs}ms")
```

**Target:** < 50ms on modern Android devices

### Verify Predictions

```kotlin
val predictions = tfLiteModel.inferChord(chromaVector)
val sum = predictions.sum()
Log.d("Sanity", "Probability sum: $sum (should be ~1.0)")

val topProb = predictions.maxOrNull() ?: 0f
Log.d("Sanity", "Top prediction confidence: ${topProb * 100}% (should be 20-95%)")
```

## Troubleshooting

### Model File Not Found
```
Exception: Cannot read file models/chord_recognizer.tflite
```

**Solution:**
1. Verify file exists: `app/src/main/assets/models/chord_recognizer.tflite`
2. Check `build.gradle.kts` includes assets
3. Rebuild app: `./gradlew clean assembleDebug`

### Input/Output Shape Mismatch
```
Exception: Input size (14) does not match expected size (12)
```

**Solution:**
1. Verify chroma vector is 12 elements
2. Check model was trained with 12-input
3. Reshape input if needed:
   ```kotlin
   val paddedChroma = FloatArray(12)
   chromaVector.copyInto(paddedChroma)
   ```

### Low Prediction Confidence
```
Predictions all < 30%
```

**Causes:**
1. Model not trained on your music style
2. Chroma extraction is poor (use harmonic stem from HPSS)
3. Audio is too noisy

**Solutions:**
1. Re-train model on target domain
2. Apply HPSS before chord detection
3. Use temporal smoothing (average 3-5 frames)

## Best Practices

1. **Always check model readiness:**
   ```kotlin
   if (tfLiteModel.isReady()) {
       // Safe to call inference
   }
   ```

2. **Use harmonic stem for input:**
   ```kotlin
   val harmonicStems = hpss.separateFrame(audio)
   val chromaVector = chroma.computeChroma(harmonicStems.harmonic)
   ```

3. **Implement temporal smoothing:**
   ```kotlin
   // Average predictions over 3 frames
   predictions = listOf(
       tfLiteModel.inferChord(chroma1),
       tfLiteModel.inferChord(chroma2),
       tfLiteModel.inferChord(chroma3)
   ).fold(FloatArray(108)) { acc, pred ->
       FloatArray(108) { i -> acc[i] + pred[i] }
   }.map { it / 3f }.toFloatArray()
   ```

4. **Clean up resources:**
   ```kotlin
   override fun onDestroy() {
       tfLiteModel.close()
       super.onDestroy()
   }
   ```

## Future Enhancements

1. **Ensemble Models:** Combine multiple chord recognizers
2. **Streaming Inference:** Process audio in real-time without buffering
3. **Transfer Learning:** Fine-tune model on user's music library
4. **Dynamic Quantization:** Adaptive precision based on audio quality
5. **On-Device Training:** Update model from user feedback
