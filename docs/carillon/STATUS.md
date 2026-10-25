# Carillon status

Plan: docs/plan-carillon.html (artifact https://claude.ai/artifact/SpBBMyKg3n7GH9B6Wsgi6W), approved by Euan on
2026-10-24 with every recommendation, including the Runner's Kit change (SPEC §16.0.1). Source of truth: SPEC §16.
Orchestrator: session "btw: kind of a side question…" (local_fe8b1e52…, ListAgents ref [8b9253]).

## RESUMED on the lean plan (2026-10-24 evening)

Euan resumed Carillon with the lean build (docs/plan-carillon-lean.html, decisions 14–19 in SPEC §16.0): one builder at a
time per milestone, Opus at extra-high effort for look and feel, Sonnet for data, generators, tests and docs; GameTests
only where breakage is silent, on GitHub CI (carillon/main and carillon/m*); one `slot.sh light` tour per milestone;
perf once at the end of Detail; Euan's three play sessions are the quality gate. Stage 0 and gate G0 are closed (verdicts
below; SPEC §16 numbers updated). The Runner's Kit belongs to The Ascent (session "The Ascent", local_47a3f561); spike
S3 is its starting point. MMO-lite is deferred; party gates and shared credit landed on build via PartyHooks.

| Milestone | Builder (model) | Branch / worktree | State |
|---|---|---|---|
| M1 the city in blockout | City Architect (Opus xhigh) | carillon/m1-city · /Volumes/MEDIA/Awaken-city | **done, merged** into carillon/main (94de89a7); report docs/carillon/milestones/M1-city.md |
| M2 the opening in blockout | Director (Opus xhigh) | carillon/m2-opening · /Volumes/MEDIA/Awaken-opening | **done, merged**; report docs/carillon/milestones/M2-opening.md; **Euan's play 1 next** |
| M3 alive | Citizens (Opus xhigh + Sonnet data) | carillon/m3-alive · /Volumes/MEDIA/Awaken-alive | **done, merged** into carillon/main; report docs/carillon/milestones/M3-alive.md |
| M4 detail | Phase B, one builder per step (Opus xhigh) | B3a: carillon/m4-b3a · /Volumes/MEDIA/Awaken-m4-b3a | B1, B2 **done, merged**; **B3a running** (street life; brief: briefs/M4-B3a-street-life.md); then B3b (animals, soundscape), B4…B7 |
| M5 the Atlas | Atlas (Opus xhigh) | — | after M4 |

## Stage 0 (spikes): closed at G0

| Spike | Question | Worktree / branch | State |
|---|---|---|---|
| S1 city | paint cost per chunk, frame cost, look of a Bellwatch slice | /Volumes/MEDIA/Awaken-c-city · carillon/spike-city (c36561c) | **ADJUST**: generation fine (city 2–5% of pregen); whole city always in view at RD 16 → solid cores, district density, RD ≤ 12 + DH for the sky shot; overhead look needs roof palette, plan hierarchy, landmarks, kit |
| S2 camera | sky shot through a camera entity, no mixin, smooth | /Volumes/MEDIA/Awaken-c-camera · carillon/spike-camera (7582f98) | **GO within limits**: no mixin, seamless hand-off, Lens runs; Sodium warm p99 15.6 ms; camera ≤ 215 from the player, below y 320; pre-warm behind the card (SPEC §16.4) |
| S3 runner | wall-run, hang, ledge jump on the body pattern; latency; accidental triggers | /Volumes/MEDIA/Awaken-c-runner · carillon/spike-runner (7a184a7) | **handed to The Ascent** (decision 15); report committed as left; findings in SPEC §16.3 |
| S4 crowd | 240 ledger + 40 bodies: tick and frame cost | /Volumes/MEDIA/Awaken-c-crowd · carillon/spike-crowd (3dd907d) | **GO**: ledger free, 40 bodies 0.3 ms/tick p50 and ≤ 1.2 ms frame; 2 stuck per play-day; movement rules in SPEC §16.5 |
| S5 atlas | picture-in-picture diorama + clouds cost; reuse Diorama or not | /Volumes/MEDIA/Awaken-c-atlas · carillon/spike-atlas (2a4672f) | **GO, build our own**: +0.7–1.0 ms open, 83 ms cold open; mesh memory to bound (see spikes/G0-notes.md) |
| S6 basin | city basin at spawn | Awakened Lands session, pack/awakened-lands (07d24ce, fb39078) | **done**: API landed, 300/300 GameTests, verified on seed 777 (site 1178, 828 on a peninsula; flat y 70 to 330, eased by 700; river carved to y 57, water to 63, terraced banks) |

