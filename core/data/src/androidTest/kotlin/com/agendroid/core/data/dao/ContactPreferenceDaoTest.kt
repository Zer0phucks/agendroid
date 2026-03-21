package com.agendroid.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.ContactPreferenceEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactPreferenceDaoTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun teardown() { db.close() }

    @Test
    fun upsert_insertsPreference() = runTest {
        db.contactPreferenceDao().upsert(ContactPreferenceEntity(contactKey = "+15550001234", smsAutonomy = "auto", callHandling = "agent"))
        val pref = db.contactPreferenceDao().get("+15550001234")
        assertNotNull(pref)
        assertEquals("auto", pref!!.smsAutonomy)
        assertEquals("agent", pref.callHandling)
    }

    @Test
    fun upsert_replacesExistingPreference() = runTest {
        val id = "+15559998888"
        db.contactPreferenceDao().upsert(ContactPreferenceEntity(contactKey = id, smsAutonomy = "semi", callHandling = "screen"))
        db.contactPreferenceDao().upsert(ContactPreferenceEntity(contactKey = id, smsAutonomy = "manual", callHandling = "passthrough"))
        val pref = db.contactPreferenceDao().get(id)!!
        assertEquals("manual", pref.smsAutonomy)
        assertEquals("passthrough", pref.callHandling)
    }

    @Test
    fun get_returnsNullForUnknownContact() = runTest {
        assertNull(db.contactPreferenceDao().get("+15550000000"))
    }

    @Test
    fun delete_removesPreference() = runTest {
        val id = "+15551112222"
        db.contactPreferenceDao().upsert(ContactPreferenceEntity(contactKey = id, smsAutonomy = "auto", callHandling = "agent"))
        db.contactPreferenceDao().delete(id)
        assertNull(db.contactPreferenceDao().get(id))
    }
}
