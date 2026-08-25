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

    val isEmpty: Boolean get() = stacks.isEmpty()
}
