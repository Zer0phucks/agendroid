package com.agendroid.feature.phone

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agendroid.feature.phone.calllog.CallLogScreen
import com.agendroid.feature.phone.calllog.CallLogViewModel
import com.agendroid.feature.phone.dialer.DialerScreen
import com.agendroid.feature.phone.dialer.DialerViewModel
import com.agendroid.feature.phone.incall.InCallScreen
import com.agendroid.feature.phone.incall.InCallViewModel

private const val DIALER_ROUTE = "phone/dialer"
private const val CALL_LOG_ROUTE = "phone/call-log"
private const val IN_CALL_ROUTE = "phone/in-call"

@Composable
fun PhoneNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = DIALER_ROUTE,
        modifier = modifier,
    ) {
        composable(route = DIALER_ROUTE) {
            val viewModel: DialerViewModel = hiltViewModel()
            DialerScreen(
                uiState = viewModel.uiState,
                onDigitPressed = viewModel::appendDigit,
                onBackspace = viewModel::removeDigit,
                onCallPressed = viewModel::prepareCallIntent,
                onShowCallLog = { navController.navigate(CALL_LOG_ROUTE) },
                onShowInCall = { navController.navigate(IN_CALL_ROUTE) },
            )
        }

        composable(route = CALL_LOG_ROUTE) {
            val viewModel: CallLogViewModel = hiltViewModel()
            CallLogScreen(
                uiState = viewModel.uiState,
                onBack = navController::popBackStack,
            )
        }

        composable(route = IN_CALL_ROUTE) {
            val viewModel: InCallViewModel = hiltViewModel()
            InCallScreen(
                uiState = viewModel.uiState,
                onBack = navController::popBackStack,
                onTakeover = viewModel::requestTakeover,
            )
        }
    }
}
