package com.etoken

import com.etoken.data.MoxfieldRepository
import com.etoken.data.moxfield.Board
import com.etoken.data.moxfield.BoardEntry
import com.etoken.data.moxfield.Boards
import com.etoken.data.moxfield.DeckResponse
import com.etoken.data.moxfield.MoxfieldApi
import com.etoken.data.moxfield.MoxfieldCard
import com.etoken.data.moxfield.SearchDeckSummary
import com.etoken.data.moxfield.SearchResponse
import com.etoken.domain.DeckSource
import com.etoken.domain.model.DeckSummary
import com.etoken.data.scryfall.CollectionRequest
import com.etoken.data.scryfall.CollectionResponse
import com.etoken.data.scryfall.ImageUris
import com.etoken.data.scryfall.RelatedCard
import com.etoken.data.scryfall.ScryfallApi
import com.etoken.data.scryfall.ScryfallCard
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the two-phase Scryfall lookup and the paging/caching around it. */
class MoxfieldRepositoryTest {

    @Test
    fun `pages through search until the last page`() = runTest {
        val moxfield = FakeMoxfield(
            pages = listOf(
                searchPage(totalPages = 3, ids = listOf("a", "b")),
                searchPage(totalPages = 3, ids = listOf("c")),
                searchPage(totalPages = 3, ids = listOf("d")),
            ),
        )
        val repository = MoxfieldRepository(moxfield, FakeScryfall())

        val decks = repository.listDecks(DeckSource("someone"))

        assertEquals(listOf("a", "b", "c", "d"), decks.map { it.publicId })
        assertEquals(listOf(1, 2, 3), moxfield.requestedPages)
    }

    @Test
    fun `stops paging when a page comes back empty`() = runTest {
        val moxfield = FakeMoxfield(
            pages = listOf(
                searchPage(totalPages = 9, ids = listOf("a")),
                searchPage(totalPages = 9, ids = emptyList()),
            ),
        )

        val decks = MoxfieldRepository(moxfield, FakeScryfall()).listDecks(DeckSource("someone"))

        assertEquals(listOf("a"), decks.map { it.publicId })
        assertEquals(listOf(1, 2), moxfield.requestedPages)
    }

    @Test
    fun `hydrating an already complete summary costs no request`() = runTest {
        val moxfield = FakeMoxfield(deck = krenkoDeck())
        val repository = MoxfieldRepository(moxfield, FakeScryfall())

        val complete = DeckSummary(
            publicId = "abc",
            name = "Krenko Goblins",
            imageUrl = "https://assets.moxfield.net/cards/card-aB3xY-art_crop.jpg",
            commander = "Krenko, Mob Boss",
        )

        assertEquals(complete, repository.hydrate(complete))
        assertEquals(0, moxfield.deckCalls)
    }

    @Test
    fun `a summary with a name and a cover but no commander is still fetched`() = runTest {
        // Search returns both of those and never a commander, so this is the
        // ordinary case rather than an edge one.
        val moxfield = FakeMoxfield(
            pages = listOf(searchPage(1, listOf("abc"), withCover = true)),
            deck = krenkoDeck(),
        )
        val repository = MoxfieldRepository(moxfield, FakeScryfall())

        val hydrated = repository.hydrate(repository.listDecks(DeckSource("someone")).single())

        assertEquals("Krenko, Mob Boss", hydrated.commander)
        assertEquals(1, moxfield.deckCalls)
    }

    @Test
    fun `hydrate fills in a missing cover from the deck itself`() = runTest {
        val moxfield = FakeMoxfield(
            pages = listOf(searchPage(1, listOf("abc"), withCover = false)),
            deck = krenkoDeck(),
        )
        val repository = MoxfieldRepository(moxfield, FakeScryfall())

        val hydrated = repository.hydrate(repository.listDecks(DeckSource("someone")).single())

        assertEquals("https://assets.moxfield.net/cards/card-aB3xY-art_crop.jpg", hydrated.imageUrl)
        assertEquals("Krenko, Mob Boss", hydrated.commander)
        assertEquals(1, moxfield.deckCalls)
    }

    @Test
    fun `deck detail is fetched once and then served from cache`() = runTest {
        val moxfield = FakeMoxfield(deck = krenkoDeck())
        val repository = MoxfieldRepository(moxfield, FakeScryfall())

        repository.deckDetail("abc")
        repository.deckDetail("abc")
        repository.tokensFor("abc")

        assertEquals(1, moxfield.deckCalls)
    }

