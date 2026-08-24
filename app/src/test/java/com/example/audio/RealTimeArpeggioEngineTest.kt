package com.example.audio

import com.example.audio.arpeggio.RealTimeArpeggioEngine
import com.example.audio.dsp.MultiPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

class RealTimeArpeggioEngineTest {

    private lateinit var engine: RealTimeArpeggioEngine

    @Before
    fun setUp() {
        engine = RealTimeArpeggioEngine(bufferWindowMs = 600L, confidenceThreshold = 0.70f)
    }

    @Test
    fun testSequentialArpeggioReconstruction() {
        val now = System.currentTimeMillis()

        // C4 -> E4 -> G4 -> B4 played sequentially over 120ms (Arpeggio)
        val noteC = createNote("C4", 60, 261.63f, 90, now)
        val noteE = createNote("E4", 64, 329.63f, 85, now + 35)
        val noteG = createNote("G4", 67, 392.00f, 80, now + 70)
        val noteB = createNote("B4", 71, 493.88f, 85, now + 110)

        engine.processDetectedNotes(listOf(noteC), now)
        engine.processDetectedNotes(listOf(noteE), now + 35)
        engine.processDetectedNotes(listOf(noteG), now + 70)
        val result = engine.processDetectedNotes(listOf(noteB), now + 110)

        // The engine should reconstruct Cmaj7 rather than single notes
        assertTrue("Reconstructed chord should be Cmaj7 or C family", result.chordSymbol.contains("C"))
        assertTrue("Confidence should be above 70%", result.confidence >= 0.70f)
        assertEquals(RealTimeArpeggioEngine.ArpeggioSpeed.ROLLED_CHORD, result.arpeggioSpeed)
    }

    @Test
    fun testFourVoiceSeparation() {
        val now = System.currentTimeMillis()

        val bass = createNote("C2", 36, 65.41f, 100, now)     // Bass (MIDI < 48)
        val tenor = createNote("E3", 52, 164.81f, 90, now)    // Tenor (48..59)
        val alto = createNote("G4", 67, 392.00f, 85, now)     // Alto (60..71)
        val soprano = createNote("C5", 72, 523.25f, 95, now)  // Soprano (>= 72)

        val result = engine.processDetectedNotes(listOf(bass, tenor, alto, soprano), now)

        val voiceSep = result.voiceSeparation
        assertEquals(1, voiceSep.bass.size)
        assertEquals("C2", voiceSep.bass[0].pitch)
        assertEquals(1, voiceSep.tenor.size)
        assertEquals("E3", voiceSep.tenor[0].pitch)
        assertEquals(1, voiceSep.alto.size)
        assertEquals("G4", voiceSep.alto[0].pitch)
        assertEquals(1, voiceSep.soprano.size)
        assertEquals("C5", voiceSep.soprano[0].pitch)
    }

    @Test
    fun testNonChordToneAndGraceNoteWeighting() {
        val now = System.currentTimeMillis()

        // C4 + G4 (Chord tones) + C#4 (Grace note / Acciaccatura)
        val noteC = createNote("C4", 60, 261.63f, 95, now)
        val noteG = createNote("G4", 67, 392.00f, 90, now)
        val graceNote = createNote("C#4", 61, 277.18f, 50, now, isGrace = true)

        val result = engine.processDetectedNotes(listOf(noteC, noteG, graceNote), now)

        val nctMap = result.nonChordTones
        assertEquals(RealTimeArpeggioEngine.NonChordToneType.ACCIACCATURA, nctMap["C#4"])
    }

    @Test
    fun testStrictConfidenceRejection() {
        val now = System.currentTimeMillis()

        // Unharmonized random noisy pitch with very low confidence
        val noiseNote = createNote("F#4", 66, 369.99f, 20, now, confidence = 0.20f)

        val result = engine.processDetectedNotes(listOf(noiseNote), now)

        // Low confidence should result in "Unknown" rejection
        assertEquals("Unknown", result.chordSymbol)
        assertEquals(0.0f, result.confidence)
    }

    @Test
    fun testSpecializedGenreModes() {
        val now = System.currentTimeMillis()

        // Worship mode with Add9 chord
        val noteC = createNote("C4", 60, 261.63f, 90, now)
        val noteD = createNote("D4", 62, 293.66f, 85, now + 20)
        val noteE = createNote("E4", 64, 329.63f, 85, now + 40)
        val noteG = createNote("G4", 67, 392.00f, 90, now + 60)

        val worshipResult = engine.processDetectedNotes(
            listOf(noteC, noteD, noteE, noteG),
            nowMs = now + 60,
            specializedMode = "Worship"
        )

        assertNotNull(worshipResult.genreStyle)
        assertTrue("Worship style should be identified", worshipResult.genreStyle!!.contains("Worship"))

        // Sungura guitar mode with F# / C#
        val noteFSharp = createNote("F#4", 66, 369.99f, 100, now)
        val noteCSharp = createNote("C#5", 73, 554.37f, 95, now + 15)

        val sunguraResult = engine.processDetectedNotes(
            listOf(noteFSharp, noteCSharp),
            nowMs = now + 15,
            specializedMode = "Sungura"
        )

        assertNotNull(sunguraResult.genreStyle)
        assertTrue("Sungura style should be identified", sunguraResult.genreStyle!!.contains("Sungura"))
    }

    @Test
    fun testProcessingLatencyUnder20ms() {
        val now = System.currentTimeMillis()
        val triad = listOf(
            createNote("C4", 60, 261.63f, 90, now),
            createNote("E4", 64, 329.63f, 85, now + 10),
            createNote("G4", 67, 392.00f, 90, now + 20)
        )

        // Benchmark 100 iterations of arpeggio processing
        val elapsedMs = measureTimeMillis {
            repeat(100) { i ->
                engine.processDetectedNotes(triad, now + i * 10)
            }
        }

        val averageLatencyMs = elapsedMs / 100.0
        assertTrue("Mean latency per frame must be < 20ms (Actual: ${averageLatencyMs}ms)", averageLatencyMs < 20.0)
    }

    @Test
    fun testHighThroughputStress() {
        val now = System.currentTimeMillis()

        // Rapid stream of 500 note events
        for (i in 0 until 500) {
            val pitchIndex = i % 12
            val midi = 60 + pitchIndex
            val note = createNote("Pitch$i", midi, 260.0f + i, 80, now + i * 5)
            val res = engine.processDetectedNotes(listOf(note), now + i * 5)
            assertNotNull(res)
        }
    }

    private fun createNote(
        name: String,
        midi: Int,
        freq: Float,
        vel: Int,
        timestampMs: Long,
        confidence: Float = 0.90f,
        isGrace: Boolean = false
    ): MultiPitchDetector.DetectedNote {
        val pc = midi % 12
        return MultiPitchDetector.DetectedNote(
            pitchName = name,
            frequency = freq,
            midiNumber = midi,
            octave = midi / 12 - 1,
            pitchClass = pc,
            amplitude = vel / 127.0f,
            velocity = vel,
            confidence = confidence,
            attackTimeMs = timestampMs,
            releaseTimeMs = timestampMs + 300L,
            isGraceNote = isGrace
        )
    }
}
