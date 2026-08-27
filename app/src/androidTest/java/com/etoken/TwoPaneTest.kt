package com.etoken

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.etoken.data.GameBoardStore
import com.etoken.ui.board.BoardScreen
import com.etoken.ui.board.BoardViewModel
import com.etoken.ui.theme.EtokenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether the picker is a pane or a sheet behind the "+" is decided from the
 * width actually available, so these tests hand the screen a width and check
 * which arrangement comes back.
 */
@RunWith(AndroidJUnit4::class)
class TwoPaneTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    /**
     * Asserted with `assertExists` rather than `assertIsDisplayed` in the wide
     * case: 1280dp is wider than the emulator's screen, so the right-hand pane
     * is composed but clipped out of view. What is under test is which
     * arrangement the screen chose, not what fits on this AVD.
     */
    private fun show(width: Dp) {
        // Built out here rather than inside the composable: constructing a view
        // model in composition is exactly what lint refuses, test or not.
        val viewModel = BoardViewModel(
            repository = Fakes.repository(),
            boards = GameBoardStore(),
            publicId = Fakes.DECK_ID,
            deckName = Fakes.DECK_NAME,
        )
        compose.setContent {
            EtokenTheme {
                // requiredSize, not size: size() coerces itself into the
                // constraints the parent hands down, so on a phone-sized AVD a
                // 1280dp box quietly came back 411dp wide and the layout kept
                // choosing one pane. requiredSize ignores the parent, which is
                // the whole point of asking for a width the screen has not got.
                Box(Modifier.requiredSize(width, 800.dp)) {
                    BoardScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }
        awaitText(str(R.string.board_empty))
    }

    @Test
    fun a_wide_window_keeps_the_picker_open_beside_the_table() {
        show(1280.dp)

        // The deck's tokens are on screen without anything being pressed, and
        // the empty table is beside them.
        compose.onNodeWithText(Fakes.TOKEN_NAME).assertExists()
        compose.onNodeWithText(str(R.string.board_empty)).assertExists()
        // A button to open a picker that is already open would open nothing.
        compose.onAllNodesWithContentDescription(str(R.string.board_add_token))
            .assertCountEquals(0)
    }

    @Test
    fun a_phone_window_keeps_the_picker_behind_the_button() {
        show(400.dp)

        // Only the table, and the "+" that reaches the deck's tokens.
        compose.onNodeWithContentDescription(str(R.string.board_add_token)).assertExists()
        compose.onAllNodesWithText(Fakes.TOKEN_NAME).assertCountEquals(0)
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
