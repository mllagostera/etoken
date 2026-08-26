# etoken

An Android app that answers one question during a game of Magic: The Gathering —
**which tokens can this deck make, and how many of them are on the table right
now?**

Type a Moxfield username, pick a deck from a grid of commander art, and see
every token its cards are able to create. Tap one and it becomes a tracker for
the game in progress: how many are in play, which ones are still summoning
sick, and what +1/+1 counters they carry.

---

## 1. What it does

### Loading decks

You enter a Moxfield username once; the app remembers it. It then lists that
user's public decks as a grid of commander artwork.

The grid streams rather than blocks. Deck names appear as soon as the search
endpoint answers, and the cover art fills in afterwards four decks at a time,
so the grid is usable immediately instead of waiting on a request per deck. One
deck failing to load its cover leaves the rest of the grid alone.

You can filter the loaded list by typing. The filter is accent-blind — `canon`
finds *Cañón* — and it searches the commander as well as the deck name, because
people remember the general they play rather than what they called the deck
three years ago. A multi-word query has to match every word, in any order.

Refreshing drops both caches, decks and tokens together: a deck whose contents
changed on Moxfield also creates a different set of tokens, so keeping the old
token list would be worse than fetching it again. A refresh that fails keeps
the list already on screen and says so, rather than replacing something
perfectly usable with an error page.

### Finding the tokens

Opening a deck shows every token its cards can create, each with its artwork,
its type line, and the list of cards that create it.

These come from Scryfall's `all_parts`, which names the tokens related to a
card. **The app never parses rules text to work out what a card creates.**
Scryfall already knows, and a text parser would be a permanent source of bugs.

Tokens are collapsed by name, type, rules text and printed power/toughness
rather than by id. `all_parts` points at one specific *printing*, so a deck
drawing on several sets would otherwise show the same 1/1 Soldier four times
over. The creator lists are merged; the first printing that has artwork wins
the image.

### Tracking them in play

Tapping a token opens its board — local state only, with no request leaving the
device.

The board holds **stacks**: groups of copies that are genuinely identical.
Magic tracks counters and summoning sickness per permanent, so seven Goblins of
which three carry a +1/+1 counter are not interchangeable, and a single count
with counters bolted onto it would be a lie. So:

- Tokens arrive **summoning sick**, which is what actually happens.
- **"My turn begins"** clears sickness across every stack at once.
- Putting a counter on **only some** of a stack splits it; taking that counter
  off merges it straight back.
- Stacks sort ready-before-sick and larger-first, so the row you are most
  likely to touch is at the top, and a merge keeps the older stack id so the
  list does not re-animate under your finger.

`TokenBoardRules` enforces two invariants after every operation — merge what
has become identical, drop what has emptied — so no action can leave a board
showing two rows that look the same but are not.

Both destructive actions ask first and say what is at stake: clearing one
token's board, and starting a new game, which clears every board because token
ids are Scryfall ids and the same Goblin is the same entry whichever deck
brought it.

## 2. Project structure

