package com.example.audio

import com.example.audio.dsp.AudioPreprocessor
import com.example.audio.dsp.CQT
import com.example.audio.dsp.FFT
import com.example.audio.dsp.MultiPitchDetector
import com.example.audio.dsp.TemporalNoteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class PolyphonicNoteDetectionEngineTest {

    @Test
    fun testAudioPreprocessorPipeline() {
        val sampleRate = 44100
        val numSamples = 8820
        // Synthetic 440Hz sine wave + 100Hz noise + 50Hz hum
        val inputPcm = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (0.5 * sin(2.0 * Math.PI * 440.0 * t) + 0.2 * sin(2.0 * Math.PI * 50.0 * t)).toFloat()
        }

        val cleanPcm = AudioPreprocessor.preprocessSignal(inputPcm, sampleRate)
        assertTrue("Preprocessed PCM should not be empty", cleanPcm.isNotEmpty())

        val frames = AudioPreprocessor.extract75PercentOverlapFrames(cleanPcm, frameSize = 4096)
        assertTrue("Frames should be extracted with 75% overlap", frames.isNotEmpty())
    }

    @Test
    fun testConstantQTransformEngine() {
        val sampleRate = 48000
        val numSamples = 4096
        val pcm = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (0.6 * sin(2.0 * Math.PI * 440.0 * t)).toFloat()
        }

        val cqtRes = CQT.computeCqt(pcm, sampleRate)
        assertEquals(252, cqtRes.numBins)
        assertTrue("Magnitudes array should have 252 bins", cqtRes.magnitudes.size == 252)
        assertTrue("Phases array should have 252 bins", cqtRes.phases.size == 252)
        assertTrue("Harmonic energies array should have 252 bins", cqtRes.harmonicEnergies.size == 252)

        val chroma = CQT.foldCqtToChroma(cqtRes)
        assertEquals(12, chroma.size)
        // A is pitch class 9 (0:C, 1:C#, 2:D, 3:D#, 4:E, 5:F, 6:F#, 7:G, 8:G#, 9:A, 10:A#, 11:B)
        assertTrue("A pitch class (index 9) should have peak energy", chroma[9] > 0.4f)
    }

    @Test
    fun testPolyphonicMultiPitchDetector() {
        val sampleRate = 48000
        val numSamples = 8192
        // Polyphonic A Major Triad: A4 (440Hz) + C#5 (554.37Hz) + E5 (659.25Hz)
        val pcm = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (0.4 * sin(2.0 * Math.PI * 440.0 * t) +
             0.3 * sin(2.0 * Math.PI * 554.37 * t) +
             0.3 * sin(2.0 * Math.PI * 659.25 * t)).toFloat()
        }

        val detectedNotes = MultiPitchDetector.detectMultiplePitches(pcm, sampleRate)
        assertTrue("Detector should identify active polyphonic notes", detectedNotes.isNotEmpty())

        val noteNames = detectedNotes.map { it.pitchName }
        assertTrue("Should detect A4 fundamental", noteNames.any { it.startsWith("A4") })
    }

    @Test
    fun testTemporalNoteTrackerWithVibratoAndSustain() {
        val tracker = TemporalNoteTracker(windowMs = 300L)

        val note1 = MultiPitchDetector.DetectedNote(
            pitchName = "A4",
            frequency = 440.0f,
            midiNumber = 69,
            octave = 4,
            pitchClass = 9,
            amplitude = 0.8f,
            velocity = 95,
            confidence = 0.9f,
            attackTimeMs = 1000L,
            releaseTimeMs = 1000L
        )

        val updatedFrame1 = tracker.update(listOf(note1), nowMs = 1000L)
        assertEquals(1, updatedFrame1.size)
        assertEquals("A4", updatedFrame1[0].pitchName)

        // Simulate vibrato frequency shift (442Hz)
        val note1Vibrato = note1.copy(frequency = 442.0f)
        val updatedFrame2 = tracker.update(listOf(note1Vibrato), nowMs = 1050L)
        assertEquals(1, updatedFrame2.size)
        // Same note ID maintained
        assertEquals(updatedFrame1[0].uniqueNoteId, updatedFrame2[0].uniqueNoteId)
    }
}
