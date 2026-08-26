package com.etoken.domain

import com.etoken.domain.model.DeckSummary
import java.text.Normalizer

/**
 * Narrows the deck grid by what the user types.
 *
 * Pure and Android-free so the matching rules are unit-testable, which matters
 * because they are less obvious than they look: the filter is accent-blind
 * (typing "canon" finds "Cañón"), it searches the commander as well as the
 * deck name, and a multi-word query has to match on every word rather than as
 * one literal string.
 */
object DeckFilter {

    fun apply(decks: List<DeckSummary>, query: String): List<DeckSummary> {
        val terms = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return decks

        return decks.filter { deck ->
            // Searching the commander too is the point: people remember the
            // general they play, not what they called the deck three years ago.
            val haystack = normalize("${deck.name} ${deck.commander.orEmpty()}")
            terms.all { term -> haystack.contains(term) }
        }
    }

    /**
     * Lower-cases and strips diacritics, so "Ñ", "ñ" and "n" all match.
     *
     * NFD splits an accented letter into its base letter plus a combining
     * mark; dropping the marks leaves the base letters behind.
     */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(WHITESPACE, " ")
            .trim()

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
}
