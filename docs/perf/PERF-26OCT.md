# 50-60 fps with hitches to 20 on Cinematic (26 Oct 2026)

Euan, build 3e1f9d11: "massive FPS hitches down to 20 fps sometimes", base 50-60 fps on Cinematic. F3 at the Runners'
Roost at noon over Carillon:
- 59 fps; p50 59, p98 34, p99.5 17 fps;
- 231 MiB/s allocated, the 8 GB heap at 72%;
- 4K Dell (1920×1080 at 2×), Pixels 0.75 steady, Vulkan, integrated server at 7.6 ms.

Targets (Euan): Cinematic 100+ fps p50, p99.5 well over 60, no hitch under 60; Ultra 60+ p50, p99.5 over 40.

## What his session showed (play log, read only)

**Contention on the Mac while he played.**
- Load average was 10-15, peaking at 23.
- The Arms builder was baking rigs in 6 Python processes (about 3.3 cores).
- Swap was at 7.3 of 8 GB; his game was 12 GB resident.
- Frame numbers taken then aren't clean (see [[builders-keep-running]]: report it and let Euan decide).

**The integrated server lagged.**
- mspt p50 was 17-25 ms, p95 53-115 ms, max 877 ms.
- There were three "Can't keep up" stalls of about 2 s each.
- Citizens were 1-1.7 ms of that tick.

**It was a new world.** The city pregen ran at 19:57, and Far Sight's rings were still generating
(`generated=54498/55941`, 1.1M column probes).

**The village planner ran inside world generation.** One hamlet plan took 5,480 ms, and a "no site" check took 431 ms.
In 26.3, structure starts run on the overworld's single worldgen executor, so nothing else in the overworld
generates during a plan.

**ZGC (since 25 Oct) is on.**

## Code audits (three read-only passes over everything merged since 25 Oct)

No per-frame allocator of ours comes near 150 MB/s. F3's rate is whole-heap and counts every thread, including the
integrated server, the worldgen workers and Far Sight's workers. Ranked:

| # | where | cost | status |
|---|---|---|---|
| 1 | Village planner inside worldgen (`VillageStructure:32` → `Plans.of`; `GenTerrain.both` evaluated two full noise columns per read) | 0.4-5.5 s per plan on the worldgen queue (measured in his log) | **fixed**: one column per read; the first hamlet's plan kept from the start-up search |
| 2 | `FirstHamlet.find` read each winning site twice | 5,063 ms at server start (measured) | **fixed** (plan() alone) |
| 3 | Far Sight: every chunk save rebuilt 7-12 patches across the rings (`FarCollector` → `FarStore.put` → `FarField.chunkChanged`) | 5-10 ms of worker time per ring-6 patch, plus uploads | **fixed**: unchanged saves are skipped |
| 4 | City Sight `sortByPage`: a `long[n]` and three `int[n]` every frame | 4-10 MB/s | **fixed** |
| 5 | City Sight mesh upload, one `putInt` per int | 1-3 ms in upload bursts | **fixed** (bulk copy) |
| 6 | Checklist card (TaskHud): whole layout, 4 regex compiles per key row and 5 compass strings per row, every frame | ~13 MB/s, 0.3-0.5 ms | **fixed** (cached layout) |
| 7 | Holo HUD text drawn 5× (4 glow copies), glyphs re-prepared each copy every frame | ~5 MB/s HUD, 15-30 MB/s with the K menu open | open, for the Holo owner |
| 8 | `FpSwings` 1.1 MB JSON parsed on the render thread at the first blade drawn | one 30-80 ms hitch | **fixed** (warmed on a worker) |
| 9 | Gate pillars open a pass on the main target every frame a gate is within 1.7 km, on screen or not | 0.3-1 ms GPU at 4K (guess) | **fixed** (frustum cull) |
| 10 | City Sight drops and re-reads chunks every 10 s when over its 16M-quad budget | 100-200 MB/s bursts | not active in his session (291k quads); open |
| 11 | City Sight section probe every other frame (`FarRenderer.chunks`), ~15-18k sections | 0.5-0.9 ms | open |
| 12 | Far terrain pushes ~17 descriptors per piece | 0.2-0.5 ms | open |
| 13 | Life `values()` clones per creature per frame | small | **fixed** |

