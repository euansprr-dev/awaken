# Three frame-budget builds (approved by Euan, 26 Oct 2026: "Start all three ideas now")

The page: https://claude.ai/artifact/PmqZcfLiAeotjsa1vFTkFB. The numbers: docs/perf/PERF-26OCT.md on pack/perf-26oct.

At the Runners' Roost on Cinematic, Euan's world size (4.7 MP, the Dell at render scale 0.75), the GPU's world pass is
~21 ms:
- Sodium terrain at RD 24: 8.5 ms;
- Far Sight: ~5 ms (City Sight ~1 of that);
- Lens 2 Cinematic: ~5.4 ms;
- steady + upscale: ~2 ms at 4K out.

Ultra adds ~5.5-6 ms of Lens. The targets: Cinematic 100+ fps p50 (10 ms), Ultra 60+ (16.7 ms). Euan's condition: no
softer textures and no visible quality loss.

## Rules for all three builders

- **Effort and paths:** Opus at effort xhigh. Use absolute paths. Read CLAUDE.md, docs/SPEC.md (§20 Far Sight, §22
  Lens 2, §18 Pixels) and this file. Verify every Minecraft, NeoForge or renderpearl API in docs/api/ and
  /Volumes/MEDIA/Awaken/.ref/src first.
- **Base:** start from origin/build/awaken-v1 with origin/pack/perf-26oct merged in. pack/perf-26oct changes
  `FarRenderer.chunks` and `VoxelLayer` (sort scratch, bulk upload), so builds A and C must sit on it.
- **Machine lock:** wrap every Gradle run in `SLOT_WHO=<you> /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build
  tools/gradle.sh 900 <args>`, and every tour in `slot.sh tour`.
- **Tour budget:** one Mac perf tour per hand-back, under ~10 min. No "before" tour: the before is the 26 Oct tour
  (`tools/autopilot/perf-26oct.txt`, its numbers in PERF-26OCT.md). Run the same script, with your own windows added.
  Ask the orchestrator before the tour; Euan may be playing.
- **Keep the look:** behaviour and look stay as they are unless stated. Same-moment shots before and after (your own
  switch on and off in one tour) prove it.
- **Switches:** every new path gets a runtime switch (an autopilot line and a client config key), and the old path
  stays as the fallback.
- **Wrap-up:** commit small, with the attribution line from CLAUDE.md; push your branch; CI runs the GameTests. Write
  the result into SPEC in the same commit as the code.

## A. A cached horizon for Far Sight (Far Sight owner; branch pack/far-horizon)

- **Why:** Far Sight costs ~5 ms of GPU per frame at the Roost (its own `farsight status` gpu_ms 4.7-6.4) to redraw
  7 rings of 256×256 cells to 16 km, plus thousands of far trees, every frame. Land 2 km+ away barely moves: walking
  30 blocks shifts a ridge 3 km out by under a pixel.
- **What:**
  - Render the rings from ~2 km out (rings 3-6, or whichever split measures best) into a cached panorama around the
    camera: a cube map, or a cylinder plus a cap, with colour and depth.
  - Refresh it a slice per frame (one face or a band) so the cost is spread out. Redraw it fully, still spread over
    frames, when the camera has moved past a threshold (~32-64 blocks horizontally, more with height) or the sun or
    weather has changed enough.
  - Each frame, draw the panorama into the sky pass with depth, so Lens's fog, haze and clouds and the near rings
    still sit right against it. The near rings (to ~1 km) and City Sight stay live.
- **Keep:** the same pixels at rest. Cross-fade or dither a refreshed slice so it never pops. No seam between the
  cached and live rings: overlap them and let depth decide.
- **Watch:**
  - Lens reads Far Sight's depth for haze (`lens_far_probe`), so the cached depth must feed it the same way.
  - Pixels' render scale changes the target size; the cache must not depend on it.
  - Fast flight with Wings or Sovereign: refresh more often at speed and height, and log how often.
  - Video memory: keep it within ~100 MB, and log what it takes.
- **Prove (in one tour):**
  - the Roost, the countryside and a 30 s flight, each with the cache on and off: GPU world pass, `farsight status`,
    frame p50/p99;
  - same-moment shots, cache on and off, at the Roost and mid-flight.
- **Target:** Far Sight ≤ 2 ms at the Roost.

