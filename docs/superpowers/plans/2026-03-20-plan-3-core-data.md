# Core Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `:core:common` extensions and `:core:data` — the encrypted Room database, sqlite-vec vector store, an index-consistency coordinator, and content-provider repositories that every other module depends on.

**Architecture:** Room + SQLCipher provides an AES-256 encrypted database for structured data (chunks, documents, summaries, notes, preferences). A separate SQLite file loaded with the sqlite-vec extension stores float embeddings by chunk ID only; the sensitive chunk text stays in Room. A `KnowledgeIndexRepository` is the only component allowed to mutate both stores; it coordinates document re-index/delete flows, compensates on failure, and prunes orphan vectors so Room remains the source of truth. Content-provider repositories wrap Android's system SMS, contacts, and call-log providers behind interfaces so feature modules never touch `ContentResolver` directly.

**Tech Stack:** Room 2.7, SQLCipher 4.5.6 (`net.zetetic:sqlcipher-android`), sqlite-vec 0.1.6 (`io.github.asg017:sqlite-vec-android-bundled`), Jetpack Security Crypto 1.0.0, Hilt, Kotlin Coroutines/Flow, JUnit 5 (JVM), AndroidJUnit4 (instrumented).

---

## Context for implementers

`:core:common` already has `Result.kt` and `DispatcherModule.kt` — do not touch those.

`:core:data` currently contains only an empty `AndroidManifest.xml`. Everything else is created by this plan.

The dependency chain for Plans 4–8:

```
:core:data   ← :core:ai (Plan 4), :core:embeddings (Plan 4), :core:voice (Plan 5)
             ← :core:telephony (Plan 6), :feature:sms (Plan 7), :feature:phone (Plan 7)
```

Get this module right — every other module depends on it.

### V1 scope boundaries

This plan delivers a **shipping-quality encrypted data layer** and an **SMS-first** messaging repository. It does **not** complete every requirement for a production-ready default messaging app on its own.

- Task 9 covers SMS threads/messages and subscription-aware sending for dual-SIM devices.
- MMS, MMS group threads, attachment persistence, and `WAP_PUSH_DELIVER_ACTION` handling are explicitly **out of scope for Plan 3** and must be implemented before any public/default-SMS release.
- The repository APIs in this plan therefore model **SMS-only** messaging data. Follow-up plans may replace them with a richer messaging abstraction once MMS is scheduled.

### Two-database design

| Database | File | Purpose | Encryption |
|---|---|---|---|
| Room (AppDatabase) | `agendroid.db` | Chunks text, docs, summaries, notes, prefs | SQLCipher AES-256 |
| VectorStore | `vectors.db` | Float embeddings keyed by chunk_id | AOSP SQLite (app-private storage — see note below) |

The Room DB is the source of truth for text and metadata. The VectorStore is a search index. At query time: sqlite-vec returns `(chunk_id, distance)` → Room fetches `(chunk_text, metadata)` by those IDs.

Because these are separate databases, cross-store writes are **not atomic**. To prevent drift:

- Direct `ChunkDao.deleteByDocumentId()` and direct `VectorStore` mutation are internal-only primitives.
- A `KnowledgeIndexRepository` (Task 8) owns document re-index/delete flows end-to-end.
- `KnowledgeIndexRepository` prunes orphan vector rows and compensates failed writes so stale chunk IDs do not leak into retrieval results.

**Why vectors.db is not SQLCipher-encrypted (deliberate deviation from spec §11.3):**
`sqlite-vec-android-bundled` statically links its own native sqlite-vec extension that registers against AOSP's `android.database.sqlite.SQLiteDatabase`. SQLCipher uses a forked SQLite (`net.sqlcipher.database.SQLiteDatabase`). The two share no code and the extension cannot be loaded into the SQLCipher connection without significant native bridging work. This is not feasible within Plan 3's scope.

Security impact is minimal: `vectors.db` stores only float arrays (384 floats per chunk). The sensitive text (`chunk_text`) is always in Room/SQLCipher. Even if `vectors.db` is read by a root-privileged attacker, the floats are unintelligible without the embedding model and significant computation to partially reverse. The Android app-private sandbox (`context.getDatabasePath()`) prevents access by other apps without root. Encrypt vectors.db with SQLCipher as a future hardening task if the threat model requires it.

### sqlcipher key management

`KeystoreKeyManager` generates a random 32-byte key on first launch, stores it in `EncryptedSharedPreferences` (backed by Android Keystore AES-256-GCM), and returns the same bytes on subsequent launches. The SQLCipher `SupportFactory` receives these bytes as its passphrase.

---

## File map

```
gradle/libs.versions.toml                               ← add security-crypto, AndroidJUnit4 ext, fix sqlite-vec alias

core/data/
├── build.gradle.kts                                    ← add deps, ksp schema dir, androidTest block
└── src/
    ├── main/kotlin/com/agendroid/core/data/
    │   ├── db/
    │   │   ├── AppDatabase.kt                          ← Room @Database, SQLCipher SupportFactory hook
    │   │   ├── DatabaseModule.kt                       ← Hilt @Singleton Room + VectorStore provision
    │   │   └── KeystoreKeyManager.kt                   ← EncryptedSharedPreferences key generation
    │   ├── entity/
    │   │   ├── ChunkEntity.kt                          ← RAG text chunk (text lives here, not VectorStore)
    │   │   ├── KnowledgeDocumentEntity.kt              ← metadata for an indexed document/source
    │   │   ├── ConversationSummaryEntity.kt            ← AI-generated call/SMS summary per contact
    │   │   ├── NoteEntity.kt                           ← user note (ingested into RAG)
    │   │   └── ContactPreferenceEntity.kt              ← per-contact SMS/call autonomy settings
    │   ├── dao/
    │   │   ├── ChunkDao.kt
    │   │   ├── KnowledgeDocumentDao.kt
    │   │   ├── ConversationSummaryDao.kt
    │   │   ├── NoteDao.kt
    │   │   └── ContactPreferenceDao.kt
    │   ├── model/
    │   │   ├── ContactInfo.kt                          ← data class returned by ContactsRepository
    │   │   ├── SmsThread.kt                            ← data class: thread metadata
    │   │   ├── SmsMessage.kt                           ← data class: individual message
    │   │   └── CallLogEntry.kt                         ← data class: call log row
    │   ├── repository/
    │   │   ├── KnowledgeIndexRepository.kt             ← only write path that mutates Room + VectorStore together
    │   │   ├── ContactsRepository.kt                   ← interface + impl (ContactsProvider)
    │   │   ├── SmsThreadRepository.kt                  ← interface + impl (SMS Provider)
    │   │   ├── CallLogRepository.kt                    ← interface + impl (CallLog.Calls)
    │   │   └── RepositoriesModule.kt                   ← Hilt bindings for repositories
    │   ├── util/
    │   │   └── PhoneNumberNormalizer.kt                ← E.164-when-possible normalization helper
    │   └── vector/
    │       ├── VectorResult.kt                         ← (chunkId, distance) return type
    │       ├── VectorStore.kt                          ← sqlite-vec wrapper (insert/query/delete)
    │       └── VectorStoreModule.kt                    ← Hilt @Singleton VectorStore provision
    ├── test/kotlin/com/agendroid/core/data/
    │   └── (none — all Room tests are instrumented; see androidTest)
    └── androidTest/kotlin/com/agendroid/core/data/
        ├── db/
        │   └── AppDatabaseEncryptionTest.kt            ← verifies SQLCipher key bootstrap + reopen path
        ├── dao/
        │   ├── ChunkDaoTest.kt
        │   ├── KnowledgeDocumentDaoTest.kt
        │   ├── ConversationSummaryDaoTest.kt
        │   ├── NoteDaoTest.kt
        │   └── ContactPreferenceDaoTest.kt
        ├── repository/
        │   ├── KnowledgeIndexRepositoryTest.kt         ← Room/vector consistency tests
        │   └── ProviderRepositorySmokeTest.kt          ← exercises Contacts/SMS/CallLog adapters on device
        ├── util/
        │   └── PhoneNumberNormalizerTest.kt            ← normalization contract tests
        └── vector/
            └── VectorStoreTest.kt
```

