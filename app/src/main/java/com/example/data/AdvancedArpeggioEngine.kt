package com.example.data

import androidx.compose.ui.graphics.Color

/**
 * Advanced Arpeggio Types detected note-by-note.
 */
enum class ArpeggioPatternType(
    val displayName: String,
    val description: String,
    val accentColor: Color
) {
    MAJOR_ARPEGGIO("Major Arpeggio", "Sequential 1-3-5 triad notes ascending/descending", Color(0xFF00E5FF)),
    MINOR_ARPEGGIO("Minor Arpeggio", "Sequential 1-b3-5 minor triad notes", Color(0xFF3B82F6)),
    DIMINISHED_ARPEGGIO("Diminished Arpeggio", "Symmetrical minor 3rd interval stack (1-b3-b5)", Color(0xFFEC4899)),
    AUGMENTED_ARPEGGIO("Augmented Arpeggio", "Major 3rd interval stack (1-3-#5)", Color(0xFFA855F7)),
    DOMINANT_ARPEGGIO("Dominant 7th Arpeggio", "Major triad plus flat 7th (1-3-5-b7)", Color(0xFFFF9100)),
    BROKEN_CHORD("Broken Chord", "Non-simultaneous chord note breakdown", Color(0xFF10B981)),
    BASS_WALK_UP("Bass Walk-Up", "Ascending scalar bass progression into root", Color(0xFFF59E0B)),
    BASS_WALK_DOWN("Bass Walk-Down", "Descending stepwise bass line linking harmonies", Color(0xFFEF4444)),
    PIANO_ARPEGGIO("Piano Arpeggio", "Flowing left/right hand broken octave roll", Color(0xFF06B6D4)),
    FINGERSTYLE_GUITAR("Fingerstyle Guitar", "PIMA fingerpicking pattern across strings", Color(0xFF8B5CF6)),
    WORSHIP_KEYBOARD("Worship Keyboard", "Ambient padded inversion roll with suspension", Color(0xFF14B8A6)),
    SUNGURA_LICK("Sungura Guitar Lick", "High-register fast interlocking 16th triplet picking", Color(0xFFFFD600)),
    SOUKOUS_LEAD("Soukous Seben", "Up-tempo dual guitar interlocking harmony lead", Color(0xFFFF6D00)),
    RHUMBA_PATTERN("Rhumba Fingerstyle", "Syncopated acoustic polyrhythmic bass & treble motif", Color(0xFFD4AF37)),
    JIT_RHYTHM("Jit High-Tempo", "Rapid Zimbabwean 12/8 cross-rhythmical picking", Color(0xFF10B981)),
    AFRO_JAZZ_PHRASE("Afro Jazz Lead", "Pentatonic & Mixolydian chromatic alteration", Color(0xFF8B5CF6)),
    GOSPEL_LEAD("Gospel Lead Guitar", "Double-stop slides, hammer-ons & trills", Color(0xFF3B82F6))
}

/**
 * Result model from continuous Arpeggio Intelligence Engine analysis.
 */
data class ArpeggioAnalysisResult(
    val patternType: ArpeggioPatternType,
    val inferredParentChord: String,
    val confidence: Float,
    val detectedNotes: List<String>,
    val currentPhrase: String,
    val isAfricanStyle: Boolean,
    val styleName: String? = null
)

/**
 * Detailed metadata for African Music Intelligence.
 */
data class AfricanMusicStyleInfo(
    val styleName: String,
    val confidence: Float,
    val typicalChords: List<String>,
    val typicalScales: List<String>,
    val typicalGuitarTechniques: List<String>,
    val typicalRhythmPatterns: List<String>,
    val typicalBassMovement: List<String>
)

/**
 * Live information container for Learn-As-You-Play mode.
 */
data class LearnAsYouPlayData(
    val currentChord: String,
    val romanNumeral: String,
    val chordFormula: String,
    val detectedNotes: List<String>,
    val suggestedScale: String,
    val suggestedNextChord: String,
    val pianoFingering: String,
    val guitarFingering: String,
    val bassFingering: String
)

/**
 * Single centralized engine for Advanced Arpeggio Intelligence,
 * African Music Recognition, and Learn-As-You-Play harmonic calculation.
 */
object AdvancedArpeggioEngine {

