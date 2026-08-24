package com.example.audio

import com.example.data.AdvancedArpeggioEngine
import com.example.data.ArpeggioAnalysisResult
import com.example.data.GuitarLick
import com.example.data.MusicWorkstationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ArpeggioIntelligenceEngine processes raw note streams from the audio analyzer
 * to identify note-by-note playing, harmonic structures, and specific style patterns 
 * (including Sungura, Soukous, Rhumba, Jit, Afro Jazz, Gospel, Broken Chords, Walk-Ups),
 * automatically persisting detected patterns to the Room Database.
 */
class ArpeggioIntelligenceEngine(
    private val repository: MusicWorkstationRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    /**
     * Analyzes raw detected note stream, calculates parent chords and style confidence,
     * and automatically saves identified licks/arpeggios to the Room database.
     */
    fun processNoteStream(
        rawNotes: List<String>,
        bpm: Int = 0,
        autoSaveToRoom: Boolean = true
    ): ArpeggioAnalysisResult {
        val result = AdvancedArpeggioEngine.analyzeNoteStream(rawNotes)

        if (autoSaveToRoom && repository != null && rawNotes.isNotEmpty()) {
            scope.launch {
                val genreName = when {
                    result.isAfricanStyle && result.styleName != null -> result.styleName
                    else -> result.patternType.displayName
                }

                val title = if (result.isAfricanStyle) {
                    "${result.styleName} Lead Pattern (${result.inferredParentChord})"
                } else {
                    "${result.patternType.displayName} in ${result.inferredParentChord}"
                }

                val lickEntity = GuitarLick(
                    title = title,
                    genre = genreName,
                    notes = result.detectedNotes.joinToString(" "),
                    bpm = bpm,
                    confidence = result.confidence,
                    timestamp = System.currentTimeMillis(),
                    durationMs = 2500L,
                    isFavorite = result.isAfricanStyle
                )

                repository.insertLick(lickEntity)
            }
        }

        return result
    }

    /**
     * Direct helper for offline processing of raw pitch buffers.
     */
    companion object {
        fun analyzeNotesDirectly(notes: List<String>): ArpeggioAnalysisResult {
            return AdvancedArpeggioEngine.analyzeNoteStream(notes)
        }
    }
}
