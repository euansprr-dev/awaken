# Spike: waving leaves under Sodium 0.9.3-beta.1

*Later (2026-10-26):* the same override now sways every plant too (SPEC 15, "Swaying plants"): plant vertices
carry alpha 213..253 (`terrain.PlantSway`), leaves keep 254, and the corner holds 27 texels.

Spike for SPEC 15 ("Waving leaves only if the Sodium spike proves them safe"), branch `lands/wave-spike`, 2026-10-24.

## Verdict

**Safe, with a version gate: ship it.** Leaves wave under Sodium 0.9.3-beta.1 through a built-in resource pack that
replaces Sodium's terrain vertex shader, offered only when the loaded Sodium is exactly that version with byte-identical
terrain shaders, and absent everywhere else (any other Sodium, Iris, the vanilla renderer), so an update turns waving
off instead of breaking terrain. No mixin, no reflection, no texture change: the marker is an alpha byte baked into leaf
models in code, the wind reaches the shader through four texels in a free corner of the block atlas. Measured cost in a
dense dark forest: within noise, at most about 0.05 ms p50 (budget 0.3 ms). Works on OpenGL and Vulkan (MoltenVK).

## How it works

Three parts, switched together by `dev.awaken.terrain.client.WavingLeaves`:

1. **Shader override.** `src/main/resources/resourcepacks/awaken_waving_leaves` is a built-in client resource pack
   holding one file, `assets/sodium/shaders/blocks/block_layer_opaque.vsh`: Sodium's own terrain vertex shader with a
   sway added for marked vertices (every change is marked `AWAKEN` in the file). Sodium builds its terrain pipelines
   from `sodium:blocks/block_layer_opaque` through the game's shader manager, so a pack higher in the stack replaces
   it (supported by Sodium since 0.9.0; its resource-pack scanner only warns about `minecraft:` terrain shaders). The
   fragment shader and Sodium's includes are not overridden: the pack replaces as little as possible.
2. **Marker.** `WavingLeavesModel` wraps the baked model of every block state whose block is a `LeavesBlock` (all
   vanilla leaves: oak, spruce, birch, jungle, acacia, cherry, dark oak, pale oak, mangrove, azalea, flowering azalea,
   the three poplars; and any modded or Awaken leaves that extend it) and re-bakes its quads with vertex colour alpha
   254 instead of 255. Textures, shape, tint index and lighting are untouched. The shader sways exactly the vertices
   with alpha 254 and writes their alpha back to 1.0, so nothing downstream sees the marker.
3. **Wind.** `WindTexels` writes 4 texels into the bottom-right corner of the block atlas once a frame: a signature,
   the time (seconds modulo 256, float bits), the camera to 16 blocks modulo 4096 plus the amplitude, and the wind
   (direction, steady strength, gust). *Later (living foliage, same day):* 22 texels from `terrain.Wind`: time modulo
   2048 s, the exact camera, direction, steady strength, amplitude and a 64-sample gust profile along the wind, so the
   canopy sways with the same gust fronts the falling leaves ride (docs/modules/lands.md). Sodium binds only its own uniforms, the block atlas
   and the lightmap; the atlas is the one thing Awaken can write that its shader can read. The stitcher fills the atlas
   from the top left, so the corner lies outside every sprite (checked at every stitch; if a sprite is there nothing is
   written, the signature is missing and nothing moves).

## The marker: what was tried

Sodium's chunk vertex (`CompactChunkVertex`, 20 bytes) is position (3 x 20 bits), colour (RGBA8), UV (2 x 16 bits),
light (2 x 8 bits), material (8 bits) and draw id (8 bits). No block id. Read from Sodium 0.9.3-beta.1's classes
(decompiled with Vineflower from the jar the pack installs):

