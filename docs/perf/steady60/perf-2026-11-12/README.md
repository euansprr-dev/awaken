# Steady 60 after the shadow fix (12 Nov 2026, overnight)

Euan, 11 Nov: "the FPS is now at like a pretty stable like 50 to 55, which is weird because before we were getting
over 60. So let's see what we can optimize even more to get a stable 60. Without reducing any of the quality."

Branch `claude/lens-steady-60-perf-1de2f5`, cut from 5a1f0582b (the tree shadow fix). Nothing merged. To play it:

```
/Volumes/MEDIA/Awaken-play/tools/play.sh claude/lens-steady-60-perf-1de2f5 --pack
```

Every number here is from his own world (a copy of New World V20), his pack and his settings (Cinematic, Far Sight
off, Lens temporal off, Tiles on, Vivid), on the Dell (U2725QE, 3840x2160 at 120 Hz, Pixels at 0.85), his morning hour
(time 1950, held), in the jungle where he stood (-1535 72 -725) and at the tree whose shadow he reported (18 blocks
west of the trunk, looking at it). "GPU" is the GPU's world pass with Lens's post chain (`pixels gpu`), the number that
decides the frame here: the game with Lens off is 2.8 ms of it. Data: `data/` (each tour's summary, frame traces and
`report.txt`, tools/steady60-report.py; twin checks in `twincheck.txt`, tools/lens/walkcompare.py).

