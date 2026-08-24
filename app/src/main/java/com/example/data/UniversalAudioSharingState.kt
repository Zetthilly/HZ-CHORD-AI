package com.example.data

data class SharingModuleStage(
    val id: String,
    val name: String,
    val description: String,
    val isBypassed: Boolean = false,
    val syncLatencyMs: Long = 0L,
    val statusMessage: String = "Idle",
    val gainDb: Float = 0.0f
)

data class UniversalAudioSharingState(
    val activeProjectId: String = "",
    val activeProjectTitle: String = "No active project",
    val audioSourceFileName: String = "",
    val audioDurationMs: Long = 0L,
    val currentPlayheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val sampleRateHz: Int = 0,
    val channels: Int = 0,
    val bitrateKbps: Int = 0,
    val sharedBufferMemoryRef: String = "Not active",
    val duplicateFilesCreated: Int = 0,
    val oboeBackendName: String = "Not active",
    val oboeLatencyMs: Float = 0f,
    val oboeEngineRunning: Boolean = false,
    val modulesChain: List<SharingModuleStage> = listOf(
        SharingModuleStage("import", "Import", "Ingest internal/external storage audio & metadata without file dups"),
        SharingModuleStage("audio_player", "Audio Player", "Synchronized multi-rate playback engine"),
        SharingModuleStage("chord_detection", "Chord Detection", "Harmonic pitch-class profile correlation & triad inference"),
        SharingModuleStage("arpeggio_intelligence", "Arpeggio Intelligence", "Sub-beat arpeggio pattern & scale note decoder"),
        SharingModuleStage("phrase_recognition", "Phrase Recognition", "African Guitar, Lick & motif phrasing extractor"),
        SharingModuleStage("bpm_studio", "BPM Studio", "Spectral flux transient onset & tempo preservation lock"),
        SharingModuleStage("key_detection", "Key Detection", "Global 12-chroma tonic-dominant key signature solver"),
        SharingModuleStage("stem_separation", "Stem Separation", "Vocal, Melody, Bass, Drums stem isolation matrix"),
        SharingModuleStage("audio_restoration", "Audio Restoration", "Spectral noise reduction, hum filter & clipping repair"),
        SharingModuleStage("recorder", "Recorder", "Zero-latency overdub & reference audio monitor layer"),
        SharingModuleStage("visualization", "Visualization", "Realtime FFT spectrum, waveform & 3D chroma scope"),
        SharingModuleStage("export", "Export", "Single-pass non-destructive bounce & project archive")
    )
)
