// core/ai/src/main/kotlin/com/agendroid/core/ai/RagOrchestrator.kt
package com.agendroid.core.ai

import com.agendroid.core.data.dao.ChunkDao
import com.agendroid.core.data.vector.VectorStore
import com.agendroid.core.embeddings.EmbeddingModel
import javax.inject.Inject
import javax.inject.Singleton

private const val TOP_K = 5

/**
 * Orchestrates the RAG query pipeline (read-only).
 *
 * Pipeline: embed query → ANN search in VectorStore → fetch chunk text from Room → build prompt
 *
 * All writes (indexing) go through [com.agendroid.core.data.repository.KnowledgeIndexRepository].
 * This class never writes to either store.
 *
 * Thread safety: all operations are suspending; safe to call from any coroutine.
 */
@Singleton
class RagOrchestrator @Inject constructor(
    private val embeddingModel: EmbeddingModel,
    private val vectorStore: VectorStore,
    private val chunkDao: ChunkDao,
    private val promptBuilder: PromptBuilder,
) {
    /**
     * Embeds [userQuery], retrieves the top-[TOP_K] most similar chunks (optionally filtered
     * to chunks with [contactFilter]), fetches their text from Room, and assembles a
     * Gemma 3-ready prompt string.
     *
     * @param userQuery     The user's current input or transcribed speech.
     * @param contactFilter Normalised phone number to restrict retrieval to one contact's chunks;
     *                      null means global search across all indexed content.
     * @param conversationHistory Previous turns for multi-turn context. Note: this list is passed
     *                      unbounded to PromptBuilder; in practice history is short in Plan 4, but
     *                      callers should trim to a reasonable depth for production use.
     * @return Complete prompt string ready for [LlmInferenceEngine.generate].
     */
    suspend fun buildPrompt(
        userQuery: String,
        contactFilter: String? = null,
        conversationHistory: List<String> = emptyList(),
    ): String {
        // 1. Embed the query
        val queryEmbedding = embeddingModel.embed(userQuery)

        // 2. ANN search — returns (chunkId, distance) ordered by ascending distance
        val vectorResults = vectorStore.query(queryEmbedding, limit = TOP_K)
        val chunkIds = vectorResults.map { it.chunkId }

        // 3. Fetch chunk text from Room (filter by contact if specified)
        val chunks = if (chunkIds.isEmpty()) emptyList()
                     else chunkDao.getByIds(chunkIds)
                         .let { rows ->
                             if (contactFilter != null)
                                 rows.filter { it.contactFilter == contactFilter || it.contactFilter == null }
                             else rows
                         }
                         .map { it.chunkText }

        // 4. Assemble prompt (chunks already in relevance order from sqlite-vec)
        return promptBuilder.build(
            ragChunks           = chunks,
            conversationHistory = conversationHistory,
            userQuery           = userQuery,
        )
    }
}
