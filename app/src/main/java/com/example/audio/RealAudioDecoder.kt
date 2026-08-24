package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.data.ImportedAudioMetadata
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * RealAudioDecoder handles real PCM decoding, waveform extraction, and metadata extraction
 * from device audio files (MP3, WAV, FLAC, AAC, M4A, OGG, MIDI).
 */
object RealAudioDecoder {

    private const val TAG = "RealAudioDecoder"

    data class DecodedAudioData(
        val pcmSamples: FloatArray,
        val sampleRate: Int,
        val channelCount: Int,
        val durationMs: Long,
        val waveformPeaks: List<Float>
    )

    /**
     * Extracts full metadata and true waveform amplitudes from a Uri.
     */
    fun extractMetadataAndWaveform(context: Context, uri: Uri): ImportedAudioMetadata {
        var fileName = "imported_audio.mp3"
        var fileSizeByte = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx != -1) cursor.getString(nameIdx)?.let { if (it.isNotEmpty()) fileName = it }
                    if (sizeIdx != -1) fileSizeByte = cursor.getLong(sizeIdx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying ContentResolver: ${e.message}")
        }

        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var durationMs = 0L
        var bitrate = "Unknown"
        var sampleRate = "Unknown"
        var channels = "Unknown"

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { if (it.isNotBlank()) artist = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { if (it.isNotBlank()) album = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { if (it > 0) durationMs = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { if (it > 0) bitrate = "${it / 1000} kbps" }
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed: ${e.message}")
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }

        // Extract real waveform from audio PCM frames
        val decodedData = decodePcmAudio(context, uri, maxDurationMs = 30000L)
        val waveform = decodedData?.waveformPeaks ?: emptyList()

        val ext = fileName.substringAfterLast('.', "MP3").uppercase()
        val durationFormatted = formatDuration(durationMs)
        val fileSizeFormatted = if (fileSizeByte > 0) String.format("%.1f MB", fileSizeByte.toDouble() / (1024 * 1024)) else "Unknown"

        if (decodedData != null) {
            sampleRate = "${decodedData.sampleRate / 1000.0} kHz"
            channels = "${decodedData.channelCount} ch"
            if (durationMs <= 0L) durationMs = decodedData.durationMs
        }

        val (bpm, key) = analyzeTempoAndKey(
            decodedData?.pcmSamples ?: FloatArray(0),
            decodedData?.sampleRate ?: 44100,
            decodedData?.channelCount ?: 1
        )

        return ImportedAudioMetadata(
            uriString = uri.toString(),
            fileName = fileName,
            artist = artist,
            album = album,
            durationMs = durationMs,
            durationFormatted = durationFormatted,
            bitrateKbps = bitrate,
            sampleRateHz = sampleRate,
            channels = channels,
            fileSize = fileSizeFormatted,
            formatExtension = ext,
            sourceType = "Device Import",
            waveformAmplitudes = waveform,
            detectedBpm = bpm,
            detectedKey = key
        )
    }

    /**
     * Decodes raw audio PCM samples using MediaExtractor and MediaCodec.
     */
    fun decodePcmAudio(context: Context, uri: Uri, maxDurationMs: Long = 60000L): DecodedAudioData? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val durationMs = durationUs / 1000L

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            val kTimeOutUs = 5000L

            while (!isEOS && pcmStream.size() < (sampleRate * channelCount * 2 * (maxDurationMs / 1000L))) {
                val inIndex = codec.dequeueInputBuffer(kTimeOutUs)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, kTimeOutUs)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(chunk)
                        pcmStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val pcmBytes = pcmStream.toByteArray()
            val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val samplesCount = shortBuffer.remaining()
            val floatSamples = FloatArray(samplesCount)
            for (i in 0 until samplesCount) {
                floatSamples[i] = shortBuffer.get(i) / 32768.0f
            }

            val waveformPeaks = computeWaveformPeaks(floatSamples, 64)

            DecodedAudioData(
                pcmSamples = floatSamples,
                sampleRate = sampleRate,
                channelCount = channelCount,
                durationMs = durationMs,
                waveformPeaks = waveformPeaks
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode PCM audio: ${e.message}")
            try { extractor.release() } catch (_: Exception) {}
            null
        }
    }

