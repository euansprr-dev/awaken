# lands/biomes-2: hand-off (2026-10-24, cloud session)

Glass Steppe and Shattered Highlands, the second pair of Awakened biomes (docs/modules/lands.md, SPEC §15). Branch
`lands/biomes-2`, from Euan's WIP 461254c, with `origin/build/awaken-v1` (2e05a76) and `origin/pack/throughput`
(e1138f3) merged in. Part 2 (Frost Spires, Mirror Marsh, the awakening share) continues on `lands/biomes-3`.

## Done

- **Wiring audit.** The WIP commit 461254c was empty: whatever the builder was wiring into `TerrainModule` when it
  stopped never reached disk. Everything committed before it is registered: the `glass_shards`, `mirror_pool`,
  `fault_lines` and `floating_shard` feature types, the `landmark` structure type and piece, both blocks registries,
  both ambience sound events, `LandmarkCommands` and `LandmarkTests`, all through `TerrainModule`. The client's
  `LandsMotes` is driven from `FallingLeaves`, and its sprites come from the particle atlas's `particle` directory
  source. The branch compiled as it was.
- **Content regenerated.** `tools/content/lands_content.py` (which runs `lands2_content.py`), `tools/art/build_all.py
  lands lands2`, `tools/terrain/biomes.py check` and `tools/terrain/looks.py check` all agree with the committed
  files. The PNGs the art scripts rewrote were pixel-identical, differing only in encoder bytes, so they were left
  out. After merging build, the two generated leaves tags that conflicted were regenerated rather than hand-merged.
- **Tests** (Elderwood's and Lumen's kinds, for this pair):
  - `terrain_lands_placement` (new, covers every Awakened biome): reads the lands pack's Lithostitched injectors as
    data. It checks one injector per Awakened biome, with its archetype among the targets, only vanilla overworld
    targets, no target claimed twice, and a field that exists. Every `material_rule/lands/<biome>_surface|_under`
    must be spliced first by a modifier of the same name, guarded by its biome. It then applies the injectors to the
    vanilla generator the way Lithostitched's `InjectorBiomeSource` does: first match wins, and the field is sampled
    at the biome's quart corner (decompiled from the pinned 2.0.6). That runs on the five studio seeds, 3,072 blocks
    round the origin every 96, and logs each biome's share of land. Today: **21.5% of land** (19-25% a seed; the
    archetypes cover 41-46%), of it Elderwood 65%, Glass Steppe 25%, Lumen Forest 7%, Shattered Highlands 2%.
  - `terrain_landmark_shapes`: Gate Scar depth 7-11 and 8-12 rays, the gate's stumps, the crystal where the gate
    stood. Cost is now asserted on the **fastest of eight seeds** against the 2 ms hero budget, like
    `terrain_hero_blueprints`. Gate Scar 0.63 ms (VM) / 1.20 ms (CI); Shard Drift 0.90 / 0.60 ms.
  - `terrain_biome_table_census` names all four built biomes (it already covered every lands-pack biome).
  - `LandsPack.files(server, folder)` lists the pack's own files, for both tests.
- **CI flakes ported.** Two runs here failed, each on a different known flake: `anomaly_rift_ignored_closes_cleanly`
  ("the platform ticks") and `fauna_p3_caves_spawn` ("no hunter spawns in torchlight"). `pack/throughput` fixes both
  at their cause and is green on CI twice, so it is merged in here. It is a no-op once build carries it, and it
  brings the official `-Ptests=` filter.

## Measured (cloud VM, 4 cores; live-level numbers include block updates and light, so they sit above worldgen's)

- Data trees: glassbark 38 logs / 244 leaves, top 8, reach 8; tall 61 / 443, top 12, reach 11.
- Features in a live level: shard spires 153 blocks, 3.3 ms avg (one in four chunks); mirror pools 79 water blocks,
  1.5 ms; the fault painter 1.6 ms a chunk avg; floating shards 1.6 ms each.
- Landmark builds in a live level: Gate Scar 6,838 blocks over 25 chunks, 0.69 ms a chunk avg (5.0 worst); Shard
  Drift 829 blocks over 16 chunks, 0.14 ms (0.78 worst).
- Fault noise: band 3.3% of the land, 4.9 crossings per km, mean run 6.7 blocks, 45% of the band opens.
- Sites on seed 777 (vanilla): Gate Scar 11 of 40 candidates, Shard Drift 11 of 14.

## Left

- **The Mac tours** (no GPU in the cloud). Run them in this order:
  1. Looks, a Vulkan contact sheet: `PACK=perf,world TECTONIC=awakened RD=24 tools/autopilot.sh
     tools/autopilot/lands-biomes2.txt 1800`. It covers noon, golden hour and night, from the ground and from 60 up,
     the Gate Scar and the Shard Drift from 80 and at the foot. Review the sheet honestly against the six signatures in
     docs/modules/lands.md: silhouette, ground, night light, air (glints, sparks, dust), sound and the Lens look
     easing in.
  2. Frame time beside the vanilla archetypes: `PACK=perf,world TECTONIC=awakened tools/autopilot.sh
     tools/autopilot/lands-biomes2-perf.txt 1500`. This shoots the densest Glass Steppe at its Gate Scar against
     vanilla savanna, and the night Highlands at a Shard Drift against windswept hills, two passes each. The budget is
     p50 ≤ 9.1 ms at 2880x1800, within about half a millisecond of the archetype. The closing `lands2cost` line gives
     the real worldgen cost (≤ 2 ms a shape; ≤ 0.5 ms a chunk for features).
  3. The real Awakened share: `tools/terrain/studio.sh awakened survey`, which runs Tectonic and Lithostitched and
     prints the `awakened` stat. Compare it with the 21.5% the placement test models on vanilla shapes.
- `hearth_why_not_reasons` failed once in a full local run here and passed alone. `pack/throughput`'s hand-off saw
  the same once on CI, cause not established (its room shares a chunk-aligned plot with other tests). It is not in
  terrain and not touched here.
- The awakening share (today ~21% on vanilla shapes, 10-18% in the studio) toward the plan's 40%, and the third
  pair: `lands/biomes-3`.

## CI

See the last section, filled when the run is green.
