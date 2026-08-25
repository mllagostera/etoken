package com.etoken.domain.model

/** A deck as shown in the grid. */
data class DeckSummary(
    val publicId: String,
    val name: String,
    /** Commander art crop. Null while it is still being resolved, or if Moxfield has none. */
    val imageUrl: String? = null,
    /** Commander name(s), joined with " & " for partner pairs. */
    val commander: String? = null,
)

/** A deck's full contents, which is what the token screen works from. */
data class DeckDetail(
    val publicId: String,
    val name: String,
    val commander: String?,
    val imageUrl: String?,
    val cards: List<DeckCard>,
)

data class DeckCard(
    val name: String,
    /** Null when Moxfield didn't report one; the card is then resolved by name. */
    val scryfallId: String?,
    val quantity: Int,
)

/** A token (or emblem) that some card in the deck is able to create. */
data class TokenCard(
    val id: String,
    val name: String,
    val typeLine: String,
    val imageUrl: String?,
    val oracleText: String?,
    /** Names of the deck's cards that create this token, sorted, deduplicated. */
    val createdBy: List<String>,
)
