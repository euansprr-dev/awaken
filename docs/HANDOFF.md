# AWAKEN handoff (2026-10-26, ~05:45; addendum ~13:00)

Start here if you are a new Claude session with no context. Read this, then `CLAUDE.md`, then the memory index at
`~/.claude/projects/-Volumes-MEDIA-Awaken/memory/MEMORY.md`. Its files hold Euan's decisions, preferences and the
history behind them. `docs/SPEC.md` is the source of truth for every system.

## Addendum, 26 Oct ~13:00 (build a3869560, CI green)
Merged since the morning, each on green CI with its shots reviewed:
- **Fixes from Euan's playtest:**
  - the vault rank lock (the Tether needs C or the door's rank and an open door);
  - gate pillars as soft light (no boxed beacon beam);
  - the Rolling Storm as a real cumulonimbus in Lens's clouds, with local rain and thunder late by distance;
  - the Far Sight sea (no sky under the water, a far sea floor);
  - leaps from a hang with momentum (§17.26).
- **New systems:**
  - the notification director, discovery and the rift (§26);
  - the Registry ceremony (sign, take the band, wear it in the wrist slot, the boot, the K tip);
  - the Atlas clouds' drift;
  - wild life (§29: birds, insects, small animals);
  - Arms S7, the animation lab, slicing swings, real holds per style, and the arm holding the weapon in first person.
- **The Way Forward (§27, `docs/progression/`):** Euan approved all 11 decisions. Phases 1, 2, 4 and 5 (`pack/progression`) and Phase 3, locks (`pack/locks`, §17.27), are merged. The Tether is at D.
- **Building in their own sessions:**
  - fence and wall mantling (`pack/fence-mantle`, being reconciled with §17.26);
  - grass and plant sway (`pack/grass-sway`);
  - storm, nothing open;
  - CI tours on GitHub macOS runners (`pack/ci-tours`, `tools/tour-ci.sh`);
  - Between the Gates (§28, `docs/world-depth/`, all 13 decisions approved): W1 the early Hearth that reads your build, W2 mining.
- **Tour budget** (memory `tour-budget.md`): one tour per hand-back, no "before" tours, under about 8 minutes.

## Who and how
- **Euan** owns the project. He wants AWAKEN to feel "genuinely magical", at a AAA bar. He decides the look and the
  direction, and approves plans; builders build autonomously and verify with GameTests and screenshot tours.
- **Keep usage low.** Build first. One test per mechanism. GitHub CI runs the full suite. At most one screenshot tour
  per milestone. Never loop on tuning.
- **Models:** Opus at extra high effort for anything about the look (the city, the opening, visuals). Sonnet for data,
  generators, tests and docs.
- **Before each piece of work, show him the plan or the concept** (an artifact page) and get his yes.
- **Ask him before launching the game client** (runClient): it steals focus and plays sound. Never build in
  `/Volumes/MEDIA/Awaken-play` while his game runs.

## Git and CI
- **Branches:**
  - `build/awaken-v1` is the integration branch. Only the orchestrator (one session) merges into it.
  - Feature work happens on `pack/*`, `carillon/*` and `lands/*` branches, each pushed to origin.
  - Never force-push.
- **Repo:** `euansprr-dev/awaken` on GitHub, **public since 2026-10-25** (for free CI). Never commit secrets, tokens or
  copies of Mojang's assets.
- **CI:** `.github/workflows/gametests.yml` runs the full GameTest suite on every push to `build/**`, `pack/**`,
  `carillon/main` and `carillon/m*`. A newer push to the same branch cancels the older run.
- **To merge:**
  1. Fetch the branch.
  2. Check its CI run is green, or that only known flakes or other modules' tests failed.
  3. Merge with `--no-ff`.
  4. Run `slot.sh build tools/gradle.sh 900 build -x test`.
  5. Push, then watch the build's CI.
- **Tours on CI:** `.github/workflows/tours.yml` runs one autopilot tour per dispatch on a macos-15 runner (Vulkan on
  its paravirtual GPU, a virtual display like the MacBook's), free and in parallel (about 5 at once), 5-8 minutes from
  dispatch to shots. Builders run `tools/tour-ci.sh <script> [timeout] [KEY=VAL ...]` after pushing; it waits and
  downloads the shots, logs and summary into `run-autopilot/screenshots-ci/<script>-<run id>/`, and exits non-zero if
  the tour failed. The run's artifact keeps them 7 days. Caches (Gradle, Mojang's assets, pack mods) are written only by
  runs on build/awaken-v1: after a merge that changes Minecraft, NeoForge or pack/lock.json, run one tour there
  (`tools/tour-ci.sh tools/autopilot/ci-probe.txt`) to warm them. Mac-only (under `slot.sh tour`): perf and frame-time
  tours, Far Sight's tours (the runner's GPU scrambles its far mesh, so CI tours run without Far Sight), sound listening.
