# Far Sight 3: within a pixel (approved in full by Euan, 2026-10-26: "Go with all recommendations")

Page: PLAN-3.html (published as an artifact). Every far shape should stay within about a pixel of the real thing, so
loading it in changes nothing you can see. Carillon first: the whole city drawn from its real blocks, at any render
distance, from the first frame of the flight.

Euan, 26 Oct: "The biggest issue is the city itself … half the city is using Far Sight, so half the city just looks
blocky … if you're moving super fast with flight and things are just rendering different than they looked, it takes a
lot of the consistency away."

## Fixed today (pack/far-sight)

| What he saw | Cause | Fix |
|---|---|---|
| Wedge on the Roost's spire, grey box behind the quay tower, ceilings over streets | far terrain hidden by a sphere 48 blocks short of where Sodium draws | Sodium's own rule, per section (`farRealSection`) |
| Teal cones over the Lumen forest; shapes over loaded chunks away from Carillon | the camera's world cell reached the shaders with z = 0 | x and z both reach the shaders |
| See-through ground outside the city | rings 1-5 empty a minute after a jump; empty rings fall back to 128-block cells (coast as sea) | the three below |
| | 86% of worker time in biome lookups (Lithostitched over Tectonic's climate): 178 µs a column vs 32 on vanilla | biomes every second corner, at least 8 blocks apart; real-chunk patches only where trees stand |
| | rings asked for their hidden middles first | rings ask only for land they draw |
| | 2 paced workers even while the view was empty | up to 5 unpaced workers while > 64 patches are missing |

## Why more is needed

A heightfield (one height per 2-128 block cell, mostly predicted) can't draw facades, arches or gaps between houses
(half the city blocky), takes a cell's tallest block (thin things swell), and predicts land without its trees and
structures (things change when they load, worst in fast flight).

## Can the whole city stay loaded?

Not in the game's sense: the client keeps only chunks inside its render distance and the server sends only those; from
the walls the far side is ~660 blocks off (render distance 42), and changing the distance reloads every chunk. But the
server already generates every city chunk at world creation, so Far Sight can draw those real blocks itself.

## Design

0. **Detail follows the air** (Euan's idea, 26 Oct): one curve of distance sets the haze, the contrast and the detail
   budget. Textures fade to colour, colour to shaded relief, fine levels to coarse, gradually and inside bands the air
   has already softened; level choice is "under a pixel of error after the haze". The real chunks share the curve (no
   step at the render distance). Clear days still reach 16 km; strength tuned by eye in the tours.
1. **City Sight**: at world creation the server turns the generated city (~1,300 chunks, visible faces only) into block
   data, saved with the world and sent once; Far Sight draws it with the real block textures (at 500 m a block is still
   2-3 px), 2-block levels past that; edits update their section.
2. **Spans** for the rest: a few vertical runs per column (bottom, top, block, colour), from every chunk ever
   generated, kept on disk.
3. **Shadow generator** for unvisited land: height (within 0.6 blocks today), the surface rules' blocks, trees at the
   spots the game's seeded placement picks (replayed without growing them).
4. **Size-true, pixel-chosen levels**: a coarser level keeps a block where at least half its finer cells do; each tile
   uses the coarsest level whose error stays under ~1 px at its distance; levels cross-fade.
5. **Work toward where you're heading**: the queue ranks land by the camera's path; workers burst while the view is
   cold (built today).
6. **One renderer per section**: a Turbo hook (contract, Sodium 0.9.3) reads Sodium's list of sections with a mesh.
7. **The opening waits for the city** and the flight path's chunks.

## Proof

Seam audit in every Far Sight tour at 12 spots: far over a drawn section 0 px; holes 0 px; the city at the seam ≤ 1 px
shift; thin landmarks keep their width within a level; fill after a jump ≤ 10 s. GameTests: the city's block data
reproduces its chunks; coarser levels never grow a silhouette; shadow trees match real ones.

## Phases

P0 today's fixes + the audit; P1 City Sight; P2 spans + disk; P3 shadow generator; P4 pixel-error levels, cross-fades,
path-ahead queue; P5 the Turbo Sodium hook; P6 look and frame time (within +3 ms on Ultra), one hand-back tour.
Estimates to measure in P1: City Sight 30-60 MB GPU, seconds of server time at world creation; ~6 KB disk per explored
chunk. About an evening of builder time.

## Alternatives

Raising the render distance in the city (42 needed, hitch on change, fps); Distant Horizons 3.3.4 (exists for 26.3;
chunky, heavy generation on the Mac, its own look, no Lens haze on Vulkan); patching Far Sight 2 (a heightfield can't
draw a facade or spire).

## Decisions

1. Build Far Sight 3, City Sight first (recommended).
2. City Sight with real block textures to ~500 m, colour past that (recommended), or average colours throughout.
3. The Turbo hook into Sodium's drawn sections (recommended yes).
4. Spans reach 1.5 km (recommended), 1 km or 3 km.
