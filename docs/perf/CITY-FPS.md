# Carillon at 55 fps: where the frame and the garbage go (25 Oct 2026)

Euan's playtest, 25 Oct: Apple M4, the 4K Dell at 1920×1080 ×2 (3840×2160 framebuffer), RD 32, Vulkan, the full pack
(perf, horizon, feel, sound, comfort, world), build/awaken-v1 at about f672f8f6. A new world. About 55 fps in the
city with big hitches; a 4 GB heap at 78% and about 400 MB/s allocated.

This is an investigation only. Nothing here is fixed yet; the plan waits for Euan's yes. No client was launched.

## How this was measured

1. **His own play logs** (`/Volumes/MEDIA/Awaken-play/run-pack/logs`, read only): the once-a-minute `[citizens]` line
   carries the integrated server's mspt, the macOS load average, the citizens' own cost and `gcTicks` (ticks with a GC
   pause). Two sessions: 15:36 (new world, city pregen) and 15:59 (reload).
2. **His play folder's config** (read only): `lens = "ULTRA"`, Distant Horizons on the Cinematic preset
   (`lodChunkRenderDistanceRadius = 192`, SSAO, textured LODs, 4 threads, distant generation in FEATURES mode),
   Sound Physics at 32 rays × 4 bounces, Xaero's World Map, G1 garbage collector, vsync on, maxFps 120. RD is 24 now
   (he lowered it after the playtest). The config files changed at 16:07, after the playtest, so the Look he played on
   is not certain.
3. **Two read-only code audits** (client render paths; server ticks), with every hot listener read and costs checked
   against the vanilla sources in `.ref/src`.
4. **One headless JFR run** of the city, citizens and Living Wilds GameTests (24 tests in 3.9 s). It can't reproduce
   the scene, and its per-site numbers are sampled and noisy, so it's only used for one fact: our code was about 11%
   of the allocation in that run, and the largest share of ours was `StreetNet.bake`.
5. **Earlier measurements in the repo**: `docs/perf/batch3`, `docs/perf/pixels/REPORT.md`, the Lens 2 vista numbers in
   SPEC §22, Carillon M3 (`docs/carillon/milestones/M3-alive.md`), and Euan's 24 Oct note that vanilla RD 32 on the
   Dell (DH off) holds 100+ fps.

There is **no measurement at 4K** of Lens or DH, and no client allocation profile. The page's capture closes both.

## What the logs say

| minute (15:36 session) | load avg | mspt p50 / p95 / max | GC ticks per min | citizens (bodies avg + ledger avg) |
|---|---|---|---|---|
| 1 (new world) | 17.6 | 20.9 / 95.7 / 367 ms | 39 | 1.0 + 0.18 ms |
| 2 | 19.6 | 14.4 / 47.3 / 474 ms | 16 | 0.54 + 0.10 ms |
| 3 | 14.9 | 13.2 / 49.0 / 406 ms | 12 | 0.46 + 0.12 ms |
| 4 | 19.5 | 12.5 / 41.6 / 190 ms | 18 | 0.55 + 0.14 ms |
| ~14 | 7.8 | 9.0 / 38.0 / 267 ms | 16 | 0.97 + 0.09 ms |
| ~15-18 | 9.8-14 | 2.5-8.5 / 7-22 / 65-214 ms | 0-9 | 0.07-0.29 + 0.02-0.08 ms |

- The M4 has 10 cores. A load of 17-19 means the CPU was oversubscribed nearly twice over. No Gradle build ran on the
  Mac in those first minutes (the first build log after 15:36 is at 15:46), so the game alone did that.
- The citizens are about 5% of the server tick. M3 measured them the same way (0.36 ms/tick for 40 bodies).
- GC frequency falls about tenfold as the world settles: the garbage is mostly the new world's first minutes, not
  the steady city.

## Ranked causes

### Frame time (the 55 fps)

1. **The Look's GPU cost at 4K.** Estimated 7-15 ms a frame at 4K; extrapolated, not measured.
   - Lens 2 on the Carillon vista at 1440p: off 6.8 ms, Cinematic 9.96 ms, Ultra 13.46 ms. That is +3.2 and +6.7 ms.
   - Lens is screen-space, so its cost scales with pixels. 4K has 2.25× the pixels of 1440p, which gives about +7 ms
     for Cinematic and +15 ms for Ultra. His config says Ultra.
   - Cinematic and Ultra also switch DH to its Cinematic preset (SSAO, AA, textured LODs, 192 chunks). In batch 3
     (GL, 2880×1800), the Cinematic look cost +7.5 ms over Swift.
   - Vanilla RD 32 on the same Dell holds 100+ fps (about 9 ms). 9 ms plus 7-15 ms comes to 16-24 ms, which is
     42-62 fps: what he saw.
