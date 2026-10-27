# Regions plan: the prototype behind the numbers

Planning harness for `docs/regions/PLAN.md` (nothing here ships, nothing is on the Gradle build path).

- `NewMap.java`: the proposed level field (ceiling by distance, temper by region, soft borders, quiet vales). Same grid, warp and
  hashing as `RegionMap`, so a world's regions sit where they do today; only the level changes.
- `Compare`, `Annuli`, `Cliffs`: Monte Carlo over random worlds, current rules (the real `RegionMap`, copied from `src`) against `NewMap`.
- `EuanWorld`: Euan's world (seed 3500122753516418813, centre -1238 -736) along eight bearings, now and proposed.
- `MapOut`: the two level maps for the approval page and the ASCII maps. `Bench`: lookup cost.
- `sim_variants.py`: the pacing simulator with an elite share by band and a shift in `zone_fit`.
- `nbt.py`: reads a save's `living_regions.dat` (read only; the world is never written).
- `run.sh Compare 300` (or `Annuli 1.0 200`, `Cliffs`, `EuanWorld`, `MapOut`, `Bench`) builds and runs one. Outputs of the runs the plan quotes are in `../results/`.

Biome tilt is not available outside the game, so both models use the same random tilt (harsh 10% of regions, gentle 25%).
The regions named in Euan's Atlas (the Commons, Amberwood 1-7, Black Glades 72-78) come out exactly as he saw them.
