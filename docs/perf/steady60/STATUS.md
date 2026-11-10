# Steady Light: where the build stands

Euan approved the whole plan on 10 Nov ("go ahead with the entire plan ... nearly on par with immersive VFX ... a stable
60 but extremely high quality"). He found the plan page's proof of concept "basically like vanilla": the bar is
ImmersiveVFX's sky, clouds, gradient, depth and life. All five decisions are yes (Far Sight off by default on Cinematic
and Ultra; the cloud march replaced; Lens's clouds whatever the Clouds setting says; VFX Look left alone; stage 3 now).

Pages: plan https://claude.ai/artifact/4UjwicVDJEoS9vceUanMyQ, first-pass pairs https://claude.ai/artifact/Rt4U8jC2pCgf7Nc6axTXZg.

## Done (branch claude/look-steady-60-plan-889268, SPEC §22 "Steady Light")
- **The Air** (`lensAir`, autopilot `lens air on|off|setting`): sky, far and near fog, block clouds, stronger sunlight,
  fireflies and pollen; always spread over frames. `include/lens_air.glsl`, `lens_composite.glsl`, `lens_clouds.*`,
  `lens_light.fsh`, `LensParticles`, `LensFrame.air` (texels 22-23).
- **The Sun Pass** (`lensSunPass`, autopilot `lens sunpass ...`): `LensBlocks` (the block map, texture
  `awaken:lens_blocks`, frame texel 17 z/w), `blockRay` in `lens_light.fsh`; no facing term inside the map; long
  texel-snapped contact shadows; Cinematic's light buffer 1440x810, cloud buffer 800x450.
- Euan's notes, 10 Nov, both fixed: bright rims on plates and block lips at dusk (facing term on small edges; low-sun
  fade); dark un-fogged patches on far trees and the mountain (fog skipped covered points; haze thinned over a tree's
  height). His third: "the atmosphere between you and the fog" -> the near haze; he said "much better".
- **Dell, his settings, quiet (dell-6):** 11.2 ms GPU, 78 fps uncapped, p99 16.0 ms; as played 98.6% on time standing,
  98.9% turning, 95.2% flying (target 98%).
- Pairs page (second pass): https://claude.ai/artifact/Rt4U8jC2pCgf7Nc6axTXZg (source in the session's scratchpad;
  shots are `run-autopilot/results/air-10/` and `docs/perf/steady60/page/shots/p*_vfx.jpg`).

## To do, in order
1. **Flying to 98% on time**: Awaken's own workers give way while frames run late (road planner, cave horizon); the
   governor (steps: cloud and light refresh, light buffer size); look at what the late frames in flight are (chunk
   meshing, the block map's reads: 14 sections a tick may be too many in flight).
2. **Frame record** on the Look page (on-time share, `/awaken look report`).
3. Far Sight off by default on Cinematic and Ultra (LookPresets, config default); Look page rows for `lensAir` and
   `lensSunPass`.
4. More atmosphere: ground mist at dawn and dusk (big soft sprites, low), beams under a canopy with the sun high (the
   sun map is too coarse there; the block map could cut the beams), a lighter night, a lighter and more pastel day ground
   (ask Euan), sharper small shadows (the light buffer's upsample is still bilinear).
5. The Air on Swift (needs a view direction: Swift has no frame texture).
6. GameTests for `LensBlocks.classify` (common-side kinds) if it moves to common code; CI green; hand-back to the manager.
7. `tools/steady60-tour.sh`: the autopilot's first-tick vsync-off is lost when his options are copied in (scripts set
   `opt vsync 0` and `opt maxFps 260` themselves).

## Tours
`FOREGROUND= SLOT=light tools/steady60-tour.sh tools/autopilot/steady60-air.txt <name>` shoots the look on the
MacBook's screen in a copy of his world; `steady60-edges.txt` is the dusk edge check; `steady60-dell-4.txt` the Dell
cost run (`tools/steady60-tour.sh tools/autopilot/steady60-dell-4.txt <name>`, then `tools/steady60-report.py`).
ImmersiveVFX's shots at the same moments are `docs/perf/steady60/page/shots/p*_vfx.jpg`.