---

## Task 1: Build configuration

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/data/build.gradle.kts`

- [ ] **Step 1: Add new version entries and library aliases to `gradle/libs.versions.toml`**

Add under `[versions]`:
```toml
security-crypto     = "1.0.0"
androidx-test-ext-junit = "1.2.1"
androidx-test-rules = "1.6.1"
```

Add under `[libraries]` (replace the awkward `sqlite-vec-version` entry — leave it as-is for now, just add the properly-named alias below it):
```toml
sqlite-vec-android  = { module = "io.github.asg017:sqlite-vec-android-bundled", version.ref = "sqlite-vec" }
security-crypto     = { module = "androidx.security:security-crypto", version.ref = "security-crypto" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext-junit" }
androidx-test-rules = { module = "androidx.test:rules", version.ref = "androidx-test-rules" }
```

- [ ] **Step 2: Update `core/data/build.gradle.kts`**

Replace the entire file with:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.agendroid.core.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Tell Room where to write schema JSON files (commit these for migration tracking)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room (encrypted via SQLCipher)
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)

    // SQLCipher key storage
    implementation(libs.security.crypto)

    // sqlite-vec vector store
    implementation(libs.sqlite.vec.android)

    // WorkManager (RAG ingestion workers live here)
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // JVM unit tests
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.bundles.coroutines)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 3: Sync Gradle and verify it compiles**

```bash
cd /home/noob/agendroid
./gradlew :core:data:assembleDebug
```

Expected: `BUILD SUCCESSFUL` — the module compiles (no source yet, but all deps resolve, including AndroidJUnit4 test support).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml core/data/build.gradle.kts
git commit -m "build: add security-crypto and sqlite-vec deps to :core:data"
```

---

## Task 2: KeystoreKeyManager + AppDatabase scaffold + DatabaseModule

**Files:**
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/db/KeystoreKeyManager.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/db/DatabaseModule.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/db/AppDatabaseEncryptionTest.kt`

No JVM unit tests for this task — `KeystoreKeyManager` requires Android Keystore (native, can't run on JVM). Do **not** rely on the DAO tests in Tasks 3–6 for encryption coverage; those use in-memory databases and do not exercise SQLCipher or on-disk reopen behavior.

- [ ] **Step 1: Create `KeystoreKeyManager.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/db/KeystoreKeyManager.kt
package com.agendroid.core.data.db

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "agendroid_db_key_prefs",
            masterKey,
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
```

- [ ] **Step 2: Create `AppDatabase.kt`** (empty entity list — entities added in Tasks 3–6)

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt
package com.agendroid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Main Room database. Encrypted at rest via SQLCipher (see DatabaseModule).
 * Entities are added incrementally in Tasks 3–6. Version is bumped with each
 * schema change; migration stubs must be added before version is incremented.
 *
 * Schema JSON files are exported to core/data/schemas/ — commit them.
 */
@Database(
    entities = [],   // populated in Tasks 3–6
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase()
// DAOs added in Tasks 3–6
```

- [ ] **Step 3: Create `DatabaseModule.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/db/DatabaseModule.kt
package com.agendroid.core.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyManager: KeystoreKeyManager,
    ): AppDatabase {
        // SQLiteDatabase.getBytes(charArray) treats each char as a raw byte.
        // ISO_8859_1 is the only charset where byte 0x00–0xFF maps 1:1 to char 0x00–0xFF,
        // so this preserves the full 32-byte random key without corruption.
        val passphrase = SQLiteDatabase.getBytes(
            keyManager.getOrCreateKey().toString(Charsets.ISO_8859_1).toCharArray()
        )
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, "agendroid.db")
            .openHelperFactory(factory)
            .build()
    }
}
```

- [ ] **Step 4: Create `AppDatabaseEncryptionTest.kt`**

```kotlin
package com.agendroid.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseEncryptionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "app-database-encryption-test.db"

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
        val passphrase = SQLiteDatabase.getBytes(
            keyManager.getOrCreateKey().toString(Charsets.ISO_8859_1).toCharArray()
        )
        val factory = SupportFactory(passphrase)

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
```

- [ ] **Step 5: Run the encryption/bootstrap test**

```bash
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.db.AppDatabaseEncryptionTest"
```

Expected: 2 tests PASS.

- [ ] **Step 6: Verify it compiles**

```bash
./gradlew :core:data:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add core/data/src/
git commit -m "feat(data): add KeystoreKeyManager, AppDatabase scaffold, and DatabaseModule"
```

---

## Task 3: ChunkEntity + ChunkDao

The `chunks` table stores RAG text segments. Float embeddings for these chunks go in VectorStore (Task 7) — the two are linked by `chunk.id == vector.chunkId`.

**Files:**
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/ChunkEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/ChunkDao.kt`
- Modify: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/ChunkDaoTest.kt`

- [ ] **Step 1: Write the failing instrumented test**

Create `core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/ChunkDaoTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test — expect compile failure (entities don't exist yet)**

```bash
./gradlew :core:data:assembleAndroidTest 2>&1 | grep -E "error:|ERROR"
```

Expected: compile errors for `ChunkEntity`, `ChunkDao`, `AppDatabase.chunkDao()`.

- [ ] **Step 3: Create `ChunkEntity.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/entity/ChunkEntity.kt
package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A RAG text chunk from a document or conversation source.
 * The float embedding for this chunk is stored in VectorStore keyed by [id].
 */
@Entity(
    tableName = "chunks",
    indices = [Index("document_id"), Index("contact_filter")],
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    /** "sms" | "call" | "note" | "contact" | "doc" | "calendar" */
    @ColumnInfo(name = "source_type") val sourceType: String,
    /** Normalized phone number for contact-scoped retrieval; null for global chunks. */
    @ColumnInfo(name = "contact_filter") val contactFilter: String?,
    @ColumnInfo(name = "chunk_text") val chunkText: String,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int,
)
```

- [ ] **Step 4: Create `ChunkDao.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/dao/ChunkDao.kt
package com.agendroid.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendroid.core.data.entity.ChunkEntity

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>): List<Long>

    /** Fetch chunks by their IDs (used after VectorStore returns top-k chunk IDs). */
    @Query("SELECT * FROM chunks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE document_id = :documentId")
    suspend fun getByDocumentId(documentId: Long): List<ChunkEntity>

    /** Internal: used by KnowledgeIndexRepository to keep VectorStore in sync with Room. */
    @Query("SELECT id FROM chunks WHERE document_id = :documentId")
    suspend fun getIdsByDocumentId(documentId: Long): List<Long>

    /** Internal: used by KnowledgeIndexRepository.pruneOrphanVectors(). */
    @Query("SELECT id FROM chunks")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM chunks WHERE document_id = :documentId")
    suspend fun deleteByDocumentId(documentId: Long)

    @Query("SELECT COUNT(*) FROM chunks WHERE document_id = :documentId")
    suspend fun countByDocumentId(documentId: Long): Int
}
```

- [ ] **Step 5: Update `AppDatabase.kt`** — add entity and DAO

Replace the file contents:

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt
package com.agendroid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agendroid.core.data.dao.ChunkDao
import com.agendroid.core.data.entity.ChunkEntity

@Database(
    entities = [ChunkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
}
```

