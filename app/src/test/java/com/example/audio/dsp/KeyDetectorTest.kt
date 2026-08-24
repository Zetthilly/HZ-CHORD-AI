package com.example.audio.dsp

import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyDetectorTest {
    private lateinit var detector: KeyDetector
    
    @Before
    fun setUp() {
        detector = KeyDetector()
    }
    
    @Test
    fun testCMajorKeyDetection() {
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
        // Normalize
        val sum = chromaVector.sum()
        for (i in chromaVector.indices) chromaVector[i] /= sum
        
        val result = detector.detectKey(chromaVector)
        
        assertEquals(MusicalKey.C, result.key, "Should detect C major key")
        assertEquals(KeyMode.MAJOR, result.mode, "Should detect MAJOR mode")
        assertTrue(result.confidence > 70f, "Confidence ${result.confidence} should be > 70")
    }
    
    @Test
    fun testAMinorKeyDetection() {
        // Ideal A Minor chroma (A, C, E active)
        val chromaVector = floatArrayOf(
            1f,   // C  (0)
            0f,   // C#
            0f,   // D
            0f,   // D#
            1f,   // E  (4)
            0f,   // F
            0f,   // F#
            0f,   // G
            0f,   // G#
            1f,   // A  (9)
            0f,   // A#
            0f    // B
        )
        // Normalize
        val sum = chromaVector.sum()
        for (i in chromaVector.indices) chromaVector[i] /= sum
        
        val result = detector.detectKey(chromaVector)
        
        assertEquals(MusicalKey.A, result.key, "Should detect A key")
        assertEquals(KeyMode.MINOR, result.mode, "Should detect MINOR mode")
        assertTrue(result.confidence > 70f, "Confidence ${result.confidence} should be > 70")
    }
    
    @Test
    fun testConfidenceRange() {
        val chromaVector = floatArrayOf(
            1f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f
        )
        val sum = chromaVector.sum()
        for (i in chromaVector.indices) chromaVector[i] /= sum
        
        val result = detector.detectKey(chromaVector)
        
        assertTrue(result.confidence in 0f..100f, "Confidence ${result.confidence} should be in [0, 100]")
    }
}
