package com.agendroid.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
 * Root nav host for Agendroid.
 *
 * Routing:
 * - [AgendroidDestination.Onboarding] shows the multi-step onboarding flow.
 *   After completion the host navigates to [AgendroidDestination.Sms] and
 *   pops the onboarding destination off the back stack so the back button
 *   does not return to it.
 * - The three main tabs (SMS, Phone, Assistant) are wrapped in a [Scaffold]
 *   with a [NavigationBar].
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

    // Determine whether we are on a tab destination (to show the bottom bar)
    val showBottomBar = BOTTOM_TABS.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.destination.route } == true
    }

    Scaffold(
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
