package com.etoken

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * A token printed with haste enters able to attack, and the board says so.
 *
 * Krenko's deck makes one here — Hellion Crucible's 4/4 — beside a Goblin that
 * has no haste at all. The rule itself is unit-tested; what these drive is the
 * screen asking the token instead of assuming every token arrives summoning
 * sick, and still letting the player say otherwise.
 */
@RunWith(AndroidJUnit4::class)
class HasteBoardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun showBoard(tokenId: String, name: String) {
        val viewModel = TokenBoardViewModel(
            repository = Fakes.repository(),
            boards = TokenBoardStore(),
            publicId = Fakes.DECK_ID,
            tokenId = tokenId,
        )
        compose.setContent {
            EtokenTheme { TokenBoardScreen(onBack = {}, viewModel = viewModel) }
        }
        awaitText(name)
    }

    private fun showHasteBoard() = showBoard(Fakes.HASTE_TOKEN_ID, Fakes.HASTE_TOKEN_NAME)

    @Test
    fun copies_of_a_hasty_token_arrive_able_to_attack() {
        showHasteBoard()

        compose.onNodeWithText("+2").performClick()

        awaitText(str(R.string.stack_ready))
        compose.onNodeWithText("2 ×").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.board_none_sick)).assertIsDisplayed()
        // Nothing is waiting, so the untap step has nothing to clear.
        compose.onNodeWithText(str(R.string.board_begin_turn)).assertIsNotEnabled()
    }

    @Test
    fun the_board_says_the_token_has_haste() {
        showHasteBoard()

        // Without this, a stack that never shows "Mareo" is indistinguishable
        // from the app having got it wrong.
        compose.onNodeWithText(str(R.string.board_haste)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.board_haste_note), substring = true).assertExists()
    }

    @Test
    fun summoning_sickness_can_still_be_put_back_by_hand() {
        showHasteBoard()

        compose.onNodeWithText("+2").performClick()
        awaitText(str(R.string.stack_ready))

        compose.onNodeWithText(str(R.string.stack_ready)).performClick()

        // Printed haste is automatic; the chip is still the last word.
        awaitText(str(R.string.board_sick, 2))
        compose.onNodeWithText(str(R.string.stack_sick)).assertIsDisplayed()
    }

    @Test
    fun a_token_without_haste_is_left_alone() {
        // The same screen, the same deck, the other token: the rule has to come
        // off the token rather than being switched on for the whole board.
        showBoard(Fakes.TOKEN_ID, Fakes.TOKEN_NAME)

        compose.onAllNodesWithText(str(R.string.board_haste)).assertCountEquals(0)

        compose.onNodeWithText("+2").performClick()

        awaitText(str(R.string.board_sick, 2))
        compose.onNodeWithText(str(R.string.stack_sick)).assertIsDisplayed()
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
