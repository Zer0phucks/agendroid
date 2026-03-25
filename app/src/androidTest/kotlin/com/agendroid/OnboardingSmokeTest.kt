package com.agendroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agendroid.navigation.AgendroidDestination
import com.agendroid.navigation.AgendroidNavHost
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests for the onboarding flow.
 *
 * These are instrumented tests that verify:
 * 1. The app launches into the onboarding screen when permissions are missing.
 * 2. After completing onboarding the app routes to the main nav host.
 *
 * Note: Full permission / role verification requires a real device with controllable
 * permission state. These tests verify the structural wiring (activity starts,
 * nav host composes without crashing) rather than real permission state, which
 * cannot be controlled in a unit/CI context.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Verifies the app launches into the onboarding screen on a fresh install.
     * On a device without roles granted the first onboarding step ("Set as Default
     * SMS App") should be visible.
     */
    @Test
    fun app_launches_into_onboarding_when_permissions_missing() {
        // The first onboarding step requests the SMS default role
        composeTestRule.onNodeWithText("Set as Default SMS App", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * Verifies that the main nav host renders the bottom navigation bar with the
     * expected tabs when started directly at the SMS destination (i.e. after
     * onboarding is complete). Uses the Hilt-injected compose rule so hiltViewModel()
     * calls inside AgendroidNavHost are satisfied.
     */
    @Test
    fun completed_onboarding_routes_to_main_nav_host() {
        composeTestRule.setContent {
            AgendroidNavHost(startDestination = AgendroidDestination.Sms.route)
        }
        // The bottom navigation bar "Messages" tab should be visible
        composeTestRule.onNodeWithText("Messages").assertIsDisplayed()
    }
}
