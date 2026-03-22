package com.agendroid.core.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.agendroid.core.data.model.ContactInfo
import com.agendroid.core.data.util.PhoneNumberNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ContactsRepository {
    suspend fun getByPhoneNumber(phoneNumber: String): ContactInfo?
    suspend fun getAll(): List<ContactInfo>
}

@Singleton
class ContactsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val normalizer: PhoneNumberNormalizer,
) : ContactsRepository {

    override suspend fun getByPhoneNumber(phoneNumber: String): ContactInfo? {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber),
        )
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER,
                ContactsContract.PhoneLookup.PHOTO_URI,
            ),
            null, null, null,
        ) ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return null
            ContactInfo(
                contactId = it.getString(0),
                displayName = it.getString(1) ?: phoneNumber,
                phoneNumber = normalizer.normalize(it.getString(2) ?: phoneNumber),
                photoUri = it.getString(3),
            )
        }
    }

    override suspend fun getAll(): List<ContactInfo> {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        ) ?: return emptyList()

        return cursor.use {
            val results = mutableListOf<ContactInfo>()
            while (it.moveToNext()) {
                val normalized = normalizer.normalize(it.getString(2))
                if (normalized.isBlank()) continue
                results += ContactInfo(
                    contactId = it.getString(0),
                    displayName = it.getString(1) ?: it.getString(2),
                    phoneNumber = normalized,
                    photoUri = it.getString(3),
                )
            }
            results
        }
    }
}
