# VILLAGE module

AWAKEN's own hamlets and villages (Between the Gates W3, SPEC §28.6): planned in code on the settlement grid, painted
by the city's grammar with furnished interiors, and lived in by the people engine (W0's `Settlements`). Package
`dev.awaken.village` (worldgen in `village.world`, the planner and painter in `village.plan`, the notice board's screen in
`village.client`); shared lines in `src/main/merge/village/`. New worlds only: the built-in **Awakened Villages** pack
(feature flag `awaken:awakened_villages`) places them and switches vanilla's villages off.

## What's where

| Part | Classes | Notes |
|---|---|---|
| Where settlements are | `world.SettlementGrid`, `world.SettlementPlacement`, `world.FirstHamlet` | 640-block cells, one anchor each; the first hamlet found up the road at server start |
| Worldgen | `world.VillageStructure`, `world.VillagePiece`, `world.VillageMarker`, `world.VillageWorldgen`, `world.Plans`, `world.GenTerrain` | one painter piece, beard markers (houses and two past, fields, pens, the green, every street segment); the land's trees felled within 6 of the ground (`SettlementPaint.fell`); plans cached per server, made again from the anchor after a restart |
| The planner | `plan.VillagePlanner`, `plan.VillagePlan`, `plan.Names`, `plan.Terrain` | pure; reads the generator's estimate only |
| Its own painting | `plan.VillagePainter`, `plan.VillageBlocks` | streets, green, yards, fields, gardens, pens, pier, lamps, bell, forge, wheel, trees |
| Houses and rooms | `dev.awaken.city.SettlementPaint`, `PlanView`, `VillageStyles`, `CityInteriors` (city module) | the city's grammar in land and wealth styles; Carillon unchanged |
| People | `VillageRoster`, `VillageService` | the roster on W0's `RosterBuilder`; settlements start near players, sleep and wake with the engine |
| Trade, standing, board, inn | `Trade`, `Emeralds`, `Requests`, `Inn`, `VillageNet`, `client.NoticeBoardScreen` | emeralds through `EconomyHooks` |
| State and API | `VillageState` (`awaken:villages`), `api.Villages`, `VillagesAccess` | what W4 builds on |
| Content | `VillageContent` (the inn's key) | the village props (`carcass_pole`, `shepherd_crook`) are the citizens' |

Speech: `data/awaken/village/village_lines.json` (read by `citizens.Lines` under `village.`). Soundscape beds:
`assets/awaken/music_layers/village/` (`day`, `night`, `as/` for AmbientSounds players). Art:
`tools/art/specs/village_props.py` (the hunter's pole, the crook, the key).

## Commands (`/awaken village ...`, game masters)

- `list` — the settlements meant to be within 2,000 blocks and whether they're planned (name, kind, size) or have no site.
- `view <key|first|nearest|nearest_village> <hill|green|street|night|inn|smithy|mill|dock|field|pen|notice|bell|house1|house2>`
  — stand at a framing (the tour's).
- `start` — start the people of the nearest settlement now (they start by themselves within reach).
- `info` — the settlement you're in: plan, people, bodies, prosperity, mood, your standing, damage, notices.
- `trade <job>` — open that trade's goods in the settlement you're in; `board` — its notice board.
- `standing <points>` — set your standing there (tests, tours).
- `stats` — plans made and their cost.

## Tests (`village_`)

- `village_plan_deterministic` — the planner and painter are pure (the same plan and the same blocks twice, chunk by
  chunk), sizes within the spec, the inn, forge, tower and post where they belong, ≤ 6 lights a chunk; logs the cost.
  Studio: `village-dump.request` in the server folder writes dumps for `tools/city/render.py` (the village, the hamlet,
  the village with its roofs cut to show the rooms); `village-survey.request` plans the grid over the real overworld's
  noise for seed 26102610 (success rate, cost, the first hamlet's road search) and dumps the first few with their land.
- `village_day` (in `dev.awaken.citizens`) — a village's day on the engine: at work at ten, home on real routes for the
  midday meal, guests at the inn and lamps lit in the evening, asleep at midnight but for the Watch.
- `village_trade` — emeralds paid once per claim, prices by standing, standing from trade (four a day), a notice
  filled, a night at the inn (respawn there once).

Render a dump: `python3 -I tools/city/render.py run/village-dump.bin <out-dir> 4` or a close-up
`... run/village-rooms.bin out.png crop -30 -30 20 20 12 62`.

## Rules other modules keep

- Gates never open on a settlement's ground or within 64 of it (`Villages.reserved`, read by `gates/Director`); a Hearth
  can't be founded within 8 of one (`hearth/Embers`, `HearthService`).
- Damage is recorded with the block that stood there for W4's Rebuild: break a village's wall and the village remembers.
- A camp grown to raid (Living Wilds) hands a raid on a village to the village (`Troubles.raidComing`); a lair a Taken
  ended in stays empty for good (`Troubles.lairSealed`, read by fauna's `Lairs.wake`).

## Troubles and growth (Between the Gates W4, SPEC §28.7)

When a village needs you: something in the world troubles it (a lair, a camp, a gate, a storm, its damage), the call is
in the world (the alarm bell heard to 256, smoke, a silent bell, a runner if you're Known), someone asks (Help / Not now),
and it ends better with you or worse without you on its own clock. Never a dead end, never a named death, always paid.
World option `awaken:calm_villages` turns them off (festivals and growth go on).

| Part | Classes | Notes |
|---|---|---|
| The director, its rules, payouts, the Journal and Now rows, the API | `trouble.TroubleDirector`, `trouble.Plot`, `trouble.TroubleState` (`awaken:village_troubles`), `trouble.Runner` | ≤ 1 a village, ≤ 2 within 1,500 of a player, none in a world's first two hours or a Hearth |
| Taken | `trouble.Taken`, `trouble.Rescuee` (+ `client.RescueeRenderer`, `client.CarryArmPose`) | the alarm, the parent, the trail (Tracks glow), the nest, the carry, the reunion, the token |
| The Burning | `trouble.Burning`, `trouble.Char`, `trouble.Lasting` | the ladder, the raid's night (fire arrows, the bucket line), charred houses, survivors by the well, smoke |
| The Rebuild | `trouble.Rebuild` | three notices, scaffolding and masons over three play-days, your beam, the family back |
| The Bell | `trouble.BellTrouble`, `trouble.VillageBell` | silent at dusk, cast at the smith from bell bronze, hauled up and hung; the ring of light |
| Gone Missing | `trouble.Missing` | the search party, clues, the drop, home |
| The Grey Fields | `trouble.GreyFields` | close the gate in time; else grey fields, the Hollowed, the herb remedy |
| The Festival | `trouble.Festival`, `trouble.Race` | lanterns, bonfire, the jig, the dance, contests, the rooftop race, fireworks |
| Growth | `growth.Growth` | a plot fenced past a lane's end, a house raised, a family moving in |
| What they leave | `trouble.TrailMark`, `trouble.CarvedToken`, `trouble.Keepsake`, `trouble.TroubleContent` | drag marks, torn cloth, the token, keepsakes, the bell, the remedy, the game rule |
| Light on the client | `client.TroubleFx`, `trouble.TroubleNet` | rings of light, the trail's glow, festoons, the race's marks |

Lines: `data/awaken/village/village_lines.json` under `trouble` (`village.trouble.*`). Art: `tools/art/specs/village_troubles.py`.

### Commands (`/awaken village trouble ...`, game masters; tours and tests)

- `start <taken|burning|rebuild|bell|missing|grey_fields|festival>` — begin one at the village you're in or near (a den,
  a camp put there if the world has none); `info`; `help` (say Help); `end`; `miss` (its clock runs out now).
- `night` (Taken: in the nest now; Burning: the scouts), `raid`, `fire`, `cut [fraction]`, `carry`, `home`, `deliver`,
  `build <fraction>`, `bell`, `hang`, `ruin` (a Burning missed), `quiet on|off` (hold the director's own starts).
- `view <well|edge|scouts|trail|lair|nest|fight|roofs|line|ruin|smoke|scaffold|belfry|bell|green|dance>` — the tour's
  framings; each eye is moved as little as it takes to one with sky over it, room to stand, a clear sight of its mark
  and nothing close filling a side of the frame (never onto a roof).
- `site` — stands you past the village's edge on even, open ground facing out: a lair built in front of you
  (`/awaken fauna lair place <beast>`) is then in a Taken's reach (the W4 tour does this).
- `/awaken village grow [now]` — a plot fenced now; again: the masons on; `now`: the stage done at once.

### Tests

- `village_trouble_taken` — from the lair to the reunion: taken (away), the trail, the Journal only after Help, bound,
  cut free and carried (burdened), home, paid, the API's rescued, the lair sealed, back in the village's day.
- `village_burning_rebuild` — a missed Burning chars a built house into the damage record, the household camps by the
  well; the Rebuild's three notices, scaffolding, the house put back exactly, the camp struck.
- `village_director_caps` — none in a young world, none with calm villages on (a festival still comes), two near a
  player and one a village, the Journal only after Help.
- `village_deep_mine` (in `trouble.TroubleTests`) — a mining town over its mine on a test mountain: the Deep Mine
  started, Help and its Journal row, the nest woken (Hollows round the miners), cleared, the miners following up the
  workings while the roof comes down, home at the head and paid; a second town that nobody helps seals its shaft.

## Roads and towns (Between the Gates W7, SPEC §28.9)

The world joined up: roads between Carillon, the Wayfarer camp and the settlements, in each region's own blocks,
following the land, with bridges, waymarkers naming the next place, lanterns into each settlement; an inn about a day's
walk apart; walled towns (about one region in five); mining towns at a mountain's foot over their own mine, and the
Deep Mine; the Living Wilds caravan walking a real road; the Atlas showing the roads you've walked and the places
you've found. New worlds with the Awakened Villages pack only.

| Part | Classes | Notes |
|---|---|---|
| The network | `road.RoadNet`, `road.Highroads`, `road.FirstTown`, `road.RoadWorld`, `road.Road` | a hub per region (town, settlement near its heart, wayhouse, crossroads); highroads between neighbouring regions through the settlements on their way; Carillon's three ports; lanes to every other settlement; made once, planned ahead of players on a low-priority thread |
| The line | `road.RoadPlanner` | pure from the ends and the generator's estimate of the land; A* on a coarse lattice, cut corners, a profile that never climbs more than a block a point; bridges, boardwalks, causeways |
| Worldgen | `road.RoadStructure`, `road.RoadPlacement`, `road.RoadPiece` (`world.VillageWorldgen` registers them) | legs of ≤ 72 points starting on a lattice of every eighth chunk; thin-beard markers under the dry points; the piece takes no beard |
| Painting | `road.RoadPaint`, `road.RoadMarks`, `road.Waymarks` | through the settlement painter (fell, sweep, fill); the land's paving, half-step slabs, bridges, lantern posts; waymarkers with a hanging-sign arm per way, the cairn at the pass |
| Towns | `plan.TownPlanner`, `plan.TownPainter` | wall ring 64 out (76 where that fits under 25 homes) with towers and gates, the market square where two main streets cross, a lane grid, about 30-40 homes, the inn, hall, scribe, smithy, a bell tower, stalls |
| Wayhouses | `plan.WayhousePlanner` | an inn and stable on a crossroads at a region's heart, a horse paddock, a carter |
| Mining towns | `plan.MinePlanner`, `plan.MinePainter`, `world.MinePiece` | the shaft head with its winch, the assayer's store, two levels down to a lode, the cavern behind the face |
| The Deep Mine | `trouble.DeepMine` | the bell at the shaft head, the families at the mouth, the nest, the roof coming down behind, the miners brought up; or the shaft sealed |
| Caravans | `road.RoadAccess` (`core.api.RoadHooks`) | a stretch of a laid road for the World Pulse's caravan; trade and news when it comes in; W6's Tier IV call site (`RoadHooks.HearthRoads`) |
| The Atlas | `road.RoadAtlas` (`awaken:road_atlas`), `client.RoadAtlasClient` | stretches walked and places found per player; lines through `MapMarks.registerLines` |
| The studio | `road.RoadStudio`, `tools/city/roadmap.py` | `run/road-survey.request` ("seed radius") surveys the real overworld's roads, hubs and settlements with their cost; dumps towns, wayhouses, mines, the first town and legs for `tools/city/render.py` |

Lines: `village_lines.json` under `trouble.deep_mine`, `greet.assayer`, `calls.assayer`.

### Commands

- `/awaken village road info` — the road you're on (class, ends, point, flags) and the network's cost so far;
  `plan [radius]` — plan every road within a radius now and say what was laid; `camp` — stand on the road north out of
  the Wayfarer camp (else a port's road, else the nearest), facing along it; `walk <points>` — move that many points
  along the road you're on; `marker` — stand before the nearest waymarker; `watch` — stand ahead of the nearest
  caravan, facing it.
- `/awaken village view <key|first_town|nearest_town|nearest_mine|nearest_wayhouse|...> <name>` — W7's framings:
  `gate`, `gate_out`, `market`, `scribe`, `wall`, `pass` (the cairn, the town below), `head`, `shaft`, `level1`, `winze`,
  `face`, `cavern`, `assay`, `stable`, `marker`.
- `/awaken village trouble start deep_mine`, then `trouble mine <wake|clear|lead|fall|up>` — move the Deep Mine along.

### Tests

- `village_roads` — a road across a test valley's river: deterministic, bridged, no step over two, cuttings ≤ 7, legs
  within reach of their lattice starts, painted (the first leg) with a waymarker naming the next place.
- `village_town_plan` — a town fits the test valley (25-45 homes, 60-120 people, two gates or more, six towers, the
  inn, hall, scribe and smithy, three stalls and the 5x5 bell tower, plots inside the wall, fields outside, pure, ≤ 10
  lights a chunk); a wayhouse (inn, stable, horse paddock, three exits); a mining town on a test mountain (the assay, the
  levels, the workings inside the square, three miners or more).
- `village_deep_mine` — above.
