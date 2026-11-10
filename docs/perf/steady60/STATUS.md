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
- **Flying is not the look's** (fly-1): Cinematic with the Air off flies the same route at 92.4 to 94.9% on time, the
  Air at 92.2 to 95.6%. The late frames are the game's own chunk work on the render thread.

## To do, in order
1. **Flying to 98% on time**: profile the late frames in flight (`JFR=1 tools/steady60-tour.sh
   tools/autopilot/steady60-fly.txt <name>`, then the render thread's frames over 20 ms): chunk meshes arriving,
   Awaken's own client ticks across modules, the road planner and cave horizon threads. Then act on what it shows
   (Awaken's workers giving way while frames run late; a frame governor only if the GPU turns out to matter).
2. **The Budget**: a pass-by-pass trim of the older Cinematic steps on the Dell (the probe chains,
   `tools/lens/probes.py`): the game alone is 2.8 ms and Cinematic-before 8.9, so they are most of the frame.
3. **Frame record** on the Look page (on-time share, `/awaken look report`).
4. Far Sight off by default on Cinematic and Ultra (LookPresets, config default); Look page rows for `lensAir` and
   `lensSunPass`.
5. More atmosphere, as Euan asks: ground mist at dawn and dusk, thinner cloud by night (it hides the stars when there
   is a lot of it), a lighter night ground.
6. The Air on Swift (needs a view direction: Swift has no frame texture).
7. CI: no GameTests have run on this branch since the build began; run the suite before hand-back. The manager merges
   (never push build/awaken-v1 from here). The branch carries a cherry-pick of the ClearOfSettlements fix (ea8dba375)
   that should merge from its own branch.

## Tours
`FOREGROUND= SLOT=light tools/steady60-tour.sh tools/autopilot/steady60-air.txt <name>` shoots the look on the
MacBook's screen in a copy of his world (`steady60-sky.txt`: the shorter one for the sky, clouds and rays;
`steady60-edges.txt`: the dusk edge check). On the Dell: `steady60-dell-5.txt` (cost and as played),
`steady60-fly.txt` (flight with each part off in turn), then `tools/steady60-report.py <results dir>`. "On time" is a
frame gap that rounds to exactly two refreshes of the 120 Hz screen. ImmersiveVFX's shots at the same moments are
`docs/perf/steady60/page/shots/p*_vfx.jpg`.
