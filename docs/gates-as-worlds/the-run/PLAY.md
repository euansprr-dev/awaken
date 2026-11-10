# The Run: how to play it, and what to look for

Everything here is on branch `claude/gates-as-worlds-plan-fcb81b`. It is not in `build/awaken-v1` and not in the play copy
until someone merges it. The new gates are behind the `gateRealms` switch (off), so the quickest way in is the command.

## Getting in

Stand anywhere in the overworld, as an admin:

| Gate | Command | What it is |
|---|---|---|
| The Hour Crypt | `/awaken gate realm_enter E awaken:hour_crypt` | The crypt you know, as a 16-leg run |
| The Broodwarren | `/awaken gate realm_enter E awaken:broodwarren` | A short crawl built by hand, fifty blocks down |
| The Lower Combs | `/awaken gate realm_enter E awaken:brood_burrow 1` | A brood nest rolled from the seed |
| The Bone Roads | `/awaken gate realm_enter E awaken:catacombs 1` | Catacombs rolled from the seed |
| The Hollow Hills | `/awaken gate realm_enter E awaken:hollow_hills 1` | Barrow passages rolled from the seed |
| The Root Cellars | `/awaken gate realm_enter E awaken:root_cellars 1` | A temple's undercroft rolled from the seed |
| The Tallow Runs | `/awaken gate realm_enter E awaken:tallow_runs 1` | Wax runs rolled from the seed |
| The Cinder Workings | `/awaken gate realm_enter E awaken:cinder_workings 1` | A burning mine rolled from the seed |
| The Sporewells | `/awaken gate realm_enter E awaken:sporewells 1` | Fungal wells rolled from the seed |
| The Sunken Stacks | `/awaken gate realm_enter D awaken:sunken_stacks 1` | A drowned library rolled from the seed (rank D) |
| The Frozen Cells | `/awaken gate realm_enter C awaken:frozen_cells 1` | A monastery's cells under ice, rolled from the seed (rank C) |
| The Glass Warrens | `/awaken gate realm_enter D awaken:glass_warrens 1` | Burrows under the glass desert, rolled from the seed (rank D) |
| The Stopped Works | `/awaken gate realm_enter D awaken:stopped_works 1` | A foundry stopped mid-stroke, rolled from the seed (rank D) |
| The Silvered Halls | `/awaken gate realm_enter C awaken:silvered_halls 1` | A mirror palace's back halls, rolled from the seed (rank C) |
| The Sealed Wing | `/awaken gate realm_enter C awaken:sealed_wing 1` | A sealed laboratory wing, rolled from the seed (rank C) |
| The Sleepless Ward | `/awaken gate realm_enter C awaken:sleepless_ward 1` | A house as a bad dream remembers it, rolled from the seed (rank C) |
| The Rime Hollows | `/awaken gate realm_enter C awaken:rime_hollows 1` | Ice caves under the monastery's mountain, rolled from the seed (rank C) |
| The Green Dark | `/awaken gate realm_enter E awaken:green_dark 1` | Jungle caves under the temple, rolled from the seed |
| The Unlit Stair | `/awaken gate realm_enter A awaken:unlit_stair 1` | A shrine cut down into nothing, rolled from the seed (rank A) |

A seed decides everything about a rolled run: how long it is (short, standard, or great with a second Warden and about
fifteen legs), which way it goes (delving, plunging, climbing to a Lord at the top, or sprawling nearly level), and what
every leg is cut like. No two legs need be alike:

- **Rooms** come as an oval, a long hall, a cross, a ring round a core of rock, a hall of piers, a room with a terrace along
  one side, a pit with a walk round it, or a tall rift whose floor steps up.
- **Wardens' rooms and Lords' halls** come as a ring of pillars, a nave, a cross, a rotunda, a round floor with tiers of
  steps, or a square hall on four great piers.
- **Gulfs** are crossed on pillars, a broken bridge, rafters one block wide, a ledge along each wall in turn, platforms hung
  on chains, great steps down, stones in shallow water, or a deck that gives way under you. From a standard run up there is
  always one, and often a second down a side way that leads to a vault with two caches. Nobody has to take that one.
- **Shafts** are round, or square stairwells where the place was built.

Each kind has its own share of these (a palace has galleries and naves, a nest has pits and round cells). The same seed
still gives two kinds the same order of legs, but each cuts them its own way. Seed 1 is a great delve in every kind.

Since 11 Nov every rolled room also has **depth and air**:

- The ground rises and falls in half blocks. The way through a room is worn level; off it the floor stands higher, rolls,
  and banks up against the walls. Ways have half a step along their sides.
