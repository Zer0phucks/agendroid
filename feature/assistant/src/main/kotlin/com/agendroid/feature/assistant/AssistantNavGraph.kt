package com.agendroid.feature.assistant

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agendroid.feature.assistant.integrations.IntegrationsSettingsScreen
import com.agendroid.feature.assistant.knowledge.KnowledgeBaseScreen
import com.agendroid.feature.assistant.overlay.AssistantOverlayScreen
import com.agendroid.feature.assistant.settings.AutonomySettingsScreen
import com.agendroid.feature.assistant.settings.ModelSettingsScreen

/**
 * Composable wrapper for the Assistant feature that hosts its own nested [NavHost].
 * Use this from the root [AgendroidNavHost] so the tab's route appears as a single
 * top-level destination, keeping bottom-bar tab selection consistent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AssistantRoutes.OVERLAY,
    ) {
        composable(route = AssistantRoutes.OVERLAY) {
            AssistantOverlayScreen(
                onDismiss = { navController.popBackStack() },
            )
        }

        composable(route = AssistantRoutes.SETTINGS_AUTONOMY) {
            AutonomySettingsScreen()
        }

        composable(route = AssistantRoutes.SETTINGS_MODEL) {
            ModelSettingsScreen()
        }

        composable(route = AssistantRoutes.KNOWLEDGE) {
            KnowledgeBaseScreen()
        }

        composable(route = AssistantRoutes.INTEGRATIONS) {
            IntegrationsSettingsScreen()
        }
    }
}

object AssistantRoutes {
    const val GRAPH = "assistant"
    const val OVERLAY = "assistant/overlay"
    const val SETTINGS_AUTONOMY = "assistant/settings/autonomy"
    const val SETTINGS_MODEL = "assistant/settings/model"
    const val KNOWLEDGE = "assistant/knowledge"
    const val INTEGRATIONS = "assistant/integrations"
}

