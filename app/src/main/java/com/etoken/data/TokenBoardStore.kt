package com.etoken.data

import com.etoken.domain.UndoHistory
import com.etoken.domain.model.TokenBoard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * What is on the battlefield right now, per token.
 *
 * In-memory on purpose. The board belongs to the game being played, not to the
 * deck, so it survives navigating between tokens and decks but goes away with
 * the process — there is nothing worth restoring three days later, and a stale
 * board restored mid-game would be worse than an empty one.
 *
 * Undo is kept here, over every board at once, rather than one trail per token.
 * That is what makes "Nueva partida" undoable at all — it empties every board,
 * and a per-token trail could not put that back. It also means undo means the
 * same thing wherever it is pressed: put back whatever just changed.
 */
class TokenBoardStore {

    private val boards = MutableStateFlow<Map<String, TokenBoard>>(emptyMap())
    private val history = MutableStateFlow(UndoHistory<Map<String, TokenBoard>>())

    // Every caller is the main thread today. The lock is here because the state
    // and its history have to move as one: a read-modify-write that interleaved
    // would leave undo pointing at a state that was never on screen.
    private val lock = Any()

    /** Every board at once, which is what the token grid's badges read. */
    val all: Flow<Map<String, TokenBoard>> = boards.asStateFlow()

    val canUndo: Flow<Boolean> = history.map { it.canUndo }

    fun board(tokenId: String): Flow<TokenBoard> =
        boards.map { it[tokenId] ?: TokenBoard() }

    fun update(tokenId: String, transform: (TokenBoard) -> TokenBoard) {
        synchronized(lock) {
            val before = boards.value
            val next = transform(before[tokenId] ?: TokenBoard())
            // A board with nothing on it leaves the map rather than sitting in
            // it as an empty entry. That keeps "has anything changed?" an
            // honest question: without it, clearing a board that was already
            // empty -- or reading one that had never been touched -- would
            // count as a change and cost a step of undo history.
            commit(before, if (next.isEmpty) before - tokenId else before + (tokenId to next))
        }
    }

    /** New game: everything leaves the battlefield. */
    fun clearAll() {
        synchronized(lock) { commit(boards.value, emptyMap()) }
    }

    /**
     * Puts back the state before the last change that made one.
     *
     * Restoring a whole snapshot rewinds `nextStackId` along with the stacks,
     * so an id can be handed out twice over the life of a game. That is
     * harmless: the stack that first held it no longer exists, and ids only
     * ever have to be unique among the stacks actually on the board.
     */
    fun undo() {
        synchronized(lock) {
            val (previous, rest) = history.value.undo() ?: return
            history.value = rest
            boards.value = previous
        }
    }

    private fun commit(before: Map<String, TokenBoard>, next: Map<String, TokenBoard>) {
        if (next == before) return
        history.value = history.value.recording(before, next)
        boards.value = next
    }
}