    @Test
    fun `resolves every token the deck can create, with its artwork and creators`() = runTest {
        val moxfield = FakeMoxfield(deck = krenkoDeck())
        val scryfall = FakeScryfall(
            byId = mapOf(
                "krenko-sid" to ScryfallCard(
                    id = "krenko-sid",
                    name = "Krenko, Mob Boss",
                    allParts = listOf(tokenPart("goblin-sid", "Goblin")),
                ),
                "chieftain-sid" to ScryfallCard(
                    id = "chieftain-sid",
                    name = "Goblin Chieftain",
                    allParts = emptyList(),
                ),
                "goblin-sid" to ScryfallCard(
                    id = "goblin-sid",
                    name = "Goblin",
                    typeLine = "Token Creature — Goblin",
                    imageUris = ImageUris(normal = "https://cards.scryfall.io/normal/goblin.jpg"),
                ),
            ),
            // Skirk Prospector has no scryfall_id on Moxfield's side, so the
            // repository has to fall back to resolving it by name.
            byName = mapOf(
                "Skirk Prospector" to ScryfallCard(
                    id = "prospector-sid",
                    name = "Skirk Prospector",
                    allParts = listOf(tokenPart("goblin-sid", "Goblin")),
                ),
            ),
        )

        val tokens = MoxfieldRepository(moxfield, scryfall).tokensFor("abc")

        val goblin = tokens.single()
        assertEquals("Goblin", goblin.name)
        assertEquals("https://cards.scryfall.io/normal/goblin.jpg", goblin.imageUrl)
        assertEquals(listOf("Krenko, Mob Boss", "Skirk Prospector"), goblin.createdBy)
    }

    @Test
    fun `a deck whose cards make no tokens resolves to an empty list without a second call`() =
        runTest {
            val moxfield = FakeMoxfield(deck = krenkoDeck())
            val scryfall = FakeScryfall(
                byId = mapOf(
                    "krenko-sid" to ScryfallCard(id = "krenko-sid", name = "Krenko, Mob Boss"),
                    "chieftain-sid" to ScryfallCard(id = "chieftain-sid", name = "Goblin Chieftain"),
                ),
                byName = mapOf(
                    "Skirk Prospector" to ScryfallCard(id = "p", name = "Skirk Prospector"),
                ),
            )

            assertTrue(MoxfieldRepository(moxfield, scryfall).tokensFor("abc").isEmpty())
            // One batch for the deck's cards, and no second batch for tokens.
            assertEquals(listOf(3), scryfall.batchSizes)
        }

    @Test
    fun `splits lookups into batches Scryfall will accept`() = runTest {
        val bigDeck = DeckResponse(
            publicId = "big",
            name = "Big",
            boards = Boards(
                mainboard = Board(
                    cards = (1..170).associate { index ->
                        "k$index" to BoardEntry(
                            quantity = 1,
                            card = MoxfieldCard(
                                id = "m$index",
                                name = "Card $index",
                                scryfallId = "sid-$index",
                            ),
                        )
                    },
                ),
            ),
        )
        val scryfall = FakeScryfall()

        MoxfieldRepository(FakeMoxfield(deck = bigDeck), scryfall).tokensFor("big")

        assertEquals(ScryfallApi.MAX_IDENTIFIERS, 75)
        assertEquals(listOf(75, 75, 20), scryfall.batchSizes)
    }

    @Test
    fun `deduplicates identifiers so repeated basics cost one slot`() = runTest {
        val deck = DeckResponse(
            publicId = "lands",
            name = "Lands",
            boards = Boards(
                mainboard = Board(
                    cards = (1..10).associate { index ->
                        "k$index" to BoardEntry(
                            quantity = 1,
                            card = MoxfieldCard(id = "m$index", name = "Mountain", scryfallId = "mountain-sid"),
                        )
                    },
                ),
            ),
        )
        val scryfall = FakeScryfall()

        MoxfieldRepository(FakeMoxfield(deck = deck), scryfall).tokensFor("lands")

        assertEquals(listOf(1), scryfall.batchSizes)
    }

    @Test
    fun `a search result with no cover card yields no image`() = runTest {
        val moxfield = FakeMoxfield(pages = listOf(searchPage(1, listOf("a"), withCover = false)))

        assertNull(MoxfieldRepository(moxfield, FakeScryfall()).listDecks(DeckSource("u")).single().imageUrl)
    }

