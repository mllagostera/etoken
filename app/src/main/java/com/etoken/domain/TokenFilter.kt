package com.etoken.domain

import com.etoken.domain.model.TokenBoard
import com.etoken.domain.model.TokenCard

/**
 * Narrows the token grid to the ones that have copies on the battlefield.
 *
 * Deliberately reads the same boards the grid's badges are drawn from, so
 * "shown by the filter" and "carries a badge" can never disagree: one token
 * type is active when at least one copy of it is in play, whatever deck put it
 * there.
 *
 * Pure and Android-free, like [DeckFilter] — the rule is small, but it is the
 * one place that decides what "active" means, and it is worth being able to
 * test it without a device.
 */
object TokenFilter {

    /**
     * @param inPlay token id -> its board, as [com.etoken.data.TokenBoardStore]
     *   reports it. A missing entry and an empty board are the same thing here:
     *   a board that empties leaves the map, but nothing about the rule depends
     *   on that.
     */
    fun apply(
        tokens: List<TokenCard>,
        inPlay: Map<String, TokenBoard>,
        onlyInPlay: Boolean,
    ): List<TokenCard> {
        if (!onlyInPlay) return tokens
        return tokens.filter { token -> (inPlay[token.id]?.total ?: 0) > 0 }
    }
}
