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

Before any of that there is a splash: the launcher icon's mark on the icon's
own background. Android 12 and later put one up whether an app asks or not, so
this is less about adding a screen than about deciding what that screen looks
like — and about drawing the same one on Android 8 through 11, which otherwise
open on a blank window. It is held for the moment it takes to read the
remembered username out of DataStore, so the field is already filled the first
time it is seen rather than a beat later, and it is bounded: a store that never
answers costs a second, not a splash that never leaves.

**Public** is the whole of it: the app reads Moxfield without signing in, so
private and unlisted decks are invisible to it. Moxfield's search endpoint does
not mention the decks it will not show, which means an account with only
private decks looks exactly like an empty one — so the app says so where the
question comes up, under the username field and again on an empty list, rather
than leaving a missing deck to read as a bug.

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

A quick filter sits above the grid: one chip that narrows it to the tokens with
copies on the battlefield. It appears with the first token on the table — a
filter with nothing to filter is noise — and it reads the same boards the grid's
badges are drawn from, so the two can never disagree. Emptying the table leaves
the chip in place and the grid saying so, rather than blank.

A cell with copies in play carries the count, and beneath it the **+1/+1
counters** those copies are carrying — but only while the token's board is a
single stack. Two stacks mean two answers, and one badge that picked either
would be lying about the other; there the grid says nothing and the board
screen, which has room for a stack at a time, is where to look.

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

- Tokens arrive **summoning sick**, which is what actually happens — unless the
  token is printed with **haste**, in which case they enter able to attack and
  the board says so. Printed haste comes from Scryfall's `keywords`, never from
  reading rules text: a token that *grants* haste to other creatures has none
  itself. Haste handed out at the table by another permanent is something no
  app can see, so that half stays a chip the player taps.
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

On a screen at least 840dp wide — a tablet in landscape — the board stops
being a screen of its own and becomes a pane beside the grid, with the open
token marked in the list. Below that it is a destination like any other. The
width decides, not the navigation graph, and both arrangements run on the same
view models and the same board store.

**Undo** covers both, and every other edit. The trail is kept over all the
boards at once rather than one per token, which is what makes a new game
undoable at all — it empties every board, and a per-token trail could not put
that back. An edit that changed nothing is not a step, so undo never has to be
pressed twice to see something happen, and the trail stops at twenty steps:
this is a play aid for one game at one table, not a document editor.

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
    │   │   ├── TokenBoardStore.kt        in-memory battlefield state, and undo
    │   │   └── UserPreferences.kt        DataStore: the remembered username
    │   │
    │   ├── domain/                   pure Kotlin — no Android imports at all
    │   │   ├── TokenExtractor.kt         which tokens a deck can make
    │   │   ├── TokenBoardRules.kt        what is on the battlefield
    │   │   ├── DeckFilter.kt             accent-blind search over name and commander
    │   │   ├── TokenFilter.kt            the quick filter: what counts as in play
    │   │   ├── PowerToughness.kt         printed size plus counters, `*` included
    │   │   ├── UndoHistory.kt            a bounded trail, and what counts as a step
    │   │   └── model/
    │   │       ├── Models.kt             DeckSummary, DeckDetail, TokenCard
    │   │       └── Board.kt              TokenStack, TokenBoard
    │   │
    │   └── ui/                       Compose, Material 3, one ViewModel per screen
    │       ├── EtokenNavHost.kt          the four routes
    │       ├── TokensAndBoard.kt         one pane or two, decided by the width
    │       ├── username/                 screen 1 — who are you on Moxfield
    │       ├── decks/                    screen 2 — the deck grid, search, refresh
    │       ├── tokens/                   screen 3 — what this deck can create, and the quick filter
    │       ├── board/                    screen 4 — what is on the table
    │       ├── common/                   error states, icon buttons, LoadError
    │       └── theme/                    colours, dynamic colour on Android 12+
    │
    ├── main/res/
    │   ├── values/                   English — the default locale, and the fallback for the rest
    │   ├── values-es|ca|fr|de|it|ja/  Spanish, Catalan, French, German, Italian, Japanese
    │   ├── values-night/             dark theme
    │   └── drawable/                 local vector icons; no material-icons artifacts
    │
    └── test/java/com/etoken/         115 unit tests, all on the JVM
        ├── TokenBoardRulesTest.kt        29
        ├── MoxfieldRepositoryTest.kt     11
        ├── DeckFilterTest.kt             10
        ├── TokenFilterTest.kt            6
        ├── TokenExtractorTest.kt         9
        ├── MoxfieldParsingTest.kt        8
        ├── ScryfallParsingTest.kt        8
        ├── HasteTokenTest.kt             8
        ├── TokenBoardStoreTest.kt        7
        ├── UndoHistoryTest.kt            6
        ├── AppLanguageTest.kt            6
        ├── PowerToughnessTest.kt         4
        └── CopyTokenTest.kt              3
