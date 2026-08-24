# HZ CHORD AI

**Hear the Notes. Understand the Music. Powered by AI.**

Offline-first Android music analysis workstation designed for musicians, worship teams, producers, students, guitarists and keyboard players.

## UI

The active Compose UI follows the supplied reference screens: premium near-black/navy panels, gold HZ branding, cyan/blue/purple accents, module launcher cards, chord detector, keyboard/fretboard, arpeggio, BPM/key, stem separation, transcription, tuner, library and settings.

## Real implementation policy

This repository intentionally avoids demo data and filename-based musical guesses.

- Real Android audio URI import with MediaCodec decoding.
- Real Media3 playback with source validation.
- Offline spectral-flux BPM estimation + tempo autocorrelation.
- Offline FFT/chroma + Krumhansl major/minor key estimation.
- On-device harmonic analysis pipeline with CQT, HPSS, multi-pitch tracking and chord template scoring.
- Real microphone PCM tuner path.
- On-device HPSS/filter-based stem separation path.
- Room persistence for real imported sessions.
- No Gemini/API key/cloud analysis in this build.

## Phone-only build

You do not need a PC or paid software to build the debug APK.

1. Create a GitHub repository.
2. Upload this repository's contents to the root of that repository.
3. Open **Actions** → **Android CI**.
4. Press **Run workflow**.
5. Wait for the workflow to finish.
6. Open the successful run → **Artifacts** → `hz-chord-ai-debug-apk`.
7. Download `app-debug.apk` to the phone and install it.

The workflow installs JDK 17, Android SDK 36, NDK 27.0.12077973, CMake 3.22.1 and Gradle 8.10.2 automatically.

## Android Studio

Open the project root in Android Studio with JDK 17. Gradle 8.10.2 and Android Gradle Plugin 8.8.2 are configured.
