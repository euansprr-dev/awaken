# Old World Monuments: the build brief (10 Nov 2026)

Euan, on the three paintings (`img/stair.jpg`, `img/dish.jpg`, `img/hall.jpg`; page
https://claude.ai/artifact/1Fign2AXfzSB9AhJwe9Jns): "yeah this is 100% what I'm looking for." His verdict on the thirty places
that came before is at the top of `STATUS.md`. His way of working (10 Nov, to two other sessions): an in-depth plan for
yourself, code the whole thing at the best level, test once at the end. This file is that plan. SPEC §61 is the record.

## What a monument is

A ruin the Precursors left (docs/STORY.md), a little broken down, that the world went on living round. Each one has:

1. **A look held to the Gates' rule** (SPEC §50.8, "Quality as a process gate"): its painting is the bar; a block family of
   its own; relief at three scales (silhouette; buttress, arch and recess; chip, drip and joint); no flat face over 5 x 5;
   at most a third of a view the game's own blocks; its own light and air; the game picture shown beside the painting.
2. **A relic only it gives,** with one real use.
3. **Something to do to earn it:** a climb, a setting-right, an order to find. Never a fight, never a way to be stuck.
4. **Carvings the System reads** (in brackets, plain speech), each a piece of who built it and how they left. A monument
   hints; it never states the truth outright.
5. **What later people left:** a camp, lean-tos, candles. The layers of a long-inhabited world.

## The family: olden stone (`olden_*`, 30 blocks, drawn by `tools/art/specs/finds_olden.py`)

Pale weathered ashlar and its dark, damp twin; dressed blocks; a carved band, a carved panel, an inscribed panel; flags and a
mosaic for floors; rubble; a fluted pillar and its capital; verdigris bronze, a bronze band on stone; a lamp (unlit and
lit, pale gold); a seal inlay (teal glow); glowing lichen. Slabs and stairs of the four stones and the flags, and one wall.
Stone faces come in three drawings a block so no two neighbours match.

## The kit: `finds/kit/M` (the seven passes, as working parts)

- **Bones:** `mass` (a volume of coursed stone), `pier` (battered, with a foot and a capital), `span` (an arch between two
  piers, voussoirs stepped with stairs), `deck`.
- **Courses:** every wall plane goes through `face`: a plinth standing proud at the foot, pilasters every few blocks,
  panels set back between them, a carved band at the springing, a cornice that overhangs. Nothing is one flat plane.
- **Pieces:** `column`, `colossus` (a worn standing figure in blocks), `doors`, `brazier`, `plinth`.
- **Age** (one pass over everything built, `M.age`): break the top along a ragged line and whole bays where asked; swap
  faces to worn, dark (under any ledge two deep) or mossy (low, north, wet); crumble edges to stairs and slabs; drop
  rubble where masonry went; then drop anything no longer held up (the same flood the test runs).
- **Growth:** moss carpet, vines, roots (`B.limb`), the land's own trees through `B.wild`.
- **Light and air:** lamps where people would want them, unseen light in deep halls, one `Air`, thin.

## The three

### A. The Stair That Stops (`stair_that_stops`; mountain, snow, forest, plains; reach 56)

Side-on to whoever comes from the south: a stair-bridge climbing west to east on eight piers, 100 long, its last span 66
above the ground and broken off. Chains hang from the end. Two spans have fallen: one early (a rope ladder up the pier
beyond it), one near the top (three blocks: a running jump). At the west foot a gatehouse 21 wide: sealed bronze doors five
high between two colossi, pilastered walls, a cornice, a broken parapet. On the top span, the release: a bronze winch.
Use it and the doors open. Inside, the embarkation hall: two rows of columns, benches, baggage left in heaps, three
carvings, and on a plinth under a lit lamp the relic.

- Relic: **the Counterweight.** While you carry it, a fall that would hurt you is slowed for a moment (once a minute).
- Later people: a climbers' camp at the first pier (tent, fire, bedrolls), the rope ladder, names cut in the pier.
- Carvings: who left, what they could not carry, what they said to those who stayed.

### B. The Listening Dish (`listening_dish`; plains, forest, desert, badlands; reach 52)

A stone dish 61 across, tilted to the north-east, its low rim sunk in the ground and its high rim 24 up on a drum of
buttresses. A third of the rim has fallen in steps of rubble. Bronze bands ring it. A broken pylon stands from the bowl's
centre with a lamp still burning in its core. Under the high rim: herders' lean-tos and hearths. Three rim stones lie in
the grass; set back in their sockets, the dish hums and the vault door under the pylon opens.

- Relic: **the Listening Stone.** Use it: it tells you which way the nearest monument you haven't found lies, and how far.
- Carvings: what the ears were for, the day they heard something answer, why they were left switched on.

### C. The Sleepers' Hall (`sleepers_hall`; dark forest, forest, swamp, jungle; reach 44)

From above, a long hollow in the forest floor with roots hanging into it. A stair goes down ten blocks into a hall 61 long
and 21 wide: two rows of fluted columns, the vault fallen in along the middle so light comes down in shafts, a mosaic floor
with pools. Sixteen carved beds in the aisles, a stone sleeper on each but one. Great roots through the roof and round
the columns. Glowing lichen. Pilgrims' candles. Eight lamps along the beds, to be lit in the order the carving at the
door shows; then the round door at the far end turns, and past it is the empty bed.

- Relic: **the Sleeper's Lamp.** In your off hand it lets you see in the dark below ground.
- Carvings: why they lay down, who kept watch, who the empty bed was for.

## Engine changes

`Sites.MAX_REACH` 48 to 56. A monument is a `PLACE` with weight 2 beside the old thirty at 0 (the thirty are no longer
dealt). Piers and drums go down to the land's own ground column by column, so only the foot is levelled.

## The one test and picture pass, at the end

`finds_` tests (stands, plays, catalogue), `journey_ledger`, `core_blocks_motion`; the offline pictures; one tour a
monument with a camera set where its painting stands; game picture beside painting on the page.
