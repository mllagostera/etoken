package com.etoken.ui.board

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
import com.etoken.domain.TokenBoardRules
import com.etoken.domain.model.TokenBoard
import com.etoken.domain.model.TokenCard
import com.etoken.ui.common.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TokenBoardUiState {
    data object Loading : TokenBoardUiState
    data class Failed(val error: LoadError) : TokenBoardUiState
    data class Ready(
        val token: TokenCard,
        val board: TokenBoard,
        val canUndo: Boolean,
    ) : TokenBoardUiState
}

class TokenBoardViewModel(
    private val repository: MoxfieldRepository,
    private val boards: TokenBoardStore,
    private val publicId: String,
    private val tokenId: String,
) : ViewModel() {

    private val token = MutableStateFlow<TokenCard?>(null)
    private val failure = MutableStateFlow<LoadError?>(null)

    val state: StateFlow<TokenBoardUiState> =
        combine(
            token,
            boards.board(tokenId),
            failure,
            boards.canUndo,
        ) { card, board, error, canUndo ->
            when {
                error != null -> TokenBoardUiState.Failed(error)
                card != null -> TokenBoardUiState.Ready(card, board, canUndo)
                else -> TokenBoardUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TokenBoardUiState.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            failure.value = null
            try {
                // Normally a cache hit: the token grid resolved this already.
                val card = repository.token(publicId, tokenId)
                if (card == null) failure.value = LoadError.NotFound else token.value = card
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure.value = LoadError.from(e)
            }
        }
    }

    /**
     * A token printed with haste enters able to attack, so nobody has to clear
     * summoning sickness by hand every single time one lands. Haste given by
     * another permanent is table state the app cannot see, and stays what the
     * per-stack chip is for.
     *
     * Only creatures can be summoning sick at all — a Treasure or a Clue is
     * usable the instant it exists — so a non-creature token never arrives
     * sick regardless of [TokenCard.hasHaste].
     *
     * [tapped] is the player's call at the moment of adding: some effects
     * create tokens tapped, and it is easier to say so up front than to flip
     * every stack's chip right after.
     */
    fun add(quantity: Int, copying: String? = null, tapped: Boolean = false) = edit {
        val card = token.value
        TokenBoardRules.add(
            it,
            quantity,
            copying,
            entersSick = card?.isCreature == true && card.hasHaste != true,
            entersTapped = tapped,
        )
    }

    fun changeQuantity(stackId: Long, delta: Int) =
        edit { TokenBoardRules.changeQuantity(it, stackId, delta) }

    fun changeCounters(stackId: Long, delta: Int, appliesTo: Int? = null) =
        edit { TokenBoardRules.addCounters(it, stackId, delta, appliesTo) }

    fun addCounterToAll() = edit { TokenBoardRules.addCountersToAll(it, 1) }

    fun setSummoningSick(stackId: Long, sick: Boolean) =
        edit { TokenBoardRules.setSummoningSick(it, stackId, sick) }

    fun setTapped(stackId: Long, tapped: Boolean) =
        edit { TokenBoardRules.setTapped(it, stackId, tapped) }

    fun beginTurn() = edit { TokenBoardRules.beginTurn(it) }

    fun clear() = edit { TokenBoardRules.clear(it) }

    /**
     * Undoes the last change to any board, not just this one. The trail is
     * shared, so this puts back whatever the player last did, wherever.
     */
    fun undo() = boards.undo()

    private fun edit(transform: (TokenBoard) -> TokenBoard) = boards.update(tokenId, transform)

    companion object {
        const val ARG_PUBLIC_ID = "publicId"
        const val ARG_TOKEN_ID = "tokenId"

        /**
         * For the tablet layout, where the board is a pane rather than a route
         * and so has no navigation arguments of its own to read.
         */
        fun factoryFor(publicId: String, tokenId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as EtokenApplication
                    TokenBoardViewModel(
                        repository = app.container.repository,
                        boards = app.container.tokenBoards,
                        publicId = publicId,
                        tokenId = tokenId,
                    )
                }
            }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as EtokenApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                TokenBoardViewModel(
                    repository = app.container.repository,
                    boards = app.container.tokenBoards,
                    publicId = checkNotNull(handle.get<String>(ARG_PUBLIC_ID)) { "missing $ARG_PUBLIC_ID" },
                    tokenId = checkNotNull(handle.get<String>(ARG_TOKEN_ID)) { "missing $ARG_TOKEN_ID" },
                )
            }
        }
    }
}
