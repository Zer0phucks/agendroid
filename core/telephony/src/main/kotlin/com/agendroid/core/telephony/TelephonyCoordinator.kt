package com.agendroid.core.telephony

import com.agendroid.core.ai.AiServiceInterface
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelephonyCoordinator @Inject constructor(
    private val repository: CallSessionRepository,
    private val aiProvider: AiProvider,
    private val speechRecognizer: SpeechRecognizer,
    private val speechSynthesizer: SpeechSynthesizer,
    internal val audioBridge: CallAudioBridge,
) {

    fun interface AiProvider {
        suspend fun get(): AiServiceInterface
        fun unbind() {}
    }

    interface SpeechRecognizer {
        suspend fun load()
        suspend fun transcribe(pcm: ShortArray): String
        fun close() {}
    }

    interface SpeechSynthesizer {
        suspend fun load()
        suspend fun synthesize(text: String): FloatArray
        fun close() {}
    }

    private val mutex = Mutex()
    internal var consecutiveEmptyTranscripts: Int = 0
        private set
    private var aiTurnCount: Int = 0

    suspend fun startSession(callId: String, number: String?, mode: CallAutonomyMode) {
        repository.startSession(callId, number, mode)
        speechRecognizer.load()
        if (mode == CallAutonomyMode.FULL_AGENT) {
            speechSynthesizer.load()
        }
        consecutiveEmptyTranscripts = 0
        aiTurnCount = 0
    }

    suspend fun handleCallerAudio(pcm: ShortArray): String? = mutex.withLock {
        val session = repository.activeSession.value ?: return null
        if (session.isTakeoverRequested) return null

        val transcript = speechRecognizer.transcribe(pcm).trim()
        if (transcript.isEmpty()) {
            consecutiveEmptyTranscripts += 1
            if (consecutiveEmptyTranscripts >= 3) {
                requestTakeover()
            }
            return null
        }

        consecutiveEmptyTranscripts = 0
        repository.appendTranscript(
            CallTranscriptLine(
                speaker = CallTranscriptLine.Speaker.CALLER,
                text = transcript,
                timestampMs = System.currentTimeMillis(),
            ),
        )

        when (session.mode) {
            CallAutonomyMode.PASS_THROUGH,
            CallAutonomyMode.SCREEN_ONLY,
            -> return null

            CallAutonomyMode.FULL_AGENT -> {
                if (aiTurnCount >= 10) {
                    requestTakeover()
                    return null
                }

                repository.setAiHandling(true)
                try {
                    val aiService = aiProvider.get()
                    val response = aiService.generateResponse(
                        userQuery = transcript,
                        contactFilter = session.number,
                        conversationHistory = repository.activeSession.value
                            ?.transcript
                            ?.map { it.text }
                            .orEmpty(),
                    )
                    repository.appendTranscript(
                        CallTranscriptLine(
                            speaker = CallTranscriptLine.Speaker.ASSISTANT,
                            text = response,
                            timestampMs = System.currentTimeMillis(),
                        ),
                    )
                    val samples = speechSynthesizer.synthesize(response)
                    audioBridge.playAssistantAudio(samples)
                    aiTurnCount += 1
                    return response
                } finally {
                    repository.setAiHandling(false)
                }
            }
        }
    }

    fun requestTakeover() {
        audioBridge.stopAssistantAudio()
        repository.requestTakeover()
    }

    fun endSession() {
        audioBridge.stopAssistantAudio()
        speechRecognizer.close()
        speechSynthesizer.close()
        aiProvider.unbind()
        audioBridge.close()
        repository.clearSession()
        consecutiveEmptyTranscripts = 0
        aiTurnCount = 0
    }
}
