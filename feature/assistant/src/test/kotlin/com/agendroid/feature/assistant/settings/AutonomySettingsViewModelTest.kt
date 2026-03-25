package com.agendroid.feature.assistant.settings

import com.agendroid.core.data.entity.AppSettingsEntity
import com.agendroid.core.data.entity.IndexedSourceEntity
import com.agendroid.core.data.repository.AppSettingsRepository
import com.agendroid.core.data.repository.IndexedSourceRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.junit5.MockKExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class AutonomySettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val defaultSettings = AppSettingsEntity(
        id = 1,
        smsAutonomyMode = "SEMI",
        callAutonomyMode = "SCREEN_ONLY",
        assistantEnabled = true,
        selectedModel = "gemma3",
    )

    private val settingsFlow = MutableStateFlow<AppSettingsEntity?>(defaultSettings)
    private val sourcesFlow = MutableStateFlow<List<IndexedSourceEntity>>(emptyList())

    private val appSettingsRepository: AppSettingsRepository = mockk()
    private val indexedSourceRepository: IndexedSourceRepository = mockk()

    private lateinit var viewModel: AutonomySettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { appSettingsRepository.settingsFlow } returns settingsFlow
        every { indexedSourceRepository.sourcesFlow } returns sourcesFlow
        coEvery { appSettingsRepository.updateSmsMode(any()) } returns Unit
        coEvery { appSettingsRepository.updateCallMode(any()) } returns Unit
        coEvery { appSettingsRepository.updateSelectedModel(any()) } returns Unit
        viewModel = AutonomySettingsViewModel(appSettingsRepository, indexedSourceRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `changing SMS autonomy mode persists to AppSettingsRepository`() = runTest {
        viewModel.onSmsMode("AUTO")
        advanceUntilIdle()
        coVerify { appSettingsRepository.updateSmsMode("AUTO") }
    }

    @Test
    fun `changing call autonomy mode persists to AppSettingsRepository`() = runTest {
        viewModel.onCallMode("FULL_AGENT")
        advanceUntilIdle()
        coVerify { appSettingsRepository.updateCallMode("FULL_AGENT") }
    }

    @Test
    fun `model selection change persists and updates UI state`() = runTest {
        viewModel.onModelSelected("gemma2")
        advanceUntilIdle()
        coVerify { appSettingsRepository.updateSelectedModel("gemma2") }

        val updatedSettings = defaultSettings.copy(selectedModel = "gemma2")
        settingsFlow.value = updatedSettings
        advanceUntilIdle()

        assertEquals("gemma2", viewModel.settingsState.value?.selectedModel)
    }

    @Test
    fun `indexed source list loads from IndexedSourceRepository`() = runTest {
        val sources = listOf(
            IndexedSourceEntity(
                id = 1L,
                sourceType = "FILE",
                uri = "file:///doc.pdf",
                title = "My Doc",
                indexedAt = 1000L,
                status = "INDEXED",
            )
        )
        sourcesFlow.value = sources
        advanceUntilIdle()

        assertEquals(1, viewModel.sourcesState.value.size)
        assertEquals("My Doc", viewModel.sourcesState.value.first().title)
    }
}
