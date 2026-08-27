package com.etoken

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
 * Two rules that share a screen: only creatures can be summoning sick, and any
 * token can be told to enter tapped. `CreatureTokenTest` covers the first as a
 * pure function; this drives the board screen asking that question of a real
 * (fake) token instead of assuming every token is a creature.
 */
@RunWith(AndroidJUnit4::class)
class CreatureSicknessAndTappedBoardTest {

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

    @Test
    fun a_treasure_never_shows_summoning_sickness() {
        showBoard(Fakes.TREASURE_TOKEN_ID, Fakes.TREASURE_TOKEN_NAME)

        compose.onNodeWithText("+2").performClick()
        awaitText("2 ×")

        compose.onAllNodesWithText(str(R.string.stack_sick)).assertCountEquals(0)
        compose.onAllNodesWithText(str(R.string.stack_ready)).assertCountEquals(0)
        compose.onAllNodesWithText(str(R.string.board_sick, 2)).assertCountEquals(0)
    }

    @Test
    fun a_creature_still_arrives_summoning_sick() {
        showBoard(Fakes.TOKEN_ID, Fakes.TOKEN_NAME)

        compose.onNodeWithText("+2").performClick()

        awaitText(str(R.string.stack_sick))
        compose.onNodeWithText(str(R.string.board_sick, 2)).assertIsDisplayed()
    }

    @Test
    fun turning_on_enter_tapped_makes_new_copies_arrive_tapped() {
        showBoard(Fakes.TOKEN_ID, Fakes.TOKEN_NAME)

        compose.onNodeWithText(str(R.string.board_enter_tapped)).performClick()
        compose.onNodeWithText("+2").performClick()

        awaitText(str(R.string.stack_tapped))
        compose.onNodeWithText(str(R.string.stack_tapped)).assertIsDisplayed()
    }

    @Test
    fun tokens_arrive_untapped_by_default() {
        showBoard(Fakes.TOKEN_ID, Fakes.TOKEN_NAME)

        compose.onNodeWithText("+2").performClick()

        awaitText(str(R.string.stack_untapped))
        compose.onNodeWithText(str(R.string.stack_untapped)).assertIsDisplayed()
    }

    @Test
    fun the_chip_can_untap_a_stack_by_hand() {
        showBoard(Fakes.TOKEN_ID, Fakes.TOKEN_NAME)

        compose.onNodeWithText(str(R.string.board_enter_tapped)).performClick()
        compose.onNodeWithText("+2").performClick()
        awaitText(str(R.string.stack_tapped))

        compose.onNodeWithText(str(R.string.stack_tapped)).performClick()

        awaitText(str(R.string.stack_untapped))
        compose.onNodeWithText(str(R.string.stack_untapped)).assertIsDisplayed()
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
