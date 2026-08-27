package com.etoken.domain

/**
 * Whose decks the grid is listing.
 *
 * Moxfield has no "decks by user" endpoint and no "precons" endpoint either:
 * both are the same deck-*search* call with different filters, which is how
 * the site itself does it. A user's decks filter by author; the preconstructed
 * decks filter by author *and* by format, because WizardsOfTheCoast publishes
 * far more than Commander precons under that account.
 *
 * Pure data, so the route, the repository and the title all read the same
 * answer instead of each spelling the filter out again.
 */
data class DeckSource(
    val username: String,
    /** Moxfield's `fmt` search filter. Null means "every format", the usual case. */
    val format: String? = null,
) {

    /**
     * Whether this is the preconstructed-deck listing, which the deck screen
     * titles by name rather than by author: "WizardsOfTheCoast" is an
     * implementation detail of where precons live, not something a user typed.
     */
    val isPrecons: Boolean get() = format == PRECON_FORMAT

    companion object {
        /** The account Wizards publishes its preconstructed decks under. */
        const val PRECON_AUTHOR = "WizardsOfTheCoast"

        /** Moxfield's own name for the format, as its search endpoint spells it. */
        const val PRECON_FORMAT = "commanderPrecons"

        val PRECONS = DeckSource(PRECON_AUTHOR, PRECON_FORMAT)

        /**
         * A source out of the two route arguments, which arrive as free text:
         * a blank format is no filter at all, and the username is trimmed here
         * so the network layer never has to wonder.
         */
        fun of(username: String, format: String?) =
            DeckSource(username.trim(), format?.takeIf { it.isNotBlank() })
    }
}
