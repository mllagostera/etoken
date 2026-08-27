package com.etoken.data

import com.etoken.data.moxfield.MoxfieldApi
import com.etoken.data.moxfield.MoxfieldImages
import com.etoken.data.scryfall.CollectionRequest
import com.etoken.data.scryfall.Identifier
import com.etoken.data.scryfall.ScryfallApi
import com.etoken.data.scryfall.ScryfallCard
import com.etoken.domain.DeckSource
import com.etoken.domain.TokenExtractor
import com.etoken.domain.model.DeckDetail
import com.etoken.domain.model.DeckSummary
import com.etoken.domain.model.TokenCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MoxfieldRepository(
    private val moxfield: MoxfieldApi,
    private val scryfall: ScryfallApi,
) {

    private val cacheMutex = Mutex()
    private val deckCache = mutableMapOf<String, DeckDetail>()
    private val tokenCache = mutableMapOf<String, List<TokenCard>>()

    /**
     * Every public deck the [source] names, newest update first.
     *
     * Moxfield exposes no "decks of user X" endpoint, so this pages through
     * the deck-search endpoint filtered to one author — the same approach
     * commander-companion's backend uses. The preconstructed decks are that
     * same search with a format filter on top, so they share this path rather
     * than getting one of their own.
     */
    suspend fun listDecks(source: DeckSource): List<DeckSummary> = withContext(Dispatchers.IO) {
        val summaries = mutableListOf<DeckSummary>()

        var page = 1
        while (page <= MAX_PAGES) {
            val response = moxfield.searchDecks(
                username = source.username.trim(),
                pageNumber = page,
                pageSize = MoxfieldApi.PAGE_SIZE,
                format = source.format,
            )

            response.data
                .filter { it.publicId.isNotBlank() }
                .mapTo(summaries) { deck ->
                    DeckSummary(
                        publicId = deck.publicId,
                        name = deck.name,
                        imageUrl = MoxfieldImages.artCrop(deck.main),
                    )
                }

            if (response.data.isEmpty() || page >= response.totalPages) break
            page++
        }

        summaries
    }

    /**
     * Fills in whatever the search endpoint left out.
     *
     * Search is undocumented and its payload has changed before, so the grid
     * never assumes it carried a name or a cover image: anything missing is
     * fetched per deck. When search does return everything this is a no-op and
     * costs no request at all.
     */
    suspend fun hydrate(summary: DeckSummary): DeckSummary {
        // The commander is part of "complete", not an afterthought: the grid
        // prints it under every deck and the search matches on it. Search never
        // returns one, so leaving it out of this check meant that whenever
        // search did supply a name and a cover, the commander stayed null and
        // both of those features quietly did nothing.
        if (summary.name.isNotBlank() && summary.imageUrl != null && summary.commander != null) {
            return summary
        }

        val detail = deckDetail(summary.publicId)
        return summary.copy(
            name = summary.name.ifBlank { detail.name },
            imageUrl = summary.imageUrl ?: detail.imageUrl,
            commander = detail.commander,
        )
    }

    /**
     * Throws away everything cached, so the next load goes back to the network.
     *
     * Both caches go together on purpose: a deck whose contents changed on
     * Moxfield also creates a different set of tokens, and keeping the old
     * token list would be worse than refetching it.
     */
    suspend fun invalidate() {
        cacheMutex.withLock {
            deckCache.clear()
            tokenCache.clear()
        }
    }

    /** Cached: the deck grid and the token screen both need the same payload. */
    suspend fun deckDetail(publicId: String): DeckDetail {
        cacheMutex.withLock { deckCache[publicId] }?.let { return it }

        val detail = withContext(Dispatchers.IO) {
            DeckMapper.toDetail(moxfield.deck(publicId))
        }
        cacheMutex.withLock { deckCache[publicId] = detail }
        return detail
    }

    /**
     * Every token the deck's cards can create.
     *
     * Two Scryfall round trips: the deck's cards (whose `all_parts` name the
     * tokens) and then the tokens themselves (which is where the artwork is).
     * Both are batched at Scryfall's 75-identifier limit.
     */
    suspend fun tokensFor(publicId: String): List<TokenCard> {
        cacheMutex.withLock { tokenCache[publicId] }?.let { return it }

        val tokens = computeTokens(publicId)
        cacheMutex.withLock { tokenCache[publicId] = tokens }
        return tokens
    }

    /** The token the board screen is showing, out of the cached deck lookup. */
    suspend fun token(publicId: String, tokenId: String): TokenCard? =
        tokensFor(publicId).firstOrNull { it.id == tokenId }

    private suspend fun computeTokens(publicId: String): List<TokenCard> = withContext(Dispatchers.IO) {
        val deck = deckDetail(publicId)

        val identifiers = deck.cards
            .map { card ->
                card.scryfallId?.let(Identifier::byId) ?: Identifier.byName(card.name)
            }
            .distinct()

        val deckCards = resolve(identifiers)
        val references = TokenExtractor.tokenReferences(deckCards)
        if (references.isEmpty()) return@withContext emptyList()

        val tokenCards = resolve(references.keys.map(Identifier::byId))
        TokenExtractor.buildTokens(references, tokenCards)
    }

    private suspend fun resolve(identifiers: List<Identifier>): List<ScryfallCard> =
        identifiers
            .chunked(ScryfallApi.MAX_IDENTIFIERS)
            .flatMap { chunk -> scryfall.collection(CollectionRequest(chunk)).data }

    private companion object {
        /** Guard against a pagination bug upstream turning into an endless loop. */
        const val MAX_PAGES = 20
    }
}
