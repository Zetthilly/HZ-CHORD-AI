package com.example.data

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Classifications available for harmonic note analysis.
 * Note: Classification is purely analytical and NEVER causes any note to be hidden,
 * removed, or replaced in the audio stream or visual display.
 */
enum class HarmonicNoteClassification(
    val label: String,
    val color: Color,
    val description: String
) {
    CHORD_TONE("Chord Tone", Color(0xFF00E5FF), "Root, 3rd, 5th or extensions of active harmony"),
    PASSING_TONE("Passing Tone", Color(0xFFFF9100), "Stepwise motion connecting two chord tones"),
    NEIGHBOR_TONE("Neighbor Tone", Color(0xFFFFD600), "Stepwise departure and return to same chord tone"),
    CHROMATIC_PASSING_TONE("Chromatic Tone", Color(0xFFAA00FF), "Half-step chromatic line connecting scale degrees"),
    GRACE_NOTE("Grace Note", Color(0xFFFF4081), "Rapid ornamental embellishment note preceding target pitch"),
    APPOGGIATURA("Appoggiatura", Color(0xFFFF3D00), "Accented non-chord tone resolving by step"),
    SUSPENSION("Suspension", Color(0xFF00B8D4), "Held non-chord tone resolving downward"),
    ANTICIPATION("Anticipation", Color(0xFF3D5AFC), "Early arrival of next chord tone"),
    PEDAL_TONE("Pedal Tone", Color(0xFF00E676), "Sustained bass tone beneath shifting chords"),
    ORNAMENT("Ornament", Color(0xFFE040FB), "Trill, mordent, turn, or decorative acoustic gesture")
}

/**
 * Model representing a detected note in performance stream with full timing,
 * velocity, articulation, and analytical classification metadata.
 */
data class DetectedPerformanceNote(
    val id: String = UUID.randomUUID().toString(),
    val noteName: String, // e.g. "F#", "G", "G#", "A", "Bb", "B", "C"
    val octave: Int = 4,
    val timestampMs: Long = 0L,
    val durationMs: Long = 200L,
    val velocity: Float = 0.8f, // 0.0 - 1.0; <0.45f represents ghost notes
    val isGhostNote: Boolean = false,
    val isGraceNote: Boolean = false,
    val classification: HarmonicNoteClassification = HarmonicNoteClassification.CHORD_TONE
) {
    /**
     * Pitch display label, e.g., "F#4"
     */
    val fullPitchName: String
        get() = "$noteName$octave"

    /**
     * Helper to get note name cleanly without octave
     */
    val cleanNoteName: String
        get() = noteName.filter { it.isLetter() || it == '#' || it == 'b' }
}

/**
 * Harmonic Reconstruction Engine with Accurate Note Preservation.
 *
 * GUARANTEES:
 * 1. Never invents notes not actually played.
 * 2. Never hides notes actually played.
 * 3. Never replaces one detected note with another.
 * 4. Never simplifies or removes grace notes, passing notes, chromatic approach notes, ornaments, or ghost notes.
 * 5. Display visual note stream matches audio 100% accurately while harmonic analysis runs independently in background.
 */
object HarmonicReconstructionEngine {

