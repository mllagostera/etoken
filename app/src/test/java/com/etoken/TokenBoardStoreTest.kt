package com.etoken

import com.etoken.data.TokenBoardStore
import com.etoken.domain.TokenBoardRules
import com.etoken.domain.UndoHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store is where undo lives, because undo has to span every board: a new
 * game empties all of them at once.
 */
class TokenBoardStoreTest {

    private val goblin = "goblin"
    private val soldier = "soldier"

    @Test
    fun `undo puts back the board before the last edit`() = runTest {
        val store = TokenBoardStore()
        store.update(goblin) { TokenBoardRules.add(it, 3) }
        store.update(goblin) { TokenBoardRules.add(it, 2) }
        assertEquals(5, store.board(goblin).first().total)

        store.undo()

        assertEquals(3, store.board(goblin).first().total)
    }

    @Test
    fun `undo after a new game brings every board back`() = runTest {
        val store = TokenBoardStore()
        store.update(goblin) { TokenBoardRules.add(it, 3) }
        store.update(soldier) { TokenBoardRules.add(it, 1) }

        store.clearAll()
        assertTrue(store.all.first().isEmpty())

        store.undo()

        assertEquals(3, store.board(goblin).first().total)
        assertEquals(1, store.board(soldier).first().total)
    }

    @Test
    fun `there is nothing to undo until something changes`() = runTest {
        val store = TokenBoardStore()
        assertFalse(store.canUndo.first())

        store.update(goblin) { TokenBoardRules.add(it, 1) }

        assertTrue(store.canUndo.first())
    }

    @Test
    fun `an edit that changed nothing is not a step`() = runTest {
        val store = TokenBoardStore()
        store.update(goblin) { TokenBoardRules.add(it, 2) }

        // Adding zero is a no-op the rules already refuse, and clearing an
        // empty board is another. Neither should cost a step of history.
        store.update(goblin) { TokenBoardRules.add(it, 0) }
        store.update(soldier) { TokenBoardRules.clear(it) }

        store.undo()

        assertEquals(0, store.board(goblin).first().total)
        assertFalse(store.canUndo.first())
    }

    @Test
    fun `a board with nothing on it is not kept in the map`() = runTest {
        val store = TokenBoardStore()
        store.update(goblin) { TokenBoardRules.add(it, 2) }
        store.update(goblin) { TokenBoardRules.clear(it) }

        assertTrue(store.all.first().isEmpty())
    }

    @Test
    fun `undoing with nothing left does nothing`() = runTest {
        val store = TokenBoardStore()
        store.update(goblin) { TokenBoardRules.add(it, 4) }

        repeat(3) { store.undo() }

        assertEquals(0, store.board(goblin).first().total)
        assertFalse(store.canUndo.first())
    }

    @Test
    fun `history stops at the limit rather than growing all game`() = runTest {
        val store = TokenBoardStore()
        val steps = UndoHistory.DEFAULT_LIMIT + 5
        repeat(steps) { store.update(goblin) { board -> TokenBoardRules.add(board, 1) } }
        assertEquals(steps, store.board(goblin).first().total)

        repeat(steps) { store.undo() }

        // Five of the steps fell off the end, so the board cannot go all the
        // way back to empty -- it stops at the oldest state still remembered.
        assertEquals(steps - UndoHistory.DEFAULT_LIMIT, store.board(goblin).first().total)
        assertFalse(store.canUndo.first())
    }
}
