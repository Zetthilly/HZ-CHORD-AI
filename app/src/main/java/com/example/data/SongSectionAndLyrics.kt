package com.example.data

data class SongSectionInfo(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val colorHex: Long = 0xFF00E5FF
)

data class SyncedLyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val chordsAbove: String
)
