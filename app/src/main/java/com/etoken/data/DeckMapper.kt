package com.etoken.data

import com.etoken.data.moxfield.Board
import com.etoken.data.moxfield.DeckResponse
import com.etoken.data.moxfield.MoxfieldImages
import com.etoken.domain.model.DeckCard
import com.etoken.domain.model.DeckDetail

/** Flattens Moxfield's board-keyed deck JSON into the app's own model. */
object DeckMapper {

    fun toDetail(response: DeckResponse): DeckDetail {
        val boards = response.boards

        // Sideboard and maybeboard are left out on purpose: a token this deck
        // "can use" has to come from a card that is actually in the deck.
        val cards = listOf(boards.commanders, boards.mainboard, boards.companions)
            .flatMap { board -> board.cards.values }
            .map { entry ->
                DeckCard(
                    name = entry.card.name,
                    scryfallId = entry.card.scryfallId?.takeIf { it.isNotBlank() },
                    quantity = entry.quantity,
                )
            }

        return DeckDetail(
            publicId = response.publicId,
            name = response.name,
            commander = commanderNames(boards.commanders),
            // `main` is Moxfield's own cover card, which for a commander deck
            // is the commander; falling back to the commander board covers
            // decks where it is absent.
            imageUrl = MoxfieldImages.artCrop(response.main)
                ?: MoxfieldImages.artCrop(boards.commanders.cards.values.firstOrNull()?.card),
            cards = cards,
        )
    }

    /** Partner commanders are joined; sorted so the label is stable across fetches. */
    fun commanderNames(board: Board): String? =
        board.cards.values
            .map { it.card.name }
            .filter { it.isNotBlank() }
            .sorted()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" & ")
}