## B. Temporal Lens 2: light and clouds updated in part each frame (Lens owner; branch pack/lens-temporal)

- **Why:** Lens Cinematic costs ~5.4 ms at the Roost (Cinematic 20.9 vs Swift 15.5 GPU ms). Ultra adds ~5.5-6 ms on
  top. The passes already run at fixed reduced sizes and keep a history:
  - Cinematic: `lens_light` 960×540 with `lens_hist`; `lens_clouds` 560×315 with `lens_clouds_hist`; `lens_sunmap`
    512²;
  - Ultra: 1440×810, 800×450 and 1024².
  So the cost is the per-pixel marching (sun shadows through the heightfield, AO, volumetric clouds), all of it
  recomputed every frame. Shaders: `assets/awaken/shaders/post/lens_light.fsh`, `lens_clouds.fsh`, `lens_sunmap.fsh`,
  `lens_composite_lit.fsh`. Chains: `assets/awaken/post_effect/lens_cinematic.json` and `lens_ultra.json`. CPU side:
  `dev.awaken.lens.client`.
- **What:**
  - First, time each Lens pass on the GPU (timestamps like `PixelsTiming` or Far Sight's) and log them per pass, so
    the split is measured rather than guessed.
  - Then interleave the expensive marches: the clouds update one pixel in four each frame (a 2×2 pattern turning with
    the frame counter); the light and shadow march updates one in two. Every other pixel reprojects from history
    using the previous frame's view-projection, with a neighbourhood colour clamp and a depth test that rejects
    disocclusions (steady's technique, in `turbo.pixels.PixelsSteady`).
  - Reset history on a cut: a teleport, a dimension change, a large rotation in one frame, a Look switch.
  - Consider whether `lens_sunmap` only needs re-rendering when the sun direction moves past a small angle. The sun
    moves slowly.
- **Keep:** still and slow views identical; a fast 180° turn settles within ~4 frames. No ghost trails behind moving
  mobs or the player's arms, and no shimmering cloud edges.
- **Prove (in one tour):**
  - the Roost on Cinematic and on Ultra, interleaving on and off: each pass's GPU time and the frame;
  - a scripted fast turn (autopilot `turn`) with frames saved before and after;
  - same-moment shots, on and off.
- **Target:** Lens Cinematic ≤ 2.5 ms and Lens Ultra ≤ 5.5 ms at the Roost.

## C. Far blocks handed to City Sight (Far Sight owner, after A; branch pack/far-handoff)

- **Why:** Sodium terrain is 8.5 ms at RD 24, and on Apple's GPU most of that is triangles, not pixels (Pixels
  report: only ~1/3 of the world pass scales with resolution). Sodium draws full-detail facade meshes to 384 m,
  where a block is 2-3 pixels at 4K. City Sight (`farsight.voxel`, `farsight.client.VoxelLayer`) already captures and
  meshes the same chunks in levels (`VoxelMesher.LEVELS = 4`). This is the direction of Far Sight 3, "detail
  follows the air" (SPEC §20).
- **What:**
  - Sodium keeps the near ~12 chunks; City Sight draws the city and terrain beyond it from its own meshes; Far Sight's
    rings carry on from there.
  - The vanilla render distance in the pack's Cinematic default drops to match. Entity draw distance gets its own
    value, so people, mobs and gate pillars beyond 12 chunks still draw.
  - Dither or cross-fade the Sodium-to-City Sight border so no seam shows.
  - City Sight's look (face colours and tints, `VoxelLooks`) must hold up at 200-400 m against the textured blocks
    it replaces.
- **Watch:**
  - `FarRenderer.chunks` / `sectionsBuilt` decide where City Sight steps aside today; that logic inverts at the
    border.
  - Simulation distance (12) is separate and stays.
- **Prove (in one tour):**
  - the Roost, the market avenue looking down its length, and a flight over the city, hand-off on and off: GPU world
    pass and frame;
  - same-moment shots at the border, for Euan to judge.
- **Target:** terrain plus City Sight ≤ 5 ms at the Roost, with no seam visible in the shots.

## Free check for whichever tour runs first

Add `mdi off` (vanilla's multi-draw-indirect, which MoltenVK may emulate slowly) for one Roost window, beside an
`mdi on` window.
