package com.agendroid.feature.sms.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.common.Result
import com.agendroid.core.data.entity.PendingSmsReplyEntity
import com.agendroid.core.data.model.SmsMessage
import com.agendroid.core.data.repository.PendingSmsReplyRepository
import com.agendroid.core.data.repository.SmsThreadRepository
import com.agendroid.feature.sms.ui.NO_SUBSCRIPTION_ID
import com.agendroid.feature.sms.ui.PARTICIPANT_ADDRESS_ARG
import com.agendroid.feature.sms.ui.SUBSCRIPTION_ID_ARG
import com.agendroid.feature.sms.ui.THREAD_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationUiState(
    val participantAddress: String,
    val messages: List<SmsMessage> = emptyList(),
    val draftText: String = "",
    val pendingDraft: PendingSmsReplyEntity? = null,
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val smsThreadRepository: SmsThreadRepository,
    private val pendingSmsReplyRepository: PendingSmsReplyRepository,
) : ViewModel() {

    private val threadId: Long = checkNotNull(savedStateHandle[THREAD_ID_ARG])
    private val participantAddress: String = checkNotNull(savedStateHandle[PARTICIPANT_ADDRESS_ARG])
    private val subscriptionId: Int? = savedStateHandle.get<Int>(SUBSCRIPTION_ID_ARG)
        ?.takeUnless { it == NO_SUBSCRIPTION_ID }

    constructor(
        threadId: Long,
        participantAddress: String,
        subscriptionId: Int?,
        smsThreadRepository: SmsThreadRepository,
        pendingSmsReplyRepository: PendingSmsReplyRepository,
    ) : this(
        savedStateHandle = SavedStateHandle(
            mapOf(
                THREAD_ID_ARG to threadId,
                PARTICIPANT_ADDRESS_ARG to participantAddress,
                SUBSCRIPTION_ID_ARG to (subscriptionId ?: NO_SUBSCRIPTION_ID),
            ),
        ),
        smsThreadRepository = smsThreadRepository,
        pendingSmsReplyRepository = pendingSmsReplyRepository,
    )

    private val _uiState = MutableStateFlow(
        ConversationUiState(participantAddress = participantAddress),
    )
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        observePendingDrafts()
        refreshMessages()
    }

    fun onDraftChanged(text: String) {
        _uiState.update { it.copy(draftText = text) }
    }

    fun sendReply() {
        val draft = _uiState.value.draftText.trim()
        if (draft.isBlank() || _uiState.value.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            when (val result = smsThreadRepository.sendSms(participantAddress, draft, subscriptionId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(draftText = "", isSending = false) }
                    refreshMessages()
                }

                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = result.exception.message ?: "Unable to send message",
                        )
                    }
                }
            }
        }
    }

    private fun observePendingDrafts() {
        viewModelScope.launch {
            pendingSmsReplyRepository.pendingRepliesFlow.collect { replies ->
                val pendingDraft = replies
                    .filter { it.threadId == threadId }
                    .maxByOrNull(PendingSmsReplyEntity::createdAt)
                _uiState.update { it.copy(pendingDraft = pendingDraft) }
            }
        }
    }

    private fun refreshMessages() {
        viewModelScope.launch {
            val messages = smsThreadRepository.getMessages(threadId = threadId).sortedBy(SmsMessage::date)
            _uiState.update {
                it.copy(
                    messages = messages,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }
}
