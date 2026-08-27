package com.etoken.data

import com.etoken.domain.UndoHistory
import com.etoken.domain.model.GameBoard
import com.etoken.domain.model.TokenCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * What is on the battlefield right now — the whole table, every token type in
 * one board.
 *
 * In-memory on purpose. The board belongs to the game being played, not to the
 * deck, so it survives navigating between decks but goes away with the process
 * — there is nothing worth restoring three days later, and a stale board
 * restored mid-game would be worse than an empty one.
 *
 * Undo is kept here rather than in a view model so it means the same thing
 * wherever it is pressed, and so that "Nueva partida" is undoable like any
 * other change: it is one more state the trail remembers.
 */
class GameBoardStore {

    private val state = MutableStateFlow(GameBoard())
    private val history = MutableStateFlow(UndoHistory<GameBoard>())
    private val catalog = MutableStateFlow<Map<String, TokenCard>>(emptyMap())

    // Every caller is the main thread today. The lock is here because the state
    // and its history have to move as one: a read-modify-write that interleaved
    // would leave undo pointing at a state that was never on screen.
    private val lock = Any()

    val board: StateFlow<GameBoard> = state.asStateFlow()

    /**
     * Every token the game has seen put into play, by id.
     *
     * The board names its entries by token id alone, and the deck on screen is
     * not always the deck an entry came from — a game can draw on more than one
     * — so the art and the name have to be remembered here rather than looked
     * up in whichever deck is open.
     *
     * Deliberately outside the undo trail, and deliberately not emptied by a
     * new game: it is a lookup table, not state anyone can see. Rewinding it
     * would leave a restored entry with nothing to draw.
     */
    val tokens: StateFlow<Map<String, TokenCard>> = catalog.asStateFlow()

    val canUndo: Flow<Boolean> = history.map { it.canUndo }

    /**
     * Applies one edit as one step of history.
     *
     * One call is one undoable thing, which is why adding an entry of seven
     * copies is a single [com.etoken.domain.BoardRules.add] rather than seven.
     */
    fun update(transform: (GameBoard) -> GameBoard) {
        synchronized(lock) { commit(state.value, transform(state.value)) }
    }

    /** Notes what a token looks like, so an entry of it can always be drawn. */
    fun remember(token: TokenCard) {
        catalog.value = catalog.value + (token.id to token)
    }

    /** New game: everything leaves the battlefield, entry ids included. */
    fun newGame() {
        synchronized(lock) { commit(state.value, GameBoard()) }
    }

    /**
     * Puts back the state before the last change that made one.
     *
     * Restoring a whole snapshot rewinds `nextEntryId` along with the entries,
     * so an id can be handed out twice over the life of a game. That is
     * harmless: the entry that first held it no longer exists, and ids only
     * ever have to be unique among the entries actually on the board.
     */
    fun undo() {
        synchronized(lock) {
            val (previous, rest) = history.value.undo() ?: return
            history.value = rest
            state.value = previous
        }
    }

    private fun commit(before: GameBoard, next: GameBoard) {
        if (next == before) return
        history.value = history.value.recording(before, next)
        state.value = next
    }
}
