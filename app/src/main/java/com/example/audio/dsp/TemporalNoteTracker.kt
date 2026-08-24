package com.example.audio.dsp

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Rolling Polyphonic Note Event Tracker with 300ms windowing.
 * Implements:
 * 1. Vibrato pitch trajectory smoothing (prevents note fragmentation during pitch modulation)
 * 2. Glissando continuous pitch tracking
 * 3. Sustain pedal decay (retains notes after key release until energy decays)
 * 4. Grace note weighting (retains brief grace notes with reduced weight)
 * 5. Stable unique note ID persistence across frames
 */
class TemporalNoteTracker(
    private val windowMs: Long = 300L,
    private val graceNoteDecayFactor: Float = 0.82f,
    private val sustainDecayFactor: Float = 0.92f
) {

    private val activeTrackedNotes = ConcurrentHashMap<String, MultiPitchDetector.DetectedNote>()
    private val pitchHistoryMap = ConcurrentHashMap<String, MutableList<Float>>()

    /**
     * Updates tracked notes with a batch of freshly detected multi-pitch instances.
     */
    fun update(
        freshDetections: List<MultiPitchDetector.DetectedNote>,
        nowMs: Long = System.currentTimeMillis(),
        sustainPedalActive: Boolean = false
    ): List<MultiPitchDetector.DetectedNote> {
        // 1. Process aging / decaying notes
        for ((key, note) in activeTrackedNotes) {
            val ageMs = nowMs - note.attackTimeMs
            val timeSinceLastSeen = nowMs - note.releaseTimeMs

            if (timeSinceLastSeen > windowMs) {
                if (sustainPedalActive && note.amplitude > 0.08f) {
                    // Sustain pedal decay mode: retain note active with slow energy decay
                    val decayedAmp = note.amplitude * sustainDecayFactor
                    activeTrackedNotes[key] = note.copy(
                        amplitude = decayedAmp,
                        isSustained = true
                    )
                } else {
                    // Standard grace note / decay removal
                    val decayedAmp = note.amplitude * graceNoteDecayFactor
                    if (decayedAmp < 0.10f) {
                        activeTrackedNotes.remove(key)
                        pitchHistoryMap.remove(key)
                    } else {
                        activeTrackedNotes[key] = note.copy(amplitude = decayedAmp)
                    }
                }
            }
        }

        // 2. Insert or update newly detected pitch instances
        for (fresh in freshDetections) {
            val key = fresh.pitchName

            val existing = activeTrackedNotes[key]
            if (existing != null) {
                // Vibrato pitch smoothing: average frequency across last N frames to avoid jitter
                val history = pitchHistoryMap.getOrPut(key) { mutableListOf() }
                history.add(fresh.frequency)
                if (history.size > 8) history.removeAt(0)
                val smoothedFreq = history.average().toFloat()

                val updatedAmp = (existing.amplitude * 0.35f + fresh.amplitude * 0.65f).coerceIn(0.1f, 1.0f)

                activeTrackedNotes[key] = existing.copy(
                    frequency = smoothedFreq,
                    amplitude = updatedAmp,
                    confidence = (existing.confidence * 0.4f + fresh.confidence * 0.6f).coerceIn(0.1f, 1.0f),
                    releaseTimeMs = nowMs,
                    isSustained = false
                )
            } else {
                // Glissando check: see if a neighboring note key (1 semitone away) shifted continuously
                var glissandoFound = false
                for ((otherKey, otherNote) in activeTrackedNotes) {
                    if (abs(otherNote.midiNumber - fresh.midiNumber) == 1 && (nowMs - otherNote.releaseTimeMs) < 150L) {
                        // Continuous glissando movement detected: transfer unique note ID
                        activeTrackedNotes.remove(otherKey)
                        activeTrackedNotes[key] = fresh.copy(
                            uniqueNoteId = otherNote.uniqueNoteId,
                            releaseTimeMs = nowMs
                        )
                        glissandoFound = true
                        break
                    }
                }

                if (!glissandoFound) {
                    pitchHistoryMap[key] = mutableListOf(fresh.frequency)
                    activeTrackedNotes[key] = fresh.copy(releaseTimeMs = nowMs)
                }
            }
        }

        return activeTrackedNotes.values.sortedByDescending { it.amplitude }
    }

    /**
     * Returns current 12-bin Pitch Class Profile derived from accumulated note weights.
     */
    fun getPitchClassProfile(): FloatArray {
        val profile = FloatArray(12)
        for ((_, note) in activeTrackedNotes) {
            profile[note.pitchClass] += note.amplitude
        }
        var maxV = 0f
        for (v in profile) if (v > maxV) maxV = v
        if (maxV > 1e-6f) {
            for (i in 0 until 12) profile[i] /= maxV
        }
        return profile
    }

    /**
     * Resets note tracking state.
     */
    fun clear() {
        activeTrackedNotes.clear()
        pitchHistoryMap.clear()
    }
}