- Built places have pilasters on their walls and beams under their roofs. Dug places have roofs that swell, dip and come
  down in lumps at the walls.
- Stairs stand at the foot of walls and hang where a roof steps. Drifts lie along the walls. Plants grow in clumps that
  thicken away from the way. Small bits lie underfoot and things hang from high roofs. Each kind uses its own theme's.
- Each kind has its own fog, its own things drifting in the air, its own colour of light, its own ambient sound and music
  (Minecraft's own recordings), and its own Lens look. The Unlit Stair has no sound at all.
- In a gulf there is still water and a low mist; over every fight a soft light colours the mist.
- Light comes down into every room through a slot in its roof (two in a Lord's hall): a pool of real light on the floor,
  with dust turning in it. There is no beam in the air: three ways of drawing one as sprites were tried and taken out
  (SPEC 50.9, "Air").
- The long walls of gulfs and stairwells are cut into bays between piers (built places) or scooped out (dug ones).

Every rolled gate takes any whole number as the seed: `... awaken:catacombs 2` is a different dungeon. Change `E` for another rank
to make everything in it stronger. `/awaken gate leave` gets you out. Play in survival: an ambush does not spring for a
creative player, and packs do not notice one.

## What should happen

- **Packs wait where you can see them** and wake when you come within about thirteen blocks in their sight, or five whatever
  is in the way, or when you hurt one.
- **A fight's doors shut** while its pack is awake and you are inside, and open when the pack is dead or you are gone.
- **An ambush** has nothing standing in it. In the Broodwarren it is the larder: walk to the middle and the sacs burst.
- **Traps.** The Crypt's Ossuary gallery has three rows of stone teeth that come up one after another. The Broodwarren's
  gallery has five cells that light and then spit across it. Thin comb on the nursery's ledge gives way under you.
- **A long fall** in the Crypt's tower or a nest's shaft costs a little health and puts you back on the last ledge you stood on.
- **A cache appears** where a herd falls.
- **The HUD** reads "Herds 2 / 5 · Wardens 1 / 3", then that the way on is open.
- **Hidden places.** The Crypt's reliquary is at the south end of the Ossuary's cross aisle; its door stands open only while
  the bell rings, and Hushlings keep the dark aisle in front of it (sneak, or stay in candle light). The Broodwarren's jelly
  cell is behind a curtain of sacs on the larder's east side. Finding one pays into your Satchel.
- **Shortcuts.** Kill the Crypt's Warden among the ropes and a bell rope comes down from the bridge to the floor (bell ropes
  can be climbed now). Clear the Broodwarren's larder and the workers' crawl opens back up to the first chamber.
- **Creatures with their own bodies.** Drip Lurkers in the Broodwarren's larder, Hushlings in the Crypt's south aisle, and the
  Thousandfoot as the Broodwarren's Warden. The rolled dungeons field them too.

## What I could not check, so you are the first to see it

- Whether any of it is fun. Nothing here has been played by a person.
- How hard it is. Packs are five to seven at E rank and Wardens have companies; pressure sends waves after four idle minutes.
- The teeth, the spitters and the fall catch hurting and moving a player: compiled and unit-checked only. The ambush, the
  sealing door, a hidden place, named monsters and the Thousandfoot were seen working in a real-game walk.
- Whether the Thousandfoot, a Lurker or a Hushling fights well as a gate monster. They come with the habits they have in the
  caves.
- Sound. Traps and doors use sounds the mod already had.

## Known rough edges

- Doors can't be dug through. I first reported that they could; that was wrong. A gate already protects its blocks (no
  breaking but your own powers' constructs, no placing, no buckets, no blast damage), and that covers these gates too.
- The Hour Crypt still looks flatter and bluer than its painting, and The Quiet Barrow is still short of its painting (listed
  in `../round-two/STATUS.md`). This work was about play, not those looks.
- The rolled dungeons are plainer than a gate built by hand from a painting. They are meant to be the short gates in the mix.
- Every rolled kind is checked by test on eight seeds, 104 different layouts in all (each can be walked end to end, nothing
  floats, every fight has room). All seventeen kinds have been seen in the real game.
- The Sporewells' walls were a clutter of five blocks on its first walk; it was rewritten and its second walk reads as a
  fungus cave, still the busiest kind. The Glass Warrens glowed over half its walls; that is turned down, and its second
  walk is calmer. The Unlit Stair is the darkest kind and The Rime Hollows the brightest.
- A great run's chasm was too dark in the real game. Every shaft and chasm is lit by the generator now, and that was seen
  working in The Lower Combs, The Silvered Halls and The Sealed Wing.