How it was measured: tours 1 to 3 ran fullscreen on the Dell while he was up. His Mac's screen locked when he went to
bed, and macOS gives a locked session no fullscreen Space (tours 4 and 5 stayed in an 854x480 window), so tours 6 to 11
(`perf12-f` to `perf12-k`) put the game on the Dell's whole 3840x2160 without a border (autopilot
`display DELL borderless`): the same pixels. Two things differ there. The game with Lens as written measured about
0.6 ms more GPU than fullscreen had (18.4 against 17.7 ms where he stood; a warmer machine or the window being
composited), and the window never went past 60 fps, vsync or not (the locked screen's compositor), so above 60 the
GPU time is the measure. Every before and after below comes from the same tour, alternating.

## Result

Tour 9 (`data/perf12-i`, tools/autopilot/steady60-perf12-i.txt), the chain as written (as he plays it today, `lens twin
off`) against this branch (the twin with its calm composite, the walk's skips), each window 10 to 25 s, alternating:

| | as written | this branch |
|---|---|---|
| where he stood, still | 49.0 / 49.0 fps, GPU 18.66 / 18.66 ms (p99 19.56 / 19.52) | 60.0 / 60.0 fps (the cap), GPU 13.83 / 13.66 (p99 14.89 / 14.91) |
| where he stood, as he plays (vsync, 60 cap) | 48.6 fps, GPU 18.77 (p99 19.71) | 59.9 fps, GPU 13.98 (p99 14.39) |
| at the tree, still | 50.0 fps, GPU 18.24 (p99 19.32) | 60.0 fps, GPU 12.83 (p99 14.23) |
| at the tree, turning | 47.0 fps, GPU 19.62 (p99 21.86) | 59.8 fps, GPU 14.45 (p99 16.38) |
| at the tree, turning as he plays | 47.1 fps, GPU 19.45 (p99 21.81) | 59.6 fps, GPU 14.05 (p99 15.80) |
| running through the trees | 45.2 fps, GPU 19.98 (p99 23.14) | 57.5 fps, GPU 14.99 (p99 18.02) |
| running, as he plays | 45.0 fps, GPU 19.99 (p99 23.77) | 57.3 fps, GPU 14.61 (p99 18.83) |

Standing and turning: 60, with the GPU at 13 to 14.5 ms of the 16.7 a frame has. Running: 57.3, not a steady 60 in
this setup. Two things to know about that number. Its slow frames (p99 18 to 19 ms against a p50 of 14.6 to 15) are
the stretches of the run that look west, away from the morning sun, where the game and Lens both draw more: not
anything Lens does now and then, which tour 10 checked frame by frame (next section). And this tour ran in the borderless window his locked screen left (see "How it
was measured"), where a frame took about 2.5 ms more than the GPU's world pass; in fullscreen, tour 1, it took about
1.3. So in his own fullscreen game, running should land nearer 60 than 57.3, but I could not measure that tonight:
please run through those trees and look at the fps. If it still dips, the choices below are what is left, each with
what it costs.

## Running's slow frames (tour 10)

Tour 10 (`data/perf12-j`, tools/autopilot/steady60-perf12-j.txt): the same four running legs (east, west, east, west,
28 blocks each at running speed, from -1560 69 -715) with this branch's Lens and with Lens off, alternating, raw and as
he plays, each frame's GPU time written down with what Lens did in that frame (`perf-traces/gpu_*.txt`, read by
tools/lens/slowframes.py; the marks are set where LensLit, LensSunMap, LensHeightfield and LensBlocks upload or start a
sweep, and in LensClient's tick).

| GPU p50 / p95 / p99 | Lens on (this branch) | Lens off |
|---|---|---|
| running, raw (two windows) | 14.64 / 17.37 / 17.82, 14.47 / 17.06 / 17.89 | 5.34 / 7.73 / 9.27, 5.35 / 7.68 / 8.96 |
| running, as he plays | 13.87 / 16.88 / 18.17 | 5.47 / 7.65 / 8.67 |
| standing at the start of the run | 13.39 / 14.40 / 14.52 | 5.20 / 7.56 / 9.05 |

Frames over 16.7 ms by frame time say nothing here: with Lens off, the GPU at 5.3 ms, 36 to 38% of frames still took
over 16.7 ms in the locked screen's borderless window (its compositor paces them), against 47 to 51% with Lens on. The
GPU time is the measure.

**Lens's periodic work is not on the slow frames.** For each thing Lens does now and then: its share of all frames
against its share of the slowest 5%, and the mean GPU time of the frames that carried it against those that didn't
(running raw 1, raw 2, as he plays):

| Lens's work in that frame | of all frames | of the slowest 5% | mean with / without, ms |
|---|---|---|---|
| the lit air's upload (`LensLit`, 2 MB each time it re-sweeps) | 4.4 to 4.5% | 3.3, 3.4, 4.9% | 14.50 / 14.85, 14.27 / 14.43, 14.10 / 14.28 |
| the lit air's sweep starting | 4.3 to 4.5% | 3.3, 1.7, 3.3% | 14.54 / 14.85, 14.23 / 14.44, 13.97 / 14.29 |
| the sun map's rolling rows | 34.2 to 34.7% | 28.3, 33.9, 34.4% | 14.85 / 14.83, 14.52 / 14.38, 14.29 / 14.27 |
| the sun map's sweep starting | 4.2 to 4.4% | 0, 3.4, 8.2% | 14.69 / 14.84, 14.52 / 14.42, 14.28 / 14.28 |
| a heightfield upload (chunks crossed) | 0.7 to 2.4% | 0, 0, 1.6% | 13.85 / 14.86, 14.27 / 14.43, 14.39 / 14.28 |
| a block map upload | 5.2 to 5.6% | 6.7, 3.4, 3.3% | 15.02 / 14.82, 14.62 / 14.41, 14.36 / 14.27 |
| block map sections read | 5.1 to 5.4% | 6.7, 3.4, 3.3% | 15.07 / 14.82, 14.60 / 14.42, 14.37 / 14.27 |
| a tick, in `tickBudget()`'s 150 µs mode | 34.2 to 34.7% | 28.3, 33.9, 34.4% | 14.85 / 14.83, 14.52 / 14.38, 14.29 / 14.27 |

A frame with any of them is within 0.3 ms of a frame without, as often faster as slower, and none is over-represented
among the slow ones beyond a frame or two of chance. The 150 µs mode was on for every tick of every window (the
borderless window's paced frames look late to the governor), so it can't single out a frame either. `lens cost` for
the same windows: Lens's tick work is 0.12 to 0.22 ms of CPU for the heightfield, 0.20 for the block map, 0.02 for the
lit air; the sweeps run on their worker (9 to 11 ms and 5 to 7 ms, off the render thread).

**The slow frames are where the run looks west.** 95 to 98% of running's slowest 5% fall in the two legs facing west
(yaw 90, the morning sun behind the camera):

| GPU p50 / p95 / p99 | looking east | looking west |
|---|---|---|
| Lens on (three windows) | 12.98 to 13.41 / 14.98 to 15.18 / 16.01 to 16.82 | 15.62 to 16.23 / 17.39 to 17.63 / 18.11 to 18.52 |
| Lens off | 4.45 to 4.66 / 6.77 to 6.94 / 7.63 to 8.64 | 5.62 to 6.00 / 8.00 to 8.22 / 8.88 to 9.76 |

Looking west the game's own work is 1.2 ms more and Lens's 1.5 ms more (8.4 to 8.8 ms east, 9.6 to 10.4 west): the
view is sunlit leaves and ground from the eye out to the far trees, and Lens's light works on every lit pixel. That is
the same cost on every frame of that view, the picture itself, so there is nothing to spread or move off those frames.
Looking east the run holds 60 (p99 16.0 to 16.8); looking west its median is already 15.6 to 16.2 and frames go past
16.7. Only the choices further down would buy that back.

## What cost what (before)

Tour 1 (`data/perf12-base`, tools/autopilot/steady60-perf12.txt, fullscreen), the chain as it stood at 5a1f0582b:

| | GPU p50 | fps uncapped | frames over 16.7 ms |
|---|---|---|---|
| where he stood, still | 17.72 ms | 51.5 | 95.5% |
| at the tree, still | 17.55 | 51.8 | 90.2% |
| at the tree, turning | 18.66 | 48.7 | 98.3% |
| running through the trees | 19.24 | 46.1 | 94.1% |
| as he plays (vsync, 60 cap), still / turning / running | 17.64 / 18.66 / 19.08 | 51.7 / 48.8 / 46.4 | 95.7 / 97.6 / 93.8% |
| Lens off | 2.76 | 229 | 0% |

Each pass by leaving it out (probe chains, tools/lens/probes.py): the composite 11.5 ms, the light pass 1.8 (its walk
about 1.0, the lit air 0.2), the bloom prefilter 0.45, the rays 0.43, the bloom 0.33, the far clouds 0.27. The shadow
fix's own cost is the walk (the Sun Pass off saves 0.95 ms, the lit air off 0.23): it is not what took him under 60.
The composite has grown since its 11.9 ms at 0.75 scale (the block storm, Glasswater, the hand's depth, Lite) and now
runs at 0.85 of 4K.

Inside the composite (tour 2, `data/perf12-b`, each part left out at the tree, against 17.42 ms):

| part left out | saves |
|---|---|
| its light (sun, shade, AO, crisp edges, cover) | 3.66 ms |
| the clouds (far and near) | 2.62 |
| the near clouds' walk alone | 2.12 |
| the Air's sky and fog | 1.74 |
| the Air's water (no water in view) | 1.46 |
| the rays and the scatter | 0.95 |
| the Air's fog alone | 0.73 |
| the crisp light near the eye | 0.44 |
| the cover under roofs | 0.34 |
| the grade | 0.34 |
| the under-cover floor | 0.07 |

The near clouds' walk saving 2.1 ms at a view where nearly every pixel leaves it at once, the water 1.5 ms with no
water in view, and the parts summing to more than the whole: the composite was not slow for what it did but for its
size. A GPU keeps as many pixels in flight as its registers allow; a shader that needs more registers runs fewer at
once and waits longer on every read. Tour 3 (`data/perf12-c`, tools/lens/regs.py) found where its registers went:
each `F()` and `P()` (the frame's and the look's values) chose per pixel between Tiles' uniform block and Lens's
texture, so each value was a per-pixel register, some 60 of them held through the whole pass. Reading them from the
block alone saved 3.6 ms at the tree, and leaving out the development views (a debug switch's values held too) 4.2 ms
with both.

## Changes

1. **The Tiles twins** (commit 771753199; SPEC §22 "Lens on Apple", "The Tiles twins"). Each lit chain has a twin
   (written from it by tools/lens/block.py) whose composite reads the block alone and has no development views;
   LensClient runs it while Tiles is handing the block over (his setting: Tiles on), and the chain as written whenever
   Tiles is off or failed, or a development view is on. 4.0 to 4.6 ms of GPU where he stood, at the tree, turning and
   running (tour 6, `data/perf12-f`).
2. **The walk's reads** (commit 742827e15; SPEC §22 "The walk's reads"). The Sun Pass's walk steps through the block
   map cell by cell; it now skips the reads whose answer it already has (an open map of 4x4x4 blocks under the block
   map, and the block texel it last read). 0.2 ms at the tree (17.49 → 17.28). Bit for bit the same answers.
3. **The calm composite** (SPEC §22 "Lens on Apple", "The calm composite"). The twin's composite still carried the
   code for rain's wet, storm cells, heat's shimmer, frost's rime, the motion smear, a holo panel's focus, the tear and
   the old water, all off on a calm day, and code it never reaches still costs registers. Each twin chain now carries a
   second composite without those paths, just before its own; every frame Tiles leaves one of the two out: the calm one
   runs only when the frame's values (the very ones both composites read) have every one of those switches off. No
   chain switch, so nothing resets when rain starts or a panel opens.
   Tour 8 (`data/perf12-h`), the twin's own against the calm one, two pairs each: 0.76 ms where he stood (14.32 →
   13.55), 0.83 at the tree (13.81 → 12.98), 0.87 running (15.63 → 14.76).
4. **Lite's paths out of the twins** (SPEC §22, "The Tiles twins"): Cinematic's and Ultra's chains never run as Lite,
   so the twin's composites leave its branches out. 0.1 to 0.2 ms, bit for bit (tour 7's twin check: 0 pixels).

## Proof the picture is unchanged

- **The walk** is the same bit for bit: tools/lens/walkcheck.py runs the walk as it stood at 5a1f0582b beside the new
  one on every pixel that walks, in the same frame, and marks any pixel where the two differ in status, exit distance
  or answer. Ten frames (morning, noon, dusk and late afternoon suns; the tree, where he stood, looking west; 3 to 50%
  of pixels walking): **0 pixels differ** (`data/perf12-b/walkcompare.txt`).
- **The twin** is the same picture to far under the scene's own noise, but **not bit for bit**, and you should know
  why. tools/lens/twincheck.py runs the composite as written and the twin's composite on the same frame from the same
  inputs and marks every pixel where the two differ (with its neighbours, three pixels wide, so a mark survives the
  upscale; magenta one step of 8 bits, yellow 2 to 4, cyan more). Tour 6 (`data/perf12-f/twincheck.txt`, 3840x2160,
  marked pixels of 8.3 million, a mark three pixels wide, so the pixels that differ are about a ninth to a third of
  these):

  | view | one step | 2 to 4 steps | more | of the shot |
  |---|---|---|---|---|
  | where he stood | 4,226 | 6,226 | 21,091 | 0.38% |
  | the tree | 2,489 | 3,464 | 11,991 | 0.22% |
  | looking west | 1,464 | 1,602 | 7,459 | 0.13% |
  | where he stood, night | 5,104 | 4,180 | 2,596 | 0.14% |
  | where he stood, Ultra | 4,379 | 6,259 | 22,232 | 0.40% |
  | a pool of still water | 4,961 | 8,366 | 30,384 | 0.53% |
  | the control (crisp light left out) | 2,073,752 | 72,127 | 142,680 | 27.6% |
  - Why they differ at all: the twin makes the same reads of the same values, but once Apple's shader compiler knows
    a value is the same for every pixel it orders some sums differently, so a result can differ in its last bit.
    Most pixels don't care. A few sit exactly on a threshold, chiefly far leaves, where the composite picks the light
    buffer's neighbour whose stored distance best matches the pixel's, and two neighbours tie: there the last bit
    picks the other, equally right neighbour (`data/perf12-f/t_own_crop.png`: the marks on the far trees against the
    sky; nowhere near the eye, nothing on the water's surface).
  - The control: the composite as written, with every one of its values nudged one bit (`_nudge`), marks 0.4 to 1.8%
    of the shot, more than the twin does (fewer of them big). The picture as written already turns on its last bit
    there, and in play the sun moves every tick, which shifts every one of those values by far more than a bit.
  - Can it be pinned? (tour 11, `data/perf12-k/twincheck.txt`, tools/autopilot/steady60-perf12-k.txt) GLSL's
    `precise` on every sum that leads to that pick (the unprojection, the distance, its one-byte code, the bilinear
    weights and the distance match; PROBE_PRECISE in lens_frame.glsl and lens_composite.glsl, which keeps each of
    those in source order, none fused) does not make the twin exact. Each against the composite as written on one
    frame, marked share of the 4K shot:

    | view | the twin as it is | the twin with `precise` | both with `precise` | the composite as written with `precise`, against itself without |
    |---|---|---|---|---|
    | the tree | 0.215% | 0.215% | 0.207% | 0 |
    | where he stood | 0.385% | 0.391% | 0.393% | 0.0008% |
    | looking west | 0.148% | 0.142% | 0.131% | 0.0008% |
    | the sky | 0.450% | 0.456% | 0.447% | 0.0024% |
    | Ultra, the tree | 0.206% | 0.208% | 0.208% | 0.0003% |
    | Ultra, where he stood | 0.384% | 0.376% | 0.383% | 0.0003% |

    `precise` is honoured (it moves a handful of pixels in the composite as written), but the twin's marks stay where
    and what they were, so its rounding is not in those sums: it comes in wherever a value from the block meets
    arithmetic, all through the composite. Pinning all of it would mean `precise` on nearly every line, or Metal's fast
    math off for every shader in the game (MoltenVK's own setting, untried), which would also move the picture as
    written's rounding and cost time across the board. Not a cheap way, so the twin stays as it is: Euan decides from
    the numbers above whether a frame where 0.13 to 0.53% of the shot is marked (some 1,200 to 4,900 pixels of 8.3
    million differ, chiefly far leaves), a fraction of what one tick of the scene changes, counts as the same picture. (The control, the crisp light left out: 8.4% of the shot marked;
    the composite as written against itself: 0.)
  - The twin's two halves alone (tour 5, `data/perf12-e`, 1708x960): without the development views alone is exact,
    **0 pixels** in every view; from the block alone is where the rounding comes in. The composite as written against
    itself: 0 (the check of the check).
  - The scene's own noise for scale (tour 7, `data/perf12-g`, the chain as written, two frames one tick apart, nothing
    moved): 76 to 80% of pixels differ, 24 to 32% by more than 4 steps (the leaves, the water and the clouds move every
    tick). The twin's marks are a fraction of a percent.
- **The calm composite** against the twin's own on one frame (tour 8, `data/perf12-h/twincheck.txt`, eight views:
  the tree, where he stood, the sky, west at morning and dusk, the sky at night, Ultra at the tree and where he
  stood): 39 to 241 pixels marked of 8.3 million, some 5 to 27 real pixels, each one step of 8 bits but for a single
  pixel in two views (a far leaf's edge in shade). A hundredth of what the twin itself shows. In rain the frame is not
  calm and the twin's own composite draws it (`data/perf12-h/p_rain_small.jpg`).
- **Lite's paths out**: 0 pixels at the tree and where he stood (tour 7, `data/perf12-g/twincheck.txt`).
- Before and after stills of the scene (A B A B A, 5 s apart, `data/perf12-c/stills.txt`) differ across by no more
  than the scene differs from itself between two frames 10 s apart.

## Tried and dropped, or not done

Measured and dropped (tour 7, `data/perf12-g`, each against the twin's composite in the same probe chain, drift between
the first and last window taken out):

- **Every pass from the block** (the light, clouds, prefilter, rays and sky passes read the block alone, as the twin's
  composite does): 0.1 ms where he stood, 0.3 at the tree, 0.1 running. Five more twin shaders, each with the twin's
  rounding, for a tenth of a millisecond: not worth it.
- **The lean twin as a chain of its own** (what became the calm composite): the same 1 ms, but a chain switch whenever
  rain starts or a panel opens resets the chain's histories (a visible blip). Carrying both composites in one chain
  costs no switch, so that is what was built.
- **More of the walk's reads skipped** (beyond the open map): every further skip changed some answers, so none kept.
- **Far Sight's paths in the twin**: under 0.2 ms, and they are off with Far Sight off already.
- **The far clouds pass** while running: costs nothing measurable there (15.11 ms without it, 14.99 with it).

Where running's extra time goes (tour 7, the start of the run, standing against running the same legs): Lens costs the
same 9.7 ms either way (with it 14.36 standing, 14.99 running; without it 4.63 and 5.27). The extra 0.6 ms is the game's
own terrain while moving, which this branch does not touch. The composite is 6.9 ms of Lens's 9.7, the light pass 1.75
(the Sun Pass's walk about 1.0), everything else together 1.05. Tour 10 splits the run by where it looks: Lens costs
8.4 to 8.8 ms looking east and 9.6 to 10.4 looking west, the game 4.5 against 5.8 ("Running's slow frames" above).

## Choices that would cost something (not applied)

No exact win of any size is left that I could find: the composite is now 6 ms of Lens's 9.7, and what it does
there is the picture (its light, the clouds' walk, the Air's sky, fog and water). These would buy the rest, each costs
some of the picture, and none is applied. Tour 9 (`data/perf12-i`), each against this branch in the same tour (running
base 14.58 / 14.60 ms, 57.3 / 57.4 fps; where he stood 13.91 / 13.84 ms):

| choice | what it costs | running: GPU, fps | where he stood: GPU |
|---|---|---|---|
| Pixels 0.80 (his is 0.85) | 11% fewer pixels drawn before the upscale: a little softer | −0.86 ms, 58.9 fps | −1.16 ms |
| Pixels 0.75 | 22% fewer pixels: softer still | −1.89 ms, 59.7 fps | −2.00 ms |
| the Sun Pass off | shadows from the height map alone: no block-shaped shadows, no sun under trees, through doorways or under arches | −0.67 ms, 58.9 fps | −0.96 ms |
| Lens temporal on (his is off) | clouds worked out one frame in four, light every other frame; a fast turn settles over a few frames | nothing (14.58) | −0.14 ms |

Pixels' "steady" (the pass that stops far detail shimmering) is already on in his settings and measured the same as
the base. A day-only composite (the calm one without the night sky's code, which draws nothing by day) measured
0.1 ms against 13.9 (`own_pcalm` / `own_pnoday`), inside the noise, so it was not built.

## Tools added (dev only)

- `tools/lens/regs.py`: composite variants that say what its size costs (from the block alone, without the
  development views, the calm one, without Lite's paths, every pass from the block, without the night sky's code).
- `tools/lens/walkcheck.py` and `tools/lens/twincheck.py`: same-frame proofs for the walk and the composite, with
  a rounding control (`_nudge`) and the check of the check (`_same`); `tools/lens/walkcompare.py` counts their marks
  and compares stills.
- `tools/lens/probes.py` now builds the probe chains from the twins, with more composite parts
  (cover, crisp, water, air, air fog, near clouds, under).
- Autopilot `lens twin off|setting` (the chain as written, or the twin), `lens calm off|setting` (the twin's own
  composite every frame, or the calm one on calm frames; says which the last frame was) and `display <name> borderless`.
- Tours `tools/autopilot/steady60-perf12.txt`, `-b`, `-c`, `-d`, `-f`, `-g`, `-h` (the calm composite), `-i` (the final
  before and after, and the choices), `-j` (running's slow frames, Lens on and off), `-k` (`precise` and the twin).
- `pixels gpu` now also writes each frame's GPU time with what Lens did in that frame (`perf-traces/gpu_<window>.txt`;
  `Pixels.mark` in LensLit, LensSunMap, LensHeightfield, LensBlocks and LensClient's tick, a no-op unless timing is on),
  read by `tools/lens/slowframes.py`; `tools/lens/twincheck.py` has `_precise_twin`, `_precise_both`, `_precise_self`.
