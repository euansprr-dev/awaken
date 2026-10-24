# Far Sight: status

**PAUSED 2026-10-24** at Euan's request ("Pause all for now, I'll lyk when to start these again").
Branch `pack/far-sight`, cut from build/awaken-v1 at 8c0c359. Nothing pushed and no CI run yet.

## Done
- Read the approved plan (https://claude.ai/artifact/LvAoTbYB82o6ggcHS73rX1), CLAUDE.md, SPEC §12 and §14.
- Read the code Far Sight plugs into: `compat.FarView`, `compat.client.FarTerrain`, `compat.client.DistantHorizonsClient`,
  `lens.client.LensClient` / `LensParams`, `lens_composite.fsh`, `lens_swift.json`, and `holo.client.HoloPipelines`
  (the working example of a custom `RenderPipeline`).
- Checked in `.ref`:
  - `RenderLevelStageEvent.AfterSky` gives **no** active RenderPass. It fires inside the frame graph's "sky" pass,
    after the sky draws (`LevelRenderer.addSkyPass`, about line 389). It only fires when the skybox isn't NONE and the
    fog type isn't lava or powder snow, so Far Sight opens its own pass on the main colour target there.
  - The main target is cleared to depth 0.0 (reversed-Z) in the "clear" pass (about line 259).

## Design notes so far (not yet in SPEC)
- Far Sight can't write the main depth buffer. Its far plane (about 20 km) differs from vanilla's (RD×64), and with
  both near planes at 0.05, real chunks near the render-distance edge would fail the depth test against far terrain.
  It keeps its own depth texture instead. Real chunks overwrite its colour because they draw later against the
  cleared main depth. Lens reads that depth through `FarTerrain.pointDepthAt`, with planes and convention in texel 15
  and texel 0 b/a, the same path DH uses.
- Far terrain is discarded inside the render distance, with a dither band at the edge, so prediction errors never show
  against the sky over real chunks.
- Vanilla's render-distance fog has to move out while Far Sight draws (DH turns it off), or the chunk edge fogs to a
  band. Open question for the Look builder: Lens's haze reads `fog.renderDistanceEnd` at LOWEST priority. If Far Sight
  moves it out, Lens loses its near haze. The likely fix is for FarTerrain to also carry vanilla's render-distance end
  for Lens. That is a Lens far-terrain-input change, so merge build/awaken-v1 and tell the pack session first.

## Half-done
- Nothing in code. API checks still to do before writing: `FogRenderer` (when RenderFog fires and what gets
  uploaded), vanilla `fog.glsl`, `RenderPass` / `CommandEncoder.writeToTexture` (strip uploads), texture formats
  for the per-ring height and colour textures, how to build a custom projection uniform for a pass, and the
  worldgen side (`NoiseRouter.chunk_surface_level`, `NoiseBasedChunkGenerator.getBaseHeight`, `RandomState` on the
  client in singleplayer).

## Exact next step
1. Finish the API checks above (`.ref/src`: `client/renderer/fog/FogRenderer.java`, `renderpearl/api/commands/*`,
   `renderpearl/api/device/GpuDevice.java`, `world/level/levelgen/NoiseRouter.java`, `NoiseBasedChunkGenerator.java:136`).
2. Write the `dev.awaken.farsight` skeleton: FarSightModule and FarSightClient, ring state, the worker-thread height
   predictor, the `GpuWork` interface with a FragmentPass implementation, and the pipeline and shaders.
3. Build through `SLOT_WHO=far-sight /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 900 build`.