- [ ] **Step 6: Run the instrumented test on device**

```bash
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.dao.ChunkDaoTest"
```

Expected: 5 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add core/data/src/
git commit -m "feat(data): add ChunkEntity + ChunkDao with instrumented tests"
```

---

## Task 4: KnowledgeDocumentEntity + KnowledgeDocumentDao

**Files:**
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/KnowledgeDocumentEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/KnowledgeDocumentDao.kt`
- Modify: `core/data/src/main/kotlin/com/agendroid/core/data/entity/ChunkEntity.kt`
- Modify: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/KnowledgeDocumentDaoTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/KnowledgeDocumentDaoTest.kt
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

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun teardown() { db.close() }

    @Test
    fun insert_and_getAll_returnsInsertedDoc() = runTest {
        val doc = KnowledgeDocumentEntity(sourceType = "pdf", sourceUri = "file:///notes.pdf", title = "My Notes", checksum = "abc123")
        val id = db.knowledgeDocumentDao().insert(doc)
        assertTrue(id > 0)

        val all = db.knowledgeDocumentDao().getAll().first()
        assertEquals(1, all.size)
        assertEquals("My Notes", all[0].title)
    }

    @Test
    fun getBySourceUri_returnsCorrectDoc() = runTest {
        db.knowledgeDocumentDao().insert(KnowledgeDocumentEntity(sourceType = "url", sourceUri = "https://example.com", title = "Example", checksum = "def"))
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
        val id = db.knowledgeDocumentDao().insert(KnowledgeDocumentEntity(sourceType = "note", sourceUri = "note://1", title = "Note", checksum = "xyz"))
        val doc = db.knowledgeDocumentDao().getBySourceUri("note://1")!!
        db.knowledgeDocumentDao().delete(doc)
        assertTrue(db.knowledgeDocumentDao().getAll().first().isEmpty())
    }

    @Test
    fun update_changesChunkCount() = runTest {
        val id = db.knowledgeDocumentDao().insert(KnowledgeDocumentEntity(sourceType = "doc", sourceUri = "doc://1", title = "Doc", checksum = "aaa"))
        val doc = db.knowledgeDocumentDao().getBySourceUri("doc://1")!!
        db.knowledgeDocumentDao().update(doc.copy(chunkCount = 42))

        val updated = db.knowledgeDocumentDao().getBySourceUri("doc://1")!!
        assertEquals(42, updated.chunkCount)
    }
}
```

- [ ] **Step 2: Create `KnowledgeDocumentEntity.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/entity/KnowledgeDocumentEntity.kt
package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata for a document (or conversation source) that has been chunked
 * and indexed into the RAG pipeline.
 */
@Entity(
    tableName = "knowledge_documents",
    indices = [Index(value = ["source_uri"], unique = true)],
)
data class KnowledgeDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "pdf" | "url" | "sms" | "call" | "note" | "contact" | "calendar" */
    @ColumnInfo(name = "source_type") val sourceType: String,
    /** Unique URI identifying the source (file path, URL, or synthetic URI for system data). */
    @ColumnInfo(name = "source_uri") val sourceUri: String,
    val title: String,
    @ColumnInfo(name = "chunk_count") val chunkCount: Int = 0,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long = System.currentTimeMillis(),
    /** SHA-256 of the source content — used to skip re-indexing unchanged documents. */
    val checksum: String = "",
)
```

- [ ] **Step 3: Update `ChunkEntity.kt` to add document FK + stable per-document ordering**

Replace the file contents:

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/entity/ChunkEntity.kt
package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A RAG text chunk from a document or conversation source.
 * The float embedding for this chunk is stored in VectorStore keyed by [id].
 */
@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("document_id"),
        Index("contact_filter"),
        Index(value = ["document_id", "chunk_index"], unique = true),
    ],
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    /** "sms" | "call" | "note" | "contact" | "doc" | "calendar" */
    @ColumnInfo(name = "source_type") val sourceType: String,
    /** Normalized phone number for contact-scoped retrieval; null for global chunks. */
    @ColumnInfo(name = "contact_filter") val contactFilter: String?,
    @ColumnInfo(name = "chunk_text") val chunkText: String,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int,
)
```