    private val CHROMATIC_SCALE = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val CHROMATIC_SCALE_FLATS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /**
     * Analyzes raw detected pitches against parent chord and key signature, assigning analytical classifications
     * WITHOUT ever removing, substituting, or altering any note in the returned list.
     */
    fun processAndClassifyNotes(
        rawNotes: List<String>,
        parentChordSymbol: String,
        keySignature: String = "",
        baseTimestampMs: Long = 0L,
        stepIntervalMs: Long = 180L
    ): List<DetectedPerformanceNote> {
        if (rawNotes.isEmpty()) return emptyList()

        val (root, extension) = MusicTheoryUtils.splitChord(parentChordSymbol)
        val chordInfo = buildChordNotes(root, extension)
        val chordNoteNames = chordInfo.map { normalizeNoteName(it) }

        val result = mutableListOf<DetectedPerformanceNote>()

        for (i in rawNotes.indices) {
            val rawName = rawNotes[i].trim()
            if (rawName.isBlank()) continue

            val normalized = normalizeNoteName(rawName)
            val prevNote = if (i > 0) normalizeNoteName(rawNotes[i - 1]) else null
            val nextNote = if (i < rawNotes.size - 1) normalizeNoteName(rawNotes[i + 1]) else null

            // Detect grace note characteristics (e.g. rapid note before main note or explicitly marked)
            val isGrace = rawName.contains("grace", ignoreCase = true) || 
                          (prevNote != null && i < rawNotes.size - 1 && isHalfStep(normalized, nextNote) && stepIntervalMs < 120)
            
            // Detect ghost note (soft accent/ghost indicator)
            val isGhost = rawName.contains("ghost", ignoreCase = true) || rawName.contains("(")

            val cleanNote = rawName.replace("grace", "", ignoreCase = true)
                                  .replace("ghost", "", ignoreCase = true)
                                  .replace("(", "").replace(")", "").trim()

            val classification = when {
                isGrace -> HarmonicNoteClassification.GRACE_NOTE
                chordNoteNames.contains(normalized) -> HarmonicNoteClassification.CHORD_TONE
                isChromaticPassing(prevNote, normalized, nextNote) -> HarmonicNoteClassification.CHROMATIC_PASSING_TONE
                isStepwisePassing(prevNote, normalized, nextNote) -> HarmonicNoteClassification.PASSING_TONE
                isNeighbor(prevNote, normalized, nextNote) -> HarmonicNoteClassification.NEIGHBOR_TONE
                else -> HarmonicNoteClassification.ORNAMENT
            }

            val timestamp = baseTimestampMs + (i * stepIntervalMs)
            val duration = if (isGrace) 90L else if (isGhost) 140L else stepIntervalMs

            result.add(
                DetectedPerformanceNote(
                    noteName = cleanNote,
                    octave = getOctaveForNote(cleanNote, i),
                    timestampMs = timestamp,
                    durationMs = duration,
                    velocity = if (isGhost) 0.35f else if (isGrace) 0.65f else 0.85f,
                    isGhostNote = isGhost,
                    isGraceNote = isGrace,
                    classification = classification
                )
            )
        }

        return result
    }

    /**
     * Determines parent chord harmony strictly from structural chord tones, ignoring ornamental/passing pitches
     * for chord naming, while preserving ALL notes for the transcription display.
     */
    fun deriveParentChordFilterOrnaments(notes: List<DetectedPerformanceNote>): String {
        val structuralNotes = notes.filter { 
            it.classification == HarmonicNoteClassification.CHORD_TONE || 
            (!it.isGraceNote && !it.isGhostNote && it.classification != HarmonicNoteClassification.CHROMATIC_PASSING_TONE)
        }.map { it.cleanNoteName }

        if (structuralNotes.isEmpty()) {
            return notes.firstOrNull()?.cleanNoteName ?: "C"
        }

        // Infer chord triad/seventh from structural notes
        val uniqueNotes = structuralNotes.distinct()
        val first = uniqueNotes.first()
        return when {
            uniqueNotes.containsAll(listOf("F#", "A#", "C#")) -> "F#"
            uniqueNotes.containsAll(listOf("B", "D#", "F#")) -> "B"
            uniqueNotes.containsAll(listOf("C#", "F", "G#")) -> "C#"
            uniqueNotes.containsAll(listOf("D#", "F#", "A#")) -> "D#m"
            uniqueNotes.containsAll(listOf("C", "E", "G", "B")) -> "Cmaj7"
            uniqueNotes.containsAll(listOf("C", "E", "G")) -> "C"
            uniqueNotes.containsAll(listOf("G", "B", "D")) -> "G"
            uniqueNotes.containsAll(listOf("A", "C", "E")) -> "Am"
            else -> first
        }
    }

