# Steady Light: where the build stands

Euan approved the whole plan on 10 Nov ("go ahead with the entire plan ... nearly on par with immersive VFX ... a stable
60 but extremely high quality"). He found the plan page's proof of concept "basically like vanilla": the bar is
ImmersiveVFX's sky, clouds, gradient, depth and life. All five decisions are yes (Far Sight off by default on Cinematic
and Ultra; the cloud march replaced; Lens's clouds whatever the Clouds setting says; VFX Look left alone; stage 3 now).

Pages: plan https://claude.ai/artifact/4UjwicVDJEoS9vceUanMyQ, first-pass pairs https://claude.ai/artifact/Rt4U8jC2pCgf7Nc6axTXZg.

## Done (branch claude/look-steady-60-plan-889268)
- **The Air, first pass** (b9947aa8d), setting `lensAir` (default on), autopilot `lens air on|off|setting`:
  - `shaders/include/lens_air.glsl`: `airSky`, `airFog`, `airStars`, `airHour`. Frame texel 22 = toward the sun + on,
    23 = the game's sky colour + star brightness (`LensFrame.air`, written in `LensClient.writeFrame`).
  - `lens_composite.glsl` (LENS_LIGHT): the sky replaced for sky pixels (sun, moon and the game's stars kept as what the
    frame held over its own sky colour), fog by `1 - exp(-3.2 t²)` toward `airFog(dir)`, off under cover (`lensOpen`);
    key and shade stronger in `lensLight`; the sunlit mist x2.7.
  - `lens_clouds.glsl` / `lens_clouds.fsh`: block clouds (`cloudCell`, `cloudBlock`, `blockPiece`: a cell-by-cell walk,
    12-block cells, tiers of 7, sigma 0.16, no history blend when walked). Cinematic's cloud buffer is 800x450.
  - `lens_light.fsh`: shadows asked from the texel grid.
  - `LensParticles`: FIREFLY (dusk and night) and POLLEN (day) on the spore sprite; more sunlit dust.
- Cost (MacBook at 4.7 MP, `steady60-ab.txt`): Cinematic 9.03 ms with the Air off, 10.11 on, 9.61 on with temporal;
  Swift 6.17. The Dell run `dell-4` was spoiled by another session's client: rerun `steady60-dell-4.txt` for clean numbers.

## To do, in order
1. SPEC §22 "Steady Light" section; a clean Dell run; `tools/steady60-tour.sh` fix: the autopilot's first-tick
   vsync-off is lost when his options are copied in (set `opt vsync 0` and `opt maxFps 260` in scripts).
2. **Sun Pass** (stage 3): cached shadow map from our own caster meshes (reuse `farsight.voxel`), texel-snapped
   look-up; falls back to the heightfield.
3. **Budget** (stage 2): frame record on the Look page (on-time share), the governor, workers giving way;
   Far Sight off by default on Cinematic and Ultra (LookPresets and config default).
4. Tuning from Euan's eye: day ground lighter and more pastel? clouds flatter or as they are? a lighter night.
5. Swift: the Air's fog and sky need a view direction there (no frame texture on Swift today).
6. Tests (Lens GameTests still pass), CI green, hand-back note; the manager merges.

## Tours
`FOREGROUND= SLOT=light tools/steady60-tour.sh tools/autopilot/steady60-air.txt <name>` shoots the look on the
MacBook's screen in a copy of his world; ImmersiveVFX's shots at the same moments are `docs/perf/steady60/page/shots/p*_vfx.jpg`.
