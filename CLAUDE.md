# CLAUDE.md

Working notes for AI agents on this repo. Read this before touching code; read
[README.md](README.md) for what the app is and why the API layer looks the way
it does.

etoken is a sibling of [mllagostera/commander-companion](https://github.com/mllagostera/commander-companion)
and follows its conventions. It is a **standalone app**, though: no shared code,
no backend, no dependency on that project at runtime. What it borrows is the
hard-won knowledge about Moxfield's undocumented API, and the house style below.

---

## 1. Language

This trips people up because the repo is deliberately bilingual.

| Where | Language |
|---|---|
| Code comments, KDoc, file names | **English** |
| README, CLAUDE.md, ADRs, any `docs/` | **English** |
| Commit messages, PR titles and bodies | **English** |
| UI strings — `res/values/strings.xml` | **English** (this is the default locale) |
| UI strings — `res/values-es/`, `-ca/`, `-fr/`, `-de/`, `-it/`, `-ja/` | Spanish, Catalan, French, German, Italian, Japanese |
| Conversation with the repo owner | **Spanish** |

`values/` is English and **there is no `values-en/`** — English *is* the
default, so a `values-en/` would be a copy that can only drift. Spanish is a
locale like any other, in `values-es/`.

This is where etoken parts from commander-companion, which defaults to Spanish,
and it was a deliberate change: `values/` is not "the app's language", it is
what every locale the app does *not* ship falls back to. A phone set to
Portuguese used to get Spanish; it now gets English. A phone set to Spanish is
unaffected — `values-es/` answers first.

The user can also override the device entirely, from the picker in the corner
of the username screen (`ui/common/LanguagePicker.kt`). Adding a locale means
three things: a `values-<tag>/` folder, an `AppLanguage` entry, and a
`language_<tag>` string naming the language **in itself** (`Français`, not
`French`) — a picker is only useful to someone who cannot read the language
they are stuck in. `AppLanguageTest` fails if the first two come apart, which
is the pair that fails silently: an entry with no folder is a switch that
changes nothing.

## 2. Branching, commits, PRs

Branch names carry a type prefix. Taken from what commander-companion actually
uses across its history, not from a style guide:

| Prefix | For | Example |
|---|---|---|
| `claude/<topic>-<suffix>` | Work started from Claude Code, which appends its own six-character suffix | `claude/admin-dashboard-l3fexw` |
| `feat/<topic>` | A feature branch opened by hand | `feat/tracker-fullscreen-api` |
| `chore/<topic>` | Tooling, dependencies, docs | `chore/pre-push-md-filter` |

Topics are kebab-case and name the work, not the files it touches. One old
branch used `feature/` instead of `feat/`; treat it as a stray, not a variant.

- Never commit straight to `main`.
- Integrate through a pull request. Don't open one unless the owner asks.
- Commit subjects: English, imperative, sentence case, no Conventional Commits
  prefix. "Add the friends screen to Android", not "feat(android): add screen".
- Write a real body when the change has reasoning worth keeping — why this
  approach, what was rejected, what is verified and what isn't. The commit log
  is the project's memory; this repo has no `docs/roadmap/` to carry it.

## 3. Commands

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # Android Lint
```

Needs an Android SDK (`compileSdk 37`, `minSdk 26`). There is **no CI yet**;
when it is added, model it on commander-companion's
`.github/workflows/android-ci.yml` (JDK 21 temurin, lint + unit tests +
`assembleDebug`).

## 4. Layout, and one rule that holds it together

```
com.etoken
├── data/       wire types, Retrofit APIs, OkHttp setup, repository, in-memory board store
├── domain/     the rules — token extraction, battlefield state, deck search, power/toughness
└── ui/         four screens, one ViewModel each, Compose + Material 3
```

**Keep `domain/` free of Android imports, and keep `data/` free of them except
where a platform API is unavoidable** (`UserPreferences` and `AppLanguageStore`
need `Context`, and the latter also needs `Configuration` to apply a locale;
nothing else should). This is not architectural purity for its own sake — it is
what lets the interesting logic be unit-tested on the JVM. Every rule worth
arguing about lives in `domain/` behind a pure function, and
`app/src/test/` covers it. If you find yourself reaching for a Compose or
Android type inside `domain/`, the logic is in the wrong place.

Dependencies are wired by hand in `AppContainer`. **Do not introduce Hilt.**
commander-companion uses it and earns it across many feature modules; this app
has one repository and two stores, and staying KSP-free keeps the build fast.

**Do not add `androidx.compose.material:material-icons-*`.** Icons are local
vector drawables in `res/drawable/`, rendered through `ui/common/ActionButton`.
commander-companion avoids those artifacts too.

## 5. Things that look wrong and are not

Every item here cost real debugging, in this repo or the sibling one. Each has
a comment at the call site saying so. Don't "clean them up".

- **Moxfield needs a browser-shaped `User-Agent` and a `Referer`.** It sits
  behind Cloudflare and turns away anything that looks like a client. See
  `data/Network.kt`.
- **There is no "decks by user" endpoint.** Listing a user's decks goes through
  the deck-*search* endpoint filtered to one author (`v2/decks/search-sfw`),
  which is how the site itself does it.
- **Two-faced cards: the art crop only exists under `card-face-{faceId}`.**
  Requesting `card-{faceId}` also answers `200` but serves a *different card* —
  the two id namespaces are separate. See `data/moxfield/MoxfieldImages.kt`.
- **Scryfall's image CDN answers `400` to OkHttp's default `User-Agent`.** The
  Coil `ImageLoader` in `EtokenApplication` sets a descriptive one. The failure
  is silent: images simply never appear.
- **Tokens come from Scryfall's `all_parts`.** The app never parses a card's
  rules text to work out what it creates, and it should stay that way.
- **`app/debug.keystore` is committed on purpose.** Without it, every CI run
  signs with a debug key it generated seconds earlier, and each APK refuses to
  install over the last one. It holds Android's own published debug
  credentials, so there is nothing in it to leak. Release signing is separate
  and still unsolved (C4).
- **CI's emulator is a 320x640dp screen**, and the token grid is one column
  wide there. A lazy grid composes only what is near the viewport, so an
  instrumented test has to scroll to a cell before asserting on it — below the
  fold, the cell is not in the semantics tree at all and even `assertExists`
  fails. See `TokensScreenTest.scrollToCell`.
- **The chosen language is in `SharedPreferences`, not in the DataStore that
  holds the username.** A locale has to be applied in `attachBaseContext`,
  before `onCreate` and with nothing to suspend in; DataStore can only be read
  from a coroutine, so putting it there means `runBlocking` on the main thread
  at every cold start. See `data/AppLanguageStore.kt`. Changing the language
  recreates the activity for the same reason — `Resources` are built once, from
  that context, and no recomposition can reach them.
- **The token board stores *stacks*, not a count plus counters.** Magic tracks
  counters and summoning sickness per permanent, so "7 tokens, 3 sick, +1/+1 on
  all" cannot represent reality. `TokenBoardRules` also enforces two invariants
  — merge what became identical, drop what emptied — and every operation must
  go through it.

## 6. Deliberate scope decisions

Not gaps. Don't "fix" them without asking.

- The token board is **in-memory only**. It belongs to the game on the table; a
  stale board restored days later would be worse than an empty one.
- **Only +1/+1 counters.** The model already stores the count per stack, so
  more types are cheap to add later.
- **The sideboard doesn't count** toward a deck's tokens.
- **No backend.** The app talks to Moxfield and Scryfall directly.

## 7. State of the repo

**Start at [docs/TASKS.md](docs/TASKS.md)** — what works, what is merely
written, and what is pending, audited against the code rather than against
filenames. Update it in the same change that resolves an item.

The unit tests pass and cover the logic. **The UI layer has never been
compiled**, and no request has ever been made against the real APIs — the
environment this was written in has no Android SDK and blocks both hosts. Treat
`ui/` as unverified until someone runs a build.

Keep the "State of verification" section in `README.md` honest as this changes:
say what actually ran, not what should work.
