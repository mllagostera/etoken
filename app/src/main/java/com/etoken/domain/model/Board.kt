package com.etoken.domain.model

/**
 * Copies of one token that are currently on the battlefield in the same state.
 *
 * Magic tracks counters and summoning sickness per permanent, not per token
 * type: four Goblins where one carries a +1/+1 counter are not interchangeable.
 * A stack is the coarsest grouping that stays truthful — every copy inside one
 * really is identical — so the board is a list of stacks rather than a single
 * count with counters bolted on.
 */
data class TokenStack(
    /** Stable across edits, so the list doesn't re-animate on every tap. */
    val id: Long,
    val quantity: Int,
    val plusOneCounters: Int,
    val summoningSick: Boolean,
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
     * On the stack rather than on the token because two copies of different
     * creatures are two different things on the battlefield, however identical
     * their token card is.
     */
    val copying: String? = null,
)

/**
 * Everything of one token type that is in play.
 *
 * Deliberately in-memory only: this is a play aid for the table, and it is
 * reset by starting a new game rather than by anything persisted.
 */
data class TokenBoard(
    val stacks: List<TokenStack> = emptyList(),
    /** Kept in the state so stack ids stay pure — no global counter. */
    val nextStackId: Long = 1,
) {
    val total: Int get() = stacks.sumOf { it.quantity }

    val summoningSickCount: Int get() =
        stacks.filter { it.summoningSick }.sumOf { it.quantity }

    /**
     * The +1/+1 counters every copy in play is carrying, or null when the board
     * cannot answer with one number.
     *
     * Only a single stack can: two stacks mean two answers, and a badge that
     * picked one of them would be lying about the other. Zero is a real answer
     * — one stack with no counters — and is not the same as null.
     */
    val uniformPlusOneCounters: Int? get() = stacks.singleOrNull()?.plusOneCounters

    /**
     * The same figure as [summoningSickCount], told in the three cases a badge
     * can draw. Derived here rather than in the grid so both screens read one
     * answer, and so the rule is unit-tested off the JVM like every other.
     */
    val summoningSickness: SummoningSickness get() = when (val sick = summoningSickCount) {
        // First, so an empty board answers None rather than All.
        0 -> SummoningSickness.None
        total -> SummoningSickness.All
        else -> SummoningSickness.Some(sick)
    }

    val isEmpty: Boolean get() = stacks.isEmpty()
}

/**
 * How much of what is in play is still summoning sick, in the shape a badge
 * can draw without opening the board.
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