2. **CPU contention from new-world generation.** The render thread shares 10 cores with:
   - DH distant generation (4 threads of full worldgen, out to 192 chunks);
   - vanilla generation of the roughly 2,500 RD-32 chunks outside the pregen ring;
   - Sodium's chunk meshing, G1's concurrent threads, Xaero's map and Sound Physics.
3. **Our steady per-frame client code is small:** about 3-8 MB/s, 1-2% of the garbage, with a few tenths of a ms of
   CPU. Details are in "Our code" below.

### Hitches

1. **GC pauses on a 4 GB G1 heap at 78%.** In the first minute, 39 of 1,200 ticks had a GC pause.
   - At 400 MB/s, a young GC runs every second or two. At 78% full, mixed or full collections take hundreds of ms.
   - The 8 GB heap (2a802686) removes the full-GC risk but not the young pauses.
   - DH printed its own warning in his log: "G1 Garbage collector detected. This can cause FPS stuttering."
2. **The new world's first minutes.**
   - Server mspt maxed at 367-616 ms with p95 41-96 ms, while DH generated LODs and RD 32 filled in.
   - **Lead source of the 400 MB/s:** DH and vanilla worldgen, on worker threads in the same JVM. This needs the
     capture's thread split to confirm.
3. **At night: the citizens' hand lanterns** (`citizens/Lanterns.java:19,51,82`).
   - Up to 12 real light blocks of level 11 move every 5 ticks. Each move relights 8-18 sections.
   - On the client, it marks about 60-100 render sections dirty. The render thread then copies their neighbourhoods
     (`LevelExtractor.java:166-183`, `SectionCopy.java:34,41`) and the workers re-mesh them.
   - Estimate: 1-3 ms of render thread per move, 5-40 MB/s, and chunk workers kept busy. A guess from the vanilla
     code path; the carrier count is not measured.
4. **The pregen's 1,700 chunks unload and reload at join** (`city/CityPregen.java:77,92`).
   - The pregen's `GENERATE_FORCED` tickets are dropped one by one. 26.3 has no spawn-chunk ticket (`TicketType`
     has no START), so the chunks unload and save, then load again when the player arrives.
   - Estimate: about 0.5-1 s of main thread and 100-200 MB of churn at join.
5. **The Lens heightfield uploads whole 1024-wide rows** (`lens/client/LensHeightfield.java:240-258`).
   - A single changed chunk dirties its whole row. Crossing a chunk boundary east-west dirties all 64 rows: about
     5.6 MB, uploaded as 40 `writeToTexture` calls a tick for 8 ticks.
   - Each call on Vulkan is a staging copy plus a full memory barrier. These are plausible micro-hitches while
     walking.
6. **Smaller server spikes:**
   - Wilds herds and packs can spawn inside Carillon: `living/population/Ledger.java:195` has no `CityBasin.reserved`
     check. Their `steer()` calls `moveTo` for every far body each 40 ticks (`:385-396`), giving 2-20 ms spikes in
     city geometry (estimate).
   - `StreetNet.bake` allocates about 90 KB of scratch per route and rebuilds every route on each start and when the
     Bell Bridge falls (`citizens/StreetNet.java:162-170,204,210-255`).
   - `HeroCrowns` re-lays out a queued tree every tick and can rebuild a 1.1 MB blueprint on the server thread
     (`body/ascent/HeroCrowns.java:206-227`).

### Our code, the rest (each under 2 MB/s; one cleanup commit)

| where | what | estimate |
|---|---|---|
| `citizens/client/CitizenRenderer.java:46-48`, `CitizenRenderState.java:11,16` | two `float[24]` and an `ItemStackRenderState` per citizen per frame | 1.0-1.9 MB/s |
| `citizens/client/CitizenRenderer.java:127-137` | speech: `font.width` twice per frame, each line submitted twice | 0.25-0.9 MB/s |
| `world/landmark/client/LandmarkClient.java:103-158` (night) | new list, up to 42 records, lambdas and a Quaternionf per frame | 0.7-1.2 MB/s |
| Lens Cinematic/Ultra post chain | 15 passes; vanilla `PostPass.addToFrame` allocates per pass per frame | 1-3.5 MB/s |
| `powers/client/PowerHud.java:76-151`, `LivingPlates.java:54-110`, `LivingFx.java:172`, `HoloGuiLook.card` | strings, Identifiers and Components built per frame | 0.5-1 MB/s together |

