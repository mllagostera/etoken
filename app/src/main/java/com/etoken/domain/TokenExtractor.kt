package com.etoken.domain

import com.etoken.data.scryfall.RelatedCard
import com.etoken.data.scryfall.ScryfallCard
import com.etoken.domain.model.TokenCard

/**
 * Turns "the cards in this deck" into "the tokens this deck can make".
 *
 * Deliberately free of Android and of any I/O: the repository does the two
 * network round trips and hands the results here, which keeps the interesting
 * rules (what counts as a token, how duplicates collapse) unit-testable.
 */
object TokenExtractor {

    /**
     * Maps token id -> names of the deck cards that create it.
     *
     * A related part counts when Scryfall tags it `component == "token"`, or
     * when its type line says Token/Emblem. The second condition is belt and
     * braces: emblems are tagged as tokens today, and this keeps them in even
     * if that ever changes. Meld results and combo pieces are excluded — they
     * are real cards, not tokens.
     *
     * A card is never treated as creating itself: double-faced cards list
     * their own id in `all_parts`, which would otherwise put e.g. a flip
     * creature into its own token list.
     */
    fun tokenReferences(deckCards: List<ScryfallCard>): Map<String, Set<String>> {
        val references = LinkedHashMap<String, MutableSet<String>>()

        for (card in deckCards) {
            for (part in card.allParts) {
                if (part.id.isEmpty() || part.id == card.id) continue
                if (!isToken(part)) continue

                references.getOrPut(part.id) { linkedSetOf() }.add(card.name)
            }
        }
        return references
    }

    private fun isToken(part: RelatedCard): Boolean {
        if (part.component.equals("token", ignoreCase = true)) return true

        val type = part.typeLine
        return type.contains("Token", ignoreCase = true) ||
            type.contains("Emblem", ignoreCase = true)
    }

    /**
     * Joins the resolved token cards back onto their creators.
     *
     * Tokens are collapsed by name + type line rather than by id: `all_parts`
     * points at one specific *printing*, so a deck whose cards come from
     * different sets otherwise shows the same 1/1 white Soldier several times
     * over. The first printing seen wins the artwork, and the creator lists
     * are merged.
     */
    fun buildTokens(
        references: Map<String, Set<String>>,
        tokenCards: List<ScryfallCard>,
    ): List<TokenCard> {
        val byPrinting = tokenCards.associateBy { it.id }
        val collapsed = LinkedHashMap<String, TokenCard>()

        for ((tokenId, creators) in references) {
            val card = byPrinting[tokenId] ?: continue
            val key = dedupeKey(card)
            val existing = collapsed[key]

            collapsed[key] = if (existing == null) {
                TokenCard(
                    id = card.id,
                    name = card.name,
                    typeLine = card.typeLine.orEmpty(),
                    imageUrl = card.imageUrl(),
                    oracleText = card.oracleText,
                    createdBy = creators.toList(),
                    power = card.power,
                    toughness = card.toughness,
                )
            } else {
                existing.copy(
                    // A later printing may be the one that actually has art.
                    imageUrl = existing.imageUrl ?: card.imageUrl(),
                    createdBy = (existing.createdBy + creators).distinct(),
                )
            }
        }

        return collapsed.values
            .map { it.copy(createdBy = it.createdBy.sorted()) }
            .sortedWith(compareBy({ it.typeLine }, { it.name }))
    }

    /**
     * Power/toughness is part of the key: a 1/1 Goblin and a 2/2 Goblin share a
     * name, a type line and an empty rules text, and collapsing them would lose
     * the only thing that tells them apart.
     */
    private fun dedupeKey(card: ScryfallCard): String = listOf(
        card.name,
        card.typeLine.orEmpty(),
        card.oracleText.orEmpty(),
        card.power.orEmpty(),
        card.toughness.orEmpty(),
    ).joinToString("|") { it.lowercase() }
}
