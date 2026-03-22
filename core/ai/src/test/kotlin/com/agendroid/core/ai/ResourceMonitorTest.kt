// core/ai/src/test/kotlin/com/agendroid/core/ai/ResourceMonitorTest.kt
package com.agendroid.core.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceMonitorTest {

    @Test
    fun `toResourceState returns Normal when temp lt 38 and battery gt 15`() {
        assertEquals(ResourceState.Normal, ResourceMonitor.toResourceState(tempCelsius = 30f, batteryPct = 80))
    }

    @Test
    fun `toResourceState returns Warm when 38 le temp lt 42`() {
        assertEquals(ResourceState.Warm, ResourceMonitor.toResourceState(tempCelsius = 39f, batteryPct = 50))
    }

    @Test
    fun `toResourceState returns Hot when temp ge 42`() {
        assertEquals(ResourceState.Hot, ResourceMonitor.toResourceState(tempCelsius = 42f, batteryPct = 50))
    }

    @Test
    fun `toResourceState returns Hot when battery between 10 and 14`() {
        assertEquals(ResourceState.Hot, ResourceMonitor.toResourceState(tempCelsius = 20f, batteryPct = 12))
    }

    @Test
    fun `toResourceState returns LowBattery when battery lt 10`() {
        assertEquals(ResourceState.LowBattery, ResourceMonitor.toResourceState(tempCelsius = 20f, batteryPct = 9))
    }

    @Test
    fun `toResourceState returns Warm for boundary temp 38`() {
        assertEquals(ResourceState.Warm, ResourceMonitor.toResourceState(tempCelsius = 38f, batteryPct = 80))
    }

    @Test
    fun `toResourceState treats negative temp sentinel as Normal`() {
        // ThermalManager returns -1f on unsupported devices (OnePlus 12 in spike)
        assertEquals(ResourceState.Normal, ResourceMonitor.toResourceState(tempCelsius = -1f, batteryPct = 80))
    }
}
