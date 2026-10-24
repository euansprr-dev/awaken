# Turbo bridge (24 Oct 2026)

Path 2 of the Far Sight plan: raw Vulkan under the Turbo contract (CLAUDE.md, SPEC §19). MacBook Pro M4, Minecraft 26.3,
NeoForge 26.3.0.95, MoltenVK 1.4.2 (LWJGL 3.4.3). Tours: `tools/turbo-bench.sh` (tour `tools/autopilot/turbo.txt`), window
854x480 on the built-in display (fb 1708x960), render distance 16, frame cap lifted (maxFps 260, vsync off); the Vulkan
swapchain still holds most runs at the display's 120 Hz, so p50 is the refresh interval and p99, p99.5, the worst frame
and the frames over 16.7 ms are what show hitches. Logs: `docs/perf/turbo/<bench>/<n>-<variant>.txt` (shots not committed).

## The gate: passed

> A compute shader fills a buffer that Minecraft draws in the same frame, with no hitch, on Vulkan; with OpenGL selected
> the game runs exactly as without Turbo.

- **Vulkan.** The probe turns Turbo on every run: `[turbo] on: Vulkan on Apple M4 (1.2.350 MoltenVK 1.4.2), MC 26.3,
  NeoForge 26.3.0.95; self-test 170-210 ms; background queue is its own queue (family 3)` (the self-test includes
  compiling its shader during loading). `/awaken turbo demo on`: right after the sky, a frame pass dispatches
  `grid.comp` (65,536 vertices, the indices appended with an atomic counter, the indexed-indirect arguments) and a normal
  RenderPipeline draws it with `drawIndexedIndirect` in the same frame. Shot: `smoke/1-all-turbo_demo_on.png` (the wavy
  sheet below the glass platform; the stepped gap on the right is the ring of quads the GPU chose not to draw).
- **No hitch.** Demo on and off, 10 s each, same view, same run (bench A):

  | Run | Demo on: p50 / p99 / worst | Demo off: p50 / p99 / worst |
  |---|---|---|
  | 1 (120 Hz) | 8.26 / 15.00 / 24.0 ms | 8.26 / 10.16 / 17.1 ms |
  | 2 (120 Hz) | 8.31 / 10.64 / 15.7 ms | 8.36 / 15.25 / 16.4 ms |
  | 3 (uncapped, 213 vs 181 fps) | 4.48 / 8.60 / 40.2 ms | 4.81 / 9.81 / 33.3 ms |

  The differences go both ways from run to run: noise, nothing the demo adds.
