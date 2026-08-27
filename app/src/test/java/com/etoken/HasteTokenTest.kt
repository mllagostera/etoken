package com.etoken

import com.etoken.data.scryfall.ScryfallCard
import com.etoken.domain.BoardRules
import com.etoken.domain.TokenExtractor
import com.etoken.domain.model.GameBoard
import com.etoken.domain.model.TokenCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tokens printed with haste enter able to attack.
 *
 * There are two halves to it and both are here: whether Scryfall says the token
 * has haste — read off `keywords`, never off the rules text — and what the
 * board does once it knows. Haste handed out at the table by another permanent
 * is neither: nothing in the app can see it, so it stays a chip the player taps.
 */
class HasteTokenTest {

    private val empty = GameBoard()

    private val hellion = "hellion-token-id"

    /** Hellion Crucible's Hellion: a 4/4 that really is printed with haste. */
    private fun printing(
        id: String,
        keywords: List<String> = emptyList(),
        oracle: String? = null,
    ) = ScryfallCard(
        id = id,
        name = "Hellion",
        typeLine = "Token Creature — Hellion",
        oracleText = oracle,
        keywords = keywords,
        power = "4",
        toughness = "4",
    )

    private fun tokenFrom(vararg printings: ScryfallCard): TokenCard = TokenExtractor.buildTokens(
        references = printings.associate { it.id to setOf("Hellion Crucible") },
        tokenCards = printings.toList(),
    ).single()

    @Test
    fun `a token whose keywords carry haste has it`() {
        assertTrue(tokenFrom(printing("hellion-t", keywords = listOf("Flying", "Haste"))).hasHaste)
    }

    @Test
    fun `a token without the keyword does not`() {
        assertFalse(tokenFrom(printing("hellion-t", keywords = listOf("Flying"))).hasHaste)
    }

    @Test
    fun `rules text that only grants haste is not the token having it`() {
        // A token that hands haste to everything else has none of its own.
        // Reading the keyword list is what keeps those two apart, and is why
        // this app parses no rules text to answer the question.
        val lord = printing("hellion-t", oracle = "Other creatures you control have haste.")

        assertFalse(tokenFrom(lord).hasHaste)
    }

    @Test
    fun `a printing that came back without keywords cannot take haste away`() {
        // The two collapse into one token, as printings of the same token do.
        // Whichever order they arrive in, the one that knows about the keyword
        // is the one telling the truth.
        val known = printing("hellion-2010", keywords = listOf("Haste"))
        val bare = printing("hellion-2019")

        assertTrue(tokenFrom(known, bare).hasHaste)
        assertTrue(tokenFrom(bare, known).hasHaste)
    }

    @Test
    fun `copies of a hasty token arrive able to attack`() {
        val board = BoardRules.add(empty, hellion, 2, entersSick = false)

        assertFalse(board.entries.single().summoningSick)
        assertEquals(0, board.summoningSickCount)
        assertEquals(2, board.total)
    }

    @Test
    fun `every hasty entry can attack, however many entries there are`() {
        // Two made this turn and three made last turn are five Hellions able to
        // attack. They are two entries now -- each press of "add" is its own
        // thing on the table -- and the count that matters is the same either way.
        val board = BoardRules.add(
            BoardRules.add(empty, hellion, 3, entersSick = false),
            hellion,
            2,
            entersSick = false,
        )

        assertEquals(2, board.entries.size)
        assertEquals(5, board.total)
        assertEquals(0, board.summoningSickCount)
    }

    @Test
    fun `the untap step has nothing left to do`() {
        val board = BoardRules.add(empty, hellion, 2, entersSick = false)

        assertEquals(board, BoardRules.beginTurn(board))
    }

    @Test
    fun `the chip can still put summoning sickness back by hand`() {
        // Printed haste can be turned off at the table — a Torpor Orb effect,
        // or simply a miscount — so automating it must not take the control away.
        val hasty = BoardRules.add(empty, hellion, 2, entersSick = false)

        val board = BoardRules.setSummoningSick(hasty, hasty.entries.single().id, sick = true)

        assertTrue(board.entries.single().summoningSick)
        assertEquals(2, board.summoningSickCount)
    }
}
