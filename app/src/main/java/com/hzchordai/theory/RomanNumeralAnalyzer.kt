package com.hzchordai.theory

/**
 * Roman Numeral Harmonic Function Analyzer.
 * Converts chords into diatonic Roman numerals (e.g., I, ii, iii, IV, V, vi, vii°) and handles
 * extended qualities, slash bases, and secondary dominants (e.g., V/V, V/ii).
 */
object RomanNumeralAnalyzer {

    /**
     * Analyze chord symbol and root relative to estimated key.
     */
    fun analyze(
        chordRootPc: Int,
        template: ChordTemplate,
        keyRootPc: Int,
        isMinorKey: Boolean,
        slashBassPc: Int? = null,
        useFlats: Boolean = false
    ): String {
        val rootInterval = IntervalCalculator.calculateInterval(chordRootPc, keyRootPc)

        var baseNumeral = if (!isMinorKey) {
            getMajorKeyNumeral(rootInterval, template.quality)
        } else {
            getMinorKeyNumeral(rootInterval, template.quality)
        }

        // Secondary dominant check (Major/Dominant 7th on diatonic scale degrees ii, iii, IV, V, vi)
        if (template.quality == "Dominant" || template.quality == "Altered") {
            val secondaryTarget = getSecondaryDominantTarget(rootInterval, isMinorKey)
            if (secondaryTarget != null) {
                baseNumeral = "V${template.symbolSuffix}/$secondaryTarget"
            } else if (!baseNumeral.contains(template.symbolSuffix)) {
                baseNumeral += template.symbolSuffix
            }
        } else if (template.symbolSuffix.isNotEmpty() && !baseNumeral.contains(template.symbolSuffix)) {
            // Append extensions like maj7, m7, add9, 6, etc.
            baseNumeral += template.symbolSuffix
        }

        // Handle slash bass
        if (slashBassPc != null && slashBassPc != chordRootPc) {
            val bassName = IntervalCalculator.pitchClassToNoteName(slashBassPc, useFlats)
            baseNumeral += "/$bassName"
        }

        return baseNumeral
    }

    /**
     * Diatonic Roman numerals for Major Keys.
     */
    private fun getMajorKeyNumeral(degreeInterval: Int, quality: String): String {
        val isMinor = (quality == "Minor" || quality == "Diminished")
        val isDim = (quality == "Diminished")

        return when (degreeInterval) {
            0 -> if (isMinor) "i" else "I"           // 1st degree (Tonic)
            1 -> if (isMinor) "bii" else "bII"       // Neapolitan / bII
            2 -> if (isDim) "ii°" else if (isMinor) "ii" else "II" // 2nd degree
            3 -> if (isMinor) "biii" else "bIII"
            4 -> if (isMinor) "iii" else "III"       // 3rd degree
            5 -> if (isMinor) "iv" else "IV"         // 4th degree (Subdominant)
            6 -> "#IV°"
            7 -> if (isMinor) "v" else "V"           // 5th degree (Dominant)
            8 -> if (isMinor) "bvi" else "bVI"
            9 -> if (isMinor) "vi" else "VI"         // 6th degree (Submediant)
            10 -> if (isMinor) "bvii" else "bVII"    // Subtonic
            11 -> if (isDim) "vii°" else "VII"       // Leading tone
            else -> "I"
        }
    }

    /**
     * Diatonic Roman numerals for Minor Keys.
     */
    private fun getMinorKeyNumeral(degreeInterval: Int, quality: String): String {
        val isMajor = (quality == "Major" || quality == "Dominant")
        val isDim = (quality == "Diminished")

        return when (degreeInterval) {
            0 -> if (isMajor) "I" else "i"           // Tonic minor
            1 -> "bII"
            2 -> if (isDim) "ii°" else "ii"          // Supertonic dim
            3 -> if (isMajor) "III" else "iii"       // Mediant Major
            4 -> "iii"
            5 -> if (isMajor) "IV" else "iv"         // Subdominant minor
            6 -> "#iv°"
            7 -> if (isMajor) "V" else "v"           // Dominant (Major V or minor v)
            8 -> if (isMajor) "VI" else "vi"         // Submediant Major
            9 -> "vi°"
            10 -> if (isMajor) "VII" else "vii°"     // Subtonic / Leading Tone
            11 -> "vii°"
            else -> "i"
        }
    }

    /**
     * Secondary Dominant target detector (e.g., V/V, V/ii, V/vi).
     */
    private fun getSecondaryDominantTarget(degreeInterval: Int, isMinorKey: Boolean): String? {
        return if (!isMinorKey) {
            when (degreeInterval) {
                2 -> "V"   // V/V (e.g. D7 in C major)
                9 -> "ii"  // V/ii (e.g. A7 in C major)
                4 -> "vi"  // V/vi (e.g. E7 in C major)
                11 -> "iii"// V/iii (e.g. B7 in C major)
                0 -> "IV"  // V/IV (e.g. C7 in C major)
                else -> null
            }
        } else {
            when (degreeInterval) {
                2 -> "V"   // V/V in minor
                7 -> "iv"  // V/iv
                0 -> "VI"  // V/VI
                else -> null
            }
        }
    }
}
