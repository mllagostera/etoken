package com.etoken

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.etoken.data.AppLanguageStore
import com.etoken.data.UserPreferences
import com.etoken.domain.AppLanguage
import com.etoken.ui.theme.EtokenTheme
import com.etoken.ui.username.UsernameScreen
import com.etoken.ui.username.UsernameViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    // The language store is backed by a real file, shared by every test in the
    // run. Each test that touches it says what it starts from, and puts it back.
    private val languages = AppLanguageStore(context)

    @After
    fun resetTheLanguage() = languages.set(AppLanguage.SYSTEM)

    @Test
    fun a_remembered_username_comes_back_prefilled() {
        val preferences = UserPreferences(context)
        runBlocking { preferences.setUsername("seeded-user") }

        // Built outside setContent: a view model constructed inside a composable
        // would be rebuilt on every recomposition, which lint rightly rejects.
        val viewModel = UsernameViewModel(preferences, languages)
        compose.setContent {
            EtokenTheme { UsernameScreen(onSubmit = {}, viewModel = viewModel) }
        }

        awaitText("seeded-user")
    }

    @Test
    fun typing_a_name_and_submitting_hands_it_over() {
        val preferences = UserPreferences(context)
        runBlocking { preferences.setUsername("seeded-user") }

        var submitted: String? = null
        val viewModel = UsernameViewModel(preferences, languages)
        compose.setContent {
            EtokenTheme { UsernameScreen(onSubmit = { submitted = it }, viewModel = viewModel) }
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

    @Test
    fun the_screen_says_up_front_that_only_public_decks_are_read() {
        val viewModel = UsernameViewModel(UserPreferences(context), languages)
        compose.setContent {
            EtokenTheme { UsernameScreen(onSubmit = {}, viewModel = viewModel) }
        }

        compose.onNodeWithText(str(R.string.public_decks_only)).assertIsDisplayed()
    }

    @Test
    fun the_picker_offers_every_language_in_its_own_words() {
        val viewModel = UsernameViewModel(UserPreferences(context), languages)
        compose.setContent {
            EtokenTheme { UsernameScreen(onSubmit = {}, viewModel = viewModel) }
        }

        compose.onNodeWithContentDescription(str(R.string.action_language)).performClick()

        // The endonyms are the same string in every locale, so this asserts on
        // what the user sees whatever language the emulator happens to be in.
        // Scrolled to first: eight rows are taller than the dialog on CI's
        // 320x640dp screen, and the ones below the fold are clipped rather
        // than absent -- a Column composes all of its children.
        compose.onNodeWithText(str(R.string.language_system)).assertIsDisplayed()
        compose.onNodeWithText("English").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Español").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("日本語").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosing_a_language_is_remembered() {
        languages.set(AppLanguage.SYSTEM)

        val viewModel = UsernameViewModel(UserPreferences(context), languages)
        compose.setContent {
            EtokenTheme { UsernameScreen(onSubmit = {}, viewModel = viewModel) }
        }

        compose.onNodeWithContentDescription(str(R.string.action_language)).performClick()
        compose.onNodeWithText("Català").performScrollTo().performClick()

        // The screen itself does not change language -- MainActivity recreates
        // itself off this same flow, and there is no activity here to recreate.
        compose.waitUntil(TIMEOUT) { languages.language.value == AppLanguage.CATALAN }

        // A store built fresh reads the same answer, which is the half that
        // matters: the choice has to survive the process it was made in.
        assertEquals(AppLanguage.CATALAN, AppLanguageStore(context).language.value)
    }

    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
