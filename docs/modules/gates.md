# Gates (`dev.awaken.gates`)

The main loop: pillars of light on the horizon, a gate card when you walk up, a dungeon built before you arrived,
floors that push back the longer you stay, a Satchel you have to carry out, and a Lord at the bottom. SPEC sections 2,
4.10 and 7 are the contract; this page says how it is built and how to try it.

## Try it

| Command | Who | What |
|---|---|---|
| `/awaken gate open <rank> [theme]` | admin | Rolls a gate 18–30 blocks from you with the Director's rules (rank/theme forced). It forms in a few seconds. |
| `/awaken gate list` | anyone | Gates in the world: rank, state, distance, direction, breaks-in (theme only if scouted or admin). Click to trace. |
| `/awaken gate trace <id>` | owner/admin | Record, seed, cell/region file and every Director decision for that gate. |
| `/awaken gate enter <id>` | admin | Walk in without walking there. |
| `/awaken gate break <id>` | admin | Break it now (spill wave, roaming Lord, blight). |
| `/awaken gate satchel` | anyone | Look into your Satchel. |
| `/awaken gate leave` | admin | Extract immediately (keeps the Satchel). |
| `/awaken gate scout <id>` | admin | What the Veil's scouting does (`Gates.markScouted`). |
| `/awaken gate director` | admin | Run one Director pass for you now (no chance roll). |
| `/awaken why` | anyone | The last decisions, including every gate roll line. |
| `/awaken gate interior enter` | admin | Walk into the newest standing gate (yours first), for tours that can't know its id. |
| `/awaken gate interior goto <start\|piece\|small\|shrine\|ante\|lord>` | admin | Inside a gate: stand where that place reads best, facing it (`ante` and `lord` go to the Lord's floor). |

A quick tour: `/awaken admin awaken`, `/awaken gate open C clockwork_foundry`, walk into the core (or
`/awaken gate enter <id>`), clear floors, read the run panel top right (under the task card), open K → Gate Map for the run summary and the
Satchel button, extract at a Rest Shrine or break the Seal. `/awaken gate break <id>` on another gate shows the blight.

Creative tab (AWAKEN): frame, cracked frame, Rest Shrine, Seal, Cache, Rune Bars, Rune, Entrance Sigil, Lost Lantern,
Updraft (all unbreakable outside creative).

## Lifecycle

`BUILDING → READY → ACTIVE ⇄ OPEN → CLEARED | BROKEN → RELEASED` (`GateState`, illegal transitions refused).
- **BUILDING**: a cell is allocated, the plan is registered for worldgen, the surface chunk and the first floor around
  the entrance (radius 3 chunks) are loaded with an `awaken:gate_build` ticket. Both are polled each tick (no main-thread
  generation); when done the frame is placed on real ground and the ticket released. A build that fails (wet ground,
  timeout 90 s) releases its cell; the Director simply rolls again later. You never see a broken build.
- **READY/OPEN/ACTIVE**: enterable. ACTIVE while anyone is inside (offline players count).
- **CLEARED**: the Lord fell. When the last player walks out, the gate is released.
- **BROKEN**: its play-day came. Released when its roaming Lord dies.
- **RELEASED**: the surface structure comes down (leaves a scorched patch), the cell waits for cleanup.

**Cleanup.** Released cells' `region`, `entities` and `poi` files are deleted in `ServerStoppedEvent` (the level is
closed, nothing can resurrect them). On the next start a released cell whose files are gone returns to the free list.
A cell is never released while an online player stands in it; an offline player whose gate is gone is sent home on
login with their Satchel as a claim ("The gate collapsed behind you. Your Satchel came with you.").

## Director (`Director`)

Every 1000 ticks per online awakened player in the overworld (staggered by UUID), if `AwakenConfig.GATES`:
- counts standing gates within 1500 blocks; spawn chance 100% with none, 30% with one, 8% with two or three, never at four;
- picks a spot 300–1500 blocks away from the generator's height estimate (no chunk load), never in ocean/river/beach
  biomes, ≥ 96 blocks from other gates;
- rolls the plan exactly like the plan's gate generator: rank 70/20/10 around `Progression.rank`; themes fitting the
  rank (THEMES `min`), none of the last 3 (memory relaxed only if nothing fits), the friendly market at weight 0.35;
  layout not one of the last 2; modifiers drawn without repeats inside `Curve.modifierBudget`/`modifierMax` (25% chance
  of one fewer), none for friendly gates; twist 1/8, forced on the 12th gate without one (weights 2/2/1/2/1/1/1/1);
  floors `Curve.floorsFor`, break timer `Curve.breakDays` play-days;
- writes every line to `DirectorLog` and to the gate's trace (`/awaken gate trace`).
Novelty memory lives in the player attachment `awaken:gate_memory` (kept on death): last 3 themes, last 2 layouts, twist
pity, scouted gates, extraction floors ("comfort"), twist streak, echo pace, themes cleared, bosses since a Power Core.

## The gate dimension (`awaken:gates`)

`data/awaken/dimension_type/gates.json` (y 0..255, no skylight, fixed time, no beds/anchors/raids) and
`data/awaken/dimension/gates.json` (generator `awaken:gate_cells`, biome source `awaken:gate_cells`).

- **Cells**: cell *i* at `((i mod 64)·1024, (i div 64)·1024)`, using 512×512 blocks = exactly one region file
  `r.(2·(i mod 64)).(2·(i div 64)).mca`. Main dungeon in slot columns 0–15, the annex (second gate / inner gate) in 16–31.
- **Floors**: floor *f* at y = 200 − 32·(f−1), each owning a 32-block band (−4..+27). A floor is a grid of 16-block slots
  (one slot = one chunk); rooms cover 1×1 to 4×6 slots.
- **Generation** (`GateChunkGenerator.buildTerrain`): looks up the cell's plan (`GateCells`, a concurrent map written on
  the server thread before any chunk of the cell is requested), builds the layout (`FloorBuilder`, cached, pure) and
  paints the chunk (`GatePainter`, pure). Everything else is void. No features, no structures, no natural spawns.