Gate G0: every spike's numbers against SPEC §16.10, with shots; go / adjust / cut per feature; SPEC §16 numbers marked
(G0) updated; workstream briefs W1–W9 written from the results.

## Agreements with other sessions (2026-10-24)
- Main session ("Ultimate mod pack experience", local_95821df4): owns merges into build/awaken-v1 and the body module
  until W2. body-anims, awakened-body and cast-anims are already in build/awaken-v1 (08d5809). pack/ritual (c387982),
  holo parts 1–2 (401f1b0, 66883a4), awakened-lands (c8bd922, slice b4edbc7) and avatar-render
  (8c102d8) landed: the decision-13 set is complete and pack/awakened-lands when their sessions say green; it pings Carillon with each
  commit, and carillon/main merges build/awaken-v1 after each. It takes the shared tour lock for its own perf tours.
  W2 notes from it: keep `MovementHooks` stable (powers/MoveTwists uses it); clips come from
  tools/art/specs/body_moves.py (edit the spec, not the JSON); update docs/proposals/awakened-body.md and the SPEC rule
  text in the same commit as the movement change; the Ritual's wake destination is hooked in story/Prologue + the
  ritual director behind the Carillon world flag, classic path as the default.
- Terrain ("btw: What is lens", local_745133ef, pack/awakened-lands): builds `dev.awaken.terrain.api.CityBasin`
  (site, reserved(), CitySite with core 330 / blend 700 / street Y 70 / river path / basinCarved) behind the feature
  flag `awaken:carillon` (which it adds to META-INF/feature_flags.json). Asked to carve the river inside the core
  along the city's fixed path (offsets in SPEC §16.2 thread). Pings when the API lands.
- Holo UI (local_cc1dc312, pack/holo-ui): touches nothing in body/powers. Its K/inventory wrist cancels
  RenderHandEvent at HIGH while open. Decided: a ledge hang holds while a screen is open; a wall-run ends.

## Machine share
`tools/carillon/slot.sh build|tour <cmd>`; locks in /Volumes/MEDIA/.carillon-locks (build-1..3, tour; owner file
"pid who start"). The main session uses it for its tours too.

## Log
- 2026-10-24: plan approved; carillon/main created from build/awaken-v1 08d5809; SPEC §16, hooks v0, rules, briefs,
  slot.sh committed; spikes S1–S5 started; S6 agreed with the terrain session.
- 2026-10-24: pack/ritual landed on build/awaken-v1 (c387982) and was merged into carillon/main. Wake destination hook:
  story/Prologue + the ritual director (crypt mouth today) → infirmary behind awaken:carillon. Holo lands in two merges
  (1: wristband + visual pass + text glow, the one the Atlas needs; 2: power-pick screen + notifications).
- 2026-10-24: Holo part 1 (wristband, K menu, text glow, core/api/UiHooks) + Lens dimming (texel 14) landed on
  build/awaken-v1 (238546c, 401f1b0; 310/310 GameTests) and was merged into carillon/main. Holo part 2 (power-pick
  screens, notifications) still on pack/holo-ui.
