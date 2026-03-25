package com.agendroid.feature.sms.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agendroid.feature.sms.ui.conversation.ConversationScreen
import com.agendroid.feature.sms.ui.conversation.ConversationViewModel
import com.agendroid.feature.sms.ui.threads.SmsThreadsScreen
import com.agendroid.feature.sms.ui.threads.SmsThreadsViewModel

internal const val SMS_THREADS_ROUTE = "sms/threads"
internal const val SMS_CONVERSATION_ROUTE = "sms/conversation"
internal const val THREAD_ID_ARG = "threadId"
internal const val PARTICIPANT_ADDRESS_ARG = "participantAddress"
internal const val SUBSCRIPTION_ID_ARG = "subscriptionId"
internal const val NO_SUBSCRIPTION_ID = -1

internal fun smsConversationRoute(
    threadId: Long,
    participantAddress: String,
    subscriptionId: Int?,
): String {
    val encodedAddress = Uri.encode(participantAddress)
    val encodedSubscriptionId = subscriptionId ?: NO_SUBSCRIPTION_ID
    return "$SMS_CONVERSATION_ROUTE/$threadId/$encodedAddress/$encodedSubscriptionId"
}

@Composable
fun SmsNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = SMS_THREADS_ROUTE,
        modifier = modifier,
    ) {
        composable(route = SMS_THREADS_ROUTE) {
            val viewModel: SmsThreadsViewModel = hiltViewModel()
            SmsThreadsScreen(
                uiState = viewModel.uiState,
                onThreadSelected = { thread ->
                    navController.navigate(
                        smsConversationRoute(
                            threadId = thread.threadId,
                            participantAddress = thread.participantKey,
                            subscriptionId = thread.subscriptionId,
                        ),
                    )
                },
            )
        }

        composable(
            route = "$SMS_CONVERSATION_ROUTE/{$THREAD_ID_ARG}/{$PARTICIPANT_ADDRESS_ARG}/{$SUBSCRIPTION_ID_ARG}",
            arguments = listOf(
                navArgument(THREAD_ID_ARG) { type = NavType.LongType },
                navArgument(PARTICIPANT_ADDRESS_ARG) { type = NavType.StringType },
                navArgument(SUBSCRIPTION_ID_ARG) { type = NavType.IntType },
            ),
        ) {
            val viewModel: ConversationViewModel = hiltViewModel()
            ConversationScreen(
                uiState = viewModel.uiState,
                onBack = navController::popBackStack,
                onDraftChanged = viewModel::onDraftChanged,
                onSendClick = viewModel::sendReply,
            )
        }
    }
}
