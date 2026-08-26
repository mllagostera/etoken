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
| `[~]` | Driven by instrumented tests on an emulator, but **never looked at by a person** |
| `[?]` | **Waiting on a decision**, not on someone doing it |
| `[ ]` | Not started |

`[~]` has narrowed twice. It began as "no compiler has ever read this"; CI
build made it "the code is valid"; and since 2026-08-26 an emulator drives the
real screens on every push, so it now means the screens *work* — they render,
they respond, and the state behind them moves correctly. What is left is
perceptual: nobody has looked at whether any of it is legible, well spaced, or
not truncated. A4, A5 and C8 are that gap.

---

## 1. Verified — 94 unit and 22 instrumented tests, 0 failures, green on CI

The logic layer runs on the JVM; the screens run on an emulator in CI. Only
the two APIs are ever faked.

- [x] Moxfield wire contract: search paging, v3 deck, board flattening — `data/moxfield/`, 8 tests
- [x] Art-crop URLs, including the two-faced `card-face-` case — `data/moxfield/MoxfieldImages.kt`
- [x] Scryfall collection envelope and identifier serialization — `data/scryfall/`, 6 tests
- [x] Token extraction from `all_parts`, dedupe, emblems, self-reference — `domain/TokenExtractor.kt`, 9 tests
- [x] Repository: paging, caching, 75-id batching, by-name fallback — `data/MoxfieldRepository.kt`, 11 tests
- [x] Battlefield rules: stacks, split, merge, ordering, clamping — `domain/TokenBoardRules.kt`, 24 tests
- [x] Undo: bounded trail, no-op edits are not steps, a cleared board and a new game both come
  back — `domain/UndoHistory.kt`, `data/TokenBoardStore.kt`, 13 tests
- [x] Power/toughness with counters, including `*` and `1+*` — `domain/PowerToughness.kt`, 4 tests
- [x] Deck search: accent-blind, commander, multi-word AND — `domain/DeckFilter.kt`, 10 tests
- [x] Token quick filter: what counts as in play, deck order kept, another deck's board ignored —
  `domain/TokenFilter.kt`, 6 tests
- [x] The whole app compiles: `assembleDebug`, `testDebugUnitTest` and `lintDebug` all green — run #3
- [x] Scryfall's half of the contract, live: `/cards/collection` answers 200 and `all_parts` is there
- [x] Scryfall's image CDN really does answer **400** to OkHttp's default User-Agent, and 200 to a
  descriptive one — the gotcha inherited from commander-companion, now confirmed rather than trusted
- [x] The screens run: a remembered username returns, decks reach the grid and name their commanders,
  search narrows by name and by commander, tapping a deck reports the right one, tokens arrive
  summoning sick and the untap step clears them, a counter turns a 1/1 into a 2/2, and clearing asks
  first — `app/src/androidTest/`, 18 tests on an AVD
- [x] A copy token asks what it is copying, keeps the answer on the stack, and two copies of
  different creatures stay two rows — `domain/`, `ui/board/`, 3 unit and 4 instrumented tests
- [x] The quick filter appears with the first token on the table, hides the rest of the grid, and
  says so instead of going blank when the table empties — `ui/tokens/`, 4 instrumented tests

## 2. Works, never looked at

Everything the user actually touches. An emulator drives all of it on every
push; no person has looked at any of it.

### Screens
- [~] Username entry, remembered in DataStore; insets- and keyboard-aware — `ui/username/`
- [~] Deck grid with commander art and streaming hydration — `ui/decks/`
- [~] Deck search field with result counter — `ui/decks/DecksScreen.kt`
- [~] Refresh, with a banner that keeps the last good list on failure — `ui/decks/`
- [~] Token grid with in-play badges, +1/+1 counters included while there is one stack — `ui/tokens/`
- [~] Quick filter chip: only the tokens with copies on the table — `ui/tokens/TokensScreen.kt`
- [~] Token board: quantity, summoning sickness, +1/+1 counters — `ui/board/`
- [~] Both destructive actions confirm and name what is lost — `ui/board/`, `ui/tokens/`
- [~] Every dialog survives rotation, and the counters one holds a stack id rather than a stack
- [~] New game, behind a confirmation that names what is lost — `ui/tokens/TokensScreen.kt`

### Text
- [~] Seven locales: Spanish default, plus `ca`, `en`, `fr`, `de`, `it`, `ja` — key parity and XML verified

### Build
- [~] Gradle setup, catalog aligned with commander-companion's `android/`
- [~] Manual DI in `AppContainer`, no Hilt
- [~] Coil `ImageLoader` with the User-Agent Scryfall's CDN demands
- [x] `api-smoke.yml`: walks the real APIs with the app's own headers, on demand
- [x] Every green run publishes an installable debug APK of its own, kept 90 days — `android-ci.yml`

## 3. Pending

### A. Verification — blocking

A1 and A2 fell on 2026-08-25. What is left needs a real device or a real network.

