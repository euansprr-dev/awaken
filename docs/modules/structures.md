# Structures: one visual language for the overworld

The overworld's built things are the Signal's gates, the Precursors' ruins and their Well, and the named beasts' lairs.
They were add-ons: small, flat, single-material boxes, and the gate's opening was a grid of little pinwheels. This page
is the art direction they are now built to, then what each one is, how to look at it, and how it is tested.

Code: `gates/GateVortex` (pure shape and look), `gates/GateVortexBlockEntity`, `gates/Overworld`, `gates/GateExterior`,
`gates/client/GateVortexRenderer`, `gates/client/GateHum`; `above/ruin/RuinBuilder`, `RuinBlocks`, `RuinShowcase`;
`descent/well/Well`; `world/fauna/lair/LairBuilder`. Art and previews: `tools/art/specs/structures.py`. Autopilot:
`tools/autopilot/structures.txt`.

## The visual language

### Three families, three lights

| Family | Who made it | Story it tells | Light that names it |
|---|---|---|---|
| **Gates** | the Signal | a wound in the world, tearing the ground toward itself | the **rank colour** (E pale, D green, C blue, B violet, A amber, S red, Sovereign gold on black) |
| **Precursor** (ruins, the Well) | the Ascendants, Keepers and Sleepers | engineers who studied the gates and lost; their machines still hum under moss | **signal blue** for anything still alive, **amber** for warnings, spire tips and doors |
| **Lairs** | the named beasts | a home: where it sleeps, eats, hoards and marks its ground | one warm natural light (glow berries, froglights, candles, shroomlights, lanterns) |

Dark slate, amber lantern and signal blue stay the mod's identity. **Brass is an accent, never a material**: it marks
corners, portals, spire collars and crowns. A first pass that banded every column and railed every deck in brass read
as gaudy in the previews and was taken back.

### Palette by kind

| Kind | Body | Accent | Light | Weathering |
|---|---|---|---|---|
| Gate | `gate_frame` (obsidian with a glyph), blackstone, basalt, polished blackstone | `gate_frame_cracked` once broken | the vortex; `gate_fissure` (cracks in the rank colour, red when broken) | the pad: polished blackstone at the foot, basalt and tuff, coarse dirt and gravel at the crater edge, in patches |
| Vault | `precursor_plating`, `ruin_pillar` pilasters, polished deepslate plinth and cornice | `precursor_trim` at corners, portal and crowns; `ruin_glyph` panels | glow-panel lines, amber spire tips and beacon | `ruin_plating_cracked`, `ruin_plating_mossy`, moss carpets, vines in the recesses, hanging roots at the fallen ceiling |
| Monolith | polished blackstone (black, unbroken) | brass only at the foot and the top of the shaft | blue glyph channels, an amber eye in the floating capstone | half drowned in sand; broken sandstone standing stones |
| Ocean lab | light-blue glass domes ribbed in plating | a void-glass belt | sea lanterns round the base rings, glow rings inside, a mast of lights | prismarine and dark prismarine crust, dead coral |
| Sky platform | plating disc, glass cabin | brass at the cabin corners and rim posts | the keel's glowing rings (seen from the ground), deck lamps, amber spire | floating islands of grass, dirt, stone and ore it tore up |
| The Well | `well_stone`, deepslate tiles, polished and chiselled deepslate | brass collars under the crowns and lanterns | `well_light` crowns and lanterns, `well_glyph` bands and the floor circle, a glowing band at the shaft's mouth | cracked tiles, cobbled deepslate and moss on the outer steps, three fallen pillars |
| Lairs | the biome's own materials | the beast's sign: bone, silk, petals, horn, mycelium | one warm light at the den | trampled and churned ground, remains |

### Motifs

- **The ring.** The Well's ring of pillars, the ring of light round the vault's sealed door, the sky platform's disc,
  the ocean lab's domes and the gate's vortex are circles: the Signal's shape, which the Precursors copied.