    @Test
    fun `a plain user listing asks for no format at all`() = runTest {
        val moxfield = FakeMoxfield(pages = listOf(searchPage(1, listOf("a"))))

        MoxfieldRepository(moxfield, FakeScryfall()).listDecks(DeckSource(" someone "))

        // Trimmed, because the username comes from a text field.
        assertEquals(listOf("someone"), moxfield.requestedAuthors)
        // Null rather than an empty string: Retrofit then leaves `fmt` off the
        // URL entirely, which is what an unfiltered search is.
        assertEquals(listOf<String?>(null), moxfield.requestedFormats)
    }

    @Test
    fun `the precons listing filters by Wizards and by the precon format`() = runTest {
        val moxfield = FakeMoxfield(
            pages = listOf(
                searchPage(totalPages = 2, ids = listOf("a")),
                searchPage(totalPages = 2, ids = listOf("b")),
            ),
        )

        val decks = MoxfieldRepository(moxfield, FakeScryfall()).listDecks(DeckSource.PRECONS)

        assertEquals(listOf("a", "b"), decks.map { it.publicId })
        // The filter has to hold on every page, not only the first: without it
        // page two would come back with the whole account's decks.
        assertEquals(listOf("WizardsOfTheCoast", "WizardsOfTheCoast"), moxfield.requestedAuthors)
        assertEquals(listOf("commanderPrecons", "commanderPrecons"), moxfield.requestedFormats)
    }

    // --- fixtures -------------------------------------------------------

    private fun tokenPart(id: String, name: String) = RelatedCard(
        id = id,
        component = "token",
        name = name,
        typeLine = "Token Creature — Goblin",
    )

    private fun searchPage(totalPages: Int, ids: List<String>, withCover: Boolean = true) =
        SearchResponse(
            totalPages = totalPages,
            data = ids.map { id ->
                SearchDeckSummary(
                    publicId = id,
                    name = "Deck $id",
                    main = if (withCover) MoxfieldCard(id = "cover-$id", name = "Cover") else null,
                )
            },
        )

    private fun krenkoDeck() = DeckResponse(
        publicId = "abc",
        name = "Krenko Goblins",
        main = MoxfieldCard(id = "aB3xY", name = "Krenko, Mob Boss"),
        boards = Boards(
            commanders = Board(
                count = 1,
                cards = mapOf(
                    "k" to BoardEntry(
                        card = MoxfieldCard(
                            id = "aB3xY",
                            name = "Krenko, Mob Boss",
                            scryfallId = "krenko-sid",
                        ),
                    ),
                ),
            ),
            mainboard = Board(
                count = 2,
                cards = mapOf(
                    "m1" to BoardEntry(
                        card = MoxfieldCard(
                            id = "cH13f",
                            name = "Goblin Chieftain",
                            scryfallId = "chieftain-sid",
                        ),
                    ),
                    // No scryfall_id: exercises the resolve-by-name fallback.
                    "m2" to BoardEntry(card = MoxfieldCard(id = "pRo5p", name = "Skirk Prospector")),
                ),
            ),
        ),
    )
}

private class FakeMoxfield(
    private val pages: List<SearchResponse> = emptyList(),
    private val deck: DeckResponse = DeckResponse(),
) : MoxfieldApi {

    val requestedPages = mutableListOf<Int>()
    val requestedAuthors = mutableListOf<String>()
    val requestedFormats = mutableListOf<String?>()
    var deckCalls = 0
        private set

    override suspend fun searchDecks(
        username: String,
        pageNumber: Int,
        pageSize: Int,
        format: String?,
        sortType: String,
        sortDirection: String,
        includePinned: Boolean,
        showIllegal: Boolean,
    ): SearchResponse {
        requestedPages += pageNumber
        requestedAuthors += username
        requestedFormats += format
        return pages.getOrElse(pageNumber - 1) { SearchResponse() }
    }

    override suspend fun deck(publicId: String): DeckResponse {
        deckCalls++
        return deck
    }
}

private class FakeScryfall(
    private val byId: Map<String, ScryfallCard> = emptyMap(),
    private val byName: Map<String, ScryfallCard> = emptyMap(),
) : ScryfallApi {

    val batchSizes = mutableListOf<Int>()

    override suspend fun collection(body: CollectionRequest): CollectionResponse {
        batchSizes += body.identifiers.size
        return CollectionResponse(
            data = body.identifiers.mapNotNull { identifier ->
                identifier.id?.let(byId::get) ?: identifier.name?.let(byName::get)
            },
        )
    }
}
