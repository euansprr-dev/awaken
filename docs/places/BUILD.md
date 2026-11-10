# The Long World: build brief (my own working notes; PLAN.md is what Euan approved, 10 Nov 2026)

Approved in full: "Go with the full plan". Added by him the same hour: everything must look naturally placed (the Hall's
way in looked broken and bulky, the Dish's pylon floated), each closer to its painting, AAA for each, and code more than
check: one test and picture pass at the end.

## Decisions taken while building (each goes into SPEC §62 at the end)

- **A monument is a `Kind`** with a `Family` and a tier 1..5 (`Kind.family`, `Kind.tier`). Sixty kinds, ids
  `<family>_<n>` except the three built (`listening_dish`, `stair_that_stops`, `sleepers_hall` stay, tier 3).
- **Bands.** A kind's rank is its band's first rank: tiers 1..5 are ranks 0..4 (the down group 1..5). A cell never deals
  a kind above the land's rank. At the land's rank and one under it the weight is full; older ones halve per rank.
- **No family twice in touching cells**, no kind within two cells (it was three: sixty kinds need less room than three did).
- **Ground.** `Anchor.TERRACE`: seventeen points within the kind's `slope` (now 10 to 18), the site's height their 60th
  centile, 49 tries on a grid 24 apart, and the kind turned so its front (south) looks downhill. `Anchor.HIGH` the same,
  taking the highest spot that fits. `kit/G.platform` builds the terrace: level inside, a battered retaining wall where
  the land falls away, a faced cut where it rises, nothing where it is level; never a dirt ramp. `G.stairs` runs steps
  from the terrace down to whatever ground is there.
- **Skyline.** Each generator has something of olden stone well above its ground (ten for the smallest, fourteen and more for the rest).
- **Relics** are given by code, one a family, the first time any of its five is finished; `Relics.level` is how many of
  the family's five a player has done. Hoards are loot tables by rank (`finds/hoard_e` .. `hoard_s`).
- **The Archive** is sockets in a marked order (studs under each niche), tablets lying loose: the approved wording.
- **Remains** are layer 1 (cells 256), fifteen kinds of `FSet.REMAINS`, dealt only where a monument stands within 1,000
  blocks; turned to face it; their carving says which way and how far and gives a lead.
- **Site box** for clicks: 64 below to 128 above the heart (the Stair's release sat one block above the old box).

## Order of work (tick as done)

- [x] Engine: Family, Kind fields, Scene.nat/flows, G, Anchors, Sites (bands, families, remains layer), Site box
- [x] Kit: figures, rings, domes, towers, arcades, camps of later people, wear by land
- [x] Relics (12, levels), Reward (relic by family, a level for a finished family), Reading Glass hook
- [x] Before: Dish x5, Road x5, Archive x5
- [x] Up: Stair x5, Mast x5, Orrery x5
- [x] In: Hall x5, Garden x5, Water Clock x5
- [x] Down: Shaft x5, Chain Works x5, Door x5
- [x] Remains x15
- [x] Art and data: nine relic sprites, lang, hoard tables, Journal by family
- [x] Compile, tests, dump on flat and on a hill, draw all, fix (finds tests green over all 75; game's own blocks 0.2% to 20%)
- [ ] Merge build in, CI suite, twelve tours, world count, page of sixty, SPEC §62, notes

## Where things are (after the 14:47 UTC restart wiped the scratchpad)

Working files live on the SSD in the worktree's `run/plan-page/` (gitignored): `compile.sh` (compile through the slot, print
javac's errors), `tests.sh [prefix]` (finds tests, writes `run/finds-dump/*.txt`), `site/` (the plan's page), `mon2/` (game
pictures of the three, second round), `census5/`. Pictures of every scene: `python3 tools/content/finds_render.py run/finds-dump
run/plan-page/render [ids]`. First clean compile of the whole build: commit 9d5f4a023.

Page of the sixty: https://claude.ai/artifact/NNj4yph5Drm23jkcbmZi9z (built by `run/plan-page/build_page.py` into `run/plan-page/site2/`; game
pictures go in through `keep.json`, one list of checked shots per painted monument).

## Finalising (Euan, 11 Nov: "tied into the world generation... never glitchy, like vanilla villages... very rarely see two of the same")

- Deal: a deck per block of 8 by 8 cells, 3,072 blocks square (`Sites.deal`), a kind once a block; each cell that holds a
  card gets a second for when the first cannot stand; across block edges a card yields where a first-say cell within six
  cells really stands the same kind. Nothing repeats within 2,300 blocks. A kind may turn up one band early (0.3 weight).
  Within 1,000 of the start a cell's chance is never under 0.35.
- Spot: the best of the cell's 49, not the first (gentlest; highest for masts and orreries). The terrace (reach - 14)
  checked every eighth block (dry, within slope + 4, no water over the floor), two dry rings round the eased ground,
  never above sea + 110, land agreed at 4 of 5 points, never within 300 (or both widths + 24) of a monument with first
  say. Structures count only where one can really start (own start chunk, a biome it stands in).
- Ground: `G.SKIRT` 10 regrades the land to within `G.WALL` 6 / `G.CUT` 4 of the floor; reaches grew to hold it
  (`Sites.MAX_REACH` 72); the feature looks 48 down and fills hollows with rock.
- Spot, again: the land round the whole reach counts (a cliff's lip loses to calmer land; more than 28 under the floor or
  24 over it on that ring is refused, 40 under for masts and orreries). The way in looks downhill only where that is a
  walk; else along the hillside (`Anchors.front`).
- Spot, by the monument's own footprint (`Anchors.foot`): it is built once on level land with the site's seed and its
  terrace, the ring round it and what else meets the ground are read off; every spot is tried in all four turns on a
  lattice of eight; the best spot and turn win (gentle ground, calm ring, a way in that is a walk). `Terrain.memo` keeps
  the cost at about a thousand noise columns a cell. `B.wild` and the feature keep what grows out of water.
- Lands: pits and chains also stand in snow and plains (chains in dark forest too); the three down families weigh 16 to 18.
- Count: `/awaken finds census` says how many stand, of how many kinds, how far to the next and to the next twin, and how
  far the nearest is from any spot. Without a client: `tools/content/finds_census.sh` (five minutes, headless server),
  `REAL=1` adds drawings of real placements (`run/finds-real` → `finds_render.py`). Numbers: SPEC §62.9.
- The CI tour queue was forty deep on 11 Nov; the headless count and the real-land drawings are what the placement was
  tuned from. Tours `placed-a..d` (twelve monuments where the world put them) are the game's own pictures of it.
