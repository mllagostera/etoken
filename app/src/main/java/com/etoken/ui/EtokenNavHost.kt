package com.etoken.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.etoken.domain.DeckSource
import com.etoken.ui.board.BoardScreen
import com.etoken.ui.board.BoardViewModel
import com.etoken.ui.decks.DecksScreen
import com.etoken.ui.decks.DecksViewModel
import com.etoken.ui.username.UsernameScreen

object Routes {
    const val USERNAME = "username"
    const val DECKS =
        "decks/{${DecksViewModel.ARG_USERNAME}}?${DecksViewModel.ARG_FORMAT}={${DecksViewModel.ARG_FORMAT}}"

    /**
     * The battlefield, which is where a deck opens.
     *
     * There used to be a second destination for one token's board; the table
     * now holds every token at once, and what the deck can create is a picker
     * inside this screen rather than a screen of its own.
     */
    const val BOARD =
        "board/{${BoardViewModel.ARG_PUBLIC_ID}}?${BoardViewModel.ARG_DECK_NAME}={${BoardViewModel.ARG_DECK_NAME}}"

    // Usernames and deck names are free text, so both are encoded into the route.
    // The format goes in the route rather than being looked up from the username:
    // it is what tells the precons listing from a user who happens to be Wizards.
    fun decks(source: DeckSource) =
        "decks/${Uri.encode(source.username)}" +
            "?${DecksViewModel.ARG_FORMAT}=${Uri.encode(source.format.orEmpty())}"

    fun board(publicId: String, deckName: String) =
        "board/${Uri.encode(publicId)}?${BoardViewModel.ARG_DECK_NAME}=${Uri.encode(deckName)}"
}

@Composable
fun EtokenNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.USERNAME) {
        composable(Routes.USERNAME) {
            UsernameScreen(
                onSubmit = { username ->
                    navController.navigate(Routes.decks(DeckSource(username)))
                },
                onPrecons = { navController.navigate(Routes.decks(DeckSource.PRECONS)) },
            )
        }

        composable(
            route = Routes.DECKS,
            arguments = listOf(
                navArgument(DecksViewModel.ARG_USERNAME) { type = NavType.StringType },
                navArgument(DecksViewModel.ARG_FORMAT) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            DecksScreen(
                onDeckClick = { deck ->
                    navController.navigate(Routes.board(deck.publicId, deck.name))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.BOARD,
            arguments = listOf(
                navArgument(BoardViewModel.ARG_PUBLIC_ID) { type = NavType.StringType },
                navArgument(BoardViewModel.ARG_DECK_NAME) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            BoardScreen(onBack = { navController.popBackStack() })
        }
    }
}
