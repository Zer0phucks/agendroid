package com.agendroid.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.entity.KnowledgeDocumentEntity
import com.agendroid.core.data.vector.VectorStore
import com.agendroid.core.common.Result
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeIndexRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var vectorStore: VectorStore
    private lateinit var repository: KnowledgeIndexRepository
    private var documentId: Long = 0L

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            context.deleteDatabase("knowledge-index-test.db")
            context.getDatabasePath("knowledge-index-vectors.db").delete()

            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            vectorStore = VectorStore(context, dbName = "knowledge-index-vectors.db")
            repository = KnowledgeIndexRepositoryImpl(db, vectorStore)
            documentId = db.knowledgeDocumentDao().insert(
                KnowledgeDocumentEntity(
                    sourceType = "pdf",
                    sourceUri = "file:///doc.pdf",
                    title = "Doc",
                    checksum = "checksum",
                )
            )
        }
    }

    @After
    fun teardown() {
        db.close()
        vectorStore.close()
        context.getDatabasePath("knowledge-index-vectors.db").delete()
    }

    @Test
    fun replaceDocumentChunks_replacesRoomRowsAndVectors() = runTest {
        val firstChunks = listOf(
            ChunkEntity(documentId = documentId, sourceType = "pdf", contactFilter = null, chunkText = "one", chunkIndex = 0),
            ChunkEntity(documentId = documentId, sourceType = "pdf", contactFilter = null, chunkText = "two", chunkIndex = 1),
        )
        val secondChunks = listOf(
            ChunkEntity(documentId = documentId, sourceType = "pdf", contactFilter = null, chunkText = "updated", chunkIndex = 0),
        )

        val result1 = repository.replaceDocumentChunks(documentId, firstChunks, List(2) { FloatArray(384) { it.toFloat() } })
        assertTrue("Expected Success but got $result1", result1 is Result.Success)
        val result2 = repository.replaceDocumentChunks(documentId, secondChunks, listOf(FloatArray(384) { 0.5f }))
        assertTrue("Expected Success but got $result2", result2 is Result.Success)

        assertEquals(1, db.chunkDao().getByDocumentId(documentId).size)
        assertEquals(1, vectorStore.listChunkIds().size)
    }

    @Test
    fun deleteDocumentIndex_removesRoomRowsAndVectors() = runTest {
        val chunks = listOf(
            ChunkEntity(documentId = documentId, sourceType = "pdf", contactFilter = null, chunkText = "one", chunkIndex = 0),
        )
        val insertResult = repository.replaceDocumentChunks(documentId, chunks, listOf(FloatArray(384) { 1f }))
        assertTrue("Expected Success but got $insertResult", insertResult is Result.Success)

        val deleteResult = repository.deleteDocumentIndex(documentId)
        assertTrue("Expected Success but got $deleteResult", deleteResult is Result.Success)

        assertTrue(db.chunkDao().getByDocumentId(documentId).isEmpty())
        assertTrue(vectorStore.listChunkIds().isEmpty())
    }

    @Test
    fun pruneOrphanVectors_deletesVectorsMissingFromRoom() = runTest {
        vectorStore.insert(999L, FloatArray(384) { 1f })

        val pruneResult = repository.pruneOrphanVectors()
        assertTrue("Expected Success but got $pruneResult", pruneResult is Result.Success)

        assertTrue(vectorStore.listChunkIds().isEmpty())
    }
}
