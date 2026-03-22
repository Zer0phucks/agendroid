package com.agendroid.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.KnowledgeDocumentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeDocumentDaoTest {

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

    @Test
    fun insert_and_getAll_returnsInsertedDoc() = runTest {
        val doc = KnowledgeDocumentEntity(
            sourceType = "pdf",
            sourceUri = "file:///notes.pdf",
            title = "My Notes",
            checksum = "abc123"
        )
        val id = db.knowledgeDocumentDao().insert(doc)
        assertTrue(id > 0)

        val all = db.knowledgeDocumentDao().getAll().first()
        assertEquals(1, all.size)
        assertEquals("My Notes", all[0].title)
    }

    @Test
    fun getBySourceUri_returnsCorrectDoc() = runTest {
        db.knowledgeDocumentDao().insert(
            KnowledgeDocumentEntity(
                sourceType = "url",
                sourceUri = "https://example.com",
                title = "Example",
                checksum = "def"
            )
        )
        val doc = db.knowledgeDocumentDao().getBySourceUri("https://example.com")
        assertNotNull(doc)
        assertEquals("Example", doc!!.title)
    }

    @Test
    fun getBySourceUri_returnsNullForMissingUri() = runTest {
        assertNull(db.knowledgeDocumentDao().getBySourceUri("https://notexist.com"))
    }

    @Test
    fun delete_removesDoc() = runTest {
        val id = db.knowledgeDocumentDao().insert(
            KnowledgeDocumentEntity(
                sourceType = "note",
                sourceUri = "note://1",
                title = "Note",
                checksum = "xyz"
            )
        )
        val doc = db.knowledgeDocumentDao().getBySourceUri("note://1")!!
        db.knowledgeDocumentDao().delete(doc)
        assertTrue(db.knowledgeDocumentDao().getAll().first().isEmpty())
    }

    @Test
    fun update_changesChunkCount() = runTest {
        val id = db.knowledgeDocumentDao().insert(
            KnowledgeDocumentEntity(
                sourceType = "doc",
                sourceUri = "doc://1",
                title = "Doc",
                checksum = "aaa"
            )
        )
        val doc = db.knowledgeDocumentDao().getBySourceUri("doc://1")!!
        db.knowledgeDocumentDao().update(doc.copy(chunkCount = 42))

        val updated = db.knowledgeDocumentDao().getBySourceUri("doc://1")!!
        assertEquals(42, updated.chunkCount)
    }
}
