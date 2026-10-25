# Milestone 4 · the Walls (Walls builder)

Branch `carillon/m4-walls` (from carillon/main 1381b043, merged up to b27c7613), worktree
`/Volumes/MEDIA/Awaken/.claude/worktrees/walls-74d4bc`. Brief from the Carillon orchestrator, Euan's words: "the walls
are really, really bad. They're super flat… it should put the same effort as the entire other plans… make sure the walls
are also kind of climbable and have their own kind of things there… on par with the city." Date 2026-10-26, Apple M4,
overnight (Euan asleep). A Sonnet subagent drew the wall kit's art from my brief (briefs/M4-walls-sonnet-1.md); the
painters, the citizens' wall walk, the tests and the look are mine.

The plan is unchanged (hash 575dde8a…): every rule lives in the painters, so routes.json, the golden runs and the
opening's stage are untouched. Generator changes never touch a built city: the new wall is for new worlds.

## What changed

**One cross-section swept round the ring (`CityWall`).** Every column works itself out from the wall polygon's own
geometry: which of the 24 sides it belongs to, how deep it stands in the wall, and where along the side, counted in face
cells (so a rhythm of 8 is eight cells on a diagonal side too). Chunk-alone painting holds as before.

- *Outside, from the foot:* a talus in three steps of rough stone (caps are stairs leaning on the wall), buttresses three
  wide every 8 with set-offs at rows 13 and 33 (each a step), string courses at rows 15 and 27 that wrap round the
  buttresses (ledges, joined by shape as vanilla would), two-block arrow loops (slits, crosses every 16) at 18-19 and
  28-29, putlog holes at 12 and 24, drain spouts with moss streaks down the face, and a machicolated parapet two deep:
  corbels in two tiers, slots between them, a breastwork one high, merlons three high with loops, crenel sills that are
  walls (a "no"). Six sides (1, 3, 10, 11, 19, 21) carry a roofed wall walk instead: a timber hoarding on beams and
  struts, a plank floor with grates, a front with shuttered openings, one slate roof from the hoarding to the inner
  parapet, a plank ceiling on tie beams inside with lanterns.
- *Stone:* stone bricks and the kit's ashlar, cracked and andesite in places; moss by height and aspect (wet at the
  foot, heavier on north faces and the sheltered inner face, the Garden's and the Quay's wetter, the Terrace's cleaner);
  repair patches in tuff, rubble or fresh ashlar (deepslate by the Works); andesite courses over the talus and under the
  corbels; soot high on the Works' stretch.
- *Inside, the town's side:* an arcade of bays 7 apart (3 deep, open to row 5 under pointed heads) holding the wall's
  life: stores of crates, sacks and casks, a stable with hay (soft landings), workshops, stalls with counters and wares,
  a smithy with an anvil and a lantern, a wayside shrine with candles and the city's shield, woodpiles, benches with
  planters, laundry across the bay. Awnings at row 3 over stalls, workshops and smithies (holds and soft landings); wall
  lanterns on the piers; a course at row 9; above it recessed blind panels to row 31 between pilasters, with putlog
  holes and ivy climbing in them; long district banners on every third pilaster and flagpoles with banners at row 13 on
  every fourth (swings over the ring); a drainpipe up every third pier and near each end of every stretch (the climb
  lines to the walk); a corbel table and a band under an inner parapet two deep (stone walls, a "no"), lanterns on it
  every 16. The west side carries a grand stair up the inside of the wall under arched openings instead of bays.
