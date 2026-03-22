package com.agendroid.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.entity.KnowledgeDocumentEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChunkDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() { db.close() }

    private suspend fun insertDocument(idSeed: String, sourceType: String = "doc"): Long {
        return db.knowledgeDocumentDao().insert(
            KnowledgeDocumentEntity(
                sourceType = sourceType,
                sourceUri = "test://$idSeed",
                title = "Doc $idSeed",
                checksum = idSeed,
            )
        )
    }

    @Test
    fun insertAll_and_getByIds_roundTrip() = runTest {
        val documentId = insertDocument("chunks-1", sourceType = "note")
        val chunks = listOf(
            ChunkEntity(documentId = documentId, sourceType = "note", contactFilter = null, chunkText = "hello world", chunkIndex = 0),
            ChunkEntity(documentId = documentId, sourceType = "note", contactFilter = null, chunkText = "second chunk", chunkIndex = 1),
        )
        val insertedIds = db.chunkDao().insertAll(chunks)

        val all = db.chunkDao().getByDocumentId(documentId)
        assertEquals(2, all.size)
        assertEquals(2, insertedIds.size)
        assertTrue(all.any { it.chunkText == "hello world" })
        assertTrue(all.any { it.chunkText == "second chunk" })
    }

    @Test
    fun getByIds_returnsOnlyRequestedChunks() = runTest {
        val documentId = insertDocument("chunks-2", sourceType = "sms")
        val chunks = listOf(
            ChunkEntity(documentId = documentId, sourceType = "sms", contactFilter = "+15550001234", chunkText = "a", chunkIndex = 0),
            ChunkEntity(documentId = documentId, sourceType = "sms", contactFilter = "+15550001234", chunkText = "b", chunkIndex = 1),
            ChunkEntity(documentId = documentId, sourceType = "sms", contactFilter = "+15550001234", chunkText = "c", chunkIndex = 2),
        )
        db.chunkDao().insertAll(chunks)
        val all = db.chunkDao().getByDocumentId(documentId)
        val targetIds = all.filter { it.chunkText == "a" || it.chunkText == "c" }.map { it.id }

        val fetched = db.chunkDao().getByIds(targetIds)
        assertEquals(2, fetched.size)
        assertTrue(fetched.none { it.chunkText == "b" })
    }

    @Test
    fun deleteByDocumentId_removesAllChunksForDocument() = runTest {
        val document3Id = insertDocument("chunks-3a")
        val document4Id = insertDocument("chunks-3b")
        db.chunkDao().insertAll(listOf(
            ChunkEntity(documentId = document3Id, sourceType = "doc", contactFilter = null, chunkText = "x", chunkIndex = 0),
            ChunkEntity(documentId = document3Id, sourceType = "doc", contactFilter = null, chunkText = "y", chunkIndex = 1),
            ChunkEntity(documentId = document4Id, sourceType = "doc", contactFilter = null, chunkText = "z", chunkIndex = 0),
        ))
        db.chunkDao().deleteByDocumentId(document3Id)

        assertTrue(db.chunkDao().getByDocumentId(document3Id).isEmpty())
        assertEquals(1, db.chunkDao().getByDocumentId(document4Id).size)
    }

    @Test
    fun countByDocumentId_returnsCorrectCount() = runTest {
        val documentId = insertDocument("chunks-4", sourceType = "note")
        db.chunkDao().insertAll(listOf(
            ChunkEntity(documentId = documentId, sourceType = "note", contactFilter = null, chunkText = "p", chunkIndex = 0),
            ChunkEntity(documentId = documentId, sourceType = "note", contactFilter = null, chunkText = "q", chunkIndex = 1),
        ))
        assertEquals(2, db.chunkDao().countByDocumentId(documentId))
    }

    @Test
    fun getIdsByDocumentId_returnsPersistedChunkIds() = runTest {
        val documentId = insertDocument("chunks-5")
        db.chunkDao().insertAll(listOf(
            ChunkEntity(documentId = documentId, sourceType = "doc", contactFilter = null, chunkText = "alpha", chunkIndex = 0),
            ChunkEntity(documentId = documentId, sourceType = "doc", contactFilter = null, chunkText = "beta", chunkIndex = 1),
        ))

        val ids = db.chunkDao().getIdsByDocumentId(documentId)
        assertEquals(2, ids.size)
        assertTrue(ids.all { it > 0L })
    }
}
