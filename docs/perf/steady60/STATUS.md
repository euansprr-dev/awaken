# Steady Light: where the build stands

Euan approved the whole plan on 10 Nov ("go ahead with the entire plan ... nearly on par with immersive VFX ... a stable
60 but extremely high quality"). He found the plan page's proof of concept "basically like vanilla": the bar is
ImmersiveVFX's sky, clouds, gradient, depth and life. All five decisions are yes (Far Sight off by default on Cinematic
and Ultra; the cloud march replaced; Lens's clouds whatever the Clouds setting says; VFX Look left alone; stage 3 now).

Pages: plan https://claude.ai/artifact/4UjwicVDJEoS9vceUanMyQ, pairs (third pass)
https://claude.ai/artifact/Rt4U8jC2pCgf7Nc6axTXZg (source in the session's scratchpad; Lens shots are
`run-autopilot/results/air-11/` and `sky-4/`, ImmersiveVFX's `docs/perf/steady60/page/shots/p*_vfx.jpg`).

## Done (branch claude/look-steady-60-plan-889268, SPEC §22 "Steady Light")
- **The Air** (`lensAir`, autopilot `lens air on|off|setting`): sky, far and near fog, solid block clouds, sunlight
  with weight, a night sky (stars in four sizes, a galaxy, shooting stars), high wisps, fireflies, pollen, motes that
  stand only in sunbeams. `include/lens_air.glsl`, `lens_composite.glsl`, `lens_clouds.*`, `lens_light.fsh`,
  `LensParticles` (its own pipeline, no depth write), `LensFrame.air` (texels 22-23).
- **The Sun Pass** (`lensSunPass`, autopilot `lens sunpass ...`): `LensBlocks` (the block map, `awaken:lens_blocks`,
  frame texel 17 z/w), `blockRay` in `lens_light.fsh`; texel-snapped contact shadows and a texel-snapped read of the
  light buffer in the composite (`upsampleAt`); Cinematic's light buffer 960x540, cloud buffer 800x450.
- **The sun's rays** (autopilot `lens rays ...`): `LensLit` (the lit air: a worker sweeps the block map slice by slice
  into `awaken:lens_lit`; frame texels 15 w, 20 zw, 21 w), read by the mist march, by the Sun Pass before it walks
  (`litSure`) and by the motes; and screen rays in `lens_shafts.fsh` whose light is the sun's own neighbourhood.
- **Crisp clouds** (his fourth note, 10 Nov: "jittery ... very low resolution ... they don't look like clouds"): the
  clouds within 640 blocks are walked at the frame's own size every frame (`lens_cloud_blocks.fsh`, target
  `awaken:lens_cloudblocks`), the far band stays in the 800x450 buffer with nothing carried between frames; the drift
  follows the partial tick; the cell field is one fixed texture (`awaken:lens_cloud_cells`, `tools/lens/cloud_cells.py`).
- **The floor patchwork under a low sun** (same day): the lit air no longer shades any surface and no longer holds the
  far land's shade; it only saves the block walk where it is sure of the sun (`litSure`, `walkEnd`).
- **The late frames in flight, profiled** (`LateFrames`, autopilot `late ...`; SPEC §22 has the breakdown). The top
  cause was Sodium freeing every unloaded chunk in one frame. **Chunk pacing** (Turbo, `turboChunkPacing`,
  `dev.awaken.turbo.chunkpace`) lets three through a frame: 94.9% on time against 93.3% on alternating legs (pace-1).
  Approved by Euan on 10 Nov, asked about the hook by name: "Yes, turn it on". On by default. CLAUDE.md's Turbo
  list still needs its line (this session may not edit that file).
  A smaller chunk batch from the server was tried and dropped (batch-1).
