# ML Chord Recognizer Training Guide

This document describes how to train and deploy a TensorFlow model for chord recognition.

## Architecture

### Input
- **Shape:** `(batch_size, 12)` — Chroma vector (pitch class distribution)
- **Optionally:** Temporal context (1-4 previous frames stacked)

### Output
- **Shape:** `(batch_size, 108)` — Softmax probabilities over chord classes
- **Classes:** 12 roots × 9 chord types (Major, Minor, Dim, Aug, Major7, Minor7, 7, Sus2, Sus4)

### Model (Keras)

```python
import tensorflow as tf
from tensorflow import keras

def create_chord_recognizer(input_size=12, hidden_units=[64, 32]):
    model = keras.Sequential([
        keras.layers.Input(shape=(input_size,)),
        keras.layers.Dense(hidden_units[0], activation='relu'),
        keras.layers.Dropout(0.3),
        keras.layers.Dense(hidden_units[1], activation='relu'),
        keras.layers.Dropout(0.3),
        keras.layers.Dense(108, activation='softmax')  # 12 roots × 9 types
    ])
    return model

model = create_chord_recognizer()
model.compile(
    optimizer='adam',
    loss='categorical_crossentropy',
    metrics=['accuracy']
)
model.summary()
```

## Training Data

### Dataset Requirements
1. **Audio Files:** WAV or MP3 clips (1-10 seconds each)
2. **Annotations:** Ground truth chord labels (format: "C Major", "G Minor", etc.)
3. **Size:** Minimum 1,000 clips; ideally 5,000+ for robust performance

### Data Pipeline

```python
def extract_chroma_features(audio_path, sr=44100, hop_length=2048):
    import librosa
    import numpy as np
    
    # Load audio
    y, sr = librosa.load(audio_path, sr=sr)
    
    # Compute constant-Q transform (CQT) for better pitch resolution
    cqt = np.abs(librosa.cqt(y, sr=sr, hop_length=hop_length))
    
    # Compute chroma vector (12-bin pitch class distribution)
    chroma = librosa.feature.chroma_cqt(C=cqt, sr=sr)
    
    # Average over time to get single chroma vector
    chroma_mean = np.mean(chroma, axis=1)  # Shape: (12,)
    
    return chroma_mean.astype(np.float32)

def load_dataset(audio_dir, annotation_file):
    import pandas as pd
    
    # Load annotations (CSV with columns: filename, chord_label)
    annotations = pd.read_csv(annotation_file)
    
    X = []  # Features
    y = []  # Labels
    
    chord_classes = {
        'C Major': 0, 'C# Major': 1, ..., 'B Sus4': 107
    }
    
    for idx, row in annotations.iterrows():
        audio_path = f"{audio_dir}/{row['filename']}"
        chord_label = row['chord_label']
        
        try:
            chroma = extract_chroma_features(audio_path)
            X.append(chroma)
            y.append(chord_classes[chord_label])
        except Exception as e:
            print(f"Skipped {audio_path}: {e}")
    
    return np.array(X), keras.utils.to_categorical(np.array(y), num_classes=108)

# Load data
X_train, y_train = load_dataset('data/audio', 'data/annotations_train.csv')
X_val, y_val = load_dataset('data/audio', 'data/annotations_val.csv')
```

## Training

```python
# Create and train model
model = create_chord_recognizer()

callback = keras.callbacks.EarlyStopping(
    monitor='val_loss',
    patience=5,
    restore_best_weights=True
)

history = model.fit(
    X_train, y_train,
    validation_data=(X_val, y_val),
    epochs=50,
    batch_size=32,
    callbacks=[callback],
    verbose=1
)

# Evaluate
test_loss, test_accuracy = model.evaluate(X_val, y_val)
print(f"Validation Accuracy: {test_accuracy * 100:.2f}%")
```

## Export to TensorFlow Lite

```python
# Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

tflite_model = converter.convert()

# Save
with open('models/chord_recognizer.tflite', 'wb') as f:
    f.write(tflite_model)

print("Model saved to: models/chord_recognizer.tflite")
```

## On-Device Integration (Android)

```kotlin
// In MLChordRecognizer.kt, replace loadTFLiteModel():

fun loadTFLiteModel(context: Context, modelPath: String = "models/chord_recognizer.tflite"): Boolean {
    return try {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val fileInputStream = assetFileDescriptor.createInputStream()
        val fileChannel = fileInputStream.channel
        
        val model = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
        
        tfLiteInterpreter = Interpreter(model)
        true
    } catch (e: Exception) {
        Log.e("MLChordRecognizer", "Failed to load model", e)
        false
    }
}
```

## Performance Metrics

**Target Performance:**
- Accuracy: > 85% on test set
- Latency: < 50ms per inference on Android
- Model size: < 200 KB

**Typical Results:**
- Clean, isolated chords: 95%+ accuracy
- Real music (polyphonic, noisy): 75-85% accuracy

## Future Improvements

1. **Temporal Smoothing:** Use LSTM/Transformer to consider previous frames
2. **Multi-Octave Awareness:** Stack chroma vectors at different frequencies
3. **Inversion Detection:** Separate root position from inversions
4. **Fine-tuning:** Domain adaptation for specific instruments (guitar, piano, etc.)
5. **Confidence Calibration:** Proper uncertainty quantification for decision-making