- 2026-10-24: S6 done. dev.awaken.terrain.api.CityBasin landed on pack/awakened-lands (07d24ce, fb39078): request(Predicate),
  site(server), reserved(x, z); STREET_Y 70, CORE_RADIUS 330, BLEND_RADIUS 700, RIVER (the city's path; terrain owns the
  constant); CitySite(..., river in world coords, riverHalfWidth 9, riverFloorY 57, basinCarved). Feature flag
  awaken:carillon declared; the Carillon pack's pack.mcmeta requests it. Vanilla trees and flowers still grow on the flat
  ground (the painter clears plots); caves and ores continue deeper than 20 below the street. Dev view:
  `CITY=on PACK=perf,world TECTONIC=awakened tools/autopilot.sh tools/autopilot/city-basin.txt`, `/awaken terrain city`.
  Carillon gets it through build/awaken-v1 once the main session merges pack/awakened-lands (decision 13).
- 2026-10-24: S4 found the 26.3 avatar-render trap (any AvatarRenderState → vanilla player renderer). The main session
  confirmed five renderers affected (TownNpc, training Ghost, Shade, ShadowSelf, AwRenderers' player-model mobs) and is
  fixing them on pack/avatar-render with the HumanoidRenderState pattern; the rule is in briefs/RULES.md. pack/awakened-
  lands 4f81ce8 is being merged into build/awaken-v1 (tests running).
- 2026-10-24: pack/awakened-lands landed on build/awaken-v1 (c8bd922, 318/318) and was merged into carillon/main
  (0047e13; SPEC end-of-file conflict resolved: §15 before §16). carillon/main now has dev.awaken.terrain (CityBasin,
  CitySite) and the feature flags awaken:awakened_lands and awaken:carillon. Waiting on: pack/avatar-render, holo merge 2.
- 2026-10-24: Lands slice (b4edbc7) and holo part 2 (66883a4: UiCanvas.texture, per-owner UiHooks focus, holo
  first-power screen and notifications) merged into carillon/main (22af14c, 34a56cc). Still to land: pack/avatar-render.
- G0 so far: S5 Atlas GO (own build), S1 City ADJUST, S4 Crowd GO, S6 basin done. Waiting: S2 camera, S3 runner.
- 2026-10-24: pack/avatar-render landed (8c102d8) and was merged into carillon/main: core.client.skin.SkinnedMobRenderer
  / SkinRenderState / SkinModel for non-player humanoids (citizens use it). Decision 13 complete. Known flake added:
  anomaly_rift_close_pays_once.
- 2026-10-24 ~19:50: Carillon paused by Euan (usage window). S3 stopped and its report committed as WIP (7a184a7); the
  avatar-render merge's compile check stopped; no agents running, no locks held. See PAUSED above to resume.
- 2026-10-24 evening: resumed on the lean plan. build/awaken-v1 4c9ffb7 merged into carillon/main (SPEC end-of-file
  conflict: "Together" before §16); compiles. SPEC §16 gets decisions 14–19 and the G0 numbers; RULES.md gets the lean
  rules; CI runs on carillon/m* too. The Ascent has Carillon's run requirements (§16.3 + the S3 findings). M1 started.
- 2026-10-24: The Ascent confirmed the Runner tier = its Phase 1, built to §16.3 as written: press-in-the-air wall-run
  trigger, server checks from S3's 12-position history, the 0.58 upward-grab Y fixed, MovementHooks.wallRunning/hanging
  added (existing methods unchanged), and `/awaken ascent pilot route record|play <name>` (per-tick keys + look through
  the real input path) for M2's golden runs. Phase 1 starts after Euan's swing session (Phase 0 is on
  claude/ascent-7dcb3a); it pings once when the Runner tier lands on build/awaken-v1. M2 waits for that ping.
- 2026-10-25: order change (SPEC §16.0 decision 20, Euan via the pack session): after M1, ask the Ascent for its Runner
  timing; if the Runner tier isn't on build yet, start M3 (Alive); start M2 the moment it lands.
- 2026-10-25: M1 reviewed and merged (94de89a7). Checked: 6 commits, tree clean, CI green on carillon/m1-city; the edits
  outside city (gates/Director, world/anomaly/Spots, above/ruin/SpawnRingPlacement, descent/well/Well) are small guarded
  "stay out of the reserve" checks; shots reviewed (m1/: sky nw/top/low, the Roost, the square, the Quay): from the sky it
  reads as a designed walled city (ring wall and towers, river and canals, radial avenues from a lit round square, the
  green spire, the white Terrace, cherry-tree Garden Ward, district colours). Short of the bar: Bellwatch is a dark
  slate mass from above, the wall one heavy grey, all vanilla blocks up close (→ M4). Interim: the prologue's Bell Crypt
  gate opens in Choir Square in Carillon worlds (until M2). Watch: a Distant Horizons + Sodium meshing glitch drew
  floating windows once (tour 1; not the city's blocks). New flake seen on CI: gates_build_break_hold.
- 2026-10-25: the Ascent's Runner tier is done on pack/ascent (CI green d6d86e91; MovementHooks.wallRunning/hanging;
  route replay `pilot route rec|play|stop`, SPEC §17.6); it reaches build/awaken-v1 through the pack session in ~1–3 h.
  So by decision 20, M3 started now; M2 follows M3. M3 moved the Association wake/Registry scenes to M2 and leaves
  rooftop runners and rooftop chases until the Ascent's moves are in.
- 2026-10-25: M1 landed on build/awaken-v1 (a1110aec; SPEC now §16 Carillon, §17 The Ascent, §18 Pixels; §19–20
  reserved). build/awaken-v1 merged into carillon/main (9fee4177: wilds-base wards, gate-rig, biomes-3, pixels). Wards
  vs civic protection: no overlap (wards cover #awaken:warded sites + 12 blocks; terrain keeps those sites out of
  CityBasin.reserved, which reaches well past the wall). Euan's test rule added: one GameTest per mechanism, suite once.
- 2026-10-25: the Ascent's Runner tier landed on build/awaken-v1 (c5117925: Runner moves, MovementHooks.wallRunning/
  hanging, routes + golden runs, SPEC §17) and is merged into carillon/main (c4210481). Kept decision 20's order: M3
  (started shortly before) finishes first; M2 starts right after.
