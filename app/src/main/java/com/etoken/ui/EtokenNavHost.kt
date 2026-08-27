package com.etoken.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.etoken.domain.DeckSource
import com.etoken.ui.board.TokenBoardScreen
import com.etoken.ui.board.TokenBoardViewModel
import com.etoken.ui.decks.DecksScreen
import com.etoken.ui.decks.DecksViewModel
import com.etoken.ui.tokens.TokensViewModel
import com.etoken.ui.username.UsernameScreen

object Routes {
    const val USERNAME = "username"
    const val DECKS =
        "decks/{${DecksViewModel.ARG_USERNAME}}?${DecksViewModel.ARG_FORMAT}={${DecksViewModel.ARG_FORMAT}}"
    const val TOKENS =
        "tokens/{${TokensViewModel.ARG_PUBLIC_ID}}?${TokensViewModel.ARG_DECK_NAME}={${TokensViewModel.ARG_DECK_NAME}}"
    const val BOARD =
        "board/{${TokenBoardViewModel.ARG_PUBLIC_ID}}/{${TokenBoardViewModel.ARG_TOKEN_ID}}"

    // Usernames and deck names are free text, so both are encoded into the route.
    // The format goes in the route rather than being looked up from the username:
    // it is what tells the precons listing from a user who happens to be Wizards.
    fun decks(source: DeckSource) =
        "decks/${Uri.encode(source.username)}" +
            "?${DecksViewModel.ARG_FORMAT}=${Uri.encode(source.format.orEmpty())}"

    fun tokens(publicId: String, deckName: String) =
        "tokens/${Uri.encode(publicId)}?${TokensViewModel.ARG_DECK_NAME}=${Uri.encode(deckName)}"

    fun board(publicId: String, tokenId: String) =
        "board/${Uri.encode(publicId)}/${Uri.encode(tokenId)}"
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
        ) { entry ->
            // The board route needs the deck too, and the entry already carries it.
            val publicId = entry.arguments?.getString(TokensViewModel.ARG_PUBLIC_ID).orEmpty()

            // Wide enough, and the board is the pane on the right instead of
            // the destination this navigates to. Which one it is is decided
            // inside, from the width actually available.
            TokensAndBoard(
                publicId = publicId,
                onOpenBoard = { token ->
                    navController.navigate(Routes.board(publicId, token.id))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.BOARD,
            arguments = listOf(
                navArgument(TokenBoardViewModel.ARG_PUBLIC_ID) { type = NavType.StringType },
                navArgument(TokenBoardViewModel.ARG_TOKEN_ID) { type = NavType.StringType },
            ),
        ) {
            TokenBoardScreen(onBack = { navController.popBackStack() })
        }
    }
}