Clean: Pixels steady (one pass, ~0.4 ms GPU, no heap use); Lens 2's CPU side; plant sway's CPU side; the wildlife
renderers; W0 and W3's per-tick work (no per-tick packets); the Atlas with the map shut; Delve; first person and Arms
at rest; audio.

## The tour (26 Oct, 20:33-20:41)

Set-up:
- `tools/autopilot/perf-26oct.txt` in a new world (seed 20261024), on the MacBook's own screen, Vulkan.
- Cinematic, ZGC with an 8 GB heap, RD 24, the pack without comfort.
- Render scale 0.95, so the world is 2736×1710 (4.7 MP), the same as Euan's Dell draws at 0.75 (2880×1620). The
  GPU's world pass is therefore his; only the upscale differs: 5.2 MP out here, 8.3 MP out on the Dell.
- JFR recorded the client.

Caveats:
- The load average fell from 19.9 to 8.7 over the windows (the new world was filling in, and Arms paused its bake
  partway through).
- The GPU's world pass drifts upward as chunks arrive: from 18.6 ms in the first window to 21 ms.
- So each system's cost is read against its neighbouring windows, and anything under about 0.5 ms is noise.

| window (Roost at noon) | frame p50 / p99 / max | GPU world pass p50 | upscale (incl. steady) |
|---|---|---|---|
| everything on | 20.72 / 31.7 / 47 ms | 18.55 ms | 1.05 ms |
| steady off | 20.69 / 36.9 / 43 | 19.30 | **0.35** |
| plant sway off | 23.52 / 47.6 / 79 | 20.90 | 1.30 |
| wildlife off | 23.35 / 38.8 / 329 | 20.75 | 1.22 |
| people off | 23.52 / 38.7 / 63 | 20.74 | 1.25 |
| HUD off | 23.10 / 39.8 / 66 | 21.11 | 1.25 |
| City Sight off | 22.75 / 40.9 / 47 | 19.88 | 1.32 |
| Lens Swift | 18.25 / 32.3 / 39 | 15.52 | 1.10 |
| Lens off | 19.10 / 32.8 / 46 | 16.51 | 1.07 |
| **Ultra** | 29.45 / 42.5 / 49 | **26.58** | 1.27 |
| Far Sight off (Cinematic) | 17.11 / 23.8 / 27 | 14.65 | 1.20 |
| bare (Far Sight and Lens off) | 11.06 / 19.2 / 24 | **8.49** | 1.06 |
| street (all on) | 21.68 / 45.6 / 89 | 19.36 | 1.33 |
| countryside, 900 blocks out | 19.76 / 38.6 / 111 | 17.21 | 1.20 |
| first hamlet | 21.49 / 43.4 / 92 | 18.20 | 1.23 |

### Where a Cinematic frame goes at the Roost (GPU-bound; the 10 ms budget for 100 fps)

| system | GPU ms (4.7 MP world) | how measured |
|---|---|---|
| Vanilla and Sodium terrain, entities, sky (RD 24 over the whole city) | **8.5** | bare window |
| Far Sight (rings, trees, mist, sea) | **~5** | Far Sight off vs its neighbours (−6.1); its own timer says 4.7-6.4 |
| of which City Sight | ~1.0 | City Sight off vs neighbours |
| Lens 2 Cinematic (light, shadows, AO, fog, clouds) | **~5.4** | Cinematic ~20.9 vs Swift 15.5 |
| Lens Swift | ≤ 1 | Swift vs off: within noise |
| Ultra on top of Cinematic | **+5.5-6** | 26.6 vs ~20.9 |
| Pixels steady | 0.7 here, ~1.2 at 4K out | upscale 1.05 vs 0.35 |
| Upscale (SHARP) | 0.35 here, ~0.6 at 4K out | |
| Plant sway, wildlife, people, HUD | each under ~0.5 (in the noise) | off one at a time |
| **Total, Cinematic** | **~21 ms (48 fps)** | everything-on windows |
| **Total, Ultra** | **~28 ms (35 fps)** | |

