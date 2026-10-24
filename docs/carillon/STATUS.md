# Carillon status

Plan: docs/plan-carillon.html (artifact https://claude.ai/artifact/SpBBMyKg3n7GH9B6Wsgi6W), approved by Euan on
2026-10-24 with every recommendation, including the Runner's Kit change (SPEC §16.0.1). Source of truth: SPEC §16.
Orchestrator: session "btw: kind of a side question…" (local_fe8b1e52…, ListAgents ref [8b9253]).

## Now: Stage 0 (spikes), gate G0 next

| Spike | Question | Worktree / branch | State |
|---|---|---|---|
| S1 city | paint cost per chunk, frame cost, look of a Bellwatch slice | /Volumes/MEDIA/Awaken-c-city · carillon/spike-city | running |
| S2 camera | sky shot through a camera entity, no mixin, smooth | /Volumes/MEDIA/Awaken-c-camera · carillon/spike-camera | running |
| S3 runner | wall-run, hang, ledge jump on the body pattern; latency; accidental triggers | /Volumes/MEDIA/Awaken-c-runner · carillon/spike-runner | running |
| S4 crowd | 240 ledger + 40 bodies: tick and frame cost | /Volumes/MEDIA/Awaken-c-crowd · carillon/spike-crowd | running |
| S5 atlas | picture-in-picture diorama + clouds cost; reuse Diorama or not | /Volumes/MEDIA/Awaken-c-atlas · carillon/spike-atlas | running |
| S6 basin | city basin at spawn | Awakened Lands session, pack/awakened-lands | agreed, ~1 day (API below) |

Gate G0: every spike's numbers against SPEC §16.10, with shots; go / adjust / cut per feature; SPEC §16 numbers marked
(G0) updated; workstream briefs W1–W9 written from the results.

## Agreements with other sessions (2026-10-24)
- Main session ("Ultimate mod pack experience", local_95821df4): owns merges into build/awaken-v1 and the body module
  until W2. body-anims, awakened-body and cast-anims are already in build/awaken-v1 (08d5809). It is merging
  pack/ritual now, then pack/holo-ui and pack/awakened-lands when their sessions say green; it pings Carillon with each
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