- [x] **A1** `:app:assembleDebug` green — two AGP 9 config errors and one missing supertype, all fixed
- [x] **A2** `:app:lintDebug` clean — nothing to clear, it passed first time
- [ ] **A3** Moxfield answers **403** to the app's headers from a CI runner · M
  - Run via `.github/workflows/api-smoke.yml`, 2026-08-25. Inconclusive rather than damning: a GitHub
    runner is a datacenter IP, which Cloudflare treats far more harshly than a phone on mobile data.
  - Settling it needs the APK on a real device. If it 403s there too, `Network.kt`'s headers are the place to look.
  - C9 is the same question asked the other way round, and stays red until this one is answered.
- [ ] **A4** Confirm Moxfield's image CDN loads on a device · S — Scryfall's is confirmed, see §1
- [ ] **A5** Look at the board screen on a small phone — it is the longest layout · S
  - The emulator proves it *works*; it says nothing about whether it fits.
  - The same goes for the two-pane layout: it is driven by tests at 1280dp, but 1280dp is wider than
    the AVD's screen, so nobody has seen the two panes side by side.

### B. Product gaps

- [x] **B1** Undo, over every board at once — an action in both top bars
  - **Decided 2026-08-26**, which is the design pass this was waiting on. One trail for the whole
    store, not one per token: a new game empties every board, and a per-token trail could not put
    that back. It also makes undo mean one thing wherever it is pressed.
  - Covers every edit, not only the destructive ones — "I tapped -1 on the wrong row" is the mistake
    that actually happens at a table.
  - Bounded at 20 steps, and an edit that changed nothing is not a step. No redo: out of scope for a
    play aid, and nothing in the app hints at one.
- [ ] **B2** The deck's card list is not visible anywhere, only its tokens · M
- [ ] **B3** Private and unlisted decks are invisible — needs Moxfield auth, if they allow it · L
- [x] **B4** The search query survives process death — it lives in the `SavedStateHandle`
- [x] **B5** Two panes past 840dp: the token grid beside the open board — `ui/TokensAndBoard.kt`
  - The split reads the layout's own constraints instead of pulling in `material3-window-size-class`:
    same 840dp threshold, one place in the app asks the question, one fewer artifact in the build.
  - 840 and not 600: a tablet in portrait is around 800dp, and half of that is narrower than the phone
    the board was laid out for. Portrait stays on one pane on purpose.
  - The board is still its own route on a phone. Which arrangement is used is decided from the width,
    not from the navigation graph, so both drive the same view models and the same board store.
- [x] **B6** "Vaciar" asks first and names how many tokens leave the table
- [x] **B7** A copy token asks what it is copying — recognised by name, since Scryfall calls them `Copy`
  - The name lives on the **stack**, not the token, and joined the merge signature in
    `TokenBoardRules`: a copy of Krenko and a copy of Atraxa are two stacks, not three tokens in one.
  - In memory like the rest of the board, and cleared by "Nueva partida".
- [x] **B8** A quick filter over the token grid: only what has copies on the battlefield
  - A deck that makes a dozen tokens shows two or three of them at a time in a real game. Mid-turn
    the question is "what have I got out?", and the answer was spread across a grid of mostly empty
    cells.
  - One chip rather than a search field: the grid is short, and the only cut worth making quickly is
    the one the badges already draw. It reads the same boards those badges do, so what the filter
    keeps and what carries a badge cannot disagree.
  - The chip only appears once something is in play — and stays while it is on, so emptying the table
    cannot strand a grid filtered down to nothing. In that case the grid says the table is empty
    rather than going blank.
  - Left alone by "Nueva partida" and by undo on purpose: the filter is the user's, not the board's.
  - In the `SavedStateHandle` like the deck search, for the same reason: coming back from a killed
    process to a grid that had quietly un-filtered itself reads as having lost your place.
- [ ] **B9** Tokens printed with haste still arrive summoning sick · S
  - `TokenBoardRules.add` sets `summoningSick = true` unconditionally, so a Dragon with haste has
    to be corrected by hand with the per-stack chip every single time it enters. The word "haste"
    appears nowhere in the code: this is missing, not half-done.
  - The fix that fits the repo: ask Scryfall for **`keywords`** -- the structured list, which
    `ScryfallCard` does not request today -- expose it as `TokenCard.hasHaste`, and let `add` take
    whether the copies enter sick instead of assuming it. **No rules-text parsing**, per CLAUDE.md
    §5; `oracleText` is kept for dedupe and display and should stay that way.
  - The manual chip stays whatever happens. Haste handed out by another permanent -- Goblin
    Chieftain, Anger -- is table state the app cannot know, so printed haste is the only half of
    this that can ever be automatic. Worth saying on the board screen so an absent "Mareo" does
    not read as a bug.
  - **Unverified assumption**: that Scryfall populates `keywords` on token card objects. It could
    not be checked from the environment this was written in, which the proxy blocks with 403.
    `api-smoke.yml` is where to settle it before the rule is trusted.
