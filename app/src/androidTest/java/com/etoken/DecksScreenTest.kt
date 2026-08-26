package com.etoken

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.etoken.ui.decks.DecksScreen
import com.etoken.ui.decks.DecksViewModel
import com.etoken.ui.theme.EtokenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecksScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    private fun showDecks(onDeckClick: (com.etoken.domain.model.DeckSummary) -> Unit = {}) {
        val viewModel = DecksViewModel(Fakes.repository(), SavedStateHandle(), "someone")
        compose.setContent {
            EtokenTheme { DecksScreen(onDeckClick = onDeckClick, onBack = {}, viewModel = viewModel) }
        }
        awaitText(Fakes.DECK_NAME)
    }

    @Test
    fun the_users_decks_arrive_in_the_grid() {
        showDecks()

        compose.onNodeWithText(Fakes.DECK_NAME).assertIsDisplayed()
        compose.onNodeWithText(Fakes.OTHER_DECK_NAME).assertIsDisplayed()
    }

    @Test
    fun searching_narrows_the_grid_and_clearing_restores_it() {
        showDecks()

        compose.onNode(hasSetTextAction()).performTextInput("atraxa")
        compose.waitUntil(TIMEOUT) { nodesWithText(Fakes.DECK_NAME).isEmpty() }
        compose.onNodeWithText(Fakes.OTHER_DECK_NAME).assertIsDisplayed()

        compose.onNodeWithContentDescription(str(R.string.decks_search_clear)).performClick()
        awaitText(Fakes.DECK_NAME)
    }

    @Test
    fun searching_by_commander_finds_a_deck_its_name_never_mentions() {
        showDecks()

        // "Krenko, Mob Boss" is the commander; the query never touches the deck's title.
        compose.onNode(hasSetTextAction()).performTextInput("mob boss")
        compose.waitUntil(TIMEOUT) { nodesWithText(Fakes.OTHER_DECK_NAME).isEmpty() }
        compose.onNodeWithText(Fakes.DECK_NAME).assertIsDisplayed()
    }

    @Test
    fun tapping_a_deck_reports_which_one() {
        var clicked: String? = null
        showDecks(onDeckClick = { clicked = it.publicId })

        compose.onNodeWithText(Fakes.DECK_NAME).performClick()

        compose.waitUntil(TIMEOUT) { clicked != null }
        assertEquals(Fakes.DECK_ID, clicked)
    }

    private fun nodesWithText(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes()

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) { nodesWithText(text).isNotEmpty() }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
