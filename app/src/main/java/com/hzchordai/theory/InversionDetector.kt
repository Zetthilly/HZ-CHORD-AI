package com.hzchordai.theory

/**
 * Result of inversion and slash chord analysis.
 */
data class InversionAnalysis(
    val rootPitchClass: Int,
    val bassPitchClass: Int,
    val isSlashChord: Boolean,
    val inversionName: String, // "Root Position", "1st Inversion", "2nd Inversion", "3rd Inversion", "Slash Chord"
    val bassNoteName: String,
    val slashSymbolSuffix: String // e.g., "/E" or "" if Root position
)

/**
 * Inversion and Slash Chord Engine.
 * Analyzes bass pitch class separately from chord root and determines whether
 * the chord is in Root Position, 1st/2nd/3rd Inversion, or a custom Slash Chord (e.g., C/E, G/B, D/F#).
 */
object InversionDetector {

    /**
     * Analyze bass note in relation to the chord root and template intervals.
     */
    fun analyzeInversion(
        rootPitchClass: Int,
        bassPitchClass: Int,
        template: ChordTemplate,
        useFlats: Boolean = false
    ): InversionAnalysis {
        val root = ((rootPitchClass % 12) + 12) % 12
        val bass = ((bassPitchClass % 12) + 12) % 12

        val isRootPosition = (root == bass)
        val bassInterval = IntervalCalculator.calculateInterval(bass, root)
        val bassNoteName = IntervalCalculator.pitchClassToNoteName(bass, useFlats)

        if (isRootPosition) {
            return InversionAnalysis(
                rootPitchClass = root,
                bassPitchClass = bass,
                isSlashChord = false,
                inversionName = "Root Position",
                bassNoteName = bassNoteName,
                slashSymbolSuffix = ""
            )
        }

        val inversionName = when (bassInterval) {
            3, 4 -> "1st Inversion" // Minor or Major 3rd in bass
            6, 7, 8 -> "2nd Inversion" // Dim, Perf, or Aug 5th in bass
            9, 10, 11 -> "3rd Inversion" // 7th in bass
            else -> "Slash Chord" // Bass is extension or non-chord tone
        }

        return InversionAnalysis(
            rootPitchClass = root,
            bassPitchClass = bass,
            isSlashChord = true,
            inversionName = inversionName,
            bassNoteName = bassNoteName,
            slashSymbolSuffix = "/$bassNoteName"
        )
    }
}
