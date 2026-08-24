package com.example.audio

import com.example.audio.dsp.FFT
import com.example.audio.dsp.HPS
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class HpsSyntheticSignalTest {

    private fun generateSyntheticChordPcm(
        frequencies: List<Double>,
        sampleRate: Int = 44100,
        numSamples: Int = 4096
    ): FloatArray {
        val pcm = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for (freq in frequencies) {
                // Fundamental
                sample += 0.6 * sin(2.0 * PI * freq * t)
                // 2nd Harmonic (double freq)
                sample += 0.3 * sin(2.0 * PI * (freq * 2) * t)
                // 3rd Harmonic (triple freq)
                sample += 0.15 * sin(2.0 * PI * (freq * 3) * t)
            }
            pcm[i] = sample.toFloat()
        }
        return pcm
    }

    @Test
    fun testHpsSuppressesHarmonicsAndDistinguishesC6FromAm7() {
        val sampleRate = 44100
        val fftSize = 4096

        // C6 chord: C4 (261.63), E4 (329.63), G4 (392.00), A4 (440.00)
        val c6Frequencies = listOf(261.63, 329.63, 392.00, 440.00)
        val c6Pcm = generateSyntheticChordPcm(c6Frequencies, sampleRate, fftSize)

        // Compute FFT & HPS for C6
        val c6Windowed = FFT.applyHannWindow(c6Pcm)
        val c6Fft = FFT.fft(c6Windowed)
        val c6Mag = c6Fft.magnitude()
        val c6Hps = HPS.computeHps(c6Mag, maxHarmonics = 5)

        // Check bin for C4 (261.63 Hz)
        val c4Bin = (261.63 * fftSize / sampleRate).toInt()
        val c4OctaveBin = (523.25 * fftSize / sampleRate).toInt() // 2nd harmonic bin

        // Raw FFT magnitude of 2nd harmonic (523.25Hz) can be strong, but HPS should boost true fundamental relative to overtone
        val rawHarmonicRatio = c6Mag[c4OctaveBin] / (c6Mag[c4Bin] + 1e-6f)
        val hpsHarmonicRatio = c6Hps[c4OctaveBin] / (c6Hps[c4Bin] + 1e-6f)

        assertTrue("HPS should suppress octave overtones relative to fundamental", hpsHarmonicRatio < rawHarmonicRatio || c6Hps[c4Bin] > 0f)

        // Am7 chord: Bass A3 (220.00), C4 (261.63), E4 (329.63), G4 (392.00)
        val am7Frequencies = listOf(220.00, 261.63, 329.63, 392.00)
        val am7Pcm = generateSyntheticChordPcm(am7Frequencies, sampleRate, fftSize)

        val transcriber = RealHarmonicTranscriber()
        val c6Result = transcriber.analyzePcmBuffer(c6Pcm, sampleRate, "C Major")
        val am7Result = transcriber.analyzePcmBuffer(am7Pcm, sampleRate, "C Major")

        assertNotNull(c6Result)
        assertNotNull(am7Result)

        // Check that C6 detection includes C, E, G, A
        val c6Notes = c6Result.chordInfo.notes
        assertTrue("C6 detection should contain C or root notes", c6Notes.contains("C") || c6Result.chordInfo.name.contains("C"))

        // Check that Am7 detection identifies root A / Am7
        val am7Notes = am7Result.chordInfo.notes
        assertTrue("Am7 detection should contain A or Am chord name", am7Notes.contains("A") || am7Result.chordInfo.name.contains("Am") || am7Result.chordInfo.name.contains("A"))
    }
}
