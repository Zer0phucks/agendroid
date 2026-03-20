// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/SpikeInCallService.kt
package com.agendroid.spike.callpipeline.phase2

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import kotlinx.coroutines.*

/** Spike in-call service. Tests:
 *  (1) AudioRecord captures caller audio during the call
 *  (2) AudioTrack plays back audio to caller
 *  (3) Take-over handoff: measures ms from takeover request to mic released */
class SpikeInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null

    // Exposed for Phase2TelecomActivity to read
    var lastTakeoverMs: Long = -1L
        private set

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added — answering and starting audio test")
        call.answer(0) // answer the call
        startAudioTest(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        stopAudio()
        Log.d(TAG, "Call removed — audio test complete")
    }

    private fun startAudioTest(call: Call) {
        val sampleRate = 16_000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 4,
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord?.startRecording()
            audioTrack?.play()
            Log.d(TAG, "AudioRecord state=${audioRecord?.state}, AudioTrack state=${audioTrack?.state}")

            // Echo loop — captures audio and plays it back (tests duplex routing)
            recordingJob = scope.launch {
                val buffer = ShortArray(bufferSize)
                var frameCount = 0
                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0) {
                        audioTrack?.write(buffer, 0, read)
                        frameCount++
                        if (frameCount == 1) {
                            Log.d(TAG, "First audio frame captured and played — duplex routing WORKS")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio setup failed", e)
        }
    }

    /** Called when user taps Take Over. Measures handoff latency. */
    fun requestTakeover(call: Call) {
        val startMs = System.currentTimeMillis()
        stopAudio()
        // Switch audio to user's earpiece
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_CALL
        am.isSpeakerphoneOn = false
        lastTakeoverMs = System.currentTimeMillis() - startMs
        Log.d(TAG, "Take-over handoff complete in ${lastTakeoverMs}ms")
    }

    private fun stopAudio() {
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopAudio()
    }

    companion object { const val TAG = "SpikeInCallService" }
}