- [ ] **Step 4: Create `KnowledgeDocumentDao.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/dao/KnowledgeDocumentDao.kt
package com.agendroid.core.data.dao

import androidx.room.*
import com.agendroid.core.data.entity.KnowledgeDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDocumentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(doc: KnowledgeDocumentEntity): Long

    @Update
    suspend fun update(doc: KnowledgeDocumentEntity)

    @Delete
    suspend fun delete(doc: KnowledgeDocumentEntity)

    @Query("SELECT * FROM knowledge_documents ORDER BY indexed_at DESC")
    fun getAll(): Flow<List<KnowledgeDocumentEntity>>

    @Query("SELECT * FROM knowledge_documents WHERE source_uri = :sourceUri LIMIT 1")
    suspend fun getBySourceUri(sourceUri: String): KnowledgeDocumentEntity?

    @Query("SELECT * FROM knowledge_documents WHERE id = :documentId LIMIT 1")
    suspend fun getById(documentId: Long): KnowledgeDocumentEntity?
}
```

- [ ] **Step 5: Update `AppDatabase.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt
package com.agendroid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agendroid.core.data.dao.ChunkDao
import com.agendroid.core.data.dao.KnowledgeDocumentDao
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.entity.KnowledgeDocumentEntity

@Database(
    entities = [
        ChunkEntity::class,
        KnowledgeDocumentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun knowledgeDocumentDao(): KnowledgeDocumentDao
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.dao.KnowledgeDocumentDaoTest"
```

Expected: 5 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add core/data/src/
git commit -m "feat(data): add KnowledgeDocumentEntity + KnowledgeDocumentDao"
```

---

## Task 5: ConversationSummaryEntity + ConversationSummaryDao

**Files:**
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/ConversationSummaryEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/ConversationSummaryDao.kt`
- Modify: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/ConversationSummaryDaoTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/ConversationSummaryDaoTest.kt
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
```

- [ ] **Step 2: Create `ConversationSummaryEntity.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/entity/ConversationSummaryEntity.kt
package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * AI-generated summary of a contact's SMS conversation or call history.
 * Keyed by (contactKey, type) — one row per normalized phone/address key per type.
 * Upserted by AiCoreService after each significant exchange.
 */
@Entity(
    tableName = "conversation_summaries",
    primaryKeys = ["contact_key", "type"],
)
data class ConversationSummaryEntity(
    @ColumnInfo(name = "contact_key") val contactKey: String,
    /** "sms" or "call" */
    val type: String,
    val summary: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 3: Create `ConversationSummaryDao.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/dao/ConversationSummaryDao.kt
package com.agendroid.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendroid.core.data.entity.ConversationSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: ConversationSummaryEntity)

    @Query("SELECT * FROM conversation_summaries WHERE contact_key = :contactKey")
    fun getForContactKey(contactKey: String): Flow<List<ConversationSummaryEntity>>

    @Query("SELECT * FROM conversation_summaries WHERE contact_key = :contactKey AND type = :type LIMIT 1")
    suspend fun get(contactKey: String, type: String): ConversationSummaryEntity?
}
```

- [ ] **Step 4: Update `AppDatabase.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt
package com.agendroid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agendroid.core.data.dao.ChunkDao
import com.agendroid.core.data.dao.ConversationSummaryDao
import com.agendroid.core.data.dao.KnowledgeDocumentDao
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.entity.ConversationSummaryEntity
import com.agendroid.core.data.entity.KnowledgeDocumentEntity

@Database(
    entities = [
        ChunkEntity::class,
        KnowledgeDocumentEntity::class,
        ConversationSummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun knowledgeDocumentDao(): KnowledgeDocumentDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.dao.ConversationSummaryDaoTest"
```

Expected: 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/data/src/
git commit -m "feat(data): add ConversationSummaryEntity + ConversationSummaryDao"
```

---

## Task 6: NoteEntity + ContactPreferenceEntity with DAOs

**Files:**
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/NoteEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/NoteDao.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/ContactPreferenceEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/ContactPreferenceDao.kt`
- Modify: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/NoteDaoTest.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/ContactPreferenceDaoTest.kt`

- [ ] **Step 1: Write failing tests for NoteDao**

```kotlin
// core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/NoteDaoTest.kt
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
```

- [ ] **Step 2: Write failing test for ContactPreferenceDao**

```kotlin
// core/data/src/androidTest/kotlin/com/agendroid/core/data/dao/ContactPreferenceDaoTest.kt
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
```

- [ ] **Step 3: Create `NoteEntity.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/entity/NoteEntity.kt
package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    /** Comma-separated tags for filtering. */
    val tags: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 4: Create `NoteDao.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/dao/NoteDao.kt
package com.agendroid.core.data.dao

import androidx.room.*
import com.agendroid.core.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?
}
```

- [ ] **Step 5: Create `ContactPreferenceEntity.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/entity/ContactPreferenceEntity.kt
package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-contact override of global AI autonomy settings.
 * Global defaults apply when no row exists for a normalized contact key.
 */
@Entity(tableName = "contact_preferences")
data class ContactPreferenceEntity(
    @PrimaryKey @ColumnInfo(name = "contact_key") val contactKey: String,
    /** "auto" | "semi" | "manual" */
    @ColumnInfo(name = "sms_autonomy") val smsAutonomy: String = "semi",
    /** "agent" | "screen" | "passthrough" */
    @ColumnInfo(name = "call_handling") val callHandling: String = "screen",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 6: Create `ContactPreferenceDao.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/dao/ContactPreferenceDao.kt
package com.agendroid.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendroid.core.data.entity.ContactPreferenceEntity

@Dao
interface ContactPreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: ContactPreferenceEntity)

    @Query("SELECT * FROM contact_preferences WHERE contact_key = :contactKey LIMIT 1")
    suspend fun get(contactKey: String): ContactPreferenceEntity?

    @Query("DELETE FROM contact_preferences WHERE contact_key = :contactKey")
    suspend fun delete(contactKey: String)
}
```

- [ ] **Step 7: Update `AppDatabase.kt`** (final entity list — all 5 entities)

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt
package com.agendroid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agendroid.core.data.dao.*
import com.agendroid.core.data.entity.*

@Database(
    entities = [
        ChunkEntity::class,
        KnowledgeDocumentEntity::class,
        ConversationSummaryEntity::class,
        NoteEntity::class,
        ContactPreferenceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun knowledgeDocumentDao(): KnowledgeDocumentDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao
    abstract fun noteDao(): NoteDao
    abstract fun contactPreferenceDao(): ContactPreferenceDao
}
```

Also replace `DatabaseModule.kt` with the complete final version (adding DAO providers):

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/db/DatabaseModule.kt
package com.agendroid.core.data.db

