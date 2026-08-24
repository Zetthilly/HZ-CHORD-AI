package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detected_chords")
data class DetectedChord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int = 0,
    val timestampMs: Long = 0L,
    val chordName: String, // e.g. "Cmaj7", "Am", "G"
    val rootNote: String, // e.g. "C", "A", "G"
    val chordType: String, // e.g. "Major 7th", "Minor", "Major"
    val confidence: Float = 1.0f,
    val notes: String = "", // Comma-separated notes, e.g. "C,E,G,B"
    val durationMs: Long = 1000L
) {
    /**
     * Helper to get notes as list of Strings.
     */
    fun getNotesList(): List<String> {
        if (notes.isBlank()) return emptyList()
        return notes.split(",").map { it.trim() }
    }
}
