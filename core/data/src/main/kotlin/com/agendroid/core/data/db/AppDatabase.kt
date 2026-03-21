// core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt
package com.agendroid.core.data.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

/**
 * Main Room database. Encrypted at rest via SQLCipher (see DatabaseModule).
 * Entities are added incrementally in Tasks 3–6. Version is bumped with each
 * schema change; migration stubs must be added before version is incremented.
 *
 * Schema JSON files are exported to core/data/schemas/ — commit them.
 *
 * NOTE: _DbScaffold is a temporary placeholder required because Room KSP
 * rejects an empty entities list. It will be replaced/removed when real
 * entities are added in Tasks 3–6.
 */
@Entity(tableName = "_db_scaffold")
internal data class _DbScaffold(
    @PrimaryKey val id: Int = 0,
)

@Database(
    entities = [_DbScaffold::class],   // TODO(Tasks 3–6): replace with real entities
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase()
// DAOs added in Tasks 3–6
