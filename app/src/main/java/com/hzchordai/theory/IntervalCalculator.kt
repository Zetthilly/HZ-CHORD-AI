package com.hzchordai.theory

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Mathematical interval and pitch-class calculator.
 * Standard pitch class mapping: C=0, C#=1, D=2, D#=3, E=4, F=5, F#=6, G=7, G#=8, A=9, A#=10, B=11.
 */
object IntervalCalculator {

    val NOTE_NAMES_SHARP = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val NOTE_NAMES_FLAT  = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /**
     * Map a note string to a pitch class (0..11).
     */
    fun noteNameToPitchClass(name: String): Int {
        val clean = name.trim().replace("♯", "#").replace("♭", "b")
        if (clean.isEmpty()) return 0

        val rootPart = when {
            clean.length >= 2 && (clean[1] == '#' || clean[1] == 'b') -> clean.substring(0, 2)
            else -> clean.substring(0, 1)
        }

        return when (rootPart.uppercase()) {
            "C" -> 0
            "C#", "DB" -> 1
            "D" -> 2
            "D#", "EB" -> 3
            "E" -> 4
            "F" -> 5
            "F#", "GB" -> 6
            "G" -> 7
            "G#", "AB" -> 8
            "A" -> 9
            "A#", "BB" -> 10
            "B" -> 11
            else -> 0
        }
    }

    /**
     * Convert pitch class (0..11) to note name.
     */
    fun pitchClassToNoteName(pitchClass: Int, useFlats: Boolean = false): String {
        val pc = ((pitchClass % 12) + 12) % 12
        return if (useFlats) NOTE_NAMES_FLAT[pc] else NOTE_NAMES_SHARP[pc]
    }

    /**
     * Convert MIDI note number to pitch class (0..11).
     */
    fun midiToPitchClass(midi: Int): Int {
        return ((midi % 12) + 12) % 12
    }

    /**
     * Calculate MIDI note from frequency in Hz.
     */
    fun frequencyToMidi(freqHz: Double): Int {
        if (freqHz <= 0.0) return 0
        return (69.0 + 12.0 * log2(freqHz / 440.0)).roundToInt()
    }

    /**
     * Calculate pitch class from frequency in Hz.
     */
    fun frequencyToPitchClass(freqHz: Double): Int {
        return midiToPitchClass(frequencyToMidi(freqHz))
    }

    /**
     * Calculate pitch class interval: (note - root + 12) mod 12.
     */
    fun calculateInterval(note: Int, root: Int): Int {
        return ((note - root) % 12 + 12) % 12
    }

    /**
     * Calculate interval vector relative to a root.
     */
    fun calculateIntervalVector(notes: Collection<Int>, root: Int): Set<Int> {
        return notes.map { calculateInterval(it, root) }.toSet()
    }

    /**
     * Calculate shortest chromatic distance between two pitch classes (0..6).
     */
    fun shortestDistance(pc1: Int, pc2: Int): Int {
        val diff = abs(pc1 - pc2) % 12
        return if (diff > 6) 12 - diff else diff
    }

    /**
     * Get human-readable interval name.
     */
    fun getIntervalName(intervalSemitones: Int): String {
        val semitones = ((intervalSemitones % 12) + 12) % 12
        return when (semitones) {
            0 -> "Unison"
            1 -> "Minor 2nd (b9)"
            2 -> "Major 2nd (9)"
            3 -> "Minor 3rd (m3)"
            4 -> "Major 3rd (M3)"
            5 -> "Perfect 4th (11)"
            6 -> "Tritone (#11/b5)"
            7 -> "Perfect 5th (P5)"
            8 -> "Minor 6th (b13/b6)"
            9 -> "Major 6th (13/6)"
            10 -> "Minor 7th (b7)"
            11 -> "Major 7th (maj7)"
            else -> "Unison"
        }
    }

    /**
     * Calculate a 12-element chromagram vector from frequencies and magnitudes.
     */
    fun chromagramFromFrequencies(frequencies: List<Double>, magnitudes: List<Double>): FloatArray {
        val chroma = FloatArray(12)
        val count = minOf(frequencies.size, magnitudes.size)
        var totalMag = 0.0f

        for (i in 0 until count) {
            val f = frequencies[i]
            val m = magnitudes[i].toFloat()
            if (f > 20.0 && f < 10000.0 && m > 0.001f) {
                val pc = frequencyToPitchClass(f)
                chroma[pc] += m
                totalMag += m
            }
        }

        if (totalMag > 0f) {
            for (i in 0 until 12) {
                chroma[i] /= totalMag
            }
        }
        return chroma
    }
}
