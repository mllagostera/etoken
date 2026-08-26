package com.etoken.ui.tokens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.etoken.EtokenApplication
import com.etoken.data.MoxfieldRepository
import com.etoken.data.TokenBoardStore
import com.etoken.domain.TokenFilter
import com.etoken.domain.model.TokenCard
import com.etoken.ui.common.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TokensUiState {
    data object Loading : TokensUiState
    data object Empty : TokensUiState
    data class Failed(val error: LoadError) : TokensUiState

    data class Ready(
        /** What the grid shows: the deck's tokens, less whatever the filter hides. */
        val tokens: List<TokenCard>,
        /** How many the deck creates in all, for the "3 de 12" counter. */
        val total: Int,
        /** Whether the quick filter — only what is on the battlefield — is on. */
        val onlyInPlay: Boolean,
    ) : TokensUiState
}

class TokensViewModel(
    private val repository: MoxfieldRepository,
    private val boards: TokenBoardStore,
    private val savedState: SavedStateHandle,
    private val publicId: String,
    val deckName: String,
) : ViewModel() {

    private val tokens = MutableStateFlow<List<TokenCard>>(emptyList())
    private val phase = MutableStateFlow<Phase>(Phase.Loading)

    private var loadJob: Job? = null

    /** Token id -> how many are on the battlefield, for the grid's badges. */
    val inPlay: StateFlow<Map<String, Int>> = boards.all
        .map { all -> all.mapValues { (_, board) -> board.total }.filterValues { it > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val canUndo: StateFlow<Boolean> = boards.canUndo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // In the SavedStateHandle rather than a plain flow, for the same reason the
    // deck search is: coming back from a killed process to a grid that had
    // quietly un-filtered itself reads as the app having lost your place.
    private val onlyInPlay: StateFlow<Boolean> = savedState.getStateFlow(KEY_ONLY_IN_PLAY, false)

    /**
     * Derived rather than assigned, so that putting a token on the battlefield
     * re-runs the filter on its own. The board moves from four other screens'
     * worth of taps; nothing here could remember to re-filter at each of them.
     */
    val state: StateFlow<TokensUiState> =
        combine(phase, tokens, inPlay, onlyInPlay) { current, all, board, only ->
            when {
                current is Phase.Failed -> TokensUiState.Failed(current.error)
                current is Phase.Loading -> TokensUiState.Loading
                all.isEmpty() -> TokensUiState.Empty
                else -> TokensUiState.Ready(
                    tokens = TokenFilter.apply(all, board, only),
                    total = all.size,
                    onlyInPlay = only,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TokensUiState.Loading)

    init {
        load()
    }

    /**
     * Clears every board, not just this deck's.
     *
     * Token ids are Scryfall ids, so the same Goblin is the same entry whatever
     * deck brought it. A new game empties the table, and leaving another deck's
     * leftovers behind would show stale counts the moment you switched.
     */
    fun startNewGame() {
        boards.clearAll()
    }

    /** Puts back the last change, which right after a new game is the table. */
    fun undo() {
        boards.undo()
    }

    /**
     * Turns the "only what is in play" filter on or off.
     *
     * Left alone by [startNewGame] and by [undo] on purpose: the filter is the
     * user's, and an emptied table is a thing the grid says out loud rather
     * than something to silently paper over by dropping their filter.
     */
    fun toggleOnlyInPlay() {
        savedState[KEY_ONLY_IN_PLAY] = !onlyInPlay.value
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            phase.value = Phase.Loading
            try {
                tokens.value = repository.tokensFor(publicId)
                phase.value = Phase.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase.value = Phase.Failed(LoadError.from(e))
            }
        }
    }

    private sealed interface Phase {
        data object Loading : Phase
        data object Ready : Phase
        data class Failed(val error: LoadError) : Phase
    }

    companion object {
        const val ARG_PUBLIC_ID = "publicId"
        const val ARG_DECK_NAME = "deckName"

        private const val KEY_ONLY_IN_PLAY = "onlyInPlay"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as EtokenApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                TokensViewModel(
                    repository = app.container.repository,
                    boards = app.container.tokenBoards,
                    savedState = handle,
                    publicId = checkNotNull(handle.get<String>(ARG_PUBLIC_ID)) { "missing $ARG_PUBLIC_ID" },
                    deckName = handle.get<String>(ARG_DECK_NAME).orEmpty(),
                )
            }
        }
    }
}