    private fun computeWaveformPeaks(samples: FloatArray, numPeaks: Int = 64): List<Float> {
        if (samples.isEmpty()) return emptyList()
        val chunkSize = (samples.size / numPeaks).coerceAtLeast(1)
        val peaks = mutableListOf<Float>()

        for (i in 0 until numPeaks) {
            val start = i * chunkSize
            val end = (start + chunkSize).coerceAtMost(samples.size)
            var maxAmp = 0.0f
            for (j in start until end) {
                val absAmp = abs(samples[j])
                if (absAmp > maxAmp) maxAmp = absAmp
            }
            peaks.add(maxAmp.coerceIn(0.12f, 1.0f))
        }

        return peaks
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    /**
     * Offline tempo + key estimation from decoded PCM. No filename heuristics are used.
     * Tempo: spectral-flux onset envelope + autocorrelation over 60..200 BPM.
     * Key: chroma energy averaged across frames and correlated with Krumhansl major/minor profiles.
     */
    private fun analyzeTempoAndKey(samples: FloatArray, sampleRate: Int, channelCount: Int): Pair<Int, String> {
        if (samples.size < sampleRate / 2) return 0 to ""
        val mono = if (channelCount <= 1) samples else {
            val frames = samples.size / channelCount
            FloatArray(frames) { frame ->
                var sum = 0f
                for (ch in 0 until channelCount) sum += samples[frame * channelCount + ch]
                sum / channelCount
            }
        }
        val frame = 2048
        val hop = 512
        val flux = ArrayList<Float>()
        var previous = FloatArray(frame / 2)
        var pos = 0
        while (pos + frame <= mono.size && flux.size < 12000) {
            val frameData = FloatArray(frame) { n ->
                val w = 0.5f * (1f - cos(2.0 * Math.PI * n / (frame - 1)).toFloat())
                samples[pos + n] * w
            }
            val spectrum = com.example.audio.dsp.FFT.fft(frameData).magnitude()
            val mag = spectrum.copyOf(frame / 2)
            var sum = 0f
            for (k in mag.indices) sum += max(0f, mag[k] - previous[k])
            flux.add(sum)
            previous = mag
            pos += hop
        }
        val bpm = estimateBpm(flux, hop, sampleRate)
        val key = estimateKey(mono, sampleRate)
        return bpm to key
    }

    private fun estimateBpm(onset: List<Float>, hop: Int, sampleRate: Int): Int {
        if (onset.size < 8) return 0
        val mean = onset.average().toFloat()
        val normalized = FloatArray(onset.size) { max(0f, onset[it] - mean) }
        var bestBpm = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (bpm in 60..200) {
            val lag = (60.0 * sampleRate / (bpm * hop)).roundToInt()
            if (lag <= 0 || lag >= normalized.size) continue
            var score = 0.0
            var count = 0
            var i = lag
            while (i < normalized.size) {
                score += normalized[i].toDouble() * normalized[i - lag].toDouble()
                count++
                i++
            }
            if (count > 0) score /= count
            // Also reward the half/double-time relationships less strongly.
            if (score > bestScore) { bestScore = score; bestBpm = bpm }
        }
        return bestBpm
    }

    private fun estimateKey(samples: FloatArray, sampleRate: Int): String {
        val chroma = DoubleArray(12)
        val frame = 4096
        val hop = 2048
        var frames = 0
        var pos = 0
        while (pos + frame <= samples.size && frames < 300) {
            val frameData = FloatArray(frame) { n ->
                val w = 0.5f * (1f - cos(2.0 * Math.PI * n / (frame - 1)).toFloat())
                samples[pos + n] * w
            }
            val spectrum = com.example.audio.dsp.FFT.fft(frameData).magnitude()
            val mag = DoubleArray(frame / 2) { spectrum[it].toDouble() }
            for (k in mag.indices) {
                val freq = k.toDouble() * sampleRate / frame
                if (freq < 50.0 || freq > 5000.0) continue
                val midi = (69.0 + 12.0 * (ln(freq / 440.0) / ln(2.0))).roundToInt()
                val pc = ((midi % 12) + 12) % 12
                chroma[pc] += mag[k]
            }
            frames++
            pos += hop
        }
        if (frames == 0) return ""
        val major = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        val minor = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
        val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        var best = Double.NEGATIVE_INFINITY
        var bestName = ""
        for (root in 0 until 12) {
            var majorScore = 0.0
            var minorScore = 0.0
            for (i in 0 until 12) {
                val pc = (root + i) % 12
                majorScore += chroma[pc] * major[i]
                minorScore += chroma[pc] * minor[i]
            }
            if (majorScore > best) { best = majorScore; bestName = "${names[root]} Major" }
            if (minorScore > best) { best = minorScore; bestName = "${names[root]} Minor" }
        }
        return bestName
    }

    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
}
