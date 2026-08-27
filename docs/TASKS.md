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

## 1. Verified — 128 unit and 36 instrumented tests, 0 failures, green on CI

> Run **#81** is where those figures come from. It is the first to execute the
> precons button's 10 tests (B13) and the summoning-sickness badge's 6 (B14)
> together — #73 had measured neither, #76 only the second — so every test in
> the tree has now run at least once on the tree it ships in.

The logic layer runs on the JVM; the screens run on an emulator in CI. Only
the two APIs are ever faked.

- [x] Moxfield wire contract: search paging, v3 deck, board flattening — `data/moxfield/`, 8 tests
- [x] Art-crop URLs, including the two-faced `card-face-` case — `data/moxfield/MoxfieldImages.kt`
- [x] Scryfall collection envelope and identifier serialization — `data/scryfall/`, 6 tests
- [x] Token extraction from `all_parts`, dedupe, emblems, self-reference — `domain/TokenExtractor.kt`, 9 tests
- [x] Repository: paging, caching, 75-id batching, by-name fallback — `data/MoxfieldRepository.kt`, 11 tests
- [x] Battlefield rules: entries that never merge except at the untap step, split, order of entry,
  clamping — `domain/BoardRules.kt`, 39 tests
- [x] Undo: bounded trail, no-op edits are not steps, an emptied table comes back, and one press of
  add is one step — `domain/UndoHistory.kt`, `data/GameBoardStore.kt`, 14 tests
- [x] Power/toughness with counters, including `*` and `1+*` — `domain/PowerToughness.kt`, 4 tests
- [x] Deck search: accent-blind, commander, multi-word AND — `domain/DeckFilter.kt`, 10 tests
- [x] The whole app compiles: `assembleDebug`, `testDebugUnitTest` and `lintDebug` all green — run #3
- [x] Scryfall's half of the contract, live: `/cards/collection` answers 200 and `all_parts` is there
- [x] Scryfall's image CDN really does answer **400** to OkHttp's default User-Agent, and 200 to a
  descriptive one — the gotcha inherited from commander-companion, now confirmed rather than trusted
- [x] The screens run: a remembered username returns, decks reach the grid and name their commanders,
  search narrows by name and by commander, tapping a deck reports the right one, tokens arrive
  summoning sick and the untap step clears them, a counter turns a 1/1 into a 2/2, and clearing asks
  first — `app/src/androidTest/`, 18 tests on an AVD
- [x] A copy token asks what it is copying, and two copies of different creatures stay two rows —
  `domain/`, `ui/board/`, 3 unit and 4 instrumented tests. The answer now lives on the entry, and
  B15 added the case that two copies of the *same* creature also stay two rows
- [x] The quick filter appears with the first token on the table, hides the rest of the grid, and
  says so instead of going blank when the table empties — `ui/tokens/`, 4 instrumented tests.
  **Retired by B15**: the table is now the screen, so there is nothing left to filter
- [x] The public-decks limit is on screen before the username is typed, and again on an empty deck
  list — `ui/username/`, `ui/decks/`, 2 instrumented tests
- [x] The **real** `MainActivity` starts: the launch theme, `installSplashScreen()` and
  `attachBaseContext` all run, and the app reaches the username screen — `SplashScreenTest`, run #73
  - Every other UI test drives a composable inside a test activity, so until this one nothing in CI
    ever executed `MainActivity.onCreate`. A splash theme the library cannot use, or a
    `postSplashScreenTheme` pointing at nothing, takes the activity down there and nowhere else.
- [x] Printed haste: `keywords` off the wire, `TokenCard.hasHaste`, copies that enter able to
  attack, and a chip that still overrides it — `domain/`, `ui/board/`, 10 unit and 4 instrumented tests
- [x] The grid says how much of a token is still summoning sick: nothing, all of it, or a count —
  `domain/model/Board.kt`, `ui/tokens/`, 5 unit and 1 instrumented test — B14. B15 moved the
  question onto each entry's own cell, where a badge answers for the copies it is drawn on

## 2. Works, never looked at

Everything the user actually touches. An emulator drives all of it on every
push; no person has looked at any of it.

### Screens
- [~] Username entry, remembered in DataStore; insets- and keyboard-aware — `ui/username/`
- [ ] A second button on that screen loading Wizards' Commander precons, no username needed —
  `ui/username/`, `domain/DeckSource.kt` (B13: written, never compiled or run)
