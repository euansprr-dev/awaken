# Far Sight 2: plan (approved in full 2026-10-25; built the same evening)

**As built:** SPEC §20 (rewritten) and §15 "Awakened Alpine"; STATUS.md has the numbers and what was left out (Turbo
compute culling: no measured win to have; taller worlds: needs Tectonic's noise settings copied or a mixin, so summits
round off under the ceiling instead). The plan below is as Euan approved it.

Euan: "Right now it just looks super blocky and flat instead of like actual detail far away. Make it do a plan for
this that really is cutting edge for Vulkan and overall performance." The bar is his five reference shots in
`/Volumes/MEDIA/farsight-ref/` (1: Terrain Diffusion + Voxy, a sunset flight over ranges with cloud in the valleys;
2-5: Tellus + shaders, Alpine peaks, a deep valley, forested slopes, haze). This is a plan only: no production code
until he says yes. Approval page: https://claude.ai/artifact/1USNxEEEGNHTL1NAzfnKWN. Branch `pack/far-sight` (merged with build/awaken-v1 at 8906e3e7).

## 1. What makes the references work

| What you see | Which half closes it |
|---|---|
| Ridgelines, gullies, spurs and V valleys that read as real mountains, in connected ranges | **Terrain** (Awakened Lands): erosion and range-scale shape |
| Fine relief far away: no plates, no terraces, silhouettes as sharp as the pixels | **Renderer**: a continuous mesh with sub-cell normals, true float heights |
| Forests as thousands of separate trees on far slopes | **Both**: the terrain says where forest stands and how dense (treeline), the renderer draws instanced trees |
| Snow by height, slope and shade; bare rock on steep faces | **Both**: one snow rule shared by the surface rules and the far shader |
| Layered blue haze, warm sunset air, cloud lying in valleys, ridges shadowing valleys | **Renderer + Lens**: aerial perspective, far sun shadows, a valley cloud layer |
| Tall waving grass in the foreground | Neither: near-field foliage (Lands/Lens). Listed as decision 7 |

## 2. Today, measured (tour `tools/autopilot/farsight2-baseline.txt`, 2026-10-25 21:24)

MacBook M4, built-in display 2880×1800 (5.2 MP: 11% more pixels than 4K at render scale 0.75, so a fair proxy),
Vulkan (MoltenVK 1.4.2), RD 16, pack `perf,world`, Tectonic `awakened`, Lens Cinematic, G1 4 GB, seed 777. Raw lines in
`baseline2/farsight.txt`; shots in `baseline2/shots/` (git-ignored, on the approval page).

| Scene (p50 / p99 ms) | Far Sight on | Far Sight off |
|---|---|---|
| Jagged peaks, noon, Cinematic | 12.72 / 26.76 | |
| Jagged peaks, sunset, Cinematic | 12.69 / 20.21 | |
| Jagged peaks, sunset, **Ultra** | **14.79** / 20.29 | **11.74** / 14.66 |
| High flight at sunset, 20 blocks/s, Cinematic | 13.45 / 25.91 | |
| Meadow under peaks, Cinematic | 14.61 / 16.95 | |

(The render-scale 0.75 sample in this run is void: the window was minimized. The Distant Horizons half: §2b.)

What the numbers and shots say:
1. **Far Sight costs about 3 ms of frame** at sunset on Ultra (its own timer: `gpu_ms` 2.6-4.6), for terrain that reads
   as stepped plates. Each cell is a top plus two walls (12 vertices) and re-runs ~28 atlas fetch chains; 172k cells
   is ~2 M vertex invocations a frame. Stage 0 measured 1-3 ms in flatter land; mountains cost more.
2. **Far heights are 8-block steps.** Rings 3-6 sample `chunk_surface_level`, an interpolation of
   `preliminary_surface_level` (`find_top_surface` with cell height 8), so every far height is a multiple of 8. That is
   the "terraces" in every far shot. Heights are stored as whole blocks (16-bit), normals 8-bit, no sub-cell relief.
