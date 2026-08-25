# TASKS

Status audited against the code on **2026-08-25** — against what the files
actually do, not against what they are named. This document is only useful
while it stays true, so update it in the same change that resolves an item.

One short line per item: state, what, and where. Reasoning worth keeping goes
in the commit that resolves the item, not here — this repo has no decisions
log, and the commit log is its memory.

## Legend

| | |
|---|---|
| `[x]` | Done **and verified** — something actually ran and proved it |
| `[~]` | Written, unit-tested where testable, but **never compiled or run** |
| `[ ]` | Not started |

`[~]` covers most of the app and is not a formality: no Android build has ever
been executed, so no screen has been seen working. That is item A1 below, and
until it is done every `[~]` here is a claim, not a fact.

---

## 1. Verified — 65 unit tests, 0 failures

The logic layer. All of it is Android-free and runs on the JVM, which is what
made it testable without an SDK.

- [x] Moxfield wire contract: search paging, v3 deck, board flattening — `data/moxfield/`, 8 tests
- [x] Art-crop URLs, including the two-faced `card-face-` case — `data/moxfield/MoxfieldImages.kt`
- [x] Scryfall collection envelope and identifier serialization — `data/scryfall/`, 6 tests
- [x] Token extraction from `all_parts`, dedupe, emblems, self-reference — `domain/TokenExtractor.kt`, 9 tests
- [x] Repository: paging, caching, 75-id batching, by-name fallback — `data/MoxfieldRepository.kt`, 10 tests
- [x] Battlefield rules: stacks, split, merge, ordering, clamping — `domain/TokenBoardRules.kt`, 18 tests
- [x] Power/toughness with counters, including `*` and `1+*` — `domain/PowerToughness.kt`, 4 tests
- [x] Deck search: accent-blind, commander, multi-word AND — `domain/DeckFilter.kt`, 10 tests
- [x] Whole data layer compiles against the pinned library versions

## 2. Written, never run

Everything the user actually touches. Each has passing tests underneath it
where there was logic to test; none has been seen on a screen.

### Screens
- [~] Username entry, remembered in DataStore; insets- and keyboard-aware — `ui/username/`
- [~] Deck grid with commander art and streaming hydration — `ui/decks/`
- [~] Deck search field with result counter — `ui/decks/DecksScreen.kt`
- [~] Refresh, with a banner that keeps the last good list on failure — `ui/decks/`
- [~] Token grid with in-play badges — `ui/tokens/`
- [~] Token board: quantity, summoning sickness, +1/+1 counters — `ui/board/`
- [~] New game, behind a confirmation that names what is lost — `ui/tokens/TokensScreen.kt`

### Build
- [~] Gradle setup, catalog aligned with commander-companion's `android/`
- [~] Manual DI in `AppContainer`, no Hilt
- [~] Coil `ImageLoader` with the User-Agent Scryfall's CDN demands

## 3. Pending

### A. Verification — blocking

Nothing below A matters until A1 passes.

- [ ] **A1** Run `./gradlew :app:assembleDebug` once — 1 728 lines of Compose no compiler has read · S
- [ ] **A2** Run `./gradlew :app:lintDebug` and clear what it finds · S
- [ ] **A3** First real Moxfield call — confirm Cloudflare accepts the app's User-Agent · S
- [ ] **A4** Confirm both image CDNs load on a device, Moxfield's and Scryfall's · S
- [ ] **A5** Check the board screen on a small phone — it is the longest layout · S

### B. Product gaps

- [ ] **B1** No undo — nothing brings a board back once it is cleared · M
- [ ] **B2** The deck's card list is not visible anywhere, only its tokens · M
- [ ] **B3** Private and unlisted decks are invisible — needs Moxfield auth, if they allow it · L
- [ ] **B4** The search query is lost if Android kills the process mid-session · S
- [ ] **B5** Nothing caps content width, so the board screen's rows stretch across a tablet · M
- [ ] **B6** Per-token "Vaciar" clears without asking, unlike "Nueva partida" · S

### C. Quality and infrastructure

- [ ] **C1** No CI. Model it on commander-companion's `.github/workflows/android-ci.yml` · S
- [ ] **C2** No `androidTest/`, although `testInstrumentationRunner` is declared · M
- [ ] **C3** Only `values/`. `values-ca` and `values-en` are missing, unlike commander-companion · S
- [ ] **C4** Release build never exercised: R8 off, ProGuard rules unproven, unsigned · M
- [ ] **C5** Launcher icon is a hand-drawn placeholder · S
- [ ] **C6** Two declared dependencies are unused: `okhttp-logging-interceptor`, `ui-tooling-preview` · S
- [ ] **C7** Accessibility never tested. Content descriptions exist; TalkBack has not seen them · S

## 4. Deliberate decisions — not gaps

Don't turn these into tasks without asking. The reasoning is in `CLAUDE.md` §6.

- The token board is in-memory only, and resets with the process.
- Only +1/+1 counters.
- The sideboard doesn't count toward a deck's tokens.
- No backend: the app talks to Moxfield and Scryfall directly.
- No Hilt, and no `material-icons-*` artifacts.

---

**Last reviewed:** 2026-08-25 · 7 commits · 2 954 lines of Kotlin, 1 050 of tests
