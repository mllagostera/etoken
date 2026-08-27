package com.etoken.domain.model

/**
 * Copies of one token that entered the battlefield together and are still in
 * the same state.
 *
 * An entry is created by one press of "add" and stays its own thing for the
 * rest of the game: two entries of three Goblins each are two entries, never a
 * merged six. That is the whole difference from the stacks this replaced —
 * those fused whenever they became indistinguishable, which is right for a
 * count and wrong for cards on a table. What survives from stacks is
 * [quantity]: "make three Goblins" is one thing that happened, and splitting it
 * into three rows would be as much of a lie as merging it into somebody else's.
 *
 * Counters and summoning sickness stay per entry because Magic tracks them per
 * permanent; [splitting][com.etoken.domain.BoardRules.addCounters] an entry is
 * how "three of these seven got a counter" is said.
 */
data class BoardEntry(
    /** Stable across edits, so the grid doesn't re-animate on every tap. */
    val id: Long,
    /** Scryfall id of the token this is copies of. The board mixes types now. */
    val tokenId: String,
    val quantity: Int,
    val plusOneCounters: Int = 0,
    val summoningSick: Boolean = true,
    /**
     * Some tokens are made tapped — Krenko's Goblins with an added "tap" clause,
     * a Treasure fetched by a spell that says so. Independent of
     * [summoningSick]: a tapped token can still swing once it is no longer
     * sick, and a non-creature token can be tapped despite never being sick at
     * all.
     */
    val tapped: Boolean = false,
    /**
     * What this copy is a copy of, for tokens named "Copy"; null for every
     * other token.
     *
     * On the entry rather than on the token because two copies of different
     * creatures are two different things on the battlefield, however identical
     * their token card is.
     */
    val copying: String? = null,
)

/**
 * The whole battlefield for the game being played — every token type at once,
 * in the order the entries were made.
 *
 * One board rather than one per token: the player adds from a deck-wide picker
 * and looks at the result as a table, so "what is out" is a single list. It is
 * deliberately in-memory only; this is a play aid for the table, and it is
 * reset by starting a new game rather than by anything persisted.
 */
data class GameBoard(
    val entries: List<BoardEntry> = emptyList(),
    /** Kept in the state so entry ids stay pure — no global counter. */
    val nextEntryId: Long = 1,
) {
    val total: Int get() = entries.sumOf { it.quantity }

    val summoningSickCount: Int get() =
        entries.filter { it.summoningSick }.sumOf { it.quantity }

    val isEmpty: Boolean get() = entries.isEmpty()

    /** How many copies of one token are in play, for the picker's badge. */
    fun countOf(tokenId: String): Int =
        entries.filter { it.tokenId == tokenId }.sumOf { it.quantity }

    /**
     * The same figure as [summoningSickCount], told in the three cases a badge
     * can draw. Derived here rather than in the grid so every screen reads one
     * answer, and so the rule is unit-tested off the JVM like every other.
     */
    val summoningSickness: SummoningSickness get() = when (val sick = summoningSickCount) {
        // First, so an empty board answers None rather than All.
        0 -> SummoningSickness.None
        total -> SummoningSickness.All
        else -> SummoningSickness.Some(sick)
    }
}

/**
 * How much of what is in play is still summoning sick, in the shape a badge
 * can draw without opening an entry.
 *
 * A single number would not do it: "2" says nothing about whether the other
 * five can attack, and on a board where every copy is waiting the number is
 * only the total said twice. The three cases are what a player actually asks
 * mid-turn — nothing is waiting, everything is, or these many of them are.
 */
sealed interface SummoningSickness {
    /** Everything in play can attack. An empty battlefield answers this too. */
    data object None : SummoningSickness

    /** Every copy is waiting, so the count is the one already on the cell. */
    data object All : SummoningSickness

    /** Part of the table is waiting, and only a number can say how much. */
    data class Some(val count: Int) : SummoningSickness
}
