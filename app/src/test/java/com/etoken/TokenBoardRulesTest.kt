package com.etoken

import com.etoken.domain.TokenBoardRules
import com.etoken.domain.model.SummoningSickness
import com.etoken.domain.model.TokenBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBoardRulesTest {

    private val empty = TokenBoard()

    @Test
    fun `tokens arrive summoning sick and without counters`() {
        val board = TokenBoardRules.add(empty, 3)

        val stack = board.stacks.single()
        assertEquals(3, stack.quantity)
        assertEquals(0, stack.plusOneCounters)
        assertTrue(stack.summoningSick)
        assertEquals(3, board.total)
        assertEquals(3, board.summoningSickCount)
    }

    @Test
    fun `adding twice in the same turn keeps one stack`() {
        val board = TokenBoardRules.add(TokenBoardRules.add(empty, 3), 4)

        assertEquals(1, board.stacks.size)
        assertEquals(7, board.total)
    }

    @Test
    fun `adding is a no-op for zero or negative`() {
        assertTrue(TokenBoardRules.add(empty, 0).isEmpty)
        assertTrue(TokenBoardRules.add(empty, -2).isEmpty)
    }

    @Test
    fun `tokens made after the untap step stay separate from the ready ones`() {
        val ready = TokenBoardRules.beginTurn(TokenBoardRules.add(empty, 2))
        val board = TokenBoardRules.add(ready, 3)

        assertEquals(2, board.stacks.size)
        assertEquals(5, board.total)
        assertEquals(3, board.summoningSickCount)
        // Ready ones sort first: they are the ones you can actually attack with.
        assertFalse(board.stacks.first().summoningSick)
    }

    @Test
    fun `starting a turn clears sickness and merges what is now identical`() {
        val mixed = TokenBoardRules.add(
            TokenBoardRules.beginTurn(TokenBoardRules.add(empty, 2)),
            3,
        )

        val board = TokenBoardRules.beginTurn(mixed)

        assertEquals(1, board.stacks.size)
        assertEquals(5, board.total)
        assertEquals(0, board.summoningSickCount)
    }

    @Test
    fun `a counter can land on only some of a stack`() {
        val board = TokenBoardRules.addCounters(
            TokenBoardRules.add(empty, 7),
            stackId = 1,
            delta = 1,
            appliesTo = 3,
        )

        assertEquals(2, board.stacks.size)
        assertEquals(7, board.total)
        // Bigger creatures sort first.
        assertEquals(listOf(3 to 1, 4 to 0), board.stacks.map { it.quantity to it.plusOneCounters })
    }

    @Test
    fun `taking that counter off again collapses back into one stack`() {
        val split = TokenBoardRules.addCounters(TokenBoardRules.add(empty, 7), 1, 1, appliesTo = 3)
        val counted = split.stacks.single { it.plusOneCounters == 1 }

        val board = TokenBoardRules.addCounters(split, counted.id, -1)

        assertEquals(1, board.stacks.size)
        assertEquals(7, board.total)
        assertEquals(0, board.stacks.single().plusOneCounters)
        // The surviving row keeps the older id, so the list does not re-animate.
        assertEquals(1L, board.stacks.single().id)
    }

    @Test
    fun `applying a counter to all of a stack does not split it`() {
        val board = TokenBoardRules.addCounters(TokenBoardRules.add(empty, 4), 1, 1)

        assertEquals(1, board.stacks.size)
        assertEquals(1, board.stacks.single().plusOneCounters)
        assertEquals(1L, board.stacks.single().id)
    }

    @Test
    fun `counters never go below zero`() {
        val board = TokenBoardRules.addCounters(TokenBoardRules.add(empty, 2), 1, -5)

        assertEquals(0, board.stacks.single().plusOneCounters)
    }

    @Test
    fun `a counter on everything grows every stack at once`() {
        val split = TokenBoardRules.addCounters(TokenBoardRules.add(empty, 7), 1, 1, appliesTo = 3)

        val board = TokenBoardRules.addCountersToAll(split, 1)

        assertEquals(listOf(3 to 2, 4 to 1), board.stacks.map { it.quantity to it.plusOneCounters })
        assertEquals(7, board.total)
    }

    @Test
    fun `removing the last token drops the stack`() {
        val board = TokenBoardRules.changeQuantity(TokenBoardRules.add(empty, 1), 1, -1)

        assertTrue(board.isEmpty)
        assertEquals(0, board.total)
    }

    @Test
    fun `a stack cannot go negative`() {
        val board = TokenBoardRules.changeQuantity(TokenBoardRules.add(empty, 2), 1, -9)

        assertTrue(board.isEmpty)
    }

    @Test
    fun `sickness can be lifted for part of a stack`() {
        val board = TokenBoardRules.setSummoningSick(
            TokenBoardRules.add(empty, 5),
            stackId = 1,
            sick = false,
            appliesTo = 2,
        )

        assertEquals(2, board.stacks.size)
        assertEquals(5, board.total)
        assertEquals(3, board.summoningSickCount)
    }

    @Test
    fun `editing a stack that is not there changes nothing`() {
        val board = TokenBoardRules.add(empty, 2)

        assertEquals(board, TokenBoardRules.addCounters(board, stackId = 99, delta = 1))
        assertEquals(board, TokenBoardRules.setSummoningSick(board, 99, false))
        assertEquals(board, TokenBoardRules.changeQuantity(board, 99, 1))
    }

    @Test
    fun `applying a counter to nobody changes nothing`() {
        val board = TokenBoardRules.add(empty, 4)

        assertEquals(board, TokenBoardRules.addCounters(board, 1, 1, appliesTo = 0))
    }

    @Test
    fun `asking for more than the stack holds just takes the whole stack`() {
        val board = TokenBoardRules.addCounters(TokenBoardRules.add(empty, 4), 1, 1, appliesTo = 99)

        assertEquals(1, board.stacks.size)
        assertEquals(4, board.stacks.single().quantity)
    }

    @Test
    fun `a copy remembers what it copies`() {
        val board = TokenBoardRules.add(empty, 2, copying = "Krenko, Mob Boss")

        assertEquals("Krenko, Mob Boss", board.stacks.single().copying)
    }

    @Test
    fun `copies of different creatures never merge`() {
        // The whole point of putting it in the signature: these are two things
        // on the battlefield, however identical their token card is.
        var board = TokenBoardRules.add(empty, 2, copying = "Krenko, Mob Boss")
        board = TokenBoardRules.add(board, 1, copying = "Atraxa, Praetors' Voice")

        assertEquals(2, board.stacks.size)
        assertEquals(3, board.total)
        assertEquals(
            setOf("Krenko, Mob Boss", "Atraxa, Praetors' Voice"),
            board.stacks.mapNotNull { it.copying }.toSet(),
        )
    }

    @Test
    fun `copies of the same creature do merge`() {
        var board = TokenBoardRules.add(empty, 2, copying = "Krenko, Mob Boss")
        board = TokenBoardRules.add(board, 3, copying = "Krenko, Mob Boss")

        assertEquals(1, board.stacks.size)
        assertEquals(5, board.total)
    }

    @Test
    fun `a copy and a plain token are never the same stack`() {
        var board = TokenBoardRules.add(empty, 1, copying = "Krenko, Mob Boss")
        board = TokenBoardRules.add(board, 1)

        assertEquals(2, board.stacks.size)
    }

    @Test
    fun `a blank copy name is stored as none at all`() {
        assertNull(TokenBoardRules.add(empty, 1, copying = "   ").stacks.single().copying)
    }

    @Test
    fun `counters still split a copy stack without losing what it copies`() {
        val board = TokenBoardRules.addCounters(
            TokenBoardRules.add(empty, 4, copying = "Krenko, Mob Boss"),
            stackId = 1,
            delta = 1,
            appliesTo = 1,
        )

        assertEquals(2, board.stacks.size)
        assertTrue(board.stacks.all { it.copying == "Krenko, Mob Boss" })
    }

    @Test
    fun `clearing empties the battlefield`() {
        val board = TokenBoardRules.clear(TokenBoardRules.add(empty, 6))

        assertTrue(board.isEmpty)
        assertEquals(0, board.total)
    }

    @Test
    fun `stacks are ordered ready first, then by size`() {
        // 7 sick tokens; 3 of them get a counter, then 2 of those become ready.
        var board = TokenBoardRules.add(empty, 7)
        board = TokenBoardRules.addCounters(board, 1, 1, appliesTo = 3)
        val counted = board.stacks.single { it.plusOneCounters == 1 }
        board = TokenBoardRules.setSummoningSick(board, counted.id, sick = false, appliesTo = 2)

        assertEquals(
            listOf(
                Triple(2, 1, false),
                Triple(1, 1, true),
                Triple(4, 0, true),
            ),
            board.stacks.map { Triple(it.quantity, it.plusOneCounters, it.summoningSick) },
        )
        assertEquals(7, board.total)
        assertEquals(5, board.summoningSickCount)
    }

    @Test
    fun `one stack can name the counters its copies carry`() {
        // What the token grid's badge is drawn from: four Goblins in one state,
        // so "+1/+1 ×2" is true of every copy in play.
        val board = TokenBoardRules.addCounters(
            TokenBoardRules.add(empty, 4),
            stackId = 1,
            delta = 2,
        )

        assertEquals(2, board.uniformPlusOneCounters)
    }

    @Test
    fun `no counters on the only stack is an answer, not a missing one`() {
        // Zero and "cannot say" are different: the grid draws no badge for
        // either, but the board is not being asked to guess here.
        assertEquals(0, TokenBoardRules.add(empty, 4).uniformPlusOneCounters)
    }

    @Test
    fun `two stacks cannot name one number of counters`() {
        // 3 of the 7 got a counter, so no single figure is true of the table.
        val board = TokenBoardRules.addCounters(
            TokenBoardRules.add(empty, 7),
            stackId = 1,
            delta = 1,
            appliesTo = 3,
        )

        assertEquals(2, board.stacks.size)
        assertNull(board.uniformPlusOneCounters)
    }

    @Test
    fun `an empty battlefield has no counters to name`() {
        assertNull(empty.uniformPlusOneCounters)
    }

    @Test
    fun `stacks that merge back together can name their counters again`() {
        // The split above, undone: the counter comes off the three, normalize
        // merges them back, and the board has one answer once more.
        var board = TokenBoardRules.addCounters(TokenBoardRules.add(empty, 7), 1, 1, appliesTo = 3)
        val counted = board.stacks.single { it.plusOneCounters == 1 }
        board = TokenBoardRules.addCounters(board, counted.id, -1)

        assertEquals(1, board.stacks.size)
        assertEquals(0, board.uniformPlusOneCounters)
    }

    @Test
    fun `a table where every copy is waiting says so without a number`() {
        // What the grid's badge is drawn from: five Goblins that all entered
        // this turn. The count would only repeat the ×5 already on the cell.
        val board = TokenBoardRules.add(empty, 5)

        assertEquals(SummoningSickness.All, board.summoningSickness)
    }

    @Test
    fun `part of the table waiting is the case that needs the number`() {
        // Two of the five can attack, three cannot, and neither half describes
        // the cell on its own.
        var board = TokenBoardRules.add(empty, 5)
        board = TokenBoardRules.setSummoningSick(board, 1, sick = false, appliesTo = 2)

        assertEquals(SummoningSickness.Some(3), board.summoningSickness)
    }

    @Test
    fun `the untap step leaves nothing waiting`() {
        val board = TokenBoardRules.beginTurn(TokenBoardRules.add(empty, 5))

        assertEquals(SummoningSickness.None, board.summoningSickness)
    }

    @Test
    fun `an empty battlefield is not a battlefield of sick tokens`() {
        // Zero of zero is All if the cases are read in the wrong order, and a
        // sickness badge on a token with nothing in play is the worst of the
        // three answers.
        assertEquals(SummoningSickness.None, empty.summoningSickness)
    }

    @Test
    fun `a token with haste is not waiting the turn it arrives`() {
        val board = TokenBoardRules.add(empty, 3, entersSick = false)

        assertEquals(SummoningSickness.None, board.summoningSickness)
    }

    @Test
    fun `tokens arrive untapped unless told otherwise`() {
        assertFalse(TokenBoardRules.add(empty, 3).stacks.single().tapped)
    }

    @Test
    fun `a token can be told to enter tapped`() {
        val board = TokenBoardRules.add(empty, 3, entersTapped = true)

        assertTrue(board.stacks.single().tapped)
    }

    @Test
    fun `tapped and untapped copies of the same token stay two stacks`() {
        var board = TokenBoardRules.add(empty, 2, entersTapped = true)
        board = TokenBoardRules.add(board, 3)

        assertEquals(2, board.stacks.size)
        assertEquals(5, board.total)
    }

    @Test
    fun `a stack can be tapped and untapped by hand`() {
        val untapped = TokenBoardRules.add(empty, 2)

        val tapped = TokenBoardRules.setTapped(untapped, stackId = 1, tapped = true)
        assertTrue(tapped.stacks.single().tapped)

        val backUntapped = TokenBoardRules.setTapped(tapped, stackId = tapped.stacks.single().id, tapped = false)
        assertFalse(backUntapped.stacks.single().tapped)
    }

    @Test
    fun `tapping part of a stack splits it, same as sickness and counters`() {
        val board = TokenBoardRules.setTapped(
            TokenBoardRules.add(empty, 5),
            stackId = 1,
            tapped = true,
            appliesTo = 2,
        )

        assertEquals(2, board.stacks.size)
        assertEquals(5, board.total)
        assertEquals(2, board.stacks.single { it.tapped }.quantity)
    }

    @Test
    fun `the untap step also untaps everything, not only sickness`() {
        val board = TokenBoardRules.beginTurn(TokenBoardRules.add(empty, 4, entersTapped = true))

        assertTrue(board.stacks.none { it.tapped })
        assertFalse(board.stacks.single().summoningSick)
    }
}
