package com.etoken

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.etoken.data.AppLanguageStore
import com.etoken.domain.AppLanguage
import com.etoken.ui.EtokenNavHost
import com.etoken.ui.theme.EtokenTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    /**
     * The language the resources this activity is holding were resolved with.
     * Set in [attachBaseContext], which runs before everything else here.
     */
    private var appliedLanguage = AppLanguage.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        // The only place a locale can be applied: Resources are built from the
        // base context, once, and every stringResource() below reads them.
        appliedLanguage = AppLanguageStore.stored(newBase)
        super.attachBaseContext(AppLanguageStore.localized(newBase, appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before anything else, and before super.onCreate(): this is what swaps
        // the launch theme (Theme.Etoken.Splash, set in the manifest) for the
        // app's own. Skip the call and the activity keeps the splash theme and
        // never draws a screen.
        val splash = installSplashScreen()

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as EtokenApplication).container

        // Hold the splash while the remembered username is read. It is the
        // first thing the first screen shows, it comes from DataStore, and
        // DataStore can only be read from a coroutine -- so without this the
        // field is drawn empty and fills itself in a moment later, on the one
        // screen every launch passes through.
        //
        // Cold start only. A language change applies itself by recreating this
        // activity, which arrives with a saved state and against a store that
        // has already answered once; splashing again there would read as the
        // app restarting rather than as the language changing.
        if (savedInstanceState == null) {
            var storeAnswered = false
            splash.setKeepOnScreenCondition { !storeAnswered }
            lifecycleScope.launch {
                // Bounded, because the alternative failure is the worst one an
                // app can have: a splash that never leaves. The screen copes
                // with an empty field perfectly well -- it is what a first-time
                // user gets anyway.
                withTimeoutOrNull(STORE_TIMEOUT_MS) { container.userPreferences.username.first() }
                storeAnswered = true
            }
        }

        val languages = container.appLanguage

        setContent {
            val language by languages.language.collectAsStateWithLifecycle()

            // Picking a language cannot be recomposed into place: the strings
            // come from Resources, and Resources were handed over before this
            // composition existed. Recreating the activity re-runs
            // attachBaseContext, which is what actually applies the choice.
            LaunchedEffect(language) {
                if (language != appliedLanguage) recreate()
            }

            EtokenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    EtokenNavHost()
                }
            }
        }
    }

    private companion object {
        /** Long enough for a disk read, short enough not to be a hang. */
        const val STORE_TIMEOUT_MS = 1_000L
    }
}
