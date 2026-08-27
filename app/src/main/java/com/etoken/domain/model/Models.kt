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
    /** Printed power/toughness. Strings because Magic uses `*` and `1+*`. */
    val power: String? = null,
    val toughness: String? = null,
    /**
     * Whether the token is *printed* with haste, out of Scryfall's `keywords`.
     *
     * Printed haste is the only half of it the app can ever know: copies of
     * this token enter able to attack. Haste handed out at the table by
     * another permanent — Goblin Chieftain, Anger — is state nothing here
     * can see, and stays the per-stack chip's job.
     */
    val hasHaste: Boolean = false,
) {
    /**
     * Whether this is a token that copies something else.
     *
     * Scryfall names these exactly "Copy", which is the whole rule — and it is
     * a card name, so it stays English however the app is localised. It lives
     * here rather than as a literal at the call sites so there is one place to
     * change if Scryfall ever names them differently.
     */
    val isCopy: Boolean get() = name.trim().equals(COPY_TOKEN_NAME, ignoreCase = true)

    /**
     * Whether this token is a creature at all.
     *
     * Only creatures get summoning sickness — a Treasure or a Clue can never be
     * "sick" no matter when it entered. Read off the type line rather than a
     * separate flag because Scryfall already states it there for every token,
     * printed or emblem alike.
     */
    val isCreature: Boolean get() = typeLine.contains("Creature", ignoreCase = true)

    companion object {
        const val COPY_TOKEN_NAME = "Copy"
    }
}
