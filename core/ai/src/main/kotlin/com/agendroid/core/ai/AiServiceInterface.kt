// core/ai/src/main/kotlin/com/agendroid/core/ai/AiServiceInterface.kt
package com.agendroid.core.ai

import kotlinx.coroutines.flow.Flow

/**
 * Public contract for [AiCoreService], consumed by feature modules via Android binding.
 *
 * All suspend functions must be called from a coroutine with an appropriate dispatcher.
 * [generateResponse] runs on an internal IO thread inside the service.
 */
interface AiServiceInterface {

    /** Returns true if the Gemma model file is present in filesDir. */
    fun isModelAvailable(): Boolean

    /** Emits the current [ResourceState] and updates on thermal/battery changes. */
    val resourceState: Flow<ResourceState>

    /**
     * Runs the RAG pipeline and streams the LLM response token by token.
     *
     * @param userQuery         Current user input.
     * @param contactFilter     Normalised phone number for contact-scoped retrieval; null = global.
     * @param conversationHistory Previous turns for multi-turn context.
     * @param onToken           Called on each partial token. Set [done]=true to signal completion.
     *                          Called on an internal thread — do NOT touch UI from here.
     * @return The complete generated response text.
     */
    suspend fun generateResponse(
        userQuery: String,
        contactFilter: String? = null,
        conversationHistory: List<String> = emptyList(),
        onToken: (partial: String, done: Boolean) -> Unit = { _, _ -> },
    ): String
}