- **Biomes**: one per theme, `awaken:gate_<theme>`, carrying fog colour and distance, ambient particles, ambient sound
  and music as environment attributes; `GateBiomeSource` returns the cell's theme (the annex half its own).

## Themes

| Theme | Min rank | Palette (fill / floor / walls / light) | Atmosphere | Lord |
|---|---|---|---|---|
| Bell Crypt | E | deepslate / polished deepslate / deepslate bricks with bone ossuary niches / pearlescent froglight, soul lanterns, candles, cobwebs | grey-violet fog, ash, buried bells | the Bell Warden |
| Fungal Hollow | E | tuff / mycelium & moss / packed mud / shroomlight, spore blossoms, mushrooms | violet haze, spores | Mother of Spores |
| Ember Mines | E | blackstone / polished blackstone & gilded / blackstone bricks with ore seams / ochre froglight, timber posts and beams, lava pits | ember fog, ash, basalt sounds | the Ember Tyrant |
| Drowned Archive | D | prismarine / dark prismarine / bookshelf walls / sea lanterns, water pits | teal murk, bubbles, drips | the Drowned Archivist |
| Clockwork Foundry | D | deepslate / waxed copper with grates / weathered copper / copper bulbs, anvils, chains | smoke, sparks, grinding gears | the Gearwright Colossus |
| Glass Desert | D | sandstone / smooth & cut sandstone / chiseled bands / glowstone, end rods, amethyst, glass shards | sand haze, ringing glass | the Glass Serpent |
| Hive | E | honeycomb / honeycomb & terracotta hex walls / ochre froglight, shroomlight, honey | amber murk, honey drips, hum | the Hive Regent |
| Overgrown Temple | E | mossy cobble / mossy stone bricks / moss creeping up walls / verdant froglight, leaves, azaleas | green dusk, fireflies | the Root Saint |
| Lantern Night Market | D (friendly) | dark oak / spruce / striped wool awnings / lanterns everywhere | night purple, fireflies, music | the Lantern Broker |
| Frost Monastery | C | snow / diorite & blue ice / packed ice and quartz / sea lanterns, snow layers | pale fog, snowfall, chanting | the Abbot of Frost |
| Mirror Palace | C | calcite / smooth quartz / glass panels framed in quartz / sea lanterns, end rods | white fog, glints | the Mirror Knight |
| Precursor Lab | C | grey concrete / smooth stone & cyan terracotta / concrete with glass stripes / sea lanterns, copper bulbs | blue-black, sparks, counting voice | Unit Zero |
| Nightmare | C | obsidian / wrong floors (planks, grass, wool) / purple concrete / crying obsidian, floating furniture | purple dark, reverse portal motes, whispers | the Sleeper |
| Sky Archipelago | B | cloud wool / grass islands over a cloud floor with updrafts / birch / glowstone | sky fog, clouds, wind | the Sky Leviathan |
| Abyssal Shrine | A | obsidian / deepslate tiles & sculk / blackstone / crying obsidian, black candles | near-black, short fog, **no sound at all** (music volume 0) | the Hollow Herald |

## Layouts (`FloorBuilder`)

| Layout | Grammar |
|---|---|
| Labyrinth | 5×5 (6×6 from A) grid, one or two 2×2 halls, DFS spanning tree + 12% loops; Shrine at the farthest single room; dead ends become caches/elites. |
| Spiral Tower | A 12-room ring climbing 2 blocks per room (stairs in the lower room of each doorway) to a round Shrine hall at the top centre. |
| Shaft | The ring descends from +22 to the floor around a 2×2 open drop with platforms at each balcony and a pool at the bottom (a brave shortcut). |
| Hub and Spokes | A 3×3 ring hub, four spokes; three KEY rooms with elites; the Shrine's doorway is barred until all three keys fall. |
| Arena Chain | Snaking chain: connector → 2×2 arena (sealed until two waves are dead) × 2–4 → Shrine. |
| Stealth Halls | Lattice of junctions and long alcoved halls, dim lighting, sentries placed on arrival with short sight; being targeted raises an alarm (three more monsters, once per floor); clearing unseen pays a cache. |
| Puzzle Vault | Labyrinth with 2–3 rune puzzle rooms; each solved puzzle opens its caged vault cache; the Shrine opens when all agree. |
| Open Cavern | One noise-carved 4×6-slot cave (uneven floor, rock pillars, varied ceiling) whose zones wake one by one; side cache and elite rooms. |

The last floor is always the Lord's: start → hall → antechamber → a 3×3 arena (17 high; content version 2: round, built for its Lord, see "Interiors").
Room shapes: rect, octagon, round, cross, pillared, ring (solid core), pit (ledge + liquid), dais, hall (alcoves), cave,
shaft, islands. Doorways are 4 wide, framed in the theme's pillar block; height differences get real stairs; lights,
carpets on the main walks, wall props, hanging decor and theme details are placed by deterministic hashing.

## Rooms

START (entrance sigil; on floor 1 it offers a retreat that forfeits the Satchel), NORMAL, ELITE, KEY, CACHE (one
`Cache` block, loot into the Satchel), PUZZLE (rune line: pressing flips a rune and its neighbours; every start state is
solvable), SHRINE (Rest Shrine + Seal), BOSS, HALL, HUB, ARENA, CAVERN, ANTE, INNER_GATE, MARKET, SPIRIT.
Monsters appear when a player first enters a room (`MobHooks.spawnThemed` / `spawnBoss`, content level
`Progression.contentLevel(rank)`, theme tags + modifier tags), tracked per room (`GateMobs`, rebuilt from entity data
after restarts). Cap: 40 live gate monsters per gate.

## Interiors (content version 2)

