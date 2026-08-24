package com.example.data

data class ImportedAudioMetadata(
    val uriString: String? = null,
    val fileName: String,
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val durationMs: Long = 0L,
    val durationFormatted: String = "00:00",
    val bitrateKbps: String = "Unknown",
    val sampleRateHz: String = "Unknown",
    val channels: String = "Unknown",
    val fileSize: String = "Unknown",
    val formatExtension: String = "",
    val sourceType: String = "Unknown",
    val waveformAmplitudes: List<Float> = emptyList(),
    val detectedBpm: Int = 0,
    val detectedKey: String = ""
)