3. **The predictor got ~30× slower on the Awakened terrain.** 615-870 µs a column for both the estimate and the exact
   path (stage 0, vanilla terrain: 17-29 µs estimate). 90 s after arriving, rings 0-2 still miss 168/244/256 patches,
   and the sunset shots show **sky through the ground** where those patches are missing. Suspects: Tectonic's heavier
   router and the city basin's wrapper of `preliminary_surface_level`; P0 profiles it, P1 removes the cost.
4. **The land itself is not mountain ranges.** From a jagged peak the view is lowland, lakes and sea to the horizon; the
   peaks are isolated massifs with stair-step faces, no drainage, no spurs, no valleys between ranges. No renderer can
   draw ridgelines that the terrain doesn't have.
5. **Lens sees far terrain as sky** in its light pass: no sun shadow, no AO, no volumetric light, and cloud rays don't
   stop at far ridges. Its haze is linear distance fog, saturating about 2 km out at RD 16.
6. No trees past the real chunks (rings 0-2 get hashed treetop bumps), no forest density, no treeline.

### 2b. Distant Horizons on the same route

The same script with Far Sight off and DH 3.3.4 on Euan's Cinematic preset (192 chunks, 4 generator threads), pack
`perf,world,horizon`, same seed and places (`baseline2/dh.txt`):

| Scene (p50 / p99 ms) | Far Sight | Distant Horizons |
|---|---|---|
| Jagged peaks, noon, Cinematic | 12.72 / 26.76 | 12.19 / 54.91 |
| Jagged peaks, sunset, Cinematic | 12.69 / 20.21 | 14.46 / 21.28 |
| Jagged peaks, sunset, Ultra | 14.79 / 20.29 | 16.40 / 26.68 |
| High flight at sunset | 13.45 / 25.91 | 18.44 / 43.17 |
| Meadow under peaks, Cinematic | 14.61 / 16.95 | 20.99 / 46.47 |

- DH costs more in every scene but one, and **draws nothing past the real chunks** in the minute or two it had at
  each new place: its 4 threads were still generating (CPU load 16-24, server tick p95 up to 20 ms). Far Sight drew
  land to 16 km in seconds, badly.
- The DH run's render-scale 0.75 sample is valid: Ultra at sunset 16.40 → **12.44 ms** at 0.75 (the Far Sight run's
  0.75 sample is void, its window was minimized). Render scale is the 4K lever the budget below leans on.

## 3. Research (sources and dates in §13)

- **Voxy** (all rights reserved, "do not redistribute": learn only). GPU-driven: a compute pass walks an LOD tree by
  screen size, tests a Hi-Z pyramid, and draws through `glMultiDrawElementsIndirectCount`; 32³ sections at 5 levels; far
  faces keep real block textures. Needs OpenGL 4.6: no Vulkan, no Mac upstream (0.2.20, 2026-10-19). Its "real detail far
  away" is real chunk data that the player explored or imported; it generates nothing itself.
- **Distant Horizons** (LGPL-3.0: learn, or depend on as a separate mod). Column data, flat-colour LODs past texture
  level 8, real worldgen on worker threads (the 400 MB/s garbage in CITY-FPS.md). 3.3 added a fast surface generator
  and an API for mods to supply far terrain (Tellus uses it).
- **Terrain Diffusion** (MIT code and weights; SIGGRAPH 2026). Real-Earth ridge and drainage spacing learned from DEMs.
  Runs ONNX (CoreML on Apple), 1.5 GB VRAM, 2.5 GB RAM, 2.5 GB of models, server-side, Fabric. Too heavy to run inside
  AWAKEN; it proves the look comes from erosion and drainage, not from more noise octaves.
- **Tellus** (LGPL-3.0). Real elevation (SRTM and others), ESA WorldCover land cover, canopy heights from a 10 m tree
  map; a "fast" far generator that samples its data directly without building chunks. The look in shots 2-5 is real
  Earth data plus DH/Voxy plus a shader pack.
