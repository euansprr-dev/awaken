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
