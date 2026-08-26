package com.etoken

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * A token named "Copy" says nothing on its own, so adding one asks what it is
 * a copy of. The answer belongs to the stack, which is what these tests are
 * really guarding: two copies of different creatures must stay two rows.
 */
@RunWith(AndroidJUnit4::class)
class CopyTokenBoardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    private fun showCopyBoard() {
        val viewModel = TokenBoardViewModel(
            repository = Fakes.repository(),
            boards = TokenBoardStore(),
            publicId = Fakes.OTHER_DECK_ID,
            tokenId = Fakes.COPY_TOKEN_ID,
        )
        compose.setContent {
            EtokenTheme { TokenBoardScreen(onBack = {}, viewModel = viewModel) }
        }
        awaitText(Fakes.COPY_TOKEN_NAME)
    }

    private fun addCopy(button: String, name: String) {
        compose.onNodeWithText(button).performClick()
        awaitText(str(R.string.copy_of_title))
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText(str(R.string.action_accept)).performClick()
        awaitText(name)
    }

    @Test
    fun adding_a_copy_asks_what_it_is_copying() {
        showCopyBoard()

        compose.onNodeWithText("+2").performClick()

        awaitText(str(R.string.copy_of_title))
        compose.onNodeWithText(str(R.string.copy_of_title)).assertIsDisplayed()
    }

    @Test
    fun the_name_given_is_the_one_the_stack_shows() {
        showCopyBoard()

        addCopy("+2", "Krenko, Mob Boss")

        compose.onNodeWithText("Krenko, Mob Boss").assertIsDisplayed()
        compose.onNodeWithText("2 ×").assertIsDisplayed()
    }

    @Test
    fun copies_of_different_creatures_stay_two_stacks() {
        showCopyBoard()

        addCopy("+2", "Krenko, Mob Boss")
        addCopy("+1", "Atraxa")

        // Three tokens across two rows, not one row of three. The rows are
        // asserted to exist rather than to be visible: two stack cards do not
        // both fit above the fold on a phone, and this test is about how the
        // board is divided, not about what fits on screen.
        compose.onNodeWithText("Krenko, Mob Boss").assertExists()
        compose.onNodeWithText("Atraxa").assertExists()
        compose.onNodeWithText("2 ×").assertExists()
        compose.onNodeWithText("1 ×").assertExists()

        // The running total is at the top, so it really is displayed.
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun copies_of_the_same_creature_become_one_stack() {
        showCopyBoard()

        addCopy("+2", "Krenko, Mob Boss")
        addCopy("+1", "Krenko, Mob Boss")

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("3 ×").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("3 ×").assertIsDisplayed()
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