- **Per-column erosion** (the practical path): Rune Skovbo Johansen's erosion filter and Phacelle noise (MPL-2.0, posts
  2026-01-22 and 2026-03-30): gully stripes aligned to the slope, octave by octave, with ridge and valley masks, point
  evaluable, ~1-5 µs a column (estimate). CATTO (MPL-2.0, 2026-10-23) already ships it as Minecraft density functions
  and shows the trap: evaluate it once per column (2D cache), never per voxel. Inigo Quilez's derivative fBm (2008) gives
  the analytic normals for free.
- **MoltenVK 1.4.2 on the M4** (the game's own copy, probed from the dylib and a Metal query on this Mac):
  - no mesh shaders (PR #2842 open since 2026-09-25), no `drawIndirectCount` (merged for the unreleased 1.4.3, and it
    restarts the render pass), no 64-bit atomics, no sparse resources, no fp64;
  - multiDrawIndirect yes (each sub-draw a Metal draw, encoded in a CPU loop), bindless via argument buffers tier 2,
    buffer device address, subgroups, dynamic rendering, `VK_EXT_metal_objects` (the MetalFX door);
  - timestamps only at pass boundaries; **any compute inside a render pass splits the Metal pass** (a store and reload
    of colour and depth: ~110 MB, ~0.9 ms at 4.7 MP on the M4's 120 GB/s).
  - So Nanite-style software raster (needs 64-bit atomics) and CBT terrain (fp64) are out; GPU culling and baking in
    compute **before** the passes, feeding indirect draws with an instance count, is in.
- **Apple TBDR**: hidden-surface removal makes draw order and depth prepasses irrelevant for opaque geometry; alpha
  test and shader depth writes defeat it; vertex output is stored per tile, so triangle count costs more than on a
  desktop GPU. Opaque instanced trees beat alpha-tested impostors here.
- **Atmosphere**: Hillaire 2020's aerial-perspective LUT (32×32×32 froxels over 32 km) costs ~0.1 ms on a 2015 phone;
  horizon mapping (Timonen 2010) gives far terrain sun shadows from the heightfield; Frostbite 2016's cloud shadow
  texture is one fetch per pixel.

## 4. Recommended design

Two halves built on one shared piece: **the far height model**, a 2D function per column that both the chunk
generator and Far Sight call, so far terrain is the real terrain, cheaply and exactly.

### 4.1 Terrain: Awakened Lands "Alpine" (closes items 4 and 6; feeds 2 and 3)

1. **Ranges, not islands.** Retune the `awakened` Tectonic preset (continents scale, mountain frequency, ocean offset
   -0.55 today) so mountains come as connected ranges with long valleys between them; judge by survey maps and the
   terrain studio, not by feel.
2. **Erosion relief** `awaken:eroded` (a codec'd DensityFunction, the `awaken:city_basin` pattern): runevision's filter
   (our own Java, StrictMath so it is bit-identical everywhere; MPL-2.0 files kept as MPL if ported) applied to the
   router's surface height, masked to mountains by the peaks/valleys and erosion climate values. Gullies, spurs,
   ridgelines and drainage lines down every slope; cached once per column. It wraps both `final_density` (chunks) and
   `preliminary_surface_level` (surface rules, aquifers).
3. **Snow and stone by rule, shared with the renderer**: snow above a line that drops on north faces and lifts on
   south; no snow above ~55° slope (exposed stone, gravel scree below cliffs); a treeline by temperature and height,
   with spruce forest thick on slopes below it. One Java rule (`AlpineSurface`) used by a surface rule and by the far
   palette.
4. **Taller headroom (decision 3)**: build top 320 → 448 for new worlds if the studio shows peaks capped. Empty
   sections cost almost nothing; peaks get 128 more blocks of relief.
5. New worlds only, behind the Lands feature flag (existing worlds would seam).

### 4.2 The far height model (closes item 3; feeds 2)

- Vanilla and Tectonic heights come from 2D, flat-cached terms (`offset`, `factor`, `jaggedness`) and a depth gradient;
  the surface is where `depth × factor` crosses zero. Far Sight 2 compiles that 2D sub-graph of the router once per
  level (walking the DensityFunction tree, reading the same JSON Tectonic writes) and solves the crossing analytically
  in float, then adds `awaken:eroded`. No `find_top_surface` search, no 384-block column, no NoiseChunk.
- Target **≤ 10 µs a column** (today 615-870), float heights (no 8-block steps), and the analytic gradient for normals.
- A GameTest pins it: over 4096 columns in each archetype, |predicted − real top| ≤ 1.5 blocks mean, ≤ 6 blocks p99
  (3D noise overhangs are the rest; the generated layer replaces them once seen).
- Zero steady-state allocation: primitive arrays, pooled jobs, the store's Long boxing replaced by a long-keyed map.

### 4.3 Renderer: geometry (closes items 1 and 2)

- **A continuous mesh, not columns.** Clipmap rings stay (they work: no seams, horizon in under a second) but each
  ring becomes a regular grid with **one shared vertex per cell** and CDLOD geomorphing toward the coarser ring, so no
  pop and no wall quads. ~460k vertices a frame instead of ~2 M, three fetches each instead of ~28 chains.
- **Precision**: heights in R32F (or 16-bit at 1/16 block), normals RG16, all baked once per patch, not per vertex.
- **Sub-cell relief**: a detail normal and AO texture per ring at 4× the cell rate, baked from the erosion field's
  analytic derivatives; the fragment shader lights gullies and ridges far finer than the mesh.
- **Blocks while a block is visible.** Inside ~1.5 km a block is still more than ~1.5 px at 4K: rings 0-1 keep a
  voxel-faithful look (block-step edges, per-block colour, real faces from the generated layer) and blend into the
  smooth mesh as a block falls under a pixel. Blockiness past that is an artifact of oversized cells, not Minecraft's
  look.
- **Materials by slope, height and aspect** from `AlpineSurface` (snow, rock, scree, grass, forest floor), with the
  biome's own colours; Awakened ground blocks (glass, hoarfrost, shale, mirepeat) from the palette, not only from the
  generated layer.
- Fixes: patches never left empty (a slot always shows the coarser ring until its own data lands: the sunset holes),
  the "same key, now visible" slots asked again, a thrown generated job re-queued (audit list).

### 4.4 Renderer: far forests (closes item 6)

- A **forest field** per ring texel (density, species, crown height) from the biome's tree features and the treeline.
- **Instanced voxel trees**: per cell a deterministic jittered set of trees; three opaque LODs (a 40-triangle voxel
  tree with real trunk and layered crown within ~1.5 km, a 12-triangle crown block to ~4 km, then canopy relief in the
  terrain shading: bumps and AO in the detail texture). Opaque, no alpha test (TBDR).
- The generated layer keeps real tree positions per chunk (FarChunk gains a small tree list), so explored land draws
  the trees that are really there.
- Budget: ≤ 1.5 M triangles after culling.

### 4.5 Light and air (closes item 5)

- **Far sun shadows**: a horizon angle per ring texel toward the sun, re-baked a slice per frame as the sun moves; ridges
  shadow valleys at golden hour (reference 1).
- **Aerial perspective**: a Hillaire LUT (32×32×32, Rayleigh + Mie, sun-coloured) for far pixels, replacing linear fog
  past the real chunks: layered blue haze by day, warm air at sunset, keyed to Lens's looks.
- **Valley cloud**: a thin cloud layer that lies in low ground relative to the surrounding ridges (from the ring
  heights), marched at quarter resolution; Lens's cloud rays stop at Far Sight's depth; cloud shadows on far terrain
  from Lens's coverage texture.
- Lens reads Far Sight's shading as finished (it skips its own light pass where far depth wins) and applies only the
  composite: no double fog.

### 4.6 Performance architecture ("cutting edge" within MoltenVK's limits)

Two paths, the same images:
- **Base path (public API, Vulkan and OpenGL)**: vertex pulling from atlases, CPU culling of 16×16-cell patches
  (cheap: a few hundred boxes), instanced draws, bakes as fragment passes. This alone should cut today's cost.
- **Turbo path (raw Vulkan, under the Turbo contract)**: before the frame's render passes, one compute submission does
  the incremental bake (normals, horizon angles, min/max), Hi-Z occlusion culling of terrain patches and tree instances
  against last frame's depth (mountains hide valleys and the forests in them), and writes compacted instance lists and
  `DrawIndexedIndirect` commands with instance counts; the pass then issues a handful of multi-draw-indirect calls.
  Nothing compute runs inside a render pass. Kept only if it measures better than the base path.
- **Watch list, never required**: MoltenVK 1.4.3 (draw-indirect-count), mesh shaders (PR #2842), MetalFX temporal
  upscaling through `VK_EXT_metal_objects` (the paused Pixels path 4: the biggest single 4K win, a separate decision).

## 5. Alternatives considered

| Option | Why not (now) |
|---|---|
| Port Voxy's design (voxel LOD of explored chunks, GPU traversal) | ARR licence (learn only); needs real chunks for detail, i.e. DH's generation cost; GL 4.6. We take its ideas: Hi-Z, indirect, LOD by screen size |
| Keep Distant Horizons and style it | Its generator was most of the CPU and garbage in a new world (CITY-FPS.md); flat-colour LODs past level 8; no Awaken shading; off since 25 Oct |
| Run Terrain Diffusion inside the game | 2.5 GB of models, ONNX/CoreML, server-side; world must be generated by it. Possible later as an offline exemplar library, not now |
| Real-world data (Tellus) | A different game (Earth); not our worlds |
| Nanite-style virtual geometry / CBT | Needs 64-bit atomics or fp64, absent on MoltenVK; small gain over clipmaps for a heightfield |
| Relief ray-marching for far terrain | Depth writes from the shader defeat TBDR hidden-surface removal; normals from the bake give the same read for less |
| Alpha-tested octahedral impostors for trees | Alpha test is expensive on TBDR; opaque voxel crowns match Minecraft and cost less |

## 6. Budgets

**Frame** (the target: Ultra and Cinematic ≥ 60 fps at 4K, render scale 0.75, Far Sight to 16 km). Proxy: the MacBook
at 2880×1800, 5.2 MP, which is 11% more pixels than 4K at 0.75 (4.67 MP).

| Far Sight 2 part | GPU ms (Cinematic / Ultra) |
|---|---|
| Terrain mesh, all rings | 0.9 / 1.0 |
| Trees (after culling) | 0.6 / 0.9 |
| Bake slices, horizon shadows, aerial LUT | 0.3 / 0.3 |
| Valley cloud | 0.3 / 0.5 |
| **Total** (today ~3.0 on Ultra) | **≤ 2.1 / ≤ 2.7** |

Today's Ultra with Far Sight off is 11.74 ms p50 on the peaks at sunset; plus 2.7 is ~14.5 ms (69 fps) on the MacBook
at native size. Ultra p99 ≤ 25 ms, Cinematic p50 ≤ 11.1 ms where Lens off holds 120 (SPEC §22 budgets stand).
Render thread ≤ 0.4 ms a frame.

**Memory**: GPU ≤ 64 MB (ring textures ~7 MB, detail textures ~16 MB, generated layer ~2 MB, tree instances ~5 MB, its
depth at 4.7 MP ~19 MB); heap ≤ 32 MB steady, off-heap mirrors included; disk ~1 MB per km² explored (FarChunk + tree
lists, compressed).

**Generation**: the whole 16 km view (~460k columns) in ≤ 2 s on 3 low-priority workers after a teleport; rings 0-2 in
≤ 1 s; keeps up with 100 blocks/s flight. **CPU and GC**: ≤ 3 worker threads at minimum priority, never on the server
tick; ≤ 5 MB/s allocation from Far Sight (today: Long boxing, per-column NoiseChunks). Chunk generation with
`awaken:eroded` within SPEC §15's 20% of plain Tectonic.

## 7. Fallbacks

| When | Then |
|---|---|
| OpenGL 4.1 (Metal) | Base path: CPU culling, instancing, fragment bakes; no compute. Same look, a little more cost |
| Turbo probe fails (version, multiDrawIndirect, a validation error) | Base path, logged once; Turbo off for the session |
| MoltenVK without a capability we may add later (indirect count, mesh shaders) | Never required; ignored |
| Swift look or low memory | Rings 0-4 trees only, no valley cloud, half-rate bake |
| Distant Horizons installed | Far Sight off (as today) |
| A remote server (multiplayer guest) | Off until the server streams tiles (decision 6) |
| Not the overworld sky / instanced dimension | No far view (as today) |

## 8. Replacing Distant Horizons in the pack

DH is already off by default (25 Oct). Once P2 passes its gate, Far Sight 2 turns on by default (a client setting in the
Look screen: off / near 4 km / full 16 km), the `horizon` profile drops DH from `pack/mods.json` and `tools/pack.py`
stops writing its config. `tools/play.sh --horizon` keeps DH for one more release for anyone who wants it; Far Sight
steps aside when it is present. Agreed with the pack owner (the orchestrator) before the mods.json change.

## 9. Phases and gates

Every gate: one tour (`farsight2-refshots.txt`, five scenes framed like the five references, plus the perf scenes of
§2) and a contact sheet with each reference beside the new shot and today's. Judged by the builder against the
references; sheets sent to Euan as progress.

| Phase | What | Gate |
|---|---|---|
| P0 | Harness and fixes: the refshots tour, sheet tool, no holes, slot fixes, profile the 700 µs | Same shots, no sky through ground, cost split known |
| P1 | Terrain Alpine: ranges, `awaken:eroded`, `AlpineSurface`, mountain forests, height test | Studio sheet against refs 2, 4, 5; chunk gen within 20%; Euan sees one sheet |
| P2 | Far height model + continuous mesh + detail normals + materials | Predictor ≤ 10 µs and accuracy test; refs 1 and 3 composition; terrain ≤ 1.0 ms |
| P3 | Far forests: field, instanced trees, canopy relief, real trees from the generated layer | Refs 3 and 4: trees read one by one on far slopes; ≤ 0.9 ms |
| P4 | Light and air: horizon shadows, aerial LUT, valley cloud, Lens hand-off | Ref 1 at sunset; full budget table met |
| P5 | Turbo GPU-driven culling and bake | Kept only with a measured win over the base path |
| P6 | Pack: on by default, DH out of the horizon profile, settings, SPEC §20 rewrite | CI green; the city-fps tour shows no regression |
| P7 (later, decision 6) | Server-made tiles for multiplayer guests | |

## 10. Effort at AI pace

From git: Far Sight stage 0, stage 1 and the look upgrades (2,268 lines of Java plus shaders) took about 2 hours of
builder time (24 Oct 22:15 → 25 Oct 00:16); Lens 2 L1-L4 took an afternoon; all of AWAKEN ~30 hours. Estimate: P0 1 h,
P1 3-4 h, P2 3 h, P3 3 h, P4 3 h, P5 2-3 h, P6 1 h: **about 16-18 builder hours, 2 calendar days** with tours sharing
the Mac (about 8 tours of 10 minutes, each alone). What doesn't compress: tours queue behind other sessions' tours,
and taste passes on the sheets.

## 11. Risks

1. **New terrain seams old worlds.** New worlds only (feature flag). Euan's current world keeps its land; far view still
   improves there.
2. **Erosion cost in chunk generation.** Cached per column; measured against the 20% budget at P1; masked to mountains.
3. **Predicted trees and terrain differ from the real ones** near the hand-over (3D noise, real feature placement). The
   32-block dither band hides the swap; the generated layer replaces predictions as land is seen.
4. **Turbo touches internals.** Version-locked, probed, off on failure, kept only with a measured win (the contract).
5. **MoltenVK surprises** (pass splits, coarse timestamps). Compute stays outside passes; timing per pass; a Metal capture
   when a number is in doubt.
6. **Lens coupling.** Far Sight now shades its own far pixels; Lens changes are small (skip, clip, read the LUT) and
   go through the orchestrator.
7. **Tectonic changes** under us. We read its router at runtime and never copy its files (its repo has no LICENSE file).
8. **Machine time.** One tour per phase, alone; CI runs the suite.

## 12. Euan's decisions

1. **Approve the two-half design** (Alpine terrain + Far Sight 2 renderer, one shared height model). *Recommend yes.*
2. **New terrain for new worlds only**, behind the Lands flag. *Recommend yes.*
3. **More headroom**: let P1's studio choose between today's top (y 320) and +128 (y 448) by the shots. *Recommend
   allowing +128.*
4. **Distant Horizons out of the pack** once P2 passes (Far Sight on by default; `--horizon` keeps DH one more release).
   *Recommend yes.*
5. **Turbo raw Vulkan** for GPU culling and baking, under the contract, kept only on a measured win. *Recommend yes.*
6. **Multiplayer guests** get no far view until P7's server tiles. *Recommend accept for now, P7 right after P6.*
7. **Tall swaying grass** (the references' foreground) as its own small Lands task, not Far Sight. *Recommend yes, after
   P1.*
8. **MetalFX upscaling** (paused Pixels path 4) to buy back 4K headroom. *Recommend later, only if P4 misses the 4K
   budget.*

## 13. Sources (read 2026-10-25)

- Voxy: github.com/MCRcortex/voxy (LICENSE.md ARR; dev branch 2026-10-19), modrinth.com/mod/voxy (0.2.20-beta,
  2026-10-19; "require opengl"); Metal fork github.com/srjefers/voxy-mseries-support (2026, unverified figures).
- Distant Horizons: gitlab.com/distant-horizons-team/distant-horizons (LGPL-3.0; 3.3.4 2026-09-29; 3.3.0 2026-09-17;
  3.1.0-b Vulkan via Blaze3D 2026-06-17).
- Terrain Diffusion: arXiv 2512.08309 (v1 2025-12-09, v4 2026-05-03), github.com/xandergos/terrain-diffusion (MIT,
  2026-08-12), github.com/xandergos/terrain-diffusion-mc (MIT, 2.3.0-preview 2026-05-13).
- Tellus: github.com/Yucareux/Tellus (LGPL-3.0, v0.8.3 2026-08-18; pushed 2026-10-25), modrinth.com/mod/tellus
  (2026-09-13).
- Erosion: blog.runevision.com (Phacelle 2026-01-22; erosion filter 2026-03-30; mountain forest 2026-07-29), MPL-2.0;
  github.com/oovaa8/catto (MPL-2.0, 2026-10-23); iquilezles.org/articles/morenoise (2008).
- Tectonic: modrinth.com/mod/tectonic (MIT listed, 2026-10-18), github.com/Apollounknowndev/tectonic (no LICENSE file).
- MoltenVK: github.com/KhronosGroup/MoltenVK v1.4.2 (tag 2026-07-20; source MVKDevice.mm, MVKExtensions.def), PR #2775
  (draw indirect count, merged 2026-07-24, unreleased), PR #2842 (mesh shaders, open since 2026-09-25); local probe of
  the bundled libMoltenVK.dylib (lwjgl-vulkan 3.4.3, commit 4b715bdc of 2026-07-12) and a Metal device query.
- Apple: developer.apple.com/metal/capabilities (Feature Set Tables, 2026-05-21); WWDC20 10602 (TBDR); WWDC25 211
  (MetalFX frame interpolation and denoising, 2025-06); M4 bandwidth, Apple newsroom 2024-10.
- Techniques: Losasso & Hoppe 2004 (geometry clipmaps); Strugar 2009 (CDLOD); Haar & Aaltonen 2015 (GPU-driven
  pipelines); Karis 2021 (Nanite); Benyoub & Dupuy 2024 (CBT, arXiv 2407.02215); Hillaire 2020 (sky and aerial
  perspective, EGSR); Timonen & Westerholm 2010 (horizon mapping); Frostbite 2016 (clouds); Brucks 2018 (octahedral
  impostors).
