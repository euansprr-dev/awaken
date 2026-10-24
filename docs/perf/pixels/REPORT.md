# Pixels, steps 1–2: render scale and spatial upscalers (24 Oct 2026)

MacBook Pro M4, built-in display, framebuffer 2880×1658–1800, Vulkan (MoltenVK 1.4.2), render distance 32, Lens
Swift, pack profile perf (Sodium and friends), no Distant Horizons: Euan's settings, on the MacBook (no Dell runs).
Tour `tools/autopilot/pixels.txt`, table `tools/pixels-report.py`, crops `tools/pixels-crops.py`. Design: SPEC §16.

## Verdict

- **Land it, off by default.** Render scale buys back real GPU time where the GPU is the limit: 0.75 saves
  0.9–1.6 ms of GPU work a frame (10–14%), 0.67 saves 1.2–2.1 ms (13–19%), 0.5 saves 1.5–2.2 ms (22–25%), all with
  the SHARP upscaler. When a scene was GPU-bound, frame p50 dropped by 0.8–1.8 ms.
- **SHARP is the default upscaler, not FSR.** On Apple's GPU only about a third of the world pass scales with
  pixels (geometry doesn't), and FSR 1's two full-screen passes cost 1.2 ms at this size, eating most of what 0.67
  saves. SHARP (bilinear plus FSR's RCAS sharpening in one pass) costs 0.23 ms and keeps the gain. FSR stays as an
  option (crisper block edges; probably better value on discrete GPUs).
- **Recommended default: `renderScale` 1.0 on the MacBook, 0.75 + SHARP on the 4K Dell.** On the MacBook's own
  screen the gain (about 1 ms, and nothing once a scene sits at the 120 Hz cap or on the CPU) doesn't pay for the
  softer block textures. At 4K the pixel-bound part grows by 1.73×, so 0.75 should save roughly 1.5–3 ms there.
  That is an extrapolation: a Dell run needs Euan's OK.
- **Menus, HUD, GUI text and open Holo panels stay native.** GUI text is pixel-identical at every scale. Holo
  panel text was visibly softer at 0.67, so the world renders native while a panel holds focus (K menu, reading
  screens); the panel then looks exactly as without Pixels.
- **p99 doesn't move.** The spikes are CPU work (chunk building at RD 32), which resolution doesn't touch.
  Far Sight and the Turbo bridge are where p99 gets bought back.

## Results (run 5, the decisive one)

Each setting was measured between two native windows, so drift cancels: a world still loading at RD 32, a busy
machine. "GPU world" is the world pass on the GPU, timed with timestamp queries: level, hand and post effects
(Lens included, since it runs at render scale). "Saved" is GPU world plus upscale, against the mean of the two
native neighbours. Frame times in ms; the native value in brackets.

| scene | setting | frame p50 | frame p99 | GPU world p50 | upscale p50 | GPU total | saved |
|---|---|---|---|---|---|---|---|
| vista | 0.75 sharp¹ | 12.26 (-1.38) | 16.33 (-33.06) | 11.39 (native 13.19) | 0.24 | 11.63 | +1.56 ms (+12%) |
| vista | 0.67 sharp | 11.48 (-1.84) | 13.70 (-11.74) | 10.65 (native 13.02) | 0.23 | 10.88 | +2.13 ms (+16%) |
| vista | 0.67 FSR² | 8.36 (-2.31) | 14.98 (+1.36) | 6.54 (native 10.29) | 1.21 | 7.75 | +2.54 ms (+25%) |
| vista | 0.67 bilinear | 8.29 (-0.32) | 14.52 (-5.48) | 6.51 (native 8.13) | 0.11 | 6.62 | +1.51 ms (+19%) |
| vista | 0.5 sharp | 8.39 (-0.34) | 14.06 (-8.54) | 6.23 (native 8.25) | 0.23 | 6.46 | +1.79 ms (+22%) |
| vista | 0.5 FSR | 8.32 (-0.39) | 15.91 (+0.82) | 5.93 (native 8.23) | 1.21 | 7.14 | +1.09 ms (+13%) |
| grove | 0.75 sharp | 9.19 (-0.85) | 17.16 (-0.78) | 8.15 (native 9.29) | 0.24 | 8.39 | +0.90 ms (+10%) |
| grove | 0.67 sharp | 8.81 (-1.10) | 15.62 (+0.20) | 7.77 (native 9.21) | 0.23 | 8.00 | +1.21 ms (+13%) |
| grove | 0.67 FSR | 9.39 (-0.35) | 11.93 (+0.38) | 7.45 (native 9.06) | 1.22 | 8.67 | +0.39 ms (+4%) |
| grove | 0.67 bilinear | 8.60 (-1.11) | 14.15 (+1.58) | 7.40 (native 9.04) | 0.13 | 7.53 | +1.50 ms (+17%) |
| grove | 0.5 sharp | 8.60 (-1.09) | 15.61 (+2.80) | 6.57 (native 9.03) | 0.23 | 6.80 | +2.22 ms (+25%) |
| grove | 0.5 FSR | 8.55 (-1.09) | 14.43 (+2.59) | 6.53 (native 9.00) | 1.21 | 7.74 | +1.26 ms (+14%) |
| ground | 0.75 sharp | 8.31 (+0.03) | 14.39 (-0.55) | 5.33 (native 6.45) | 0.24 | 5.57 | +0.88 ms (+14%) |
| ground | 0.67 sharp | 8.81 (+0.52) | 15.26 (-1.35) | 5.11 (native 6.62) | 0.24 | 5.35 | +1.26 ms (+19%) |
| ground | 0.67 FSR | 8.55 (-0.09) | 15.45 (+0.98) | 5.05 (native 6.62) | 1.22 | 6.27 | +0.35 ms (+5%) |
| ground | 0.67 bilinear | 8.85 (-0.01) | 14.91 (+2.04) | 5.65 (native 6.47) | 0.13 | 5.78 | +0.69 ms (+11%) |
| ground | 0.5 sharp | 8.46 (-0.07) | 16.52 (+1.81) | 4.70 (native 6.46) | 0.28 | 4.98 | +1.48 ms (+23%) |
| ground | 0.5 FSR | 8.29 (-0.02) | 15.44 (+0.72) | 4.40 (native 6.42) | 1.22 | 5.62 | +0.79 ms (+12%) |

¹ The window shrank from 2880×1800 to 2880×1658 after the first native window, so this row's baseline mixes the two.
² Straddles the moment the vista finished loading (native GPU world fell from about 13 ms to 8 ms between its
neighbours), so it's the least reliable row. Every other row had steady neighbours: grove natives 9.0–9.3 ms, ground
6.4–6.8 ms.

How to read it:
- **Frame time reflects the GPU only while the GPU is the limit.** The vista, while loading, and the grove were
  GPU-bound: frame p50 fell 0.8–1.8 ms with SHARP. The ground scene sat at 8.3 ms, which is the 120 Hz present rate
  that Vulkan holds on this Mac whatever the vsync setting, so there was nothing to gain in frame time.
- **The world pass is mostly geometry on Apple's GPU.** At 0.67 (45% of the pixels), the world pass loses only
  16–23% of its GPU time. The M4 draws in tiles and hides overdraw for free, so at RD 32 most of the cost is
  vertex and tiling work that resolution doesn't change. The upscaler's cost per output pixel is therefore what
  decides the trade.
- **Upscaler costs at 2880×1658:** bilinear 0.11–0.13 ms, SHARP 0.23–0.28 ms, FSR 1.21 ms (EASU's twelve taps
  plus a second full-screen pass). Clearing the target instead of loading it (free on a tile-based GPU) cut FSR
  from 1.57 ms and bilinear from 0.19 ms in run 2.

## Quality (run 5 shots, zoomed 2× nearest-neighbour, left to right: native, 0.75 sharp, 0.67 sharp, 0.67 FSR, 0.67 bilinear, 0.5 sharp, 0.5 FSR)

- [Foliage close up](crops/canopy.png) and [a far tree](crops/far-tree.png): at 0.75 and 0.67, leaf edges stay
  clean but the leaf texture's holes and grain soften. 0.67 shows a few bright speckles where sky shows through
  leaves (sub-pixel holes the lower resolution can't hold steady). 0.5 is visibly soft; bilinear is the softest
  at any scale.
- [A hillside](crops/hill.png) and [leaves against the sky](crops/leaves.png): block edges survive every
  upscaler; FSR rebuilds them a touch crisper than SHARP. What no spatial upscaler brings back is the dirt and grass
  textures' texel grain, which is finer than the lower resolution.
- [Far terrain over the sea](crops/coast.png) and [the horizon](crops/horizon.png): under Lens's haze the
  differences mostly disappear; far terrain is where render scale costs least.
- [Holo K menu](crops/holo.png): identical at native, 0.67 and 0.5, because the world renders native while the panel
  holds focus. An earlier run without that rule had softer panel text, with dark sharpening fringes at 0.67.
- [GUI text](crops/gui.png) (the Codex screen): pixel-identical at native and 0.5. The GUI always draws after the
  upscale.

## Lens before the upscale

Lens runs at render scale, first in the post effects, before the upscaler. Its depth is the world's own (no
upscaled depth needed); its bloom, haze and grade get cheaper with the pixels (included in "GPU world" above); and
FSR and RCAS expect a finished, display-referred frame. No Lens artefacts in any shot.

## How the decisive run was reached (runs 1–4, kept as lessons)

- Run 1 measured frame times only, back to back: every scale came out within noise of native. The frames were
  CPU-bound on a busy machine (load 27–45) and native drifted 15–30% across each scene as RD 32 kept loading. Frame
  time alone can't judge Pixels on this Mac, which is why the GPU timestamps were added.
- Run 2 added GPU timing (it showed FSR costing 1.6 ms) but still ran back to back, so drift remained. The game
  window was minimized partway through (by hand: it covered Euan's work), which caps the game at 10 fps.
- Runs 3 and 4 opened on the Dell (window 1440×900 unclamped, framebuffer 2880×1800) and were minimized for the whole
  run, so their data is void. That led to the autopilot display fix (pack/autopilot-display: tours move to the
  built-in display and `info` logs `display=`), and Euan now leaves tour windows alone.
- Run 5 interleaves native between every setting, on the built-in display, unminimized, at load 4–9.

## Distant Horizons

Checked with `tools/autopilot/pixels-dh.txt` (pack perf + horizon, DH Swift, RD 16, the sunset vista after a
minute of DH generation): no errors and no attachment-size failures. DH's far terrain draws at 0.67 and 0.5 with
Lens's haze over it, aligned with the vanilla terrain (rivers and coasts land in the same place in every shot): DH
draws into the main target it's handed, so it follows the lower resolution like everything else. DH was still
generating LODs, so the horizon changes a little between the three shots taken two seconds apart. Shots only, no
timing: Far Sight will replace DH.

## Next

- Step 3, MetalFX spatial, waits for turbo-bridge's core on build/awaken-v1. The bar it has to clear is SHARP:
  0.23 ms at this size, and FSR-class quality.
- The texture detail that every spatial upscaler loses is what temporal upscaling brings back (camera jitter plus
  motion vectors accumulate sub-pixel detail across frames). That's the write-up after step 3.
- One Dell run at 4K (needs Euan's OK) would turn the 4K estimate above into a measurement.
