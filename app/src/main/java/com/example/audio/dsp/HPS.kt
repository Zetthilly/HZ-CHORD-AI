package com.example.audio.dsp

/**
 * Harmonic Product Spectrum (HPS) DSP pitch fundamental extraction tool.
 * Downsamples magnitude spectrum by integer factors 2, 3, 4, and 5 and multiplies bin-by-bin.
 * Isolates true fundamental frequencies from overtones/harmonics without requiring machine learning models.
 */
object HPS {

    fun computeHps(magnitudes: FloatArray, maxHarmonics: Int = 5): FloatArray {
        val n = magnitudes.size
        val hps = FloatArray(n)
        val limit = n / maxHarmonics
        for (k in 0 until limit) {
            var prod = magnitudes[k]
            for (h in 2..maxHarmonics) {
                val index = k * h
                if (index < n) {
                    prod *= magnitudes[index]
                }
            }
            hps[k] = prod
        }
        return hps
    }
}
