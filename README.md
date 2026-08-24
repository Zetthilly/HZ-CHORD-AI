# HZ CHORD AI

**Hear the Notes. Understand the Music. Powered by AI.**

Offline-first Android music analysis workstation designed for musicians, worship teams, producers, students, guitarists and keyboard players.

## Overview

HZ CHORD AI is a mobile-first music analysis platform that brings professional audio DSP to Android without requiring cloud services. The app analyzes live or imported audio to detect chords, tempo, and key in real-time.

## Current Status

**Alpha Development** — Core architecture in place. DSP algorithms and ML integration planned in phases.

### What's Working
- ✅ Android Compose UI with Hilt dependency injection
- ✅ Media3 audio playback engine with source validation
- ✅ Real Android audio URI import with MediaCodec decoding
- ✅ Gradle/NDK build infrastructure for C++ DSP layers
- ✅ Phone-only GitHub Actions build (no PC required)

### What's Planned (See [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md))
- 🔲 **Phase 1:** FFT, Chroma vectors, Key detection (Krumhansl profiles), BPM estimation
- 🔲 **Phase 2:** Chord template matching and confidence scoring
- 🔲 **Phase 3:** Harmonic/Percussive source separation (HPSS)
- 🔲 **Phase 4:** TensorFlow Lite stem separation (vocals, drums, bass, other)
- 🔲 **Phase 5:** Microphone tuner and real-time polyphonic analysis
- 🔲 **Phase 6:** Test suite and device profiling

## Technical Stack

| Component | Technology |
|-----------|-----------|
| UI | Jetpack Compose + Material3 |
| Playback | Media3/ExoPlayer |
| Audio I/O | Oboe (low-latency Android audio) |
| DSP | Kotlin/C++ (FFT, spectral analysis) |
| ML | TensorFlow Lite (quantized models) |
| Storage | Room (session persistence) |
| Build | Gradle 8.10.2, AGP 8.8.2, Android SDK 36, NDK 27.0.12077973 |

## UI Design

Premium aesthetic with near-black/navy panels, gold HZ branding, and cyan/blue/purple accents. The interface includes:

- **Module Launcher** — Quick access to Chord Detector, Tuner, Key Analyzer, BPM Counter, Stem Separator
- **Chord Detector** — Real-time chord matching with confidence scores
- **Keyboard/Fretboard** — Visual feedback for detected notes
- **Waveform & Spectrum** — Live FFT visualization
- **Session Manager** — Record and replay analysis sessions with Room persistence

## Building & Running

### Phone-Only Build (No PC Required)

Use GitHub Actions to compile the APK directly from your phone:

1. Create a GitHub repository.
2. Upload this repository's contents to the root of your repository.
3. Open **Actions** → **Android CI**.
4. Press **Run workflow**.
5. Wait for the workflow to finish.
6. Open the successful run → **Artifacts** → `hz-chord-ai-debug-apk`.
7. Download `app-debug.apk` to the phone and install it.

The workflow automatically installs JDK 17, Android SDK 36, NDK 27.0.12077973, CMake 3.22.1, and Gradle 8.10.2.

### Local Build (Android Studio)

Open the project root in Android Studio with JDK 17. Gradle and Android Gradle Plugin are pre-configured:

