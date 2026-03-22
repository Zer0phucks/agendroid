// core/ai/src/main/kotlin/com/agendroid/core/ai/ResourceState.kt
package com.agendroid.core.ai

/**
 * Represents the device thermal and battery resource state, per spec §11.2.
 *
 * Emitted by [ResourceMonitor] as a [kotlinx.coroutines.flow.Flow]. Feature modules
 * observe this to adapt UI (e.g., show a "Conserving battery" badge).
 *
 * Precedence: LowBattery > Hot > Warm > Normal
 */
sealed class ResourceState {
    /** SoC < 38 °C and battery ≥ 15 %. Full inference enabled. */
    data object Normal : ResourceState()

    /** 38 °C ≤ SoC < 42 °C. Use Gemma 3 1B (no-op in Plan 4). */
    data object Warm : ResourceState()

    /** SoC ≥ 42 °C or 10 % ≤ battery < 15 %. Use Gemma 3 1B; disable wake word (Plan 5). */
    data object Hot : ResourceState()

    /** Battery < 10 %. AI disabled; show notification only. */
    data object LowBattery : ResourceState()
}
