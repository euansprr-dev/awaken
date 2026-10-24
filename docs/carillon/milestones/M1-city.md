# Milestone 1 · The city in blockout (City Architect)

Branch `carillon/m1-city` (from carillon/main 83fdd5c), worktree /Volumes/MEDIA/Awaken-city. Brief:
docs/carillon/briefs/M1-city.md. Date 2026-10-25, Apple M4, load 8-11 all session (other sessions' builds).

## What's built

- **The plan as data.** `tools/city/plan.py` writes `data/awaken/city/plan.json` (schemaVersion 1, 295 KB, sha256 pinned
  by `city_plan_hash`; `plan.py pin` re-pins deliberately). River from `CityBasin.RIVER` (read from terrain's source,
  checked by the test). 24 tower places (21 towers, the Runners' Roost, two water gates), High/Dawn/Mill gatehouses,
  seven districts on a 512x512 raster, avenues radiating from Choir Square, an inner ring, Bellwatch's jittered
  lanes and alleys, the canal loop and a mill race, 9 bridges (the Bell Bridge marked breakable, its graph edges
  tagged `bell_bridge`), the High Terrace lifted 8 with 5 stairs, **779 plots** (Bellwatch 268, Market 111, Works 95,
  Wallring 119, Garden 67, Terrace 63, Quay 56; all ≤ 14x14), **74 enterable** (30 shops, 14 taverns, 30 on the
  opening's street route), **27 landmark groups in 88 parts**, 379 trees, 78 lamps, the street graph (2,003 nodes,
  2,187 edges, doors and landmark entrances linked), the roof graph (779 roofs, 2,201 edges: walk, jump, drop, mantle,
  hang_drop, climb_down) and the opening's street route (Roost to the square via the Mill Bridge, ~86 s walking).
- **The painter** (`dev.awaken.city`): ground per column from the raster, plots by the grammar (stone or timber ground
  storey, jettied storeys in the old town and on the quay, gable/hip/mansard/flat/sawtooth roofs in each district's
  palette, chimneys, shutters, flower boxes, drainpipe chains), landmarks in parts (the Choir: pale tower, portal, rose
  window, open belfry with the bell, a verdigris needle to 140; the Association with a copper dome; the Roost; gates,
  water gates, towers with caps; five district bell towers; cranes; mill wheels; bridges; stalls), lower quays three
  above the water with ladders, fields in irregular patches with hedgerows outside the wall. Solid cores except
  enterable plots; connections set by the painter; writes straight into proto sections; a neighbour sweep removes
  leaves spilled by trees grown next door. One block entity per bell tower (bells), ~2-4 lights per chunk.
- **New worlds only.** Built-in pack `datapacks/carillon` requests `awaken:carillon` and carries the feature; switching
  it off on the Data Packs screen is the Classic start (test `city_pack_new_worlds_only`).
- **Spawn and pregen** through `LevelEvent.CreateSpawnPosition`: spawn on the Roost's terrace facing the spire,
  `respawn_radius` 0, every city chunk plus a margin generated behind the loading screen, DH's generation held.
- **Graphs** baked into SavedData `awaken:city` (schemaVersion 1, world coordinates) at creation.
- **Protection**: break, place, buckets and explosions; creative and ops bypass; `cityProtection` in
  `awaken-city-synced.toml`. **CityHooks** installed. `/awaken city info|view|repaint|dump`.
- **Runtime exclusions**: the gate Director and anomaly spots refuse the reserve; the spawn vault ring is centred on
  the city outside it (402-522); the Well's candidates skip it (lairs, ruins, hero trees, landmarks already did).
- **GameTests** (`-Ptests=city_`, 9 pass): plan hash, chunk alone = in context (51 chunks, the hard ones first),
  protection, doors reachable (also with the Bell Bridge down), roofs ≤ 30 s from the street, graphs round trip,
  pack, pregen driver, studio dump (no-op unless requested).
- **Studio**: `tools/city/render.py` draws a painted dump from the sky without a client (`city-dump.request` in the
  game folder + `-Ptests=city_`).

## Numbers

- Pregen: **1,708 chunks in 14.7 s** (tour 1) and **14.2 s** (tour 2), Tectonic + Lithostitched + DH held, load ~10.
  City cost per chunk p50 0.13 ms paint + 0.33-0.37 ms write (p95 0.34 + 1.1), ~7,500 blocks.
- CI (full GameTest suite): **green on 25c897b** (run 40120392525). The first push had one gates flake
  (`gates_build_break_hold`, no city involved: the GameTest world has no site) that passed on rerun.

## Shots

Full-size (gitignored): /Volumes/MEDIA/Awaken-city/run-autopilot/screenshots/ (tour 3, views). Curated copies in
docs/carillon/milestones/m1/: sky m1_sky_nw, _nw_low, _n, _w, _top, _dusk; m1_spawn_roost, m1_roost_start; streets
m1_square, m1_market, m1_quay, m1_terrace, m1_works, m1_garden; studio_* (spire, association, roost, high_gate, cranes,
bellwatch/garden/terrace towers); render-nw/render-top (painter output). Tour script tools/autopilot/carillon-m1.txt.

## The overhead look, honestly

From 200 up it reads as a designed walled city you can name: the green needle over Choir Square's pale rings, the
river with stepped quays and brown warehouses, the white High Terrace with the Association's copper dome, green roofs
and cherry trees in the Garden Ward, red brick and stacks in the Works, orange-copper roofs round the square, 24
capped towers. Short of the bar: Bellwatch is still a dark slate mass from the sky (its lanes and courts show, but
low contrast); the wall is one heavy grey; every block is vanilla, so up close it is a good vanilla build; the dawn
without Lens washes the far half orange.

## Decisions taken (SPEC §16 proposals for the Carillon session)

Square at the plan's centre with the Choir on its north edge (the N avenue starts behind the apse); High Terrace
raised 8 with retaining walls and 5 stairs; a lower quay (y 66) along the river, canal and race water at y 63;
gatehouses mid-wall on N/E/S, the two water gates and the Roost on tower places; fields 252-318 in carved worlds only;
graphs generated with the plan and copied into SavedData `awaken:city` at creation; civic = everything inside the
wall, protection also covers buckets and explosions; `respawn_radius` 0 in Carillon worlds; pregen radius = the
painter's reach + 2 chunks (~1,700); the spawn vault ring at 402-522 round the city. Three light tours were run (the
first exposed camera framings and connection bugs).

## Gaps

- **M2**: roof and canal routes (the plan has the street route; wall-run edges wait for the Ascent's numbers); breaking
  the Bell Bridge (parts and edges are tagged); the sky shot path (sky_nw/_low frame well); Lens Carillon dawn;
  infirmary and Registry interiors. Tour 1 showed walls vanishing against the sky once (DH + Sodium meshing?); the save
  was whole and a Lens on/off A/B in tour 2 showed nothing; watch for it.
- **M4**: the kit (roof tiles to lift Bellwatch, cloth laundry, lit windows, bronze trim and drainpipes), facade
  modules, dormers, interiors for the 74 enterable plots, the wall's inner face and gate detail, farmsteads in the
  fields, night light, and the quiet perf measurement with solid cores.
