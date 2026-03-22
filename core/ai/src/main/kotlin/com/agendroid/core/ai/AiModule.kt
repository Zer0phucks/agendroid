// core/ai/src/main/kotlin/com/agendroid/core/ai/AiModule.kt
package com.agendroid.core.ai

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for :core:ai.
 *
 * Injectable singletons provided automatically (no explicit @Provides needed):
 *   - [RagOrchestrator] (@Singleton @Inject constructor)
 *   - [PromptBuilder]   (@Singleton @Inject constructor)
 *   - [ResourceMonitor] (@Singleton @Inject constructor)
 *
 * NOT provided here:
 *   - [LlmInferenceEngine] — owned by [AiCoreService], not injectable directly
 *   - [AiServiceInterface] — obtained via Android service binding (see Plans 7–8)
 *
 * Feature modules that need to call [AiServiceInterface] should implement an
 * AiServiceConnection helper (similar to [com.agendroid.core.data.repository] pattern)
 * that binds to [AiCoreService] and exposes the interface as a StateFlow.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule
