package com.etoken.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.edit
import com.etoken.domain.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The language the user picked, and the one piece of platform glue that applies
 * it.
 *
 * `SharedPreferences` rather than the DataStore that holds the username, which
 * looks like an oversight and is not: a locale has to be in place before the
 * activity's `Resources` are built, which means inside `attachBaseContext` —
 * earlier than `onCreate`, and with no scope to suspend in. DataStore can only
 * be read from a coroutine, so reading it there means `runBlocking` on the main
 * thread at every cold start. `SharedPreferences` answers synchronously, which
 * is exactly what that callback needs and the only thing this store is for.
 *
 * The app applies the locale itself instead of leaning on Android 13's per-app
 * language setting: `minSdk` is 26, and on Android 8 through 12 that setting
 * does not exist.
 */
class AppLanguageStore(context: Context) {

    private val preferences = preferences(context)

    private val _language = MutableStateFlow(read(preferences))

    /** Emits again when the choice changes, which is what triggers the reload. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun set(language: AppLanguage) {
        preferences.edit { putString(KEY, language.tag) }
        _language.value = language
    }

    companion object {
        private const val FILE = "etoken_language"
        private const val KEY = "app_language"

        /**
         * The stored choice, read without suspending. For `attachBaseContext`,
         * where the store itself does not exist yet.
         */
        fun stored(context: Context): AppLanguage = read(preferences(context))

        /**
         * [context] with [language] applied to its configuration, or [context]
         * untouched for [AppLanguage.SYSTEM] — leaving the configuration alone
         * is what lets Android resolve the locale the way it normally would.
         */
        fun localized(context: Context, language: AppLanguage): Context {
            if (language == AppLanguage.SYSTEM) return context

            val configuration = Configuration(context.resources.configuration)
            // setLocale, not setLocales: one language, and it also sets the
            // layout direction the resources will be resolved with.
            configuration.setLocale(Locale.forLanguageTag(language.tag))
            return context.createConfigurationContext(configuration)
        }

        // Any context of this app reaches the same file, and applicationContext
        // is not reliably there yet when attachBaseContext calls this.
        private fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        private fun read(preferences: SharedPreferences): AppLanguage =
            AppLanguage.fromTag(preferences.getString(KEY, null))
    }
}