| Candidate | Result |
|---|---|
| (a) colour from a `BlockColor` handler, low RGB bits | **No.** `BlockRenderer.tintQuad` multiplies the tint into the quad colour, then `CompactChunkVertex` multiplies RGB by AO x shade on the CPU (`ColorARGB.mulRGB(color, ao)`): low bits are gone. |
| (a') colour alpha from a `BlockColor` handler | **No.** Sodium's tint providers force alpha: `VanillaAdapter` returns `0xFF000000 \| colorInWorld(...)`, `FoliageColorProvider` the same. Also untinted leaves (cherry, azalea, pale oak) never call a colour handler. |
| (a'') colour alpha baked into the quad (`BakedQuad.bakedColors`, NeoForge) | **Yes: chosen.** The quad's own colour is Sodium's base colour (`BakedQuadMixin.getColor` reads `bakedColors`). The tint is multiplied component-wise `(a*b+255)>>>8`, which keeps alpha 254 against a forced 255 exactly; `mulRGB` keeps alpha; the encoder writes it straight to `a_Color.a`. Works for tinted and untinted leaves alike, needs no texture change and no model JSON override (the models are wrapped in code after baking). |
| (b) material bits / render pass | **No.** Material is 3 bits (mip, alpha cutoff) from the pass only: every cutout block (glass, grass overlays, plants) shares leaves' bits, and with the "cutout leaves" video option off Sodium forces leaves into the solid pass (`ModelBlockRenderer.forceOpaque`). Sodium offers no API to add bits. |
| (c) a reserved alpha value in leaf sprites | **No** (Euan: vanilla leaf textures stay untouched). Would also be ambiguous at mip levels. |
| (d) position sub-bits (models nudged by 1/32768 block) | Rejected: rotated elements (plant crosses) have arbitrary low bits, so false positives; needs model JSON overrides. |

Time is the second problem: Sodium gives the shader no global clock. `u_CurrentTime` is
`System.currentTimeMillis() - region.getCreationTime()`, different for every 128 x 64 x 128 render region, so it would
tear canopies at region borders and could not follow world time. Sodium's `u_Globals` holds only matrices, fog and
atlas sizes; adding a uniform needs a mixin. The block atlas is bound to the vertex stage too (renderpearl gives every
descriptor `VERTEX | FRAGMENT` visibility and merges same-named uniforms across stages), so `texelFetch` on it works.
Hence the atlas texels, written with `CommandEncoder.writeToTexture` (16 bytes a frame).

**The sway.** Each leaf vertex moves by a function of its world position (modulo 4096) and time only, so the corners two
leaf blocks share move together: no cracks (none seen in any still). World position: the shader rounds
`u_RegionOffset + camera` to whole render regions, which gives each region's origin; the camera only has to be known to
within half a region, so last frame's camera to 16 blocks is plenty. Motion: a lean and sway along the wind with a
canopy ripple travelling downwind (about 5 blocks/s), a slower cross-sway, a fast per-vertex flutter, all scaled by the
steady strength and by gust fronts rolling across the canopy. Every frequency is a whole number of turns in 256 s, so
the time wrap is seamless. Amplitude: 0.11 blocks at full strength and full gust (rain and thunder raise the steady
part), so about 0.01 to 0.03 blocks in a clear-weather breeze: a few screen pixels at 2880 x 1800. The wind itself is a
stand-in in `WindTexels` (game time only, a heading per dimension, value noise for strength and gusts); the terrain
module's wind model feeds the same texels when it exists.

## What breaks on a Sodium update, and how the gate catches it

The override is a modified copy of one Sodium file, compiled against Sodium's includes, its bind group (`u_BlockTex`,
`u_Globals`, `u_SectionTimeInfo`, `u_LightTex`, 20 bytes of push constants), its vertex format and its CPU colour path.
Sodium has rewritten these at every Minecraft port. What a mismatch would do:

- **Shader interface changes** (a renamed uniform, a new vertex attribute, a changed include): the pipeline fails to
  compile or link, `RenderSystem.getCompiledPipeline` throws ("Failed to find or load pipeline") and the game crashes
  on the first terrain draw. Silent variant: a changed vertex encoding makes the sway land on the wrong vertices.
