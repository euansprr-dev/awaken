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
- `view <well|edge|trail|lair|nest|roofs|line|ruin|scaffold|belfry|bell|green|dance>` — the tour's framings.
- `/awaken village grow [now]` — a plot fenced now; again: the masons on; `now`: the stage done at once.

### Tests

- `village_trouble_taken` — from the lair to the reunion: taken (away), the trail, the Journal only after Help, bound,
  cut free and carried (burdened), home, paid, the API's rescued, the lair sealed, back in the village's day.
- `village_burning_rebuild` — a missed Burning chars a built house into the damage record, the household camps by the
  well; the Rebuild's three notices, scaffolding, the house put back exactly, the camp struck.
- `village_director_caps` — none in a young world, none with calm villages on (a festival still comes), two near a
  player and one a village, the Journal only after Help.
