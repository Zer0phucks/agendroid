package com.agendroid.core.data.repository

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.core.data.util.PhoneNumberNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests for the content-provider repositories.
 *
 * Each test skips (rather than fails) when the required runtime permission has not been
 * granted, which is expected on stock API 31+ emulators/devices that do not hold the
 * default-SMS-app or ROLE_DIALER role.
 */
@RunWith(AndroidJUnit4::class)
class ProviderRepositorySmokeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val normalizer = PhoneNumberNormalizer(context)

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @Test
    fun contactsRepository_queryDoesNotCrash_andReturnsNormalizedNumbers() = runTest {
        assumeTrue("READ_CONTACTS not granted — skipping", hasPermission(Manifest.permission.READ_CONTACTS))
        val repo = ContactsRepositoryImpl(context, normalizer)
        val contacts = repo.getAll()
        assertTrue(contacts.all { it.phoneNumber.isBlank() || !it.phoneNumber.contains(' ') })
    }

    @Test
    fun smsRepository_queriesDoNotCrash_andExposeNormalizedKeys() = runTest {
        assumeTrue("READ_SMS not granted — skipping", hasPermission(Manifest.permission.READ_SMS))
        val repo = SmsThreadRepositoryImpl(context, normalizer)
        val threads = repo.getThreads().first()
        assertTrue(threads.all { !it.participantKey.contains(' ') })

        val firstThread = threads.firstOrNull()
        if (firstThread != null) {
            val messages = repo.getMessages(firstThread.threadId, limit = 5)
            assertTrue(messages.all { !it.addressKey.contains(' ') })
        }
    }

    @Test
    fun callLogRepository_queryDoesNotCrash_andExposeNormalizedKeys() = runTest {
        assumeTrue("READ_CALL_LOG not granted — skipping", hasPermission(Manifest.permission.READ_CALL_LOG))
        val repo = CallLogRepositoryImpl(context, normalizer)
        val entries = repo.getCallLog(limit = 10).first()
        assertTrue(entries.all { !it.numberKey.contains(' ') })
    }
}