import android.content.Context
import androidx.room.Room
import com.agendroid.core.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyManager: KeystoreKeyManager,
    ): AppDatabase {
        // SQLiteDatabase.getBytes(charArray) treats each char as a raw byte.
        // ISO_8859_1 is the only charset where byte 0x00–0xFF maps 1:1 to char 0x00–0xFF,
        // so this preserves the full 32-byte random key without corruption.
        val passphrase = SQLiteDatabase.getBytes(
            keyManager.getOrCreateKey().toString(Charsets.ISO_8859_1).toCharArray()
        )
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, "agendroid.db")
            .openHelperFactory(factory)
            .build()
    }

    @Provides @Singleton fun provideChunkDao(db: AppDatabase): ChunkDao = db.chunkDao()
    @Provides @Singleton fun provideKnowledgeDocumentDao(db: AppDatabase): KnowledgeDocumentDao = db.knowledgeDocumentDao()
    @Provides @Singleton fun provideConversationSummaryDao(db: AppDatabase): ConversationSummaryDao = db.conversationSummaryDao()
    @Provides @Singleton fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides @Singleton fun provideContactPreferenceDao(db: AppDatabase): ContactPreferenceDao = db.contactPreferenceDao()
}
```

- [ ] **Step 8: Run all DAO tests**

```bash
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.dao.*"
```

Expected: all 21 tests PASS (ChunkDao 5, KnowledgeDocumentDao 5, ConversationSummaryDao 4, NoteDao 3, ContactPreferenceDao 4).

- [ ] **Step 9: Commit**

```bash
git add core/data/src/
git commit -m "feat(data): add NoteEntity, ContactPreferenceEntity and their DAOs; finalize AppDatabase schema v1"
```

---

## Task 7: VectorStore (sqlite-vec)

The `VectorStore` opens a private SQLite database file, loads the sqlite-vec extension, creates a `chunks_vec` virtual table, and exposes insert/query/delete primitives. It is the only file in the project that knows about float embeddings — all callers pass `FloatArray`, get back `List<VectorResult>`. Production code must not mutate it directly; Task 8 adds the coordinator that keeps it aligned with Room.

**Files:**
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorResult.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStore.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStoreModule.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/vector/VectorStoreTest.kt`

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
// core/data/src/androidTest/kotlin/com/agendroid/core/data/vector/VectorStoreTest.kt
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
        // Clean up test file
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
```

- [ ] **Step 2: Create `VectorResult.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorResult.kt
package com.agendroid.core.data.vector

/** A result from a VectorStore similarity query. */
data class VectorResult(
    /** Matches ChunkEntity.id in the Room database. */
    val chunkId: Long,
    /** L2 distance from the query embedding — lower is more similar. */
    val distance: Float,
)
```

- [ ] **Step 3: Create `VectorStore.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStore.kt
package com.agendroid.core.data.vector

import android.content.Context
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import io.github.asg017.sqlitevec.SqliteVecAndroid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

private const val EMBEDDING_DIM = 384

/**
 * Wraps the sqlite-vec extension to store and query 384-dimensional float embeddings.
 *
 * The database file lives in app-private storage (`context.getDatabasePath(dbName)`).
 * Only embedding vectors are stored here — the matching text is in Room's AppDatabase
 * under `chunks.chunk_text`, joined by chunk_id.
 *
 * Thread safety: sqlite-vec operations are blocking I/O. Call from Dispatchers.IO.
 */