The Roost is the heaviest view in the game: the whole city, Far Sight's 16 km and the sky in one frame. The street,
the countryside and the hamlet ran 17-19 ms of GPU work.

### Hitches

- **Not GC.** ZGC paused 188 times for 6.7 ms in total, the longest 1.7 ms, with no allocation stalls.
- **Allocation standing at the Roost:** 106 MB/s for the whole JVM. Our code is 6.8 MB/s of that, mostly HUD text
  (`HudLook.text`, 2.4-5 MB/s, from the 5× glow).
- **Allocation while chunks generate:** the street, countryside and hamlet windows ran 437-582 MB/s, of which
  worldgen workers were 286-432. Euan's 231 MiB/s was a new world filling in.
- **The worst frames** (89-111 ms) came in those generating windows. Nine worldgen workers, Far Sight's 2-5 workers
  and Sodium's meshers share 10 cores with the render thread, and his session had a builder baking on 3 more.
- **Render thread (JFR samples at the Roost):**
  - Far Sight's `afterSky` was 17.5% of it, mostly `FarRenderer.chunks` (12.6%, about 2.5 ms a frame): it asked
    Sodium about every non-air section of 63×63 chunks every other frame. **Fixed**: a built section isn't asked
    again until its chunk goes, and the two masks upload only when a byte changes.
  - Sodium's terrain setup is ~9%; vanilla's feature preparation is ~8%.
- **Server thread:**
  - vanilla mob AI is 37%, mostly herd animals;
  - natural-spawn counting is 16%;
  - Lithostitched's biome-timeline attribute lookups are 12%;
  - nothing of ours is over 1%.
  - "Can't keep up" stalls of about 2 s came while chunks generated (and one of 29 s while the new world loaded with
    the load average at 20).

## What changed on pack/perf-26oct

- 36d9c38b: the items marked fixed in the audit table above.
- Plus `FarRenderer.chunks`: built sections cached per chunk; masks uploaded only when they change.

## For Euan to decide: reaching 100 on Cinematic and 60 on Ultra

At the Roost, Cinematic needs to lose about 11 ms of GPU work and Ultra about 12. No single system holds that, so the
budget has to come from scaling the big three by preset. Estimates come from the table above:

| lever | saves (Cinematic) | trade-off | owner |
|---|---|---|---|
| Render distance 16 with Cinematic (Far Sight carries the rest; Euan already plays at 15) | ~2-3 ms of the 8.5 (estimate) | real blocks end at 256 m instead of 384 | pack default |
| Far Sight Cinematic tier: its lighter parts (fewer tree draws, cheaper mist and sea), full on Ultra | ~2.5-3.5 of ~5 (needs its parts timed) | thinner far forests and mist | Far Sight session |
| Lens Cinematic tier: light pass and clouds at lower resolution, full on Ultra | ~2.5-3 of 5.4 (needs Lens's passes timed) | softer shadows and clouds | Lens session |
| Steady on Ultra only | ~1.2 at 4K out | a little more shimmer at 0.75 on Cinematic | Pixels |
| Render scale 0.67 on 4K for Cinematic | ~1.5-2 (pixel-bound part) | softer textures | Pixels |
| Cap world generation at 5 threads (`-Dmax.bg.threads=5` in play.sh) | hitches while chunks generate (unmeasured) | new land fills in a little slower | pack |

All of those together come to about 11-12 ms at the Roost, roughly 85-90 fps there, and above 100 in most other
places.

Ultra at 60 needs Lens Ultra's own +5.5-6 ms halved as well. That's Lens's work; the Lens session should time each of
its Ultra passes.
