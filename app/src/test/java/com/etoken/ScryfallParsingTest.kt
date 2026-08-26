package com.etoken

import com.etoken.data.scryfall.CollectionRequest
import com.etoken.data.scryfall.CollectionResponse
import com.etoken.data.scryfall.Identifier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScryfallParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `an identifier serializes to exactly one key`() {
        // Scryfall rejects an identifier object carrying both id and name, so
        // explicitNulls = false is load-bearing rather than cosmetic.
        assertEquals("""{"id":"abc"}""", json.encodeToString(Identifier.byId("abc")))
        assertEquals("""{"name":"Sol Ring"}""", json.encodeToString(Identifier.byName("Sol Ring")))
    }

    @Test
    fun `a collection request serializes to the documented envelope`() {
        val request = CollectionRequest(listOf(Identifier.byId("a"), Identifier.byName("b")))

        assertEquals(
            """{"identifiers":[{"id":"a"},{"name":"b"}]}""",
            json.encodeToString(request),
        )
    }

    @Test
    fun `reads related parts and artwork off a card`() {
        val payload = """
            {"data":[{
              "id":"krenko-id","name":"Krenko, Mob Boss","layout":"normal",
              "type_line":"Legendary Creature — Goblin Warrior",
              "oracle_text":"Tap: Create X 1/1 red Goblin creature tokens.",
              "image_uris":{"small":"s.jpg","normal":"n.jpg","art_crop":"a.jpg"},
              "all_parts":[
                {"id":"goblin-id","component":"token","name":"Goblin",
                 "type_line":"Token Creature — Goblin","uri":"https://api.scryfall.com/cards/goblin-id"},
                {"id":"krenko-id","component":"combo_piece","name":"Krenko, Mob Boss","type_line":"Legendary Creature"}
              ]}]}
        """.trimIndent()

        val card = json.decodeFromString<CollectionResponse>(payload).data.single()

        assertEquals("Krenko, Mob Boss", card.name)
        assertEquals("n.jpg", card.imageUrl())
        assertEquals(listOf("goblin-id", "krenko-id"), card.allParts.map { it.id })
        assertEquals("token", card.allParts.first().component)
    }

    @Test
    fun `reads the keyword list, which is where printed haste comes from`() {
        val payload = """
            {"data":[{
              "id":"dragon-t","name":"Dragon","type_line":"Token Creature — Dragon",
              "power":"5","toughness":"5","keywords":["Flying","Haste"]}]}
        """.trimIndent()

        val card = json.decodeFromString<CollectionResponse>(payload).data.single()

        assertEquals(listOf("Flying", "Haste"), card.keywords)
    }

    @Test
    fun `a card that lists no keywords decodes to an empty list`() {
        val payload = """{"data":[{"id":"goblin-t","name":"Goblin"}]}"""

        assertEquals(
            emptyList<String>(),
            json.decodeFromString<CollectionResponse>(payload).data.single().keywords,
        )
    }

    @Test
    fun `finds artwork on a double-faced card, which has none at the top level`() {
        val payload = """
            {"data":[{
              "id":"dfc","name":"Delver of Secrets // Insectile Aberration","layout":"transform",
              "card_faces":[
                {"name":"Delver of Secrets","type_line":"Creature — Human Wizard",
                 "image_uris":{"normal":"front.jpg"}},
                {"name":"Insectile Aberration","type_line":"Creature — Human Insect",
                 "image_uris":{"normal":"back.jpg"}}]}]}
        """.trimIndent()

        val card = json.decodeFromString<CollectionResponse>(payload).data.single()

        assertNull(card.imageUris)
        assertEquals("front.jpg", card.imageUrl())
    }

    @Test
    fun `a card with no artwork at all reports none`() {
        val payload = """{"data":[{"id":"x","name":"Nameless","type_line":"Token"}]}"""

        assertNull(json.decodeFromString<CollectionResponse>(payload).data.single().imageUrl())
    }

    @Test
    fun `an empty payload decodes to an empty list rather than throwing`() {
        assertEquals(emptyList<Any>(), json.decodeFromString<CollectionResponse>("{}").data)
    }
}
