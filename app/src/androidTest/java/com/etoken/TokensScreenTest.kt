package com.etoken

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * The quick filter, driven through the screen rather than the rule underneath
 * it: [com.etoken.domain.TokenFilter] decides what "in play" means and is
 * unit-tested, and this is about the chip being wired to it — that it appears
 * when there is a table to filter, and that what it hides comes back.
 *
 * Krenko's deck makes two tokens here, a Goblin and a Treasure, so a filter has
 * something to keep and something to hide.
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
        awaitText(Fakes.TOKEN_NAME)
    }

    /** Puts Goblins on the battlefield without going through the board screen. */
    private fun putGoblinsInPlay(quantity: Int = 3) {
        boards.update(Fakes.TOKEN_ID) { board -> TokenBoardRules.add(board, quantity) }
    }

    @Test
    fun both_of_the_decks_tokens_reach_the_grid() {
        showTokens()

        compose.onNodeWithText(Fakes.TOKEN_NAME).assertIsDisplayed()
        compose.onNodeWithText(Fakes.TREASURE_TOKEN_NAME).assertIsDisplayed()
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
        // And the top bar says what is being hidden: 1 of the deck's 2 tokens.
        compose.onNodeWithText(str(R.string.tokens_filtered_count, 1, 2)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.tokens_filter_in_play)).performClick()

        // assertExists rather than assertIsDisplayed, and the emulator is why:
        // the grid is lazy, so an item that comes back is in the semantics tree
        // a frame before it is placed, and asserting it is on screen right here
        // is a race. What this test is about is what the grid holds. That the
        // cell is genuinely visible is covered above, on a grid nothing has
        // filtered.
        awaitText(Fakes.TREASURE_TOKEN_NAME)
        compose.onNodeWithText(Fakes.TREASURE_TOKEN_NAME).assertExists()
        // The top bar is not lazy: it going back to counting the deck's tokens
        // is the filter being off, not merely the grid being wider.
        compose.onNodeWithText(plural(R.plurals.tokens_count, 2)).assertIsDisplayed()
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

        // Existence again, for the reason given above: both cells are being
        // re-added to a lazy grid at this instant.
        awaitText(Fakes.TREASURE_TOKEN_NAME)
        compose.onNodeWithText(Fakes.TOKEN_NAME).assertExists()
        compose.onNodeWithText(plural(R.plurals.tokens_count, 2)).assertIsDisplayed()
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
