package com.example.data

object MusicTheoryUtils {

    private val CHROMATIC_SHARPS = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val CHROMATIC_FLATS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /**
     * Finds the index of a note in the chromatic scale.
     */
    private fun getNoteIndex(note: String): Int {
        val normalized = note.trim().replaceFirstChar { it.uppercase() }
        val sharpIndex = CHROMATIC_SHARPS.indexOf(normalized)
        if (sharpIndex != -1) return sharpIndex
        
        val flatIndex = CHROMATIC_FLATS.indexOf(normalized)
        if (flatIndex != -1) return flatIndex
        
        // Try fallback matches (case insensitive or trimming pitch info)
        val cleaned = normalized.takeWhile { it.isLetter() || it == '#' || it == 'b' }
        val fallbackSharp = CHROMATIC_SHARPS.indexOf(cleaned)
        if (fallbackSharp != -1) return fallbackSharp
        
        val fallbackFlat = CHROMATIC_FLATS.indexOf(cleaned)
        if (fallbackFlat != -1) return fallbackFlat
        
        return 0 // Default fallback to C
    }

    /**
     * Splits a chord name into its root note and its symbol/suffix (e.g. "Cmaj7" -> "C" to "maj7")
     */
    fun splitChord(chordName: String): Pair<String, String> {
        val trimmed = chordName.trim()
        if (trimmed.isEmpty()) return Pair("", "")
        
        if (trimmed.length >= 2) {
            val firstTwo = trimmed.substring(0, 2)
            if (firstTwo[1] == '#' || firstTwo[1] == 'b') {
                return Pair(firstTwo, trimmed.substring(2))
            }
        }
        return Pair(trimmed.substring(0, 1), trimmed.substring(1))
    }

    /**
     * Transposes a single note by a given number of semitones.
     */
    fun transposeNote(note: String, semitones: Int): String {
        val index = getNoteIndex(note)
        var newIndex = (index + semitones) % 12
        if (newIndex < 0) newIndex += 12
        
        // Choose flat/sharp based on input signature if possible
        return if (note.contains("b")) {
            CHROMATIC_FLATS[newIndex]
        } else {
            CHROMATIC_SHARPS[newIndex]
        }
    }

    /**
     * Transposes a full chord symbol by a given number of semitones (e.g. "Cmaj7" + 2 -> "Dmaj7")
     */
    fun transposeChord(chordName: String, semitones: Int): String {
        val (root, extension) = splitChord(chordName)
        if (root.isEmpty()) return chordName
        val newRoot = transposeNote(root, semitones)
        return newRoot + extension
    }

    /**
     * Transposes an array of chord progression elements.
     */
    fun transposeProgression(progression: List<String>, semitones: Int): List<String> {
        return progression.map { transposeChord(it, semitones) }
    }

    /**
     * Converts a chord name into its corresponding Roman numeral functional progression step
     * under a specific key signature (e.g. "C" in "C Major" -> "I", "Am" in "C Major" -> "vi").
     */
    fun getRomanNumeral(chordName: String, keySignature: String): String {
        val (chordRoot, suffix) = splitChord(chordName)
        if (chordRoot.isEmpty()) return ""

        val keyPart = keySignature.replace("Key of", "").trim()
        val isMinorKey = keyPart.contains("Minor", ignoreCase = true) || keyPart.endsWith("m")
        val scaleRoot = keyPart.split(" ")[0]

        val rootOffset = (getNoteIndex(chordRoot) - getNoteIndex(scaleRoot) + 12) % 12

        // Determine if chord itself is major or minor
        val isChordMinor = (suffix.startsWith("m") && !suffix.startsWith("maj")) || 
                           suffix.contains("min") || 
                           suffix.contains("dim") || 
                           suffix.contains("°")

        val baseNumeral = if (isMinorKey) {
            // Roman Numeral table relative to Natural Minor Key
            when (rootOffset) {
                0 -> if (isChordMinor) "i" else "I"
                1 -> if (isChordMinor) "bii" else "bII"
                2 -> if (isChordMinor) "ii°" else "II"
                3 -> if (isChordMinor) "biii" else "bIII"
                4 -> if (isChordMinor) "iv" else "IV"
                5 -> if (isChordMinor) "iv" else "IV"
                6 -> "bV"
                7 -> if (isChordMinor) "v" else "V"
                8 -> if (isChordMinor) "bvi" else "bVI"
                9 -> if (isChordMinor) "vi°" else "VI"
                10 -> if (isChordMinor) "bvii" else "bVII"
                11 -> if (isChordMinor) "vii°" else "VII"
                else -> "i"
            }
        } else {
            // Roman Numeral table relative to Major Key
            when (rootOffset) {
                0 -> if (isChordMinor) "i" else "I"
                1 -> if (isChordMinor) "bii" else "bII"
                2 -> if (isChordMinor) "ii" else "II"
                3 -> if (isChordMinor) "biii" else "bIII"
                4 -> if (isChordMinor) "iii" else "III"
                5 -> if (isChordMinor) "iv" else "IV"
                6 -> "bV"
                7 -> if (isChordMinor) "v" else "V"
                8 -> if (isChordMinor) "bvi" else "bVI"
                9 -> if (isChordMinor) "vi" else "VI"
                10 -> if (isChordMinor) "bvii" else "bVII"
                11 -> if (isChordMinor) "vii°" else "VII"
                else -> "I"
            }
        }
        
        // Add extension for clarity but strip standard minor prefix characters already encoded
        val extensionLabel = if (suffix.startsWith("m") && !suffix.startsWith("maj")) {
            suffix.substring(1)
        } else {
            suffix
        }
        
        return baseNumeral + extensionLabel
    }