- **OpenGL.** The first smoke run started on OpenGL by accident (a new worktree's options.txt has no version line, and
  Minecraft's options datafix then forces the default API): `[turbo] off: the backend is OpenGL, not Vulkan`, the demo
  command answers "Turbo is off", no errors, nothing drawn (`smoke-gl-accidental/`). On OpenGL nothing of Turbo runs:
  the pipeline-cache mixin targets a Vulkan-only class, the shader mixin hands back the game's own level, the probe stops
  before touching the device.

## The demo's frame cost

Minecraft's own timestamp queries around the compute pass and the draw, read four frames later, 11 samples of 600 frames
(bench A): **GPU compute 96 µs, GPU draw 143 µs, CPU 62 µs per frame** (medians; compute 85-99, draw 91-165, CPU 30-123).
The draw is 130k triangles covering most of the screen; Far Sight's rings will be cheaper per triangle than this demo.

## Launch experiments

Each behind its own switch, measured back to back in one bench (same world and seed, fresh world each run).

### Pipeline cache (`pipelineCache`): dropped on the M4

| Bench A | Launch to loaded | Join to first frame | First 30 s p50 / p99 / p99.5 / worst | Frames > 16.7 ms |
|---|---|---|---|---|
| base | 11,455 ms | 1,058 ms | 8.22 / 14.24 / 16.23 / 29.1 ms | 12 |
| cache, from empty | 10,370 ms | 1,082 ms | 8.29 / 14.79 / 15.73 / 22.5 ms | 7 |
| cache, as saved | 10,395 ms | 1,085 ms | 8.32 / 14.87 / 15.56 / 47.6 ms | 13 |

The cache works (936 pipeline compiles go through it, 3.7 MB saved, reloaded and validated against the GPU's cache UUID)
but a full cache launches no faster than an empty one (10.40 s against 10.37 s) and the first 30 s are the same. macOS
already keeps compiled Metal shaders between launches, so what MoltenVK's cache can skip (SPIR-V to MSL) is cheap here.
The switch stays off by default (the mixin isn't applied). The code stays: drivers on Windows compile native code per
pipeline, where a cache usually matters; re-measure there before switching it on for anyone.

### Optimised shader compiles (`shaderOpt`) and MoltenVK settings: both dropped

| Bench B | Launch to loaded | Join to first frame | First 30 s fps, p50 / p99 / p99.5 / worst | Demo scene on / off |
|---|---|---|---|---|
| base | 10,531 ms | 1,208 ms | 185 fps, 4.72 / 11.07 / 12.18 / 34.5 ms | 230 / 206 fps |
| `shaderOpt` | 11,267 ms | 1,125 ms | 214 fps, 4.07 / 10.17 / 11.28 / 27.8 ms | 139 / 127 fps |
| MoltenVK settings | 10,934 ms | 1,099 ms | 125 fps, 8.13 / 14.54 / 15.65 / 31.5 ms | 164 / 171 fps |

MoltenVK settings: `USE_METAL_ARGUMENT_BUFFERS=1`, `SHOULD_MAXIMIZE_CONCURRENT_COMPILATION=1`, `USE_MTLHEAP=1`.

- **Launch:** neither is faster to load (11.3 s and 10.9 s against 10.5 s; runs vary by about 1 s). The extra
  shaderc pass over Minecraft's ~900 pipelines doesn't shorten loading.
- **In the world:** not decisive either way. On Vulkan the swapchain sometimes holds the display's 120 Hz and sometimes
  runs free, from run to run and even scene to scene (bench A: two runs at 120, one free; here the `shaderOpt` run's
  first 30 s ran faster than base and its demo scene slower). Frame rates only compare within one run's regime.
- **The demo's own GPU time**, which doesn't depend on that, is the same in all three runs (compute 84-90 µs).

No measured win, so both stay off: `shaderOpt` defaults off (its mixin isn't applied), and nothing sets the MoltenVK
variables (`MVK=` stays an experiment knob for tours).

Bench B's window opened on the Dell U2725QE (the display fix was merged; the same framebuffer, 1708x960, every run on
the same display, never minimized), so its runs compare with each other, not with bench A.

## Device extensions (for Pixels' MetalFX)

130 available, 7 enabled by Minecraft (`extensions.txt`). `VK_EXT_metal_objects` and `VK_EXT_external_memory_metal`
are available, not enabled; the `metalObjects` switch adds `VK_EXT_metal_objects` as an optional FeatureSet at device
creation. Also available and unused: `VK_KHR_present_wait` / `present_id` (frame pacing toward a steady 120),
`VK_EXT_buffer_device_address`, `VK_EXT_descriptor_indexing`, `VK_EXT_memory_budget`, `VK_EXT_pipeline_creation_feedback`.
No mesh shaders, no ray tracing.

## Notes

- Earlier runs don't count for timings: `smoke-vk-crash/` (the demo pipeline had no colour target; fixed, and every Turbo
  frame hook now switches itself off instead of propagating), the 23:14 `smoke/` run (window minimized by hand).
- The machine is shared (other sessions' builds and Euan's own client); benches run under the tour lock, so variants in
  one bench share conditions. Launch time varies about 1 s run to run.
- For Far Sight and Pixels measurements: because the Vulkan swapchain may or may not hold 120 Hz in a given run, compare
  GPU timestamps (as `[turbo] demo` and Pixels' `PixelsTiming` do) or frame times within one run, not frame rates across
  runs.