Gate interiors are where players spend most of their fighting time, so every theme has a look you'd know from one
screenshot, its bigger rooms carry set pieces, and every Lord fights in an arena built for its moves. Code:
`gates/world/GatePainter` (the dressing), `Kit` (each theme's extra materials), `SetPiece` (the catalogue),
`Pieces` (dispatch and shared shapes), `PiecesRooms`, `PiecesSmall`, `PiecesArenas`. Everything is a pure function of
the room and a column, painted per chunk on worldgen threads (no lists, no allocation per column).

### The language every room speaks

- **Walls have a rhythm.** Pilasters at slot offsets 2, 5, 10 and 13 (the door frames are the ones at 5 and 10), the
  wall between them recessed one block as a niche whose back is the theme's signature panel (ossuary, bookcases,
  ember seams, brood comb, carved faces, frosted windows lit from behind, mirrors, lab panels, void glass...). A
  plinth at the foot, a lintel over the niches, a cornice of upturned stairs under the ceiling. Rooms 12 or more
  high get a second tier of niches. Organic themes (the Fungal Hollow) get soft patched walls with alcoves and
  glowing caps instead; the Sky Archipelago's walls and vaults are painted sky (`gate_sky`, full bright) with cloud
  banks along the bottom.
- **Floors have bands** in line with the pilasters, a border, and a medallion under each chandelier; the Mirror
  Palace is a black and white checker.
- **Ceilings**: beams across flat ceilings (resting on the cornice), ribs under domes, skylights (amber glass with the
  sun behind it in the Glass Desert; light panels in the Lab).
- **Light pools.** No more even grid of lamps: a chandelier at the room's centre (a five-light cross on chains in tall
  rooms) and at each slot's centre in big rooms, niche lights hung from the lintels, the set pieces' own lights. Dim
  themes (Bell Crypt, Fungal Hollow, Abyssal Shrine) light every other niche. Stealth rooms keep only the centre.
