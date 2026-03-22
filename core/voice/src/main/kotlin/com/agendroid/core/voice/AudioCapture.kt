package com.agendroid.core.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable

class AudioCapture(
    val frameSize: Int = EnergyVad.FRAME_80MS,
    val sampleRate: Int = SAMPLE_RATE_HZ,
) : Closeable {

    private var record: AudioRecord? = null
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope, onFrame: (ShortArray) -> Unit) {
        check(!isRunning) { "Already capturing. Call stop() first." }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, FORMAT)
        val bufferSize = maxOf(minBufferSize, frameSize * Short.SIZE_BYTES * 4)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            CHANNEL,
            FORMAT,
            bufferSize,
        )

        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            "AudioRecord failed to initialise. Verify RECORD_AUDIO permission is granted."
        }

        record = audioRecord
        audioRecord.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(frameSize)
            while (isActive) {
                val read = audioRecord.read(buffer, 0, frameSize, AudioRecord.READ_BLOCKING)
                if (read > 0) onFrame(buffer.copyOf(read))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        val audioRecord = record
        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { audioRecord.stop() }
        }
    }

    override fun close() {
        stop()
        record?.release()
        record = null
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
