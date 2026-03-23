package com.agendroid.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendroid.core.data.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    /** Insert or replace the singleton settings row (always id = 1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(entity: AppSettingsEntity)
}
