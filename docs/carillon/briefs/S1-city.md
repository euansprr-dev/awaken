# Spike S1 · A Bellwatch slice (city generation cost, frame cost, look)

Worktree `/Volumes/MEDIA/Awaken-c-city`, branch `carillon/spike-city` (throwaway: it is never merged; the real city is
workstream W1). Read first: docs/carillon/briefs/RULES.md, docs/SPEC.md §16 (16.2 and 16.10), docs/plan-carillon.html
("Carillon itself").

## The questions
1. How long does a dense city chunk take to paint with the per-chunk painter pattern? (ms per chunk, p50/p95; and the
   extrapolated time to pregenerate ~1,250 city chunks, budget ≤ 30 s on the M4)
2. What does the city cost to draw? (frame p50/p99 at street level, on a rooftop and from 200 blocks up, perf profile
   and horizon-swift; budget: city blocks ≤ 1.5 ms over the same spot without it; with Distant Horizons ≤ +3 ms)
3. Does a grammar-built Bellwatch look like a place, not a pile of boxes? (shots)

## Build (minimum to answer them; prototype quality is fine, it's throwaway)
- A package `dev.awaken.city` with: a hard-coded plot plan for a ~128×128 Bellwatch slice (narrow lanes 3–5 wide, one
  wider street, a small square with a bell tower); a pure per-chunk painter in the `gates/world/GatePainter` + `Sink`
  style, where each chunk only builds the pieces that touch it; a building grammar v0: stone foundation, 2–5 storeys of
  timber frame and plaster, a jettied upper floor, windows with shutters, steep slate (deepslate tile / dark) roofs with
  dormers and chimneys, laundry lines across a few alleys, lanterns. Vanilla blocks only. A seed varies details, never
  shape.
- A dev command `/awaken city slice <x> <y> <z>` that paints the slice there, chunk by chunk, timing each chunk with
  `System.nanoTime` and logging `[city] chunk <cx> <cz> <ms>` plus a summary. It lays its own stone ground plate so the
  terrain under it doesn't matter (paint high, e.g. y 120, if that's easier).
- Optional if time allows: the same painter called from a worldgen feature to see if worldgen-time cost differs.
- An autopilot script `tools/autopilot/carillon-s1.txt`: a fixed seed, creative, clear weather, noon; paint the slice;
  `perf` scenes at street level looking down a lane, on a rooftop looking across the roofs, and 200 blocks up looking
  down; the same three viewpoints with the slice absent (a second world or the same spots before painting) as the
  baseline; dusk shots with lanterns. Run it with `PACK=perf` and with `PACK=perf,horizon DH=swift`.

## Hand back (docs/carillon/spikes/S1-city.md, committed)
- Paint time per chunk (p50/p95/max), the extrapolated pregen time, and what dominates.
- Frame p50/p99 per viewpoint with and without the slice, on both profiles, with load.
- Shot paths (run-autopilot/screenshots/) and your honest read of the look against Euan's bar.
- What W1 should do differently (piece size, block choices to avoid: block entities, lots of light sources...).
