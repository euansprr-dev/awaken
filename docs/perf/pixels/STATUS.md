# Pixels: status

**Steps 1–2 done (24 Oct 2026, late).** Render scale and the spatial upscalers are built, measured and handed to
the pack session for merging. Results and decision: `REPORT.md`; design: SPEC §18.

- Landed on pack/pixels: render scale behind a Turbo mixin (`dev.awaken.turbo.pixels`); upscalers SHARP (default),
  FSR 1 and bilinear; native while a Holo panel holds focus; GPU timing for tours (`pixels gpu`); interleaved
  measurement tour `tools/autopilot/pixels.txt`; `tools/pixels-report.py`, `tools/pixels-crops.py`.
- Defaults: `renderScale` 1.0 (off), `upscaler` SHARP. Recommended: 0.75 + SHARP on a 4K screen (estimate; a Dell run
  needs Euan's OK).
- Side fix, merged first: pack/autopilot-display (tours open on the MacBook's own display; `info` logs `display=`).

## Next
- Step 3 (MetalFX spatial) waits for turbo-bridge's core on build/awaken-v1: merge it, agree the
  VK_EXT_metal_objects feature-set mixin with turbo-bridge, then MTLFXSpatialScaler through java.lang.foreign. The
  bar it has to clear is SHARP (0.24 ms at 2880×1658) with FSR-class edges.
- Then the temporal write-up (jitter, motion vectors from depth and camera motion). Temporal is what brings back the
  texel detail every spatial upscaler loses.
- Until Pixels' version check moves onto turbo-bridge's probe, it uses its own (`Pixels.MINECRAFT/NEOFORGE`).
