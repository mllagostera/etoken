package com.etoken

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.etoken.data.TokenBoardStore
import com.etoken.ui.board.TokenBoardScreen
import com.etoken.ui.board.TokenBoardViewModel
import com.etoken.ui.theme.EtokenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The board is the screen with the most behaviour behind it, so it gets the
 * most attention here: the rules it enforces are unit-tested, but nothing had
 * ever confirmed that the buttons on top of them are wired to the right ones.
 */
@RunWith(AndroidJUnit4::class)
class TokenBoardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun showBoard() {
        val viewModel = TokenBoardViewModel(
            repository = Fakes.repository(),
            boards = TokenBoardStore(),
            publicId = Fakes.DECK_ID,
            tokenId = Fakes.TOKEN_ID,
        )
        compose.setContent {
            EtokenTheme { TokenBoardScreen(onBack = {}, viewModel = viewModel) }
        }
        awaitText(Fakes.TOKEN_NAME)
    }

    @Test
    fun the_token_the_deck_can_make_is_the_one_that_opens() {
        showBoard()

        compose.onNodeWithText(Fakes.TOKEN_NAME).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.board_empty)).assertIsDisplayed()
    }

    @Test
    fun tokens_added_arrive_summoning_sick_and_the_untap_step_clears_them() {
        showBoard()

        compose.onNodeWithText("+5").performClick()
        compose.onNodeWithText("+2").performClick()

        // Seven in play, and every one of them still summoning sick.
        awaitText(str(R.string.board_sick, 7))
        // "7" alone is ambiguous -- the running total and the stack's quantity
        // stepper both show it. The stack header does not.
        compose.onNodeWithText("7 ×").assertIsDisplayed()

        compose.onNodeWithText(str(R.string.board_begin_turn)).performClick()

        awaitText(str(R.string.board_none_sick))
        compose.onNodeWithText("7 ×").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.stack_ready)).assertIsDisplayed()
    }

    @Test
    fun a_counter_on_the_whole_stack_grows_it() {
        showBoard()

        compose.onNodeWithText("+2").performClick()
        awaitText(str(R.string.board_sick, 2))

        // Base 1/1; one +1/+1 counter on all of them makes it 2/2.
        compose.onNodeWithText(str(R.string.board_counter_all)).performClick()

        awaitText("2/2")
        compose.onNodeWithText(str(R.string.stack_counters_chip, 1)).assertIsDisplayed()
    }

    @Test
    fun clearing_asks_first_and_cancelling_leaves_the_board_alone() {
        showBoard()

        compose.onNodeWithText("+5").performClick()
        awaitText(str(R.string.board_sick, 5))

        compose.onNodeWithText(str(R.string.board_clear)).performClick()
        awaitText(str(R.string.clear_title))

        compose.onNodeWithText(str(R.string.action_cancel)).performClick()

        // Still five on the table: the confirmation is doing its job.
        awaitText(str(R.string.board_sick, 5))
    }

    @Test
    fun undo_brings_back_a_board_that_was_cleared() {
        showBoard()

        // Nothing has happened yet, so there is nothing to take back.
        undoButton().assertIsNotEnabled()

        compose.onNodeWithText("+5").performClick()
        awaitText(str(R.string.board_sick, 5))

        compose.onNodeWithText(str(R.string.board_clear)).performClick()
        awaitText(str(R.string.clear_title))
        confirmClear()
        awaitText(str(R.string.board_empty))

        undoButton().assertIsEnabled()
        undoButton().performClick()

        // The five are back, still summoning sick, exactly as they were.
        awaitText(str(R.string.board_sick, 5))
        compose.onNodeWithText("5 ×").assertIsDisplayed()

        // And one more step empties the trail: adding them was all that is left.
        undoButton().performClick()
        awaitText(str(R.string.board_empty))
        undoButton().assertIsNotEnabled()
    }

    private fun undoButton() =
        compose.onNodeWithContentDescription(str(R.string.action_undo))

    /**
     * The dialog's confirm button carries the same label as the button that
     * opened it, and both are in the tree while the dialog is up.
     */
    private fun confirmClear() =
        compose.onAllNodesWithText(str(R.string.board_clear))
            .filterToOne(hasAnyAncestor(isDialog()))
            .performClick()

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
