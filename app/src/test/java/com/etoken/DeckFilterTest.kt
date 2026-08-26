package com.etoken

import com.etoken.domain.DeckFilter
import com.etoken.domain.model.DeckSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckFilterTest {

    private fun deck(name: String, commander: String? = null) =
        DeckSummary(publicId = name.take(6), name = name, commander = commander)

    private val decks = listOf(
        deck("Krenko Goblins", "Krenko, Mob Boss"),
        deck("Atraxa Superfriends", "Atraxa, Praetors' Voice"),
        deck("Mazo del Cañón", "Purphoros, God of the Forge"),
        deck("Edgar Vampiros", "Edgar Markov"),
        deck("Sin comandante"),
    )

    private fun names(query: String) = DeckFilter.apply(decks, query).map { it.name }

    @Test
    fun `an empty query keeps every deck`() {
        assertEquals(decks, DeckFilter.apply(decks, ""))
        assertEquals(decks, DeckFilter.apply(decks, "   "))
    }

    @Test
    fun `matches part of the name, whatever the case`() {
        assertEquals(listOf("Krenko Goblins"), names("gobl"))
        assertEquals(listOf("Krenko Goblins"), names("KRENKO"))
    }

    @Test
    fun `accents do not have to be typed`() {
        // "Cañón" is findable as "canon", which is how people actually type.
        assertEquals(listOf("Mazo del Cañón"), names("canon"))
        assertEquals(listOf("Mazo del Cañón"), names("CAÑON"))
    }

    @Test
    fun `finds a deck by its commander`() {
        // Nothing in this deck's name says Purphoros.
        assertEquals(listOf("Mazo del Cañón"), names("purphoros"))
        assertEquals(listOf("Edgar Vampiros"), names("markov"))
    }

    @Test
    fun `every word of the query has to match`() {
        assertEquals(listOf("Krenko Goblins"), names("krenko gob"))
        // Both words exist in the list, but not in the same deck.
        assertTrue(names("krenko atraxa").isEmpty())
    }

    @Test
    fun `word order does not matter`() {
        assertEquals(names("krenko boss"), names("boss krenko"))
        assertEquals(listOf("Krenko Goblins"), names("boss krenko"))
    }

    @Test
    fun `extra spaces are ignored`() {
        assertEquals(listOf("Krenko Goblins"), names("  krenko   gob  "))
    }

    @Test
    fun `a deck with no commander yet is still searchable by name`() {
        // Covers are hydrated after the list loads, so commander is null at first.
        assertEquals(listOf("Sin comandante"), names("comandante"))
    }

    @Test
    fun `no match gives an empty list rather than everything`() {
        assertTrue(names("tergrid").isEmpty())
    }

    @Test
    fun `matching keeps the original order`() {
        // "Krenko Goblins / Krenko, Mob Boss" has no letter A anywhere in it.
        assertEquals(
            listOf("Atraxa Superfriends", "Mazo del Cañón", "Edgar Vampiros", "Sin comandante"),
            names("a"),
        )
    }
}
