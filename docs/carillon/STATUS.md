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
| M1 the city in blockout | City Architect (Opus xhigh) | carillon/m1-city · /Volumes/MEDIA/Awaken-city | running (brief: briefs/M1-city.md) |
| M2 the opening in blockout | Director (Opus xhigh) | — | waits for the Ascent's Runner tier on build/awaken-v1 |
| M3 alive | Citizens (Opus xhigh + Sonnet data) | — | after M2 (Euan's play 1) |
| M4 detail | City Architect + art (Opus xhigh + Sonnet) | — | after M3; perf at the end |
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
