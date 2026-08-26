package com.etoken

import com.etoken.domain.TokenBoardRules
import com.etoken.domain.TokenFilter
import com.etoken.domain.model.TokenBoard
import com.etoken.domain.model.TokenCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TokenFilterTest {

    private fun token(id: String) = TokenCard(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        typeLine = "Token Creature",
        imageUrl = null,
        oracleText = null,
        createdBy = emptyList(),
    )

    private val tokens = listOf(token("goblin"), token("treasure"), token("soldier"))

    /** A board with [quantity] copies on it, built through the real rules. */
    private fun board(quantity: Int) = TokenBoardRules.add(TokenBoard(), quantity)

    private fun inPlay(vararg counts: Pair<String, Int>) =
        counts.associate { (id, quantity) -> id to board(quantity) }

    private fun ids(inPlay: Map<String, TokenBoard>) =
        TokenFilter.apply(tokens, inPlay, onlyInPlay = true).map { it.id }

    @Test
    fun `the filter off keeps every token, board or no board`() {
        assertSame(tokens, TokenFilter.apply(tokens, emptyMap(), onlyInPlay = false))
        assertSame(tokens, TokenFilter.apply(tokens, inPlay("goblin" to 4), onlyInPlay = false))
    }

    @Test
    fun `the filter on keeps the ones with copies on the battlefield`() {
        assertEquals(listOf("goblin", "soldier"), ids(inPlay("goblin" to 4, "soldier" to 1)))
    }

    @Test
    fun `the deck's order survives the filter`() {
        // The grid must not reshuffle itself when the chip is tapped: the tokens
        // that stay are in the order they were already in.
        assertEquals(listOf("goblin", "treasure"), ids(inPlay("treasure" to 2, "goblin" to 1)))
    }

    @Test
    fun `a token in play from another deck does not smuggle itself in`() {
        // Boards are keyed by Scryfall id across every deck, so the map this
        // reads can name tokens this deck never creates.
        assertEquals(emptyList<String>(), ids(inPlay("angel" to 3)))
    }

    @Test
    fun `nothing in play leaves the grid empty rather than full`() {
        // The screen turns this into "no tienes ninguno en juego"; the rule
        // itself must not quietly fall back to showing everything.
        assertEquals(emptyList<String>(), ids(emptyMap()))
    }

    @Test
    fun `an empty board counts as absent`() {
        // TokenBoardStore drops emptied boards from the map, so this is
        // defensive rather than observed -- but a token with no copies is not
        // in play, however it came to be written down.
        assertEquals(
            listOf("treasure"),
            ids(mapOf("goblin" to TokenBoard(), "treasure" to board(1))),
        )
    }
}
