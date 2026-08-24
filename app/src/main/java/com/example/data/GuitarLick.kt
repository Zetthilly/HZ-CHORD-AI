package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "guitar_licks")
data class GuitarLick(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String, // e.g. "Sungura Lead Hook" or "Gospel Guitar Run"
    val genre: String, // "Sungura", "Jit", "Soukous", "Rhumba", "Afro-Jazz", "Gospel", "Major Arpeggio", "Minor Pentatonic"
    val notes: String, // String representation of notes mapped, e.g. "C E G C' E'"
    val bpm: Int = 0,
    val confidence: Float = 0.95f,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 2000L,
    val isFavorite: Boolean = false
)