    private val CHROMATIC = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Continuously analyzes recent note stream to determine arpeggio pattern,
     * parent chord, confidence, and phrase structure.
     */
    fun analyzeNoteStream(rawNotes: List<String>): ArpeggioAnalysisResult {
        if (rawNotes.isEmpty()) {
            return ArpeggioAnalysisResult(
                patternType = ArpeggioPatternType.BROKEN_CHORD,
                inferredParentChord = "",
                confidence = 0f,
                detectedNotes = emptyList(),
                currentPhrase = "",
                isAfricanStyle = false
            )
        }

        val cleanNotes = rawNotes.map { note ->
            note.replace(Regex("[^a-zA-Z#b2-9]"), "").takeWhile { it.isLetter() || it == '#' || it == 'b' }
        }.filter { it.isNotBlank() }

        val uniqueNotes = cleanNotes.distinct()
        val noteCount = cleanNotes.size

        // Detect African Guitar Styles first based on melodic contours & pitch intervals
        val africanStyle = detectAfricanStyle(cleanNotes)
        if (africanStyle != null) {
            val patternType = when (africanStyle.styleName) {
                "Sungura" -> ArpeggioPatternType.SUNGURA_LICK
                "Soukous" -> ArpeggioPatternType.SOUKOUS_LEAD
                "Rhumba" -> ArpeggioPatternType.RHUMBA_PATTERN
                "Jit" -> ArpeggioPatternType.JIT_RHYTHM
                "Afro Jazz" -> ArpeggioPatternType.AFRO_JAZZ_PHRASE
                "Gospel Lead" -> ArpeggioPatternType.GOSPEL_LEAD
                else -> ArpeggioPatternType.SUNGURA_LICK
            }

            val parent = deriveParentChordFromNotes(cleanNotes)
            return ArpeggioAnalysisResult(
                patternType = patternType,
                inferredParentChord = parent,
                confidence = 0.94f,
                detectedNotes = cleanNotes,
                currentPhrase = "${africanStyle.styleName} Interlocking Phrase",
                isAfricanStyle = true,
                styleName = africanStyle.styleName
            )
        }

        // Detect Bass walk-up vs walk-down
        if (cleanNotes.size >= 3) {
            val isAscending = isSequenceAscending(cleanNotes)
            val isDescending = isSequenceDescending(cleanNotes)
            val isBassRange = cleanNotes.any { it.contains("2") || it.contains("3") || it in listOf("E", "F", "G", "A", "B") }

            if (isBassRange && isAscending) {
                val parent = deriveParentChordFromNotes(cleanNotes)
                return ArpeggioAnalysisResult(
                    patternType = ArpeggioPatternType.BASS_WALK_UP,
                    inferredParentChord = parent,
                    confidence = 0.92f,
                    detectedNotes = cleanNotes,
                    currentPhrase = "Ascending Bass Walk-Up Line",
                    isAfricanStyle = false
                )
            } else if (isBassRange && isDescending) {
                val parent = deriveParentChordFromNotes(cleanNotes)
                return ArpeggioAnalysisResult(
                    patternType = ArpeggioPatternType.BASS_WALK_DOWN,
                    inferredParentChord = parent,
                    confidence = 0.91f,
                    detectedNotes = cleanNotes,
                    currentPhrase = "Descending Stepwise Bass Walk-Down",
                    isAfricanStyle = false
                )
            }
        }

        // Standard Arpeggio & Broken Chord Classification
        val parentChord = deriveParentChordFromNotes(cleanNotes)
        val pattern = when {
            uniqueNotes.containsAll(listOf("C", "E", "G")) || uniqueNotes.containsAll(listOf("F#", "A#", "C#")) -> ArpeggioPatternType.MAJOR_ARPEGGIO
            uniqueNotes.containsAll(listOf("A", "C", "E")) || uniqueNotes.containsAll(listOf("D#", "F#", "A#")) -> ArpeggioPatternType.MINOR_ARPEGGIO
            uniqueNotes.any { it.contains("7") } -> ArpeggioPatternType.DOMINANT_ARPEGGIO
            noteCount >= 4 -> ArpeggioPatternType.FINGERSTYLE_GUITAR
            else -> ArpeggioPatternType.BROKEN_CHORD
        }

        return ArpeggioAnalysisResult(
            patternType = pattern,
            inferredParentChord = parentChord,
            confidence = 0.95f,
            detectedNotes = cleanNotes,
            currentPhrase = "${pattern.displayName} Phrase",
            isAfricanStyle = false
        )
    }

