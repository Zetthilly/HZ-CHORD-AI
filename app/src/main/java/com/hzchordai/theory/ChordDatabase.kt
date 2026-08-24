package com.hzchordai.theory

/**
 * Data structure representing a mathematical chord template.
 */
data class ChordTemplate(
    val symbolSuffix: String,        // e.g. "maj7", "m9", "13", "7b9", "sus4"
    val fullDisplayName: String,     // e.g. "Major 7th", "Minor 9th"
    val quality: String,             // "Major", "Minor", "Dominant", "Suspended", "Diminished", "Augmented", "Extended", "Altered"
    val intervals: Set<Int>,         // Pitch class intervals relative to root, e.g. {0, 4, 7, 11}
    val requiredIntervals: Set<Int>, // Critical intervals that MUST be present, e.g. {0, 4, 11} for maj7
    val optionalIntervals: Set<Int> = setOf(7), // Intervals that can be omitted in performance (e.g. 5th)
    val extensions: List<String> = emptyList(),
    val alterations: List<String> = emptyList(),
    val basePriority: Float = 1.0f   // Preference multiplier (higher for fundamental/standard chords)
)

/**
 * Mathematical Chord Database containing exact music theory definitions for all triads, 6ths, 7ths,
 * extended 9ths/11ths/13ths, suspended, diminished, augmented, and altered dominant chords.
 */
object ChordDatabase {

