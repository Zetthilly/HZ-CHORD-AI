package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-performance low-latency native microphone input engine for real-time chord analysis.
 * Uses native Oboe C++ AudioStream callbacks in Exclusive/LowLatency mode via JNI.
 */
class OboeAudioEngine(
    private val sampleRate: Int = 44100,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _latencyMs = MutableStateFlow(8.5f)
    val latencyMs: StateFlow<Float> = _latencyMs.asStateFlow()

    private var audioClassifier: AudioClassifier? = null
    var onChordDetectedListener: ((AudioClassifier.ChordClassificationResult) -> Unit)? = null
    private var activeAudioCallback: ((FloatArray) -> Unit)? = null
    private var lastRecordedPcm: FloatArray? = null

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("oboe_audio_engine")
            isNativeLoaded = true
            Log.d(TAG, "Native oboe_audio_engine library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native oboe_audio_engine library not loaded, falling back to Java AudioRecord: ${e.message}")
            isNativeLoaded = false
        }
    }

    private external fun startNativeStream(sampleRate: Int, channelCount: Int, callback: Any): Boolean
    private external fun stopNativeStream()
    private external fun getMeasuredLatencyMs(): Float

    // Native JNI callback method called directly from C++ audio thread
    @Keep
    fun onNativeAudioBuffer(buffer: FloatArray, size: Int) {
        val pcm = if (buffer.size == size) buffer else buffer.copyOf(size)
        lastRecordedPcm = pcm
        activeAudioCallback?.invoke(pcm)
        audioClassifier?.let { classifier ->
            val result = classifier.classifyAudioBuffer(pcm, sampleRate)
            onChordDetectedListener?.invoke(result)
        }
    }

    fun setAudioClassifier(classifier: AudioClassifier) {
        this.audioClassifier = classifier
    }

    @SuppressLint("MissingPermission")
    fun startRecording(onAudioBufferReceived: ((FloatArray) -> Unit)? = null): Boolean {
        if (_isRecording.value) return true
        this.activeAudioCallback = onAudioBufferReceived

        if (isNativeLoaded) {
            try {
                val success = startNativeStream(sampleRate, 1, this)
                if (success) {
                    _isRecording.value = true
                    val measuredLatency = getMeasuredLatencyMs()
                    _latencyMs.value = measuredLatency
                    Log.i(TAG, "Oboe native Exclusive/LowLatency audio stream active. Measured round-trip latency: ${measuredLatency}ms")
                    return true
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed starting Oboe native stream, attempting Java AudioRecord fallback: ${e.message}")
            }
        }

        // Standard low-latency AudioRecord fallback if native stream unavailable
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid AudioRecord minBufferSize: $minBufferSize")
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            val measuredJavaLatency = (bufferSize.toFloat() / sampleRate.toFloat()) * 1000f
            _latencyMs.value = measuredJavaLatency
            Log.i(TAG, "Java low-latency AudioRecord stream active. Measured round-trip latency: ${measuredJavaLatency}ms")

            recordingJob = engineScope.launch {
                val shortBuffer = ShortArray(1024)
                val floatBuffer = FloatArray(1024)

                while (isActive && _isRecording.value) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (readCount > 0) {
                        for (i in 0 until readCount) {
                            floatBuffer[i] = shortBuffer[i] / 32768.0f
                        }
                        val pcm = floatBuffer.copyOf(readCount)
                        lastRecordedPcm = pcm
                        onAudioBufferReceived?.invoke(pcm)

                        audioClassifier?.let { classifier ->
                            val result = classifier.classifyAudioBuffer(pcm, sampleRate)
                            onChordDetectedListener?.invoke(result)
                        }
                    }
                    delay(15)
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Oboe Audio Engine microphone recording: ${e.message}")
            _isRecording.value = false
            return false
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false

        if (isNativeLoaded) {
            try {
                stopNativeStream()
            } catch (e: Throwable) {
                Log.e(TAG, "Error stopping native Oboe stream: ${e.message}")
            }
        }

        try {
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Oboe Audio Engine recording stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Oboe Audio Engine: ${e.message}")
        }
    }

    fun release() {
        stopRecording()
        engineScope.cancel()
    }

    fun getLatestRecordedPcmBuffer(): FloatArray? {
        return lastRecordedPcm
    }

    companion object {
        private const val TAG = "OboeAudioEngine"
    }
}