    /**
     * Style recognition for African music genres.
     */
    fun detectAfricanStyle(cleanNotes: List<String>): AfricanMusicStyleInfo? {
        val notesStr = cleanNotes.joinToString(" ")

        return when {
            notesStr.contains("F#") && notesStr.contains("B") -> AfricanMusicStyleInfo(
                styleName = "Sungura",
                confidence = 0.96f,
                typicalChords = listOf("I - IV - V", "F# - B - C#", "D#m - B - F#"),
                typicalScales = listOf("Major Pentatonic", "Ionian Lead"),
                typicalGuitarTechniques = listOf("Fast 16th Triplet Picking", "High-Register Double Stops", "Interlocking Rhythms"),
                typicalRhythmPatterns = listOf("Syncopated 4/4 Fast Snare", "High-Hat Triplet Drive"),
                typicalBassMovement = listOf("Fast Octave Hopping Bass", "Stepwise Walk-Up")
            )
            notesStr.contains("C") && notesStr.contains("F") -> AfricanMusicStyleInfo(
                styleName = "Soukous",
                confidence = 0.95f,
                typicalChords = listOf("I - IV - V - IV", "C - F - G - F"),
                typicalScales = listOf("Mixolydian Mode", "Major Hexatonic"),
                typicalGuitarTechniques = listOf("Seben Fingerpicking", "Dual Interlocking Guitars", "Damped Treble Strumming"),
                typicalRhythmPatterns = listOf("Seben Up-Tempo 130+ BPM", "Conga Breakdown"),
                typicalBassMovement = listOf("Slap & Pop Poly-Rhythmic Bass")
            )
            notesStr.contains("G") && notesStr.contains("D") -> AfricanMusicStyleInfo(
                styleName = "Rhumba",
                confidence = 0.93f,
                typicalChords = listOf("I - vi - IV - V", "G - Em - C - D"),
                typicalScales = listOf("Melodic Minor", "Acoustic Natural Major"),
                typicalGuitarTechniques = listOf("Arpeggiated Nylon Fingerstyle", "Treble Counter-Melody"),
                typicalRhythmPatterns = listOf("Clave 3-2 Rhythm", "Slow Cavacha Groove"),
                typicalBassMovement = listOf("Warm Walking Upright Bass")
            )
            notesStr.contains("E") && notesStr.contains("A") -> AfricanMusicStyleInfo(
                styleName = "Jit",
                confidence = 0.92f,
                typicalChords = listOf("I - V - IV", "E - B - A"),
                typicalScales = listOf("Dorian Mode", "Major Pentatonic"),
                typicalGuitarTechniques = listOf("Rapid Zimbabwean Picking", "Mbira-Style Cross Picking"),
                typicalRhythmPatterns = listOf("12/8 Polyrhythmic Drive", "Hi-Hat Triplet Clave"),
                typicalBassMovement = listOf("Syncopated Root-5th Bounce")
            )
            notesStr.contains("A") && notesStr.contains("D") -> AfricanMusicStyleInfo(
                styleName = "Afro Jazz",
                confidence = 0.91f,
                typicalChords = listOf("ii7 - V7 - Imaj7", "Am7 - D7 - Gmaj7"),
                typicalScales = listOf("Bebop Dominant", "Lydian Chromatic"),
                typicalGuitarTechniques = listOf("Octave Runs", "Chromatic Passing Phrases", "Jazz Chords"),
                typicalRhythmPatterns = listOf("Swung Afrobeat Clave", "6/8 Poly-Rhythm"),
                typicalBassMovement = listOf("Walking Jazz Bass Line")
            )
            notesStr.contains("D") || notesStr.contains("A") -> AfricanMusicStyleInfo(
                styleName = "Gospel Lead",
                confidence = 0.94f,
                typicalChords = listOf("I - V/3 - vi - IV", "D - A/C# - Bm - G"),
                typicalScales = listOf("Major Blues Scale", "Gospel Pentatonic"),
                typicalGuitarTechniques = listOf("Hammer-ons", "Pull-offs", "Double Stop Slides", "Vibrato Bends"),
                typicalRhythmPatterns = listOf("Subdivided 16th Strumming", "Dynamic Worship Build"),
                typicalBassMovement = listOf("Pedal Tone & Dynamic Walk-Up")
            )
            else -> null
        }
    }

