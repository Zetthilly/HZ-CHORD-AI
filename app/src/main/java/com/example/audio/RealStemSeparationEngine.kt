package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.data.StemChannelData
import com.example.data.StemMixerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

/**
 * RealStemSeparationEngine performs chunked STFT & multi-band spectral stem separation into 7 stems:
 * Vocals, Drums, Bass, Guitar, Piano, Strings, and Other.
 * Supports chunking with crossfading for long audio, quality mode trade-offs, and true PCM retrieval.
 */
class RealStemSeparationEngine(
    private val context: Context? = null
) {
    private val TAG = "RealStemSeparationEngine"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _stemMixerState = MutableStateFlow(StemMixerState())
    val stemMixerState: StateFlow<StemMixerState> = _stemMixerState.asStateFlow()

    private val _isSeparating = MutableStateFlow(false)
    val isSeparating: StateFlow<Boolean> = _isSeparating.asStateFlow()

    private val _separationProgress = MutableStateFlow(0.0f)
    val separationProgress: StateFlow<Float> = _separationProgress.asStateFlow()

    private val stemBuffers = java.util.Collections.synchronizedMap(mutableMapOf<String, FloatArray>())
    private var playbackJob: Job? = null

    init {
        initializeStemChannels()
    }

    private fun initializeStemChannels() {
        val channels = listOf(
            StemChannelData(id = "vocals", name = "Vocals", iconEmoji = "🎤", colorHex = 0xFF10B981, frequencyRange = "300Hz - 4kHz (Primary Stem)"),
            StemChannelData(id = "drums", name = "Drums", iconEmoji = "🥁", colorHex = 0xFFFF5252, frequencyRange = "20Hz - 16kHz (Primary Stem)"),
            StemChannelData(id = "bass", name = "Bass", iconEmoji = "🎸", colorHex = 0xFFE040FB, frequencyRange = "40Hz - 250Hz (Primary Stem)"),
            StemChannelData(id = "guitar", name = "Guitar", iconEmoji = "🎸", colorHex = 0xFF00E5FF, frequencyRange = "250Hz - 6.5kHz (Estimated from Other)"),
            StemChannelData(id = "piano", name = "Piano", iconEmoji = "🎹", colorHex = 0xFFFFD700, frequencyRange = "100Hz - 8kHz (Estimated from Other)"),
            StemChannelData(id = "strings", name = "Strings", iconEmoji = "🎻", colorHex = 0xFFFF9800, frequencyRange = "2kHz - 14kHz (Estimated from Other)"),
            StemChannelData(id = "other", name = "Other", iconEmoji = "🎛️", colorHex = 0xFF9E9E9E, frequencyRange = "Full Spectrum (Primary Stem)")
        )
        _stemMixerState.value = StemMixerState(channels = channels)
    }

    /**
     * Returns true PCM audio samples for a specific stem ID, if available.
     */
    fun getStemBuffer(stemId: String): FloatArray? {
        return stemBuffers[stemId.lowercase()]
    }

    /**
     * Executes chunked STFT & multi-band spectral separation on master PCM audio.
     */
    suspend fun separateMasterPcm(
        masterSamples: FloatArray,
        sampleRate: Int = 44100,
        qualityMode: String = "Balanced"
    ): Boolean = withContext(Dispatchers.Default) {
        if (_isSeparating.value) return@withContext false
        if (masterSamples.isEmpty()) {
            Log.w(TAG, "Cannot perform stem separation: Master PCM audio buffer is empty.")
            _isSeparating.value = false
            return@withContext false
        }
        _isSeparating.value = true
        _separationProgress.value = 0.02f

        try {
            val numSamples = masterSamples.size
            val safeMaster = masterSamples

            // Setup chunking parameters (~8 second chunks with ~1 second overlap)
            val chunkSize = (sampleRate * 8).coerceAtMost(numSamples)
            val overlapSize = (sampleRate * 1).coerceAtMost(chunkSize / 2)
            val stepSize = chunkSize - overlapSize

            val totalChunks = ((numSamples - overlapSize).toFloat() / stepSize.toFloat()).toInt().coerceAtLeast(1)

            val vocalsOut = FloatArray(numSamples)
            val drumsOut = FloatArray(numSamples)
            val bassOut = FloatArray(numSamples)
            val otherOut = FloatArray(numSamples)

            val windowSize = when (qualityMode.lowercase()) {
                "fast" -> 512
                "high quality", "studio", "pro" -> 2048
                else -> 1024 // Balanced
            }

            var chunkCount = 0
            var offset = 0
            while (offset < numSamples) {
                val currentChunkLength = (chunkSize).coerceAtMost(numSamples - offset)
                val chunkInput = FloatArray(currentChunkLength) { i -> safeMaster[offset + i] }

                val (vChunk, dChunk, bChunk, oChunk) = processChunkSTFT(chunkInput, sampleRate, windowSize)

                // Crossfade overlap writing into target arrays
                for (i in 0 until currentChunkLength) {
                    val targetIdx = offset + i
                    if (targetIdx >= numSamples) break

                    var weight = 1.0f
                    if (i < overlapSize && offset > 0) {
                        weight = i.toFloat() / overlapSize.toFloat()
                    } else if (i >= currentChunkLength - overlapSize && (offset + currentChunkLength) < numSamples) {
                        weight = (currentChunkLength - i).toFloat() / overlapSize.toFloat()
                    }

                    if (weight < 1.0f && offset > 0) {
                        vocalsOut[targetIdx] = vocalsOut[targetIdx] * (1.0f - weight) + vChunk[i] * weight
                        drumsOut[targetIdx] = drumsOut[targetIdx] * (1.0f - weight) + dChunk[i] * weight
                        bassOut[targetIdx] = bassOut[targetIdx] * (1.0f - weight) + bChunk[i] * weight
                        otherOut[targetIdx] = otherOut[targetIdx] * (1.0f - weight) + oChunk[i] * weight
                    } else {
                        vocalsOut[targetIdx] = vChunk[i]
                        drumsOut[targetIdx] = dChunk[i]
                        bassOut[targetIdx] = bChunk[i]
                        otherOut[targetIdx] = oChunk[i]
                    }
                }

                chunkCount++
                _separationProgress.value = (0.05f + 0.85f * (chunkCount.toFloat() / totalChunks.toFloat())).coerceAtMost(0.90f)

                offset += stepSize
                if (offset >= numSamples - overlapSize) break
            }

            // Derive lower-confidence sub-stems (Guitar, Piano, Strings) from Other stem
            _separationProgress.value = 0.92f
            val guitarOut = filterBandpass(otherOut, sampleRate, 250f, 4500f)
            val pianoOut = filterBandpass(otherOut, sampleRate, 100f, 5000f)
            val stringsOut = filterHighpass(otherOut, sampleRate, 2000f)

            stemBuffers["vocals"] = vocalsOut
            stemBuffers["drums"] = drumsOut
            stemBuffers["bass"] = bassOut
            stemBuffers["other"] = otherOut
            stemBuffers["guitar"] = guitarOut
            stemBuffers["piano"] = pianoOut
            stemBuffers["strings"] = stringsOut

            _separationProgress.value = 1.0f
            _isSeparating.value = false
            Log.d(TAG, "Completed real HPSS + frequency-band stem separation ($qualityMode) into 7 stems.")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Chunked HPSS stem separation error: ${e.message}", e)
            _isSeparating.value = false
            return@withContext false
        }
    }

    private fun processChunkSTFT(
        chunkInput: FloatArray,
        sampleRate: Int,
        windowSize: Int
    ): QuadrupleStems {
        val n = chunkInput.size

        // 1. Run real Harmonic-Percussive Source Separation (HPSS)
        val hpss = com.example.audio.dsp.HarmonicPercussiveSeparator.separate(
            pcmInput = chunkInput,
            windowSize = windowSize,
            hopSize = (windowSize / 4).coerceAtLeast(128)
        )

        // Drums are directly the isolated percussive component
        val drums = hpss.percussiveWaveform
        val harmonic = hpss.harmonicWaveform

        // 2. Frequency-band separation applied to drum-free harmonic waveform
        val vocals = filterBandpass(harmonic, sampleRate, 300f, 3400f)
        val bass = filterLowpass(harmonic, sampleRate, 250f)

        val other = FloatArray(n) { i ->
            val harmSample = if (i < harmonic.size) harmonic[i] else 0f
            val vocSample = if (i < vocals.size) vocals[i] else 0f
            val bassSample = if (i < bass.size) bass[i] else 0f
            (harmSample - (vocSample + bassSample) * 0.4f).coerceIn(-1.0f, 1.0f)
        }
        return QuadrupleStems(vocals, drums, bass, other)
    }

    private data class QuadrupleStems(
        val vocals: FloatArray,
        val drums: FloatArray,
        val bass: FloatArray,
        val other: FloatArray
    )

    // DSP Filter utilities
    private fun filterLowpass(input: FloatArray, sampleRate: Int, cutoffHz: Float): FloatArray {
        val output = FloatArray(input.size)
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffHz)
        val dt = 1.0f / sampleRate
        val alpha = dt / (rc + dt)
        var last = 0.0f
        for (i in input.indices) {
            last += alpha * (input[i] - last)
            output[i] = last
        }
        return output
    }

    private fun filterHighpass(input: FloatArray, sampleRate: Int, cutoffHz: Float): FloatArray {
        val output = FloatArray(input.size)
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffHz)
        val dt = 1.0f / sampleRate
        val alpha = rc / (rc + dt)
        var lastInput = 0.0f
        var lastOutput = 0.0f
        for (i in input.indices) {
            val currOut = alpha * (lastOutput + input[i] - lastInput)
            output[i] = currOut
            lastInput = input[i]
            lastOutput = currOut
        }
        return output
    }

    private fun filterBandpass(input: FloatArray, sampleRate: Int, lowHz: Float, highHz: Float): FloatArray {
        val lp = filterLowpass(input, sampleRate, highHz)
        return filterHighpass(lp, sampleRate, lowHz)
    }

    private fun filterDrums(input: FloatArray, sampleRate: Int): FloatArray {
        val kick = filterLowpass(input, sampleRate, 120f)
        val snareCymbal = filterHighpass(input, sampleRate, 3800f)
        return FloatArray(input.size) { i -> (kick[i] * 1.1f + snareCymbal[i] * 0.9f).coerceIn(-1f, 1f) }
    }

    // Mixer Control Actions
    fun toggleMute(channelId: String) {
        val current = _stemMixerState.value
        val updated = current.channels.map { ch ->
            if (ch.id == channelId) ch.copy(isMuted = !ch.isMuted) else ch
        }
        _stemMixerState.value = current.copy(channels = updated)
    }

    fun toggleSolo(channelId: String) {
        val current = _stemMixerState.value
        val updated = current.channels.map { ch ->
            if (ch.id == channelId) ch.copy(isSoloed = !ch.isSoloed) else ch
        }
        _stemMixerState.value = current.copy(channels = updated)
    }

    fun setChannelVolume(channelId: String, volume: Float) {
        val current = _stemMixerState.value
        val updated = current.channels.map { ch ->
            if (ch.id == channelId) ch.copy(volume = volume.coerceIn(0f, 1.5f)) else ch
        }
        _stemMixerState.value = current.copy(channels = updated)
    }

    fun setMasterVolume(volume: Float) {
        val current = _stemMixerState.value
        _stemMixerState.value = current.copy(masterVolume = volume.coerceIn(0f, 1.5f))
    }

    fun playOnlyStem(stemId: String) {
        val current = _stemMixerState.value
        val updated = current.channels.map { ch ->
            ch.copy(isSoloed = (ch.id == stemId), isMuted = false)
        }
        _stemMixerState.value = current.copy(channels = updated)
    }

    fun resetMixer() {
        val current = _stemMixerState.value
        val updated = current.channels.map { ch ->
            ch.copy(isSoloed = false, isMuted = false, volume = 1.0f)
        }
        _stemMixerState.value = current.copy(channels = updated, masterVolume = 1.0f)
    }

    fun updateStemProgress(progress: Float) {
        _separationProgress.value = progress
    }

    fun release() {
        playbackJob?.cancel()
        scope.cancel()
    }
}
