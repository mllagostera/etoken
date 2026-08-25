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
| `[~]` | Compiles, passes lint and unit tests, but **has never been displayed on a screen** |
| `[?]` | **Waiting on a decision**, not on someone doing it |
| `[ ]` | Not started |

`[~]` covers most of the app. It used to mean "no compiler has ever read this";
since 2026-08-25 CI builds every push, so it now means the narrower and much
better thing: the code is valid, lint is clean, and the tests pass — but nobody
has watched a screen do its job. A4 and A5 are what close that gap.

---

## 1. Verified — 65 unit tests, 0 failures, green on CI

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
- [x] The whole app compiles: `assembleDebug`, `testDebugUnitTest` and `lintDebug` all green — run #3
- [x] Scryfall's half of the contract, live: `/cards/collection` answers 200 and `all_parts` is there
- [x] Scryfall's image CDN really does answer **400** to OkHttp's default User-Agent, and 200 to a
  descriptive one — the gotcha inherited from commander-companion, now confirmed rather than trusted

## 2. Compiles, never seen running

Everything the user actually touches. It builds and lints clean; none of it
has been seen on a screen.

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

A1 and A2 fell on 2026-08-25. What is left needs a real device or a real network.

- [x] **A1** `:app:assembleDebug` green — two AGP 9 config errors and one missing supertype, all fixed
- [x] **A2** `:app:lintDebug` clean — nothing to clear, it passed first time
- [ ] **A3** Moxfield answers **403** to the app's headers from a CI runner · M
  - Run via `.github/workflows/api-smoke.yml`, 2026-08-25. Inconclusive rather than damning: a GitHub
    runner is a datacenter IP, which Cloudflare treats far more harshly than a phone on mobile data.
  - Settling it needs the APK on a real device. If it 403s there too, `Network.kt`'s headers are the place to look.
- [ ] **A4** Confirm Moxfield's image CDN loads on a device · S — Scryfall's is confirmed, see §1
- [ ] **A5** Check the board screen on a small phone — it is the longest layout · S

### B. Product gaps

- [ ] **B1** No undo — nothing brings a board back once it is cleared · M
- [ ] **B2** The deck's card list is not visible anywhere, only its tokens · M
- [ ] **B3** Private and unlisted decks are invisible — needs Moxfield auth, if they allow it · L
- [x] **B4** The search query survives process death — it lives in the `SavedStateHandle`
- [ ] **B5** Two panes on large screens: the token list beside the open board · L
  - **Decided 2026-08-25:** tablets are a realistic target, so option B. Capping the width was the cheap
    alternative and is explicitly not what we are doing.
  - Needs a `WindowSizeClass` split, and the board becoming a pane rather than its own route.
- [x] **B6** "Vaciar" asks first and names how many tokens leave the table

### C. Quality and infrastructure

- [x] **C1** CI on every branch: build, unit tests, lint, APK artifact — `.github/workflows/android-ci.yml`
- [ ] **C2** No `androidTest/`, although `testInstrumentationRunner` is declared · M
- [x] **C3** Seven locales: Spanish default plus `ca`, `en`, `fr`, `de`, `it`, `ja`, with Magic's own terminology
- [ ] **C4** Release build never exercised: R8 off, ProGuard rules unproven, unsigned · M
- [ ] **C5** Launcher icon is a hand-drawn placeholder · S
- [x] **C6** `ui-tooling-preview` dropped; the logging interceptor is now wired, debug builds only
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

**Last reviewed:** 2026-08-25 · 15 commits · CI green · Scryfall verified live, Moxfield 403 from CI