- **The machine lock:** wrap every Gradle run and Mac tour in `/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh
  build|tour|light <cmd>`. Set `SLOT_WHO=<you>`. A tour runs alone.
- **Local tests:** `tools/gradle.sh 900 runGameTestServer -Ptests=<prefix>` runs only the tests you name.
- **Euan plays** with `tools/play.sh --pack`. It builds `build/awaken-v1` into `/Volumes/MEDIA/Awaken-play`, runs on
  Vulkan, and gives the client an 8 GB heap.

## What is in build/awaken-v1 (fe260b4b, 2026-10-26 ~05:45, CI green)
Merged and green since 25 Oct 16:30, by the orchestrator, each one on green CI with its shots reviewed:

**Carillon (SPEC §16):**
- **B3a, street life:** props, the street kit, lit windows, 460 citizens, Kestrel and Bram.
- **B3b, the city's soundscape and creatures:**
  - client-only animals and boats, and the district soundscape;
  - drawn lanterns and festoons with no light blocks, and the Watch's 3D hand lantern;
  - boatmen at their own berths, and held 3D props;
  - the look-at limit.
- **The walls, towers and gatehouses** (carillon/m4-walls):
  - relief, the wall kit and climbable courses;
  - the Watch's round on the walk;
  - warm courses, the approach and the barbican.
- **M5, the Atlas** (replaces Xaero's map): a voxel diorama on M, discovery, the wrist glance, region names, party pins,
  readability, and under 2 ms.

**Movement and hands (SPEC §17, §24):**
- **Movement fixes:** the stair slide (§17.22), mantling onto stair- and slab-topped walls (§17.23), and the landing
  slide (§17.24).
- **Hands, P1-P6** (approved in game by Euan):
  - the pipe grip (Shift slides; Jump plus a direction kicks off);
  - pipe tops lead to a cornice hang and the roof, and the ledge drop;
  - first person for every move, touches that reach the world, station scenes, and the Ritual's hands.

**Arms, combat (SPEC §25):**
- S0 (every blow told), S1 (strike, guard/deflect, dodge), and S2 (feel, ribbons, camera, tells).
- All 16 styles, with 96 forged and 15 Lord-forged weapons in Euan's voxel reference style
  (/Volumes/MEDIA/arms-ref).
- Weaves (S8) and their Codex page, and the combat sim.

**Far Sight 2 (SPEC §20):** the Awakened Alpine terrain for new worlds, real far relief, far forests, ridge shadows,
haze and valley cloud. It's on by default and Distant Horizons is out of the pack. Ultra costs about +2.7-2.8 ms.

**World sound:** the Conductor, AWAKEN's own places and moments, AmbientSounds for nature, and Euan's listening
remakes.

**Earlier on the 25th:**
- Living Wilds Phase E (crafts).
- City fps: ZGC, render scale 0.75 on 4K, the city held through join, lighter Lens uploads.

**Measured quiet** (26 Oct, MacBook screen, render distance 32): the city holds the 60 Hz presentation cap on Swift,
Cinematic and Ultra. The remaining hitch is generation while walking into new chunks (p99 53 ms). Euan's 40 fps reading
was the builders' contention, not the game.

## Open, for Euan
- **The Arms sim's whole-game pacing** (`docs/combat/sim-2026-10-26.md`): dodgers and parriers lose most on-level Lords
  as built. Not applied, because it also changes how players who use powers fare.
- **Feel sessions G1 and G2,** the combat listening sheet (`docs/combat/listening-2026-10-25.html`), and his verdict on
  the voxel weapons.
- **Atlas:** should it pop open by itself at the Registry? Gate pillars on the map, with the Gate Map tab renamed?
- **Far Sight:** reference 2's massif scale is capped by the y 320 ceiling. Over the sea, Ultra's budget is lost to
  the planner's sea-floor estimate (a next fix noted in STATUS).
- **Carillon next:** M4 steps B4-B7 (interiors, landmarks, city light, Bram's Registry walk). Living Wilds next:
  phases F and G.

## Working here
- **The orchestrator session** briefs builders (Opus at xhigh effort), merges, and never stops builders unasked.
- Tours have a standing OK. Cloud credit is used up, so work runs on local sessions.
- New builders: read the memory index first.

## Small known issues
- `living_scene_finder` still fails at some CI locations ("flat open ground for a road scene"). Make it independent of
  location, the way 7ca34c66 did for its first check.
- Weakest power glyphs, worth a redraw: ember_step, cord_lash, wall_run, ghost_step, flight, third_eye.
- A Briar Sow killed with /kill vanished about 15 ticks in, instead of after its 36-tick fall (server side).