```
etoken/
├── .github/workflows/
│   ├── android-ci.yml        build, unit tests, lint and an APK, on every push
│   └── api-smoke.yml         manual: walks the real APIs with the app's own headers
├── docs/
│   └── TASKS.md              what works, what is merely written, what is pending
├── gradle/libs.versions.toml the version catalog
├── CLAUDE.md                 conventions for anyone, human or agent, working here
└── app/src/
    ├── main/java/com/etoken/
    │   ├── MainActivity.kt           the single activity; hosts the nav graph
    │   ├── EtokenApplication.kt      Coil's ImageLoader, with the CDN-safe User-Agent
    │   ├── AppContainer.kt           hand-rolled dependency container
    │   │
    │   ├── data/
    │   │   ├── moxfield/
    │   │   │   ├── MoxfieldApi.kt        Retrofit: deck search and deck detail
    │   │   │   ├── MoxfieldDtos.kt       wire types, every field defaulted
    │   │   │   └── MoxfieldImages.kt     art-crop URLs, incl. the two-faced case
    │   │   ├── scryfall/
    │   │   │   ├── ScryfallApi.kt        Retrofit: POST /cards/collection
    │   │   │   └── ScryfallDtos.kt       wire types, incl. all_parts
    │   │   ├── Network.kt                OkHttp: headers, rate limiting, retries
    │   │   ├── DeckMapper.kt             Moxfield's board-keyed JSON → domain model
    │   │   ├── MoxfieldRepository.kt     paging, caching, batching, name fallback
    │   │   ├── TokenBoardStore.kt        in-memory battlefield state, shared
    │   │   └── UserPreferences.kt        DataStore: the remembered username
    │   │
    │   ├── domain/                   pure Kotlin — no Android imports at all
    │   │   ├── TokenExtractor.kt         which tokens a deck can make
    │   │   ├── TokenBoardRules.kt        what is on the battlefield
    │   │   ├── DeckFilter.kt             accent-blind search over name and commander
    │   │   ├── PowerToughness.kt         printed size plus counters, `*` included
    │   │   └── model/
    │   │       ├── Models.kt             DeckSummary, DeckDetail, TokenCard
    │   │       └── Board.kt              TokenStack, TokenBoard
    │   │
    │   └── ui/                       Compose, Material 3, one ViewModel per screen
    │       ├── EtokenNavHost.kt          the four routes
    │       ├── username/                 screen 1 — who are you on Moxfield
    │       ├── decks/                    screen 2 — the deck grid, search, refresh
    │       ├── tokens/                   screen 3 — what this deck can create
    │       ├── board/                    screen 4 — what is on the table
    │       ├── common/                   error states, icon buttons, LoadError
    │       └── theme/                    colours, dynamic colour on Android 12+
    │
    ├── main/res/
    │   ├── values/                   Spanish — the default locale
    │   ├── values-ca|en|fr|de|it|ja/  Catalan, English, French, German, Italian, Japanese
    │   ├── values-night/             dark theme
    │   └── drawable/                 local vector icons; no material-icons artifacts
    │
    └── test/java/com/etoken/         65 unit tests, all on the JVM
        ├── TokenExtractorTest.kt         9
        ├── TokenBoardRulesTest.kt        18
        ├── DeckFilterTest.kt             10
        ├── PowerToughnessTest.kt         4
        ├── MoxfieldParsingTest.kt        8
        ├── MoxfieldRepositoryTest.kt     10
        └── ScryfallParsingTest.kt        6
```

### The rule that holds it together

**`domain/` contains no Android imports, and `data/` contains them only where a
platform API is unavoidable** — `UserPreferences` needs a `Context`; nothing
else does.

This is not architectural purity for its own sake. It is what lets every rule
worth arguing about be unit-tested on a plain JVM: the token extraction, the
battlefield invariants, the search matching, the power/toughness arithmetic,
and the repository's paging and batching against fake APIs. Reaching for a
Compose or Android type inside `domain/` means the logic is in the wrong place.

Dependencies are wired by hand in `AppContainer` rather than with Hilt. The
sibling project uses Hilt and earns it across many feature modules; this app
has one repository and two stores, and staying free of KSP keeps the build
fast.

## 3. Building

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # Android Lint
```

Requires an Android SDK — `compileSdk 37`, `minSdk 26`, JDK 21. Opening the
project in Android Studio will offer to install whatever is missing.

CI runs all three on every push and uploads the debug APK as an artifact, so a
green run produces something installable rather than just a green tick.

## 4. Languages

The default locale is **Spanish**, matching the sibling project, with Catalan,
English, French, German, Italian and Japanese as overrides.

Translations use Magic's own terminology in each language rather than a literal
rendering — *Spielstein* and *Einsatzverzögerung* in German, *pedina* and
*svogliatezza da evocazione* in Italian, *jeton* and *mal d'invocation* in
French, *召喚酔い* in Japanese. Japanese declares only the `other` plural, which
is the only form that language has.

## 5. Prior art

The Moxfield half is **ported from
[mllagostera/commander-companion](https://github.com/mllagostera/commander-companion)**
(`backend/internal/moxfield/client.go`) rather than rediscovered. Moxfield
publishes no API documentation, so that client — which runs in production — is
the only specification that exists. The endpoints, the Cloudflare headers, the
retry policy and the two-faced art-crop trap all come from it, as does the
descriptive image `User-Agent` that its Android client carries for the same
reason.

The version catalog is deliberately kept in lockstep with that project's
`android/` module, and so is the `gradle.properties` set of AGP 9 flags that
makes it work.

etoken is nonetheless **standalone**: no shared code, no backend, and no
dependency on that project at runtime.

## 6. State of verification

Item-by-item status is in [docs/TASKS.md](docs/TASKS.md). In summary:

**Verified.** The app builds. CI runs `assembleDebug`, `testDebugUnitTest` and
`lintDebug` on every push and all three are green: 65 unit tests pass, lint is
clean, and the run produces an installable APK.

**Not verified.** Anything that needs eyes or a live network. No screen has
been watched doing its job. The `api-smoke` workflow walks the real APIs with
the app's own headers, and its first run got **HTTP 403 from Moxfield** — from
a GitHub runner, which is a datacenter IP that Cloudflare treats far more
harshly than a phone on mobile data, so that result is a warning rather than a
verdict. Installing the APK is still the real test.
