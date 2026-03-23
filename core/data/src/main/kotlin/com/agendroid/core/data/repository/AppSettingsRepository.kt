package com.agendroid.core.data.repository

import com.agendroid.core.data.dao.AppSettingsDao
import com.agendroid.core.data.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val DEFAULT_SETTINGS = AppSettingsEntity(
    id = 1,
    smsAutonomyMode = "SEMI",
    callAutonomyMode = "SCREEN_ONLY",
    assistantEnabled = true,
    selectedModel = "gemma3",
)

/**
 * Repository for global AI assistant settings.
 *
 * All update functions are suspending and safe to call from Dispatchers.IO.
 * They read the current row (or use defaults) and upsert a modified copy.
 */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val dao: AppSettingsDao,
) {
    /** Emits the current settings row; null until first write. */
    val settingsFlow: Flow<AppSettingsEntity?> = dao.getSettings()

    suspend fun updateSmsMode(mode: String) {
        dao.upsertSettings(currentOrDefault().copy(smsAutonomyMode = mode))
    }

    suspend fun updateCallMode(mode: String) {
        dao.upsertSettings(currentOrDefault().copy(callAutonomyMode = mode))
    }

    suspend fun updateAssistantEnabled(enabled: Boolean) {
        dao.upsertSettings(currentOrDefault().copy(assistantEnabled = enabled))
    }

    suspend fun updateSelectedModel(model: String) {
        dao.upsertSettings(currentOrDefault().copy(selectedModel = model))
    }

    private suspend fun currentOrDefault(): AppSettingsEntity =
        dao.getSettings().first() ?: DEFAULT_SETTINGS
}
