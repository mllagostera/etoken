package com.etoken

import com.etoken.data.scryfall.ImageUris
import com.etoken.data.scryfall.RelatedCard
import com.etoken.data.scryfall.ScryfallCard
import com.etoken.domain.TokenExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenExtractorTest {

    private fun card(
        id: String,
        name: String,
        parts: List<RelatedCard> = emptyList(),
    ) = ScryfallCard(id = id, name = name, allParts = parts)

    private fun part(
        id: String,
        name: String,
        component: String = "token",
        typeLine: String = "Token Creature — Goblin",
    ) = RelatedCard(id = id, name = name, component = component, typeLine = typeLine)

    private fun token(
        id: String,
        name: String,
        typeLine: String,
        oracle: String? = null,
        image: String? = "https://cards.scryfall.io/normal/$id.jpg",
    ) = ScryfallCard(
        id = id,
        name = name,
        typeLine = typeLine,
        oracleText = oracle,
        imageUris = ImageUris(normal = image),
    )

    @Test
    fun `collects tokens and remembers which card creates each`() {
        val deck = listOf(
            card("krenko", "Krenko, Mob Boss", listOf(part("goblin-t", "Goblin"))),
            card("krenko2", "Krenko, Tin Street Kingpin", listOf(part("goblin-t", "Goblin"))),
        )

        val references = TokenExtractor.tokenReferences(deck)

        assertEquals(setOf("goblin-t"), references.keys)
        assertEquals(
            setOf("Krenko, Mob Boss", "Krenko, Tin Street Kingpin"),
            references.getValue("goblin-t"),
        )
    }

    @Test
    fun `ignores related parts that are not tokens`() {
        val deck = listOf(
            card(
                "brisela-part",
                "Bruna, the Fading Light",
                listOf(
                    part("meld", "Brisela", component = "meld_result", typeLine = "Legendary Creature"),
                    part("combo", "Gisela", component = "combo_piece", typeLine = "Legendary Creature"),
                    part("spirit", "Spirit", component = "token", typeLine = "Token Creature — Spirit"),
                ),
            ),
        )

        assertEquals(setOf("spirit"), TokenExtractor.tokenReferences(deck).keys)
    }

    @Test
    fun `keeps emblems even when they are not tagged as tokens`() {
        val deck = listOf(
            card(
                "teferi",
                "Teferi, Hero of Dominaria",
                listOf(part("emblem", "Teferi Emblem", component = "combo_piece", typeLine = "Emblem")),
            ),
        )

        assertEquals(setOf("emblem"), TokenExtractor.tokenReferences(deck).keys)
    }

    @Test
    fun `a card never counts as creating itself`() {
        // Double-faced cards list their own id among their related parts.
        val deck = listOf(
            card("delver", "Delver of Secrets", listOf(part("delver", "Insectile Aberration"))),
        )

        assertTrue(TokenExtractor.tokenReferences(deck).isEmpty())
    }

    @Test
    fun `collapses the same token printed in different sets`() {
        // Two cards each point at a different *printing* of the same 1/1 Soldier.
        val references = mapOf(
            "soldier-m21" to setOf("Precinct Captain"),
            "soldier-dom" to setOf("Adeline"),
        )
        val printings = listOf(
            token("soldier-m21", "Soldier", "Token Creature — Soldier", oracle = null),
            token("soldier-dom", "Soldier", "Token Creature — Soldier", oracle = null),
        )

        val tokens = TokenExtractor.buildTokens(references, printings)

        assertEquals(1, tokens.size)
        assertEquals(listOf("Adeline", "Precinct Captain"), tokens.single().createdBy)
    }

    @Test
    fun `keeps tokens that differ only in rules text apart`() {
        val references = mapOf("a" to setOf("Card A"), "b" to setOf("Card B"))
        val printings = listOf(
            token("a", "Spirit", "Token Creature — Spirit", oracle = "Flying"),
            token("b", "Spirit", "Token Creature — Spirit", oracle = null),
        )

        assertEquals(2, TokenExtractor.buildTokens(references, printings).size)
    }

    @Test
    fun `falls back to a later printing for artwork`() {
        val references = mapOf("a" to setOf("Card A"), "b" to setOf("Card B"))
        val printings = listOf(
            token("a", "Treasure", "Token Artifact — Treasure", image = null),
            token("b", "Treasure", "Token Artifact — Treasure", image = "https://img/treasure.jpg"),
        )

        val treasure = TokenExtractor.buildTokens(references, printings).single()
        assertEquals("https://img/treasure.jpg", treasure.imageUrl)
    }

    @Test
    fun `drops tokens whose card could not be resolved`() {
        val references = mapOf("missing" to setOf("Some Card"))

        assertTrue(TokenExtractor.buildTokens(references, emptyList()).isEmpty())
    }

    @Test
    fun `orders tokens by type then name`() {
        val references = mapOf("a" to setOf("X"), "b" to setOf("Y"), "c" to setOf("Z"))
        val printings = listOf(
            token("a", "Zombie", "Token Creature — Zombie"),
            token("b", "Treasure", "Token Artifact — Treasure"),
            token("c", "Goblin", "Token Creature — Goblin"),
        )

        assertEquals(
            listOf("Treasure", "Goblin", "Zombie"),
            TokenExtractor.buildTokens(references, printings).map { it.name },
        )
    }
}
