package com.etoken

import com.etoken.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppLanguageTest {

    @Test
    fun `a stored tag comes back as the language it names`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("es"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromTag("ja"))
    }

    @Test
    fun `nothing stored means the device decides`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
    }

    @Test
    fun `a tag this version no longer offers falls back rather than blowing up`() {
        // What an old preference looks like after a locale is dropped.
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("pt"))
    }

    @Test
    fun `the device's own language is the first thing the picker offers`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.entries.first())
    }

    @Test
    fun `no two languages claim the same tag`() {
        val tags = AppLanguage.entries.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
    }

    /**
     * The one that earns its keep: a language added to the enum without a
     * `values-` folder behind it would offer a switch that changes nothing.
     * English is the exception on purpose — it is the default locale, so its
     * strings live in `values/` and a `values-en/` would be dead weight.
     */
    @Test
    fun `every language on offer has strings behind it`() {
        AppLanguage.entries.forEach { language ->
            val folder = when (language) {
                AppLanguage.SYSTEM -> return@forEach
                AppLanguage.ENGLISH -> "values"
                else -> "values-${language.tag}"
            }
            val strings = File(resources, "$folder/strings.xml")
            assertTrue("$folder/strings.xml is missing", strings.isFile)
        }
    }

    private companion object {
        /**
         * Unit tests run with the module directory as their working directory,
         * but a run started from the repository root does not — try both
         * rather than depend on which.
         */
        val resources: File = listOf("src/main/res", "app/src/main/res")
            .map(::File)
            .first { it.isDirectory }
    }
}