    /**
     * Transposes a key signature string (e.g. "F# Major" -> "E Major" if semitones = -2).
     */
    fun transposeKeySignature(keySignature: String, semitones: Int): String {
        if (semitones == 0 || keySignature.isBlank()) return keySignature
        val clean = keySignature.replace("Key of", "").trim()
        val parts = clean.split(" ")
        val root = parts.firstOrNull() ?: return keySignature
        val scaleType = if (parts.size > 1) parts.subList(1, parts.size).joinToString(" ") else ""
        val transposedRoot = transposeNote(root, semitones)
        return if (scaleType.isNotEmpty()) "$transposedRoot $scaleType" else transposedRoot
    }

    /**
     * Transposes a space or dash-separated lyric chords string (e.g. "C - G" -> "D - A" if semitones = +2).
     */
    fun transposeLyricChords(chordsStr: String, semitones: Int): String {
        if (semitones == 0 || chordsStr.isBlank()) return chordsStr
        val tokens = chordsStr.split(" ")
        return tokens.joinToString(" ") { token ->
            val cleaned = token.trim()
            if (cleaned == "-" || cleaned == "♪" || cleaned.isEmpty()) {
                cleaned
            } else {
                transposeChord(cleaned, semitones)
            }
        }
    }

    /**
     * Returns human readable interval description for semitone shift amount.
     */
    fun getIntervalName(semitones: Int): String {
        return when (semitones) {
            -12 -> "Octave Down (-12 ST)"
            -11 -> "Major 7th Down (-11 ST)"
            -10 -> "Minor 7th Down (-10 ST)"
            -9 -> "Major 6th Down (-9 ST)"
            -8 -> "Minor 6th Down (-8 ST)"
            -7 -> "Perfect 5th Down (-7 ST)"
            -6 -> "Tritone Down (-6 ST)"
            -5 -> "Perfect 4th Down (-5 ST)"
            -4 -> "Major 3rd Down (-4 ST)"
            -3 -> "Minor 3rd Down (-3 ST)"
            -2 -> "Whole Step Down (-2 ST)"
            -1 -> "Half Step Down (-1 ST)"
            0 -> "Original Pitch (0 ST)"
            1 -> "Half Step Up (+1 ST)"
            2 -> "Whole Step Up (+2 ST)"
            3 -> "Minor 3rd Up (+3 ST)"
            4 -> "Major 3rd Up (+4 ST)"
            5 -> "Perfect 4th Up (+5 ST)"
            6 -> "Tritone Up (+6 ST)"
            7 -> "Perfect 5th Up (+7 ST)"
            8 -> "Minor 6th Up (+8 ST)"
            9 -> "Major 6th Up (+9 ST)"
            10 -> "Minor 7th Up (+10 ST)"
            11 -> "Major 7th Up (+11 ST)"
            12 -> "Octave Up (+12 ST)"
            else -> if (semitones > 0) "+$semitones Semitones" else "$semitones Semitones"
        }
    }
}