- **Fill light.** The gate dimension has no ambient light, and in the real client a room lit only by its fixtures was
  black between them at default brightness (the first in-game tour: Glass Desert, Nightmare, Abyssal Shrine, the Bell
  Warden's arena). Every room now also carries invisible light blocks on an 8-block grid just above head height
  (`GatePainter.fill`, level by theme: 12 Archive and Ember, 13 most, 14 Abyss, 15 Nightmare and every Lord's arena; none
  under the Lightless modifier), so the floor, the doors and every monster read at 12+ blocks while the fixtures still
  make the pools. Measured by `gates_interior_light` (median floor light on the critical path 7+ on two layouts per
  theme; the Lord's arena median 8+ with 8+ on 60% of its floor) and offline by `gate_interiors.py light`. The
  Nightmare's chandeliers are now warm lanterns and the Abyss's floor medallions crying obsidian.
- **Fog** of the dark themes is longer and lighter so dim rooms don't turn into haze: Bell Crypt `#24212f` 8 to 64,
  Fungal Hollow 6 to 56, Glass Desert a dark dusty sand `#5c5242` 16 to 96 (the bright sand painted every dim room
  brown), Nightmare `#231432` 8 to 64, Abyssal Shrine a deep violet-blue `#110f26` 8 to 64 (biome JSONs and
  `tools/content/gates_content.py`).
- **Version 2 rooms are taller**: single rooms 8 to 10 high (were 7 to 9), big rooms 11, arenas and hubs 12, the
  Shrine 10, the antechamber 11; the Lords' arenas vault up to the top of the band (17 at the wall, 26 at the apex).

### The way on

- The door of every room on the critical path that leads one room closer to the end has a **lit lintel** in the
  theme's guide light (froglights, sea lanterns, bulbs...). Side doors are dark.
- The last door before the Rest Shrine is lit in the **System's blue** (`gate_signal_lamp`) in its lintel and both
  frame posts; the Shrine room has a blue ring in its floor round the Shrine and Seal and a blue light above them. Blue
  through a doorway always means "the Shrine is here".
- A **runner** joins the doors on the critical path (carpet, an inlay, rails in the Ember Mines, a cyan line in the
  Lab), so the floor itself leads on.
- On the Lord's floor the antechamber's door into the arena is lit like the Shrine's (in the guide light), and
  **braziers** stand either side of it (soul fire in the cold themes, a cauldron of lava in the warm ones; neither can
  spread).

### Themes

| Theme | Walls (niche panel / pilaster) | Floor, ceiling, light | Set pieces (big rooms / single rooms) | The Lord's arena |
|---|---|---|---|---|
| Bell Crypt | `gate_ossuary` skulls behind polished deepslate; chiseled lintels | deepslate tile bands, ribbed vaults, soul lanterns in the niches | Bell Loft (a great bronze bell glowing from inside, small bells and soul lanterns on chains round it, candles where its shadow falls), Ossuary Aisles (free-standing bone walls with candles) / Sarcophagi, Candle Vigil | The Bell Warden's belfry: its great bell over the medallion, floor rings where the Toll Waves fall, eight statues with soul lanterns as cover (they move halfway) |
| Fungal Hollow | soft patched tuff, mud and moss with alcoves; glowing caps grown into the walls | mycelium and moss, shroomlight, spore blossoms | Mushroom Grove (four giant glowcaps with glowing gills) / Spore Pool (a ring of lit water round a mushroom island), Toadstool Ring | Mother of Spores: six giant caps are the only shelter from the Spore Rain; an inverted cap drips spores over her |
| Ember Mines | `gate_ember_ore` seams between timber posts | blackstone bands, timber beams with lanterns, rails down the path | Lava Forge (a lava channel the length of the hall, bridges where doorways cross, crucibles on chains, anvils) / Mine Gallery (timber frames, ore heaps), Cave-In | The Ember Tyrant: a moat of lava rings the floor (bridges on six spokes), basalt columns as cover from Eruption, glowing seams out from its anvil-stone, lanterns on timber beams |
| Drowned Archive | bookshelves and `gate_sodden_shelf` floor to lintel between dark oak | prismarine bands, reading lamps on chains | Flooded Stacks (shelves standing in shallow sea water, lamps under it) / Reading Room, Sunken Desks | The Drowned Archivist: eight tall stacks to brace behind when the Tidal Page hits, the outer ring already flooded, a chandelier of lanterns over the desk |
| Clockwork Foundry | chiseled copper and grates between iron pilasters | grate bands, `gate_gear` medallions, copper bulbs and lanterns | Conveyor (a belt with crates, a gantry with hooks, a press), Boiler (a banded copper drum to the ceiling, gauges lit, pipes) / Gear Wall (a wall of interlocking gears round lit hubs), Press Line | The Gearwright Colossus: six copper pylons with lightning rods (the Overload Arc), grate rings, a great gear flat under the vault, gears in the wall niches |
| Glass Desert | chiseled sandstone and `gate_sun_tile` | sand runner, amber skylights, lanterns | Buried Colonnade (columns half drowned in dunes, a fallen obelisk) / Glass Garden (glass and amethyst crystals out of the drifts, lit from below), Obelisks | The Glass Serpent: a floor of sand to swim through, six glass crystals to break its lunges, dunes against the walls, an amber oculus for a sun |
| Hive | `gate_brood_cell` behind resin bricks | resin and terracotta bands, honey dripping, candles in the niches | Brood Comb (six comb pillars round a royal cell glowing gold) / Honey Pools, Wax Pillars | The Hive Regent: six comb pillars carrying canopies of comb (shelter from the Honey Rain), honey pools in the middle ring, a crown of royal jelly over her |
| Overgrown Temple | `gate_temple_relief` faces with jade eyes; jade plinths | moss in patches, roots and vines | Root Tree (a trunk through the floor into the vault, buttress roots, a canopy hung with glow berries and lanterns) / Reflecting Pools, Idols | The Root Saint: root-caged pillars on a seven-block grid (dodge the Root Lances down the lanes), a jade altar, a canopy of leaves and glowing vines |
| Lantern Night Market | birch screens and cinnabar between red mangrove posts | red runner, beams strung with `gate_paper_lantern` | Lantern Tree (a dark oak hung with paper lanterns, benches round it) / Market Stalls (striped awnings, goods), Tea House | The Lantern Broker's plaza: stalls in a ring, lantern strings from the vault, a round rug where it waits |
| Frost Monastery | `gate_frost_window` lit from behind, `gate_rime_brick` | ice bands, ribbed quartz vaults | Cloister (an arcade round a frozen garden under a higher vault) / Choir, Ice Garden | The Abbot of Frost: eight quartz pillars, choir stalls in arcs (the Choir sings from them), a ring of ice where the Ice Spikes rise, a crown of sea lanterns |
| Mirror Palace | `gate_mirror` (full-bright silver) framed in quartz | black and white checker, crystal light | Mirror Maze / Mirror Hall (a ring of mirrors round a prism), Crystal Chandelier | The Mirror Knight: eight mirrors in a ring face the floor, a star of quartz in the checker, a chandelier of glass and end rods |
| Precursor Lab | `gate_lab_panel` and cyan glass between Precursor plating with glyph capitals | cyan guide line, light-panel skylights | Server Hall (rows of blinking `gate_lab_rack`, cable trays) / Specimen Tanks (lit water in plated tubes), Control Room | Unit Zero: target rings in the floor, six plated crates as cover from the Zero Lance, hazard stripes, an oculus of light panels where the Orbital Marks come from |
| Nightmare | `gate_dream_paper` (the pattern is eyes) and windows of `gate_void_glass` | wrong floors, soul lanterns | Giant Table (legs like pillars, a man-high candle, chairs you can't climb) / Wrong Room (furniture on the ceiling, a door to nowhere), Stairs to Nowhere | The Sleeper: a giant's bed against the far wall, furniture and lit pumpkins floating at every height, stars in the vault |
| Sky Archipelago | painted sky, cloud banks | grass islands over a cloud floor, glowstone suns | Wind Shrine (a ring of white pillars with streamers round a beam of light) / Floating Isles (round islands, a birch, lamp posts), Cloud Bank | The Sky Leviathan: islands over a cloud sea (updrafts throw you back up), clouds drifting below, a vault of open sky |
| Abyssal Shrine | `gate_void_glass` behind obsidian with crying lintels | sculk, black candles, almost nothing else | Void Rift (the floor falls away into stars, candles round the rim) / Monoliths, Sculk Bloom | The Hollow Herald: six obsidian monoliths as cover from the Void Bolts, an event horizon of void glass round its spot, a ceiling of stars |

### Set pieces

- 47 room pieces (3 or 4 per theme) and 15 arenas (`SetPiece`). Chosen by `FloorBuilder.pieces()` (content version
  2 only) from per-room hashes, never from the floor's random stream, so the rooms themselves are exactly what
  version 1 built. Big rooms (2x2) take a big piece, each big piece once a floor (an Arena Chain's later arenas keep
  their own shapes); single rooms take one of the theme's small pieces 3 times in
  10; the theme's pieces take turns so a floor shows its range. A piece may set the room's shape.
- Rules every piece keeps (`Pieces.clear`): the axes from each door to the centre stay walkable and level (5 wide);
  nothing within two blocks of a feature (cache, runes, the Lord's spot, the exit portal's row); a piece with an
  obstacle at the centre (a boiler, a tree, a rift: `SetPiece.core()`) keeps a 2.6-block walkway round it. Obstacles
  are `RoomGeometry.blocked`, so monsters never spawn in them.
- No block entities in worldgen (no skulls, bells, chests, lecterns, campfires): the crypt's bells are `gate_bell`
  (a plain model) and its skulls are in the `gate_ossuary` texture. Liquids sit in basins; fire only on soul soil.

### The Lords' arenas

The boss room keeps the mobs module's size (3x3 slots, 17 high at the wall) and is round (radius 22), so every spot a
player can stand on is inside the Lord's arena radius (22 to 28; the friendly Broker's is 18). The Lord appears on a
lit medallion at the centre with a ring of lights set in the floor round it (dim themes add an invisible light block
above the spot); the doors open straight onto it. Cover stands at about 0.55 of the radius; hazards (lava, flood, ice,
honey, sand) sit in rings away from the spot. The Double Gate's second Lord fights in its own Lord's arena built from
the annex theme's materials; the inner gate's keeper gets its theme's arena at 2x2. Measured by
`gates_interior_arenas_fit_lords` (every Lord spawned for real):

| Lord | size (w x h) | arena radius | farthest standing spot | open floor within 10 |
|---|---|---|---|---|
| Bell Warden | 1.4 x 3.0 | 22 | 23.0 | 317/317 |
| Mother of Spores (flies) | 4.0 x 4.0 | 24 | 23.9 | 317/317 |
| Ember Tyrant | 1.2 x 3.9 | 22 | 23.0 | 307/317 |
| Drowned Archivist | 1.1 x 3.7 | 22 | 23.0 | 317/317 |
| Gearwright Colossus | 2.5 x 4.9 | 24 | 23.0 | 317/317 |
| Glass Serpent (flies) | 2.2 x 2.2 | 26 | 23.0 | 317/317 |
| Hive Regent (flies) | 2.8 x 2.4 | 22 | 23.0 | 317/317 |
| Root Saint | 1.6 x 4.9 | 22 | 23.0 | 294/317 |
| Lantern Broker | 0.8 x 2.0 | 18 (friendly) | 23.0 | 317/317 |
| Abbot of Frost | 1.0 x 3.3 | 22 | 23.0 | 317/317 |
| Mirror Knight | 0.8 x 2.5 | 22 | 23.0 | 317/317 |
| Unit Zero | 0.6 x 1.8 | 22 | 23.0 | 315/317 |
| The Sleeper | 1.3 x 4.3 | 22 | 23.0 | 317/317 |
| Sky Leviathan (flies) | 3.2 x 3.2 | 28 | 23.0 | 316/317 |
| Hollow Herald (flies) | 1.3 x 3.9 | 24 | 23.0 | 317/317 |

### Interior blocks

26 decorative blocks registered in `GatesRegistry` (`INTERIOR_BLOCKS`, creative tab, self drops, mining tags), textures
and content from `tools/art/specs/gate_interiors.py`: `gate_ossuary`, `gate_bell_bronze`, `gate_bell`, `gate_glowcap`
(light 12), `gate_glowcap_gills` (9), `gate_ember_ore` (7), `gate_sodden_shelf`, `gate_gear`, `gate_conveyor` (axis),
`gate_sandglass` (translucent), `gate_sun_tile` (6), `gate_brood_cell` (6), `gate_royal_jelly` (translucent, 12),
`gate_temple_relief` (facing), `gate_jade`, `gate_paper_lantern` (15), `gate_rime_brick`, `gate_frost_window`
(translucent, 9), `gate_mirror` (full bright, 3), `gate_lab_panel` (9), `gate_lab_rack` (facing, blinking, 5),
`gate_dream_paper`, `gate_cloud` (full bright), `gate_sky` (full bright, 6), `gate_void_glass` (3), `gate_signal_lamp` (14).
Glowing parts are emissive overlays (`light_emission` model elements).

### Seeing it without a client

- `gates_interior_dumps` writes one floor and the Lord's floor of every theme to `run/interiors/<theme>_floor1.json`
  and `<theme>_lord.json` (the floor's band as a palette and a base64 grid, its rooms, and cameras).
- `python3 tools/art/specs/gate_interiors.py preview DIR [filters]` (also `structures.py interiors DIR`) renders each:
  a cut-away plan from above (walls capped dark at the cut, so floors read) and eye-level shots from the dump's cameras
  (`start` facing the way on, `piece` the biggest set piece, `small` a single room's piece, `shrine` facing the blue
  door, `ante` facing the arena, `lord` from the arena's doors) with the real textures, the game's block-light flood
  fill and warm lightmap curve, and the theme's fog. A numpy voxel ray-caster: cubes, boxes, crossed planes,
  alpha-tested cut-outs, translucent glass and water. `python3 tools/art/specs/gate_interiors.py sheet OUT.png` lays out
  the textures.
- What the previews showed and what changed: rooms read as boxes at version 1's heights (now taller); lamps set into
  the pilasters read as glowing cubes (gone: lights hang in niches and from chandeliers); unlit clouds and mirrors
  read as grey slabs (now full bright); the arenas of the Abyss, the Nightmare and the Temple were black from the door
  (rim lanterns, lamps in the root cages, lit floating pumpkins); single rooms all had the same piece (a second small
  piece per theme, and 3 in 10 single rooms instead of 4); door tunnels jutted into rooms as low stubs (gone: past the
  wall line the room's own floor and ceiling run); the cameras first stood in doorways (now where you walk in).
- Tour: `tools/autopilot/interiors.txt` (integrator) opens a gate of every theme with `/awaken gate open`, walks in with
  `/awaken gate interior enter` and stands at each spot with `/awaken gate interior goto <start|piece|small|shrine|ante|lord>`.

### Doorways inside a gate

The exit after the Lord, the way back from a second gate, inner gates and rifts are openings 3 wide (3 tall, 4 for inner
gates and rifts), placed whole by `GateBlocks.Portal.place` (or painted with `opening(...)`). The bottom block of the
middle column is the ANCHOR: its block entity (`GatePortalBlockEntity`, no data) is drawn by `GatePortalRenderer` as one
vortex across the opening, `GateVortex.Look.forDoorway` (the surface gate's geometry at the opening's size, no rock
shards), heart first so it hides what is behind. Colours follow the gate's rank (`rank` in the block state); rifts are
red-rimmed. The other blocks are PART: invisible, no outline, still a full block for walking in. Painted inner gates get
their anchor's block entity from `GateChunkGenerator.fill`. Doorways from before (`role=legacy`, the old per-block
texture) upgrade on a random tick (`Portal.upgradeLegacy`). GameTest `gates_doorway_one_surface`; the frames test exports
doorway frames for the offline preview.

## Tuning (P3: D–B)

Pushing deeper is now harder in the game, not only in the simulator: version 2 floors carry more monsters and more
elites the deeper you go (`FloorBuilder.depthMonsters`, `extraElite`):

| | floor 1 | floor 2 | floor 3 | floor 4 | floor 5 |
|---|---|---|---|---|---|
| extra monsters per room (plain, elite, key, hall, hub, cavern, arena) | 0 | 0 | +1 | +1 | +2 |
| arena second wave elites | 1 | 1 | 2 | 2 | 2 |
| extra chance a plain room is elite (on top of version 1's 12%) | 0 / 0 / 1 % (D/C/B) | 3 / 4 / 5 % | 7 / 8 / 9 % | 11 / 12 / 13 % | 15 / 16 / 17 % |

Unchanged and checked: floors per gate (`Curve.floorsFor`: D 3 or 4, C 4 or 5, B 4 or 5, the last the Lord's), the
modifier budget (D 2, C 3, B 4) and count (D 1, C and B 2), the twist weights (2/2/1/2/1/1/1/1, one in eight, pity at
12), labyrinth size (5x5 to B) and the version 1 room counts.

Measured composition (`gates_tuning_matches_sim` rolls 240 gates per rank through the real Director and FloorBuilder;
monsters on the critical path, an elite counting 3; ordinary floors only):

| rank | floor 1 | floor 2 | floor 3 | floor 4 | side rooms, floor 1 |
|---|---|---|---|---|---|
| D | 16.2 | 16.6 | 24.4 | – | 9.3 |
| C | 24.9 | 25.1 | 31.3 | 32.9 | 12.4 |
| B | 28.6 | 29.2 | 36.8 | 37.3 | 16.4 |

The simulator (`tools/sim/pacing_sim.py`) now plays real gates: floors, modifiers and twists drawn from that export,
a floor's danger from its monsters (`FLOOR1_RISK` x `ATTRITION`^depth x (monsters / reference)^0.7 x modifier and twist
danger / level-ratio^2.2 / skill), the Lord's floor its own risk, floors with more monsters taking longer, Red Gates
forcing the full clear. Results for the balanced player (`tools/sim/pacing_results.json`, `gate_checks`):

| | D | C | B | target |
|---|---|---|---|---|
| deaths per 10 gates | 1.31 | 1.99 | 2.13 | 1 to 3 |
| best floor to plan to stop after (levels per hour) | 4 of 4 | 5 of 5 | 4 of 4 | 3 to 5 |
| stopping one floor early: share of the best, death rate | 98%, 13% vs 24% | 99%, 26% vs 40% | 97%, 20% vs 32% | a real decision |

Stopping one floor before the Lord keeps almost all the XP at about half the risk; the Lord's hoard (Power Core,
Lord's Heart) is what the last floor is for. The pacing targets still hold (level 50 at 56.6 h, 135 at 104 h). To
re-tune: change the numbers, run the GameTests, copy `run/gate_tuning.json` to `tools/sim/`, run
`python3 tools/sim/pacing_sim.py --json tools/sim/pacing_results.json` (all checks must pass), and run the GameTests
again: `gates_tuning_matches_sim` fails while the game and the simulator disagree.

## Runs

`awaken:gate_run` attachment: gate, area, floor, Satchel (27 slots) + Satchel XP, kills, start/floor clocks, damage this
floor/run, kill XP this floor, cheat-death flag, lanterns, cleared floors, run index.
- **Pickups** inside a gate go to the Satchel (`ItemEntityPickupEvent.Pre`), except items you threw yourself.
- **Kill XP**: 40% of `Curve.floorReward` split across the floor's planned monsters (main path in full, side rooms at
  half; elites ×2, wardens ×4), granted live with `GATE_KILL`, capped at that 40%. Friendly/Hollow floors pay it on
  clearing. Below-rank gates ×0.3.
- **Floor cleared** = stepping into the Shrine room: +60% as Satchel XP, 30% heal, counters.
- **Rest Shrine** → Signal window, [Y] Extract: claim `gateId:floor:player` (`#n` for later runs), walk out of the
  gate mouth, claim delivered. **Seal**: next floor.
- **Lord**: everyone on the boss floor banks their Satchel plus their own hoard roll (Lord's Heart, essence, loot,
  Power Core 50%, guaranteed if none in the last 3 Lords); exit portal appears.
- **Claims** (`GateRegistry`): PENDING with the Satchel's contents in the same tick the Satchel empties; delivered only
  outside the gate (items, overflow at your feet, Satchel XP via `grantXp`); then DONE. A DONE or PENDING id is never
  written again. Pending claims are paid on login, respawn and every 5 s.
- **Death**: STORY keeps half of every stack (and every other single item) and half the Satchel XP as a claim, no
  Fracture; HUNTER/HARDLINE/HARDCORE lose the Satchel and get `Progression.fracture` 8 min. Insight on the boss floor.
  Gear and vanilla XP are held aside and returned on respawn. `Perks.CHEAT_DEATH_PER_GATE` cancels one death per run.
- **Pressure**: after 4 min on a floor a wave every 45 s (2 + n + rank/2, elites from the third), at 8 min the Warden.
  Red Gates: 3 min / 35 s. Waves never enter safe rooms.

## Modifiers (`Modifiers`)

| Modifier | Behaviour |
|---|---|
| Lightless | Generated without lights (Shrines keep a candle); client fog closes to ~22 blocks and darkens. |
| Low Gravity | −60% gravity and +8 safe fall on players and gate monsters. |
| Volatile | `VOLATILE` tag; a gate monster that dies sizzles and bursts a second later (no block damage). |
| Swarm | ×1.6 monsters, each −30% health (spawn-time only). |
| Elite Pack | An extra elite in every room of three or more. |
| Silence | Noise meter (sprinting, jumping, hitting, being hit; sneaking quiets it); at 24 a Listener comes for you (30 s cooldown). |
| Flooding | The room you linger in fills one block every 20 s, up to 3 (Shrine rooms stay dry). |
| Haunted | `HAUNTED` tag; a SPIRIT-role thing appears near a player every 80 s. |
| Mirror | 40 s into each floor your shadow steps out of the wall: your skin, your weapon, hunting only you. |
| Collapse Timer | Each floor caves in after 7 min − 30 s per rank (min 4); after that, falling stone hurts outside safe rooms. |
| No Regen | Natural regeneration (heals ≤ 1) is cancelled; the Shrine's rest still heals. |
| Glass | All damage to players in the gate and to its monsters is doubled. |
| Fortune | Caches and hoards roll twice, monster drops doubled, +25% monsters, bigger waves. |
| Echoing | `ECHO` tag; dead monsters get up once, two seconds later. |

## Twists (`Twists`)

| Twist | Behaviour |
|---|---|
| Double Gate | After the Lord, a cracked wall in the arena opens on a Rift into a two-floor second dungeon (another theme) with a law: strike while still / in the light / from behind (rolled per gate). Hits that break the law don't land. Its Lord pays a claim and `latent/double_gate_cleared`. |
| Red Gate | Shrines and the entrance won't let you out until the Lord dies; pressure starts sooner and comes faster. `latent/red_gate_cleared`. |
| Hollow Gate | No monsters. 15 s into each floor something follows you, moving only while nobody looks at it; a touch takes a quarter of your health and it blinks away. |
| Treasure Gate | A chest-wearing Hoarder on every floor flees; each hit shakes loose an essence shard into your Satchel, killing it pays the hoard; 90 s after it sees you it escapes. |
| Inverted | You are puppeting one of the Lord's minions: you're invisible inside it, its kind won't attack you until you strike (10 s), the Lord sees through it. |
| Friendly | No combat: a lost spirit needs the floor's three lanterns before the Shrine opens; a market on floor 1; the Lord is a broker you talk to. The Lantern Night Market theme plays the same way. |
| Echo Gate | A translucent you races you to each Shrine: replaying the exact path you took through that floor before, or walking the main path at the pace of your last cleared floor. Beat it for a bonus cache. |
| Gate within a Gate | On floor 2 a small inner gate opens on a two-floor gate of another theme with a keeper; its way back returns you to floor 2. |

## Surface, card, pillars, breaks

- **Structure**: platform, frame posts and lintel (`gate_frame`), a 3×5 animated `gate_core` coloured by rank; walking
  into it enters the gate.
- **Pillars**: the server sends nearby gates (≤1600 blocks) to the client; `Pillars` draws a beacon-style beam per gate in
  the rank colour (Sovereign: black core, gold halo; broken: dark red), clamped to ~70% of the render distance and
  scaled down so it keeps its true apparent size and never hits the far plane or the fog.
- **Card**: within 24 blocks, a GATE window: rank, breaks-in, omen; PER ≥ 20 layout, ≥ 40 modifiers, ≥ 60 floors;
  scouted: theme, Lord, twist and force interactions too.
- **Breaks**: on the break play-day (deferred while someone online is inside): spill wave of 6 + 2·rank, a roaming
  Gate Lord (BREAK role, home radius 40, boss bar to players within 96), broken cores; the client greys fog and
  desaturates (`awaken:blight` post effect) within 96 blocks. The Lord's death drops better loot (and gives each nearby
  player a roll), counts `breaks_held_at_hearth` near a Hearth, and releases the gate. One play-day ahead, players whose
  Hearth is within 256 blocks (or who stand within 512) get a warning; Day's End lists the nearest gate about to break.

## Counters

`gates_cleared`, `floors_cleared`, `floor_streak_no_early_extract` (reset by an early extraction or a retreat),
`boss_floors_no_damage`, `gates_no_hit`, `twists_seen` (once per gate, when you come out alive), `gates_solo_no_echo`,
`floors_pushed_past_comfort` (floors cleared deeper than the median of your last five extractions), `gate_cleared_unscouted_fast`
(unscouted and cleared within 20 minutes), `bosses_killed_unarmed`, `latent/red_gate_cleared`, `latent/double_gate_cleared`,
`latent/three_twists_row`, `gates_scouted` (via `Gates.markScouted`), `breaks_held_at_hearth`. Day's End gets one gate per
extraction or clear; `bestMoment` on flawless/twisted/above-rank clears and held breaks; `markNewThing` on the first clear
of each theme.

## Public API (`Gates`)

`gateAt(level, pos)`, `activeRun(player)`, `isInGate(entity)`, `satchelAdd(player, stack)`, `preview(id)` /
`preview(server, id)`, `markScouted(player, id)`, `isScouted`, `prologuePlan(seed)` + `openScripted(player, plan)` (story
prologue: a scripted gate 20 blocks ahead that never breaks), `near(level, pos, radius)`, `forceExtract(player, keep)`,
`refresh(player)`.

## Client

Run panel (top right, under the task card, SPEC §27.10: gate, floor, Satchel, kills, floor clock, pressure, noise,
collapse countdown, twist hint; never over a screen), Satchel screen
(read-only 9×3), Status → Gate Map (top-down map with rings at 500/1000/1500, list with distance/direction/breaks-in, run
summary with a View Satchel button), pillars, blight fog + post effect, Lightless fog, renderers for the shade/ghost/
follower (player model with the owner's skin, tinted) and the hoarder (baby zombie with a chest).

## Tests

`tools/gradle.sh 1200 runGameTestServer` runs: `gates_director_rules` (300 rolls × 7 ranks: no theme from the last 3, no
layout from the last 2, budget/count/no duplicates, twist within 12, rank within one, Curve floors/days; same seed → same
gate), `gates_plan_determinism` (24 plans × all layouts/twists: same plan → same rooms and same painted blocks, every
floor reaches its end, spawn spots exist), `gates_painter_features`, `gates_chunk_generator` (a real proto chunk),
`gates_dimension_json` (decodes the dimension with its codecs), `gates_claim_idempotent`, `gates_satchel_death_rules`,
`gates_state_machine`, `gates_cell_region_math`, `gates_puzzles_solvable`, `gates_codecs_roundtrip`,
`gates_overworld_structure`, `gates_build_break_hold` (create → READY → break → Lord → held → RELEASED → structure gone).

The GameTest server has no datapack dimensions, so the walking tests set `GateService.testLevel` to the test level,
paint the floors they need in place with `GatePainter`, and drive a mock player through them inside one tick:
`gates_run_walkthrough` (enter, rooms wake, kills, floor cleared at the shrine, Satchel, extract, re-enter, Seal to
floor 2), `gates_lord_and_death` (the Lord, claims, exit portal, release; death in a gate: Satchel lost, gear back,
Fracture), `gates_modifiers_live` (Low Gravity, No Regen, Glass both ways, Swarm, Echoing rise-once, Volatile without
block damage), `gates_modifiers_timed` (Mirror shade after 40 s, Silence Listener, Collapse Timer, pressure wave and
Warden), `gates_twist_echo_hollow`, `gates_twist_inverted_treasure` (disguise, target cancel, broken by a hit; Hoarder
hoard into the Satchel), `gates_twist_friendly_red` (lanterns, spirit, shrine unsealed; Red Gate refuses extraction),
`gates_twist_double_gate` (rift opens after the Lord, second gate entered, the law of the second gate) and
`gates_twist_gate_within` (inner gate painted on floor 2, keeper, way back, quiet afterwards). Mock players are creative
and can't be hurt, so player deaths are tested by posting `LivingDeathEvent`. Timers are tested by moving the floor
clock (`floorStartedAt`, `enteredAt`) back, not by waiting.

Interior tests (`gates/world/InteriorTests`):
- `gates_interior_pieces_keep_doorways`: 6 plans per theme (D to B, labyrinths and arena chains): every room with a set
  piece or arena is painted and walked (step up one, drop up to three, two blocks of head room): every doorway reaches
  every other, and in arenas the Lord's spot and the exit portal's row; every one of the 62 pieces and arenas is built somewhere.
- `gates_interior_arenas_fit_lords` (isolated): each Lord spawned for real (size with its scale, arena radius): its box
  plus a block (three above flyers) is clear at its spot, no standing spot beyond its arena radius + 3, 60%+ of the
  floor within 10 open, its spot lit 9+.
- `gates_interior_build_budget`: every theme's whole B-rank gate painted chunk by chunk after a warm-up pass: mean
  under 4 ms a chunk, worst under 40 ms, layout under 50 ms (measured: 0.1 to 0.3 ms mean, under 1.1 ms worst, layout
  under 3 ms). Floors are still generated by worldgen threads chunk by chunk as the player arrives; nothing is placed
  on the server thread.
- `gates_interior_light`: every theme's showcase floor and a second gate on another layout: median block light where
  you stand on the critical path 7+; the Lord's arena median 8+ and 8+ on 60% of its floor (a flood fill like the
  game's, each step costing the block's light dampening).
- `gates_interior_dumps`: the preview dumps (see "Seeing it without a client").
- `gates_tuning_matches_sim`: the composition export matches `tools/sim/gate_tuning.json` and every check in
  `tools/sim/pacing_results.json` passes (see "Tuning").

## Decisions

Folded into SPEC section 13 ("Gates" and "Story effects on the world"). New with the interiors (for SPEC):
1. Gate content version 2: set pieces, Lord arenas, taller rooms and the depth tuning apply to version 2 plans only;
   version 1 gates keep their rooms (READY unentered ones rebuild on start as before).
2. Every room's dressing (`Kit`) and the way-on lighting: guide-lit lintels on the critical path, the System's blue on
   the last door before the Shrine and round it, braziers by the arena doors.
3. Set pieces keep door axes (5 wide) level and clear, features clear by 2, a 2.6 walkway round central obstacles; no
   block entities in worldgen; liquids only in basins; fire only on soul soil.
4. Lord arenas stay 3x3 slots and 17 high at the wall, round (radius 22) so no standing spot is outside the Lord's
   arena radius; the Lord's spot is lit 9+ and clear by a block round its box.
5. Depth tuning: +1 monster per room from floor 3, +2 from floor 5; a second-wave elite from floor 3; extra elite chance
   0.04 x (floor - 1) + 0.01 x rank - 0.02. Targets: balanced deaths per 10 gates 1 to 3 at D, C and B; push EV peak at
   floors 3 to 5.
6. 26 interior blocks (`gate_*`), listed above.

## Changes outside the listed paths (smallest possible)

- `gates/content/GatePlan.CONTENT_VERSION` 1 -> 2 (one line): new plans get the version 2 interiors; unentered READY
  gates rebuild with them; entered gates keep the rooms they were built with.
- `GatesRegistry.init` calls `gates.world.Interiors.init()` (registers the interior tests and the
  `/awaken gate interior` tour command), like `GateExterior.init` before it.

## Integration (done on the main branch)

- `GateAccess` installs `GateHooks` for the story, Veil, Above and Hearth: views carry the gate's mouth; `stabilise`
  refuses gates that never break; `openScripted` places the gate where the caller asks.
- `GateRecord.neverBreaks` = scripted or `StoryHooks.gatesCalm`; beacons carry CALM and NO_BLIGHT flags.
- `Progression.addGateToday` feeds the Day's End tally; `bosses_killed_unarmed` is counted only by mobs.
- Clone Vat saves bank the Satchel; mobs spawn a real Lord for BREAK (no second bar).
- Hearth has no storage block, so claim overflow still drops at the player's feet (the claim window says so).