Checked and clean: the life layer (not on citizens), CitizenModel and clips, HoloWorld/HoloRender, LensFrame,
LensParams, the 10 render-state modifiers, the terrain client and leaves, Look, Pixels, every idle FX renderer;
on the server, CityBasinFunction, the city plan and layout, the ledger, Crowd, the work reconcilers, packets, and
about 45 throttled tick listeners.

### Coming with B3a (carillon/main, not in what Euan played)

- **`CityLights`** switches lit windows with `setBlock` (3 houses a tick). Each switch rebuilds a section on the
  client, so this is a burst at dusk and dawn. The audit found it causes no light updates, since the window has no
  light source. Measure it in B3a's night tour.
- **193 animated textures** (the wheel faces). Each animated sprite uploads on its frame change. Measure; don't
  assume.

## The plan

| # | fix | expected gain | risk |
|---|---|---|---|
| 1 | Hold DH's distant generation for the first ~5 min of a new world (or until the opening ends), then 2 threads at `threadRunTimeRatio` 0.5 in the Cinematic preset (`city/CityDh.java`, `pack/dh/cinematic.json`) | load ~17 → ~10 in the first minutes; most of the new-world garbage and mspt spikes gone (to confirm by thread split) | the far horizon fills in slower |
| 2 | Generational ZGC for play.sh, A/B in the capture | GC pauses from 5-50+ ms (more when the heap is full) to under 1 ms | 10-15% more CPU on GC threads and more heap headroom; measure before adopting |
| 3 | renderScale 0.75 + SHARP when the framebuffer is 4K (Pixels, merged, off) | estimated 3-6 ms on Cinematic/Ultra at 4K: Lens runs at render scale. Extrapolated from the MacBook run (0.9-1.6 ms there at 0.75) | slightly softer block textures; menus and Holo panels stay native |
| 4 | Lanterns: the glow drawn on the client (a Lens point light or an emissive billboard), no real light blocks; until then, level 7, move after ≥3 blocks, ≤4 carriers | the night's re-mesh storm gone; 5-40 MB/s (estimate) | a look change: approve on a night shot |
| 5 | The pregen keeps the city loaded until the player joins: one timed ticket (600-1200 ticks, radius ~24) instead of dropping 1,700 tickets | about 0.5-1 s and 100-200 MB at join | low |
| 6 | Lens heightfield: upload only the dirty 16×16 tile per mip | 64× less data while walking; fewer barriers | low |
| 7 | Herds and packs never inside Carillon (`CityBasin.reserved`); at most 4 `moveTo` per steer pass | removes 2-20 ms spikes in the city | low |
| 8 | Cleanups: the CitizenRenderState arrays, speech widths, LandmarkClient, PowerHud, plates, `StreetNet` scratch reuse, `HeroCrowns` layout cache | about 5-8 MB/s and a few spikes | low |
| 9 | RD: 16-20 with DH carrying the distance, as SPEC §16 already assumes | thousands fewer chunks generated, meshed and sent; Euan measured RD 16 holding 120 fps | his choice: near detail vs fps |

**MetalFX: not now.** It's a temporal upscaler (it needs motion vectors and jitter), and it would only buy GPU time
that renderScale 0.75 already buys for about 0.25 ms. The hitches are CPU and GC, which no upscaler touches (the
Pixels report: "p99 doesn't move"). Revisit if 4K Cinematic is still under 60 fps after 1-3.

## The capture (only with Euan's OK: it runs the client)

One `slot.sh tour` (it runs alone on the machine), about 12 minutes, on the Dell at 4K. Then, only if the first
shows GC pauses mattering, the same tour once more with `ZGC=1`.

- **Setup:** a new world (seed 20261024, `PACK=perf,horizon,feel,sound,comfort,world`, `RD=32`), with
  `JAVA_TOOL_OPTIONS=-XX:StartFlightRecording=settings=profile,filename=...` so JFR records the client JVM.
- **Route** (each step is a `perf` window; `perf` logs p50, p95, p99, max, tick p95 and heap):
  1. join, then 60 s standing still on the Runners' Roost (the new-world load);
  2. noon, Choir Square: 20 s each of Swift, Cinematic and Ultra at renderScale 1.0, then Cinematic and Ultra at
     0.75 SHARP;
  3. a 60 s walk down the market avenue;
  4. night (`/time set 18000`) on the same avenue with the Watch out: 30 s.
- **Read:** frame times per scene; JFR allocation by thread (Render, Server, Worker-Main, DH) and by site; GC pauses
  lined up with the frame spikes; `jdk.ExecutionSample` on the render thread.

Without the Dell, the same tour on the MacBook (5.2 MP) still answers every question but the 4K ones.

## Decision, 25 Oct: Distant Horizons off by default

