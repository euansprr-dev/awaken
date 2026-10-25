# M2 · Sonnet 1: the opening's GameTests

You write the GameTests for Carillon's opening (Milestone 2). The Director (Opus) wrote the module; you test it.

## Where you work
- Worktree `/Volumes/MEDIA/Awaken-opening`, branch `carillon/m2-opening`. Work ONLY there. Never call EnterWorktree.
  Start every Bash command with `cd /Volumes/MEDIA/Awaken-opening &&`, use `git -C /Volumes/MEDIA/Awaken-opening`,
  absolute paths in file tools. Never touch any other worktree.
- You change ONE file: `src/main/java/dev/awaken/opening/OpeningTests.java` (it is a stub now). If a test needs a seam
  that doesn't exist, don't add it: write what you need in your final report and leave that test as close as you can.
- Read first: `CLAUDE.md`, `docs/carillon/briefs/RULES.md`, then the module: `src/main/java/dev/awaken/opening/`
  (OpeningDirector is the timeline, OpeningModule the wiring, OpeningRegistry the Registry's steps, OpeningPlaces the
  places, OpeningState/OpeningWorld the saved state), `src/main/java/dev/awaken/story/Story.java` (the story's API the
  opening uses) and `StoryProgress.java` (the beats: SKY, RUN, ARRIVAL, then the story's GATE_OPEN/RITUAL/ACCEPTING,
  then WAKE, REGISTRY, DONE). For test patterns read `src/main/java/dev/awaken/story/RitualTests.java`,
  `TogetherTests.java`, `src/main/java/dev/awaken/city/CityTests.java` (`layoutAt`) and
  `src/main/java/dev/awaken/core/test/AwakenTests.java` (`register`).
- 26.3 is not 1.21.1: verify every Minecraft/NeoForge API by grepping `/Volumes/MEDIA/Awaken/.ref/src` and
  `/Volumes/MEDIA/Awaken/.ref/neo` before using it. Copy what the existing tests do where you can.

## Building and running
- Compile: `cd /Volumes/MEDIA/Awaken-opening && SLOT_WHO=M2-sonnet-1 /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 600 compileJava`
- Your tests only: `cd /Volumes/MEDIA/Awaken-opening && SLOT_WHO=M2-sonnet-1 /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 1200 runGameTestServer -Ptests=opening_`
  Run these in the background (`run_in_background`) and wait for the notification; never `tail -f`. Never run the full
  suite, never `runClient`, never a tour.
- Commit when your tests pass, one commit, message ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Don't push.

## The seams (already in the code, package-private: your tests live in the same package)
- `OpeningPlaces.testLayout`: a `CityLayout` placed in the test level (build it like `CityTests.layoutAt`, but centred on
  your test's own position and with `streetY` = your test floor's absolute y, so the Roost and the square are near:
  `new CityLayout(CityPlan.get(), new CitySite(x, z, CityBasin.CORE_RADIUS, CityBasin.BLEND_RADIUS, streetY, river,
  CityBasin.RIVER_HALF_WIDTH, CityBasin.RIVER_FLOOR_Y, true))`). With it set, `OpeningPlaces.get()` is that city.
- `OpeningStage.enabled = false`: the stage (the Association's rooms, the run's furniture, the Bell Bridge's fall) writes
  no blocks. Tests share one level: ALWAYS set it false (M3 broke other tests by writing blocks).
- `OpeningDirector.clockOffset`: added to game time everywhere the opening reads the clock (`OpeningDirector.now`).
  Move it forward instead of waiting.
- `OpeningDirector.gates`: the story's gate as the opening uses it (open, usable, pos, enter). Stand in a fake that
  opens "1", is usable, sits at the square and whose `enter` records the player and returns true.
- `OpeningDirector.MOVED` (package-private): player id -> {x bits, z bits, last moved time}; set a runner's last-moved time back past `IDLE` to test the idle pull (the 5-minute rule uses `OpeningWorld.firstArrival`).
- Restore EVERYTHING in `finally`: testLayout null, stage enabled, gates `REAL_GATE`, clockOffset 0, the `OpeningWorld`
  fields you touched (gate, firstArrival, cinematicAt, rushAt, bridgeDown, stage, dawnSet), the game rule.
- Mock players: `h.makeMockServerPlayerInLevel()` (deprecated but what the suite uses), survival, `connection.markClientLoaded()`.
  They have no client: the opening runs its client-less clock (the sky ends at `SKY_END`, the infirmary at `WAKE_END`,
  the Registry walks itself after 30 s). Player ticks: call `OpeningDirector.tick(p)` yourself after moving the clock
  (some checks run on `p.tickCount % 5 == 0`: set `p.tickCount` when you need one to fire).

## The four tests (one per mechanism, prefix `opening_`)
1. `opening_state_machine`: a fresh player goes through every beat, with a relog (`OpeningDirector.onLogout(p)` then
   `OpeningDirector.onLogin(p)`) at each beat that must leave the beat and its start time unchanged:
   `maybeBegin` → SKY (on the Roost) → clock past `SKY_END` → RUN (gate "1" in OpeningWorld, `runStart` set) → into the
   square (teleport to `OpeningPlaces.get().square()`, tick) → ARRIVAL (solo: `cinematicAt` set) → clock past the
   cinematic → the pull: story GATE_OPEN at gate "1", `arrived` true, `OpeningHooks.inOpening(p)` true, the fake gate
   entered → the Void and the Accept (`Story.acceptInPlace(p)`, then `dev.awaken.signal.Signal.answer(p,
   "story:accept_only", true)`) → WAKE (awakened, still in the opening) → tick puts them to bed (`bed` 0) → clock past
   `WAKE_END` → REGISTRY → the steps run out on the clock → DONE, awakened, the licence in the inventory (a paper whose
   custom data has `awaken: "licence"`), `OpeningHooks.inOpening` false.
2. `opening_party_wait`: two players running; the first arrives: waiting (no `cinematicAt`, `firstArrival` set, one
   runner left); the second arrives: `cinematicAt` = now + `GROUP_DELAY`. Then the stragglers: a fresh group, one arrives,
   the other keeps running; move `firstArrival` back past `STRAGGLERS` and run `OpeningDirector.serverTick` on a tick where
   it fires: the runner is pulled into ARRIVAL with `pulled` true.
3. `opening_no_death`: a player in RUN at full health takes a 100-damage fall (`hurtServer` with the level's fall damage
   source): health stays at 2 (one heart); the same player after the opening (DONE) takes 5 damage normally.
4. `opening_skip`: (a) a player in RUN: `OpeningHooks.skip(p)` → awakened, DONE, the licence given, not in the opening;
   (b) the world option: game rule `OpeningModule.SKIP` true, a fresh player, `maybeBegin` → DONE and awakened (put the
   rule back); (c) the command: a fresh player runs `awaken opening skip` through the server's command dispatcher with
   their own command source → DONE.

Each test registers with `AwakenTests.register(name, maxTicks, h -> { ... h.succeed(); })` from `OpeningTests.register()`
(already called by OpeningModule). Keep each test inside one tick where you can (like RitualTests).

## Report back
What you built, the test results (the `[gametest]` lines), anything that failed and why, and any seam you needed but
didn't have. Under 300 words.
