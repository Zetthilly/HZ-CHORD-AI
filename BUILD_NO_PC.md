# HZ CHORD AI — phone-only GitHub build

This project is intentionally configured so the APK can be built by GitHub Actions without a PC and without paid software.

## What was removed from the original base

- Corrupt `gradle-wrapper.jar` was removed instead of shipping a broken binary.
- Filename-based BPM/key guessing was removed.
- Fake/demo database seeding was removed.
- Synthetic chord timelines and playback-position chord injection were removed.
- Fake default chord/tuner signals were removed.
- The cloud/Gemini analysis call was removed; analysis reports are local.
- The generated waveform fallback was removed from audio metadata extraction.

## Real processing path

1. Android Storage Access Framework selects a real audio URI.
2. `RealAudioDecoder` decodes PCM with `MediaCodec`.
3. BPM uses spectral-flux onset detection and tempo-lag autocorrelation.
4. Key uses FFT-derived chroma and Krumhansl major/minor profile correlation.
5. Harmonic analysis uses the existing on-device DSP pipeline: preprocessing, CQT/chroma, HPSS, multi-pitch detection, temporal tracking and 420-template chord evaluation.
6. Playback uses Android Media3 ExoPlayer.
7. Stem separation uses the existing on-device HPSS/filter pipeline when explicitly started.
8. Tuner uses microphone PCM and autocorrelation pitch detection.

## Build from a phone

1. Create a GitHub repository.
2. Upload the contents of this folder to the repository root.
3. Open **Actions**.
4. Select **Android CI** and run it with **Run workflow**, or push to `main`/`master`.
5. Wait for the green build.
6. Open the completed workflow run.
7. Download the artifact named **hz-chord-ai-debug-apk**.
8. Install the APK on Android after enabling installation from the source you used for the download.

No API key is required for the build.

## Android Studio

If Android Studio is later available, open the project root and use Gradle 8.10.2 with JDK 17. The project uses Android Gradle Plugin 8.8.2.

The project does not contain a fake or corrupted wrapper JAR. The GitHub workflow installs Gradle directly, which is safer for this phone-only workflow.
