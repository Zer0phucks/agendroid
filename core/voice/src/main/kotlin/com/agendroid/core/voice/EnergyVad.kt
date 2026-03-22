package com.agendroid.core.voice

import kotlin.math.sqrt

class EnergyVad(private val threshold: Float = DEFAULT_THRESHOLD) {

    init {
        require(threshold in 0f..1f) { "threshold must be in [0, 1], got $threshold" }
    }

    fun isSpeech(samples: ShortArray): Boolean {
        if (samples.isEmpty()) return false
        val sumSq = samples.sumOf { sample -> sample.toLong() * sample }
        val rms = sqrt(sumSq.toDouble() / samples.size)
        return (rms / Short.MAX_VALUE).toFloat() >= threshold
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.02f
        const val FRAME_80MS = 1_280
        const val FRAME_160MS = 2_560
        const val FRAME_320MS = 5_120
    }
}
