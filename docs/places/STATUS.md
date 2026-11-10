# Places: where they stand (10 Nov 2026)

> **Rejected as content, 10 Nov 2026, on the picture page.** Euan: "they basically just feel like decoration... no meaning to
> them, no lore to them, no gameplay mechanics to them. They're kind of all just empty buildings... They look very vanilla
> and boring and flat right now. They don't have depth." He wants the remains of a world inhabited for a very long time:
> a little broken down, each with its own loot, lore, something to do, and a look that makes you stop. **Do not merge the
> thirty.** What carries over is the engine (SPEC §59.2 to §59.4, §59.7). The second take is three concept paintings
> (`img/stair.jpg`, `img/dish.jpg`, `img/hall.jpg`; page https://claude.ai/artifact/1Fign2AXfzSB9AhJwe9Jns) built by the
> Gates' own rule (SPEC §50.8 and "Quality as a process gate" in docs/gates-as-worlds/PLAN.md): painting first, a block
> family of its own, relief at three scales, at most a third vanilla, the game picture beside the painting, one monument
> proved before a second is started. Waiting on Euan: the look, ten to twelve monuments instead of thirty, the relics,
> danger or none, which to build first. Everything below describes the thirty as they were shown to him.

Branch `claude/finds-over-every-hill`, not merged. Design: SPEC §59. Briefs: `BRIEFS.md`. Nothing here has been played by a
person. Euan's bar: real places that stand out when you discover them, with atmosphere, natural to their land, never a
fairground (memory: finds-places-not-objects, finds-not-a-fairground, wonders-aaa-bar).

## How they were checked

1. **Tests** (`finds_` ×4, `journey_ledger`, `core_blocks_motion`): every place builds, nothing floats, nothing lies past its
   reach, a scripted player finishes it and is paid once. Green.
2. **Pictures without the game** (`tools/content/finds_render.py`, SPEC §59.7): all thirty from two sides, cut open, roof
   off, by night, in plan. This is where thin builds were caught and rebuilt.
3. **Tours on GitHub** (`places-a` to `-j`): five pictures a place where the world itself put it. Three rounds. The first
   photographed the spawn (the camera's teleport held the server); the second showed the first real pictures and most of
   what is listed under "What the pictures changed"; the third is the builds as they are now.

## What the pictures changed

- **Thin builds.** The Stilled Wedding was 308 blocks on bare grass, the mill one house in a field. Twelve places were
  rebuilt or filled out to fill their ground (the wedding, the terraces, the clearing, the mill, the bathhouse, the quarry,
  the salt works, the Ice Hall, the Hollow King, the Barrow Field, the mine head, the cave's tor).
- **Bare discs.** Every place now plants the ring round itself with what grows in its own country (`B.wild`).
- **Checkerboard ground.** The ground's dressing lies in drifts (`B.ground` on soft noise).
- **Pea-soup air.** Open-air places tint the distance now; only caves keep thick air.
- **Treetops in the air** at a clearing's edge, and loose leaves at chunk edges: cleared.
- **Wrong country.** A place goes by the biome where it stands (a desert caravanserai stood in a jungle).
- **Against the city, in a village.** Places keep clear of both by their own width and more.
- **Cameras in walls.** Every tour camera is checked against the blocks before a tour is spent.
- **Frozen hot spring.** An unseen light keeps the bathhouse's water open in snow country.

## How many, how far apart

`/awaken finds census` on seed 4242, the cells within 22 of the spawn's:

| Build | Places standing | Kinds that stand somewhere | Notes |
|---|---|---|---|
| First (every structure set asked over 7 chunks) | 2 | 2 | the keep-away rule ruled out nearly everything |
| Second (448 grid, the kind's own slope) | 205 in 20,000 blocks square | 27 of 30 | two cells in three found no level ground |
| Third (384 grid, slope plus two, second countries) | 203 in 17,300 blocks square | 26 of 30 | nearest to the spawn: 260, 592, 654, 963 blocks |
| Fourth (the biome on the spot decides the country) | see the tour log `census4` | | not read when this was written |

On the third count that is about one place to a square a little under 1,000 blocks on a side of land, and in lively land
the next place is some 400 blocks from the last. A third of the land is quiet and holds about one cell in eight. Kinds
that stood nowhere within the count on the third build: the Sunken Temple, the Bathhouse, the Bell Foundry, the
Caravanserai (their countries are rare near this spawn: 8 swamp, 23 mountain, 46 desert cells of 2,025).

**Open question for Euan:** whether that is the right density. It is deliberately not a fairground; it may be too thin
for "always something over the next hill". One number changes it (`Sites.CELL[2]`, or how much slope a place may level).

## The thirty

"Seen" is the last round of game pictures I looked at for that place. Rebuilt means rebuilt after the first real pictures.

| # | Place | Country | What stands there | Seen in game | My read |
|---|---|---|---|---|---|
| 1 | Charcoal Burners' Clearing | forest, dark | three turfed kilns (one smoking), a bothy, a woodshed, ricks of cordwood, stumps | round 2 | rebuilt; reads as a working camp from above; the plainest place, as its rank says |
| 2 | Mill at the Weir | forest, plains | mill with wheel and hoist, barn, wheat field, two ponds, a bridge | round 2 | reads as a homestead |
| 3 | Hollow King | forest, dark | a dead oak 30 high, lobed and flared, three floors inside, boughs half in leaf | round 2 | strong from afar; the door was hidden by the new roots and is recut since |
| 4 | Glowworm Cave | dark, forest, jungle, mountain | a tor over a stair down to a lake hall hung with glow | round 2 | the hall is the place; dark but lit |
| 5 | Lantern Shrine | dark, forest, snow | gate, lantern stair, shrine under two giant trees | round 2 | strong; stands on a rise, the gate frames it |
| 6 | Beekeeper's Terraces | forest, plains | a hill cut in four stone-faced steps, hives and flowers, honey house, walled yard | round 2 | rebuilt; reads well from above and at the gate |
| 7 | Stilled Wedding | plains, forest | hedged green, six cherry trees in blossom, dais and arch, marquee, dance floor, bandstand | round 2 | rebuilt; the pink shows from far off |
| 8 | Barrow Field | plains, snow | henge bank and ditch, stone ring, six barrows round an open one | round 2 | reads as an old site from above |
| 9 | Observatory | plains, mountain, snow | round tower under a copper dome, a paved court | round 2 | strong: the copper dome shows over the trees |
| 10 | Amphitheatre | plains, desert, badlands | stone tiers, a stage with columns, stone watchers | round 2 | strong inside |
| 11 | Broken Aqueduct | plains, mountain, desert | seven piers, one span down, water still in the channel | round 2 | strong from above and from the top of it |
| 12 | Drowned Chapel | swamp, dark, forest | roofless chapel and tower in a pond, a yew, a crypt | round 2 | strong |
| 13 | Stilt Market | swamp, jungle, forest | five huts and a pavilion on stilts over a pond, boardwalks | round 2 | strong |
| 14 | Hanging Village | jungle, dark | three giant trees, platforms, bridges | round 2 | strong: the best silhouette of the thirty |
| 15 | Sunken Temple | jungle, swamp, dark | stepped temple with a tree through it, a mirror room | round 2 | strong outside; the mirror room is small |
| 16 | Bathhouse at the Hot Spring | snow, mountain | bathhouse, tea house, white spring basins stepping down, pools, a smoking chimney | round 2 | rebuilt; reads well |
| 17 | Ice Hall | snow | a feast hall cased in ice among eight ice spikes | round 1 (before the spikes) | the hall is fine inside; the spikes round it are unseen in game |
| 18 | Bell Foundry | mountain, snow | walled yard, furnace and stack, casting pit, crane, a green bell | round 1 | fair |
| 19 | Stair Monastery | mountain, snow | a rock with a stair cut round it, cells, a gong under thatch at the top | round 2 | strong |
| 20 | Flooded Quarry | mountain, forest, plains | a flooded pit under a cut bluff of three benches, crane, shed | round 2 | fair; the water still reads as a square pool |
| 21 | Mine Head | mountain, badlands, snow, forest | headframe and wheel, winding house and stack, spoil heap, an adit | round 2 | reads well: headframe, smoke, a lit track into the rock |
| 22 | Salt Works | desert, badlands | nine pans, a wind pump with cloth sails, boiling house, white heaps | round 2 | reads well |
| 23 | Caravanserai | desert, badlands | walled court, palms, a well under a canopy, stables | round 2 (in the wrong country then) | strong |
| 24 | Bone Yard | badlands, desert | a giant's ribs and skull, a camp under them | round 2 | strong: a giant's ribs and skull in white desert |
| 25 | Mesa Granary | badlands, desert | a banded drum of a store, a ramp round it | round 2 | strong shape, plain face; its way in is a ladder up the wall |
| 26 | Glass Garden | desert, badlands | a crater of glass spires and amethyst round an oasis | round 2 | the strangest; stands out |
| 27 | Lighthouse on the Skerry | shore | a banded light 24 high on a rock, a cottage, a jetty | round 2 | strong: a lighthouse is a lighthouse |
| 28 | Careened Ship | shore | a ship hauled over on a beach, props, a camp | round 2 | good from above; the deck is bare |
| 29 | Hermit's Rock | forest, plains, mountain, jungle | a hollowed boulder, a garden, a stone ring | round 2 | modest and homely; the smallest |
| 30 | Spore Cathedral | mushroom, dark, swamp, jungle | giant mushrooms as a nave, a white way up it | round 2 | strong |

## Known shortfalls

- Water set in a place stands still: no falls, no running race at the mill.
- The square pit at the quarry. The granary's plain wall. The charcoal clearing is modest.
- Motes and recordings are the game's own by name; no place has a sound of its own making.
- Hands use the one plain swing; the Stilled are the kit's figures.
- A tour's first picture of its first place can still be taken before the camera lands (the first search is slow).
- GitHub's runners lose their graphics card now and then; a tour then stops short.