```

### The rule that holds it together

**`domain/` contains no Android imports, and `data/` contains them only where a
platform API is unavoidable** — `UserPreferences` and `AppLanguageStore` need a
`Context`, and applying a locale needs a `Configuration`; nothing else does.

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

CI runs all three on every push and publishes the debug APK as its own
artifact, so a green run produces something installable rather than just a
green tick.

**Installing it on a device.** Open the run under the repository's *Actions*
tab, and download `etoken-debug-apk-<run number>` from the artifacts at the
bottom of the summary. GitHub serves artifacts as a zip, so unzip it to get
`app-debug.apk`. Android will ask you to allow installs from whichever app is
delivering it. It coexists with nothing — the application id is `com.etoken` —
and it needs Android 8.0 or newer. Artifacts are kept for 90 days.

Each build installs **over** the last one: `app/debug.keystore` is committed,
so CI and every checkout sign with the same key, and `versionCode` is the run
number, so the phone can see which of two builds is newer. That is worth
saying because it was not always true: every APK CI published before this
change was signed with a key the runner had generated seconds earlier, so it
conflicted with whatever was already installed. If you are updating from one
of those, uninstall first — you lose the saved Moxfield username and nothing
else.

## 4. Languages

The default locale is **English**, with Spanish, Catalan, French, German,
Italian and Japanese as overrides. There is no `values-en/`: English *is*
`values/`, and a second copy of it could only drift.

That is a change from the sibling project, which defaults to Spanish, and it is
about fallback rather than about preference — `values/` is what a phone set to
a language the app does not ship resolves to. A phone in Portuguese used to get
Spanish and now gets English; a phone in Spanish still gets Spanish.

A phone's language is not the last word, either. The corner of the username
screen — the one screen every launch passes through — has a picker offering all
seven, each written in itself, plus *device language* for anyone who never
wants to think about it. The choice is stored on the device and applied when
the activity is built, so it survives restarts and outlives the game it was
made in. The app does this itself rather than leaning on Android 13's per-app
language setting, which does not exist on the Android 8 through 12 this app
still supports.

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

**Verified.** The app builds and the screens work. CI runs `assembleDebug`,
`testDebugUnitTest` and `lintDebug` on every push, then boots an emulator and
runs the instrumented suite against it: as of run #73, 115 unit tests and 33 UI
tests pass, lint is clean, and the run produces an installable APK. The UI tests
drive the real screens and view models with only the two APIs faked, so they
fail when the app breaks rather than when a double does.

The seven tests behind the grid's counter badge are in those figures now. The
first run to execute them, #64, failed three — and none of the three for a
reason in the app. The emulator is the default AVD, 320x640dp, which the token
grid answers with a single column of cells most of the screen tall; a lazy grid
does not compose what is far from the viewport, so a cell below the fold is
absent from the semantics tree rather than merely off screen. The tests scroll
to a cell before asserting on it, and #65 is green across all thirty.

Run #73 is the first to execute the language picker's eight tests and the
splash screen's one, both of which landed after the previous green run and were
claims until it finished. They pass. One part of the picker still has no test at
all and is called out below.

That single splash test is the only one in the suite that launches the real
`MainActivity` instead of a composable in a test activity, which is why it can
fail on a misconfigured launch theme — and why, until it existed, nothing in CI
had ever run `MainActivity.onCreate`. It cannot fail on the thing worth
watching: the splash holds the *drawing* of the first frame, and a composable
that is never drawn still reports itself displayed, so a splash that hung would
pass. That one needs eyes, and joins the list below.

**Not verified.** Anything that needs eyes or a live network. Nobody has
watched a cold start, so nothing has confirmed that the splash hands over to
the username screen without a flicker between them. The screens are
exercised, not inspected — nothing has checked that a layout is legible, well
spaced, or that a German compound noun fits the badge it lands in. The `api-smoke` workflow walks the real APIs with
the app's own headers, and its first run got **HTTP 403 from Moxfield** — from
a GitHub runner, which is a datacenter IP that Cloudflare treats far more
harshly than a phone on mobile data, so that result is a warning rather than a
verdict. Installing the APK is still the real test.

The language switch has a hole in the middle of it that no test reaches.
Choosing a language is covered — the dialog lists all seven, and the choice is
written where a freshly built store reads it back — but *applying* it happens in
`MainActivity.attachBaseContext`, and nothing asserts on the result. The splash
test narrowed that hole without closing it: launching the real activity means
`attachBaseContext` now at least runs in CI, so it can no longer throw
unnoticed. What is still untested is whether picking Japanese redraws the app in
Japanese, which is the entire point of the feature.

One live question is neither of those and needs nothing but a button press:
whether Scryfall puts `keywords` on **token** card objects, which is what the
haste rule reads. `api-smoke` asks it and goes red if the answer is no; nobody
has run it since. Until then a token printed with haste could quietly keep
arriving summoning sick, which is exactly how it behaved before.