- **Colour path changes** (Sodium starts forcing or multiplying alpha): no marker reaches the shader, leaves are still.
  Harmless.
- **Region size changes** (not 8 x 4 x 8 sections): the world-position reconstruction is off, the ripple jumps at
  region borders. Cosmetic.

The gate (`WaveSupport`) offers the pack only when **all** hold, checked once at startup:

1. Iris is not loaded.
2. Sodium is loaded and its version is exactly `0.9.3-beta.1+mc26.3`.
3. SHA-256 over Sodium's six terrain shader sources (`block_layer_opaque.vsh/.fsh`, `chunk_vertex`, `chunk_material`,
   `globals`, `fog`), read from Sodium's own jar, equals the hash of the files this override was written from
   (`61daf104...0cc8`). This catches a rebuilt or patched jar with the same version string, and any edit to the files
   the override includes or mirrors.
4. The client setting `waving_leaves` (`config/awaken-terrain-client.toml`) is on (default on).

Otherwise the log says why (`Waving leaves off: Sodium 0.9.4 is not 0.9.3-beta.1+mc26.3...`), the pack is never
added, no model is marked, nothing is written to the atlas: the game is exactly as without this feature. Porting to a
new Sodium means diffing its new `block_layer_opaque.vsh` against the copy, re-applying the `AWAKEN` blocks, checking
`CompactChunkVertex` / `BlockRenderer.tintQuad` still keep baked alpha, and updating both constants. `/awaken terrain
waving status` prints the gate's answer in game.

The wind texels need the block atlas's bottom-right 4 x 1 texels to be outside every sprite. Checked at every stitch
(`WindTexels.onBlockAtlasStitched`); if a sprite sits there (an atlas packed to the last texel), nothing is written,
the shader finds no signature and leaves stay still for that session.

## Iris, and the vanilla renderer

- **Iris loaded:** never offered (Iris swaps Sodium's terrain pipeline for its own, and Iris 1.11.7 cannot run with
  Sodium 0.9.3-beta.1 anyway: it pins 0.9.2). With a future Iris and a shaderpack on, the shaderpack draws terrain
  and the override would be unused; the shaderpack's own waving (most packs have it) takes over. `WindTexels` also
  stops writing while `Companions.shaderPackActive()`.
- **No Sodium (vanilla renderer):** leaves do not wave. Nothing is installed: no pack, no model wrapper, no texels.
  Doing it there would mean overriding vanilla's core terrain shader (`minecraft:core/terrain`), the file every
  resource pack with shaders and Sodium's own pack scanner fight over; not attempted. Every Awakened Pack look runs
  Sodium, so this only affects players without the pack. This is the agreed fallback (SPEC 15: wind, falling leaves
  and sound ship without waving).
- **Distant Horizons:** far terrain is DH's own mesh and shader: far leaves are still (sub-pixel there anyway).

## Measured cost

Same run, same cameras, alternated, `PACK=perf` (Sodium 0.9.3-beta.1, Lithium, MoreCulling, EntityCulling,
ImmediatelyFast, FerriteCore), OpenGL 4.1 Metal, render distance 16, framebuffer 2880 x 1658 (the window came up 829
points tall this run), seed 777, `/awaken fauna goto duskwood` (a vanilla dark forest; Tectonic is not on this
branch, so this is not the 8.7 ms Tectonic scene), 15 s per scene. Log: `run-autopilot/wave-perf-1/` (not committed).

| Scene | Sodium's own shader (off) | Waving on | Delta |
|---|---|---|---|
| canopy (looking down over the forest roof) | 8.28, 8.26 ms | 8.29, 8.28 ms | +0.02 ms |
| ground (eye level under the canopy) | 8.02, 7.93 ms | 8.05, 7.99 ms | +0.05 ms |

