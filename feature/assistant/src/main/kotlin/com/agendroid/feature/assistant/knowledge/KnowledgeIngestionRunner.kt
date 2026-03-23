package com.agendroid.feature.assistant.knowledge

import com.agendroid.core.common.Result
import com.agendroid.core.data.dao.KnowledgeDocumentDao
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.entity.IndexedSourceEntity
import com.agendroid.core.data.entity.KnowledgeDocumentEntity
import com.agendroid.core.data.repository.IndexedSourceRepository
import com.agendroid.core.data.repository.KnowledgeIndexRepository
import com.agendroid.core.embeddings.EmbeddingModel
import com.agendroid.core.embeddings.TextChunker
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class KnowledgeIngestionRequest(
    val sourceType: String,
    val uri: String,
    val title: String? = null,
)

@Singleton
class KnowledgeIngestionRunner @Inject constructor(
    private val documentTextExtractor: DocumentTextExtractor,
    private val urlContentFetcher: UrlContentFetcher,
    private val knowledgeDocumentDao: KnowledgeDocumentDao,
    private val knowledgeIndexRepository: KnowledgeIndexRepository,
    private val indexedSourceRepository: IndexedSourceRepository,
    private val textChunker: TextChunker,
    private val embeddingModel: EmbeddingModel,
) {

    suspend fun ingest(request: KnowledgeIngestionRequest) {
        val sourceType = request.sourceType.uppercase()
        var resolvedTitle = request.title ?: request.uri

        try {
            val content = when (sourceType) {
                "URL" -> urlContentFetcher.fetch(request.uri)
                "FILE", "PDF" -> documentTextExtractor.extract(request.uri, sourceType)
                else -> throw IllegalArgumentException("Unsupported source type: $sourceType")
            }
            resolvedTitle = request.title ?: content.title

            val chunkTexts = textChunker.chunk(content.text.trim())
            require(chunkTexts.isNotEmpty()) { "No indexable text found for ${request.uri}" }

            val now = System.currentTimeMillis()
            val knowledgeSourceType = sourceType.lowercase()
            val checksum = sha256("$knowledgeSourceType\n$resolvedTitle\n${content.text}")
            val existingDocument = knowledgeDocumentDao.getBySourceUri(request.uri)
            val documentId = existingDocument?.id ?: knowledgeDocumentDao.insert(
                KnowledgeDocumentEntity(
                    sourceType = knowledgeSourceType,
                    sourceUri = request.uri,
                    title = resolvedTitle,
                    chunkCount = 0,
                    indexedAt = now,
                    checksum = checksum,
                )
            )

            val chunkEntities = chunkTexts.mapIndexed { index, chunkText ->
                ChunkEntity(
                    documentId = documentId,
                    sourceType = knowledgeSourceType,
                    contactFilter = null,
                    chunkText = chunkText,
                    chunkIndex = index,
                )
            }
            val embeddings = chunkTexts.map { chunkText -> embeddingModel.embed(chunkText) }

            when (val result = knowledgeIndexRepository.replaceDocumentChunks(documentId, chunkEntities, embeddings)) {
                is Result.Success -> Unit
                is Result.Failure -> throw result.exception
            }

            knowledgeDocumentDao.update(
                (existingDocument ?: KnowledgeDocumentEntity(id = documentId, sourceType = knowledgeSourceType, sourceUri = request.uri, title = resolvedTitle)).copy(
                    id = documentId,
                    sourceType = knowledgeSourceType,
                    sourceUri = request.uri,
                    title = resolvedTitle,
                    chunkCount = chunkTexts.size,
                    indexedAt = now,
                    checksum = checksum,
                )
            )

            indexedSourceRepository.upsert(
                IndexedSourceEntity(
                    sourceType = sourceType,
                    uri = request.uri,
                    title = resolvedTitle,
                    indexedAt = now,
                    status = "INDEXED",
                )
            )
        } catch (t: Throwable) {
            indexedSourceRepository.upsert(
                IndexedSourceEntity(
                    sourceType = sourceType,
                    uri = request.uri,
                    title = resolvedTitle,
                    indexedAt = 0L,
                    status = "FAILED",
                )
            )
            throw t
        }
    }

    suspend fun delete(request: KnowledgeIngestionRequest) {
        knowledgeDocumentDao.getBySourceUri(request.uri)?.let { document ->
            when (val result = knowledgeIndexRepository.deleteDocumentIndex(document.id)) {
                is Result.Success -> Unit
                is Result.Failure -> throw result.exception
            }
        }
        indexedSourceRepository.deleteByUri(request.uri)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
