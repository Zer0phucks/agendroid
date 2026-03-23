package com.agendroid.core.telephony

import com.agendroid.core.ai.AiServiceInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelephonyCoordinator @Inject constructor(
    private val repository: CallSessionRepository,
    private val aiProvider: AiProvider,
    private val speechRecognizer: SpeechRecognizer,
    private val speechSynthesizer: SpeechSynthesizer,
    internal val audioBridge: CallAudioBridge,
    private val transferPhraseMatcher: CallTransferPhraseMatcher,
    private val summaryRecorder: CallSummaryRecorder,
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

    private val callAgentLoop = CallAgentLoop(
        repository = repository,
        aiProvider = aiProvider,
        speechSynthesizer = speechSynthesizer,
        audioBridge = audioBridge,
        transferPhraseMatcher = transferPhraseMatcher,
        summaryRecorder = summaryRecorder,
    )

    internal val consecutiveEmptyTranscripts: Int
        get() = callAgentLoop.consecutiveEmptyTurns

    suspend fun startSession(callId: String, number: String?, mode: CallAutonomyMode) {
        repository.startSession(callId, number, mode)
        speechRecognizer.load()
        if (mode == CallAutonomyMode.FULL_AGENT) {
            speechSynthesizer.load()
        }
        callAgentLoop.start(checkNotNull(repository.activeSession.value))
    }

    suspend fun handleCallerAudio(pcm: ShortArray): String? {
        val session = repository.activeSession.value ?: return null
        if (session.isTakeoverRequested) return null

        val transcript = speechRecognizer.transcribe(pcm).trim()
        return when (val result = callAgentLoop.handleCallerTranscript(session, transcript)) {
            is CallAgentLoop.Result.AssistantReply -> result.text
            CallAgentLoop.Result.NoReply,
            CallAgentLoop.Result.TakeoverRequested,
            -> null
        }
    }

    fun requestTakeover() {
        callAgentLoop.requestTakeover()
    }

    fun endSession() {
        callAgentLoop.finish(repository.activeSession.value)
        audioBridge.stopAssistantAudio()
        speechRecognizer.close()
        speechSynthesizer.close()
        aiProvider.unbind()
        audioBridge.close()
        repository.clearSession()
    }
}
