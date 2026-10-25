# AWAKEN handoff (2026-10-25, ~16:30)

Start here if you are a new Claude session with no context. Read this, then `CLAUDE.md`, then the memory index at
`~/.claude/projects/-Volumes-MEDIA-Awaken/memory/MEMORY.md`. Its files hold Euan's decisions, preferences and the
history behind them. `docs/SPEC.md` is the source of truth for every system.

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
- **The machine lock:** wrap every Gradle run and tour in `/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh
  build|tour|light <cmd>`. Set `SLOT_WHO=<you>`. A tour runs alone.
- **Local tests:** `tools/gradle.sh 900 runGameTestServer -Ptests=<prefix>` runs only the tests you name.
- **Euan plays** with `tools/play.sh --pack`. It builds `build/awaken-v1` into `/Volumes/MEDIA/Awaken-play`, runs on
  Vulkan, and gives the client an 8 GB heap.

## What is in build/awaken-v1 (4fa2e86c, ~16:30)
Everything below is merged and green on CI, apart from the Living Wilds location test noted under "Small known issues".

**Carillon, the walled city at spawn (SPEC §16)**
- The opening run, the Ritual, the infirmary wake, and citizens.
- M4 look, steps B1 and B2: the climbing grid on all 779 buildings, real heights, chained roofs, walkways, and 3D or
  hanging ladders.
- The approved story and onboarding plan, with TipHooks v0.

**The Ascent, movement (SPEC §17)**
- Runner moves from the start, the one-button free-run, the shoulder action camera, and weight.
- The tiers: rope, Leaper trial, Wings, flight via the Sky Leviathan trial, and Sovereign on the U key.
- The tactical sprint bar, windways, Tether Spires, hero-tree crowns, and Living Wilds quest calls.
- The stair-roof climb fix from Euan's playtest.

**The Living Wilds, MMO-lite (SPEC §21), phases B and C**
- Regions with levels, kill XP, elites, gate XP ×0.80.
- HoloWorld creature plates, damage numbers, XP stream, level-up moment, loot pillars.
- Wayfarers, eight quest chains, relics, Hale's bounties, the Journal.

**Lens 2, the shader (SPEC §22)**
- Sun shadows, AO, beams, fog glow, sky, 15 gate-theme looks, water glint, volumetric clouds.
- About 100 fps on Cinematic at 1440p on Euan's M4.

**Creature life (SPEC §23)**
- Fresh Animations (EMF and ETF) in the pack.
- A procedural life layer for our creatures, plus hero clips.

**Power icons:** 63 new 32×32 icons in 9 family frames, and crystal Power Cores.

**The pack:** Shoulder Surfing, Fresh Animations, an 8 GB heap.

## In flight (each one pushed to its branch, not yet merged)
**MERGED (see the commit after f91675c9): pack/living-wilds-d at 8ffbffd3, Living Wilds Phase D**
- The World Pulse with happenings, the ledger of herds, packs and roosts, camps and nests, and the game rule
  `awaken:living_herds`.
- CI run 40178150413 passed; merged into build.

**pack/living-wilds at 14832a98: Phase E (crafts), WORK IN PROGRESS**
- Herb and meal textures and the tour are missing.
- Do not merge yet. Finish it, then merge.
- Then Phases F (wonders, secrets, legendary beasts) and G (settlers and village) from docs/living-wilds/PLAN.md.

**carillon/main: Carillon M4 B3a (street life), committed as WIP**
- Euan's urgent stop at 98% usage; check what got pushed.
- Next: B3b (animals, the soundscape, with Euan's listening page as the gate), then B4–B7, including the landmarks and
  Bram's Registry walk.
- Plan: `docs/carillon/m4/` and `docs/carillon/STATUS.md`.

**pack/ascent**
- Idle. Its branch is behind build, so merge `build/awaken-v1` into it before any new work.
- Open: free-run tuning from Euan's feel notes, and the Sky Leviathan's aerial moveset (deferred).

**Done and idle:** pack/lens2, pack/creature-life, pack/power-icons.

**Paused on Euan's word:**
- **Far Sight** (our own far-terrain renderer, pack/far-sight). It's the path to the alpine views he wants, together
  with an Awakened Lands terrain upgrade (erosion, snow lines) and tall swaying grass.
- **Pixels and MetalFX** (render scale is merged but off by default; MetalFX isn't done).

## Euan's open notes from his playtest (25 Oct)
1. **Blue glowing crystal-tree shapes all over Choir Square in a new world.** Probably an Awakened Lands feature the
   city's sweep doesn't clear; this is for Carillon.
2. **About 55 fps with big hitches in the city.**
   - The setup: the 4K Dell (1920×1080 at 2x), 32 chunks, a 4 GB heap at 78%, allocation about 400 MB/s.
   - Done: the heap is now 8 GB.
   - To do: find the allocation hotspot in the city (citizens? the life layer? plates? Lens?). Consider renderScale 0.75
     on 4K, and finishing MetalFX.
3. **No clouds seen.** Check that he's on the Cinematic or Ultra look (the clouds draw only there).
4. **Free-run feel notes:** not given yet. Ask him for them.

## Small known issues
- `living_scene_finder` still fails at some CI locations ("flat open ground for a road scene"). Make it independent of
  location, the way 7ca34c66 did for its first check.
- Weakest power glyphs, worth a redraw: ember_step, cord_lash, wall_run, ghost_step, flight, third_eye.
- A Briar Sow killed with /kill vanished about 15 ticks in, instead of after its 36-tick fall (server side).