- **The trim** (10 Nov, "apply all of the other ones. Let's make this GREAT"; SPEC §22 "The trim"): straight out (the
  composite draws into the texture Pixels upscales, the chain's last copy skipped; `lens straight` switches it), the
  near clouds walked inside the composite, a small sky buffer for wisps and galaxy, the sun map swept on a worker,
  the wide glow in one pass, Lens's tick work standing back after a late frame. 16 passes became 14, three
  frame-size passes became two (one with straight out). **Frame times not measured yet**: that needs a quiet Mac
  (`tools/steady60-tour.sh tools/autopilot/steady60-dell-5.txt`). Pictures checked on CI
  (`steady60-trim-ci.txt`, `steady60-still-ci.txt`).
- **Grades** (10 Nov, his three pictures of a vibrant look; SPEC §22 "Grades"): a colour and atmosphere choice over
  every look. A grade is a file (`assets/awaken/lens_grade/<id>.json`), the setting is `lensGrade`, the Look page
  has a COLOUR GRADE row, autopilot `lens grade <id>`. Ships Awaken (the look as built, the default) and Vivid.
  Compared on CI with `steady60-grade-ci.txt`. Not seen by him in game yet.
- **Tick work evened out** (autopilot `lens cost`): a 0.5 ms limit a tick for the heightfield and the block map, near
  re-reads in turn.
- Euan's notes, 10 Nov, three rounds, all acted on: bright rims at dusk; dark un-fogged patches; "the atmosphere
  between you and the fog"; then clouds too see-through (sections showed), rays wanted, shadows with more weight, no
  shadows under particles, "really surprise me" with the sky, the night above all.

## Numbers (the Dell, his settings; SPEC §22 has them all, data in `docs/perf/steady60/data/`)
- First pass, quiet Mac (dell-6): 11.2 ms GPU, 78 fps uncapped; as played 98.6% on time standing, 98.9% turning, 95.2%
  flying.
- This pass, busy Mac (dell-7 to dell-9, about a tenth high): 11.3 to 11.9 ms, 71 to 77 fps; 1.0 to 1.5 ms over
  Cinematic-before in the same run (2.3 in the first pass). As played 99.1 to 99.9% standing, 96.4 to 98.5% turning,
  92.2 to 93.6% flying. **Needs one run on a quiet Mac** (`tools/steady60-tour.sh tools/autopilot/steady60-dell-5.txt`).
- With the crisp clouds (dell-10, dell-11, same busy Mac): 11.9 ms standing (74 fps), 12.2 to 12.6 flying, 12.6 to 13.2
  at night looking up; as played 99% standing and turning, 89 to 91% flying. The cloud pass costs about 0.3 ms
  standing and 0.8 flying under open sky. Levers if it must come down: `CLOUD_NEAR` (640), or folding the near walk
  into the composite to save a full-size pass.
- **Flying is not the look's** (fly-1): Cinematic with the Air off flies the same route at 92.4 to 94.9% on time, the
  Air at 92.2 to 95.6%. The late frames are the game's own chunk work on the render thread.

## To do, in order
1. **Flying to 98% on time**: about 95% now over loaded land with Chunk pacing, 77 to 84% over land never seen.
   Left, in order: the pass-by-pass trim below (every millisecond of GPU time is a millisecond of spare for the CPU's
   spikes), the game's client light engine (it copies its section maps each frame that changed any), Sodium's rebuild
   task preparation, the other modules' client ticks (Atlas, Wild Tint, Kin, Delve: their owners'), and new land (the
   integrated server's world generation).
2. **The Budget**: the trim is built (above); measure it on a quiet Mac, then go on with the composite itself (still
   the largest pass) using the probe chains (`tools/lens/probes.py`).
3. **Frame record** on the Look page (on-time share, `/awaken look report`).
4. Far Sight off by default on Cinematic and Ultra (LookPresets, config default); Look page rows for `lensAir` and
   `lensSunPass`.
5. Clouds between 640 blocks and the horizon are still the soft small buffer: if he finds the middle distance blurry,
   raise that buffer (1440x810) or `CLOUD_NEAR`, at a cost.
5b. More atmosphere, as Euan asks: ground mist at dawn and dusk, thinner cloud by night (it hides the stars when there
   is a lot of it), a lighter night ground.
6. The Air on Swift (needs a view direction: Swift has no frame texture).
7. **Handed to the orchestrator on 10 Nov** with build/awaken-v1 (325a3d003) merged in and the mod building. Not yet:
   GameTests after that merge, and Euan playing it.
8. CI: GameTests ran once on 43373dfbb (run 42798649001, started by hand: the workflow does not start on `claude/**`):
   two required tests failed, `delve_heart_in_rock` and `fights_mind_answer_gearwright_colossus_3`, both in modules
   this branch does not touch; build/awaken-v1's own last three runs were red too, and this branch forked before the
   gearwright fix (43dceee2a). Rerun after rebasing onto a green build. The manager merges
   (never push build/awaken-v1 from here). The branch carries a cherry-pick of the ClearOfSettlements fix (ea8dba375)
   that should merge from its own branch.

## Tours
`FOREGROUND= SLOT=light tools/steady60-tour.sh tools/autopilot/steady60-air.txt <name>` shoots the look on the
MacBook's screen in a copy of his world (`steady60-sky.txt`: the shorter one for the sky, clouds and rays;
`steady60-edges.txt`: the dusk edge check; `steady60-floor.txt`: the floor under a low sun with each part off in turn;
`steady60-check.txt`: floor and sky in one launch). On the Dell: `steady60-dell-5.txt` (cost and as played),
`steady60-fly.txt` (flight with each part off in turn), `steady60-late.txt` (the late-frame profile), `steady60-pace.txt`
(Chunk pacing on and off on alternating legs), then `tools/steady60-report.py <results dir>`. "On time" is a
frame gap that rounds to exactly two refreshes of the 120 Hz screen. ImmersiveVFX's shots at the same moments are
`docs/perf/steady60/page/shots/p*_vfx.jpg`.