- 2026-10-25: M3 reviewed and merged. Checked: 29 commits, tree clean, CI green on 4701a6d (401 tests; two earlier red
  runs were its events test painting stalls into the shared GameTest world, fixed); outside citizens only the init lines
  and SkinnedMobRenderer's constructor made protected. Ticks: ledger p95 0.02–0.06 ms, 40 bodies p95 0.59–0.65 ms. Shots
  reviewed (market at noon, porters with crates, lamps before/after dusk, the Watch calling the hour, the Works fire): the
  market quarter and the night lamps read as a living town; elsewhere thin (1–3 people a street), vanilla wares (→ M4:
  roster ~400–480, lit windows, kit, crowd murmur). Open risk: the events test sprints the server ~9,000 ticks, which may
  time out gate builds in tests running alongside (a simulated clock would fix it). M2 started.
- 2026-10-25: M3 is READY TO MERGE into build/awaken-v1 at carillon/main f6091499 (pushed). The pack session was not
  running when M3 finished, so it hasn't been told: whoever merges next, take f6091499 (or later) and check CI on
  carillon/main first. M2 (the opening) started in /Volumes/MEDIA/Awaken-opening.
- 2026-10-25: M2 reviewed and merged. Checked: 19 commits, tree clean, CI green on 4c4ff9f (one unrelated fauna flake on
  the first push); edits outside opening are hand-over points (story: StoryProgress beats appended after DONE with
  schema 3 and a lenient codec, Story API, Prologue ownership only in Carillon worlds, RitualClient.handOff; powers
  Starter and the fauna reveal wait for the opening; citizens CityEvents/Roles for the bell and the market rush). Shots
  reviewed (sky frames with the title card, the light column from the Roost, Act 2 roofs, the arrival flare, the
  infirmary with the Registry marker). Open items: (1) golden runs: roof routes stable (0.0–0.8, act3 ~6 by design), street
  and canal playbacks vary between tours (citizens' bodies push the runner; or the Ascent's server-timed tactical
  sprint) → make bodies pass through the player during route playback, then re-check; (2) the run is a fair course but
  not yet a thrill ("nothing chases you") → Euan's play 1 decides what the threat is.
