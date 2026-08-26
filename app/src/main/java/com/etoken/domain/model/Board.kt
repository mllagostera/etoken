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

    val isEmpty: Boolean get() = stacks.isEmpty()
}
