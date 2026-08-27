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

You do not need an account of your own to try the app. A second button on that
first screen loads **Wizards' preconstructed Commander decks**, which is the
same deck search filtered to `WizardsOfTheCoast` with `fmt=commanderPrecons`
— that account publishes far more than precons, so the format filter is what
makes it a listing rather than a dump. From there everything behaves as it does
for your own decks: the grid, the search, the tokens, the board.

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

Those tokens are what the **"+"** on the battlefield offers. Picking one asks
how many to make, whether they enter tapped, and — for a token named `Copy` —
what it is a copy of, all in one dialog, so a press of OK is exactly one thing
happening on the table. A cell in the picker carries the count of that token
already in play, so "have I made these yet?" is answered without going back.

Tokens are collapsed by name, type, rules text and printed power/toughness
rather than by id. `all_parts` points at one specific *printing*, so a deck
drawing on several sets would otherwise show the same 1/1 Soldier four times
over. The creator lists are merged; the first printing that has artwork wins
the image.

### Tracking them in play

Opening a deck goes straight to the battlefield — local state only, with no
request leaving the device. It starts empty, and the "+" is what fills it.

The table holds **entries**: the copies made by one press of "add", kept
together with their own counters, their own summoning sickness and their own
tapped state. Magic tracks all three per permanent, so seven Goblins of which
three carry a +1/+1 counter are not interchangeable.

**Two entries never merge.** Make three Goblins now and three more next turn and
that is two cells for the rest of the game, however alike they look — they are
two things that happened at the table, and an app that fused them would be
throwing that away. Quantity still lives on the entry, because a batch of three
is one thing rather than three; what is gone is anything that joins batches up.

- Tokens arrive **summoning sick**, which is what actually happens — unless the
  token is printed with **haste**, in which case they enter able to attack. A
  non-creature token is never sick at all. Printed haste comes from Scryfall's
  `keywords`, never from reading rules text: a token that *grants* haste to
  other creatures has none itself. Haste handed out at the table by another
  permanent is something no app can see, so that half stays a chip you tap.
- **A tap turns an entry**, and turns it back. It is the gesture a table asks
  for most, so it is the one that costs nothing. On an entry holding more than
  one copy it asks how many first — "All (6)" is one press, and a smaller
  number splits the entry, because three of six Goblins tapped for mana leaves
  three that can still attack and they are not the same three.
- **A long press opens everything else**: the +1/+1 counters, the sickness, the
  count, and taking the entry off the table.
- **"My turn begins"** clears sickness, untaps the whole table — and joins back
  up whatever it has just made identical. Two tapped, two ready and two
  summoning sick are three cells while those states differ, and one cell of six
  the moment a turn resets them. It is the only thing that merges, and it is
  what the no-merge rule is for: the untap step erases exactly the differences
  that kept those entries apart. What it does not reset still tells them apart —
  a different token, different counters, a different creature being copied.
- **"+1/+1 on all"** grows every entry of every token.
- Putting a counter on **only some** of an entry splits it in place; the halves
  stay side by side and stay apart.
- Nothing re-sorts. Entries sit in the order they were made, so one you touch
  does not move out from under your finger.

`BoardRules` enforces the one invariant left after every operation — an entry
that has emptied disappears — and deliberately no longer merges on every edit,
only at the untap step.

**Starting a new game** asks first and says how many tokens leave the table. It
empties everything, including what another deck put there: the board belongs to
the game being played, not to the deck on screen.

On a screen at least 840dp wide — a tablet in landscape — the picker stops
hiding behind the "+" and becomes a pane beside the table. Below that it is a
modal sheet. The width decides, not the navigation graph, and both arrangements
run on the same view model and the same board store.

**Undo** covers that and every other edit, including a new game. The trail is a
snapshot of the whole table rather than one per token, which is what makes
emptying it undoable at all. Adding an entry of seven is one step, not seven. An edit that changed nothing is not a step, so undo never has to be
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
    │   │   ├── GameBoardStore.kt         in-memory battlefield state, and undo
    │   │   └── UserPreferences.kt        DataStore: the remembered username
    │   │
    │   ├── domain/                   pure Kotlin — no Android imports at all
    │   │   ├── TokenExtractor.kt         which tokens a deck can make
    │   │   ├── BoardRules.kt             every edit the battlefield supports
    │   │   ├── DeckFilter.kt             accent-blind search over name and commander
    │   │   ├── PowerToughness.kt         printed size plus counters, `*` included
    │   │   ├── UndoHistory.kt            a bounded trail, and what counts as a step
    │   │   └── model/
    │   │       ├── Models.kt             DeckSummary, DeckDetail, TokenCard
    │   │       └── Board.kt              BoardEntry, GameBoard
    │   │
    │   └── ui/                       Compose, Material 3, one ViewModel per screen
    │       ├── EtokenNavHost.kt          the three routes
    │       ├── username/                 screen 1 — who are you on Moxfield
    │       ├── decks/                    screen 2 — the deck grid, search, refresh
    │       ├── tokens/                   the picker behind the "+", and its add dialog
    │       ├── board/                    screen 3 — the table, one pane or two
    │       ├── common/                   error states, icon buttons, LoadError
    │       └── theme/                    colours, dynamic colour on Android 12+
    │
    ├── main/res/
    │   ├── values/                   English — the default locale, and the fallback for the rest
    │   ├── values-es|ca|fr|de|it|ja/  Spanish, Catalan, French, German, Italian, Japanese
    │   ├── values-night/             dark theme
    │   └── drawable/                 local vector icons; no material-icons artifacts
    │
    └── test/java/com/etoken/         unit tests, all on the JVM
        ├── BoardRulesTest.kt             30
        ├── MoxfieldRepositoryTest.kt     11
        ├── DeckFilterTest.kt             10
        ├── TokenExtractorTest.kt         9
        ├── MoxfieldParsingTest.kt        8
        ├── ScryfallParsingTest.kt        8
        ├── HasteTokenTest.kt             8
        ├── GameBoardStoreTest.kt         8
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

**Verified (as of run #91).** The app builds and the screens work. CI runs `assembleDebug`,
`testDebugUnitTest` and `lintDebug` on every push, then boots an emulator and
runs the instrumented suite against it: as of run #91, 127 unit tests and 37 UI
tests pass, lint is clean, and the run produces an installable APK. That run is
the first on the rebuilt battlefield — the table as the screen a deck opens
onto, the "+" that adds to it, and entries that never merge — so the figures
describe the app as it now stands. The UI tests
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

Run #81 did the same for the two that landed after it: the precons button's ten
tests, which nothing had executed or even compiled, and the grid's
summoning-sickness badge's six. Every test in the tree has now run at least
once on the tree it ships in.

That single splash test is the only one in the suite that launches the real
`MainActivity` instead of a composable in a test activity, which is why it can
fail on a misconfigured launch theme — and why, until it existed, nothing in CI
had ever run `MainActivity.onCreate`. It cannot fail on the thing worth
watching: the splash holds the *drawing* of the first frame, and a composable
that is never drawn still reports itself displayed, so a splash that hung would
pass. That one needs eyes, and joins the list below.

**Written, not yet run.** The precon button landed after run #73, and its eight
unit tests and two UI tests are on top of the figures above rather than in them:
none has executed, and no compiler has read the change — the environment it was
written in has no Android SDK. What nothing in CI can check either way is
whether `fmt=commanderPrecons` is still what Moxfield's search endpoint calls
that format; it is undocumented, and the only proof is a live request.

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
