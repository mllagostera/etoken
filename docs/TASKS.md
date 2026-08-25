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
| `[?]` | **Waiting on a decision**, not on someone doing it |
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

- [ ] **A1** Get `:app:assembleDebug` green — CI now runs it on every push, so this is iterating on its log · M
- [ ] **A2** Clear what `:app:lintDebug` reports — same run, after the build passes · S
- [ ] **A3** First real Moxfield call — confirm Cloudflare accepts the app's User-Agent · S
  - A runner can answer this without a device: curl both APIs with the exact headers `Network.kt` sends.
- [ ] **A4** Confirm both image CDNs load on a device, Moxfield's and Scryfall's · S
- [ ] **A5** Check the board screen on a small phone — it is the longest layout · S

### B. Product gaps

- [ ] **B1** No undo — nothing brings a board back once it is cleared · M
- [ ] **B2** The deck's card list is not visible anywhere, only its tokens · M
- [ ] **B3** Private and unlisted decks are invisible — needs Moxfield auth, if they allow it · L
- [ ] **B4** The search query is lost if Android kills the process mid-session · S
- [ ] **B5** Two panes on large screens: the token list beside the open board · L
  - **Decided 2026-08-25:** tablets are a realistic target, so option B. Capping the width was the cheap
    alternative and is explicitly not what we are doing.
  - Needs a `WindowSizeClass` split, and the board becoming a pane rather than its own route.
- [ ] **B6** Per-token "Vaciar" clears without asking, unlike "Nueva partida" · S

### C. Quality and infrastructure

- [x] **C1** CI on every branch: build, unit tests, lint, APK artifact — `.github/workflows/android-ci.yml`
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

## 5. Order of work

Priority follows one rule: **nothing here is trustworthy until the build is
green.** Every `[~]` in §2 is a claim, and claims cost nothing to make and
everything to rely on. So anything that moves compilation forward outranks
anything that doesn't, however tempting the feature.

CI changed what that means. Until 2026-08-25 the first compile needed a human
with an Android SDK; now a runner does it on every push, and its log can be
read and acted on without one.

### Tonight, autonomously

1. **A1 — get the build green.** A loop: read the run, fix what it names, push,
   repeat. This is the night's actual work; everything below is what to do
   between runs.
2. **A2 — clear lint**, once the build stops failing ahead of it.
3. **A3 through CI** — a `workflow_dispatch` job that curls Moxfield and
   Scryfall with the exact headers `Network.kt` sends. Answers the Cloudflare
   question without waiting for a device.
4. **C6 — drop the two unused dependencies.** Independent of everything else.
5. **B6 — confirm before "Vaciar"**, reusing the "Nueva partida" pattern.
6. **B4 — keep the search query across process death.**

### Deliberately not tonight

- **B5** is decided and wanted, but it is the largest UI change on the list.
  Building a two-pane layout on code that has never compiled is laying bricks
  on wet concrete. It goes first once the build is green and *stays* green.
- **B1** (undo) needs a design pass — what it restores, and how far back —
  not just code.
- **C3** (Catalan and English strings) is translation work. A bad translation
  is worse than an honest gap.
- **A4, A5** need a real device. They stay with you.

---

**Last reviewed:** 2026-08-25 · 9 commits · 2 954 lines of Kotlin, 1 050 of tests
