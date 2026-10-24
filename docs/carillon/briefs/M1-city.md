# Milestone 1 · The city in blockout (City Architect, Opus at extra-high effort)

Worktree `/Volumes/MEDIA/Awaken-city`, branch `carillon/m1-city` (cut from carillon/main). You are the only builder
running. Report once, at the end.

Read first, in order:
1. /Volumes/MEDIA/Awaken-city/CLAUDE.md, then docs/carillon/briefs/RULES.md (house rules, the lean rules at the top).
2. docs/SPEC.md §16: 16.0 (decisions 1–19), 16.1, 16.2 (the city, drawing rules from G0), 16.9 (CityHooks), 16.10;
   and §15's city-basin part (the terrain API) plus "Together" for PartyHooks/HearthHooks context.
3. docs/plan-carillon-lean.html: "Carillon itself" and "The opening, beat by beat" (its route-map demo shows the
   intended layout: the wall ~480 across, the river N→SW west of centre, the canal loop on the west bank, Choir Square
   at the centre with the spire on its north edge, the Association on the High Terrace to the north, the Roost on the
   NW wall, the Bell Bridge and Mill Bridge, districts and avenues).
4. docs/carillon/spikes/G0-notes.md, the S1 section. Then spike S1 itself: branch carillon/spike-city,
   /Volumes/MEDIA/Awaken-c-city/docs/carillon/spikes/S1-city.md and its code in
   /Volumes/MEDIA/Awaken-c-city/src/main/java/dev/awaken/city/ (the section writer, the per-chunk painter, the grammar
   v0.1, the bee-nest fix). Reuse what's good; it is throwaway, so nothing must survive as is.
5. The terrain API: src/main/java/dev/awaken/terrain/api/ (CityBasin, CitySite: site, reserved(x, z), STREET_Y 70,
   CORE_RADIUS 330, BLEND_RADIUS 700, RIVER, riverHalfWidth 9, riverFloorY 57, basinCarved). The feature flag
   `awaken:carillon` is declared in META-INF/feature_flags.json.

## Goal
In a new world with Carillon on, the whole city stands in the basin at spawn, in blockout: true scale, heights, streets,
canals, bridges, the wall and every landmark as plain massing. Blockout does NOT mean ugly from above. Euan's first play
session (after Milestone 2) starts with the sky shot, so from the sky this blockout must already read as a city: a roof
palette per district, a street hierarchy (avenues radiating from Choir Square, lanes, alleys, courtyards, gardens and
trees in the Garden Ward), organic plots in the Bellwatch, and landmark silhouettes you can name from 200 blocks up.
Spike S1's overhead shot (a uniform dark grid) is exactly what must not happen.

## Deliver
1. **The plan as generated data.** A Python generator under tools/city/ writes `data/awaken/city/plan.json`
   (schemaVersion 1). It holds the wall line, 24 towers, 4 gatehouses + the river gates, the districts (six + the
   Wallring), streets by rank, the canal loop, the bridges (the Bell Bridge must be breakable later by the opening), ~700–900 plots (district,
   footprint ≤ 14×14 pieces, height class, enterable flag for the ~60 the run and shops need), and ~12 landmark sites:
   the Choir spire (~140 tall), the Association complex (hall, Registry, infirmary, archive, training yard), the Roost,
   the gatehouses, the Bell Bridge, the Quay crane towers, the Works mills, and one bell tower per district. Fixed
   layout, the same in every world. Align it to `CityBasin.RIVER` (terrain owns that constant). A GameTest pins the
   plan's hash.
2. **The painter.** `dev.awaken.city`: a per-chunk painter that writes straight into chunk sections, with pieces
   ≤ 14×14 plus a 2-block margin. Solid cores, except plots marked enterable (left open inside, empty for now).
   Vanilla blocks, chosen for silhouette and colour by district: Bellwatch slate and dark blue-grey, Quay brown timber,
   Market copper and bronze accents, High Terrace pale stone, Works red brick, Garden green roofs and trees,
   the Wallring stone. Paved streets by rank, quays along the canal and river, and the wall ~40 high and 10 thick with
   a walkway. It clears vanilla trees and flowers on its plots and runs only inside `CityBasin.site`, only in worlds
   with `awaken:carillon` enabled.
3. **New worlds only, with a Classic start.** A built-in data pack that requests `awaken:carillon`, on by default for
   new worlds. Classic start means turning that pack off on the world-creation Data Packs screen; a nicer toggle can
   come with Milestone 2. Existing worlds never change. Verify the NeoForge built-in pack and feature-flag APIs in .ref.
4. **Spawn** at the Runners' Roost through `LevelEvent.CreateSpawnPosition`. Install `CityHooks` (inCity, district,
   isCivic, startPoint, square).
5. **Pregeneration** of every city chunk plus a margin before the player enters, behind a plain loading message, timed
   (≤ 30 s on a quiet machine). If Distant Horizons is present, its LOD building waits until the pregen ends.
6. **Graphs.** The street graph (junctions, doors, stairs, bridges) and the roof graph (edges labelled with the move
   they need, from SPEC §16.3's moves), baked at creation into `SavedData awaken_city` (schemaVersion 1).
7. **Civic protection.** Breaking or placing in the city is denied with a short message; creative and ops bypass;
   server option `cityProtection=false`. There are no player plots yet.
8. **Runtime exclusions** through `CityBasin.reserved`: the gate Director's placement, anomalies, landmarks, the spawn
   vault ring and the Well all stay outside the reservation.
9. **GameTests** (prefix `city_`, silent breakage only): the plan hash; a chunk painted alone equals the same chunk
   painted in context; protection; every door is reachable on the street graph; every roof node is within 30 s of the
   street route (graph distance). Run them locally with `-Ptests=city_`, and push the branch for CI.
10. **One `slot.sh light` tour** (≤ 15 min), `tools/autopilot/carillon-m1.txt`, in a new world with Carillon on:
    - sky viewpoints at dawn within the S2 camera limits (≤ 215 blocks from the player, ≤ 200 above the ground), over
      the north-west half looking south-east across the spire;
    - street level in each district, the Roost, Choir Square, the wall walk;
    - a fixed "studio" set of landmark framings, reused at every later milestone so the shots compare.

## Not in this milestone
Citizens, the opening and its camera, Carillon's own block kit and textures (vanilla blocks for now), interiors beyond
marking enterable plots, the Atlas, MMO-lite.

## Model policy
You are Opus at extra-high effort and you decide the look: the plan, the painter, the landmarks. For bulk generator
code, data tables and the GameTests you may start one Sonnet subagent at a time (Agent tool, `model: "sonnet"`),
briefed from a file you write under docs/carillon/briefs/M1-sonnet-<n>.md, working in your worktree while you don't
build.

## Hand back
docs/carillon/milestones/M1-city.md, committed and pushed: what's built, the pregen time, CI status, the tour's shot
paths, an honest read of the overhead look against "a city you can name from the sky", and the gaps you'd hand to
Milestones 2 and 4. Your final message is that report (under ~600 words). Leave nothing uncommitted.
