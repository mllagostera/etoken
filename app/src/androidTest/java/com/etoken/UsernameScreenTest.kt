package com.etoken

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.etoken.data.UserPreferences
import com.etoken.ui.theme.EtokenTheme
import com.etoken.ui.username.UsernameScreen
import com.etoken.ui.username.UsernameViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsernameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    @Test
    fun a_remembered_username_comes_back_prefilled() {
        val preferences = UserPreferences(context)
        runBlocking { preferences.setUsername("seeded-user") }

        compose.setContent {
            EtokenTheme { UsernameScreen(onSubmit = {}, viewModel = UsernameViewModel(preferences)) }
        }

        awaitText("seeded-user")
    }

    @Test
    fun typing_a_name_and_submitting_hands_it_over() {
        val preferences = UserPreferences(context)
        runBlocking { preferences.setUsername("seeded-user") }

        var submitted: String? = null
        compose.setContent {
            EtokenTheme {
                UsernameScreen(onSubmit = { submitted = it }, viewModel = UsernameViewModel(preferences))
            }
        }

        // Waiting for the remembered value first is what stops this racing the
        // view model's own load and typing into a field about to be overwritten.
        awaitText("seeded-user")

        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("krenko")
        compose.onNodeWithText(str(R.string.action_load_decks)).performClick()

        compose.waitUntil(TIMEOUT) { submitted != null }
        assertEquals("krenko", submitted)
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
