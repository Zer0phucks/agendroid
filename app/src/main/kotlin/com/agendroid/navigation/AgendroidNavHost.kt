package com.agendroid.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agendroid.core.ai.ResourceState
import com.agendroid.feature.assistant.AssistantNavGraph
import com.agendroid.feature.phone.PhoneNavGraph
import com.agendroid.feature.sms.ui.SmsNavGraph
import com.agendroid.onboarding.OnboardingScreen
import com.agendroid.onboarding.OnboardingViewModel

/** Bottom navigation tabs shown after onboarding. */
private data class BottomTab(
    val destination: AgendroidDestination,
    val label: String,
    val icon: @Composable () -> Unit,
)

private val BOTTOM_TABS = listOf(
    BottomTab(
        destination = AgendroidDestination.Sms,
        label = "Messages",
        icon = { Icon(Icons.Filled.Email, contentDescription = "Messages") },
    ),
    BottomTab(
        destination = AgendroidDestination.Phone,
        label = "Phone",
        icon = { Icon(Icons.Filled.Call, contentDescription = "Phone") },
    ),
    BottomTab(
        destination = AgendroidDestination.Assistant,
        label = "Assistant",
        icon = { Icon(Icons.Filled.Star, contentDescription = "Assistant") },
    ),
)

/**
 * Persistent banner shown at the top of the screen when the device is in a degraded state
 * ([ResourceState.Hot] or [ResourceState.LowBattery]).
 */
@Composable
private fun DegradedStateBanner(resourceState: ResourceState) {
    when (resourceState) {
        is ResourceState.LowBattery -> BannerMessage(
            message = "AI disabled — battery critically low",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        is ResourceState.Hot -> BannerMessage(
            message = "Device hot — AI running in low-power mode",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> Unit
    }
}

@Composable
private fun BannerMessage(
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Root nav host for Agendroid.
 *
 * Routing:
 * - [AgendroidDestination.Onboarding] shows the multi-step onboarding flow.
 *   After completion the host navigates to [AgendroidDestination.Sms] and
 *   pops the onboarding destination off the back stack so the back button
 *   does not return to it.
 * - The three main tabs (SMS, Phone, Assistant) are wrapped in a [Scaffold]
 *   with a [NavigationBar].
 * - A persistent top banner is shown when [ResourceState] is [ResourceState.Hot]
 *   or [ResourceState.LowBattery].
 *
 * @param startDestination Controls which destination is shown first.
 *   Pass [AgendroidDestination.Onboarding.route] when onboarding has not been
 *   completed, or [AgendroidDestination.Sms.route] otherwise.
 */
@Composable
fun AgendroidNavHost(
    startDestination: String = AgendroidDestination.Onboarding.route,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val resourceStateViewModel: ResourceStateViewModel = hiltViewModel()
    val resourceState by resourceStateViewModel.resourceState.collectAsState()

    // Determine whether we are on a tab destination (to show the bottom bar)
    val showBottomBar = BOTTOM_TABS.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.destination.route } == true
    }

    Scaffold(
        topBar = {
            DegradedStateBanner(resourceState = resourceState)
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_TABS.forEach { tab ->
                        NavigationBarItem(
                            icon = tab.icon,
                            label = { Text(tab.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == tab.destination.route
                            } == true,
                            onClick = {
                                navController.navigate(tab.destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Onboarding destination
            composable(route = AgendroidDestination.Onboarding.route) {
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onOnboardingComplete = {
                        navController.navigate(AgendroidDestination.Sms.route) {
                            popUpTo(AgendroidDestination.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }

            // SMS tab — hosts the SMS feature nav graph
            composable(route = AgendroidDestination.Sms.route) {
                SmsNavGraph()
            }

            // Phone tab — hosts the Phone feature nav graph
            composable(route = AgendroidDestination.Phone.route) {
                PhoneNavGraph()
            }

            // Assistant tab — wraps the assistant feature nav graph as a single
            // composable destination so bottom-bar tab selection works correctly
            composable(route = AgendroidDestination.Assistant.route) {
                AssistantNavGraph()
            }
        }
    }
}
