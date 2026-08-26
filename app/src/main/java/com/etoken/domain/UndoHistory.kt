package com.etoken.domain

/**
 * A bounded trail of past states, and the two rules that decide what counts as
 * a step worth coming back to.
 *
 * The first is that an edit which changed nothing is not a step. Tapping "-1"
 * on a stack of zero, or clearing an empty board, leaves the state exactly as
 * it was; recording it would spend a step of history and then make the player
 * press undo twice to see anything happen.
 *
 * The second is the [limit]. This is a play aid for one game at one table, and
 * nobody wants to walk back forty taps; an unbounded trail would only grow for
 * the length of the session in exchange for steps no one will ever take.
 *
 * Immutable, so the store holds one of these in a `StateFlow` beside the state
 * it describes and the two always move together.
 */
data class UndoHistory<T>(
    val past: List<T> = emptyList(),
    val limit: Int = DEFAULT_LIMIT,
) {

    val canUndo: Boolean get() = past.isNotEmpty()

    /**
     * The history that results from [previous] becoming [next], which is the
     * old state becoming a step to come back to — unless nothing changed.
     */
    fun recording(previous: T, next: T): UndoHistory<T> =
        if (previous == next) this else copy(past = (past + previous).takeLast(limit))

    /**
     * The state to go back to and the history that remains, or null when there
     * is nothing left to undo.
     */
    fun undo(): Pair<T, UndoHistory<T>>? {
        val previous = past.lastOrNull() ?: return null
        return previous to copy(past = past.dropLast(1))
    }

    companion object {
        /** Deep enough to cover a misread of the battlefield, not a whole game. */
        const val DEFAULT_LIMIT = 20
    }
}
