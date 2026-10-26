# Far Sight 3: plan (for Euan's approval, 2026-10-26)

Euan, after the opening tour on 2026-10-26: "the entire approach needs to be optimized ... I really have confidence we
can find a system that massively improves this and makes this one of the best long distance horizon mods."

## 1. What he keeps seeing, and why Far Sight 2 does it

| What Euan saw | Why |
|---|---|
| Far shapes standing in the loaded city: a smooth wedge on the Roost's spire, a grey box behind the quay tower, a golden pyramid, ceilings over streets | The far view is hidden by a guess at which sections Sodium draws. The guess was a sphere 48 blocks short of Sodium's real reach, and the camera's world cell reached the shaders with z = 0 (every lookup off by the camera's 256-block row outside Carillon's). Both fixed 2026-10-26; the class of bug stays while the hand-off is a guess. |
| Seeing through the floor during the zoom-in | Inside the render distance the far view never draws (the "covered" hole). Where a real section isn't loaded or meshed yet there is nothing at all. |
| Small far shapes huge, tiny once they load | The far view is a heightfield: one height per 2 to 64 block cell. A cell takes its tallest built block, so a one-block spire becomes a cell-wide column. Unvisited land is predicted from the noise, which knows nothing of features and only the city's plan tops. |
| Appearing, disappearing, reappearing | The per-chunk mask flips as Sodium builds and culls sections (the 2 s settle rule); ring patches swap prediction for real data as chunks are summed; levels change at hard lines. |
| Teal cones over the Lumen forest | Far trees were placed with the same z = 0 camera cell. Fixed 2026-10-26. |

## 2. The idea

Where the far view meets the real world, make it the real world, block for block. Then the hand-off can't show: a
section drawn by the far view looks the same as Sodium's. Every fix in Far Sight 2 policed a seam between two different
pictures; Far Sight 3 removes the difference near the player and keeps the prediction only where a block is smaller than
a pixel.

## 3. Design

1. **Two bands.** The near band, from the player out to about 1.5 km, is built from *spans*: each column holds a few
   vertical runs (bottom, top, block, colour, light), the format Distant Horizons uses, so spires, arches, bridges,
   overhangs and walls keep their outline. The far band, 1.5 km to 16 km, stays Far Sight 2's heightfield (relief,
   forests, snow lines, real-scale air), where it already works.
2. **Real data first, kept.** Spans come from every chunk the server has generated or loaded (Far Sight 2 already
   collects them), saved per world on disk, so coming back shows the true city at once. Land never generated gets
   spans predicted from the noise, the city's plan (a span API on `City`, not just tops) and the forest model.
3. **Size-true levels.** The near band has levels of 1, 2, 4 and 8 blocks. A coarser cell keeps a run only where at
   least half of its finer cells hold it, so thin things thin out and fade with distance instead of swelling.
4. **One renderer per section.** Sodium draws its sections. The far view draws every other section at any distance:
   past the render distance, still meshing, not sent yet, under the camera during the flight. Nothing is drawn twice,
   nothing is left empty.
5. **Knowing Sodium's sections exactly.** A Turbo hook (under the contract, version-locked to Sodium 0.9.3) reads
   Sodium's own list of sections with a mesh each frame, in place of the timing guess. Its fallback is today's rule
   (Sodium's distance cylinder, per section).
6. **No pops.** Level changes cross-fade with a dither over a band; tiles rebuild only when their data changes; the
   server generates outward behind the render distance, so prediction gives way to real data out of view first.
7. **Opening preload** (Euan's idea): during the card the server loads the chunks along the flight and the near band
   for the city is built; the card lifts when the flight path is covered.
8. **Geometry.** Near-band tiles are greedy-meshed boxes built on worker threads, drawn with one indirect call per
   ring, coloured by each block's average texture colour with face shading, ambient occlusion and Far Sight's light and
   air, so the two bands and Lens read as one picture.

## 4. Proof

A **seam audit** in every Far Sight tour, at 12 fixed spots (the Roost, quay, market street, laundry lane, four flyover
frames, a forest, a sea edge, a mountain valley, a high flight):

- far terrain over a section Sodium drew: **0 pixels** (debug view 3 tints far terrain; a script counts it over the
  real-only frame's geometry);
- holes, sky where ground should be: **0 pixels** (far-only and real-only frames compared);
- size truth: thin landmarks (spires, masts) keep their real width within one level when they load.

GameTests: the span builder reproduces a generated chunk; downsampling never grows a silhouette; the plan's spans match
the built city.

## 5. Alternatives

| Option | Why not |
|---|---|
| Distant Horizons 3.3.4 back (it now exists for 26.3) | Its generator was most of the CPU and it drew nothing past the real chunks for minutes in a new world (measured 2026-10-25); flat LOD look; on Vulkan it keeps its depth, so Lens can't haze it |
| Keep patching Far Sight 2 | A heightfield can't draw a spire, an arch or an overhang, and the prediction will keep disagreeing with what loads |
| Voxy's design wholesale | ARR licence (ideas only); needs every chunk generated, the same cost as DH |

## 6. Budgets (estimates, measured in P2)

- Frame: no more than Far Sight 2 (+2.7 ms on Ultra today); the near band replaces rings 0 to 2.
- Disk: about 6 KB per explored chunk (256 columns, two to three 8-byte spans each): 60 MB for 10,000 chunks.
- GPU memory: about 40 MB more for near-band tiles.

## 7. Phases and gates

| Phase | Work | Gate |
|---|---|---|
| P0 | Seam audit in the tours; today's two root fixes | audit runs, counts reported |
| P1 | Spans: format, builder from real chunks, disk cache, size-true levels | GameTests |
| P2 | Near-band renderer: boxes, cross-fade, shading parity with the far band | audit 0 / 0 at the 12 spots; frame budget |
| P3 | Predicted spans for unvisited land (noise, city plan, forests) | flyover frames; size truth |
| P4 | One renderer per section; the Turbo Sodium hook | no hole in the zoom-in |
| P5 | Opening preload | card lifts only when covered |
| P6 | Look and performance pass; one hand-back tour | Euan's frames |

Effort at AI pace: Far Sight 2 went from approval to built in under two hours. This is larger: about an evening of
builder time, plus one tour per hand-back.

## 8. Euan's decisions

1. Build Far Sight 3 as above (recommended), put Distant Horizons back, or keep patching Far Sight 2.
2. The Turbo hook into Sodium's list of drawn sections (recommended yes).
3. Near-band reach: 1.5 km (recommended), 1 km (cheaper) or 3 km.
4. Ship today's two root fixes on pack/far-sight now while Far Sight 3 is built (recommended yes).
