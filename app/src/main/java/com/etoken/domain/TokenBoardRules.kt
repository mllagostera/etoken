package com.etoken.domain

import com.etoken.domain.model.TokenBoard
import com.etoken.domain.model.TokenStack

/**
 * Every edit the token board supports, as pure functions over [TokenBoard].
 *
 * Two invariants hold after every operation, enforced in one place by
 * [normalize]: stacks that have become identical are merged back together, and
 * an empty stack disappears. Without them the board silently accumulates
 * duplicate rows — put a counter on four Goblins and take it off again, and you
 * would be left with two stacks of four that look the same but aren't.
 */
object TokenBoardRules {

    /**
     * Puts [quantity] new copies onto the battlefield, with no counters.
     *
     * [entersSick] is what the caller knows about the token itself: copies
     * arrive summoning sick, which is what actually happens, unless the token
     * is printed with haste and can attack the turn it enters. It is a
     * parameter rather than an assumption because the rule lives on the token,
     * and only the caller has it.
     *
     * Haste given at the table by another permanent is not in it: nothing here
     * can see the rest of the battlefield, so that stays the per-stack chip's
     * job — as does correcting a miscount.
     */
    fun add(
        board: TokenBoard,
        quantity: Int,
        copying: String? = null,
        entersSick: Boolean = true,
    ): TokenBoard {
        if (quantity <= 0) return board

        val entering = TokenStack(
            id = board.nextStackId,
            quantity = quantity,
            plusOneCounters = 0,
            summoningSick = entersSick,
            copying = copying?.trim()?.takeIf { it.isNotEmpty() },
        )
        return normalize(
            board.copy(stacks = board.stacks + entering, nextStackId = board.nextStackId + 1),
        )
    }

    /** Adjusts one stack's count. New copies inherit that stack's state. */
    fun changeQuantity(board: TokenBoard, stackId: Long, delta: Int): TokenBoard {
        val stacks = board.stacks.map { stack ->
            if (stack.id == stackId) {
                stack.copy(quantity = (stack.quantity + delta).coerceAtLeast(0))
            } else {
                stack
            }
        }
        return normalize(board.copy(stacks = stacks))
    }

    /**
     * Adds (or removes) +1/+1 counters.
     *
     * [appliesTo] is the whole stack by default; passing fewer splits the stack
     * so the counter lands on just those copies. That split is the only way to
     * express "three of the seven got a counter", and doing it as part of the
     * same call means the board never sits in a pointless half-split state.
     */
    fun addCounters(
        board: TokenBoard,
        stackId: Long,
        delta: Int,
        appliesTo: Int? = null,
    ): TokenBoard = modify(board, stackId, appliesTo) { stack ->
        stack.copy(plusOneCounters = (stack.plusOneCounters + delta).coerceAtLeast(0))
    }

    /** The Cathars' Crusade case: everything on the battlefield grows at once. */
    fun addCountersToAll(board: TokenBoard, delta: Int): TokenBoard = normalize(
        board.copy(
            stacks = board.stacks.map { stack ->
                stack.copy(plusOneCounters = (stack.plusOneCounters + delta).coerceAtLeast(0))
            },
        ),
    )

    fun setSummoningSick(
        board: TokenBoard,
        stackId: Long,
        sick: Boolean,
        appliesTo: Int? = null,
    ): TokenBoard = modify(board, stackId, appliesTo) { stack ->
        stack.copy(summoningSick = sick)
    }

    /** Your untap step: everything that was waiting can now attack and tap. */
    fun beginTurn(board: TokenBoard): TokenBoard = normalize(
        board.copy(stacks = board.stacks.map { it.copy(summoningSick = false) }),
    )

    /** Board wipe, or simply a new game. Stack ids keep counting up. */
    fun clear(board: TokenBoard): TokenBoard = board.copy(stacks = emptyList())

    /**
     * Applies [transform] to [appliesTo] copies of one stack, peeling them off
     * into a stack of their own when that is fewer than all of them.
     */
    private fun modify(
        board: TokenBoard,
        stackId: Long,
        appliesTo: Int?,
        transform: (TokenStack) -> TokenStack,
    ): TokenBoard {
        val target = board.stacks.firstOrNull { it.id == stackId } ?: return board

        val affected = (appliesTo ?: target.quantity).coerceIn(0, target.quantity)
        if (affected == 0) return board

        val untouched = target.quantity - affected
        val splitting = untouched > 0

        val changed = transform(
            target.copy(
                quantity = affected,
                // Only mint a new id when the stack actually splits, so a plain
                // edit keeps its identity and its place in the list.
                id = if (splitting) board.nextStackId else target.id,
            ),
        )

        val stacks = board.stacks.flatMap { stack ->
            when {
                stack.id != stackId -> listOf(stack)
                splitting -> listOf(stack.copy(quantity = untouched), changed)
                else -> listOf(changed)
            }
        }

        return normalize(
            board.copy(
                stacks = stacks,
                nextStackId = if (splitting) board.nextStackId + 1 else board.nextStackId,
            ),
        )
    }

    /**
     * Drops empty stacks, merges the ones that are now indistinguishable, and
     * orders what's left: usable before summoning-sick, and bigger creatures
     * first, so the row you most likely want to touch is at the top.
     */
    private fun normalize(board: TokenBoard): TokenBoard {
        val merged = LinkedHashMap<Triple<Int, Boolean, String?>, TokenStack>()

        for (stack in board.stacks) {
            if (stack.quantity <= 0) continue

            // What this copy copies is part of the signature: without it a copy
            // of Krenko and a copy of Atraxa would merge into one row, which is
            // the exact confusion stacks exist to prevent.
            val signature = Triple(stack.plusOneCounters, stack.summoningSick, stack.copying)
            val existing = merged[signature]

            merged[signature] = if (existing == null) {
                stack
            } else {
                existing.copy(
                    // The older id wins, so a merge doesn't look like a new row.
                    id = minOf(existing.id, stack.id),
                    quantity = existing.quantity + stack.quantity,
                )
            }
        }

        val ordered = merged.values.sortedWith(
            compareBy({ it.summoningSick }, { -it.plusOneCounters }, { it.id }),
        )
        return board.copy(stacks = ordered)
    }
}
