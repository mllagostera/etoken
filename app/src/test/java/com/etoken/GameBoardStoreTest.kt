package com.etoken

import com.etoken.data.GameBoardStore
import com.etoken.domain.BoardRules
import com.etoken.domain.UndoHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store is where undo lives, because undo has to span the whole table: a
 * new game empties every token at once.
 */
class GameBoardStoreTest {

    private val goblin = "goblin"
    private val soldier = "soldier"

    @Test
    fun `undo puts back the board before the last edit`() = runTest {
        val store = GameBoardStore()
        store.update { BoardRules.add(it, goblin, 3) }
        store.update { BoardRules.add(it, goblin, 2) }
        assertEquals(5, store.board.first().total)

        store.undo()

        assertEquals(3, store.board.first().total)
    }

    @Test
    fun `adding an entry of seven is one step, not seven`() = runTest {
        val store = GameBoardStore()
        store.update { BoardRules.add(it, goblin, 7) }

        store.undo()

        assertTrue(store.board.first().isEmpty)
        assertFalse(store.canUndo.first())
    }

    @Test
    fun `undo after a new game brings the whole table back`() = runTest {
        val store = GameBoardStore()
        store.update { BoardRules.add(it, goblin, 3) }
        store.update { BoardRules.add(it, soldier, 1) }

        store.newGame()
        assertTrue(store.board.first().isEmpty)

        store.undo()

        assertEquals(3, store.board.first().countOf(goblin))
        assertEquals(1, store.board.first().countOf(soldier))
    }

    @Test
    fun `there is nothing to undo until something changes`() = runTest {
        val store = GameBoardStore()
        assertFalse(store.canUndo.first())

        store.update { BoardRules.add(it, goblin, 1) }

        assertTrue(store.canUndo.first())
    }

    @Test
    fun `an edit that changed nothing is not a step`() = runTest {
        val store = GameBoardStore()
        store.update { BoardRules.add(it, goblin, 2) }

        // Adding zero is a no-op the rules already refuse, and removing an
        // entry that is not there is another. Neither costs a step of history.
        store.update { BoardRules.add(it, goblin, 0) }
        store.update { BoardRules.remove(it, 99) }

        store.undo()

        assertTrue(store.board.first().isEmpty)
        assertFalse(store.canUndo.first())
    }

    @Test
    fun `a new game on an empty table is not a step either`() = runTest {
        val store = GameBoardStore()

        store.newGame()

        assertFalse(store.canUndo.first())
    }

    @Test
    fun `undoing with nothing left does nothing`() = runTest {
        val store = GameBoardStore()
        store.update { BoardRules.add(it, goblin, 4) }

        repeat(3) { store.undo() }

        assertTrue(store.board.first().isEmpty)
        assertFalse(store.canUndo.first())
    }

    @Test
    fun `history stops at the limit rather than growing all game`() = runTest {
        val store = GameBoardStore()
        val steps = UndoHistory.DEFAULT_LIMIT + 5
        repeat(steps) { store.update { board -> BoardRules.add(board, goblin, 1) } }
        assertEquals(steps, store.board.first().total)

        repeat(steps) { store.undo() }

        // Five of the steps fell off the end, so the board cannot go all the
        // way back to empty -- it stops at the oldest state still remembered.
        assertEquals(steps - UndoHistory.DEFAULT_LIMIT, store.board.first().total)
        assertFalse(store.canUndo.first())
    }
}
