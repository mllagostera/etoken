package com.etoken

import com.etoken.domain.BoardRules
import com.etoken.domain.model.GameBoard
import com.etoken.domain.model.SummoningSickness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRulesTest {

    private val empty = GameBoard()

    private val goblin = "goblin-token-id"
    private val treasure = "treasure-token-id"

    private fun add(
        board: GameBoard = empty,
        tokenId: String = goblin,
        quantity: Int = 1,
        copying: String? = null,
        entersSick: Boolean = true,
        entersTapped: Boolean = false,
    ) = BoardRules.add(board, tokenId, quantity, copying, entersSick, entersTapped)

    @Test
    fun `tokens arrive summoning sick and without counters`() {
        val board = add(quantity = 3)

        val entry = board.entries.single()
        assertEquals(goblin, entry.tokenId)
        assertEquals(3, entry.quantity)
        assertEquals(0, entry.plusOneCounters)
        assertTrue(entry.summoningSick)
        assertEquals(3, board.total)
        assertEquals(3, board.summoningSickCount)
    }

    @Test
    fun `adding twice makes two entries, however alike they are`() {
        // The rule the whole redesign turns on: each press of "add" is its own
        // thing on the table, and an earlier version of this file merged them.
        val board = add(add(quantity = 3), quantity = 3)

        assertEquals(2, board.entries.size)
        assertEquals(listOf(3, 3), board.entries.map { it.quantity })
        assertEquals(6, board.total)
        // Two entries, two identities: editing one cannot touch the other.
        assertEquals(2, board.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `the newest entry is the last one, not the first`() {
        var board = add(quantity = 2)
        board = add(board, tokenId = treasure, quantity = 1)

        assertEquals(listOf(goblin, treasure), board.entries.map { it.tokenId })
    }

    @Test
    fun `the board mixes token types and counts each of them`() {
        var board = add(quantity = 3)
        board = add(board, tokenId = treasure, quantity = 2)
        board = add(board, quantity = 1)

        assertEquals(6, board.total)
        assertEquals(4, board.countOf(goblin))
        assertEquals(2, board.countOf(treasure))
        assertEquals(0, board.countOf("something-else"))
    }

    @Test
    fun `adding is a no-op for zero or negative`() {
        assertTrue(add(quantity = 0).isEmpty)
        assertTrue(add(quantity = -2).isEmpty)
    }

    @Test
    fun `starting a turn clears sickness without joining entries up`() {
        val mixed = add(BoardRules.beginTurn(add(quantity = 2)), quantity = 3)

        val board = BoardRules.beginTurn(mixed)

        assertEquals(2, board.entries.size)
        assertEquals(5, board.total)
        assertEquals(0, board.summoningSickCount)
    }

    @Test
    fun `a counter can land on only some of an entry`() {
        val board = BoardRules.addCounters(add(quantity = 7), entryId = 1, delta = 1, appliesTo = 3)

        assertEquals(2, board.entries.size)
        assertEquals(7, board.total)
        // The peeled-off copies stay beside the ones they came from.
        assertEquals(listOf(4 to 0, 3 to 1), board.entries.map { it.quantity to it.plusOneCounters })
    }

    @Test
    fun `taking that counter off again leaves both halves in place`() {
        val split = BoardRules.addCounters(add(quantity = 7), 1, 1, appliesTo = 3)
        val counted = split.entries.single { it.plusOneCounters == 1 }

        val board = BoardRules.addCounters(split, counted.id, -1)

        // They matched again, and stayed apart: those three are three
        // permanents that have been through something the others have not.
        assertEquals(2, board.entries.size)
        assertEquals(listOf(4, 3), board.entries.map { it.quantity })
        assertEquals(7, board.total)
        assertTrue(board.entries.all { it.plusOneCounters == 0 })
    }

    @Test
    fun `applying a counter to all of an entry does not split it`() {
        val board = BoardRules.addCounters(add(quantity = 4), 1, 1)

        assertEquals(1, board.entries.size)
        assertEquals(1, board.entries.single().plusOneCounters)
        assertEquals(1L, board.entries.single().id)
    }

    @Test
    fun `counters never go below zero`() {
        val board = BoardRules.addCounters(add(quantity = 2), 1, -5)

        assertEquals(0, board.entries.single().plusOneCounters)
    }

    @Test
    fun `a counter on everything grows every entry of every token`() {
        var board = add(quantity = 3)
        board = add(board, tokenId = treasure, quantity = 2)

        board = BoardRules.addCountersToAll(board, 1)

        assertEquals(listOf(1, 1), board.entries.map { it.plusOneCounters })
        assertEquals(5, board.total)
    }

    @Test
    fun `removing the last copy drops the entry`() {
        val board = BoardRules.changeQuantity(add(quantity = 1), 1, -1)

        assertTrue(board.isEmpty)
        assertEquals(0, board.total)
    }

    @Test
    fun `an entry cannot go negative`() {
        assertTrue(BoardRules.changeQuantity(add(quantity = 2), 1, -9).isEmpty)
    }

    @Test
    fun `an entry can be taken off the board whole`() {
        var board = add(quantity = 3)
        board = add(board, tokenId = treasure, quantity = 2)

        board = BoardRules.remove(board, board.entries.first().id)

        assertEquals(1, board.entries.size)
        assertEquals(treasure, board.entries.single().tokenId)
    }

    @Test
    fun `removing an entry that is not there changes nothing`() {
        val board = add(quantity = 3)

        assertEquals(board, BoardRules.remove(board, 99))
    }

    @Test
    fun `correcting a count joins the copies to that entry rather than starting one`() {
        var board = add(quantity = 2)
        board = BoardRules.changeQuantity(board, 1, 3)

        assertEquals(1, board.entries.size)
        assertEquals(5, board.entries.single().quantity)
    }

    @Test
    fun `sickness can be lifted for part of an entry`() {
        val board = BoardRules.setSummoningSick(
            add(quantity = 5),
            entryId = 1,
            sick = false,
            appliesTo = 2,
        )

        assertEquals(2, board.entries.size)
        assertEquals(5, board.total)
        assertEquals(3, board.summoningSickCount)
    }

    @Test
    fun `editing an entry that is not there changes nothing`() {
        val board = add(quantity = 2)

        assertEquals(board, BoardRules.addCounters(board, entryId = 99, delta = 1))
        assertEquals(board, BoardRules.setSummoningSick(board, 99, false))
        assertEquals(board, BoardRules.changeQuantity(board, 99, 1))
    }

    @Test
    fun `applying a counter to nobody changes nothing`() {
        val board = add(quantity = 4)

        assertEquals(board, BoardRules.addCounters(board, 1, 1, appliesTo = 0))
    }

    @Test
    fun `asking for more than the entry holds just takes the whole entry`() {
        val board = BoardRules.addCounters(add(quantity = 4), 1, 1, appliesTo = 99)

        assertEquals(1, board.entries.size)
        assertEquals(4, board.entries.single().quantity)
    }

    @Test
    fun `a copy remembers what it copies`() {
        val board = add(quantity = 2, copying = "Krenko, Mob Boss")

        assertEquals("Krenko, Mob Boss", board.entries.single().copying)
    }

    @Test
    fun `copies of different creatures are different entries`() {
        var board = add(quantity = 2, copying = "Krenko, Mob Boss")
        board = add(board, quantity = 1, copying = "Atraxa, Praetors' Voice")

        assertEquals(2, board.entries.size)
        assertEquals(3, board.total)
        assertEquals(
            listOf("Krenko, Mob Boss", "Atraxa, Praetors' Voice"),
            board.entries.mapNotNull { it.copying },
        )
    }

    @Test
    fun `a blank copy name is stored as none at all`() {
        assertNull(add(quantity = 1, copying = "   ").entries.single().copying)
    }

    @Test
    fun `counters still split a copy entry without losing what it copies`() {
        val board = BoardRules.addCounters(
            add(quantity = 4, copying = "Krenko, Mob Boss"),
            entryId = 1,
            delta = 1,
            appliesTo = 1,
        )

        assertEquals(2, board.entries.size)
        assertTrue(board.entries.all { it.copying == "Krenko, Mob Boss" })
    }

    @Test
    fun `clearing empties the battlefield`() {
        val board = BoardRules.clear(add(quantity = 6))

        assertTrue(board.isEmpty)
        assertEquals(0, board.total)
    }

    @Test
    fun `a table where every copy is waiting says so without a number`() {
        assertEquals(SummoningSickness.All, add(quantity = 5).summoningSickness)
    }

    @Test
    fun `part of the table waiting is the case that needs the number`() {
        var board = add(quantity = 5)
        board = BoardRules.setSummoningSick(board, 1, sick = false, appliesTo = 2)

        assertEquals(SummoningSickness.Some(3), board.summoningSickness)
    }

    @Test
    fun `the untap step leaves nothing waiting`() {
        assertEquals(SummoningSickness.None, BoardRules.beginTurn(add(quantity = 5)).summoningSickness)
    }

    @Test
    fun `an empty battlefield is not a battlefield of sick tokens`() {
        // Zero of zero is All if the cases are read in the wrong order, and a
        // sickness badge over an empty table is the worst of the three answers.
        assertEquals(SummoningSickness.None, empty.summoningSickness)
    }

    @Test
    fun `a token with haste is not waiting the turn it arrives`() {
        assertEquals(SummoningSickness.None, add(quantity = 3, entersSick = false).summoningSickness)
    }

    @Test
    fun `tokens arrive untapped unless told otherwise`() {
        assertFalse(add(quantity = 3).entries.single().tapped)
    }

    @Test
    fun `a token can be told to enter tapped`() {
        assertTrue(add(quantity = 3, entersTapped = true).entries.single().tapped)
    }

    @Test
    fun `an entry can be tapped and untapped by hand`() {
        val untapped = add(quantity = 2)

        val tapped = BoardRules.setTapped(untapped, entryId = 1, tapped = true)
        assertTrue(tapped.entries.single().tapped)

        val back = BoardRules.setTapped(tapped, entryId = 1, tapped = false)
        assertFalse(back.entries.single().tapped)
    }

    @Test
    fun `tapping part of an entry splits it, same as sickness and counters`() {
        val board = BoardRules.setTapped(add(quantity = 5), entryId = 1, tapped = true, appliesTo = 2)

        assertEquals(2, board.entries.size)
        assertEquals(5, board.total)
        assertEquals(2, board.entries.single { it.tapped }.quantity)
    }

    @Test
    fun `the untap step also untaps everything, not only sickness`() {
        val board = BoardRules.beginTurn(add(quantity = 4, entersTapped = true))

        assertTrue(board.entries.none { it.tapped })
        assertFalse(board.entries.single().summoningSick)
    }
}
