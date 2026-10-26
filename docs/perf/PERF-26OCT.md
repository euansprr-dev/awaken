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

## The tour (pending: runs once Euan stops playing)

`tools/autopilot/perf-26oct.txt`: a new world at his settings (Cinematic, ZGC 8 GB, RD 24, the pack without comfort,
render scale 0.95 on the MacBook = the same 4.7 MP world his Dell draws at 0.75). The scenes:
- the Roost with everything on;
- the Roost with each system off in turn: steady, plant sway, wildlife, people, HUD, City Sight, then Lens Swift, Lens
  off, Ultra, Far Sight off, bare;
- a street, the countryside and the first hamlet.

Each window logs frame times, the GPU's world pass and Far Sight's status, with JFR allocation split by thread.
