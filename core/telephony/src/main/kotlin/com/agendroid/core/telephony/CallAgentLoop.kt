package com.agendroid.core.telephony

import com.agendroid.core.ai.AiServiceInterface
import kotlinx.coroutines.runBlocking

class CallAgentLoop(
    private val repository: CallSessionRepository,
    private val aiProvider: TelephonyCoordinator.AiProvider,
    private val speechSynthesizer: TelephonyCoordinator.SpeechSynthesizer,
    private val audioBridge: CallAudioBridge,
    private val transferPhraseMatcher: CallTransferPhraseMatcher,
    private val summaryRecorder: CallSummaryRecorder,
) {

    sealed interface Result {
        data object NoReply : Result
        data object TakeoverRequested : Result
        data class AssistantReply(val text: String) : Result
    }

    var consecutiveEmptyTurns: Int = 0
        private set

    private var aiTurnCount: Int = 0

    suspend fun start(session: CallSession, ownerName: String? = null) {
        consecutiveEmptyTurns = 0
        aiTurnCount = 0

        if (session.mode != CallAutonomyMode.FULL_AGENT) return

        val disclosure = CallDisclosurePrompt.forOwner(ownerName)
        repository.appendTranscript(
            CallTranscriptLine(
                speaker = CallTranscriptLine.Speaker.ASSISTANT,
                text = disclosure,
                timestampMs = System.currentTimeMillis(),
            ),
        )
        repository.markDisclosureDelivered()
        speak(disclosure)
    }

    suspend fun handleCallerTranscript(
        session: CallSession,
        transcript: String,
        ownerName: String? = null,
    ): Result {
        if (session.isTakeoverRequested) return Result.NoReply

        val normalizedTranscript = transcript.trim()
        if (normalizedTranscript.isEmpty()) {
            consecutiveEmptyTurns += 1
            if (consecutiveEmptyTurns >= 3) {
                requestTakeover()
                return Result.TakeoverRequested
            }
            return Result.NoReply
        }

        consecutiveEmptyTurns = 0
        repository.appendTranscript(
            CallTranscriptLine(
                speaker = CallTranscriptLine.Speaker.CALLER,
                text = normalizedTranscript,
                timestampMs = System.currentTimeMillis(),
            ),
        )

        if (transferPhraseMatcher.matches(normalizedTranscript, ownerName = ownerName)) {
            requestTakeover()
            return Result.TakeoverRequested
        }

        if (session.mode != CallAutonomyMode.FULL_AGENT) {
            return Result.NoReply
        }

        if (aiTurnCount >= 10) {
            requestTakeover()
            return Result.TakeoverRequested
        }

        repository.setAiHandling(true)
        return try {
            val aiService: AiServiceInterface = aiProvider.get()
            val response = aiService.generateResponse(
                userQuery = normalizedTranscript,
                contactFilter = session.number,
                conversationHistory = repository.activeSession.value?.transcript?.map { it.text }.orEmpty(),
            )
            repository.appendTranscript(
                CallTranscriptLine(
                    speaker = CallTranscriptLine.Speaker.ASSISTANT,
                    text = response,
                    timestampMs = System.currentTimeMillis(),
                ),
            )
            speak(response)
            aiTurnCount += 1
            Result.AssistantReply(response)
        } finally {
            repository.setAiHandling(false)
        }
    }

    fun requestTakeover() {
        audioBridge.stopAssistantAudio()
        repository.requestTakeover()
    }

    fun finish(session: CallSession?) {
        if (session != null) {
            runBlocking {
                summaryRecorder.record(session)
            }
        }
        consecutiveEmptyTurns = 0
        aiTurnCount = 0
    }

    private suspend fun speak(text: String) {
        val samples = speechSynthesizer.synthesize(text)
        audioBridge.playAssistantAudio(samples)
    }
}
