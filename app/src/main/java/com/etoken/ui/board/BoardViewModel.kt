package com.etoken.ui.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.etoken.EtokenApplication
import com.etoken.data.GameBoardStore
import com.etoken.data.MoxfieldRepository
import com.etoken.domain.BoardRules
import com.etoken.domain.model.BoardEntry
import com.etoken.domain.model.GameBoard
import com.etoken.domain.model.TokenCard
import com.etoken.ui.common.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One entry on the battlefield, together with the card it is copies of. */
data class EntryOnBoard(val entry: BoardEntry, val token: TokenCard)

sealed interface BoardUiState {
    data object Loading : BoardUiState
    data class Failed(val error: LoadError) : BoardUiState

    data class Ready(
        /** What the deck can create, which is what the picker offers. */
        val deckTokens: List<TokenCard>,
        /** What is on the table, newest last, each with its art and its rules. */
        val entries: List<EntryOnBoard>,
        /** Kept whole for the picker's per-token count. */
        val board: GameBoard,
        val canUndo: Boolean,
    ) : BoardUiState {
        val isEmpty: Boolean get() = entries.isEmpty()
        val total: Int get() = board.total
    }
}

/**
 * The battlefield for one deck's session: what the deck can make, what is on
 * the table, and every edit either of them supports.
 *
 * One view model for both halves of the screen, because they are one screen —
 * the picker adds to the same board the grid draws, and on a tablet the two are
 * panes side by side. Splitting them was what the old token-grid/board pair
 * did, and it cost a second view model whose only job was to read the same
 * store.
 */
class BoardViewModel(
    private val repository: MoxfieldRepository,
    private val boards: GameBoardStore,
    private val publicId: String,
    val deckName: String,
) : ViewModel() {

    private val deckTokens = MutableStateFlow<List<TokenCard>>(emptyList())
    private val phase = MutableStateFlow<Phase>(Phase.Loading)

    private var loadJob: Job? = null

    val state: StateFlow<BoardUiState> =
        combine(
            phase,
            deckTokens,
            boards.board,
            boards.tokens,
            boards.canUndo,
        ) { current, tokens, board, known, canUndo ->
            when (current) {
                is Phase.Failed -> BoardUiState.Failed(current.error)
                Phase.Loading -> BoardUiState.Loading
                Phase.Ready -> BoardUiState.Ready(
                    deckTokens = tokens,
                    // An entry whose token is not in the catalog cannot be
                    // drawn, and cannot happen either: adding is what puts a
                    // token there. Dropped rather than drawn blank, so a bug
                    // here is a missing cell instead of an empty frame.
                    entries = board.entries.mapNotNull { entry ->
                        known[entry.tokenId]?.let { EntryOnBoard(entry, it) }
                    },
                    board = board,
                    canUndo = canUndo,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState.Loading)

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            phase.value = Phase.Loading
            try {
                deckTokens.value = repository.tokensFor(publicId)
                phase.value = Phase.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase.value = Phase.Failed(LoadError.from(e))
            }
        }
    }

    /**
     * Puts a new entry of [quantity] copies of [token] onto the battlefield.
     *
     * A token printed with haste enters able to attack, so nobody has to clear
     * summoning sickness by hand every single time one lands. Haste given by
     * another permanent is table state the app cannot see, and stays what the
     * entry's chip is for.
     *
     * Only creatures can be summoning sick at all — a Treasure or a Clue is
     * usable the instant it exists — so a non-creature token never arrives
     * sick regardless of [TokenCard.hasHaste].
     *
     * [tapped] is the player's call at the moment of adding: some effects
     * create tokens tapped, and it is easier to say so up front than to flip
     * the entry's chip right after.
     */
    fun add(token: TokenCard, quantity: Int, copying: String? = null, tapped: Boolean = false) {
        // Before the edit, so the entry the board gains always has art to draw.
        boards.remember(token)
        boards.update {
            BoardRules.add(
                it,
                tokenId = token.id,
                quantity = quantity,
                copying = copying,
                entersSick = token.isCreature && !token.hasHaste,
                entersTapped = tapped,
            )
        }
    }

    fun changeQuantity(entryId: Long, delta: Int) =
        boards.update { BoardRules.changeQuantity(it, entryId, delta) }

    fun changeCounters(entryId: Long, delta: Int, appliesTo: Int? = null) =
        boards.update { BoardRules.addCounters(it, entryId, delta, appliesTo) }

    fun addCounterToAll() = boards.update { BoardRules.addCountersToAll(it, 1) }

    fun setSummoningSick(entryId: Long, sick: Boolean) =
        boards.update { BoardRules.setSummoningSick(it, entryId, sick) }

    /**
     * Turns [appliesTo] copies of an entry, or all of them when it is null.
     *
     * Fewer than all splits the entry, which is the point: tapping three of six
     * Goblins for mana leaves three that can still attack, and they are not the
     * same three.
     */
    fun setTapped(entryId: Long, tapped: Boolean, appliesTo: Int? = null) =
        boards.update { BoardRules.setTapped(it, entryId, tapped, appliesTo) }

    fun remove(entryId: Long) = boards.update { BoardRules.remove(it, entryId) }

    fun beginTurn() = boards.update { BoardRules.beginTurn(it) }

    /**
     * Empties the table, this deck's tokens and any other deck's alike.
     *
     * The board belongs to the game, not to the deck on screen, so leaving
     * another deck's leftovers behind would show stale counts the moment you
     * switched back. Undoable like any other change.
     */
    fun newGame() = boards.newGame()

    fun undo() = boards.undo()

    private sealed interface Phase {
        data object Loading : Phase
        data object Ready : Phase
        data class Failed(val error: LoadError) : Phase
    }

    companion object {
        const val ARG_PUBLIC_ID = "publicId"
        const val ARG_DECK_NAME = "deckName"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as EtokenApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                BoardViewModel(
                    repository = app.container.repository,
                    boards = app.container.gameBoard,
                    publicId = checkNotNull(handle.get<String>(ARG_PUBLIC_ID)) { "missing $ARG_PUBLIC_ID" },
                    deckName = handle.get<String>(ARG_DECK_NAME).orEmpty(),
                )
            }
        }

        /** For tests and previews, which have no navigation arguments to read. */
        fun factoryFor(publicId: String, deckName: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as EtokenApplication
                    BoardViewModel(
                        repository = app.container.repository,
                        boards = app.container.gameBoard,
                        publicId = publicId,
                        deckName = deckName,
                    )
                }
            }
    }
}
