package com.agendroid.feature.assistant

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.agendroid.feature.assistant.integrations.IntegrationsSettingsScreen
import com.agendroid.feature.assistant.knowledge.KnowledgeBaseScreen
import com.agendroid.feature.assistant.overlay.AssistantOverlayScreen
import com.agendroid.feature.assistant.settings.AutonomySettingsScreen
import com.agendroid.feature.assistant.settings.ModelSettingsScreen

object AssistantRoutes {
    const val GRAPH = "assistant"
    const val OVERLAY = "assistant/overlay"
    const val SETTINGS_AUTONOMY = "assistant/settings/autonomy"
    const val SETTINGS_MODEL = "assistant/settings/model"
    const val KNOWLEDGE = "assistant/knowledge"
    const val INTEGRATIONS = "assistant/integrations"
}

/**
 * Registers the assistant feature nav graph inside the host [NavGraphBuilder].
 *
 * Start destination: [AssistantRoutes.OVERLAY]
 */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.assistantNavGraph(navController: NavHostController) {
    navigation(
        route = AssistantRoutes.GRAPH,
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
