# Steady Light: how the numbers were taken (10 Nov 2026)

The plan is `docs/perf/steady60/PLAN.md`; its page is linked there. This file is the method, so a later session can run
the same tours and compare.

## The setup measured

Euan's own: Apple M4 (10 cores, 16 GB), the Dell U2725QE at 3840x2160 and 120 Hz, fullscreen, Vulkan through MoltenVK
1.4.2, render distance 16, simulation distance 10, Pixels at 0.75 with SHARP (the world is drawn at 2880x1620), steady
off, Far Sight off, `lensTemporal` off, vsync on and a 60 fps cap. The pack is perf, feel, sound, comfort and world, with
ImmersiveVFX 1.7.6 and Sodium 0.9.3-beta.1; an 8 GB heap on ZGC.

Every tour copies his play copy's config folder, video options and his latest world ("New Beginnings") into this
worktree's `run-autopilot/`; his play copy is only read. He allowed tours on the Dell on 10 Nov ("You can tour on my
Dell 4k screen btw for FPS data as well").

## Tools (dev only, on this branch)

- `tools/steady60-tour.sh <script> [name]`: copies his settings in, runs the tour alone through `slot.sh tour`, and
  keeps the traces, shots, log and the machine's load before and after in `run-autopilot/results/<name>/`.
  `NOVFX=1` takes ImmersiveVFX's jar out; `JFR=1` records the client; `SLOT=light FOREGROUND=` is a shots-only tour on
  the MacBook's own screen.
- Autopilot additions (`core/client/dev/Autopilot.java`, `VfxProbe.java`):
  - every `perf` window also writes its frames, in order, in microseconds, to `perf-traces/<label>.txt`;
  - `opt vsync 0|1`, `opt maxFps <n>`, `opt clouds 0|1|2`;
  - `display <name prefix> [fullscreen]` puts the tour on a named screen (tours otherwise stay on the MacBook's);
  - `vfx dump` and `vfx key=value;key=value` set ImmersiveVFX's own settings the way its menu does, through
    NeoForge's config registry (nothing of the mod is read or compiled against).
- `tools/steady60-report.py <results dir>`: per window, fps, p50/p95/p99/max, the share of frames over 16.7, 25 and
  33.3 ms, hitches, and the GPU's world pass (`pixels gpu`).
- `tools/steady60-charts.py`: the page's inline charts.
- Tours: `tools/autopilot/steady60-dell.txt`, `-dell-2.txt`, `-dell-3.txt` (measuring) and `steady60-proto.txt` (the
  proof-of-concept look's shots).

## Reading the numbers

- A window's frame times are the gaps between `RenderFrameEvent.Post` calls on the render thread. They are when the
  game finished a frame, not when the screen showed it, so "slots" (a frame held for 1, 2, 3 or more refreshes of the
  120 Hz screen) are the gap rounded to 8.33 ms.
- "As played" is vsync on with the 60 cap, his settings. "Uncapped" is vsync off and no cap: what a frame costs.
- The GPU's world pass is from timestamp queries before the world and after the post effects (`PixelsTiming`). At his
  setup a frame is that plus about 1.6 ms.
- Each window logs the machine's 1-minute load. The first tour's first minutes ran at a load of 16 to 24 (the world
  loading, Awaken's road planner, a Time Machine backup); windows from its fourth minute on, and the later tours, ran
  at 3 to 6. Numbers quoted in the plan are from the quiet windows unless it says otherwise.
- Other sessions' Gradle builds waited outside each tour (slot.sh); their Claude processes and the desktop did not.

## The tours (10 Nov, 13:09 to 14:01)

| name | script | where | what |
|---|---|---|---|
| dell-1 | steady60-dell.txt | the Dell | VFX as played and uncapped, a VFX feature off at a time, Lens's tiers, dusk, flights (JFR on) |
| dell-2 | steady60-dell-2.txt, FARSIGHT=near | the Dell | Cinematic with Far Sight, vsync and cap four ways, VFX's fog parts and lighter shadows, flights, the Roost |
| dell-3-nomod | steady60-dell-3.txt, NOVFX=1 | the Dell | Swift, Cinematic, the proof and Lens off without ImmersiveVFX's jar |
| dell-3-asleep | steady60-dell-3.txt | the Dell | the same with the jar asleep; the hybrid; VFX's particles; VFX as played on a quiet machine |
| proto-1, proto-2 | steady60-proto.txt, SLOT=light | the MacBook's screen | the proof-of-concept look beside Cinematic and VFX (shots only) |

proto-1 crashed at noon on the build's world-generation bug (`ClearOfSettlements`, "Requested chunk unavailable during
world generation"); this branch carries a cherry-pick of its fix (ceed85eb1 from `claude/bold-antonelli-2b20b9`) so the
later tours could finish. That fix is not this plan's and merges on its own branch's terms.

Known wrinkle: `tools/glsl-check.sh` reports the Swift composite (`lens_composite.fsh`) as failing. It expands each
include once, ignoring `#ifdef`, so `lens_block.glsl` lands inside the Lens 2 block. The game compiles it fine.
