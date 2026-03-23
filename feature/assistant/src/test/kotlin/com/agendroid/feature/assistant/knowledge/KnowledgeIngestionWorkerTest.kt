package com.agendroid.feature.assistant.knowledge

import androidx.work.OneTimeWorkRequest
import com.agendroid.core.common.Result
import com.agendroid.core.data.dao.KnowledgeDocumentDao
import com.agendroid.core.data.entity.IndexedSourceEntity
import com.agendroid.core.data.repository.IndexedSourceRepository
import com.agendroid.core.data.repository.KnowledgeIndexRepository
import com.agendroid.core.embeddings.EmbeddingModel
import com.agendroid.core.embeddings.TextChunker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class KnowledgeIngestionWorkerTest {

    private val documentTextExtractor: DocumentTextExtractor = mockk()
    private val urlContentFetcher: UrlContentFetcher = mockk()
    private val knowledgeDocumentDao: KnowledgeDocumentDao = mockk()
    private val knowledgeIndexRepository: KnowledgeIndexRepository = mockk()
    private val indexedSourceRepository: IndexedSourceRepository = mockk()
    private val textChunker: TextChunker = mockk()
    private val embeddingModel: EmbeddingModel = mockk()

    private lateinit var runner: KnowledgeIngestionRunner

    @BeforeEach
    fun setUp() {
        runner = KnowledgeIngestionRunner(
            documentTextExtractor = documentTextExtractor,
            urlContentFetcher = urlContentFetcher,
            knowledgeDocumentDao = knowledgeDocumentDao,
            knowledgeIndexRepository = knowledgeIndexRepository,
            indexedSourceRepository = indexedSourceRepository,
            textChunker = textChunker,
            embeddingModel = embeddingModel,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["FILE", "PDF"])
    fun `local sources become chunks embeddings and vectors`(sourceType: String) = runTest {
        val uri = when (sourceType) {
            "PDF" -> "file:///knowledge/guide.pdf"
            else -> "file:///knowledge/notes.txt"
        }
        val extracted = ExtractedKnowledgeContent(
            title = "Trip Brief",
            text = "Packing list and airport timing details",
        )
        val chunkSlot = slot<List<com.agendroid.core.data.entity.ChunkEntity>>()
        val embeddingSlot = slot<List<FloatArray>>()
        val indexedSourceSlot = slot<IndexedSourceEntity>()

        coEvery { documentTextExtractor.extract(uri, sourceType) } returns extracted
        every { textChunker.chunk(extracted.text) } returns listOf("Packing list", "Airport timing")
        coEvery { embeddingModel.embed("Packing list") } returns floatArrayOf(0.1f, 0.2f)
        coEvery { embeddingModel.embed("Airport timing") } returns floatArrayOf(0.3f, 0.4f)
        coEvery { knowledgeDocumentDao.getBySourceUri(uri) } returns null
        coEvery { knowledgeDocumentDao.insert(any()) } returns 7L
        coEvery {
            knowledgeIndexRepository.replaceDocumentChunks(
                7L,
                capture(chunkSlot),
                capture(embeddingSlot),
            )
        } returns Result.Success(listOf(11L, 12L))
        coEvery { knowledgeDocumentDao.update(any()) } returns Unit
        coEvery { indexedSourceRepository.upsert(capture(indexedSourceSlot)) } returns 3L

        runner.ingest(
            KnowledgeIngestionRequest(
                sourceType = sourceType,
                uri = uri,
            )
        )

        assertEquals(2, chunkSlot.captured.size)
        assertEquals(2, embeddingSlot.captured.size)
        assertEquals("Packing list", chunkSlot.captured[0].chunkText)
        assertEquals("Airport timing", chunkSlot.captured[1].chunkText)
        assertEquals(sourceType.lowercase(), chunkSlot.captured[0].sourceType)
        assertEquals("INDEXED", indexedSourceSlot.captured.status)
        assertEquals(sourceType, indexedSourceSlot.captured.sourceType)
        assertEquals(uri, indexedSourceSlot.captured.uri)
    }

    @Test
    fun `URL content becomes a stored indexed source`() = runTest {
        val url = "https://example.com/plans"
        val fetched = ExtractedKnowledgeContent(
            title = "Trip Plans",
            text = "Hotel check-in opens at four and parking is underground",
        )
        val indexedSourceSlot = slot<IndexedSourceEntity>()

        coEvery { urlContentFetcher.fetch(url) } returns fetched
        every { textChunker.chunk(fetched.text) } returns listOf("Hotel check-in", "Parking underground")
        coEvery { embeddingModel.embed(any()) } returns floatArrayOf(0.9f, 0.1f)
        coEvery { knowledgeDocumentDao.getBySourceUri(url) } returns null
        coEvery { knowledgeDocumentDao.insert(any()) } returns 9L
        coEvery { knowledgeIndexRepository.replaceDocumentChunks(eq(9L), any(), any()) } returns Result.Success(listOf(21L, 22L))
        coEvery { knowledgeDocumentDao.update(any()) } returns Unit
        coEvery { indexedSourceRepository.upsert(capture(indexedSourceSlot)) } returns 4L

        runner.ingest(
            KnowledgeIngestionRequest(
                sourceType = "URL",
                uri = url,
            )
        )

        assertEquals("URL", indexedSourceSlot.captured.sourceType)
        assertEquals(url, indexedSourceSlot.captured.uri)
        assertEquals("Trip Plans", indexedSourceSlot.captured.title)
        assertEquals("INDEXED", indexedSourceSlot.captured.status)
    }

    @Test
    fun `deleting a source enqueues cleanup work`() {
        val requests = mutableListOf<OneTimeWorkRequest>()
        val scheduler = KnowledgeIngestionScheduler(
            workRequestEnqueuer = WorkRequestEnqueuer { request ->
                requests += request
            }
        )

        scheduler.enqueueDelete(
            sourceType = "PDF",
            uri = "file:///knowledge/guide.pdf",
        )

        assertEquals(1, requests.size)
        assertEquals(
            KnowledgeIngestionWorker::class.java.name,
            requests.single().workSpec.workerClassName,
        )
        assertEquals(
            KnowledgeIngestionWorker.ACTION_DELETE,
            requests.single().workSpec.input.getString(KnowledgeIngestionWorker.KEY_ACTION),
        )
        assertEquals(
            "PDF",
            requests.single().workSpec.input.getString(KnowledgeIngestionWorker.KEY_SOURCE_TYPE),
        )
    }
}
