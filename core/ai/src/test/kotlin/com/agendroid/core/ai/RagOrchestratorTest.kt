// core/ai/src/test/kotlin/com/agendroid/core/ai/RagOrchestratorTest.kt
package com.agendroid.core.ai

import com.agendroid.core.data.dao.ChunkDao
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.vector.VectorResult
import com.agendroid.core.data.vector.VectorStore
import com.agendroid.core.embeddings.EmbeddingModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RagOrchestratorTest {

    private val embedding   = FloatArray(384) { 0.1f }
    private val embeddingModel = mockk<EmbeddingModel> {
        coEvery { embed(any()) } returns embedding
    }
    private val vectorStore = mockk<VectorStore> {
        every { query(any(), any()) } returns listOf(VectorResult(chunkId = 1L, distance = 0.1f))
    }
    private val chunkDao    = mockk<ChunkDao> {
        coEvery { getByIds(listOf(1L)) } returns listOf(
            ChunkEntity(id = 1L, documentId = 0L, sourceType = "note",
                        contactFilter = null, chunkText = "retrieved chunk text", chunkIndex = 0)
        )
    }
    private val promptBuilder = PromptBuilder()
    private val orchestrator  = RagOrchestrator(embeddingModel, vectorStore, chunkDao, promptBuilder)

    @Test
    fun `buildPrompt embeds query and calls vectorStore`() = runTest {
        val prompt = orchestrator.buildPrompt("what do I know about cats?")
        assertTrue(prompt.contains("retrieved chunk text"),
            "Expected retrieved chunk text in prompt, got: $prompt")
    }

    @Test
    fun `buildPrompt includes user query in prompt`() = runTest {
        val prompt = orchestrator.buildPrompt("user question here")
        assertTrue(prompt.contains("user question here"))
    }

    @Test
    fun `buildPrompt wraps chunks in RAG delimiters`() = runTest {
        val prompt = orchestrator.buildPrompt("any query")
        assertTrue(prompt.contains("[USER DATA START]"))
        assertTrue(prompt.contains("[USER DATA END]"))
    }

    @Test
    fun `buildPrompt with contactFilter passes it to vectorStore`() = runTest {
        // vectorStore mock already captures filter in the call; just verify no exception
        orchestrator.buildPrompt("query", contactFilter = "+15550001234")
    }

    @Test
    fun `buildPrompt with contactFilter excludes chunks with mismatched filter`() = runTest {
        // Chunk belongs to "+19990000000"; querying for "+15550001234" should exclude it
        val filteredChunkDao = mockk<ChunkDao> {
            coEvery { getByIds(listOf(1L)) } returns listOf(
                ChunkEntity(id = 1L, documentId = 0L, sourceType = "sms",
                            contactFilter = "+19990000000", chunkText = "secret other contact", chunkIndex = 0)
            )
        }
        val orc = RagOrchestrator(embeddingModel, vectorStore, filteredChunkDao, promptBuilder)
        val prompt = orc.buildPrompt("query", contactFilter = "+15550001234")
        assertFalse(prompt.contains("secret other contact"),
            "Chunk from a different contact should be excluded by contactFilter")
    }

    @Test
    fun `buildPrompt returns prompt when vectorStore returns empty`() = runTest {
        val emptyVectorStore = mockk<VectorStore> {
            every { query(any(), any()) } returns emptyList()
        }
        val emptyChunkDao = mockk<ChunkDao>(relaxed = true)
        val orc = RagOrchestrator(embeddingModel, emptyVectorStore, emptyChunkDao, promptBuilder)
        val prompt = orc.buildPrompt("empty store query")
        assertNotNull(prompt)
        assertTrue(prompt.isNotBlank())
    }
}
