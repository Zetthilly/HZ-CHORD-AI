package com.example.data

data class SendToDestination(
    val id: String,
    val name: String,
    val category: String,
    val iconEmoji: String,
    val targetSection: String, // "Home", "Analyzer", "Studio", "Library"
    val targetMode: String,    // Specific sub-mode or view focus
    val description: String
)

data class SendToTransferEvent(
    val id: String = System.currentTimeMillis().toString(),
    val sourceName: String,
    val destination: SendToDestination,
    val timestampMs: Long = System.currentTimeMillis(),
    val sharedMemoryPointer: String = "Unavailable",
    val duplicateFilesCreated: Int = 0,
    val statusMessage: String = "Zero-Copy Audio Stream Routed Successfully"
)

object UniversalSendToRegistry {
    val destinations: List<SendToDestination> = listOf(
        // GUITAR & CHORDS
        SendToDestination(
            id = "guitar_fretboard",
            name = "Guitar Fretboard Viewer",
            category = "Guitar & Chords",
            iconEmoji = "🎸",
            targetSection = "Home",
            targetMode = "fretboard",
            description = "Map harmonic notes & scale fingerings live on interactive guitar fretboard"
        ),
        SendToDestination(
            id = "chord_detection",
            name = "Chord Detection",
            category = "Guitar & Chords",
            iconEmoji = "🎼",
            targetSection = "Analyzer",
            targetMode = "chords",
            description = "Extract 12-chroma pitch class profiles & triad/7th chord progressions"
        ),
        SendToDestination(
            id = "arpeggio_intelligence",
            name = "Arpeggio Intelligence",
            category = "Guitar & Chords",
            iconEmoji = "🪄",
            targetSection = "Analyzer",
            targetMode = "arpeggio",
            description = "Sub-beat arpeggio pattern decoder & scale note analyzer"
        ),
        SendToDestination(
            id = "phrase_recognition",
            name = "Phrase Recognition",
            category = "Guitar & Chords",
            iconEmoji = "🎵",
            targetSection = "Library",
            targetMode = "phrase",
            description = "Extract African guitar licks, Sungura motifs & melodic phrasing"
        ),
        SendToDestination(
            id = "practice_center",
            name = "Practice Center & Speed Trainer",
            category = "Guitar & Chords",
            iconEmoji = "🎯",
            targetSection = "Home",
            targetMode = "practice",
            description = "Loop sections, slow down pitch-preserved audio & practice with metronome"
        ),

        // PIANO & KEYS
        SendToDestination(
            id = "piano_viewer",
            name = "Piano Viewer",
            category = "Piano & Keys",
            iconEmoji = "🎹",
            targetSection = "Home",
            targetMode = "piano",
            description = "Visualize polyphonic keyboard chord inversions & key highlighting"
        ),
        SendToDestination(
            id = "chord_analyzer",
            name = "Chord Analyzer & Harmonic Scope",
            category = "Piano & Keys",
            iconEmoji = "🔍",
            targetSection = "Analyzer",
            targetMode = "chords",
            description = "Deep harmonic frequency spectrum & key center correlation"
        ),
        SendToDestination(
            id = "midi_studio",
            name = "MIDI Studio & Polyphonic Export",
            category = "Piano & Keys",
            iconEmoji = "🎛️",
            targetSection = "Studio",
            targetMode = "midi",
            description = "Convert polyphonic audio directly into clean multi-track MIDI notes"
        ),

        // VOCALS & LYRICS
        SendToDestination(
            id = "lyrics_synchronizer",
            name = "Lyrics Synchronizer",
            category = "Vocals & Lyrics",
            iconEmoji = "📝",
            targetSection = "Studio",
            targetMode = "lyrics",
            description = "Sync timestamped lyrics line-by-line with vocal stem playback"
        ),
        SendToDestination(
            id = "karaoke_mode",
            name = "Karaoke Mode",
            category = "Vocals & Lyrics",
            iconEmoji = "🎤",
            targetSection = "Studio",
            targetMode = "karaoke",
            description = "Mute or attenuate vocal stem with real-time pitch guide & lyric teleprompter"
        ),

        // STUDIO & DSP
        SendToDestination(
            id = "stem_mixer",
            name = "Professional Stem Mixer",
            category = "Studio & DSP",
            iconEmoji = "🎚️",
            targetSection = "Studio",
            targetMode = "stem_mixer",
            description = "Multi-channel volume, solo, mute & zero-copy routing control"
        ),
        SendToDestination(
            id = "bpm_key_studio",
            name = "BPM & Key Studio",
            category = "Studio & DSP",
            iconEmoji = "⏱️",
            targetSection = "Analyzer",
            targetMode = "bpm_key",
            description = "Spectral flux transient onset detection & pitch transposition"
        )
    )

    fun findDestination(id: String): SendToDestination {
        return destinations.find { it.id == id } ?: destinations.first()
    }
}
