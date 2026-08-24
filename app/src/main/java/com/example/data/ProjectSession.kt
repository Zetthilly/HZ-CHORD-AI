package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_sessions")
data class ProjectSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val bpm: Int = 0,
    val keySignature: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val audioFilePath: String? = null,
    val detectedChords: String = "", // Comma-separated or JSON list of chords
    val categoryTags: String = "All" // Comma-separated tags
)
