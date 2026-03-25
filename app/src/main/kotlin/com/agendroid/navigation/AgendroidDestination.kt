package com.agendroid.navigation

/**
 * Top-level navigation destinations for the Agendroid app.
 *
 * String routes are used directly by [AgendroidNavHost].
 */
sealed interface AgendroidDestination {
    val route: String

    data object Onboarding : AgendroidDestination {
        override val route = "onboarding"
    }

    data object Sms : AgendroidDestination {
        override val route = "sms"
    }

    data object Phone : AgendroidDestination {
        override val route = "phone"
    }

    data object Assistant : AgendroidDestination {
        override val route = "assistant"
    }
}