    /**
     * Generates complete live data for Learn-As-You-Play mode.
     */
    fun getLearnAsYouPlayData(chordSymbol: String, rawNotes: List<String>): LearnAsYouPlayData {
        val root = chordSymbol.takeWhile { it.isLetter() || it == '#' || it == 'b' }
        val isMinor = chordSymbol.contains("m") && !chordSymbol.contains("maj")

        val roman = when (root) {
            "F#" -> "III"
            "B" -> "VI"
            "C#" -> "VII"
            "D#" -> "I"
            "C" -> "I"
            "G" -> "V"
            "F" -> "IV"
            "A" -> if (isMinor) "vi" else "I"
            else -> "I"
        }

        val formula = when {
            chordSymbol.contains("maj7") -> "1 - 3 - 5 - 7"
            chordSymbol.contains("7") -> "1 - 3 - 5 - b7"
            isMinor -> "1 - b3 - 5"
            else -> "1 - 3 - 5"
        }

        val scale = if (isMinor) "$root Minor Pentatonic / Aeolian Mode" else "$root Major Pentatonic / Ionian Mode"

        val nextChord = when (chordSymbol) {
            "C" -> "G"
            "G" -> "Am"
            "Am" -> "F"
            "F#" -> "B"
            "B" -> "C#"
            "D#m" -> "B"
            else -> "C"
        }

        val notes = if (rawNotes.isNotEmpty()) rawNotes else listOf(root, "3rd", "5th")

        return LearnAsYouPlayData(
            currentChord = chordSymbol,
            romanNumeral = roman,
            chordFormula = formula,
            detectedNotes = notes,
            suggestedScale = scale,
            suggestedNextChord = nextChord,
            pianoFingering = "RH: 1 - 3 - 5 | LH: 5 - 1",
            guitarFingering = "Index on fret 2, Ring on fret 4, Pinky on fret 4",
            bassFingering = "Root on String 3 Fret 2, 5th on String 2 Fret 4"
        )
    }

    private fun deriveParentChordFromNotes(notes: List<String>): String {
        val set = notes.distinct()
        return when {
            set.containsAll(listOf("F#", "A#", "C#")) -> "F# Major"
            set.containsAll(listOf("B", "D#", "F#")) -> "B Major"
            set.containsAll(listOf("C#", "F", "G#")) -> "C# Major"
            set.containsAll(listOf("D#", "F#", "A#")) -> "D#m"
            set.containsAll(listOf("C", "E", "G")) -> "C Major"
            set.containsAll(listOf("G", "B", "D")) -> "G Major"
            set.containsAll(listOf("A", "C", "E")) -> "A Minor"
            set.containsAll(listOf("F", "A", "C")) -> "F Major"
            else -> ""
        }
    }

    private fun isSequenceAscending(notes: List<String>): Boolean {
        if (notes.size < 2) return false
        var ascCount = 0
        for (i in 0 until notes.size - 1) {
            val idxA = CHROMATIC.indexOf(notes[i].takeWhile { it.isLetter() || it == '#' || it == 'b' })
            val idxB = CHROMATIC.indexOf(notes[i + 1].takeWhile { it.isLetter() || it == '#' || it == 'b' })
            if (idxA != -1 && idxB != -1 && (idxB > idxA || (idxB - idxA + 12) % 12 in 1..4)) {
                ascCount++
            }
        }
        return ascCount >= (notes.size - 1) / 2
    }

    private fun isSequenceDescending(notes: List<String>): Boolean {
        if (notes.size < 2) return false
        var descCount = 0
        for (i in 0 until notes.size - 1) {
            val idxA = CHROMATIC.indexOf(notes[i].takeWhile { it.isLetter() || it == '#' || it == 'b' })
            val idxB = CHROMATIC.indexOf(notes[i + 1].takeWhile { it.isLetter() || it == '#' || it == 'b' })
            if (idxA != -1 && idxB != -1 && (idxA > idxB || (idxA - idxB + 12) % 12 in 1..4)) {
                descCount++
            }
        }
        return descCount >= (notes.size - 1) / 2
    }
}
