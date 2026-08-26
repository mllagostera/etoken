package com.etoken

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.etoken.data.TokenBoardStore
import com.etoken.ui.TokensAndBoard
import com.etoken.ui.board.TokenBoardViewModel
import com.etoken.ui.theme.EtokenTheme
import com.etoken.ui.tokens.TokensViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether the board is a pane or a screen of its own is decided from the width
 * actually available, so these tests hand the layout a width and check which
 * arrangement comes back.
 */
@RunWith(AndroidJUnit4::class)
class TwoPaneTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    private var opened: String? = null

    /**
     * Asserted with `assertExists` rather than `assertIsDisplayed` throughout
     * the wide case: 1280dp is wider than the emulator's screen, so the pane on
     * the right is composed but clipped out of view. What is under test is
     * which arrangement the layout chose, not what fits on this AVD.
     */
    private fun show(width: Dp) {
        val boards = TokenBoardStore()
        // Built out here rather than inside the composable: constructing a view
        // model in composition is exactly what lint refuses, test or not.
        val tokens = TokensViewModel(
            repository = Fakes.repository(),
            boards = boards,
            savedState = SavedStateHandle(),
            publicId = Fakes.DECK_ID,
            deckName = Fakes.DECK_NAME,
        )
        val board = TokenBoardViewModel(
            repository = Fakes.repository(),
            boards = boards,
            publicId = Fakes.DECK_ID,
            tokenId = Fakes.TOKEN_ID,
        )
        compose.setContent {
            EtokenTheme {
                // requiredSize, not size: size() coerces itself into the
                // constraints the parent hands down, so on a phone-sized AVD a
                // 1280dp box quietly came back 411dp wide and the layout kept
                // choosing one pane. requiredSize ignores the parent, which is
                // the whole point of asking for a width the screen has not got.
                Box(Modifier.requiredSize(width, 800.dp)) {
                    TokensAndBoard(
                        publicId = Fakes.DECK_ID,
                        onOpenBoard = { token -> opened = token.id },
                        onBack = {},
                        tokensViewModel = tokens,
                        boardViewModel = { board },
                    )
                }
            }
        }
        awaitText(Fakes.TOKEN_NAME)
    }

    @Test
    fun a_wide_window_opens_the_board_beside_the_grid() {
        show(1280.dp)

        // Nothing picked yet, so the right-hand pane says so.
        compose.onNodeWithText(str(R.string.board_pick_token)).assertExists()

        compose.onNodeWithText(Fakes.TOKEN_NAME).performClick()

        // Both panes are up: the deck's title bar belongs to the grid, and an
        // empty board to the pane beside it.
        awaitText(str(R.string.board_empty))
        compose.onNodeWithText(Fakes.DECK_NAME).assertExists()
        compose.onAllNodesWithText(str(R.string.board_pick_token)).assertCountEquals(0)
        // And nothing was navigated to.
        assertNull(opened)
    }

    @Test
    fun a_phone_window_leaves_the_board_a_screen_of_its_own() {
        show(400.dp)

        compose.onNodeWithText(Fakes.TOKEN_NAME).performClick()

        // No pane appeared; the click asked for the board destination instead.
        compose.onAllNodesWithText(str(R.string.board_pick_token)).assertCountEquals(0)
        assertEquals(Fakes.TOKEN_ID, opened)
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
