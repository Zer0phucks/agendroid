package com.agendroid.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.ConversationSummaryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationSummaryDaoTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun teardown() { db.close() }

    @Test
    fun upsert_insertsNewSummary() = runTest {
        db.conversationSummaryDao().upsert(
            ConversationSummaryEntity(contactKey = "+15550001234", type = "sms", summary = "Discussed dinner plans")
        )
        val result = db.conversationSummaryDao().get("+15550001234", "sms")
        assertNotNull(result)
        assertEquals("Discussed dinner plans", result!!.summary)
    }

    @Test
    fun upsert_replacesExistingSummary() = runTest {
        val contact = "+15559876543"
        db.conversationSummaryDao().upsert(ConversationSummaryEntity(contactKey = contact, type = "call", summary = "First summary"))
        db.conversationSummaryDao().upsert(ConversationSummaryEntity(contactKey = contact, type = "call", summary = "Updated summary"))

        val result = db.conversationSummaryDao().get(contact, "call")
        assertEquals("Updated summary", result!!.summary)
    }

    @Test
    fun getForContactKey_returnsAllTypesForContact() = runTest {
        val contact = "+15551112222"
        db.conversationSummaryDao().upsert(ConversationSummaryEntity(contactKey = contact, type = "sms", summary = "sms context"))
        db.conversationSummaryDao().upsert(ConversationSummaryEntity(contactKey = contact, type = "call", summary = "call context"))

        val all = db.conversationSummaryDao().getForContactKey(contact).first()
        assertEquals(2, all.size)
    }

    @Test
    fun get_returnsNullForUnknownContact() = runTest {
        assertNull(db.conversationSummaryDao().get("+15550000000", "sms"))
    }
}