- **The glyph.** The mark on `gate_frame` is the alphabet of `well_glyph` and `ruin_glyph`: the Precursors wrote in the
  gates' letters. Blue on Precursor stone, in the rank colour on a gate's rock fangs.
- **Torn ground.** Gates scorch and split the ground and throw up rock fangs; the sky platform carried islands up with it.
- **Light guides the eye.** Lights mark the way in and the goal (the portal, the sealed door, the den). By night a
  structure is its silhouette plus its light lines; by day its shape.

### Scale rules

- Readable from 150+ blocks: ruins and the Well have a vertical element at least twice the height of the trees round
  them (vault spires 20 and mast 23, monolith 37 over its plaza, Well pillars 17-24); gates grow with rank; lairs are
  homes (radius 8-11, one focal element 5-10 tall).
- Depth: walls have a plinth, a cornice and insets (the vault's pilaster line stands a block proud of its recessed
  panels); openings get a frame one block deep (the vault's double-reveal portal).
- Weathering: 10-25% of surfaces swapped for a weathered variant by smooth noise (patches, not speckle), more at the
  base and on the tops.
- Terrain: foundations fill down to real ground; outer rings follow the terrain column by column; only natural blocks
  are ever replaced (gates and the Well never eat a build); nothing floats unless it is meant to.

## Gates

A vortex hangs a hand's breadth above a scorched pad; the bottom of the opening is where you walk in.

| Rank | Opening (radii) | Pad / crater radius | Shards | Fangs |
|---|---|---|---|---|
| E | 2.6 x 3.3 | 5.6 / 9.1 | 4 | 2 great, 2 lesser, 2 crown, rubble |
| D | 3.2 x 4.0 | 6.2 / 9.7 | 6 | |
| C | 3.9 x 4.9 | 6.9 / 10.4 | 8 | 3 crown |
| B | 4.6 x 5.8 | 7.6 / 11.1 | 10 | |
| A | 5.4 x 6.8 | 8.4 / 11.9 | 12 | 4 crown |
| S | 6.5 x 8.2 (17 tall) | 9.5 / 13.0 | 14 | |
| Sovereign | 7.5 x 9.5 (20 tall) | 10.5 / 14.0 | 16 | 5 crown |

- **One surface.** `GateVortexRenderer` (a block entity renderer on the anchor core, the bottom centre of the opening)
  draws the whole opening from one filtered 512x512 atlas (`textures/entity/gate_vortex.png`, `blur: true`) so every
  translucent layer is one render type and sorts together: a glow halo, a dark heart, spiral arms (three uneven arms on a
  logarithmic spiral; turning it moves them inward), thin filaments on their own twist and speed (they shear against
  the arms), a crackling rim in the rank colour, motes (two in three spiral in from the glow, one in three lifts off the
  pad and is pulled into the rim), glows behind the shards, and cracks over the pad. The surface is a shallow funnel
  that always recedes from the viewer. Rotation is continuous (game time plus partial ticks). Rock shards (lit, cut-out
  texture `gate_shard.png`) orbit the rim. Geometry, colours and motion are `GateVortex` (pure, shared with the preview).
- **Visibility.** Rendered off-screen-aware (a global block entity) with bounds covering the whole gate, culled per rank
  at 96-216 blocks with a soft fade over the last 20%, so it never pops at a screen edge or a section boundary. The
  pillar beam now rises from the top of the vortex.
- **Interaction.** The cores are invisible (`RenderShape.INVISIBLE`), no collision, light 15, and still enter the gate
  (`Core.entityInside` -> `Interactions.touchCore`). `Interactions.structureAt` now asks `Overworld.isPart` with the
  gate's own rank and a 26-block reach (a towering S gate's top core is 16 above its foot).
- **Ground.** A flat pad at the gate's ground level (founded below, trees and plants cleared above, the ground itself cut
  back only round the opening and three blocks up elsewhere, so a gate on a slope sits in a notch rather than a quarry),
  a ragged rubble lip at its edge (open where you walk through), a crater ring that follows the terrain and thins out,
  5-8 fissure rays wandering out from the foot plus a broken ring round it. Each `gate_fissure` block knows its crack's
  direction (`dir` 0-3) so neighbours join into one long crack; the glow is an emissive overlay.
