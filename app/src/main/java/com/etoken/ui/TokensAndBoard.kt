package com.etoken.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etoken.R
import com.etoken.domain.model.TokenCard
import com.etoken.ui.board.TokenBoardScreen
import com.etoken.ui.board.TokenBoardViewModel
import com.etoken.ui.common.MessageView
import com.etoken.ui.tokens.TokensScreen
import com.etoken.ui.tokens.TokensViewModel

/**
 * The deck's tokens, and — where there is room — the open board beside them.
 *
 * On a phone this is just the token grid; opening a token is a navigation to a
 * screen of its own. Past [TWO_PANE_WIDTH] the board stops being a destination
 * and becomes the right-hand pane, so a tablet stops throwing the grid away
 * every time you check what is in play.
 *
 * Both arrangements are driven from the same view models and the same board
 * store, so nothing about the app's state depends on which one is on screen.
 */
@Composable
fun TokensAndBoard(
    publicId: String,
    onOpenBoard: (TokenCard) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tokensViewModel: TokensViewModel = viewModel(factory = TokensViewModel.Factory),
    boardViewModel: @Composable (String) -> TokenBoardViewModel = { tokenId ->
        // Keyed by token, so switching tokens in the pane gets that token's
        // view model rather than the previous one with new arguments.
        viewModel(
            key = tokenId,
            factory = TokenBoardViewModel.factoryFor(publicId, tokenId),
        )
    },
) {
    // Saveable: a rotation that keeps the layout wide should keep the board it
    // was showing. One that narrows it drops back to the grid, which is where
    // the phone layout starts anyway.
    var selectedTokenId by rememberSaveable { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth < TWO_PANE_WIDTH) {
            TokensScreen(
                onTokenClick = onOpenBoard,
                onBack = onBack,
                viewModel = tokensViewModel,
            )
            return@BoxWithConstraints
        }

        Row(Modifier.fillMaxSize()) {
            TokensScreen(
                onTokenClick = { token -> selectedTokenId = token.id },
                onBack = onBack,
                modifier = Modifier.weight(1f),
                selectedTokenId = selectedTokenId,
                viewModel = tokensViewModel,
            )
            VerticalDivider()
            when (val tokenId = selectedTokenId) {
                null -> MessageView(
                    message = stringResource(R.string.board_pick_token),
                    modifier = Modifier.weight(1.15f),
                )
                // Back closes the pane rather than leaving the deck: the grid
                // is already on screen, so there is nothing to go back to.
                else -> TokenBoardScreen(
                    onBack = { selectedTokenId = null },
                    modifier = Modifier.weight(1.15f),
                    viewModel = boardViewModel(tokenId),
                )
            }
        }
    }
}

/**
 * Material's "expanded" width, and the point where two panes stop being a
 * squeeze: below it the board's card art and its stack rows have nowhere to go.
 * A tablet in portrait is usually around 800dp, so it stays on one pane —
 * deliberately, since half of that is narrower than the phone layout the board
 * was designed for.
 *
 * Read from the layout's own constraints rather than through
 * `material3-window-size-class`: it is the same 840dp threshold, and this app
 * has one place that asks the question.
 */
private val TWO_PANE_WIDTH = 840.dp
