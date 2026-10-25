# Spike S4 · 240 citizens on a ledger, 40 with bodies (tick and frame cost)

Worktree `/Volumes/MEDIA/Awaken-c-crowd`, branch `carillon/spike-crowd` (throwaway; the real one is workstream W4).
Read first: docs/carillon/briefs/RULES.md, docs/SPEC.md §16.5 and §16.10, docs/plan-carillon.html ("A city that lives"),
src/main/java/dev/awaken/hearth/town/ (TownNpc, Town) for the existing NPC pattern and renderer.

## The questions
1. What does a ledger of 240 citizens cost per server tick when it advances 12 per tick along a street graph?
   (budget ≤ 0.5 ms/tick)
2. What do 40 embodied citizens cost per tick when they walk the street graph with distance-throttled brains
   (every max(1, d²/256) ticks) and at most 4 pathfinding at once? (budget ≤ 1.5 ms/tick)
3. What do 40 citizens cost to draw at noon in a street, perf profile? (budget +2.0 ms p50 over the empty street)
4. Do they get stuck or bunch up? (count over a simulated play-day)

## Build
- A test street grid built by command (`/awaken citizens grid`): a 128×128 grid of streets and simple building
  blocks, with a street graph (nodes at junctions and doors, edges along streets), stored in memory for the spike.
- A ledger SavedData-free prototype: 240 records with home, job site and a seeded schedule on the vanilla day clock
  (work 2000–9000 etc.); each record moves along graph edges abstractly.
- Embodiment: within 56 blocks of a player, records become bodies (a lightweight PathfinderMob reusing the TownNpc look),
  at most 40 per player; bodies follow the graph edge by edge and use vanilla pathing only for the last few blocks;
  despawn back into the ledger beyond range, keeping the record's state.
- Timing: measure the ledger and the bodies separately with `System.nanoTime` per tick and log rolling p50/p95
  (`[citizens] ledger …ms bodies …ms`). Use spark if the pack's tools profile has it (`PACK=perf,tools`).
- An autopilot script `tools/autopilot/carillon-s4.txt`: build the grid, spawn the ledger, stand in the busiest street
  at noon, `perf` with and without citizens, shots; then a time-lapse (`time add`) through a day with a shot per phase.

## Hand back (docs/carillon/spikes/S4-crowd.md)
- Tick costs (p50/p95) for ledger and bodies, frame costs with and without bodies, with load.
- Stuck and bunching counts; what caused them.
- The recommended caps and throttles for W4, and anything in TownNpc that must not be reused.