Euan: remove Distant Horizons for now; our own far-terrain renderer comes later. `tools/play.sh --pack` no longer
installs the horizon profile (`--horizon` brings it back), and the next install takes out the DH jar an earlier one
put there (checked on a scratch folder). That removes cause 3's DH half and the DH part of cause 1. Fix A is no longer
needed; render distance now carries all the distance on its own.

## Built and measured, 25 Oct evening

Built on pack/city-perf: render scale 0.75 on 4K by default (`renderScale4k`), the pregen's timed `city_hold` ticket
through join, the Lens heightfield's changed-chunk uploads, and (from the tour below) generational ZGC for the play
client. The lantern fix went to the Carillon session.

Tour `tools/autopilot/city-perf.txt` on the MacBook (2880×1800, 5.2 MP), Vulkan, the pack without DH (perf, feel,
sound, world), RD 24, a new world, JFR on the client. Three runs:
- Run 1 is void: it came up on OpenGL, and the comfort profile's Dynamic FPS held the background window at 60 fps.
  The first tour in a freshly installed folder falls back to OpenGL; later ones get Vulkan. The same happened to B3a's
  pack tour today. On OpenGL, Lens Cinematic and Ultra also throw `ArrayIndexOutOfBoundsException` in Sodium's
  `GlStateManager._bindTexture` (a texture unit past 12). Euan plays on Vulkan, so neither touches him, but tours
  should drop comfort and check the backend line.
- Run 2: G1, 4 GB.
- Run 3: ZGC, 8 GB.

| scene | G1 p50 / p99 / max | ZGC p50 / p99 / max | GPU world pass (G1 run) |
|---|---|---|---|
| join, first 45 s | 8.30 / 21.3 / **513** ms | 8.27 / 15.9 / 81 ms | |
| square, Swift | 8.32 / 14.8 / 79 | 8.27 / 13.5 / 37 | 5.43 ms |
| square, Cinematic 1.0 | 10.54 / 14.6 / 91 | 10.41 / 13.3 / 38 | 10.00 ms |
| square, Cinematic 0.75 | 8.30 / 12.6 / 105 | 8.24 / 11.7 / 26 | 7.45 + 0.27 upscale |
| square, Ultra 1.0 | 14.88 / 20.3 / 95 | 13.73 / 15.8 / 19 | 14.37 ms |
| square, Ultra 0.75 | 12.47 / 17.2 / 61 | 11.36 / 13.5 / 17 | 11.57 + 0.28 upscale |
| walk east, 12 chunk crossings | 10.98 / 33.6 / 125 | 10.91 / 21.4 / 33 | |
| night, the Watch | 10.70 / 14.8 / 91 | 10.81 / 14.5 / 82 | |

- **GC was the hitch.** With G1 the pauses were 5.94 s in total over the tour: median 10 ms, p95 80 ms, p99 141 ms,
  young pauses of 96–180 ms while standing in the square, and a 417 ms full GC at join (the 513 ms frame). With ZGC
  they were 2 ms in total, the longest 0.7 ms. Every frame max dropped, and Ultra's p50 got faster. Applied:
  `runClient` (`play.sh`) uses `-XX:+UseZGC`; `-Pg1` goes back.
- **The 4K maths.** At 5.2 MP, 0.75 saves 2.3 ms of GPU world on Cinematic and 2.5 ms on Ultra, after its 0.27 ms
  upscale. That makes the part that scales with pixels about 5.8 ms (Cinematic) and 6.4 ms (Ultra); the rest is
  fixed. The Dell has 1.6× the pixels:
  - Ultra native: about 8.0 + 10.2 = 18 ms of GPU, **55 fps**, which is what Euan saw.
  - Ultra at 0.75: about 14 ms, ~71 fps.
  - Cinematic native: about 13.5 ms, ~74 fps.
  - Cinematic at 0.75: about 10 ms, ~100 fps.
  These are extrapolated from one display; the Dell itself is unmeasured.
- **Allocation by thread** (JFR, G1 run):
  - Standing in the city: 62–86 MB/s in total. The render thread is 26–37 MB/s, mostly Sodium and vanilla.
  - Our own direct allocation in the city is about 1–3 MB/s, mostly citizens' render states.
  - Join, first 45 s: 380 MB/s, of which 208 MB/s is worldgen workers. The walk (new chunks each second): 507 MB/s.
  - The pregen: 800 MB/s for 16 s.
  - So the garbage is new chunks being generated, which Distant Horizons multiplied. `CityBasinFunction` shows
    under "ours" in a naive count only because it wraps vanilla's density functions; it allocates nothing itself.
- The heightfield change and the city hold have no before/after in this tour (Lens's uploads don't show in
  allocation, and the hold acts before the first frame). Both are covered by code review, and `city_pregen_drives_chunks`
  checks the hold.