- **Fangs.** Voxelised leaning cones: two great fangs flank the opening (82-98% of its height), two lesser ones stand off
  them, a crown of smaller ones rings the pad within 50 degrees of the face axis (the walk through stays clear), rubble
  spikes at the edge. Base blackstone and basalt, polished blackstone in the middle, frame stone (with its glyph) toward
  the tips.
- **Looks** (synced on the anchor's block entity, refreshed from the gate every 2 s, sent only on change):
  forming (grows from a point with a little overshoot, the heart first) while BUILDING; open; **about to break**
  when the break is a play-day away or today (the vortex breathes, spins faster, the rim flickers toward red, motes
  hurry, shards jitter, cracks spread over the pad: 55% then 100% of the way); **broken** (red-black palette, arms
  churning, embers shed outward, fangs snapped and cracked, shards fallen on the pad, fissures red, ash from the cores,
  magma flecks in the pad); **calm** when the story calmed the gates (pale, a third of the speed, still shards, few
  motes); **spent** once cleared (shrunk to 42%, dim, no motes). A Red Gate's rim and glow are red whatever its rank.
- **Sound.** `GateHum` (client) loops two sounds at the vortex centre while you are within ~50 blocks: `gate.hum` (the
  beacon hum, pitched lower for higher ranks) and `gate.vortex` (a slow portal whoosh or a deep conduit drone), both
  with a 48-block linear falloff, so the hum swells as you approach. Louder and harsher when broken, pulsing about to
  break, soft when calm, nearly silent when spent; eased in and out.
- **Old gates.** Gates built before this change are rebuilt the first time their chunks are loaded (`GateExterior`, every
  10 s, only gates whose surface still has the old doorway); removal also sweeps the old doorway's blocks.
- **Placement.** `Overworld.untouched(level, pos, axis, rank)` checks the rank's whole footprint (pad, crater ring,
  opening, fangs) for builds; `GateService.placeSurface` passes the gate's rank; the rank-blind overload assumes the
  biggest gate. Game-master blocks (structure and test blocks) are not builds. The footprint stays within the surface
  build's 3x3 chunks (crater radius at most 14).

## Precursor ruins

