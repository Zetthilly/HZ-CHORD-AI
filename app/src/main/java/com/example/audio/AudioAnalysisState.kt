package com.example.audio

import com.example.audio.dsp.DetectedKeyInfo
import com.example.audio.dsp.BpmEstimate

/**
 * Central data classes for UI state management.
 * Exported from audio package for ViewModel consumption.
 */

// Re-exported from dsp package for convenience
typealias DetectedKeyInfo = com.example.audio.dsp.DetectedKeyInfo
typealias BpmEstimate = com.example.audio.dsp.BpmEstimate
typealias ChordPrediction = com.example.audio.dsp.ChordPrediction
