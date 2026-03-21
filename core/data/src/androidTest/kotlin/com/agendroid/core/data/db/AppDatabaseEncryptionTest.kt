package com.agendroid.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseEncryptionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "app-database-encryption-test.db"

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadNativeLibrary() {
            // sqlcipher-android 4.x does not auto-load the native .so; it must be
            // loaded explicitly before any database call. In production this is done
            // in Application.onCreate (or DatabaseModule init). Tests do it here.
            System.loadLibrary("sqlcipher")
        }
    }

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun keystoreKeyManager_returnsStableKeyAcrossCalls() {
        val manager = KeystoreKeyManager(context)
        val first = manager.getOrCreateKey()
        val second = manager.getOrCreateKey()

        assertArrayEquals(first, second)
        assertTrue(first.size == 32)
    }

    @Test
    fun sqlcipherDatabase_reopensWithSameKey() = runTest {
        val keyManager = KeystoreKeyManager(context)
        val passphrase = keyManager.getOrCreateKey()
        val factory = SupportOpenHelperFactory(passphrase)

        val first = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .openHelperFactory(factory)
            .build()
        val second = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .openHelperFactory(factory)
            .build()

        // Opening the same file twice with the same SQLCipher key must succeed.
        assertNotEquals(first.openHelper.writableDatabase.version, -1)
        assertNotEquals(second.openHelper.writableDatabase.version, -1)

        first.close()
        second.close()
    }
}
