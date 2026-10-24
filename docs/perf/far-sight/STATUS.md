# Far Sight: status

**PAUSED 2026-10-25 ~00:20** at Euan's request ("Pause far sight … make sure no work from far sight is lost").
Branch `pack/far-sight`, pushed to origin. Spec: docs/SPEC.md **§20** (renumbered from §16 at the pack session's
request). Plan: https://claude.ai/artifact/LvAoTbYB82o6ggcHS73rX1.

Euan's finish line (2026-10-24, through the pack session): finish Far Sight to one fixed line, then stop, with no
open-ended tuning. Turbo and Pixels are stopped; don't depend on Turbo.
1. Tour stage 1 (`tools/autopilot/farsight-gen.txt`, light);
2. the look upgrades from REPORT.md: cliff walls, terrace edges, block texture, canopies. The bar is "looks like the
   real terrain from afar";
3. pack integration: Far Sight in the horizon profile, replacing Distant Horizons (`pack/mods.json`, `tools/pack.py`).
   Agree it with the pack session (local_95821df4) first.
Then merge build/awaken-v1, push, get CI green, hand off the branch with the CI run id, and go idle.

## Done
- **Stage 0: passed** (REPORT.md, measured from `36f28c2` with display=built-in and minimized=false). Cheaper than
  vanilla RD 32 in every scene while drawing to 16 km. Horizon ring 6 in 0.4–0.8 s, rings 3–6 in 1.5–2.2 s. No
  seams. OpenGL works.
- **Stage 1, built and in CI** (run 40114472514 on `2c4f3bd`: all 379 tests pass):
  - the generated layer: `FarCollector`, `FarChunk`, `FarStore`, the region cache on disk, two client atlases;
  - edits re-summed on chunk save;
  - water depth (shallows show their floor);
  - landmarks (`FarLandmarks`, gate pillars through `Gates.near`).
- **Look upgrades, built** (`aecaa47`, all shaders pass glslang). Toured once (`tools/autopilot/farsight.txt`): forests
  read as canopies, cliffs show dirt and stone. Not yet measured for frame time; see "Open" below.
- Merged build/awaken-v1 at `ddda85a` (`2c4f3bd`).

## Half-done (the WIP commit)
- A debug view, `farsight debug 1`, tints generated-layer cells magenta. The status line adds
  `generated=<uploaded>/<asked>`. The stage 1 tour takes magenta shots too.

## Open: the exact next step
1. **The generated layer doesn't reach far patches.** Stage 1 tour, 2026-10-25 00:13:
   - The store holds the gold slab correctly. Decoding `r.0.0.dat` gives top 141 and gold for all 16 chunks, so the
     collector, `FarChunk` and the disk cache work.
   - From 1.6 km away, the magenta view shows generated cells only in a band just past the render distance around
     the camera. The patches over spawn (ring 3) show the prediction, both before and after rejoining, though
     `generated=` counts about 12k uploads.
   - Suspects, in order:
     - (a) `FarField.place` doesn't ask for a generated patch for a slot whose patch was already expected
       (`expect == key`) once the store gained data. Only `chunkChanged` refreshes those, and only for chunks that
       change while the patch is in its window and not hidden.
     - (b) at join the store is empty, so `mayHave` is false for every slot. On rejoin the store is fresh but
       `known` comes from the directory listing: check that `place` runs after `FarStore.of(level)` builds it.
     - (c) `chunkChanged` maps a chunk to `floorDiv(chunkX * 16, patchBlocks)`: check it against the ring's window.
   - Fix, then rerun `farsight-gen.txt` (light) and look at `gen_1d` and `gen_3d`: the slab and the land around spawn
     should be magenta from 1.6 km.
2. Measure the look upgrades once: `tools/farsight-ab.sh farsight16` from this branch, compared with REPORT.md's
   farsight16 and vanilla32. The status line read `gpu_ms` 3.3–3.6 (timestamps now report on MoltenVK; they read 0
   before). If frames cost noticeably more, move the terrace-edge neighbour lookups into the bake (the shade texel's
   free alpha: 2 bits per direction).
3. Pack integration (finish line step 3), agreed with the pack session first.

## Where things are
- Bench checkout pinned at the stage 0 commit: `/Volumes/MEDIA/Awaken-farsight-bench` (detached at `36f28c2`). Its
  shots were copied into docs/perf/far-sight/shots/ (git-ignored). Remove it with `git worktree remove` when done.
- Shader check without the game: inline `#include`s and run `glslangValidator -V --target-env vulkan1.2
  --auto-map-bindings --auto-map-locations` (the session's script inlined `minecraft:` includes from `.ref/assets`
  and `awaken:` includes from the repo).
