package com.agendroid.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun teardown() { db.close() }

    @Test
    fun insert_and_getAll_returnsNote() = runTest {
        val id = db.noteDao().insert(NoteEntity(title = "Shopping list", content = "Eggs, milk"))
        assertTrue(id > 0)
        val notes = db.noteDao().getAll().first()
        assertEquals(1, notes.size)
        assertEquals("Shopping list", notes[0].title)
    }

    @Test
    fun update_changesContent() = runTest {
        val id = db.noteDao().insert(NoteEntity(title = "Draft", content = "Old"))
        val note = db.noteDao().getById(id)!!
        db.noteDao().update(note.copy(content = "New content"))
        assertEquals("New content", db.noteDao().getById(id)!!.content)
    }

    @Test
    fun delete_removesNote() = runTest {
        val id = db.noteDao().insert(NoteEntity(title = "Temp", content = "Delete me"))
        val note = db.noteDao().getById(id)!!
        db.noteDao().delete(note)
        assertNull(db.noteDao().getById(id))
    }
}