Without reloads, canopy: waving 8.29 ms, full shader with amplitude 0 8.26 ms, shader exiting early (no wind
signature) 8.23 ms. All within the run-to-run spread (about 0.1 ms), so the cost is below what `perf` resolves: under
0.1 ms, roughly 0.05 ms. Discarded: the first canopy sample of each run (taken while chunks around the new camera were
still building: 14.05 ms, tick p95 21 ms) and one canopy sample at 8.72 ms (tick p95 16.8 ms, load 9.0, more than 5%
off its two repeats). A first run with the machine at load 7 to 16 (other builders' clients and tests) gave noisy
deltas up to 1.3 ms in both directions and a minimized window at the end; it is not used.

Why it is cheap: non-leaf vertices pay one compare; a leaf vertex pays `textureSize`, four `texelFetch` (the same four
texels for every vertex, always in cache) and a few `sin`; the fragment shader is Sodium's, unchanged; no new geometry,
pass or blending; leaves were already cutout-discarded (no Apple hidden-surface removal to lose). The 16-byte atlas
write per frame is inside the "on" numbers.

## Proof

- `docs/spikes/waving-leaves/motion-on-off.jpg` (run 1, OpenGL): four stills ~13 ticks apart from one fixed camera,
  each followed by what changed since the previous one (white), and the total change over the run in red. Waving on:
  leaves change (11.8% of pixels under the canopy, 48% looking down on it), trunks, ground, grass, mushrooms and logs
  do not. Waving off (same cameras, after `/awaken terrain waving off`): 0.7% (falling-leaf particles and the water
  texture, which animate on their own).
- `docs/spikes/waving-leaves/motion-final.jpg`: the final code (4-texel layout) on OpenGL, on Vulkan (MoltenVK
  1.4.2), and the vanilla renderer without Sodium, where nothing moves (0.02%).
- Logs: the gate's answer in each set-up (`Waving leaves: Sodium 0.9.3-beta.1+mc26.3 with known terrain shaders`,
  `marked 392 leaf block states`, `wind texels at 2044,2047 of the 2048x2048 block atlas`; without Sodium:
  `Waving leaves off: Sodium is not loaded`).
- Tours: `docs/spikes/waving-leaves/wave-proof.txt`, `wave-perf.txt`, `wave-check.txt` (run with `PACK=perf`, and
  `BACKEND=vulkan` / no pack for the check); `motion_sheet.py` builds the sheets.
- GameTests: 293/293 passed on this branch (they never load Sodium; all of this is client-only).

## Open points for Euan

- **Licence.** The override is a modified copy of Sodium's vertex shader (PolyForm Shield 1.0.0, which allows changes
  and redistribution with the licence link, for anything that does not compete with Sodium; the file carries the
  notice). Sodium documents resource-pack shader replacement as supported since 0.9.0. Worth a look before release.
- **Toggling** the setting reloads resources (a few seconds, like F3+T). The setting lives in
  `config/awaken-terrain-client.toml` (`waving_leaves`, default on) and in the mod's config screen;
  `/awaken terrain waving on|off|status` (client command) switches it in game.
- **Small seams:** the crumbling overlay and the block outline do not sway with a leaf (up to ~0.05 blocks apart in a
  gust); the ripple's phase jumps once when the camera crosses a world x or z multiple of 4096.
- **Awaken's own leaves** wave automatically if their block extends `LeavesBlock` (SPEC 15 puts them in
  `#minecraft:leaves`; the marker keys on the class because tags are not bound when models bake).
- **Next Sodium:** waving switches itself off (logged) until the copy is ported: diff Sodium's new
  `block_layer_opaque.vsh`, re-apply the `AWAKEN` blocks, confirm baked alpha still survives `tintQuad` and the vertex
  encoder, update `WaveSupport.SODIUM_VERSION` and `SODIUM_SHADER_SHA256`, re-run these three tours.
