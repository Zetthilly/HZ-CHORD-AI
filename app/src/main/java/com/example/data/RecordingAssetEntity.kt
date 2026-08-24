package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * Professional Recording Storage and Reuse System™ Entity.
 * Stores local recording files, audio asset metadata, waveform peaks,
 * project information, user notes, and module analysis states for offline reuse.
 */
@Entity(tableName = "recording_assets")
@JsonClass(generateAdapter = true)
data class RecordingAssetEntity(
    @PrimaryKey val id: String, // UUID
    val recordingName: String,
    val filePath: String,
    val dateCreated: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val fileFormat: String = "WAV", // WAV, FLAC, AAC, MP3
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val channels: Int = 1,
    val fileSizeBytes: Long = 0L,
    val waveformAmplitudesCsv: String = "",
    val projectName: String? = null,
    val userNotes: String? = null,
    val isFavorite: Boolean = false,
    val detectedBpm: Int = 0,
    val detectedKey: String = "",
    val detectedChordsCsv: String = "",
    val detectedArpeggio: String? = null,
    val africanStyleLick: String? = null,
    val moduleStatesJson: String? = null
) {
    fun getFormattedSize(): String {
        return if (fileSizeBytes > 0) {
            val mb = fileSizeBytes.toDouble() / (1024 * 1024)
            if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%d KB", fileSizeBytes / 1024)
        } else "1.2 MB"
    }

    fun getFormattedDuration(): String {
        val totalSecs = durationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }

    fun getWaveformPeaksList(): List<Float> {
        if (waveformAmplitudesCsv.isBlank()) {
            return List(48) { (Math.sin(it * 0.4) * 0.4 + 0.5).toFloat() }
        }
        return try {
            waveformAmplitudesCsv.split(",")
                .mapNotNull { it.trim().toFloatOrNull() }
                .takeIf { it.isNotEmpty() } ?: List(48) { 0.5f }
        } catch (e: Exception) {
            List(48) { 0.5f }
        }
    }
}
