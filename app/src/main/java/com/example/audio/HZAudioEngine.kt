package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.ImportedAudioMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Player state machine representing real playback lifecycle.
 */
enum class AudioPlayerState {
    EMPTY,      // No project / audio loaded
    IDLE,       // No audio loaded
    READY,      // Audio loaded and prepared
    PLAYING,    // Real audio output active
    PAUSED,     // Playback stopped but position saved
    STOPPED,    // Playback stopped and resources released
    ERROR       // Display error message
}

/**
 * Diagnostics information model for real-time Audio Diagnostics Panel.
 */
data class AudioDiagnosticsInfo(
    val isAudioLoaded: Boolean = false,
    val currentFileName: String = "None",
    val decoderStatus: String = "Idle",
    val sampleRateHz: Int = 0,
    val channels: String = "Unknown",
    val playerState: AudioPlayerState = AudioPlayerState.IDLE,
    val outputDevice: String = "Android Media3 ExoPlayer (Speaker / Headphone / Bluetooth)",
    val bufferedMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * HZAudioEngine is the single, centralized audio engine for HZ CHORD AI.
 * Built using Android Media3 ExoPlayer with Real Audio Source Validation,
 * Playback State Management (IDLE, READY, PLAYING, PAUSED, STOPPED, ERROR),
 * and Audio Safety Rules (strict source validation before any audio output is triggered).
 */
class HZAudioEngine(
    private val context: Context
) {
    private val TAG = "HZAudioEngine"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var exoPlayer: ExoPlayer? = null

    val stemEngine = RealStemSeparationEngine(context)
    val harmonicTranscriber = RealHarmonicTranscriber(context)

    // Player State Machine (EMPTY, IDLE, READY, PLAYING, PAUSED, STOPPED, ERROR)
    private val _playerState = MutableStateFlow(AudioPlayerState.EMPTY)
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1.0f)
    val speedMultiplier: StateFlow<Float> = _speedMultiplier.asStateFlow()

    private val _pitchShiftSemitones = MutableStateFlow(0)
    val pitchShiftSemitones: StateFlow<Int> = _pitchShiftSemitones.asStateFlow()

    // A-B Repeat Loop Points
    private val _isLoopEnabled = MutableStateFlow(false)
    val isLoopEnabled: StateFlow<Boolean> = _isLoopEnabled.asStateFlow()

    private val _loopStartMs = MutableStateFlow(0L)
    val loopStartMs: StateFlow<Long> = _loopStartMs.asStateFlow()

    private val _loopEndMs = MutableStateFlow(0L)
    val loopEndMs: StateFlow<Long> = _loopEndMs.asStateFlow()

    fun getActivePcmSamples(): FloatArray? = activePcmSamples
    private val _loadedMetadata = MutableStateFlow<ImportedAudioMetadata?>(null)
    val loadedMetadata: StateFlow<ImportedAudioMetadata?> = _loadedMetadata.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Diagnostics State
    private val _diagnostics = MutableStateFlow(AudioDiagnosticsInfo())
    val diagnostics: StateFlow<AudioDiagnosticsInfo> = _diagnostics.asStateFlow()

    // Decoded PCM buffer for analysis
    private var activePcmSamples: FloatArray? = null
    private var positionTickerJob: Job? = null

    init {
        updateDiagnostics()
        startPositionTicker()
    }

    /**
     * Strict Audio Source Validation & Media3 ExoPlayer Loader
     */
    fun loadAudioUri(uri: Uri, metadata: ImportedAudioMetadata? = null): Boolean {
        stop()
        _errorMessage.value = null

        return try {
            // 1. Strict Audio Source Validation
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists() || file.length() <= 0) {
                    setNoAudioLoadedError("Audio file does not exist or is empty.")
                    return false
                }
            }

            val finalMeta = metadata ?: RealAudioDecoder.extractMetadataAndWaveform(context, uri)
            if (finalMeta.durationMs <= 0) {
                setNoAudioLoadedError("No audio loaded. Import an audio file first.")
                return false
            }

            _loadedMetadata.value = finalMeta
            _durationMs.value = finalMeta.durationMs

            // 2. Initialize Media3 ExoPlayer instance
            releaseExoPlayer()

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val player = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true) // Audio Focus enabled
                .build()

            player.setMediaItem(MediaItem.fromUri(uri))
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _playerState.value = if (player.isPlaying) AudioPlayerState.PLAYING else AudioPlayerState.READY
                            updateDiagnostics()
                        }
                        Player.STATE_ENDED -> {
                            seekTo(0)
                            pause()
                            _playerState.value = AudioPlayerState.READY
                            updateDiagnostics()
                        }
                        Player.STATE_BUFFERING -> {
                            updateDiagnostics()
                        }
                        Player.STATE_IDLE -> {
                            _playerState.value = AudioPlayerState.IDLE
                            updateDiagnostics()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    _playerState.value = if (isPlaying) AudioPlayerState.PLAYING else AudioPlayerState.PAUSED
                    updateDiagnostics()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "Media3 ExoPlayer Error: ${error.message}")
                    _playerState.value = AudioPlayerState.ERROR
                    _errorMessage.value = "Decoder Error: ${error.localizedMessage ?: "Failed to play audio source"}"
                    updateDiagnostics()
                }
            })

            player.prepare()
            exoPlayer = player
            _playerState.value = AudioPlayerState.READY
            applyPlaybackParams()
            updateDiagnostics()

            // Decode PCM audio samples in background for real harmonic analysis & stem separation
            scope.launch(Dispatchers.IO) {
                val decoded = RealAudioDecoder.decodePcmAudio(context, uri)
                if (decoded != null) {
                    activePcmSamples = decoded.pcmSamples
                }
            }

            Log.d(TAG, "Successfully prepared Media3 ExoPlayer for: ${finalMeta.fileName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load audio file: ${e.message}")
            setNoAudioLoadedError("Could not load audio file: ${e.localizedMessage ?: "Format not supported"}")
            false
        }
    }

    private fun setNoAudioLoadedError(msg: String) {
        _playerState.value = AudioPlayerState.EMPTY
        _errorMessage.value = msg
        _loadedMetadata.value = null
        _durationMs.value = 0L
        _currentPositionMs.value = 0L
        _isPlaying.value = false
        updateDiagnostics()
    }

    fun play(): Boolean {
        val meta = _loadedMetadata.value
        val player = exoPlayer

        // Enforce strict source validation before audio output
        if (player == null || meta == null || _playerState.value == AudioPlayerState.EMPTY || _playerState.value == AudioPlayerState.IDLE) {
            setNoAudioLoadedError("No audio loaded. Please import or record an audio file.")
            return false
        }

        return try {
            player.play()
            _isPlaying.value = true
            _playerState.value = AudioPlayerState.PLAYING
            _errorMessage.value = null
            updateDiagnostics()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback: ${e.message}")
            _playerState.value = AudioPlayerState.ERROR
            _errorMessage.value = "Playback error: ${e.localizedMessage}"
            updateDiagnostics()
            false
        }
    }

    fun pause() {
        try {
            exoPlayer?.pause()
            _isPlaying.value = false
            if (_playerState.value == AudioPlayerState.PLAYING) {
                _playerState.value = AudioPlayerState.PAUSED
            }
            updateDiagnostics()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback: ${e.message}")
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun stop() {
        try {
            exoPlayer?.pause()
            exoPlayer?.seekTo(0)
            _isPlaying.value = false
            _currentPositionMs.value = 0L
            if (_playerState.value != AudioPlayerState.EMPTY && _playerState.value != AudioPlayerState.IDLE) {
                _playerState.value = AudioPlayerState.STOPPED
            }
            updateDiagnostics()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback: ${e.message}")
        }
    }

    /**
     * Clears and releases all audio project resources, releases Media3 ExoPlayer resources,
     * resets playback position, duration, metadata, and all state variables to EMPTY,
     * ensuring no audio playback persists after closing or switching a project.
     */
    fun clearProjectResources() {
        releaseExoPlayer()
        _loadedMetadata.value = null
        _durationMs.value = 0L
        _currentPositionMs.value = 0L
        _isPlaying.value = false
        activePcmSamples = null
        _errorMessage.value = null
        _isLoopEnabled.value = false
        _loopStartMs.value = 0L
        _loopEndMs.value = 0L
        _speedMultiplier.value = 1.0f
        _pitchShiftSemitones.value = 0
        _playerState.value = AudioPlayerState.EMPTY
        updateDiagnostics()
    }

    /**
     * Closes the current audio project, releases audio player resources,
     * resets playback position and duration to 0:00, clears metadata,
     * and sets state back to EMPTY.
     */
    fun closeProject() {
        clearProjectResources()
    }

    fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        _currentPositionMs.value = targetMs
        try {
            exoPlayer?.seekTo(targetMs)
            updateDiagnostics()
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking: ${e.message}")
        }
    }

    fun rewind(deltaMs: Long = 5000L) {
        seekTo(_currentPositionMs.value - deltaMs)
    }

    fun fastForward(deltaMs: Long = 5000L) {
        seekTo(_currentPositionMs.value + deltaMs)
    }

    fun setSpeedMultiplier(multiplier: Float) {
        val speed = multiplier.coerceIn(0.25f, 2.5f)
        _speedMultiplier.value = speed
        applyPlaybackParams()
    }

    fun setPitchShiftSemitones(semitones: Int) {
        val pitch = semitones.coerceIn(-12, 12)
        _pitchShiftSemitones.value = pitch
        applyPlaybackParams()
    }

    private fun applyPlaybackParams() {
        try {
            val speed = _speedMultiplier.value
            val pitchFactor = Math.pow(2.0, _pitchShiftSemitones.value / 12.0).toFloat()
            exoPlayer?.playbackParameters = PlaybackParameters(speed, pitchFactor)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set PlaybackParameters: ${e.message}")
        }
    }

    fun setLoopPoints(startMs: Long, endMs: Long) {
        val start = startMs.coerceIn(0L, _durationMs.value)
        val end = endMs.coerceIn(start + 500L, _durationMs.value.coerceAtLeast(start + 1000L))
        _loopStartMs.value = start
        _loopEndMs.value = end
        _isLoopEnabled.value = true
    }

    fun toggleLoopEnabled() {
        _isLoopEnabled.value = !_isLoopEnabled.value
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                try {
                    val player = exoPlayer
                    if (player != null && _isPlaying.value) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        _currentPositionMs.value = pos

                        // A-B Repeat Loop Check
                        if (_isLoopEnabled.value && _loopEndMs.value > _loopStartMs.value) {
                            if (pos >= _loopEndMs.value) {
                                seekTo(_loopStartMs.value)
                            }
                        }

                        // Real Harmonic Analysis on raw PCM frame slice
                        activePcmSamples?.let { samples ->
                            if (samples.isNotEmpty()) {
                                val durationMs = _durationMs.value.coerceAtLeast(1L)
                                val fraction = (pos.toDouble() / durationMs).coerceIn(0.0, 1.0)
                                val startIndex = (fraction * (samples.size - 2048)).toInt().coerceIn(0, samples.size - 2048)
                                val pcmSlice = samples.copyOfRange(startIndex, startIndex + 2048)
                                harmonicTranscriber.analyzePcmBuffer(pcmSlice)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignored during state transitions
                }
                delay(33) // ~30 fps playhead update
            }
        }
    }

    private fun updateDiagnostics() {
        val meta = _loadedMetadata.value
        val player = exoPlayer

        val bufferedMs = player?.bufferedPosition ?: 0L
        val isLoaded = meta != null && _playerState.value != AudioPlayerState.IDLE && _playerState.value != AudioPlayerState.EMPTY

        _diagnostics.value = AudioDiagnosticsInfo(
            isAudioLoaded = isLoaded,
            currentFileName = meta?.fileName ?: "None",
            decoderStatus = when (_playerState.value) {
                AudioPlayerState.PLAYING -> "Media3 ExoPlayer Decoding & Playing"
                AudioPlayerState.READY -> "Media3 ExoPlayer Ready (Loaded)"
                AudioPlayerState.PAUSED -> "Media3 ExoPlayer Paused"
                AudioPlayerState.STOPPED -> "Media3 ExoPlayer Stopped"
                AudioPlayerState.ERROR -> "Media3 Decoder Error"
                AudioPlayerState.IDLE, AudioPlayerState.EMPTY -> "No Audio Loaded"
            },
            sampleRateHz = meta?.sampleRateHz?.substringBefore(" ")?.toDoubleOrNull()?.let { (it * 1000).toInt() } ?: 0,
            channels = meta?.channels ?: "Unknown",
            playerState = _playerState.value,
            outputDevice = "Android Media3 ExoPlayer (Speaker / Headphone / Bluetooth)",
            bufferedMs = bufferedMs,
            errorMessage = _errorMessage.value
        )
    }

    private fun releaseExoPlayer() {
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing exoPlayer: ${e.message}")
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
        updateDiagnostics()
    }

    fun release() {
        positionTickerJob?.cancel()
        releaseExoPlayer()
        stemEngine.release()
        harmonicTranscriber.close()
        scope.cancel()
    }
}

/**
 * Typealias for backward compatibility with MasterAudioEngine references.
 */
typealias MasterAudioEngine = HZAudioEngine
