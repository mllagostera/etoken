package com.etoken.domain

import com.etoken.domain.model.BoardEntry
import com.etoken.domain.model.GameBoard

/**
 * Every edit the battlefield supports, as pure functions over [GameBoard].
 *
 * One invariant holds after every operation, enforced in one place by
 * [normalize]: an entry that has emptied disappears. Entries that look alike
 * are deliberately left alone — each press of "add" is a thing that happened at
 * the table, and merging two of them because their state happens to coincide
 * loses that. (An earlier version of this file did merge them, which is why the
 * absence is worth stating.)
 *
 * Order is the order entries were made, and nothing here re-sorts: an entry
 * that jumped to the top of the grid because you put a counter on it would be
 * an entry you then have to hunt for.
 */
object BoardRules {

    /**
     * Puts a new entry of [quantity] copies onto the battlefield.
     *
     * Always a *new* entry, even when an identical one is already out. That is
     * the point: "make three Goblins" twice is two things that happened, and
     * the second one lands where the eye expects it, at the end.
     *
     * [entersSick] is what the caller knows about the token itself: copies
     * arrive summoning sick, which is what actually happens, unless the token
     * is printed with haste and can attack the turn it enters — or is not a
     * creature at all, since only creatures can be summoning sick. It is a
     * parameter rather than an assumption because the rule lives on the token,
     * and only the caller has it.
     *
     * Haste given at the table by another permanent is not in it: nothing here
     * can see the rest of the battlefield, so that stays the entry chip's job —
     * as does correcting a miscount.
     *
     * [entersTapped] is likewise the caller's to say: some tokens are made
     * tapped by the effect that creates them, and some players enter their
     * tokens tapped as a house rule for pre-tapped mana rocks and the like.
     */
    fun add(
        board: GameBoard,
        tokenId: String,
        quantity: Int,
        copying: String? = null,
        entersSick: Boolean = true,
        entersTapped: Boolean = false,
    ): GameBoard {
        if (quantity <= 0) return board

        val entering = BoardEntry(
            id = board.nextEntryId,
            tokenId = tokenId,
            quantity = quantity,
            plusOneCounters = 0,
            summoningSick = entersSick,
            tapped = entersTapped,
            copying = copying?.trim()?.takeIf { it.isNotEmpty() },
        )
        return normalize(
            board.copy(entries = board.entries + entering, nextEntryId = board.nextEntryId + 1),
        )
    }

    /**
     * Adjusts one entry's count, for a miscount or for copies that died.
     *
     * New copies join that entry rather than starting one, which is the
     * difference from [add]: this is correcting something already on the table.
     */
    fun changeQuantity(board: GameBoard, entryId: Long, delta: Int): GameBoard {
        val entries = board.entries.map { entry ->
            if (entry.id == entryId) {
                entry.copy(quantity = (entry.quantity + delta).coerceAtLeast(0))
            } else {
                entry
            }
        }
        return normalize(board.copy(entries = entries))
    }

    /** Takes an entry off the battlefield outright, however many copies it holds. */
    fun remove(board: GameBoard, entryId: Long): GameBoard =
        normalize(board.copy(entries = board.entries.filterNot { it.id == entryId }))

    /**
     * Adds (or removes) +1/+1 counters.
     *
     * [appliesTo] is the whole entry by default; passing fewer splits it so the
     * counter lands on just those copies. That split is the only way to express
     * "three of the seven got a counter", and doing it as part of the same call
     * means the board never sits in a pointless half-split state.
     */
    fun addCounters(
        board: GameBoard,
        entryId: Long,
        delta: Int,
        appliesTo: Int? = null,
    ): GameBoard = modify(board, entryId, appliesTo) { entry ->
        entry.copy(plusOneCounters = (entry.plusOneCounters + delta).coerceAtLeast(0))
    }

    /**
     * The Cathars' Crusade case: everything on the battlefield grows at once.
     *
     * Everything means every entry of every token, since the board is now the
     * whole table rather than one token's corner of it.
     */
    fun addCountersToAll(board: GameBoard, delta: Int): GameBoard = normalize(
        board.copy(
            entries = board.entries.map { entry ->
                entry.copy(plusOneCounters = (entry.plusOneCounters + delta).coerceAtLeast(0))
            },
        ),
    )

    fun setSummoningSick(
        board: GameBoard,
        entryId: Long,
        sick: Boolean,
        appliesTo: Int? = null,
    ): GameBoard = modify(board, entryId, appliesTo) { entry ->
        entry.copy(summoningSick = sick)
    }

    fun setTapped(
        board: GameBoard,
        entryId: Long,
        tapped: Boolean,
        appliesTo: Int? = null,
    ): GameBoard = modify(board, entryId, appliesTo) { entry ->
        entry.copy(tapped = tapped)
    }

    /** Your untap step: everything that was waiting can now attack, and everything tapped untaps. */
    fun beginTurn(board: GameBoard): GameBoard = normalize(
        board.copy(entries = board.entries.map { it.copy(summoningSick = false, tapped = false) }),
    )

    /** Board wipe, or simply a new game. Entry ids keep counting up. */
    fun clear(board: GameBoard): GameBoard = board.copy(entries = emptyList())

    /**
     * Applies [transform] to [appliesTo] copies of one entry, peeling them off
     * into an entry of their own when that is fewer than all of them.
     *
     * The peeled-off copies stay next to where they came from rather than going
     * to the end of the board: they are the same permanents, only in a
     * different state now.
     */
    private fun modify(
        board: GameBoard,
        entryId: Long,
        appliesTo: Int?,
        transform: (BoardEntry) -> BoardEntry,
    ): GameBoard {
        val target = board.entries.firstOrNull { it.id == entryId } ?: return board

        val affected = (appliesTo ?: target.quantity).coerceIn(0, target.quantity)
        if (affected == 0) return board

        val untouched = target.quantity - affected
        val splitting = untouched > 0

        val changed = transform(
            target.copy(
                quantity = affected,
                // Only mint a new id when the entry actually splits, so a plain
                // edit keeps its identity and its place in the grid.
                id = if (splitting) board.nextEntryId else target.id,
            ),
        )

        val entries = board.entries.flatMap { entry ->
            when {
                entry.id != entryId -> listOf(entry)
                splitting -> listOf(entry.copy(quantity = untouched), changed)
                else -> listOf(changed)
            }
        }

        return normalize(
            board.copy(
                entries = entries,
                nextEntryId = if (splitting) board.nextEntryId + 1 else board.nextEntryId,
            ),
        )
    }

    /**
     * Drops the entries that have emptied, and leaves everything else exactly
     * where it was.
     *
     * That is the whole of it — no merging, no re-ordering. It stays a function
     * rather than a `filter` at each call site so there is one place to look
     * for what "a valid board" means.
     */
    private fun normalize(board: GameBoard): GameBoard =
        board.copy(entries = board.entries.filter { it.quantity > 0 })
}
