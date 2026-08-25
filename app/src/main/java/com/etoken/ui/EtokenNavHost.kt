package com.etoken.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.etoken.ui.decks.DecksScreen
import com.etoken.ui.decks.DecksViewModel
import com.etoken.ui.tokens.TokensScreen
import com.etoken.ui.tokens.TokensViewModel
import com.etoken.ui.username.UsernameScreen

object Routes {
    const val USERNAME = "username"
    const val DECKS = "decks/{${DecksViewModel.ARG_USERNAME}}"
    const val TOKENS =
        "tokens/{${TokensViewModel.ARG_PUBLIC_ID}}?${TokensViewModel.ARG_DECK_NAME}={${TokensViewModel.ARG_DECK_NAME}}"

    // Usernames and deck names are free text, so both are encoded into the route.
    fun decks(username: String) = "decks/${Uri.encode(username)}"

    fun tokens(publicId: String, deckName: String) =
        "tokens/${Uri.encode(publicId)}?${TokensViewModel.ARG_DECK_NAME}=${Uri.encode(deckName)}"
}

@Composable
fun EtokenNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.USERNAME) {
        composable(Routes.USERNAME) {
            UsernameScreen(
                onSubmit = { username -> navController.navigate(Routes.decks(username)) },
            )
        }

        composable(
            route = Routes.DECKS,
            arguments = listOf(navArgument(DecksViewModel.ARG_USERNAME) { type = NavType.StringType }),
        ) {
            DecksScreen(
                onDeckClick = { deck ->
                    navController.navigate(Routes.tokens(deck.publicId, deck.name))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TOKENS,
            arguments = listOf(
                navArgument(TokensViewModel.ARG_PUBLIC_ID) { type = NavType.StringType },
                navArgument(TokensViewModel.ARG_DECK_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            TokensScreen(onBack = { navController.popBackStack() })
        }
    }
}