- M1 + M3 + M2 are all on carillon/main and NOT yet on build/awaken-v1 beyond M1: the next merge into build takes all.
- 2026-10-25: NEXT = Euan's play 1 (the opening in blockout). The pack session was offline, so carillon/main (df903178:
  M1 + M3 + M2) is not merged into build/awaken-v1 yet; Euan can play it directly with `tools/play.sh carillon/main --pack`
  in a NEW world. Then one word per beat (sky, bell, run, bridge, arrival, Ritual, wake, Registry) decides M4's
  priorities and the run's threat. M4 (Detail) is not started until play 1 is in, per the lean plan.
- 2026-10-25: carillon/main c3d4f735 (M1 + M3 + M2) is on build/awaken-v1 as d50ab96 (clean, compiles; CI running on
  the combination). Euan's play 1 runs on build with `tools/play.sh --pack` in a NEW world. M4 waits for play 1.
- 2026-10-25: Euan's play 1 is in (via the pack session; his words in the M4 Phase A brief): the city is flat, boring and
  not alive; the run looks intentionally placed ("why would a city just have random wooden blocks in the middle of the
  sky?"); the story doesn't hang together (the Void says you died in a gate, the wake says you walked out of one); floating
  Elder Wood in the sky. Approved: "All of it, concept art first." M4 north star: the city is the course, climbable because
  of its architecture (real elements, tuned to the movement envelope); routes derived from affordances (the 414-block
  stage shrinks to ~0); a height plan; a street-life layer; tokens into the grammar's rules + ~12 hand-made landmarks.
  Running: Phase A (Opus xhigh, docs only: look bible per district, ~8 concept images with gpt-image-2, story outline and
  the exact opening lines) on carillon/m4-look; and a Sonnet bug fix for the floating Elder Wood on carillon/m4-elderfix
  (likely source: HeroStructure.cityNear checks only 4 axis points of the crown, and the painter clears only 40 above
  plots). Phase B (one builder) waits for Euan's approval of Phase A.
- 2026-10-25: Euan added guided onboarding (via the pack session): just-in-time move tips (reverses SPEC §17.2's "found,
  not told"; one shared tip system with a public API the Ascent will call; real key bindings; fades once done; option
  off), the Registry as a guided walk with the officer (Holo band, K menu, map, first Directive, a camera pan to the city
  gate), and light guidance for the first stretch outside. Folded into Phase A (onboarding.md + story lines); built in
  Phase B. Carillon owns the tip system.
- 2026-10-25: M4 Phase A done (carillon/m4-look, pushed: docs/carillon/m4/ README, look-bible, story, onboarding,
  facade-grid.svg, 9 concept images) and published for approval: https://claude.ai/artifact/9DmvY3bCg8FGMbJyCnbYBm.
  Decisions asked: approve the package; Terrace 8 → 12; no Quay jetties and Market spokes 6 → 5; tips replace "found, not
  told" (SPEC §17.2); one builder (B6 after B5) vs two; vanilla banners/signs/barrels out of the city fabric.
- 2026-10-25: Euan approved M4 Phase A directly: "Go ahead with the full plan and all recommendations". Recorded as SPEC
  §16.0 decisions 21–26; §17.2's "found, not told" marked superseded; `core.api.TipHooks` v0 added (no-op until B6, so
  the Ascent can wire its tips now). carillon/m4-look merged. B1 (facade grammar + core kit) started.
