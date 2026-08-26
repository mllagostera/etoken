package com.etoken

import com.etoken.domain.UndoHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryTest {

    @Test
    fun `a fresh history has nothing to undo`() {
        val history = UndoHistory<String>()

        assertFalse(history.canUndo)
        assertNull(history.undo())
    }

    @Test
    fun `undo returns the state before the change`() {
        val history = UndoHistory<String>().recording("a", "b")

        val (previous, rest) = history.undo()!!
        assertEquals("a", previous)
        assertFalse(rest.canUndo)
    }

    @Test
    fun `steps come back in reverse order`() {
        var history = UndoHistory<String>().recording("a", "b").recording("b", "c")

        val (first, afterFirst) = history.undo()!!
        assertEquals("b", first)

        val (second, afterSecond) = afterFirst.undo()!!
        assertEquals("a", second)
        assertNull(afterSecond.undo())

        // The original is untouched: recording and undoing both return copies.
        assertTrue(history.canUndo)
    }

    @Test
    fun `a change that changed nothing is not a step`() {
        val history = UndoHistory<String>().recording("a", "a")

        assertFalse(history.canUndo)
    }

    @Test
    fun `the trail stops at the limit, dropping the oldest`() {
        var history = UndoHistory<Int>(limit = 3)
        (1..5).forEach { history = history.recording(it, it + 1) }

        assertEquals(listOf(3, 4, 5), history.past)
    }

    @Test
    fun `undoing past the limit eventually runs out`() {
        var history = UndoHistory<Int>(limit = 2)
        (1..4).forEach { history = history.recording(it, it + 1) }

        assertEquals(4, history.undo()!!.first)
        assertEquals(3, history.undo()!!.second.undo()!!.first)
        assertNull(history.undo()!!.second.undo()!!.second.undo())
    }
}
