package com.etoken.data

import com.etoken.domain.model.TokenBoard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * What is on the battlefield right now, per token.
 *
 * In-memory on purpose. The board belongs to the game being played, not to the
 * deck, so it survives navigating between tokens and decks but goes away with
 * the process — there is nothing worth restoring three days later, and a stale
 * board restored mid-game would be worse than an empty one.
 */
class TokenBoardStore {

    private val boards = MutableStateFlow<Map<String, TokenBoard>>(emptyMap())

    /** Every board at once, which is what the token grid's badges read. */
    val all: Flow<Map<String, TokenBoard>> = boards.asStateFlow()

    fun board(tokenId: String): Flow<TokenBoard> =
        boards.map { it[tokenId] ?: TokenBoard() }

    fun update(tokenId: String, transform: (TokenBoard) -> TokenBoard) {
        boards.update { current ->
            val next = transform(current[tokenId] ?: TokenBoard())
            current + (tokenId to next)
        }
    }

    /** New game: everything leaves the battlefield. */
    fun clearAll() {
        boards.value = emptyMap()
    }
}
