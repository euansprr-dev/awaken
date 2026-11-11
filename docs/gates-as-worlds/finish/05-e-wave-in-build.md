# Brief 05: five part-built E gates become runs at the bar

**Why.** Five hand-built gates are in the build but held out of the world (`"ready"` is not set): The Vine Seat, Kingsroot Keep,
Saltcomb Flats, The Quiet Barrow and The Wax Galleries. Each was built as a *place*, before Euan corrected the plan on 10 Nov: a
gate is a run, "a very well-built dungeon that has atmosphere, that goes up, that goes down, that has good fighting". None of the
five has a route of legs, so in them nothing waits, nothing ambushes, nothing has to be jumped and no door shuts. Three of them
have look faults that were written down and never fixed. The Vine Seat is the gate he rejected ("very blocky... there's no
atmosphere... nothing that makes this world feel like truly alive"); it was remade and he has not seen the remake.

Two builders, one lane each. A lane's gates share a theme's materials, so the second goes faster than the first.

- **Lane A:** The Vine Seat, then Kingsroot Keep (both Stronghold, Overgrown Temple).
- **Lane B:** The Wax Galleries, then The Quiet Barrow, then Saltcomb Flats.

**Done looks like, for each gate:** it plays as a run from the way in to the Lord; the bench is clean at seeds 1, 2 and 3; one
real-game walk is read shot by shot with nothing broken; its hand-back has the shots. The gates session then looks, sends back at
most one look sheet, and sets `"ready": true`.

## Read first

- `docs/gates-as-worlds/BAR.md` (the six things every gate must have, and the numbers a picture is judged by).
- `docs/SPEC.md` §50.9 from its top to "No two alike" (legs, packs, ambush, traps, the fall catch, the shortcut, the hidden
  place, pay by leg), and your gate's own notes in §50.8.
- `realms/Land.java` lines 20 to 130: `LegKind`, `Leg`, `Trap`, `Course`, and how a land declares them.
- The two hand-built gates that already are runs: `realms/land/HourCrypt.java` (search "The run": sixteen legs through a place
  that was built first, with toll doors as its traps) and `realms/land/Broodwarren.java` (349 lines, a short crawl from parts).
- Your gate's row in `docs/gates-as-worlds/ROSTER.md` (its rule, its great thing, its Lord's place), its painting in
  `docs/gates-as-worlds/img/paint-<gate>.jpg` and the Lord's painting beside it, and, for The Quiet Barrow and The Wax Galleries,
  their sheets in `docs/gates-as-worlds/sheets/`.
- Your gate's class in `realms/land/` end to end, once, before you change it.

## Part 1: make it a run

Lay a route through the place that is already there. Do not rebuild the place to fit a route; find the route the place wants.

- **Legs.** From the way in to the Lord: the first fight within ninety seconds' walk of the way in; never more than a minute's
  walk with nothing in it; at least one ambush; at least one leg that is about moving (a climb, a crossing, a drop with a fall
  catch); at least one trap leg that uses **the gate's own rule** (the Vine Seat's roots closing a way behind you, Kingsroot's
  broken walls to climb, the Wax Galleries' warm and cold wax, the Barrow's sound waking what sleeps, Saltcomb's salt going soft);
  one Warden for a short gate, two where the place has the room; a breather before the Lord; a shortcut that opens back toward the
  way in once the Warden falls; one hidden place that pays. Twelve to sixteen legs.
- **Up and down.** The route must change height by twenty blocks or more and use it: you should see where you are going from
  above, or where you have been from below, at least once.
- **Packs** by bestiary id from the theme's own monsters (the gate monsters' bodies come with the ids). Each fight leg needs
  twelve standable cells per monster inside its radius; the bench checks it.
- **Doors** shut a fight in the way the place would (roots, a wax curtain, a fallen stone), with the gate's own block, and open
  when the pack is down. Never bars.
- **The Lord's place** is the one in the roster and the painting. Check his fight (`data/awaken/kit/fights/<lord>.json`) against
  the room: its radius, its height, and a level floor to five sixths of the way out.
- **Pay** by leg as §50.9 says. Do not change the numbers.

## Part 2: bring it to the bar

- **Depth**, by BAR.md's rule: no wall plane wider than five cells without something stepping in or out; floors in half blocks off
  the way; roofs that are not one plane. Use the theme's slabs, stairs, drifts and bits (`gates/block/DepthBlocks`).
- **Air of its own**: the gate's biome (`data/awaken/worldgen/biome/realm/<gate>.json`) with fog that can be seen indoors, what
  drifts in it, its ambient loop, mood sound and music (Minecraft's own recordings by name); its own Lens look in
  `assets/awaken/lens/looks_gates.json`; its `sky.air` list with `shaft_dust` first; `AirPlan` lights at every lit thing, shafts
  where light comes down, still water where there is water. Under a roof, real light where light should fall (an unseen
  `minecraft:light`), and no room left black.
- **Known faults**, fix these whatever else you do:
  - *The Quiet Barrow:* too blue; the path is a wide straight tiled road (make it three wide and winding); no mist in the game;
    the deck shot shows Minecraft's own square sun and clouds (give the gate its own night sky, no vanilla sun in view);
    `old_antler` is a Warden-tier stag and must not be the herd.
  - *The Wax Galleries:* thick pale drips; the arcades too bright; the painting shot's brightness 54 against a target of 60 to
    100; a chain crosses the `seat_painting` camera; its blueprint must only name creatures that exist.
  - *The Vine Seat:* judged "very blocky" on its first pass. Remade since; look at its real-game shots (`realm-vine*` under
    `run-autopilot/screenshots-ci/`) and fix every wall, stair and roof that is still one plane or one block.
  - *Kingsroot Keep* and *Saltcomb Flats:* their last looks were never read by eye. Read them, list what you see, fix it.
- **Nothing floats, everything is reached**, at seeds 1, 2 and 3.

## Checks

- `tools/check-ci.sh compile`, then `tools/check-ci.sh bench tools/autopilot/realm-<gate>.txt 1 2 3` after each part: `floats: 0`,
  `reach: all`, and a `route:` line with every leg and no check failed.
- Rewrite the gate's tour so its shots follow the run leg by leg (`realm_leg`, as the rolled kinds' tours do), in survival at
  normal difficulty so monsters stand in the pictures, each shot after `wait 200`.
- One real-game walk per gate when you believe it is done. Read every shot for what is broken before handing back.

## Yours

Lane A: `land/VineSeat.java`, `land/KingsrootKeep.java`, their rules (`RealmRoots` and the Keep's), their blueprints, biomes, tours
and tests. Lane B: `land/WaxGalleries.java`, `land/QuietBarrow.java`, `land/SaltcombFlats.java`, their rules (`RealmWax`, `RealmHush`,
`RealmSalt`), blueprints, biomes, tours and tests. Each lane may add its own entries to `looks_gates.json`. `RealmLive.java` only
for a line your rule needs. SPEC §50.8: your gates' notes, rewritten to say what they are now.

## Traps

- Amber Reach's hashes must not change. If you touch anything under `realms/kit`, bench Amber at seeds 1, 2 and 3 too.
- These classes are a thousand lines and more of hand-placed pieces. A carve for a new way can cut a support; bench after every
  carve, not at the end.
- A fight leg in an open land (The Quiet Barrow, Saltcomb Flats) still needs edges: the pack must have a place it holds, and the
  player a reason not to walk round it. Use the land (a hollow, a causeway, a ring of stones), not walls.
- Do not make any of them bigger. Euan: "It would be way too long to like explore." A run is twenty minutes at E.