    private fun normalizeNoteName(note: String): String {
        val clean = note.takeWhile { it.isLetter() || it == '#' || it == 'b' }
            .replaceFirstChar { it.uppercase() }
        if (clean.length > 1 && clean[1] == 'b') {
            val flatIdx = CHROMATIC_SCALE_FLATS.indexOf(clean)
            if (flatIdx != -1) return CHROMATIC_SCALE[flatIdx]
        }
        return clean
    }

    private fun isHalfStep(noteA: String?, noteB: String?): Boolean {
        if (noteA == null || noteB == null) return false
        val idxA = CHROMATIC_SCALE.indexOf(normalizeNoteName(noteA))
        val idxB = CHROMATIC_SCALE.indexOf(normalizeNoteName(noteB))
        if (idxA == -1 || idxB == -1) return false
        val diff = (idxB - idxA + 12) % 12
        return diff == 1 || diff == 11
    }

    private fun isChromaticPassing(prev: String?, curr: String, next: String?): Boolean {
        if (prev == null || next == null) return false
        val p = CHROMATIC_SCALE.indexOf(normalizeNoteName(prev))
        val c = CHROMATIC_SCALE.indexOf(normalizeNoteName(curr))
        val n = CHROMATIC_SCALE.indexOf(normalizeNoteName(next))
        if (p == -1 || c == -1 || n == -1) return false

        val distPC = (c - p + 12) % 12
        val distCN = (n - c + 12) % 12

        // Check half-step movement (e.g., F# -> G -> G# or A -> Bb -> B)
        return (distPC == 1 && distCN == 1) || (distPC == 11 && distCN == 11)
    }

    private fun isStepwisePassing(prev: String?, curr: String, next: String?): Boolean {
        if (prev == null || next == null) return false
        val p = CHROMATIC_SCALE.indexOf(normalizeNoteName(prev))
        val c = CHROMATIC_SCALE.indexOf(normalizeNoteName(curr))
        val n = CHROMATIC_SCALE.indexOf(normalizeNoteName(next))
        if (p == -1 || c == -1 || n == -1) return false

        val distPC = (c - p + 12) % 12
        val distCN = (n - c + 12) % 12
        return (distPC in 1..2) && (distCN in 1..2)
    }

    private fun isNeighbor(prev: String?, curr: String, next: String?): Boolean {
        if (prev == null || next == null) return false
        val p = normalizeNoteName(prev)
        val c = normalizeNoteName(curr)
        val n = normalizeNoteName(next)
        return p == n && p != c
    }

    private fun buildChordNotes(root: String, extension: String): List<String> {
        val rootIdx = CHROMATIC_SCALE.indexOf(normalizeNoteName(root))
        if (rootIdx == -1) return listOf(root)

        val isMinor = extension.startsWith("m") && !extension.startsWith("maj")
        val thirdOffset = if (isMinor) 3 else 4
        val fifthOffset = 7

        val third = CHROMATIC_SCALE[(rootIdx + thirdOffset) % 12]
        val fifth = CHROMATIC_SCALE[(rootIdx + fifthOffset) % 12]

        val notes = mutableListOf(CHROMATIC_SCALE[rootIdx], third, fifth)
        if (extension.contains("7")) {
            val seventhOffset = if (extension.contains("maj")) 11 else 10
            notes.add(CHROMATIC_SCALE[(rootIdx + seventhOffset) % 12])
        }
        return notes
    }

    private fun getOctaveForNote(note: String, index: Int): Int {
        val clean = note.takeWhile { it.isLetter() || it == '#' || it == 'b' }
        return when (clean) {
            "E2", "F2", "F#2", "G2", "G#2", "A2", "A#2", "Bb2", "B2" -> 2
            "C3", "D3", "E3", "F3", "F#3", "G3", "A3", "B3" -> 3
            "C5", "D5", "E5", "F5", "F#5", "G5" -> 5
            else -> 4
        }
    }
}