- 2026-10-25: floating Elder Wood fixed and merged (carillon/m4-elderfix f457c82e). Source: the city's vegetation sweep
  skipped every *_wood block (the city's own trunks are vanilla bark), so elder trees' bark limbs stayed hanging when the
  sweep cleared their trunks (33 floating groups on one seed, 7 inside the wall). Also closed: the 4-point crown probe
  (CityBasin.reservedWithin). Scan after: 0 floaters in 1970 chunks. Existing worlds need `/awaken city tidy`. CI red only
  on unrelated flakes (fauna_p2_families_spawn, fauna_natural_spawning_end_to_end, gates_hooks_view_stabilise_calm).
- 2026-10-25: Euan added five extras to Phase B (SPEC §16.0 decision 27): the kit to ~60 (furniture, wares, shop signs,
  random texture variants: variants in B1, the rest in B3), movement for free (B3), animals (B3), interiors you can
  glimpse plus four full interiors (B4), city light (B6/B7). Not added: the soundscape, citizens who interact, weather.
- 2026-10-25: Euan added the city soundscape (SPEC §16.0 decision 28) to B3's builder: vanilla layered first, CC0 only for
  what vanilla lacks and only after his yes per file, synthesis for bells and the Choir; a listening page is the gate
  before B3 merges.
- 2026-10-25: M4 B1 reviewed and merged. Checked: tree clean, CI green (9ef9262c), all Java changes inside city; before/
  after pairs from M1's cameras (sky, Market street, square, Quay): the Market street goes from blank walls to courses,
  canopies, window boxes and lanterns; the roofscape breaks up (slate, ridges, colour). Short of the bar: one floor's
  rhythm repeated up a house, plain ground storeys (B3), dark 4-wide Bellwatch lanes, chunky drainpipes; four post-tour
  fixes unseen in game. Tags filled (runner_lines/soft partly → B3). B2 (relief and heights) started.
- 2026-10-25: B1 landed on build/awaken-v1 (19afe05e, with CI flake fixes); build merged into carillon/main (e9202766).
  Agreed with the Living Wilds (decision 29: the High Gate yard and CityHooks.spot, bounties as the board's first slice,
  Bram's pan line, chain drafts for review). Said yes to Creature life's life layer for citizens (shared API, B3 wires it
  into CitizenModel; client-only, ≤ ~0.1 ms for a crowd, clips win, head tracking only near the player or a partner).
- 2026-10-25: Euan asked for 3D ladders like the "Smart Ladders" mod (reference /Volumes/MEDIA/carillon-ref/smart-ladders.webp):
  folded into B2. Required: a model override for minecraft:ladder (rails and rungs; vanilla collision and climb box
  unchanged). If cheap: hanging ladders that extend down or up with no wall behind. Approved Living Wilds' two chains
  with edits; added spot bellwatch_cellar (B2 reserves the room under the Knoll); B5 will publish a roof-route chase hook.
- 2026-10-25: M4 B2 reviewed and merged (a7537a8f). Checked: tree clean, CI green on 109d50a9; outside city only
  OpeningPlaces.TERRACE reads the lift and the citizens roster regenerated. Shots (the Knoll, the bowl, the Terrace's
  stepped lane, the Quay ladder, the gate yard): depth reads from the street and roofs; from the sky only partly (a Knoll
  of +8 against houses 9–25 tall; two more rings is a plan change, for B4 to consider with the Knoll's tower). Known: a
  tree grew in the gate yard (B3a clears it). B5 inherits changed stage routes (act1_roof 30/35 points etc.). B3 split
  into B3a (street life) and B3b (animals + soundscape + Euan's listening page).
- 2026-10-25: for the Living Wilds' first chains: the opening counts `awaken:carillon_registered` when it ends or is
  skipped (db8bd104); B3a adds Kestrel (the Roost) and Bram (the Association) as named citizens; B3b wires
  `living.api.Quests.talk(player, id)` first for every named citizen (once talk() is on build). The hearth Forge
  (Oren's temper step) is the pack session's. Oren's move to a Hearth waits on renown tiers past Known (MMO-lite).
