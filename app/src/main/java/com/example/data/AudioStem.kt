package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_stems")
data class AudioStem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val name: String, // "vocals", "drums", "bass", "melody"
    val filePath: String?, // Local file path on disk (since 100% offline-first)
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
    val durationMs: Long = 0L,
    val sampleRate: Int = 44100
) {
    val displayName: String
        get() = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
