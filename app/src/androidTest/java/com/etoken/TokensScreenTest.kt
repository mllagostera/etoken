package com.etoken

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.etoken.data.TokenBoardStore
import com.etoken.domain.TokenBoardRules
import com.etoken.ui.theme.EtokenTheme
import com.etoken.ui.tokens.TokensScreen
import com.etoken.ui.tokens.TokensViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The quick filter and the cells' badges, driven through the screen rather than
 * the rules underneath them: [com.etoken.domain.TokenFilter] decides what "in
 * play" means and [com.etoken.domain.model.TokenBoard] decides when the counters
 * can be named, both unit-tested. This is about the screen being wired to them —
 * that the chip appears when there is a table to filter, that what it hides
 * comes back, and that a cell says what its copies are carrying.
 *
 * Krenko's deck makes three tokens here — a Goblin, a Treasure and a hasty
 * Hellion — so a filter has something to keep and something to hide.
 */
@RunWith(AndroidJUnit4::class)
class TokensScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun plural(id: Int, count: Int) = context.resources.getQuantityString(id, count, count)

    private val boards = TokenBoardStore()

    private fun showTokens() {
        val viewModel = TokensViewModel(
            repository = Fakes.repository(),
            boards = boards,
            savedState = SavedStateHandle(),
            publicId = Fakes.DECK_ID,
            deckName = Fakes.DECK_NAME,
        )
        compose.setContent {
            EtokenTheme { TokensScreen(onTokenClick = {}, onBack = {}, viewModel = viewModel) }
        }
        // The top bar's count, not a token name: the deck is called "Krenko
        // Goblins", so waiting on the Goblin would match the title and return
        // while the screen was still loading.
        awaitText(plural(R.plurals.tokens_count, 3))
    }

    /** Puts Goblins on the battlefield without going through the board screen. */
    private fun putGoblinsInPlay(quantity: Int = 3) {
        boards.update(Fakes.TOKEN_ID) { board -> TokenBoardRules.add(board, quantity) }
    }

    @Test
    fun all_of_the_decks_tokens_reach_the_grid() {
        showTokens()

        // One at a time, each scrolled to first. The order is the grid's own —
        // by type line, so the Treasure leads — and what is being asserted is
        // that all three are in the grid, not that a screen exists tall enough
        // to hold them at once.
        for (name in listOf(Fakes.TREASURE_TOKEN_NAME, Fakes.TOKEN_NAME, Fakes.HASTE_TOKEN_NAME)) {
            scrollToCell(name)
            compose.onNodeWithText(name).assertIsDisplayed()
        }
    }

    @Test
    fun the_filter_is_not_offered_while_the_table_is_empty() {
        showTokens()

        // Nothing is in play, so there is nothing to filter down to.
        compose.onAllNodesWithText(str(R.string.tokens_filter_in_play)).assertCountEquals(0)
    }

    @Test
    fun the_filter_keeps_what_is_on_the_table_and_gives_it_back() {
        showTokens()
        putGoblinsInPlay()
        awaitText(str(R.string.tokens_filter_in_play))

        compose.onNodeWithText(str(R.string.tokens_filter_in_play)).performClick()

        // The Goblins are on the table; the Treasures are not.
        awaitGone(Fakes.TREASURE_TOKEN_NAME)
        compose.onNodeWithText(Fakes.TOKEN_NAME).assertIsDisplayed()
        // And the top bar says what is being hidden: 1 of the deck's 3 tokens.
        compose.onNodeWithText(str(R.string.tokens_filtered_count, 1, 3)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.tokens_filter_in_play)).performClick()

        // Waited for and then scrolled to, like every other assertion about a
        // cell here: an item coming back to a lazy grid is not on screen the
        // same frame, and on this grid it may not be composed at all.
        awaitText(Fakes.TREASURE_TOKEN_NAME)
        scrollToCell(Fakes.TREASURE_TOKEN_NAME)
        compose.onNodeWithText(Fakes.TREASURE_TOKEN_NAME).assertIsDisplayed()
        // The top bar is not lazy: it going back to counting the deck's tokens
        // is the filter being off, not merely the grid being wider.
        compose.onNodeWithText(plural(R.plurals.tokens_count, 3)).assertIsDisplayed()
    }

    @Test
    fun a_new_game_leaves_the_filter_saying_the_table_is_empty() {
        showTokens()
        putGoblinsInPlay()
        awaitText(str(R.string.tokens_filter_in_play))
        compose.onNodeWithText(str(R.string.tokens_filter_in_play)).performClick()
        awaitGone(Fakes.TREASURE_TOKEN_NAME)

        boards.clearAll()

        // The grid does not go blank and silent: it says why it is empty, and
        // the chip stays put so the filter can be turned off.
        awaitText(str(R.string.tokens_none_in_play))
        compose.onNodeWithText(str(R.string.tokens_filter_in_play)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.tokens_filter_in_play)).performClick()

        // Both cells are being re-added to a lazy grid at this instant, and the
        // Goblin's is the second of the three: scrolling is what reaches it.
        awaitText(Fakes.TREASURE_TOKEN_NAME)
        scrollToCell(Fakes.TOKEN_NAME)
        compose.onNodeWithText(Fakes.TOKEN_NAME).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.tokens_count, 3)).assertIsDisplayed()
    }

    @Test
    fun the_grid_says_what_counters_the_copies_carry() {
        showTokens()
        putGoblinsInPlay()
        // The chip appearing is the board having reached the screen; scrolling
        // after it means the grid is no longer about to move underneath us.
        awaitText(str(R.string.tokens_filter_in_play))
        scrollToCell(Fakes.TOKEN_NAME)
        awaitText("×3")

        // Two +1/+1 counters on the whole stack: every Goblin in play carries
        // them, so the cell can say so without lying about any of them.
        boards.update(Fakes.TOKEN_ID) { board ->
            TokenBoardRules.addCounters(board, board.stacks.single().id, delta = 2)
        }

        awaitText(str(R.string.stack_counters_chip, 2))
        // The count is still there beside it: the badge adds to it, not replaces it.
        compose.onNodeWithText("×3").assertExists()
    }

    @Test
    fun a_split_stack_leaves_the_counters_off_the_grid() {
        showTokens()
        putGoblinsInPlay()
        boards.update(Fakes.TOKEN_ID) { board ->
            TokenBoardRules.addCounters(board, board.stacks.single().id, delta = 2)
        }
        awaitText(str(R.string.tokens_filter_in_play))
        scrollToCell(Fakes.TOKEN_NAME)
        awaitText(str(R.string.stack_counters_chip, 2))

        // One of the three loses its counters, and now the table holds two
        // different things. One badge cannot describe both, so it goes.
        boards.update(Fakes.TOKEN_ID) { board ->
            TokenBoardRules.addCounters(
                board,
                board.stacks.single().id,
                delta = -2,
                appliesTo = 1,
            )
        }

        awaitGone(str(R.string.stack_counters_chip, 2))
        // The count is untouched: three Goblins are still three Goblins.
        compose.onNodeWithText("×3").assertExists()
    }

    /**
     * Brings one token's cell into view, and into existence.
     *
     * The grid is lazy, and CI's emulator is the default AVD rather than a
     * phone: 320x640dp, which `GridCells.Adaptive(150.dp)` answers with a
     * single column of cells about 480dp tall. Two rows reach the viewport;
     * the third is not merely off screen, it is never composed, so it is
     * absent from the semantics tree and `assertExists` fails on it. The
     * second row goes the same way the moment the filter chip claims its
     * 48dp. Scrolling is what puts a cell in the tree, and it is also what
     * makes `assertIsDisplayed` mean anything afterwards.
     */
    private fun scrollToCell(name: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitGone(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