- [~] Deck grid with commander art and streaming hydration — `ui/decks/`
- [~] Deck search field with result counter — `ui/decks/DecksScreen.kt`
- [~] Refresh, with a banner that keeps the last good list on failure — `ui/decks/`
- [~] The battlefield, and the picker behind its "+": every entry its own cell, a tap to turn one,
  a long press for the rest — `ui/board/BoardScreen.kt`, `ui/tokens/TokenPicker.kt` (B15, green on
  run **#91**: 23 instrumented tests across the table, the picker, copies, haste and the two panes)
- [~] The picker's cells say how many of each token are already out — `ui/tokens/TokenPicker.kt`
- [~] Summoning sickness is gated on `TokenCard.isCreature`, and every entry can be marked entering
  tapped (a switch in the add dialog, plus a chip to correct it by hand); the untap step clears
  both — `domain/model/Models.kt`, `domain/BoardRules.kt`, `ui/board/`. 9 unit and 5
  instrumented tests, green on CI run **#86**. The first two pushes (#84, #85) each caught a real
  bug before this one did: a bad assertion in a unit test, then a switch whose click action sat on
  the `Switch` alone so tapping its label — what the instrumented tests did — never toggled it
- [~] A "Prisa" badge and one line saying why a hasty token shows no "Mareo" — `ui/board/`
- [~] Every dialog survives rotation, and the ones about an entry hold its id rather than the entry
- [~] New game, behind a confirmation that names what is lost — `ui/board/BoardScreen.kt`
- [~] A splash screen on every version, held while the remembered username is read — `MainActivity`, `res/values/themes.xml`

### Text
- [~] Seven locales: **English default**, plus `es`, `ca`, `fr`, `de`, `it`, `ja` — key parity and XML verified
- [~] A language picker in the corner of the username screen, and the choice remembered — `ui/common/LanguagePicker.kt`
- [~] The note saying private and unlisted decks are not read — `ui/username/`, `ui/decks/`

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
- [ ] **A6** Confirm Scryfall really populates `keywords` on **token** card objects · S
  - The haste rule rests on it, and the app fails quiet if it is wrong: a hasty token would simply
    keep arriving summoning sick, exactly as before B9.
  - `api-smoke.yml` §6 now asks, through Hellion Crucible's 4/4 Hellion, and turns the job red if
    the token comes back without `Haste` among its keywords. Nobody has run it yet — and it does not
    need a device, unlike A3 and A4, only someone pressing the button.

- [ ] **A7** Watch a cold start: the splash, and the handover to the username screen · S
  - What no test can reach — whether the splash is held long enough to be seen and short enough not
    to be waited on, and whether the field is already filled when it appears or fills in visibly.
  - It is also the second thing installing an APK answers for free, after A4 and A5, and wants no
    setup at all: launch the app from the launcher icon rather than from Studio, which is the only
    way to get a real starting window.

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
  - **Said out loud since 2026-08-26**, which is not the same as fixed: the username screen and the
    empty deck list both name the limit. Until auth exists, the failure mode worth avoiding is a user
    reading a missing deck as a broken app.
  - The search endpoint never mentions the decks it is not allowed to show, so an account with only
    private decks is indistinguishable from an empty one. That is why the note lives on the empty
    state rather than being computed from anything.
- [x] **B4** The search query survives process death — it lives in the `SavedStateHandle`
- [x] **B5** Two panes past 840dp — now the picker beside the table, `ui/board/BoardScreen.kt`
  - **Reshaped by B15**, not undone: the threshold, the reason for it and the reading of the
    layout's own constraints are unchanged; what sits in the panes swapped round, since the table
    is the screen now and the deck's tokens are what opens beside it.
  - The split reads the layout's own constraints instead of pulling in `material3-window-size-class`:
    same 840dp threshold, one place in the app asks the question, one fewer artifact in the build.
  - 840 and not 600: a tablet in portrait is around 800dp, and half of that is narrower than the phone
    the board was laid out for. Portrait stays on one pane on purpose.
  - The board is still its own route on a phone. Which arrangement is used is decided from the width,
    not from the navigation graph, so both drive the same view models and the same board store.
- [x] **B6** "Vaciar" asks first and names how many tokens leave the table
  - **Retired by B15.** With one board for the whole table, "clear this token" and "new game" did
    the same thing to within a token type. The confirmation survives on the one that is left.
- [x] **B7** A copy token asks what it is copying — recognised by name, since Scryfall calls them `Copy`
  - The name lives on the **stack**, not the token, and joined the merge signature in
    `TokenBoardRules`: a copy of Krenko and a copy of Atraxa are two stacks, not three tokens in one.
  - In memory like the rest of the board, and cleared by "Nueva partida".
- [x] **B8** A quick filter over the token grid: only what has copies on the battlefield
  - **Retired by B15**, filter and `TokenFilter` alike. The question it answered — "what have I got
    out?" — is now the screen a deck opens onto, and a filter over the picker would hide the tokens
    you came there to make.
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
- [x] **B9** Tokens printed with haste arrive able to attack
  - Done as written: `ScryfallCard.keywords` off the wire, `TokenCard.hasHaste` off that list, and
    `TokenBoardRules.add` taking `entersSick` instead of assuming it. No rules-text parsing — a
    token reading "Other creatures you control have haste" has none itself, and only the structured
    list tells those two apart.
  - The parameter defaults to `true`, so every caller that has no token in hand still gets the
    rule that holds for almost every token. The board screen is the one that knows, and it asks
    the token.
  - The manual chip is untouched, and the board says "Prisa" with a line of explanation: printed
    haste is the only half of this the app can ever know, so an absent "Mareo" had to stop looking
    like a bug. Haste handed out by another permanent is still the player's to mark.
  - The assumption underneath — that Scryfall populates `keywords` on token card objects — is now
    **A6**: `api-smoke.yml` §6 asks it and fails if the answer is no. It could not be asked from
    here, where the proxy blocks Scryfall with 403.
- [x] **B10** The grid's badge names the +1/+1 counters when the board has a single stack
  - **Retired by B15**, and by the thing it was working around: one badge had to speak for a whole
    token, so it could only speak when the token had one answer. Each entry now draws its own
    counters, so the case that made `uniformPlusOneCounters` necessary cannot arise.
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
  - **Run first, green second.** #54 and #57 never produced a log — a runner that never arrived and
    a `startup_failure` — so the first run to execute this was #64, on main, after the merge. It
    failed, and so did the Hellion assertion B9 had added: three of the thirty instrumented tests.
    None of them for a reason in the app. CI's emulator is the default AVD, 320x640dp, where
    `GridCells.Adaptive(150.dp)` gives a single column of cells ~480dp tall: only two rows are
    composed, and the filter chip's 48dp is enough to push the second out. The tests now scroll to a
    cell before asserting on it. #65 runs all thirty green, `TokensScreen.kt` included.

- [~] **B11** A language picker, and English as the default locale
  - Two things at once, and only one of them is the switch. `values/` was Spanish, which meant a
    phone set to a language the app does not ship — Portuguese, Polish, anything — fell back to
    Spanish. It falls back to English now. A phone in Spanish is untouched: `values-es/` answers
    first, and `values-en/` is gone because English *is* the default.
  - The picker sits in the top corner of the **username screen**, which is the one screen every
    launch passes through and the only one where a screen-level control does not compete with the
    game. Not a settings screen: there is one setting, and a route for it would be a screen the app
    would then have to explain.
  - Each language names itself — `Español`, `日本語` — and the entries are deliberately untranslated.
    A picker exists for the person who cannot read the language they are stuck in; "Spanish" in
    Japanese is no help to them.
  - `SharedPreferences`, not the DataStore holding the username. The locale must be applied in
    `attachBaseContext`, which runs before `onCreate` with nothing to suspend in, and DataStore can
    only be read from a coroutine. Applying it means recreating the activity: `Resources` are built
    from that context once and no recomposition reaches them.
  - Android 13's per-app language setting does the same job and was not used: `minSdk` is 26, and
    on Android 8 through 12 that setting does not exist.
  - **The half nobody can test is the half that matters.** The dialog and the stored choice are
    covered — 2 instrumented tests — but nothing asserts on the result of applying a language, so
    nothing in CI can tell you whether picking Japanese actually redraws the app in Japanese.
    B12's `SplashScreenTest` narrowed this without closing it: it launches the real activity, so
    `attachBaseContext` does now run in CI and can no longer throw unnoticed. That needs the APK on a device, and it joins A5 in wanting one. `AppLanguageTest`
    covers the part that can be: 6 unit tests, including one that fails if a language is offered
    with no `values-` folder behind it.

- [~] **B12** A splash screen, and the same one on every version the app supports
  - Android 12 draws a splash from the launcher icon whether an app asks or not, so on half the
    supported versions this was never a question of whether to have one — only of whether it was
    chosen. Below 12 there was no splash at all: a blank window in the theme's background colour
    until the first frame. `androidx.core:core-splashscreen` is what makes those two the same
    screen, and it is the reason the launch theme is `Theme.Etoken.Splash` in the manifest and
    `installSplashScreen()` is the first line of `onCreate` — that call is what swaps it for
    `Theme.Etoken`, and without it the app would wear the splash theme and never draw.
  - The mark is the launcher icon's, redrawn at 1.5x as `ic_splash_logo`. Reusing
    `ic_launcher_foreground` was the obvious move and is wrong: an adaptive foreground keeps its
    content inside the middle 2/3 of the canvas, which is the same safe zone the platform sizes a
    splash icon against, so the mark would arrive at a third of the size it should be.
  - The background is the launcher icon's background, in light and dark alike. Not an oversight:
    the mark is a pale purple and an amber picked to sit on `#1C1A21`, and neither has any contrast
    left on white.
  - It is **held** until DataStore answers with the remembered username, which is the one thing the
    first screen shows and cannot show synchronously. Bounded at a second — a splash that never
    leaves is a worse bug than an empty field, and an empty field is what a first-time user sees
    anyway. Cold start only: a language change applies itself by recreating the activity, and
    splashing again there would read as the app restarting.
  - **Green on run #73**, which is also what proved `androidx.core:core-splashscreen:1.0.1`
    resolves: the machine this was written on has no Android SDK and cannot reach Google's Maven,
    so the coordinate was a guess until the build ran.
  - One instrumented test, and it is the first in the suite to launch the real `MainActivity` —
    every other one drives a composable inside a test activity, which is why B11's
    `attachBaseContext` hole exists. It fails on a launch theme the library cannot use. It cannot
    fail on a splash that hangs: the condition holds *drawing*, and an undrawn composable still
    reports itself displayed to the semantics tree. That half is A7.

- [~] **B13** A way in for someone with no Moxfield account: Wizards' Commander precons
  - One button on the username screen, and no new screen behind it. The precons are the same
    `v2/decks/search-sfw` call the app already makes, filtered to `authorUserNames=WizardsOfTheCoast`
    **and** `fmt=commanderPrecons` — Wizards publishes far more than precons under that account, so
    the author alone would list a few thousand decks of every format.
  - `DeckSource` in `domain/` is what carries the pair, so the route, the repository call and the
    screen title all read one answer instead of each spelling the filter out again.
    `MoxfieldRepository.listDecks` takes it in place of a bare username.
  - The deck screen titles that listing by what it is, not by who published it: "WizardsOfTheCoast"
    is where precons live, not something a user typed. Its empty state differs too — nothing there
    is private, so an empty answer means the filter moved rather than that decks are being withheld.
  - **Green on run #81**, the first to compile or execute any of it: eight unit tests and two
    instrumented. The unverifiable half is untouched by that — `fmt=commanderPrecons` itself.
    Moxfield documents nothing, and only a live request can say whether that is still the format's
    name. If it has moved, the symptom is an empty grid with the precon empty state, and
    `DeckSource.PRECON_FORMAT` is the one line to change.

- [~] **B14** The grid's badge says how much of a token is still summoning sick
  - **Reshaped by B15**: same question, asked per entry, where the answer is always all or nothing
    and needs no count. `SummoningSickness` stays for the table as a whole.
  - Same question as B10, asked about the other half of a stack's state: mid-turn what a player
    needs is "what have I got out, and can it attack?" The count and the counters were on the cell;
    whether the copies were still waiting meant opening the board.
  - Three cases, not a number — `TokenBoard.summoningSickness`. Nothing waiting draws no badge at
    all, which is what an untouched cell already looks like; a table where every copy is waiting is
    named without a count, since the number would only repeat the ×N in the opposite corner; and a
    part-waiting table is the one case where the figure earns its room.
  - Zero of zero is `None`, not `All`: a sickness badge on a token with nothing in play would be the
    worst of the three answers, so the empty case is decided before the "all of them" one.
  - The word is `stack_sick`, the board screen's own, so one state cannot end up with two names. The
    only new string is `tokens_sick_some` (`%1$s ×%2$d`), which is the same pattern in all seven
    locales.
  - The two bottom badges sit in halves of the cell rather than at their own corners: at ~150dp wide
    and with a label that is a full word in every language — `Einsatzverzögerung` is 19 characters —
    corner alignment lets them meet in the middle. A half each truncates instead of overlapping. That
    is a layout claim nobody has looked at, and it belongs to C8.
  - **Green on runs #76 and #81**, the emulator suite included: the badge appears when copies
    arrive, names a count once part of the table can attack, and goes when the untap step clears
    the rest.

- [~] **B15** The table is the screen, and the "+" is what adds to it
  - Asked for on 2026-08-27, and it inverts the app: a deck used to open onto a grid of every token
    it could create, with one token's board a tap further in. What a player looks at between turns
    is the table, so the table is what a deck opens onto now, and the deck's tokens live behind a
    "+" — a modal sheet on a phone, the left-hand pane past 840dp.
  - **Each press of add is its own entry, and nothing merges.** That is the whole of the model
    change. Quantity stays on the entry — "make three Goblins" is one thing that happened, and
    splitting it into three cells would be as much of a lie as merging it into somebody else's —
    but two entries that come to look alike stay two cells for the rest of the game. `normalize`
    keeps only the half of its job that was never in question, dropping what emptied, and does no
    re-ordering either: an entry that jumped up the grid because you put a counter on it is an
    entry you then have to hunt for.
  - One board for the whole game rather than one per token, so `GameBoardStore` holds a single
    `GameBoard` and undo goes on being a snapshot of it. Adding an entry of seven is one step.
  - The store also remembers what each token looks like. An entry names its token by id, the deck
    on screen is not always the deck that entry came from, and the alternative was a table that
    silently hid what another deck put on it. That catalog sits outside the undo trail on purpose.
  - A tap turns an entry; a long press opens counters, sickness, the count and taking it off the
    table. Tapping and untapping is what a table asks for most, and it wanted to be one gesture —
    the repo owner's call, against a detail sheet on every tap.
  - `TokenFilter` and the per-token "Vaciar" go (B8, B6), and `stack_*` becomes `entry_*` across
    seven locales. Two view models become one: the old pair existed only because the grid and the
    board were separate destinations.
  - **Green on run #91**, third attempt, and both failures before it were in the tests rather than
    the app. #89 died compiling them, on an import of `onAllNodes` — a member of the test rule, not
    an extension. #90 then failed every board test on its first line, waiting for the button that
    opens the picker: `ExtendedFloatingActionButton`'s label does not survive into the merged
    semantics tree, so the button said nothing to the tests *or* to TalkBack. It is a plain
    `FloatingActionButton` with an icon and a description now, like every other action here, which
    is an accessibility fix that the emulator happened to find.
  - It joins the C8 list: the entry cell is a new layout with badges in it, in seven languages, and
    nobody has looked at any of them.

- [~] **B16** Tapping asks how many, everywhere; the untap step joins the table back up
  - Two follow-ups to B15 from the repo owner, and they pull in opposite directions on purpose.
  - **Tapping some.** A tap on an entry of one still just turns it. On an entry holding more, the
    screen asks how many — "All (6)" is one press, a smaller number splits the entry so the copies
    that were tapped are a cell of their own. That is the case the table actually reaches: three of
    six Goblins tapped for mana leaves three that can still attack, and they are not the same three.
    Untapping asks the same question the other way round.
  - **The untap step merges.** Two tapped, two ready and two summoning sick are three cells while
    those states differ, and one cell of six the moment a turn resets them. This is the one place
    that merges and it is not an exception to the no-merge rule — it is what the rule is for: the
    untap step erases exactly the differences that kept those entries apart, so keeping them apart
    afterwards is bookkeeping about a distinction that no longer exists. What a turn does not reset
    still tells entries apart: a different token, different +1/+1 counters, a different creature
    being copied.
  - "My turn begins" is offered whenever there is a table, rather than only when something is
    summoning sick: it now has three jobs and the row knew about one.
  - Every edit that can land on part of an entry now asks the same way, not just the tap on a
    cell: the tapped chip, the summoning-sickness chip and the +1/+1 stepper in the detail sheet
    all go through one `EntryAsk`, which also decides when there is nothing to ask. That retired
    the "+1/+1 on just some…" button — it existed only because the stepper could speak for a whole
    entry and nothing else, and it had become the same question in a second place.
  - **Green on runs #93 and #96.** The rules went in first and passed on the first try: four unit
    tests and two instrumented, including the owner's own example — two tapped, two ready and two
    sick becoming one entry of six, with a Hellion beside them that stays its own cell because a
    different token is not a difference a turn erases. Spreading the question to the chips and the
    stepper cost one red run, and it was the test's fault rather than the app's: with the detail
    sheet open there are two nodes saying "Sick", the cell's badge and the chip over it, and the
    finder has to say which window it means. That is now a named step in the robot, `inSheet`.

### C. Quality and infrastructure

- [x] **C1** CI on every branch: build, unit tests, lint, APK artifact — `.github/workflows/android-ci.yml`
- [x] **C2** 30 instrumented tests drive the real screens on an emulator in CI — `.github/workflows/android-ci.yml`
  - The AVD is `avdmanager create avd` with no `--device`: a 320x640dp screen, which is a narrower
    phone than any the app will meet. That is worth keeping — it caught three assertions that only
    held on a taller screen — but it means an assertion about a lazy list has to scroll to its item
    first. A cell below the fold is not off screen, it is never composed, and absent from the
    semantics tree: `assertExists` fails on it as surely as `assertIsDisplayed` would.
- [x] **C3** Seven locales: English default plus `es`, `ca`, `fr`, `de`, `it`, `ja`, with Magic's own terminology
- [ ] **C4** Release build never exercised: R8 off, ProGuard rules unproven, unsigned · M
- [ ] **C5** Launcher icon is a hand-drawn placeholder · S
  - Now in two places: B12's splash draws the same mark, scaled up. Replacing the icon means
    replacing `ic_splash_logo` in the same change, or the launcher and the splash stop agreeing.
- [x] **C6** `ui-tooling-preview` dropped; the logging interceptor is now wired, debug builds only
- [x] **C10** One debug key for every build, so an APK from CI installs over the last one
  - CI signed with AGP's auto-generated `~/.android/debug.keystore`, which a GitHub runner does not
    have and therefore creates fresh — a different key on each run. Android refuses an update whose
    signature has changed, so each APK could only be installed by uninstalling the previous one.
  - `app/debug.keystore` is committed and wired as `signingConfigs.debug`. It is not a secret and is
    not treated as one: the credentials are Android's own published debug ones. C4's release signing
    is a separate question and deliberately untouched.
  - `versionCode` is now `GITHUB_RUN_NUMBER`, 1 outside CI. It costs a Kotlin recompile per run —
    `BuildConfig` carries the version, so its task stops hitting the build cache — and buys an APK
    the phone can order against the one already on it.
- [ ] **C7** Accessibility never tested. Content descriptions exist; TalkBack has not seen them · S
- [ ] **C8** Six of the seven locales have never been rendered, and now neither has the picker · S
  - Lint is satisfied and the keys line up, which says nothing about layout. `Einsatzverzögerung`
    is 19 characters in a badge sized for `Mareo`, and Japanese breaks lines by rules Latin text does not.
  - `board_haste_note` joins that list and is the longest string in the app: a full sentence in the
    narrow column beside the token's artwork, in seven languages.
  - B11 makes this cheap to do properly: the languages are now two taps apart inside the app rather
    than a trip through the phone's settings, so one pass through the screens covers all seven.
    Look at the picker itself while you are there — eight rows in a dialog on a 320x640dp screen is
    exactly the shape of thing that scrolls when nobody expected it to.
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

1. **A4, A5 and A7, and they need you.** Install the APK from the latest green run:
   Actions → the run → `etoken-debug-apk-<run number>` at the bottom, unzipped.
   Everything below is guesswork ranked against an app no one has used.
2. **A3 resolves itself from that.** If Moxfield answers on a phone, the 403
   was the runner's datacenter IP and there is nothing to fix. If it does not,
   `Network.kt`'s headers are the work, and they are the part of the inherited
   contract most likely to have moved.
3. **C8** — look at the locales while you are there. It costs one pass through
   the screens per language, and B11 made that two taps inside the app rather
   than a trip through the phone's settings. It is also the only way to find
   out whether the switch works at all: nothing in CI runs the code that
   applies it.
4. The rest of C is housekeeping, in any order.

Nothing here is blocked on anything I can do without a device.

---

**Last reviewed:** 2026-08-27 · run **#96** green including the emulator: 131 unit tests and 40 instrumented, lint clean · Scryfall verified live, Moxfield 403 from CI · B15 rebuilt the battlefield in this run's tree, so every figure above is measured on the app as it now stands — what nobody has done is *look* at it
