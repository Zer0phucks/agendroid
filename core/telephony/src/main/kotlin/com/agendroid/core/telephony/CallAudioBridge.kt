package com.agendroid.core.telephony

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
open class CallAudioBridge @Inject constructor() {

    private var audioTrack: AudioTrack? = null

    open fun playAssistantAudio(samples: FloatArray) {
        if (samples.isEmpty()) return

        val bufferSize = max(
            AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            samples.size * Short.SIZE_BYTES,
        )
        val track = audioTrack ?: AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
            .also {
                audioTrack = it
                it.play()
            }

        val pcm = ShortArray(samples.size) { index ->
            (min(1f, max(-1f, samples[index])) * Short.MAX_VALUE).toInt().toShort()
        }
        track.write(pcm, 0, pcm.size)
    }

    open fun stopAssistantAudio() {
        audioTrack?.pause()
        audioTrack?.flush()
    }

    fun close() {
        audioTrack?.release()
        audioTrack = null
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 24_000
    }
}
