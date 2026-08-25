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
import com.etoken.domain.model.TokenCard
import com.etoken.ui.common.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TokensUiState {
    data object Loading : TokensUiState
    data object Empty : TokensUiState
    data class Failed(val error: LoadError) : TokensUiState
    data class Ready(val tokens: List<TokenCard>) : TokensUiState
}

class TokensViewModel(
    private val repository: MoxfieldRepository,
    private val boards: TokenBoardStore,
    private val publicId: String,
    val deckName: String,
) : ViewModel() {

    private val _state = MutableStateFlow<TokensUiState>(TokensUiState.Loading)
    val state: StateFlow<TokensUiState> = _state.asStateFlow()

    /** Token id -> how many are on the battlefield, for the grid's badges. */
    val inPlay: StateFlow<Map<String, Int>> = boards.all
        .map { all -> all.mapValues { (_, board) -> board.total }.filterValues { it > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    fun load() {
        viewModelScope.launch {
            _state.value = TokensUiState.Loading
            try {
                val tokens = repository.tokensFor(publicId)
                _state.value = if (tokens.isEmpty()) {
                    TokensUiState.Empty
                } else {
                    TokensUiState.Ready(tokens)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = TokensUiState.Failed(LoadError.from(e))
            }
        }
    }

    companion object {
        const val ARG_PUBLIC_ID = "publicId"
        const val ARG_DECK_NAME = "deckName"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as EtokenApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                TokensViewModel(
                    repository = app.container.repository,
                    boards = app.container.tokenBoards,
                    publicId = checkNotNull(handle.get<String>(ARG_PUBLIC_ID)) { "missing $ARG_PUBLIC_ID" },
                    deckName = handle.get<String>(ARG_DECK_NAME).orEmpty(),
                )
            }
        }
    }
}
