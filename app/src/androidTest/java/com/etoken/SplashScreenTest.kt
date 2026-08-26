package com.etoken

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The only test in the suite that launches the **real** [MainActivity] rather
 * than driving a composable inside a test activity, which is what makes it
 * worth having: `attachBaseContext`, `installSplashScreen()` and the launch
 * theme all run here and nowhere else. A splash theme missing an attribute the
 * library needs, or a `postSplashScreenTheme` pointing at nothing, takes the
 * activity down in onCreate — and this fails.
 *
 * What it does **not** prove is that the splash was ever seen, or that it let
 * go: the keep-on-screen condition holds the *drawing* of the first frame, and
 * a composable that is never drawn still reports itself as displayed to the
 * semantics tree. A splash that hung would pass this test. Watching one
 * happen still needs a person and a phone.
 */
@RunWith(AndroidJUnit4::class)
class SplashScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun the_app_starts_and_lands_on_the_username_screen() {
        // The field, not its label: this activity applies whatever language is
        // stored on the device, so nothing on screen is reliably in English.
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }
}
