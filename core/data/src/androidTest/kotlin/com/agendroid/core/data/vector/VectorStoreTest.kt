package com.agendroid.core.data.vector

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VectorStoreTest {

    private lateinit var store: VectorStore

    @Before fun setup() {
        store = VectorStore(ApplicationProvider.getApplicationContext(), dbName = "test_vectors.db")
    }

    @After fun teardown() {
        store.close()
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getDatabasePath("test_vectors.db").delete()
    }

    @Test
    fun insert_and_query_returnsSameChunkId() {
        val embedding = FloatArray(384) { it.toFloat() / 384f }
        store.insert(chunkId = 42L, embedding = embedding)

        val results = store.query(embedding, limit = 5)
        assertEquals(1, results.size)
        assertEquals(42L, results[0].chunkId)
    }

    @Test
    fun query_returnsClosestEmbeddingFirst() {
        val base = FloatArray(384) { 0f }
        val close = FloatArray(384) { 0.1f }
        val far = FloatArray(384) { 1.0f }

        store.insert(chunkId = 1L, embedding = base)
        store.insert(chunkId = 2L, embedding = close)
        store.insert(chunkId = 3L, embedding = far)

        val results = store.query(base, limit = 3)
        assertEquals(3, results.size)
        assertEquals(1L, results[0].chunkId)  // base is closest to itself
    }

    @Test
    fun delete_removesChunk() {
        val embedding = FloatArray(384) { 1f }
        store.insert(chunkId = 99L, embedding = embedding)
        store.delete(99L)

        val results = store.query(embedding, limit = 5)
        assertTrue(results.none { it.chunkId == 99L })
    }

    @Test
    fun query_onEmptyStore_returnsEmptyList() {
        val results = store.query(FloatArray(384) { 0f }, limit = 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun listChunkIds_returnsPersistedIds() {
        store.insert(chunkId = 10L, embedding = FloatArray(384) { 0.25f })
        store.insert(chunkId = 20L, embedding = FloatArray(384) { 0.5f })

        assertEquals(setOf(10L, 20L), store.listChunkIds())
    }
}