```bash
# Build debug APK
./gradlew assembleDebug

# Build and install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

**Requirements:**
- Android Studio (latest)
- JDK 17
- Android SDK 36+

## Project Structure

```
HZ-CHORD-AI/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/example/
│   │   │   ├── audio/          # Audio engine, playback, recording
│   │   │   ├── dsp/            # DSP algorithms (planned phases)
│   │   │   ├── ui/             # Compose screens and components
│   │   │   └── viewmodel/      # ViewModels for UI state
│   │   ├── cpp/                # C++ DSP implementations (CMake)
│   │   └── res/                # Resources (strings, themes, etc.)
│   └── build.gradle.kts        # App-level build config
├── build.gradle.kts            # Root build config
├── settings.gradle.kts         # Gradle settings
├── IMPLEMENTATION_GUIDE.md     # Detailed phase-by-phase implementation plan
└── README.md                   # This file
```

## Implementation Roadmap

See [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) for a detailed breakdown of each phase. Key algorithms include:

### Phase 1: Core DSP (Foundation)
- Cooley-Tukey FFT with Hann windowing
- Pitch class (chroma) vector extraction
- Krumhansl-Schmuckler key detection (major/minor)
- Spectral flux + autocorrelation BPM estimation (40–300 BPM)

### Phase 2: Chord Recognition
- Template-based chord matching on chroma vectors
- Top-3 chord hypothesis scoring
- Confidence normalization (0–100%)

### Phase 3: Harmonic/Percussive Separation
- Median filtering for harmonic mask estimation
- Spectral separation into melodic and percussive components

### Phase 4: ML Integration (TensorFlow Lite)
- Stem separation model (vocals, drums, bass, other)
- GPU acceleration via TFLite NNAPI backend

### Phase 5: Real-Time Features
- Microphone PCM tuner
- Polyphonic note tracking
- Session recording and playback

### Phase 6: Testing & Optimization
- Unit tests for DSP algorithms
- Integration tests for end-to-end analysis
- Device profiling and performance tuning

## Performance Targets

| Feature | Goal | Metric |
|---------|------|--------|
| FFT Latency | < 20 ms | Real-time playback compatibility |
| Chord Detection | < 50 ms | Perceptual responsiveness |
| BPM Accuracy | ± 1 BPM | Per specification |
| Stem Separation | < 500 ms | Per audio frame (GPU accelerated) |
| Memory Usage | < 200 MB | On-device DSP processing |

## Dependencies

Key libraries (see `app/build.gradle.kts` for full list):

- **androidx.compose** — Reactive UI framework
- **androidx.room** — Local database for session persistence
- **androidx.lifecycle** — ViewModel & LiveData
- **androidx.media3** — ExoPlayer and playback control
- **com.google.dagger.hilt** — Dependency injection
- **io.oboe** — Low-latency audio I/O
- **org.tensorflow:tensorflow-lite** — Inference engine (Phase 4)

## Contributing

This is an active development project. To contribute:

1. Fork and clone the repository.
2. Create a feature branch (`git checkout -b feature/your-feature`).
3. Follow the phase checklist in [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md).
4. Write unit tests for DSP changes (see test examples in guide).
5. Open a pull request with a clear description.

## Troubleshooting

### Build Fails with "NDK not found"
Ensure Android Studio has NDK 27.0.12077973 installed:
- **Android Studio Settings** → **SDK Manager** → **SDK Tools** → Install **NDK (Side by side)** 27.0.12077973

### Audio Permission Denied
The app requires:
- `RECORD_AUDIO` (for microphone input)
- `READ_EXTERNAL_STORAGE` or `READ_MEDIA_AUDIO` (for file import)

Grant these in **Settings** → **Apps** → **HZ CHORD AI** → **Permissions**.

### Slow Chord Detection
Profile with Android Profiler:
1. Run the app with **Profile** (not **Debug**).
2. Open **Android Profiler** → **CPU** tab.
3. Identify hot paths in `FFTAnalyzer` and `ChromaAnalyzer`.
4. Consider C++ implementation for FFT if Kotlin performance is insufficient.

## Real Implementation Policy

This repository intentionally avoids:
- ❌ Demo data or filename-based guesses
- ❌ Cloud APIs or API keys
- ❌ Placeholder implementations

Instead, we build:
- ✅ Real Android audio URI import with MediaCodec decoding
- ✅ Real Media3 playback with source validation
- ✅ Offline spectral-flux BPM estimation + tempo autocorrelation
- ✅ Offline FFT/chroma + Krumhansl major/minor key estimation
- ✅ On-device harmonic analysis pipeline (CQT, HPSS, chord scoring)
- ✅ Real microphone PCM tuner path
- ✅ On-device stem separation (HPSS + TFLite)
- ✅ Room persistence for real imported sessions

## References

- **FFT:** Cooley & Tukey, *An Algorithm for the Machine Calculation of Complex Fourier Series*, J. ACM, 1965
- **Chroma Vectors:** Müller & Ewert, *Improving Source Separation and Chord Recognition*, 2011
- **Key Detection:** Krumhansl & Schmuckler, *Key Distance Effects in Probe-Tone Ratings*, Music Perception, 1990
- **BPM Detection:** Ellis, *Beat Tracking by Dynamic Programming*, JNMR, 2007
- **HPSS:** Fitzgerald, *Harmonic/Percussive Source Separation Using Median Filtering*, ISMIR, 2010
- **TensorFlow Lite:** https://www.tensorflow.org/lite

## License

(Add your license here)

## Authors

- **Zetthilly** — Primary developer

---

**Questions or feedback?** Open an [issue](https://github.com/Zetthilly/HZ-CHORD-AI/issues) or start a [discussion](https://github.com/Zetthilly/HZ-CHORD-AI/discussions).