- [x] **B10** The grid's badge names the +1/+1 counters when the board has a single stack
  - Mid-turn the question a badge should answer is "what have I got out, and how big is it?" The
    count was there; the counters meant opening the board to find out.
  - Only with **one stack**, and that is the whole rule — `TokenBoard.uniformPlusOneCounters`, null
    when the board cannot answer with one number. Seven Goblins of which three grew is two answers,
    and a badge that showed either would be lying about the other half of the table.
  - Zero counters draws nothing: it is what an untouched token already looks like.
  - It reuses `stack_counters_chip` (`+1/+1 ×%1$d`), already translated in all seven locales, so
    nothing new needed a translation.
  - The screen now reads the boards themselves rather than a map of totals, which is also what
    `TokenFilter` takes — one source for the filter and the badges, as B8 intended.
  - **Not yet run anywhere**: the environment this was written in has no Android SDK, and the first
    CI run for it (#54) sat in the queue without a runner and was cancelled before executing a step.
    Five unit tests and two instrumented ones cover it on paper. A green run is what settles it.

### C. Quality and infrastructure

- [x] **C1** CI on every branch: build, unit tests, lint, APK artifact — `.github/workflows/android-ci.yml`
- [x] **C2** 22 instrumented tests drive the real screens on an emulator in CI — `.github/workflows/android-ci.yml`
  - B10 adds 2 more, which no run has executed yet: see its note above.
- [x] **C3** Seven locales: Spanish default plus `ca`, `en`, `fr`, `de`, `it`, `ja`, with Magic's own terminology
- [ ] **C4** Release build never exercised: R8 off, ProGuard rules unproven, unsigned · M
- [ ] **C5** Launcher icon is a hand-drawn placeholder · S
- [x] **C6** `ui-tooling-preview` dropped; the logging interceptor is now wired, debug builds only
- [ ] **C7** Accessibility never tested. Content descriptions exist; TalkBack has not seen them · S
- [ ] **C8** The six new locales have never been rendered · S
  - Lint is satisfied and the keys line up, which says nothing about layout. `Einsatzverzögerung`
    is 19 characters in a badge sized for `Mareo`, and Japanese breaks lines by rules Latin text does not.
- [ ] **C9** An end-to-end test in CI against the real Moxfield user `vansid` · M — **blocked by A3**
  - One test walking the whole path with nothing faked: `vansid` → the deck list → one deck → its
    tokens with images. That account has more than enough decks to exercise search paging, the v3
    deck fetch, commander art, and a token set worth extracting.
  - Everything green today is green against fakes. `api-smoke.yml` touches the real APIs but only
    asks whether they answer; this asks whether the app's own repository can carry a real account
    from a username to a screenful of tokens.
  - It cannot pass until A3 does: Moxfield answers 403 to a GitHub runner's datacenter IP. The
    answer to that is a real device or asking Moxfield for API access — **not** hunting for a header
    combination that gets past Cloudflare, which is a protection they put there deliberately.
  - So build it now and let it stay red, or leave it written down until A3 falls. Either way it wants
    its own workflow rather than a place in `android-ci.yml`: a test that depends on a third party's
    availability must never be what turns a pull request red.
  - Assert on shape, not on contents: deck count > 0, every deck named, some deck yielding tokens.
    `vansid` can rename a deck tomorrow and no test should care.

## 4. Deliberate decisions — not gaps

Don't turn these into tasks without asking. The reasoning is in `CLAUDE.md` §6.

- The token board is in-memory only, and resets with the process.
- Only +1/+1 counters.
- The sideboard doesn't count toward a deck's tokens.
- No backend: the app talks to Moxfield and Scryfall directly.
- No Hilt, and no `material-icons-*` artifacts.

## 5. Order of work

The rule has moved on. It used to be "nothing is trustworthy until the build is
green"; the build is green, so the floor is raised — the code is valid, lint is
clean, the tests pass. But every `[~]` in §2 is still a claim about behaviour
**nobody has watched happen**, and that is now the only thing standing between
this and a working app.

1. **A4 and A5, and they need you.** Install the APK from the latest green run:
   Actions → the run → `etoken-debug-apk-<run number>` at the bottom, unzipped.
   Everything below is guesswork ranked against an app no one has used.
2. **A3 resolves itself from that.** If Moxfield answers on a phone, the 403
   was the runner's datacenter IP and there is nothing to fix. If it does not,
   `Network.kt`'s headers are the work, and they are the part of the inherited
   contract most likely to have moved.
3. **C8** — look at the six locales while you are there. It costs one pass
   through the screens with the phone's language changed.
4. The rest of C is housekeeping, in any order.

Nothing here is blocked on anything I can do without a device.

---

**Last reviewed:** 2026-08-26 · 41 commits · CI green including the emulator · Scryfall verified live, Moxfield 403 from CI