@Singleton
class VectorStore @Inject constructor(
    private val context: Context,
    private val dbName: String = "vectors.db",
) {
    private val db: SQLiteDatabase by lazy { openDatabase() }

    private fun openDatabase(): SQLiteDatabase {
        // Register the sqlite-vec extension with Android's SQLite
        SqliteVecAndroid.init(context)

        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS chunks_vec USING vec0(
                chunk_id INTEGER PRIMARY KEY,
                embedding FLOAT[$EMBEDDING_DIM]
            )
        """.trimIndent())
        return db
    }

    /** Stores or replaces the embedding for [chunkId]. Call from Dispatchers.IO. */
    fun insert(chunkId: Long, embedding: FloatArray) {
        require(embedding.size == EMBEDDING_DIM) {
            "Expected $EMBEDDING_DIM floats, got ${embedding.size}"
        }
        db.execSQL(
            "INSERT OR REPLACE INTO chunks_vec (chunk_id, embedding) VALUES (?, ?)",
            arrayOf(chunkId, embedding.toVecBytes()),
        )
    }

    /**
     * Returns the [limit] most similar stored embeddings, ordered by ascending L2 distance.
     * Returns an empty list if the store is empty.
     * Call from Dispatchers.IO.
     */
    fun query(embedding: FloatArray, limit: Int = 5): List<VectorResult> {
        require(embedding.size == EMBEDDING_DIM) {
            "Expected $EMBEDDING_DIM floats, got ${embedding.size}"
        }
        require(limit > 0) { "limit must be > 0" }
        val results = mutableListOf<VectorResult>()
        val cursor = db.rawQueryWithFactory(
            { _, driver, editTable, query ->
                query.bindBlob(1, embedding.toVecBytes())
                SQLiteCursor(driver, editTable, query)
            },
            "SELECT chunk_id, distance FROM chunks_vec WHERE embedding MATCH ? AND k = $limit",
            emptyArray(),
            null,
        )
        cursor.use {
            while (it.moveToNext()) {
                results += VectorResult(
                    chunkId = it.getLong(0),
                    distance = it.getFloat(1),
                )
            }
        }
        return results
    }

    /** Removes the embedding for [chunkId]. Call from Dispatchers.IO. */
    fun delete(chunkId: Long) {
        db.execSQL("DELETE FROM chunks_vec WHERE chunk_id = ?", arrayOf(chunkId))
    }

    /** Removes multiple embeddings; used by KnowledgeIndexRepository compensation paths. */
    fun deleteAll(chunkIds: List<Long>) {
        if (chunkIds.isEmpty()) return
        db.beginTransaction()
        try {
            chunkIds.forEach { delete(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Returns all indexed chunk IDs. Used to prune stale vectors after partial failures. */
    fun listChunkIds(): Set<Long> {
        val ids = linkedSetOf<Long>()
        db.rawQuery("SELECT chunk_id FROM chunks_vec", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                ids += cursor.getLong(0)
            }
        }
        return ids
    }

    fun close() {
        if (db.isOpen) db.close()
    }

    private fun FloatArray.toVecBytes(): ByteArray {
        val buf = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        forEach { buf.putFloat(it) }
        return buf.array()
    }
}
```

- [ ] **Step 4: Create `VectorStoreModule.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStoreModule.kt
package com.agendroid.core.data.vector

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VectorStoreModule {

    @Provides
    @Singleton
    fun provideVectorStore(@ApplicationContext context: Context): VectorStore =
        VectorStore(context)
}
```

- [ ] **Step 5: Run instrumented tests**

```bash
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.vector.VectorStoreTest"
```

Expected: 5 tests PASS. If sqlite-vec fails to load (native library issue), check device ABI — the `sqlite-vec-android-bundled` artifact must include the correct `.so` for the device's ABI (arm64-v8a for OnePlus 12).

**Troubleshooting:** If `SqliteVecAndroid` class is not found, check the exact class name in the sqlite-vec-android-bundled AAR with:
```bash
cd ~/.gradle/caches && find . -name "sqlite-vec-android-bundled*.aar" | head -1 | xargs -I{} unzip -l {} | grep "SqliteVec"
```

- [ ] **Step 6: Commit**

```bash
git add core/data/src/
git commit -m "feat(data): add VectorStore with sqlite-vec for embedding storage"
```

---

## Task 8: KnowledgeIndexRepository (Room/vector consistency)

This repository is the only write path allowed to mutate both `AppDatabase` and `VectorStore`. It replaces document chunks, deletes document indexes, and prunes orphan vectors so retrieval never returns stale chunk IDs after partial failures or re-indexes.

**Files:**
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/KnowledgeIndexRepository.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/repository/KnowledgeIndexRepositoryTest.kt`

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package com.agendroid.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.entity.KnowledgeDocumentEntity
import com.agendroid.core.data.vector.VectorStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeIndexRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var vectorStore: VectorStore
    private lateinit var repository: KnowledgeIndexRepository
    private var documentId: Long = 0L

    @Before
    fun setup() {
        runTest {
            context = ApplicationProvider.getApplicationContext()
            context.deleteDatabase("knowledge-index-test.db")
            context.getDatabasePath("knowledge-index-vectors.db").delete()

            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            vectorStore = VectorStore(context, dbName = "knowledge-index-vectors.db")
            repository = KnowledgeIndexRepositoryImpl(db, vectorStore)
            documentId = db.knowledgeDocumentDao().insert(
                KnowledgeDocumentEntity(
                    sourceType = "pdf",
                    sourceUri = "file:///doc.pdf",
                    title = "Doc",
                    checksum = "checksum",
                )
            )
        }
    }

    @After
    fun teardown() {
        db.close()
        vectorStore.close()
        context.getDatabasePath("knowledge-index-vectors.db").delete()
    }

    @Test
    fun replaceDocumentChunks_replacesRoomRowsAndVectors() = runTest {
        val firstChunks = listOf(
            ChunkEntity(documentId = documentId, sourceType = "pdf", contactFilter = null, chunkText = "one", chunkIndex = 0),
            ChunkEntity(documentId = documentId, sourceType = "pdf", contactFilter = null, chunkText = "two", chunkIndex = 1),
        )
        val secondChunks = listOf(
            ChunkEntity(documentId = documentId, sourceType = "pdf", contactFilter = null, chunkText = "updated", chunkIndex = 0),
        )

        repository.replaceDocumentChunks(documentId, firstChunks, List(2) { FloatArray(384) { it.toFloat() } })
        repository.replaceDocumentChunks(documentId, secondChunks, listOf(FloatArray(384) { 0.5f }))

        assertEquals(1, db.chunkDao().getByDocumentId(documentId).size)
        assertEquals(1, vectorStore.listChunkIds().size)
    }

    @Test
    fun deleteDocumentIndex_removesRoomRowsAndVectors() = runTest {
        val chunks = listOf(
            ChunkEntity(documentId = documentId, sourceType = "pdf", contactFilter = null, chunkText = "one", chunkIndex = 0),
        )
        repository.replaceDocumentChunks(documentId, chunks, listOf(FloatArray(384) { 1f }))

        repository.deleteDocumentIndex(documentId)

        assertTrue(db.chunkDao().getByDocumentId(documentId).isEmpty())
        assertTrue(vectorStore.listChunkIds().isEmpty())
    }

    @Test
    fun pruneOrphanVectors_deletesVectorsMissingFromRoom() = runTest {
        vectorStore.insert(999L, FloatArray(384) { 1f })

        repository.pruneOrphanVectors()

        assertTrue(vectorStore.listChunkIds().isEmpty())
    }
}
```

- [ ] **Step 2: Create `KnowledgeIndexRepository.kt`**

```kotlin
package com.agendroid.core.data.repository

import androidx.room.withTransaction
import com.agendroid.core.common.Result
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.vector.VectorStore
import javax.inject.Inject
import javax.inject.Singleton

interface KnowledgeIndexRepository {
    suspend fun replaceDocumentChunks(
        documentId: Long,
        chunks: List<ChunkEntity>,
        embeddings: List<FloatArray>,
    ): Result<List<Long>>

    suspend fun deleteDocumentIndex(documentId: Long): Result<Unit>

    suspend fun pruneOrphanVectors(): Result<Unit>
}

@Singleton
class KnowledgeIndexRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val vectorStore: VectorStore,
) : KnowledgeIndexRepository {

    override suspend fun replaceDocumentChunks(
        documentId: Long,
        chunks: List<ChunkEntity>,
        embeddings: List<FloatArray>,
    ): Result<List<Long>> {
        if (chunks.size != embeddings.size) {
            return Result.Failure(
                IllegalArgumentException("chunks.size (${chunks.size}) must match embeddings.size (${embeddings.size})")
            )
        }

        return try {
            val existingIds = db.chunkDao().getIdsByDocumentId(documentId)
            val insertedIds = db.withTransaction {
                db.chunkDao().deleteByDocumentId(documentId)
                db.chunkDao().insertAll(chunks)
            }

            try {
                vectorStore.deleteAll(existingIds)
                insertedIds.zip(embeddings).forEach { (chunkId, embedding) ->
                    vectorStore.insert(chunkId, embedding)
                }
            } catch (t: Throwable) {
                db.withTransaction { db.chunkDao().deleteByDocumentId(documentId) }
                vectorStore.deleteAll(insertedIds)
                throw t
            }

            Result.Success(insertedIds)
        } catch (t: Throwable) {
            Result.Failure(t)
        }
    }

    override suspend fun deleteDocumentIndex(documentId: Long): Result<Unit> {
        return try {
            val existingIds = db.chunkDao().getIdsByDocumentId(documentId)
            db.withTransaction {
                db.chunkDao().deleteByDocumentId(documentId)
                db.knowledgeDocumentDao().getById(documentId)?.let { db.knowledgeDocumentDao().delete(it) }
            }
            vectorStore.deleteAll(existingIds)
            Result.Success(Unit)
        } catch (t: Throwable) {
            Result.Failure(t)
        }
    }

    override suspend fun pruneOrphanVectors(): Result<Unit> {
        return try {
            val roomIds = db.chunkDao().getAllIds().toSet()
            val orphanIds = vectorStore.listChunkIds() - roomIds
            vectorStore.deleteAll(orphanIds.toList())
            Result.Success(Unit)
        } catch (t: Throwable) {
            Result.Failure(t)
        }
    }
}
```

- [ ] **Step 3: Run the consistency tests**

```bash
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.repository.KnowledgeIndexRepositoryTest"
```

Expected: 3 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add core/data/src/
git commit -m "feat(data): add KnowledgeIndexRepository to coordinate Room and VectorStore"
```

---

## Task 9: Content provider repositories

These repositories wrap Android's system content providers behind interfaces. They require granted permissions at runtime. Unlike the earlier draft, this task includes targeted normalization tests and smoke tests so the most device-specific code is exercised directly instead of being trusted implicitly.

**Files:**
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/model/ContactInfo.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/model/SmsThread.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/model/SmsMessage.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/model/CallLogEntry.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/util/PhoneNumberNormalizer.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/ContactsRepository.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/SmsThreadRepository.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/CallLogRepository.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/RepositoriesModule.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/util/PhoneNumberNormalizerTest.kt`
- Create: `core/data/src/androidTest/kotlin/com/agendroid/core/data/repository/ProviderRepositorySmokeTest.kt`

- [ ] **Step 1: Create model data classes**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/model/ContactInfo.kt
package com.agendroid.core.data.model

data class ContactInfo(
    val contactId: String,
    val displayName: String,
    /** Normalized dialable number: E.164 when possible, digits-only fallback otherwise. */
    val phoneNumber: String,
    val photoUri: String?,
)
```

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/model/SmsThread.kt
package com.agendroid.core.data.model

data class SmsThread(
    val threadId: Long,
    /** Normalized phone/address key for contact matching and autonomy rules. */
    val participantKey: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val subscriptionId: Int?,
)
```

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/model/SmsMessage.kt
package com.agendroid.core.data.model

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val rawAddress: String,
    /** Normalized phone/address key for lookups and summaries. */
    val addressKey: String,
    val body: String,
    val date: Long,
    val type: Int,  // Telephony.Sms.MESSAGE_TYPE_INBOX / _SENT / etc.
    val read: Boolean,
    val subscriptionId: Int?,
)
```

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/model/CallLogEntry.kt
package com.agendroid.core.data.model

data class CallLogEntry(
    val id: Long,
    val rawNumber: String,
    val numberKey: String,
    val name: String?,
    val date: Long,
    val duration: Long,
    /** CallLog.Calls.INCOMING_TYPE / OUTGOING_TYPE / MISSED_TYPE */
    val callType: Int,
)
```

- [ ] **Step 2: Create `PhoneNumberNormalizer.kt` and its contract test**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/util/PhoneNumberNormalizer.kt
package com.agendroid.core.data.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneNumberNormalizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun normalize(raw: String?, regionIsoOverride: String? = null): String {
        if (raw.isNullOrBlank()) return ""

        val trimmed = raw.trim()
        val regionIso = regionIsoOverride
            ?.takeIf { it.isNotBlank() }
            ?: context.getSystemService(TelephonyManager::class.java)
                ?.networkCountryIso
                ?.takeIf { it.isNotBlank() }
            ?: context.getSystemService(TelephonyManager::class.java)
                ?.simCountryIso
                ?.takeIf { it.isNotBlank() }

        val e164 = regionIso?.let { PhoneNumberUtils.formatNumberToE164(trimmed, it.uppercase()) }
        val normalized = e164 ?: PhoneNumberUtils.normalizeNumber(trimmed)
        return normalized.ifBlank { trimmed }
    }
}
```

```kotlin
// core/data/src/androidTest/kotlin/com/agendroid/core/data/util/PhoneNumberNormalizerTest.kt
package com.agendroid.core.data.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneNumberNormalizerTest {

    private val normalizer = PhoneNumberNormalizer(ApplicationProvider.getApplicationContext())

    @Test
    fun normalize_formatsUsNumberToE164WhenRegionKnown() {
        assertEquals("+14155550123", normalizer.normalize("(415) 555-0123", regionIsoOverride = "US"))
    }

    @Test
    fun normalize_fallsBackToDigitsOnlyWhenE164Unavailable() {
        assertEquals("4155550123", normalizer.normalize("415-555-0123", regionIsoOverride = "ZZ"))
    }
}
```

- [ ] **Step 3: Create `ContactsRepository.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/repository/ContactsRepository.kt
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
```

- [ ] **Step 4: Create `SmsThreadRepository.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/repository/SmsThreadRepository.kt
package com.agendroid.core.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import com.agendroid.core.data.model.SmsMessage
import com.agendroid.core.data.model.SmsThread
import com.agendroid.core.common.Result
import com.agendroid.core.data.util.PhoneNumberNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface SmsThreadRepository {
    /**
     * Emits a single snapshot of SMS threads. Not a live stream — re-collect when
     * SmsReceiver fires (Plan 7) to refresh. This is intentional: live SMS updates
     * come via BroadcastReceiver, not ContentObserver, so no persistent observer is needed here.
     */
    fun getThreads(): Flow<List<SmsThread>>
    suspend fun getMessages(threadId: Long, limit: Int = 50): List<SmsMessage>
    suspend fun sendSms(to: String, body: String, subscriptionId: Int? = null): Result<Unit>
}

@Singleton
class SmsThreadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val normalizer: PhoneNumberNormalizer,
) : SmsThreadRepository {

    private fun smsManagerFor(subscriptionId: Int?): SmsManager {
        val manager = context.getSystemService(SmsManager::class.java)
        return if (subscriptionId != null) manager.createForSubscriptionId(subscriptionId) else manager
    }

    override fun getThreads(): Flow<List<SmsThread>> = flow {
        // NOTE: Telephony.Sms.Conversations.CONTENT_URI does NOT reliably expose ADDRESS
        // or DATE on all OEM implementations (those columns are message-level, not thread-level).
        // Safe approach: query all SMS sorted by date DESC and group by thread_id in memory.
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.SUBSCRIPTION_ID,
            ),
            null, null,
            "${Telephony.Sms.DATE} DESC",
        )
        // LinkedHashMap preserves date-DESC insertion order (= most-recent-first per thread).
        val threads = linkedMapOf<Long, SmsThread>()
        cursor?.use {
            val colThread  = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val colAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val colBody    = it.getColumnIndex(Telephony.Sms.BODY)
            val colDate    = it.getColumnIndex(Telephony.Sms.DATE)
            val colSubId   = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            while (it.moveToNext()) {
                val threadId = it.getLong(colThread)
                if (!threads.containsKey(threadId)) {
                    val rawAddress = it.getString(colAddress) ?: ""
                    threads[threadId] = SmsThread(
                        threadId = threadId,
                        participantKey = normalizer.normalize(rawAddress),
                        snippet = (it.getString(colBody) ?: "").take(80),
                        date = it.getLong(colDate),
                        unreadCount = 0,  // computed separately if needed
                        subscriptionId = if (colSubId >= 0 && !it.isNull(colSubId)) it.getInt(colSubId) else null,
                    )
                }
            }
        }
        emit(threads.values.toList())
    }

    override suspend fun getMessages(threadId: Long, limit: Int): List<SmsMessage> {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.SUBSCRIPTION_ID,
            ),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        ) ?: return emptyList()

        return cursor.use {
            val messages = mutableListOf<SmsMessage>()
            while (it.moveToNext()) {
                messages += SmsMessage(
                    id = it.getLong(0),
                    threadId = it.getLong(1),
                    rawAddress = it.getString(2) ?: "",
                    addressKey = normalizer.normalize(it.getString(2)),
                    body = it.getString(3) ?: "",
                    date = it.getLong(4),
                    type = it.getInt(5),
                    read = it.getInt(6) == 1,
                    subscriptionId = if (!it.isNull(7)) it.getInt(7) else null,
                )
            }
            messages
        }
    }

    override suspend fun sendSms(to: String, body: String, subscriptionId: Int?): Result<Unit> {
        return try {
            smsManagerFor(subscriptionId)
                .sendTextMessage(to, null, body, null, null)

            // Record the sent message only after a successful send call.
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, to)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
                subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
```

- [ ] **Step 5: Create `CallLogRepository.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/repository/CallLogRepository.kt
package com.agendroid.core.data.repository

import android.content.Context
import android.provider.CallLog
import com.agendroid.core.data.model.CallLogEntry
import com.agendroid.core.data.util.PhoneNumberNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface CallLogRepository {
    fun getCallLog(limit: Int = 100): Flow<List<CallLogEntry>>
    suspend fun getCallsFromNumber(phoneNumber: String, limit: Int = 20): List<CallLogEntry>
}

@Singleton
class CallLogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val normalizer: PhoneNumberNormalizer,
) : CallLogRepository {

    override fun getCallLog(limit: Int): Flow<List<CallLogEntry>> = flow {
        emit(query(limit = limit))
    }

    override suspend fun getCallsFromNumber(phoneNumber: String, limit: Int): List<CallLogEntry> {
        val target = normalizer.normalize(phoneNumber)
        return query(limit = maxOf(limit * 5, 50))
            .filter { it.numberKey == target }
            .take(limit)
    }

    private fun query(limit: Int): List<CallLogEntry> {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit",
        ) ?: return emptyList()

        return cursor.use {
            val entries = mutableListOf<CallLogEntry>()
            while (it.moveToNext()) {
                val rawNumber = it.getString(1) ?: ""
                entries += CallLogEntry(
                    id = it.getLong(0),
                    rawNumber = rawNumber,
                    numberKey = normalizer.normalize(rawNumber),
                    name = it.getString(2),
                    date = it.getLong(3),
                    duration = it.getLong(4),
                    callType = it.getInt(5),
                )
            }
            entries
        }
    }
}
```

- [ ] **Step 6: Create `RepositoriesModule.kt`**

```kotlin
// core/data/src/main/kotlin/com/agendroid/core/data/repository/RepositoriesModule.kt
package com.agendroid.core.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoriesModule {

    @Binds @Singleton
    abstract fun bindKnowledgeIndexRepository(impl: KnowledgeIndexRepositoryImpl): KnowledgeIndexRepository

    @Binds @Singleton
    abstract fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository

    @Binds @Singleton
    abstract fun bindSmsThreadRepository(impl: SmsThreadRepositoryImpl): SmsThreadRepository

    @Binds @Singleton
    abstract fun bindCallLogRepository(impl: CallLogRepositoryImpl): CallLogRepository
}
```

- [ ] **Step 7: Create `ProviderRepositorySmokeTest.kt`**

```kotlin
package com.agendroid.core.data.repository

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.agendroid.core.data.util.PhoneNumberNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderRepositorySmokeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val normalizer = PhoneNumberNormalizer(context)

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
    )

    @Test
    fun contactsRepository_queryDoesNotCrash_andReturnsNormalizedNumbers() = runTest {
        val repo = ContactsRepositoryImpl(context, normalizer)
        val contacts = repo.getAll()
        assertTrue(contacts.all { it.phoneNumber.isBlank() || !it.phoneNumber.contains(' ') })
    }

    @Test
    fun smsRepository_queriesDoNotCrash_andExposeNormalizedKeys() = runTest {
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
        val repo = CallLogRepositoryImpl(context, normalizer)
        val entries = repo.getCallLog(limit = 10).first()
        assertTrue(entries.all { !it.numberKey.contains(' ') })
    }
}
```

- [ ] **Step 8: Build and verify**

```bash
./gradlew :core:data:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Run repository-specific and full instrumented tests**

```bash
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.util.PhoneNumberNormalizerTest"
./gradlew :core:data:connectedAndroidTest --tests "com.agendroid.core.data.repository.ProviderRepositorySmokeTest"
./gradlew :core:data:connectedAndroidTest
```

Expected: normalization tests PASS, provider smoke tests PASS, and the full `:core:data` instrumented suite PASS.

Manual verification still required for actual SMS sending because that depends on a live SIM and default-SMS role:

1. Install the debug app on a physical dual-SIM device.
2. Make the app the default SMS handler.
3. Invoke `SmsThreadRepository.sendSms(..., subscriptionId = primarySubId)` and `subscriptionId = secondarySubId`.
4. Confirm the outbound message is sent on the requested SIM and appears in the sent provider.

- [ ] **Step 10: Commit**

```bash
git add core/data/src/
git commit -m "feat(data): add content provider repositories (Contacts, SMS, CallLog)"
```

---

## Final verification

- [ ] **Build all modules to confirm no downstream breakage**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` — all 10 modules compile.

- [ ] **Run :core:common tests to confirm nothing regressed**

```bash
./gradlew :core:common:test
```

Expected: PASS.

- [ ] **Final commit**

```bash
git add .
git commit -m "feat(data): :core:data complete — Room+SQLCipher, VectorStore, repositories"
```
