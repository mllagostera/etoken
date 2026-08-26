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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etoken.data.AppLanguageStore
import com.etoken.domain.AppLanguage
import com.etoken.ui.EtokenNavHost
import com.etoken.ui.theme.EtokenTheme

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val languages = (application as EtokenApplication).container.appLanguage

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
}
