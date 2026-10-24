# Pixels: status

**STOPPED 2026-10-25 by Euan** ("Finish Far Sight, stop the rest"): performance isn't the bottleneck (120 fps at
render distance 16 on his Dell), and he doesn't want usage spent on open-ended tuning. Resume only on his word.
Nothing is running and nothing is lost.

## Done and merged (build/awaken-v1 ddda85a)
- Steps 1–2: render scale behind a Turbo mixin (`dev.awaken.turbo.pixels`); upscalers SHARP (default), FSR 1 and
  bilinear; native while a Holo panel holds focus; GPU timing for tours (`pixels gpu`); interleaved measurement tour
  `tools/autopilot/pixels.txt`; `tools/pixels-report.py`, `tools/pixels-crops.py`. Results: `REPORT.md`; design:
  SPEC §18.
- Defaults: `renderScale` 1.0 (off), `upscaler` SHARP. Recommended: 0.75 + SHARP on a 4K screen (an estimate; a
  Dell run needs Euan's OK).
- Side fix: pack/autopilot-display (tours open on the MacBook's own display; `info` logs `display=`), b3e9bec.
- Pixels' version check is its own (`Pixels.MINECRAFT/NEOFORGE`), not yet turbo-bridge's probe.

## Not started
- Step 3, MetalFX spatial. The plan, checked as far as it could be without Turbo's core: `METALFX.md` (on
  pack/pixels, docs only, not merged). The bar it has to clear is SHARP (0.24 ms at 2880×1658) with FSR-class edges.
- Step 4, the temporal write-up (jitter, motion vectors from depth and camera motion). Temporal is what brings back
  the texel detail every spatial upscaler loses.

## If it's ever resumed: the exact next step
1. Merge build/awaken-v1 into pack/pixels, and pack/turbo too if it hasn't landed: step 3 needs Turbo commit
   319143fe or later (the `metalObjects` switch, `Turbo.vulkanDevice()`, `Turbo.vkImage(...)`).
2. With `TURBO=metalObjects=on`, answer `METALFX.md`'s two open questions first: does MoltenVK export an MTLTexture
   for Minecraft's images, and does a timeline semaphore export as an MTLSharedEvent?
3. Then follow `METALFX.md`'s frame: signal after the post effects, MetalFX on Pixels' own MTLCommandQueue, wait
   before the GUI. Keep it only if it beats SHARP in `tools/autopilot/pixels.txt`.