Loot tables, the Scanner chest, machines (1-3, no repeats; the spawn vault's Gate Scanner), terminals, landmark ids
(start chunk), the outer cache, rank doors and the ocean lab's membrane are unchanged in behaviour. Footprints grew
(they stay square: the piece turns inside them).

| Kind | Size | Silhouette and story |
|---|---|---|
| Vault (and spawn vault) | 23 x 24 x 23 | A stepped bunker: a forecourt between two **spires** (3x3 to the cornice, then a fluted spire to 19, amber tips), pilasters a block proud of recessed weathered panels with glyph plates, a polished deepslate cornice overhanging a block, a **double-reveal portal** framed in brass with a glowing emblem; inside, an antechamber whose ceiling fell in at one corner (sky, rubble, hanging roots), a **ring of light** round the sealed rank door, and behind it the machines on brass daises along the back wall with a glowing line on the floor to the terminal. A second tier with a broken corner, a crown and a **beacon mast** to 23. The outer cache sits in the forecourt beside the west spire (z < 4); the spawn vault's Scanner chest in the antechamber. |
| Monolith | 17 x 46 x 17 | A **black obelisk** (polished blackstone) rising from a half-buried pedestal over a sandstone plaza with a Precursor ring inlaid and broken standing stones; glyph channels glow blue up each face; brass only at its foot and the top of the shaft; above **three blocks of empty air** (stones hanging in it) its capstone floats with an amber eye. A brass-framed hatch leads down the ladder to the sealed chamber. Outer cache on the plaza by the hatch. |
| Ocean lab | 31 x 22 x 31 | **Three lit glass domes** (light-blue panes ribbed in plating, a void-glass belt) on prismarine-crusted rings: the main dome with the machines and a membrane airlock on its east side, a storage dome (loot, barrels), a study dome (terminal, lectern), joined by glass tubes; sea lanterns ring the bases and hang inside; a **mast of lights** rises toward the surface. Outer cache in the main dome by the airlock. |
| Sky platform | 49 x 26 x 49 | A disc whose **keel of rings glows** (it reads as a light in the sky from the ground), a calm deck with lamps and brass rim posts, a glass cabin with a fluted spire and amber tip; **four floating islands** it tore up with it (grass, ore in their undersides, hanging roots, a small tree, a lamp post) **tethered by chains** to posts on the rim, and chains with lanterns dangling under the keel. Outer cache on the deck before the cabin. |

New materials (`RuinBlocks`, registered from the ruin package): `ruin_plating_cracked`, `ruin_plating_mossy`,
`ruin_pillar` (fluted, axis), `ruin_glyph` (plating with an emissive glyph overlay, light 6). Items, self-drop loot,
pickaxe tag, creative tab.

`/awaken above ruin showcase <kind>` (game masters) builds one ahead of you, its front toward you.

Ruin pieces save the blueprint version (`style`); a ruin whose start was saved before the redesign keeps what it
already built and does not weld the new blueprint onto its unbuilt chunks.

## The Well

Built at runtime as before (SavedData, ~10 columns per tick, now 49x49 columns over ~12 s). A **sunken court** round a
**shaft 15 wide** (radius 7): a knee-high parapet of chiselled deepslate broken by eight gaps (jump in), a glyph circle
and spokes on the floor, the lift on the court; **four lantern pylons** at the court's corners whose caged `well_light`
sits at y+7 exactly where the landmark glow is drawn (`world/landmark/client/LandmarkClient`, (+-8, y+7.6, +-8)); terraces
step up (+1, +2) to a **ring of twelve great pillars** at radius 18 (15-22 tall: plinth, Well stone body, a glyph band
every five blocks, a brass collar, a `well_light` crown; three of them fallen as stumps with their pieces lying outward;
lintels join three pairs); steps back down to the land with moss and cobbled deepslate. Down the shaft: a band of light
just under the rim (the mouth glows at night), a glyph ring every 12 blocks, the lit pool at the bottom.
Unchanged contracts: the lining at RING_INNER + 1, the lift at RING_INNER + 3, the rim spot at RING_INNER + 4 standing
on the court, `inShaft`, the candidates' margins (RING_OUTER is now 23). Only terrain, plants and the Well's own blocks
are replaced. A Well built in the old look (`WellData.style` 1) is rebuilt in place on the next start (same centre,
ground level and shaft).

## Lairs

Same nine styles, footprints (EXTENT 11, HEIGHT 12), dens and placement; each now has a focal element, a sign of the beast,
its remains or hoard and one warm light:

| Lair | Focal element | Sign, hoard, light |
|---|---|---|
| the Bramble Wallow | an uprooted oak lying across the north, its root ball reared up five blocks | stolen hay by the den, a gouged scratching tree, a trampled path out, a double bramble ring; glow berries under the root ball |
| the Glass Hollow | the wyrm's ribcage arching seven blocks over its spine (five ribs, one snapped) | the skull with dripstone teeth, shed terracotta scales, leaning pillars; glass vents over magma |
| the Antler Grove | six antler trees 6-8 tall, tined twice, fence tips | a shed antler in bone on the altar, mossy standing stones; candles and hanging soul lanterns |
| the Toad Throne | an arch of mangrove roots and mud bricks behind the mound, six blocks high | frogspawn in the pools, root arches; three froglights in the mound and one on the arch |
| the Serpent Shrine | a stone serpent coiling one and a half turns up round the nest to seven blocks | its head bending over the den with a dripstone fang, vine-hung pillars, bamboo; froglight eyes |
| the Silk Hollow | a hollow stump nine blocks tall with a jagged crown, opening toward the den | egg sacs, a sagging silk canopy, cocooned prey, a skull; glow lichen round the stump |
| the Petal Bower | four cherry trunks leaning in to a blossom vault ten blocks up | a silk chrysalis over the den, spore blossoms, petals, a flower ring; end rods and lanterns |
| the Ram's Cairn | a nine-block cairn crowned by a ram's skull with curling bone horns | a henge with one lintel, boulders; candles at the cairn's foot |
| the Spore Ring | three giant toadstools 7-9 tall among five small ones | a toppled cap, small mushrooms; shroomlights and spore blossoms under the caps |

Lair pieces save their blueprint version (`look`) like the ruins.

## Previews (no client)

`python3 tools/art/specs/structures.py preview <dir> [filters]` renders every structure the GameTests built
(`run/structures/*.json`, `run/fauna_lairs/*.json`) as an isometric voxel image with the real block textures (vanilla
from `.ref/assets`, ours from resources; blockstate variants, model parents, rotations and overlays resolved), from two
angles by day and by night (night: a flood-fill block light like the game's, emissive faces full bright, a bloom), and
lays the gate vortex into the gate scenes from frames exported by the same Java code the renderer runs
(`run/structures/vortex_frames.json`; also `vortex_frames.png`, every rank and look straight on, and
`vortex_ranks_day.png`). Water is drawn as a faint surface so what is under it can be judged.

What the previews showed and what changed because of them: the first fissures all ran east-west and read as parallel
dashes (now oriented, joined cracks); the first spurs were stubby stacks (now leaning voxel fangs); the vortex arms were
three identical copies (now three uneven arms); brass everywhere read gaudy (now accents only); the ocean domes read as
dark blobs (now light glass, lit rings); the first monolith read as a lighthouse (now a black obelisk); the wyrm's ribs
formed a wall (spaced out).

## Tests (GameTests)

- `gates_vortex_footprint_all_ranks` (isolated): every rank on both axes on a meadow: the anchor carries the vortex, the
  opening grows with rank, spurs and fissures exist, the mouth is standing room, the footprint stays inside the build's
  chunks; a break turns the cores and the fissures red; removal leaves no core, frame or fissure, only the scar. Dumps
  E, C, S and Sovereign open and C and S broken.
- `gates_vortex_respects_builds` (isolated): the rank's footprint finds a chest the smaller one misses; a plank on the pad
  of a gate placed anyway is left alone.
- `gates_vortex_enters_from_anywhere` (isolated): an S-rank gate's top, side and anchor cores belong to it, the pad does
  not, and touching the top core enters the gate (a run starts).
- `gates_vortex_render_data_syncs` (isolated): bound to its gate (rank, axis, Red Gate), the update tag round-trips,
  about to break at one day and today, broken, spent, forming, no update when nothing changed, a showcase keeps its look.
- `gates_vortex_rebuilds_old_doorways` (isolated): build 1's doorway is recognised, rebuilt as a vortex, not twice.
- `gates_vortex_frames`: every rank and look emits whole quads within the budget, finite, v inside the atlas; exports frames.
- `ruins_redesign_build_and_contracts` (isolated): each kind built on ground made for it: machines, loot and exactly one
  outer cache, 8+ bright blocks, the vault's crown 16+ high and its sealed chamber unreachable except through the door
  (a flood fill from the forecourt), the monolith 34+ over its plaza with the hatch leading down, the lab's domes dry
  with the sea outside, the sky platform's spire; dumps each.
- `descent_well_new_look` (isolated): the shaft, lining and pool, the lift, the rim spot standing on the court, a lantern
  at each corner pylon where the landmark glow is drawn, 8+ great pillars 16+ high crowned with light, 40+ bright
  blocks, the terraces, a build beside the Well left alone; dumps it.
- Unchanged tests that still hold the contracts: `gates_overworld_structure` (updated for the wider opening),
  `gates_build_break_hold`, `gates_surface_respects_builds`, the story prologue, `above_ruin_layouts`,
  `above_ruins_generate_in_world`, `landmark_outer_caches`, `descent_well_builds`, `fauna_lair_styles_build`.

## Autopilot

`tools/autopilot/structures.txt`: the Well far and close, by day and night and down the shaft; gates of every rank from
afar by day (C, S and Sovereign by night too, C and S close); about to break, broken, calm, spent, a Red Gate and a
forming gate; the vault, the monolith and the sky platform built ahead (far, night, close, inside the vault); the real
vault, monolith and ocean lab where the seed placed them; all nine lairs (the bower and the spore ring by night).

## Performance

- Client: one gate draws about 3,000 quads a frame (the budget `GateVortex.maxSurfaceQuads` is tested), computed from
  precomputed sine tables with no per-vertex allocation; culled beyond 96-216 blocks; at most a few gates are in range.
  The hum checks loaded vortices every 10 ticks.
- Server: each anchor re-reads its gate every 40 ticks (staggered by position) and syncs only on change; old-gate
  upgrades are checked every 10 s (three block reads per placed gate). Placement is a one-time ~10-20k block reads and
  a few thousand writes inside the already-loaded 3x3 chunks.

## Known gaps

- Not seen in a real client yet: everything here is checked by GameTests and the offline previews; the autopilot script
  is for the integrator. The previews approximate the shader (no fog, simple light curve).
- The "additive" glow is translucent emissive layering (the house render type, proven in this mod); a true additive
  blend needs a custom pipeline that could not be validated without a client.
- Ruins and lairs whose generation was already under way when this lands keep their old built part (no new blueprint is
  welded on); fully generated ones keep their old look. New chunks get the new designs.
- The ocean lab and sky platform previews come from the test builds (flat sand, empty sky); their fit to real sea floors
  and skies is for the autopilot to show.

## Changes outside the listed paths (smallest possible)

- `above/AboveModule.init`: one line, `RuinBlocks.init(modBus)` (registers the ruin materials, tests and showcase).
- `descent/DescentModule.init`: one line, `WellTests.register()`.
- `gates/run/Interactions.structureAt`: uses `Overworld.REACH` and `Overworld.isPart` (rank-aware) instead of a radius of
  8 and the E-rank block list.
- `gates/GateService.placeSurface`: passes the gate's rank to `Overworld.untouched` (the placement check follows the
  footprint).
- `gates/GateTests.gates_overworld_structure`: checks the wider opening and a spur instead of the old post, and puts the
  shared test ground back as it found it.
- Module docs to fold this into: `gates.md` ("Surface, card, pillars, breaks"), `above.md` (ruin table),
  `descent.md` (the Well), `fauna.md` (lair table), `landmarks.md` (the Well's lantern pylons keep the glow positions).

## Decisions (for SPEC)

1. Surface gate: a vortex (ellipse radii by rank as in the table above) drawn by a block entity renderer on the anchor
   core; cores invisible; footprint = pad (rx + 3) + crater ring (+3.5); placement checks the rank's footprint.
2. Gate looks: forming, open, about to break (break within one play-day; 55%/100%), broken, calm (story), spent
   (cleared); Red Gates red-rimmed. Synced on the anchor block entity (schema 1), refreshed every 2 s.
3. Gate hum: two loops within ~50 blocks, 48-block linear falloff, pitch by rank.
4. Old gates are rebuilt in place when loaded; old Wells are rebuilt on start (`WellData.style` 2).
5. Ruin sizes: vault 23x24x23, monolith 17x46x17, ocean lab 31x22x31, sky platform 49x26x49. Ruin and lair pieces carry
   a blueprint version; legacy pieces never get the new blueprint in unbuilt chunks.
6. The Well: shaft radius 7, landmark radius 23, twelve pillars at radius 18, four lantern pylons at (+-8, +-8) with
   their light at y + 7.
7. New blocks: `gate_fissure` (rank 0-7, dir 0-3, light 7), `ruin_plating_cracked`, `ruin_plating_mossy`, `ruin_pillar`,
   `ruin_glyph` (light 6); block entity `awaken:gate_vortex`; sound `gate.vortex`.
