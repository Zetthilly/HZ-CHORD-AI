package com.example.data

data class StemChannelData(
    val id: String,          // "guitar", "bass", "piano", "vocals", "drums"
    val name: String,        // "Guitar", "Bass", "Piano", "Vocals", "Drums"
    val iconEmoji: String,   // "🎸", "🎸", "🎹", "🎤", "🥁"
    val colorHex: Long,      // Color hex
    val volume: Float = 1.0f,// 0.0f to 1.5f (Gain multiplier)
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
    val pan: Float = 0.0f,   // -1.0f (L) to +1.0f (R)
    val frequencyRange: String = "80Hz - 12kHz",
    val peakLevel: Float = 0.85f
)

data class StemMixerState(
    val channels: List<StemChannelData> = defaultStemChannels(),
    val masterVolume: Float = 1.0f,
    val isMasterMuted: Boolean = false
) {
    companion object {
        fun defaultStemChannels(): List<StemChannelData> = listOf(
            StemChannelData(id = "guitar", name = "Guitar", iconEmoji = "🎸", colorHex = 0xFF00E5FF, frequencyRange = "250Hz - 6.5kHz"),
            StemChannelData(id = "bass", name = "Bass", iconEmoji = "🎸", colorHex = 0xFFE040FB, frequencyRange = "40Hz - 250Hz"),
            StemChannelData(id = "piano", name = "Piano", iconEmoji = "🎹", colorHex = 0xFFFFD700, frequencyRange = "100Hz - 8kHz"),
            StemChannelData(id = "vocals", name = "Vocals", iconEmoji = "🎤", colorHex = 0xFF10B981, frequencyRange = "300Hz - 4kHz"),
            StemChannelData(id = "drums", name = "Drums", iconEmoji = "🥁", colorHex = 0xFFFF5252, frequencyRange = "20Hz - 16kHz")
        )
    }

    /**
     * DAW-Standard Solo/Mute Effective Gain Routing Matrix:
     * - If master is muted -> 0.0f
     * - If ANY channel in the project is soloed:
     *   - Only channels with isSoloed == true AND isMuted == false yield non-zero gain.
     * - If NO channel is soloed:
     *   - All channels with isMuted == false yield their volume gain.
     */
    fun calculateEffectiveGain(channelId: String): Float {
        if (isMasterMuted) return 0.0f
        val anySolo = channels.any { it.isSoloed }
        val target = channels.find { it.id == channelId } ?: return 0.0f

        return if (anySolo) {
            if (target.isSoloed && !target.isMuted) target.volume * masterVolume else 0.0f
        } else {
            if (!target.isMuted) target.volume * masterVolume else 0.0f
        }
    }

    fun isChannelActive(channelId: String): Boolean {
        return calculateEffectiveGain(channelId) > 0.001f
    }
}
