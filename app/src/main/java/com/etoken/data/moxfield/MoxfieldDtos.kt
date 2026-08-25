package com.etoken.data.moxfield

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for Moxfield's public (unofficial) API.
 *
 * The endpoint set and the field names below are not guesses: they are ported
 * from the Go client in mllagostera/commander-companion
 * (backend/internal/moxfield/client.go), which was reverse-engineered and
 * confirmed against the live API. Moxfield publishes no API docs, so that
 * client is the source of truth for this app.
 *
 * Every field carries a default so that a shape change upstream degrades into
 * a missing value rather than a parse exception that takes the whole screen
 * down (the Json instance also runs with ignoreUnknownKeys).
 */

/** One page of `/v2/decks/search-sfw`. */
@Serializable
data class SearchResponse(
    val data: List<SearchDeckSummary> = emptyList(),
    val totalPages: Int = 0,
    val pageNumber: Int = 0,
)

/**
 * A deck as it appears in search results.
 *
 * The Go client only ever reads [publicId] from this endpoint and then fetches
 * each deck in full. We map [name] and [main] too, optimistically: when search
 * does return them the grid can render immediately, and when it doesn't the
 * repository falls back to the per-deck fetch. Neither path is load-bearing on
 * the other.
 */
@Serializable
data class SearchDeckSummary(
    val publicId: String = "",
    val name: String = "",
    val format: String? = null,
    val main: MoxfieldCard? = null,
)

/** A deck from `/v3/decks/all/{publicId}`. */
@Serializable
data class DeckResponse(
    val publicId: String = "",
    val name: String = "",
    val format: String? = null,
    /** The card Moxfield highlights as the deck's cover — usually the commander. */
    val main: MoxfieldCard? = null,
    val boards: Boards = Boards(),
)

@Serializable
data class Boards(
    val commanders: Board = Board(),
    val mainboard: Board = Board(),
    val companions: Board = Board(),
    val sideboard: Board = Board(),
)

/** Moxfield keys a board's cards by an opaque id, hence the map rather than a list. */
@Serializable
data class Board(
    val count: Int = 0,
    val cards: Map<String, BoardEntry> = emptyMap(),
)

@Serializable
data class BoardEntry(
    val quantity: Int = 1,
    val card: MoxfieldCard = MoxfieldCard(),
)

@Serializable
data class MoxfieldCard(
    /** Moxfield's own short id, which is what addresses the art on their CDN. */
    val id: String = "",
    val name: String = "",
    /** Scryfall's UUID for this printing. The bridge to everything token-related. */
    @SerialName("scryfall_id") val scryfallId: String? = null,
    @SerialName("type_line") val typeLine: String? = null,
    /** Present only on two-faced cards (transform / modal DFC). */
    @SerialName("card_faces") val cardFaces: List<MoxfieldCard> = emptyList(),
)