    val TEMPLATES: List<ChordTemplate> = listOf(
        // ==================== TRIADS ====================
        ChordTemplate(
            symbolSuffix = "",
            fullDisplayName = "Major Triad",
            quality = "Major",
            intervals = setOf(0, 4, 7),
            requiredIntervals = setOf(0, 4),
            optionalIntervals = setOf(7),
            basePriority = 2.0f
        ),
        ChordTemplate(
            symbolSuffix = "m",
            fullDisplayName = "Minor Triad",
            quality = "Minor",
            intervals = setOf(0, 3, 7),
            requiredIntervals = setOf(0, 3),
            optionalIntervals = setOf(7),
            basePriority = 2.0f
        ),
        ChordTemplate(
            symbolSuffix = "sus2",
            fullDisplayName = "Suspended 2nd",
            quality = "Suspended",
            intervals = setOf(0, 2, 7),
            requiredIntervals = setOf(0, 2),
            optionalIntervals = setOf(7),
            extensions = listOf("2"),
            basePriority = 1.5f
        ),
        ChordTemplate(
            symbolSuffix = "sus4",
            fullDisplayName = "Suspended 4th",
            quality = "Suspended",
            intervals = setOf(0, 5, 7),
            requiredIntervals = setOf(0, 5),
            optionalIntervals = setOf(7),
            extensions = listOf("4"),
            basePriority = 1.5f
        ),
        ChordTemplate(
            symbolSuffix = "dim",
            fullDisplayName = "Diminished Triad",
            quality = "Diminished",
            intervals = setOf(0, 3, 6),
            requiredIntervals = setOf(0, 3, 6),
            basePriority = 1.4f
        ),
        ChordTemplate(
            symbolSuffix = "aug",
            fullDisplayName = "Augmented Triad",
            quality = "Augmented",
            intervals = setOf(0, 4, 8),
            requiredIntervals = setOf(0, 4, 8),
            alterations = listOf("#5"),
            basePriority = 1.3f
        ),

        // ==================== 6TH CHORDS ====================
        ChordTemplate(
            symbolSuffix = "6",
            fullDisplayName = "Major 6th",
            quality = "Major",
            intervals = setOf(0, 4, 7, 9),
            requiredIntervals = setOf(0, 4, 9),
            optionalIntervals = setOf(7),
            extensions = listOf("6"),
            basePriority = 1.6f
        ),
        ChordTemplate(
            symbolSuffix = "m6",
            fullDisplayName = "Minor 6th",
            quality = "Minor",
            intervals = setOf(0, 3, 7, 9),
            requiredIntervals = setOf(0, 3, 9),
            optionalIntervals = setOf(7),
            extensions = listOf("6"),
            basePriority = 1.6f
        ),
        ChordTemplate(
            symbolSuffix = "6/9",
            fullDisplayName = "6/9",
            quality = "Major",
            intervals = setOf(0, 4, 7, 9, 2),
            requiredIntervals = setOf(0, 4, 9, 2),
            optionalIntervals = setOf(7),
            extensions = listOf("6", "9"),
            basePriority = 1.7f
        ),

        // ==================== 7TH CHORDS ====================
        ChordTemplate(
            symbolSuffix = "maj7",
            fullDisplayName = "Major 7th",
            quality = "Major",
            intervals = setOf(0, 4, 7, 11),
            requiredIntervals = setOf(0, 4, 11),
            optionalIntervals = setOf(7),
            extensions = listOf("maj7"),
            basePriority = 1.8f
        ),
        ChordTemplate(
            symbolSuffix = "m7",
            fullDisplayName = "Minor 7th",
            quality = "Minor",
            intervals = setOf(0, 3, 7, 10),
            requiredIntervals = setOf(0, 3, 10),
            optionalIntervals = setOf(7),
            extensions = listOf("m7"),
            basePriority = 1.8f
        ),
        ChordTemplate(
            symbolSuffix = "7",
            fullDisplayName = "Dominant 7th",
            quality = "Dominant",
            intervals = setOf(0, 4, 7, 10),
            requiredIntervals = setOf(0, 4, 10),
            optionalIntervals = setOf(7),
            extensions = listOf("7"),
            basePriority = 1.8f
        ),
        ChordTemplate(
            symbolSuffix = "dim7",
            fullDisplayName = "Diminished 7th",
            quality = "Diminished",
            intervals = setOf(0, 3, 6, 9),
            requiredIntervals = setOf(0, 3, 6, 9),
            extensions = listOf("dim7"),
            basePriority = 1.6f
        ),
        ChordTemplate(
            symbolSuffix = "m7b5",
            fullDisplayName = "Half-Diminished 7th",
            quality = "Diminished",
            intervals = setOf(0, 3, 6, 10),
            requiredIntervals = setOf(0, 3, 6, 10),
            extensions = listOf("m7b5"),
            alterations = listOf("b5"),
            basePriority = 1.6f
        ),
        ChordTemplate(
            symbolSuffix = "m(maj7)",
            fullDisplayName = "Minor-Major 7th",
            quality = "Minor",
            intervals = setOf(0, 3, 7, 11),
            requiredIntervals = setOf(0, 3, 11),
            optionalIntervals = setOf(7),
            extensions = listOf("maj7"),
            basePriority = 1.5f
        ),

        // ==================== EXTENDED (9THS, 11THS, 13THS) ====================
        ChordTemplate(
            symbolSuffix = "add9",
            fullDisplayName = "Add 9",
            quality = "Major",
            intervals = setOf(0, 4, 7, 2),
            requiredIntervals = setOf(0, 4, 2),
            optionalIntervals = setOf(7),
            extensions = listOf("9"),
            basePriority = 1.7f
        ),
        ChordTemplate(
            symbolSuffix = "maj9",
            fullDisplayName = "Major 9th",
            quality = "Major",
            intervals = setOf(0, 4, 7, 11, 2),
            requiredIntervals = setOf(0, 4, 11, 2),
            optionalIntervals = setOf(7),
            extensions = listOf("maj7", "9"),
            basePriority = 1.75f
        ),
        ChordTemplate(
            symbolSuffix = "m9",
            fullDisplayName = "Minor 9th",
            quality = "Minor",
            intervals = setOf(0, 3, 7, 10, 2),
            requiredIntervals = setOf(0, 3, 10, 2),
            optionalIntervals = setOf(7),
            extensions = listOf("m7", "9"),
            basePriority = 1.75f
        ),
        ChordTemplate(
            symbolSuffix = "9",
            fullDisplayName = "Dominant 9th",
            quality = "Dominant",
            intervals = setOf(0, 4, 7, 10, 2),
            requiredIntervals = setOf(0, 4, 10, 2),
            optionalIntervals = setOf(7),
            extensions = listOf("7", "9"),
            basePriority = 1.75f
        ),
        ChordTemplate(
            symbolSuffix = "maj11",
            fullDisplayName = "Major 11th",
            quality = "Major",
            intervals = setOf(0, 4, 7, 11, 2, 5),
            requiredIntervals = setOf(0, 4, 11, 5),
            optionalIntervals = setOf(7, 2),
            extensions = listOf("maj7", "9", "11"),
            basePriority = 1.6f
        ),
        ChordTemplate(
            symbolSuffix = "m11",
            fullDisplayName = "Minor 11th",
            quality = "Minor",
            intervals = setOf(0, 3, 7, 10, 2, 5),
            requiredIntervals = setOf(0, 3, 10, 5),
            optionalIntervals = setOf(7, 2),
            extensions = listOf("m7", "9", "11"),
            basePriority = 1.7f
        ),
        ChordTemplate(
            symbolSuffix = "11",
            fullDisplayName = "Dominant 11th",
            quality = "Dominant",
            intervals = setOf(0, 4, 7, 10, 2, 5),
            requiredIntervals = setOf(0, 10, 5),
            optionalIntervals = setOf(4, 7, 2),
            extensions = listOf("7", "11"),
            basePriority = 1.6f
        ),
        ChordTemplate(
            symbolSuffix = "maj13",
            fullDisplayName = "Major 13th",
            quality = "Major",
            intervals = setOf(0, 4, 7, 11, 2, 9),
            requiredIntervals = setOf(0, 4, 11, 9),
            optionalIntervals = setOf(7, 2, 5),
            extensions = listOf("maj7", "9", "13"),
            basePriority = 1.65f
        ),
        ChordTemplate(
            symbolSuffix = "m13",
            fullDisplayName = "Minor 13th",
            quality = "Minor",
            intervals = setOf(0, 3, 7, 10, 2, 9),
            requiredIntervals = setOf(0, 3, 10, 9),
            optionalIntervals = setOf(7, 2, 5),
            extensions = listOf("m7", "9", "13"),
            basePriority = 1.65f
        ),
        ChordTemplate(
            symbolSuffix = "13",
            fullDisplayName = "Dominant 13th",
            quality = "Dominant",
            intervals = setOf(0, 4, 7, 10, 2, 5, 9),
            requiredIntervals = setOf(0, 4, 10, 9),
            optionalIntervals = setOf(7, 2, 5),
            extensions = listOf("7", "9", "13"),
            basePriority = 1.7f
        ),

        // ==================== ALTERED DOMINANTS ====================
        ChordTemplate(
            symbolSuffix = "7b9",
            fullDisplayName = "7 Flat 9",
            quality = "Altered",
            intervals = setOf(0, 4, 7, 10, 1),
            requiredIntervals = setOf(0, 4, 10, 1),
            optionalIntervals = setOf(7),
            extensions = listOf("7"),
            alterations = listOf("b9"),
            basePriority = 1.65f
        ),
        ChordTemplate(
            symbolSuffix = "7#9",
            fullDisplayName = "7 Sharp 9",
            quality = "Altered",
            intervals = setOf(0, 4, 7, 10, 3),
            requiredIntervals = setOf(0, 4, 10, 3),
            optionalIntervals = setOf(7),
            extensions = listOf("7"),
            alterations = listOf("#9"),
            basePriority = 1.65f
        ),
        ChordTemplate(
            symbolSuffix = "7#11",
            fullDisplayName = "7 Sharp 11",
            quality = "Altered",
            intervals = setOf(0, 4, 7, 10, 6),
            requiredIntervals = setOf(0, 4, 10, 6),
            optionalIntervals = setOf(7),
            extensions = listOf("7"),
            alterations = listOf("#11"),
            basePriority = 1.65f
        ),
        ChordTemplate(
            symbolSuffix = "7b13",
            fullDisplayName = "7 Flat 13",
            quality = "Altered",
            intervals = setOf(0, 4, 7, 10, 8),
            requiredIntervals = setOf(0, 4, 10, 8),
            optionalIntervals = setOf(7),
            extensions = listOf("7"),
            alterations = listOf("b13"),
            basePriority = 1.65f
        ),
        ChordTemplate(
            symbolSuffix = "7alt",
            fullDisplayName = "Altered Dominant",
            quality = "Altered",
            intervals = setOf(0, 4, 10, 1, 3, 8),
            requiredIntervals = setOf(0, 4, 10),
            optionalIntervals = setOf(1, 3, 8),
            extensions = listOf("7"),
            alterations = listOf("alt"),
            basePriority = 1.6f
        )
    )

    /**
     * Match a set of interval pitch classes against all templates for a candidate root.
     * Returns match score from 0.0 to 1.0.
     */
    fun evaluateMatch(
        inputIntervals: Set<Int>,
        template: ChordTemplate
    ): Float {
        // Must contain all required intervals
        val requiredMatched = template.requiredIntervals.count { it in inputIntervals }
        if (requiredMatched < template.requiredIntervals.size) {
            return 0.0f
        }

        // Calculate overlap with expected intervals
        val totalExpected = template.intervals
        val matchedCount = totalExpected.count { it in inputIntervals }
        val extraNotesCount = inputIntervals.count { it !in totalExpected }

        var rawScore = matchedCount.toFloat() / totalExpected.size.toFloat()

        // Penalty for unexpected notes not in template
        val extraPenalty = extraNotesCount * 0.15f
        rawScore -= extraPenalty

        return (rawScore * template.basePriority).coerceIn(0.0f, 1.0f)
    }
}
