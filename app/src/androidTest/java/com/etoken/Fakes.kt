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
import com.etoken.data.scryfall.CollectionRequest
import com.etoken.data.scryfall.CollectionResponse
import com.etoken.data.scryfall.ImageUris
import com.etoken.data.scryfall.RelatedCard
import com.etoken.data.scryfall.ScryfallApi
import com.etoken.data.scryfall.ScryfallCard

/**
 * A repository wired to canned answers.
 *
 * These tests drive the real screens and the real view models; only the two
 * APIs are replaced. That keeps them honest about the app's behaviour while
 * making them independent of Moxfield, of Scryfall, and of whether the device
 * running them has a network at all.
 */
object Fakes {

    const val DECK_ID = "abc123"
    const val DECK_NAME = "Krenko Goblins"
    const val OTHER_DECK_ID = "def456"
    const val OTHER_DECK_NAME = "Atraxa Superfriends"
    const val OTHER_COMMANDER = "Atraxa, Praetors' Voice"
    const val COMMANDER = "Krenko, Mob Boss"
    const val TOKEN_ID = "goblin-sid"
    const val TOKEN_NAME = "Goblin"
    // A second token in the same deck, so a test can watch a filter hide one
    // token and keep the other.
    const val TREASURE_TOKEN_ID = "treasure-sid"
    const val TREASURE_TOKEN_NAME = "Treasure"
    const val COPY_TOKEN_ID = "copy-sid"
    const val COPY_TOKEN_NAME = "Copy"

    fun repository(): MoxfieldRepository = MoxfieldRepository(FakeMoxfield(), FakeScryfall())
}

private val KRENKO = DeckResponse(
    publicId = Fakes.DECK_ID,
    name = Fakes.DECK_NAME,
    main = MoxfieldCard(id = "aB3xY", name = Fakes.COMMANDER),
    boards = Boards(
        commanders = Board(
            count = 1,
            cards = mapOf(
                "k" to BoardEntry(
                    card = MoxfieldCard(id = "aB3xY", name = Fakes.COMMANDER, scryfallId = "krenko-sid"),
                ),
            ),
        ),
        mainboard = Board(
            count = 2,
            cards = mapOf(
                "m1" to BoardEntry(
                    card = MoxfieldCard(id = "cH13f", name = "Goblin Chieftain", scryfallId = "chieftain-sid"),
                ),
                "m2" to BoardEntry(
                    card = MoxfieldCard(id = "d0Ck5", name = "Dockside Extortionist", scryfallId = "dockside-sid"),
                ),
            ),
        ),
    ),
)

private val ATRAXA = DeckResponse(
    publicId = Fakes.OTHER_DECK_ID,
    name = Fakes.OTHER_DECK_NAME,
    main = MoxfieldCard(id = "atX", name = Fakes.OTHER_COMMANDER),
    boards = Boards(
        commanders = Board(
            count = 1,
            cards = mapOf(
                "c" to BoardEntry(
                    card = MoxfieldCard(id = "atX", name = Fakes.OTHER_COMMANDER, scryfallId = "atraxa-sid"),
                ),
            ),
        ),
    ),
)

private class FakeMoxfield : MoxfieldApi {

    override suspend fun searchDecks(
        username: String,
        pageNumber: Int,
        pageSize: Int,
        sortType: String,
        sortDirection: String,
        includePinned: Boolean,
        showIllegal: Boolean,
    ): SearchResponse = if (pageNumber > 1) {
        SearchResponse()
    } else {
        SearchResponse(
            totalPages = 1,
            data = listOf(
                SearchDeckSummary(
                    publicId = Fakes.DECK_ID,
                    name = Fakes.DECK_NAME,
                    main = MoxfieldCard(id = "aB3xY", name = Fakes.COMMANDER),
                ),
                SearchDeckSummary(
                    publicId = Fakes.OTHER_DECK_ID,
                    name = Fakes.OTHER_DECK_NAME,
                    main = MoxfieldCard(id = "atX", name = Fakes.OTHER_COMMANDER),
                ),
            ),
        )
    }

    // Keyed by id rather than returning one deck for everything: hydration now
    // fetches every deck, so a fake that answered KRENKO for all of them gave
    // Atraxa Krenko's commander and made a search test fail for a reason that
    // had nothing to do with the app.
    override suspend fun deck(publicId: String): DeckResponse = when (publicId) {
        Fakes.OTHER_DECK_ID -> ATRAXA
        else -> KRENKO
    }
}

private class FakeScryfall : ScryfallApi {

    private val cards = mapOf(
        "krenko-sid" to ScryfallCard(
            id = "krenko-sid",
            name = Fakes.COMMANDER,
            allParts = listOf(
                RelatedCard(
                    id = Fakes.TOKEN_ID,
                    component = "token",
                    name = Fakes.TOKEN_NAME,
                    typeLine = "Token Creature — Goblin",
                ),
            ),
        ),
        "chieftain-sid" to ScryfallCard(id = "chieftain-sid", name = "Goblin Chieftain"),
        "dockside-sid" to ScryfallCard(
            id = "dockside-sid",
            name = "Dockside Extortionist",
            allParts = listOf(
                RelatedCard(
                    id = Fakes.TREASURE_TOKEN_ID,
                    component = "token",
                    name = Fakes.TREASURE_TOKEN_NAME,
                    typeLine = "Token Artifact — Treasure",
                ),
            ),
        ),
        // Atraxa's deck makes a copy token; Krenko's does not. Keeping them in
        // separate decks lets the copy tests have a board to themselves.
        "atraxa-sid" to ScryfallCard(
            id = "atraxa-sid",
            name = Fakes.OTHER_COMMANDER,
            allParts = listOf(
                RelatedCard(
                    id = Fakes.COPY_TOKEN_ID,
                    component = "token",
                    name = Fakes.COPY_TOKEN_NAME,
                    typeLine = "Token",
                ),
            ),
        ),
        Fakes.COPY_TOKEN_ID to ScryfallCard(
            id = Fakes.COPY_TOKEN_ID,
            name = Fakes.COPY_TOKEN_NAME,
            typeLine = "Token",
        ),
        Fakes.TREASURE_TOKEN_ID to ScryfallCard(
            id = Fakes.TREASURE_TOKEN_ID,
            name = Fakes.TREASURE_TOKEN_NAME,
            typeLine = "Token Artifact — Treasure",
        ),
        Fakes.TOKEN_ID to ScryfallCard(
            id = Fakes.TOKEN_ID,
            name = Fakes.TOKEN_NAME,
            typeLine = "Token Creature — Goblin",
            power = "1",
            toughness = "1",
            imageUris = ImageUris(normal = "https://example.invalid/goblin.jpg"),
        ),
    )

    override suspend fun collection(body: CollectionRequest): CollectionResponse =
        CollectionResponse(data = body.identifiers.mapNotNull { cards[it.id] })
}
