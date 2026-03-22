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