- *The walk:* paved with dressed edges, a worn middle and moss by the parapets, braziers (the kit's light) and crates of
  bolts by the outer parapet.
- *Scaffolding* over a stretch under repair on sides 2, 9 and 18: poles, decks on putlog beams, a ladder through the
  decks, a hoist and rope, fresh stone behind.
- *Over the river (`CityWall.waterGate`):* a deep arch with a dressed rim, the water portcullis half lowered, battlements
  and the inner parapet with lanterns.

**Towers by district (`CityTowers`).** Each tower takes the district behind it:
- Bellwatch: round drums with timber hoardings under tall slate witch-hats.
- The Terrace: square with calcite-toned quoins (polished diorite), corner bartizans corbelled out with verdigris caps,
  and a slate pyramid.
- The Garden Ward: octagons with verdigris spires.
- The Works: brick bands and brick quoins, battlements two deep round an open deck with a beacon brazier, and a
  clay-roofed watch room inside.
- The Quay's water gates: octagons under copper with a lantern for the boats.

Every tower: a battered base, the wall's courses at 15 and 27 round it, loops staggered on each storey, lit arched
windows in the guard room, two long district banners on its field face, a flag on a mast with a flagpole at the apex.
The wall walk passes through each tower's hall at its own level. Every wall and gate tower has a door on open ground and
a newel stair up to that hall; the newel stands clear of the walk's middle line. The curtain runs on into the box's
corners beside a drum, so the walk has no holes.

**Gatehouses.** The gate towers are D-shaped drums under the gate district's roof, 4 taller than the plan's blockout so
their gallery clears the walk's hall. The passage has:
- a pointed arch with a dressed rim, and the kit portcullis half raised behind it;
- murder holes in the vault, and wall lanterns down the passage with one hung in the middle;
- the gate's leaves folded back.

Outside: the city's arms and the district's, banners either side, and a machicolated box over the arch. The room over the
gate carries the walk through. On top: battlements round a walk, braziers at the corners, and the gate hall's slate hip
roof with a gilded finial. No plan lamp stands in a passage (one stood in the High Gate's).

**The Watch's night round on the walk.** `citizens.json` gains `wallWalk`: a chain of nodes along the walk's middle line
round the ring (broken at the Runners' Roost), and each wall and gate tower's door joined to the walk by its stair (move
9, hidden: the ledger climbs it, a body goes in at the door and comes out on the walk). A route changes between the
streets and the walk at most once (the same rule in `City.row` and `StreetNet.bake`), so nobody crosses the city over the
wall. The first four night watchmen walk four wall rounds of eight stands each, out and back. Wall nodes are never a
nearest node and never an event's random junction. Old worlds keep their old walls; the round assumes the new one.

**The wall kit (6 blocks, `city.kit.WallBlocks`/`WallKit`, art `tools/art/specs/city_wall.py`):**
- arrow loop: slit, cross, oillet and window; in six stones, seamless by vanilla texture id; lower, upper and single parts.
- wall ivy: climbable; three densities and three tones.
- portcullis: top, middle and a spiked bottom.
- brazier: on a tripod or a wall bracket, with an animated emissive flame. The kit's one light source, level 14.
- wall spout: a stone gutter with a grotesque.
- wall shield: the city's arms and seven districts' plus the Association's.

No block entities, no loot.

## Tests (GameTests, one per mechanism; `-Ptests=city_wall_`)
- `city_wall_grid`: every hold on the wall's faces below the walk sits in an odd row (9,841 holds), the outer courses
  (896 cells) and the inner (529) are whole along every clear stretch, and 23 of 24 sides have a climb line from the
  street to the walk (the west side has the grand stair instead).
- `city_wall_walk`: from one cell of the walk you walk to all 24 sides at the walk's level (through towers, gatehouses
  and over the water gates); a stair from the ground reaches the walk in all 27 wall and gate towers; every tower door
  node of the Watch's graph stands before a painted door, and every stretch of every round is open walk.
- `city_wall_gates`: every passage is six wide and five high, clear, end to end.
- Held: every `city_` test (the grid, catchability, chunk-alone, relief…) and every `citizens_` test (day: 2,238
  segments, 0 missed, 0 without a route; stations: 937 places, 0 problems). `tools/city/citizens.py check` passes.

## Numbers
- Painting: the studio dump paints all 1,388 chunks in 1.1-1.3 s (1.34 s before). The painted city is 3.87 M blocks,
  +32,000 (+0.8%): the wall's relief mostly replaces solid stone.
- Light: braziers ~45, lanterns ~390 (245 before). Every chunk at most 5 light sources; the wall's average 1.8.
- The kit: 2,828 arrow loops, 2,840 ivy, 344 portcullis cells, 86 spouts, 14 shields.

## Shots
Three light tours of `tools/autopilot/carillon-m4walls.txt` (B3a's world, seed and cameras; the last two on Vulkan). In
`docs/carillon/milestones/m4walls/`:
- before (B3a) | after pairs: `pair_sky_nw`, `pair_sky_n`, `pair_sky_w`, `pair_sky_top`, `pair_sky_nw_low`,
  `pair_gate_yard`, `pair_wallring`, `pair_facade_wallring`, `pair_roost_start`;
- the new framings: `walls_high_gate` (and `_dusk`), `walls_out_w`, `walls_out_s`, `walls_inner`, `walls_inner_high`,
  `walls_wall_walk`, `walls_walk_roofed_night`, `walls_scaffold`, `walls_passage`, `walls_tower`, `walls_watergate`;
- `sheet_walls.jpg`, every shot of the last tour; `kit_sheet.png` and `kit_mock.png`, the wall kit.

## Where it still falls short
- Ivy on the outer north faces is framed by the buttresses and cut by the courses, so a big patch still reads
  rectangular from the fields; the inner panels' ivy reads better.
- Close under the inner face of a slanted side, the panels' heads, the corbel table and the band step with the raster and
  read heavy.
- The Watch's round wasn't seen in a shot (the tours fly as spectators at fixed hours); `citizens_day` walks it.

## CI
GameTests on every push of `carillon/m4-walls`: 40217807842 (first pass) green, 40221579843 (the Watch's round, second
pass) green, 40223015764 (light budget, merged carillon/main) see the hand-back.

## Decisions (SPEC §16.2, "Decided in M4 Walls")
The cross-section and its rows, the rhythm in face cells, the roofed sides, the climb lines, the towers by district, gate
towers +4, the water gates without stairs, the wall walk in the citizens' graph with the one-crossing rule, the kit.

## For later
- The bay contents and the walk's crates are painted, not props: citizens don't use them as places.
- Old worlds keep the old wall (generator changes never touch a built city): Euan's existing worlds need a new world to
  see it.
