// core/data/src/main/kotlin/com/agendroid/core/data/db/KeystoreKeyManager.kt
package com.agendroid.core.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the SQLCipher database passphrase.
 * Generates a 32-byte random key on first launch and stores it in
 * EncryptedSharedPreferences (backed by Android Keystore AES-256-GCM).
 * Subsequent calls return the same key bytes.
 */
@Singleton
class KeystoreKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        // security-crypto 1.0.0 uses MasterKeys (not MasterKey from 1.1.x).
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        @Suppress("DEPRECATION") // EncryptedSharedPreferences.create is the 1.0.0 API
        EncryptedSharedPreferences.create(
            "agendroid_db_key_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Returns the database passphrase, creating and persisting one if this is the first launch. */
    fun getOrCreateKey(): ByteArray {
        val stored = prefs.getString(KEY_PREF, null)
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP)

        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_PREF, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    private companion object {
        const val KEY_PREF = "db_passphrase"
    }
}
