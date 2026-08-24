package com.hzchordai.theory

/**
 * Interface defining Genre-Specific Harmonic Weighting Profiles.
 */
interface GenreProfile {
    val name: String

    /**
     * Get preference weight multiplier for a given chord template.
     */
    fun getTemplateWeight(template: ChordTemplate): Float

    /**
     * Get transition weight multiplier between two chord root/quality pairs.
     */
    fun getTransitionWeight(
        prevRoot: Int,
        prevQuality: String,
        currRoot: Int,
        currQuality: String
    ): Float
}

/**
 * Standard Default Genre Profile.
 */
object StandardGenreProfile : GenreProfile {
    override val name: String = "Standard"

    override fun getTemplateWeight(template: ChordTemplate): Float = 1.0f

    override fun getTransitionWeight(
        prevRoot: Int,
        prevQuality: String,
        currRoot: Int,
        currQuality: String
    ): Float = 1.0f
}

/**
 * Worship Genre Harmony Profile.
 * Boosts add9, maj7, sus2, sus4, 6/9, and smooth I-IV-V-vi transitions.
 */
object WorshipProfile : GenreProfile {
    override val name: String = "Worship"

    override fun getTemplateWeight(template: ChordTemplate): Float {
        return when (template.symbolSuffix) {
            "add9", "sus2", "sus4", "maj7", "6/9" -> 2.2f
            "m7", "maj9" -> 1.8f
            "7alt", "7b9", "7#9" -> 0.4f // Penalize heavy altered dissonances in modern worship
            else -> 1.0f
        }
    }

    override fun getTransitionWeight(
        prevRoot: Int,
        prevQuality: String,
        currRoot: Int,
        currQuality: String
    ): Float {
        val interval = IntervalCalculator.calculateInterval(currRoot, prevRoot)
        return when (interval) {
            5, 7, 9 -> 1.5f // Root movement by 4th, 5th, or relative 6th (e.g. I -> IV -> V -> vi)
            2, 4 -> 1.3f
            else -> 1.0f
        }
    }
}

/**
 * Gospel Genre Harmony Profile.
 * Boosts extended dominants (7b9, 7#9, 11, 13), secondary dominants, and chromatic passing diminished chords.
 */
object GospelProfile : GenreProfile {
    override val name: String = "Gospel"

    override fun getTemplateWeight(template: ChordTemplate): Float {
        return when (template.symbolSuffix) {
            "7b9", "7#9", "11", "13", "m11", "m13", "7b13", "dim7", "m7b5" -> 2.5f
            "maj9", "m9", "7" -> 2.0f
            "" -> 0.8f // Gospel favors rich extensions over plain triads
            else -> 1.1f
        }
    }

    override fun getTransitionWeight(
        prevRoot: Int,
        prevQuality: String,
        currRoot: Int,
        currQuality: String
    ): Float {
        val interval = IntervalCalculator.calculateInterval(currRoot, prevRoot)
        return when {
            interval == 5 || interval == 7 -> 1.8f // Circle of 5ths movement
            interval == 1 -> 1.6f // Half-step passing motion
            prevQuality == "Altered" || prevQuality == "Dominant" -> 1.7f
            else -> 1.0f
        }
    }
}

/**
 * Jazz Genre Harmony Profile.
 * Boosts 9ths, 11ths, 13ths, altered dominants (7#11, 7b13), ii-V-I movements, and tritone substitutions.
 */
object JazzProfile : GenreProfile {
    override val name: String = "Jazz"

    override fun getTemplateWeight(template: ChordTemplate): Float {
        return when (template.symbolSuffix) {
            "maj9", "m9", "9", "maj11", "m11", "11", "maj13", "m13", "13",
            "7#11", "7b13", "7b9", "7#9", "7alt", "m7b5" -> 2.8f
            "maj7", "m7", "7", "dim7" -> 2.0f
            "" -> 0.5f // Plain triads are rare in jazz
            else -> 1.0f
        }
    }

    override fun getTransitionWeight(
        prevRoot: Int,
        prevQuality: String,
        currRoot: Int,
        currQuality: String
    ): Float {
        val interval = IntervalCalculator.calculateInterval(currRoot, prevRoot)
        return when {
            interval == 5 -> 2.0f // Perfect 4th up (ii-V or V-I)
            interval == 6 -> 1.8f // Tritone substitution movement
            interval == 1 || interval == 11 -> 1.5f // Chromatic approach
            else -> 1.0f
        }
    }
}

/**
 * Sungura Genre Harmony Profile.
 * Boosts crisp major triads, dominant 7ths, fast cadential I-IV-V-I progressions, and clean triad slash chords.
 */
object SunguraProfile : GenreProfile {
    override val name: String = "Sungura"

    override fun getTemplateWeight(template: ChordTemplate): Float {
        return when (template.symbolSuffix) {
            "", "7" -> 2.5f // Clear major triads and driving dominant 7ths
            "6", "add9", "sus4" -> 1.5f
            "7alt", "7b9", "7#9", "m11", "m13" -> 0.3f // Heavy jazz dissonance suppressed
            else -> 1.0f
        }
    }

    override fun getTransitionWeight(
        prevRoot: Int,
        prevQuality: String,
        currRoot: Int,
        currQuality: String
    ): Float {
        val interval = IntervalCalculator.calculateInterval(currRoot, prevRoot)
        return when (interval) {
            5, 7 -> 2.0f // Strong I -> IV -> V -> I cadences
            else -> 1.0f
        }
    }
}

/**
 * Factory for retrieving Genre Profiles by name.
 */
object GenreProfileRegistry {
    fun getProfile(name: String): GenreProfile {
        return when (name.lowercase().trim()) {
            "worship" -> WorshipProfile
            "gospel" -> GospelProfile
            "jazz" -> JazzProfile
            "sungura" -> SunguraProfile
            else -> StandardGenreProfile
        }
    }
}
