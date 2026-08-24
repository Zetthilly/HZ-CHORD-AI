package com.example.audio.dsp

import kotlin.math.abs
import kotlin.math.cos

/**
 * High-performance audio signal preprocessor implementing standard DSP stages:
 * 1. 48kHz Mono Conversion
 * 2. DC Offset Removal
 * 3. 20Hz IIR High-Pass Filtering
 * 4. Automatic Gain Normalisation
 * 5. Hann Windowing
 * 6. 75% Overlap Frame Extraction
 */
object AudioPreprocessor {

    data class ProcessedFrame(
        val windowedPcm: FloatArray,
        val rawFrame: FloatArray,
        val frameIndex: Int,
        val timeStampMs: Long
    )

    /**
     * Converts input sample buffer to 48kHz Mono PCM float array.
     */
    fun convertTo48kHzMono(input: FloatArray, sampleRate: Int, channels: Int = 1): FloatArray {
        var mono = if (channels > 1) {
            val monoSize = input.size / channels
            FloatArray(monoSize) { i ->
                var sum = 0f
                for (c in 0 until channels) {
                    sum += input[i * channels + c]
                }
                sum / channels
            }
        } else {
            input
        }

        if (sampleRate == 48000) return mono

        // Linear interpolation resampling to 48000Hz
        val ratio = 48000.0 / sampleRate
        val newSize = (mono.size * ratio).toInt()
        val resampled = FloatArray(newSize)
        for (i in 0 until newSize) {
            val srcIdx = i / ratio
            val i0 = srcIdx.toInt().coerceIn(0, mono.size - 1)
            val i1 = (i0 + 1).coerceIn(0, mono.size - 1)
            val frac = (srcIdx - i0).toFloat()
            resampled[i] = mono[i0] * (1f - frac) + mono[i1] * frac
        }
        return resampled
    }

    /**
     * Removes DC offset (mean value subtraction).
     */
    fun removeDcOffset(pcm: FloatArray): FloatArray {
        if (pcm.isEmpty()) return pcm
        var sum = 0.0
        for (v in pcm) sum += v
        val mean = (sum / pcm.size).toFloat()
        val output = FloatArray(pcm.size)
        for (i in pcm.indices) {
            output[i] = pcm[i] - mean
        }
        return output
    }

    /**
     * Applies 20Hz 1-pole High-Pass IIR filter.
     * Cutoff fc = 20Hz, fs = 48000Hz.
     */
    fun apply20HzHighPassFilter(pcm: FloatArray, sampleRate: Int = 48000): FloatArray {
        if (pcm.isEmpty()) return pcm
        val output = FloatArray(pcm.size)
        val fc = 20.0
        val dt = 1.0 / sampleRate
        val rc = 1.0 / (2.0 * Math.PI * fc)
        val alpha = (rc / (rc + dt)).toFloat()

        output[0] = pcm[0]
        for (i in 1 until pcm.size) {
            output[i] = alpha * (output[i - 1] + pcm[i] - pcm[i - 1])
        }
        return output
    }

    /**
     * Applies 20Hz High-Pass filter and 50Hz/60Hz notch filter for electrical hum rejection.
     */
    fun applyFiltersAndNotch(pcm: FloatArray, sampleRate: Int = 48000): FloatArray {
        if (pcm.isEmpty()) return pcm
        val hpFiltered = apply20HzHighPassFilter(pcm, sampleRate)
        // 50Hz notch filter (2nd order IIR notch)
        val notch50 = applyIirNotch(hpFiltered, 50.0, sampleRate)
        // 60Hz notch filter (2nd order IIR notch)
        return applyIirNotch(notch50, 60.0, sampleRate)
    }

    /**
     * 2nd-order IIR Notch Filter to attenuate specific interference frequency (50Hz or 60Hz hum).
     */
    private fun applyIirNotch(pcm: FloatArray, notchFreq: Double, sampleRate: Int): FloatArray {
        val w0 = 2.0 * Math.PI * notchFreq / sampleRate
        val bw = 4.0 // 4Hz bandwidth
        val alpha = Math.sin(w0) * Math.sinh(Math.log(2.0) / 2.0 * bw * w0 / Math.sin(w0))
        val b0 = 1.0
        val b1 = -2.0 * Math.cos(w0)
        val b2 = 1.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * Math.cos(w0)
        val a2 = 1.0 - alpha

        val output = FloatArray(pcm.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in pcm.indices) {
            val x0 = pcm[i].toDouble()
            val y0 = (b0 / a0) * x0 + (b1 / a0) * x1 + (b2 / a0) * x2 - (a1 / a0) * y1 - (a2 / a0) * y2
            output[i] = y0.toFloat()
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }
        return output
    }

    /**
     * Normalizes peak amplitude to target peak level (default 0.8f).
     */
    fun normalizeGain(pcm: FloatArray, targetPeak: Float = 0.8f): FloatArray {
        if (pcm.isEmpty()) return pcm
        var maxVal = 0f
        for (v in pcm) {
            val absV = abs(v)
            if (absV > maxVal) maxVal = absV
        }
        if (maxVal < 1e-5f) return pcm.clone()
        val scale = targetPeak / maxVal
        val normalized = FloatArray(pcm.size)
        for (i in pcm.indices) {
            normalized[i] = pcm[i] * scale
        }
        return normalized
    }

    /**
     * Executes complete preprocessing pipeline on raw PCM input:
     * 48kHz Mono -> DC Offset Removal -> 20Hz HPF + 50/60Hz Hum Notch -> Gain Normalisation.
     */
    fun preprocessSignal(inputPcm: FloatArray, sampleRate: Int = 44100, channels: Int = 1): FloatArray {
        if (inputPcm.isEmpty()) return FloatArray(0)
        val mono48k = convertTo48kHzMono(inputPcm, sampleRate, channels)
        val noDc = removeDcOffset(mono48k)
        val filtered = applyFiltersAndNotch(noDc, 48000)
        return normalizeGain(filtered, 0.8f)
    }

    /**
     * Extracts overlapping frames (75% overlap) with Hann windowing.
     * Window Size: 4096 samples, Hop Size: 1024 samples (75% overlap).
     */
    fun extract75PercentOverlapFrames(
        pcm: FloatArray,
        frameSize: Int = 4096
    ): List<ProcessedFrame> {
        val frames = mutableListOf<ProcessedFrame>()
        val hopSize = frameSize / 4 // 75% overlap
        if (pcm.size < frameSize) {
            // Zero pad single frame
            val padded = FloatArray(frameSize)
            for (i in pcm.indices) padded[i] = pcm[i]
            val windowed = FFT.applyHannWindow(padded)
            frames.add(ProcessedFrame(windowed, padded, 0, 0L))
            return frames
        }

        var offset = 0
        var frameIdx = 0
        while (offset + frameSize <= pcm.size) {
            val raw = FloatArray(frameSize)
            System.arraycopy(pcm, offset, raw, 0, frameSize)
            val windowed = FFT.applyHannWindow(raw)
            val timeMs = ((offset.toDouble() / 48000.0) * 1000.0).toLong()
            frames.add(ProcessedFrame(windowed, raw, frameIdx, timeMs))
            offset += hopSize
            frameIdx++
        }
        return frames
    }
}
