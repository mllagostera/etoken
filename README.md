# etoken

An Android app that answers one question: **which tokens can this deck make?**

Type a Moxfield username, get that user's public decks in a grid of commander
art, tap one, and see every token its cards are able to create — each with its
own artwork and the list of cards that create it. Tap a token and it becomes a
tracker for the game in progress: how many are on the battlefield, which ones
are still summoning sick, and what +1/+1 counters they carry.

---

## 1. How it works

Three screens, and two APIs behind them.

```
username  ──▶  deck grid  ──▶  token grid  ──▶  token board
              (Moxfield)      (Moxfield       (in-memory,
                               + Scryfall)     no network)
```

**Listing a user's decks.** Moxfield has no "decks of user X" endpoint. The
deck-search endpoint filtered to a single author is how the site itself does
it, and it paginates:

```
GET https://api2.moxfield.com/v2/decks/search-sfw
      ?authorUserNames={user}&pageNumber=1&pageSize=100
      &sortType=Updated&sortDirection=Descending
      &includePinned=true&showIllegal=true
```

**Deck contents and commander art.**

```
GET https://api2.moxfield.com/v3/decks/all/{publicId}
```

The response's `main` field is the card Moxfield uses as the deck's cover —
the commander, for a Commander deck — and its id addresses the art crop on
Moxfield's CDN.

**Tokens.** Every card in the deck carries a `scryfall_id`, and Scryfall's card
objects list related tokens under `all_parts`. So the token screen is two
batched Scryfall calls: resolve the deck's cards, read their `all_parts`, then
resolve the token ids that came back — the second call is where the artwork
is. Both are chunked at Scryfall's 75-identifier limit.

This means the app never parses rules text to work out what makes tokens.
Scryfall already knows.

**Tracking them in play.** The board screen is local state only — no request
leaves the device. It holds *stacks*: groups of copies that are genuinely
identical. Magic tracks counters and summoning sickness per permanent, so four
Goblins where one carries a +1/+1 counter are not interchangeable, and a single
count with counters bolted on would be a lie. Adding tokens creates a
summoning-sick stack; starting your turn clears sickness across the board;
putting a counter on only some of a stack splits it, and taking that counter
off merges it straight back. `TokenBoardRules` enforces both invariants —
merge what has become identical, drop what has emptied — in one place.

The state is deliberately not persisted. It belongs to the game on the table,
and a stale board restored three days later would be worse than an empty one.

## 2. Prior art this is built on

The Moxfield half is **ported from
[mllagostera/commander-companion](https://github.com/mllagostera/commander-companion)**
(`backend/internal/moxfield/client.go`), rather than rediscovered. Moxfield
publishes no API documentation, so that client is the only specification that
exists. Carried over from it:

- the endpoint set and query parameters above;
- the browser-shaped `User-Agent` and the `Referer` header, without which
  Cloudflare turns the request away;
- the art-crop URL templates — including the trap that a **two-faced card's**
  crop only exists under `card-face-{faceId}`, because requesting
  `card-{faceId}` also answers `200` but silently serves a different card;
- the retry policy: network errors, 5xx and 429 are retried with exponential
  backoff and honour `Retry-After`; a 404 never is.

One more thing came from that repo's Android client: Scryfall's image CDN
rejects OkHttp's default `User-Agent` as bot traffic with an HTTP 400, so
Coil's `ImageLoader` sets a descriptive one. The failure mode is silent —
images simply never appear.

The version catalog is deliberately kept in lockstep with that project's
`android/` module, which already builds green against this toolchain.

## 3. Architecture

Single Gradle module, Kotlin + Jetpack Compose, Material 3.

```
com.etoken
├── data/
│   ├── moxfield/          wire types, Retrofit API, CDN art-crop URLs
│   ├── scryfall/          wire types, Retrofit API
│   ├── Network.kt         OkHttp: headers, rate limiting, retries
│   ├── DeckMapper.kt      Moxfield's board-keyed JSON ──▶ domain model
│   ├── MoxfieldRepository.kt
│   ├── TokenBoardStore.kt in-memory battlefield state, shared across screens
│   └── UserPreferences.kt DataStore: remembers the last username
├── domain/
│   ├── TokenExtractor.kt  which tokens a deck can make — pure, no I/O
│   ├── TokenBoardRules.kt what is on the battlefield — pure, no I/O
│   ├── DeckFilter.kt      accent-blind search over name and commander
│   ├── PowerToughness.kt  printed size plus counters, `*` and all
│   └── model/
└── ui/                    four screens, one ViewModel each
```

Dependencies are wired by hand in `AppContainer` rather than with Hilt: this
app has one repository and one preferences store, so a container built in
`EtokenApplication` keeps the build free of KSP.

Three things the deck grid does that are easy to miss:

- **Search is accent-blind and covers the commander.** Typing `canon` finds
  "Cañón", and `markov` finds a deck whose name never mentions Edgar. A
  multi-word query has to match on every word, in any order.
- **Refresh drops both caches**, decks and tokens together: a deck whose
  contents changed also makes a different set of tokens. A refresh that fails
  keeps the list already on screen and says so, rather than replacing something
  usable with an error page.
- **"New game" clears every board**, not just the open deck's. Token ids are
  Scryfall ids, so the same Goblin is the same entry whichever deck brought it.

Two more decisions worth knowing about:

- **The deck grid streams.** Deck names appear as soon as search returns;
  covers fill in afterwards, four decks at a time, and one deck failing to
  hydrate leaves the rest of the grid alone. Search is undocumented and its
  payload has changed before, so the grid never assumes it carried a name or
  a cover — anything missing is fetched per deck, and when search does return
  everything that costs no request at all.
- **Tokens are collapsed by name + type + rules text**, not by id. `all_parts`
  points at one specific *printing*, so a deck drawing from several sets would
  otherwise show the same 1/1 Soldier four times over. Creator lists are
  merged; the first printing with art wins the image.

## 4. Build

```bash
./gradlew :app:assembleDebug     # APK
./gradlew :app:testDebugUnitTest # unit tests
```

Needs an Android SDK — `compileSdk 37`, `minSdk 26`. Open the project in
Android Studio and it will offer to install what's missing.

## 5. State of verification

Honest accounting of what has and has not been run.

**Verified.** The 65 unit tests in `app/src/test/` all pass, covering the
token rules, the battlefield rules (splitting, merging, ordering, clamping),
deck search, power/toughness with counters, the Moxfield and Scryfall wire
formats, and the repository's paging, caching, batching and by-name fallback
(with fake APIs).
The whole data layer — Retrofit interfaces, OkHttp interceptors, repository —
compiles against the exact library versions pinned in the catalog.

**Not verified.** The UI layer, the Gradle Android build, and any live API
call. The environment this was written in has no Android SDK available and
blocks `api2.moxfield.com`, `api.scryfall.com` and `dl.google.com` at the
network policy, so `:app` has never been assembled and no request has ever
been made against the real APIs. The endpoint contract is inherited from a
client that ran in production, not confirmed here.

The first run on a real device is therefore the real smoke test. If decks
fail to load, `Network.kt`'s Moxfield headers are the first place to look:
Cloudflare's posture is the part most likely to have moved.
