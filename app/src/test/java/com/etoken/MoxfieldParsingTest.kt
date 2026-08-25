package com.etoken

import com.etoken.data.DeckMapper
import com.etoken.data.moxfield.DeckResponse
import com.etoken.data.moxfield.MoxfieldCard
import com.etoken.data.moxfield.MoxfieldImages
import com.etoken.data.moxfield.SearchResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the wire contract with Moxfield. The fixtures below mirror the shape
 * commander-companion's Go client decodes, which is the only spec that exists.
 */
class MoxfieldParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `parses a deck search page and ignores fields we do not model`() {
        val payload = """
            {
              "pageNumber": 1,
              "totalPages": 2,
              "totalResults": 130,
              "someFutureField": {"nested": true},
              "data": [
                {"publicId": "abc123", "name": "Krenko Goblins", "format": "commander",
                 "main": {"id": "aB3xY", "name": "Krenko, Mob Boss"}},
                {"publicId": "def456", "name": "Atraxa"}
              ]
            }
        """.trimIndent()

        val page = json.decodeFromString<SearchResponse>(payload)

        assertEquals(2, page.totalPages)
        assertEquals(listOf("abc123", "def456"), page.data.map { it.publicId })
        assertEquals("Krenko Goblins", page.data.first().name)
        // Second deck has no cover card; that must be a null, not a crash.
        assertNull(page.data[1].main)
    }

    @Test
    fun `flattens a deck into commanders plus mainboard and drops the sideboard`() {
        val deck = json.decodeFromString<DeckResponse>(DECK_JSON)
        val detail = DeckMapper.toDetail(deck)

        assertEquals("abc123", detail.publicId)
        assertEquals("Krenko Goblins", detail.name)
        assertEquals("Krenko, Mob Boss", detail.commander)
        assertEquals(
            setOf("Krenko, Mob Boss", "Goblin Chieftain", "Skirk Prospector"),
            detail.cards.map { it.name }.toSet(),
        )
        assertTrue(detail.cards.none { it.name == "Pithing Needle" })
    }

    @Test
    fun `carries the scryfall id through, and tolerates it being absent`() {
        val detail = DeckMapper.toDetail(json.decodeFromString<DeckResponse>(DECK_JSON))

        assertEquals(
            "6f4b1eb0-1111-2222-3333-444455556666",
            detail.cards.single { it.name == "Krenko, Mob Boss" }.scryfallId,
        )
        // Skirk Prospector has no scryfall_id in the fixture: the repository
        // falls back to resolving it by name, so null is the expected value.
        assertNull(detail.cards.single { it.name == "Skirk Prospector" }.scryfallId)
    }

    @Test
    fun `joins partner commanders in a stable order`() {
        val payload = """
            {"publicId":"p","name":"Partners","boards":{"commanders":{"count":2,"cards":{
              "z":{"quantity":1,"card":{"id":"1","name":"Tymna the Weaver"}},
              "a":{"quantity":1,"card":{"id":"2","name":"Thrasios, Triton Hero"}}}}}}
        """.trimIndent()

        val detail = DeckMapper.toDetail(json.decodeFromString<DeckResponse>(payload))

        // Sorted, so the label doesn't flip between fetches (Moxfield keys the
        // board with a map, and map order is not guaranteed).
        assertEquals("Thrasios, Triton Hero & Tymna the Weaver", detail.commander)
    }

    @Test
    fun `builds the art crop from the cover card`() {
        val detail = DeckMapper.toDetail(json.decodeFromString<DeckResponse>(DECK_JSON))

        assertEquals(
            "https://assets.moxfield.net/cards/card-aB3xY-art_crop.jpg",
            detail.imageUrl,
        )
    }

    @Test
    fun `uses the face id for a two-faced cover card`() {
        // The card- prefix would answer 200 here but serve a different card
        // entirely: the face-id and card-id namespaces are separate.
        val twoFaced = MoxfieldCard(
            id = "combinedId",
            name = "Delver of Secrets // Insectile Aberration",
            cardFaces = listOf(
                MoxfieldCard(id = "frontFace", name = "Delver of Secrets"),
                MoxfieldCard(id = "backFace", name = "Insectile Aberration"),
            ),
        )

        assertEquals(
            "https://assets.moxfield.net/cards/card-face-frontFace-art_crop.jpg",
            MoxfieldImages.artCrop(twoFaced),
        )
    }

    @Test
    fun `has no art crop when there is no cover card`() {
        assertNull(MoxfieldImages.artCrop(null))
        assertNull(MoxfieldImages.artCrop(MoxfieldCard(id = "", name = "x")))
    }

    @Test
    fun `falls back to the commander when the deck has no cover card`() {
        val payload = """
            {"publicId":"p","name":"No cover","boards":{"commanders":{"count":1,"cards":{
              "k":{"quantity":1,"card":{"id":"cmdrId","name":"Kenrith"}}}}}}
        """.trimIndent()

        val detail = DeckMapper.toDetail(json.decodeFromString<DeckResponse>(payload))

        assertEquals("https://assets.moxfield.net/cards/card-cmdrId-art_crop.jpg", detail.imageUrl)
    }

    private companion object {
        val DECK_JSON = """
            {
              "publicId": "abc123",
              "name": "Krenko Goblins",
              "format": "commander",
              "main": {"id": "aB3xY", "name": "Krenko, Mob Boss"},
              "boards": {
                "commanders": {"count": 1, "cards": {
                  "k1": {"quantity": 1, "card": {
                    "id": "aB3xY", "name": "Krenko, Mob Boss",
                    "scryfall_id": "6f4b1eb0-1111-2222-3333-444455556666",
                    "type_line": "Legendary Creature — Goblin Warrior"}}}},
                "mainboard": {"count": 2, "cards": {
                  "m1": {"quantity": 1, "card": {
                    "id": "cH13f", "name": "Goblin Chieftain",
                    "scryfall_id": "aaaabbbb-1111-2222-3333-444455556666"}},
                  "m2": {"quantity": 1, "card": {"id": "pRo5p", "name": "Skirk Prospector"}}}},
                "sideboard": {"count": 1, "cards": {
                  "s1": {"quantity": 1, "card": {"id": "nEEdl", "name": "Pithing Needle"}}}}
              }
            }
        """.trimIndent()
    }
}
