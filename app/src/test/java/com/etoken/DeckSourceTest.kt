package com.etoken

import com.etoken.domain.DeckSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter behind the two ways into the deck grid. Small, but it is the one
 * place that decides what Moxfield is asked for and what the screen is titled.
 */
class DeckSourceTest {

    @Test
    fun `a user listing carries no format`() {
        assertNull(DeckSource("vansid").format)
        assertFalse(DeckSource("vansid").isPrecons)
    }

    @Test
    fun `the precons source is Wizards filtered to the precon format`() {
        assertEquals("WizardsOfTheCoast", DeckSource.PRECONS.username)
        assertEquals("commanderPrecons", DeckSource.PRECONS.format)
        assertTrue(DeckSource.PRECONS.isPrecons)
    }

    @Test
    fun `Wizards without the format is just another user`() {
        // The account publishes far more than Commander precons, so the author
        // alone is not the listing -- the format is what makes it one.
        assertFalse(DeckSource(DeckSource.PRECON_AUTHOR).isPrecons)
    }

    @Test
    fun `a blank format off the route means no filter`() {
        // Route arguments cannot be absent once the screen declares them, so
        // "no filter" arrives as an empty string and has to come back as null.
        assertNull(DeckSource.of("vansid", "").format)
        assertNull(DeckSource.of("vansid", "   ").format)
        assertNull(DeckSource.of("vansid", null).format)
    }

    @Test
    fun `a username off the route is trimmed`() {
        assertEquals(DeckSource("vansid"), DeckSource.of("  vansid  ", null))
    }

    @Test
    fun `the precons route rebuilds the precons source`() {
        val rebuilt = DeckSource.of(DeckSource.PRECONS.username, DeckSource.PRECONS.format)

        assertEquals(DeckSource.PRECONS, rebuilt)
        assertTrue(rebuilt.isPrecons)
    }
}
