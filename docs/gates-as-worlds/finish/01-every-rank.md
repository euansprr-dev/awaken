# Brief 01: every rank has its dungeons

**Why.** Euan, 11 Nov: "Why does it only cover two ranks?... all ranks have all their dungeons." Today each of the seventeen rolled
kinds lists the two ranks where its theme first appears, two themes have no kind at all, and nothing in the generator reads the
rank: an S-rank run is cut exactly like an E-rank one, with stronger monsters in it. He also said, of gates in general: "when I go
in a Gate, I want to gain a lot of experience and actually like massively level up", and "a ton of different variations, so I never
feel like I'm repeating the same gates".

**Done looks like.** A gate of any theme at any rank from E to S that is open to that theme can be a rolled dungeon. A higher rank
is a longer, taller, harder run, visibly so. The Lantern Night Market and the Sky Archipelago each have a kind of their own. Every
kind passes the bench at every rank it is open to.

## Read first

- `docs/SPEC.md` §50.9 whole (The Run: legs, packs, traps, the fall catch, "No two alike", "Depth", "Air").
- `src/main/java/dev/awaken/gates/realms/kit/crawl/GeneratedCrawl.java`: `generate()` lays a whole run out from the seed; `Style`
  is a kind's own share of shapes; `Trim` its small pieces; `airOf` its air. Size is rolled near the top of `generate()`
  (`roll < 30 ? SHORT : roll < 83 ? STANDARD : GREAT`).
- `kit/crawl/CrawlLand.java`: the parts (`Cave` and its `Form`s, `Arena` and its `Plan`s, `Tunnel`, `Shaft`, `Chasm` and its `Kind`s).
- Two skins end to end, one built and one dug: `realms/land/Catacomb.java` and `realms/land/GreenDark.java`.
- `gates/content/Theme.java`: `minRank` is the rank a theme opens at; `bossKey` its Lord.
- `realms/Land.java` (`Ctx` carries the rank), `realms/RealmLive.java` (how dens, ambushes, Wardens and the Lord wake; `pool` and
  `units` are the pay), `realms/RealmTraps.java`.
- `tools/realms/kinds_air.py` (one table makes each kind's biome, Lens look and small-life list) and
  `tools/autopilot/realm-rolled-tours.py` (writes each kind's tour).

## Part 1: rank changes the run

Give `GeneratedCrawl` the rank (it is in `Land.Ctx`) and let it decide these. The seed still decides everything else, and the
same seed at the same rank must always give the same run.

| | E | D | C | B | A | S |
|---|---|---|---|---|---|---|
| Minutes it should take (the plan) | 15-25 | 20-30 | 25-40 | 30-50 | 40-60 | 45-75 |
| Short / standard / great / grand, in 100 | 30/53/17/0 | 22/55/23/0 | 15/55/30/0 | 10/45/35/10 | 5/40/35/20 | 0/35/35/30 |
| Wardens (short, standard, great, grand) | 0,1,2,- | 0,1,2,- | 1,1,2,- | 1,2,2,3 | 1,2,3,3 | -,2,3,4 |
| Crossings and climbs at least (standard) | 1 | 1 | 2 | 2 | 3 | 3 |
| Traps on a trap leg | as now | as now | one more press or spitter | two kinds on one leg | two kinds, shorter gaps between beats | three kinds |
| Ambush waves | 1 | 1 | 2 | 2 | 3 | 3 |

- **Grand** is new: three stretches instead of two, a Warden closing each of the first two (and a third or fourth where the table
  says), at least three movement legs, two branches off the way where there is room, twenty to twenty-four legs. It must still fit
  the dimension: 384 tall, and a great delve already falls about 110. Make a grand run turn (delve, then sprawl, then climb or
  delve again) and assert in `generate()` that no floor goes under y 40 or over y 300; if a roll would, re-roll that stretch's shape.
- **Wardens short of the table** on a short run are fine where the table says 0; never more Wardens than there are stretches to close.
- **Harder crossings** from C: longer gulfs (more stands), more of the stands thin or crumbling, and the kinds that ask more of you
  (rafters, hung platforms, the falling deck) more often than pillars and the ford. Every jump stays a sprint over one to three
  cells, level or down. Do **not** use movement powers (rope, wall-run, wings) to cross: what a player has unlocked at a rank is not
  yours to assume. If you believe a rank safely has a move (read SPEC §63, The Way Up), write it under "For Euan to decide".
- **Packs**: read how a den's pack is sized today (the skins' herd lists and `RealmLive`'s den and ambush code) and how the old gates
  grow companies with rank (`gates/run/Rooms`, `Encounters`). A Realm pack at rank R should be the size and mix the old gates'
  room company would be at R, no smaller. Elites and the gate monsters' bodies come with the bestiary; do not invent stats.
- **Pay**: `RealmLive.pool` and `units` must still pay a whole gate's worth (SPEC §50.3) however many legs a grand run has. Show the
  arithmetic for one short E run and one grand S run in your hand-back.
- **The Lord** is the gate plan's own (the theme's `bossKey`); his hall must fit him at every rank. Check each theme's Lord against
  the six `Arena.Plan`s for room (the old gates' Lord room is radius 22, 17 high): `sky_leviathan` and `gearwright_colossus` are big.
- Put the rank in the bench's `route:` line (`rolled great delve, rank B`) so a report says what it checked.

## Part 2: open every kind upward

Each rolled kind's blueprint (`src/main/resources/data/awaken/realms/<kind>.json`) lists every rank from its theme's `minRank` to S:
E themes `["E","D","C","B","A","S"]`, D themes from D, C themes from C, the Unlit Stair `["A","S"]`. Not Sovereign (story gates
stay on built rooms). The Broodwarren, The Hour Crypt and Amber Reach are hand-built: leave their ranks alone.

## Part 3: two new kinds

Both follow the seventeen exactly: a skin class in `realms/land/` extending `GeneratedCrawl`, a blueprint with `"ready"` left out,
a row in `kinds_air.py`, a tour from `realm-rolled-tours.py`, its line in `LandPrograms` and in `RealmTests.CRAWLS`, lang in
`src/main/merge/gates/`. Use each theme's existing blocks (`gates/world/MarketDress.java`, `SkyDress.java`, `gates/block/DepthBlocks`,
`ThemeBlocks`) and its existing monsters and Lord. Obey SPEC §50.9 "A wall is one thing with things in it".

- **The Under-Market** (Lantern Night Market, from D; Lord `lantern_broker`). The cellars and canals under the night market. Built,
  not dug: brick vaults, stall fronts shuttered along the ways, paper screens, lanterns hung in rows so every room is pools of warm
  light with dark between. Its share: halls, piers, a cross; gulfs are canals crossed on moored barges (the ford and the pillars),
  hung platforms and a broken bridge; square stairwells. Air: warm, lantern-coloured, moths, smoke off the lamps. Its own trick, if
  the theme's blocks allow it cheaply: the lanterns of a trap leg go out one row at a time as the trap beats.
- **The Hollow Isle** (Sky Archipelago, from B; Lord `sky_leviathan`). A run cut through one great floating rock. Dug, wind-worn.
  What makes it a sky dungeon: **its gulfs and shafts break out through the rock's side**, so you cross with open sky and cloud
  on one hand and the rock on the other. Build that as a new switch on `Chasm` and `Shaft` (one long wall, or one face of a shaft,
  cut clean away to the edge of the rock; the generator must place such a part at the rock's edge and keep the rock's own bounds
  from closing it). The fall catch (`Land.Course`) already takes back whoever falls. Air: wind, cloud drifting through the open
  sides, bright cold light; no fog indoors beyond a haze. Its Lord's hall is the biggest rotunda the generator makes, open to the
  sky above if the Leviathan needs the room.

## Checks

- `tools/check-ci.sh compile`.
- `tools/check-ci.sh bench awaken:<kind> <seeds>` for every kind at seeds 1 and 2 and its tour's seed. The bench must be able to
  take a rank (add the argument if it cannot; default the tour's). Run each kind at its lowest and its highest rank, and the grand
  size at least once per kind family (built, dug). Every one: `floats: 0`, `reach: all`.
- Extend `realm_generated_crawls_play` so the wide sweep (`AWAKEN_CRAWL_SEEDS=8`) covers every rank and asserts a grand run was
  rolled; the light pass that runs in the suite must stay at about 34 dungeons (rotate ranks across kinds, do not multiply).
- One real-game walk each of the two new kinds, and one walk of a grand S-rank run of an old kind, read shot by shot.

## Yours

`kit/crawl/GeneratedCrawl.java` (not `finish()`, `detail()`, `wells()` or `airOf()`: brief 04 and the gates session own those),
`kit/crawl/CrawlLand.java`, the two new skins, every rolled blueprint's `ranks`, `kinds_air.py` and what it writes, the tours
script, `RealmTests.CRAWLS` and the crawl test, `bench/Bench.java` for the rank. One line each in `LandPrograms`,
`dimension/realms.json` (the script writes it), `looks_gates.json` (the script writes it). SPEC §50.9, new paragraphs "Rank" and
the two kinds added to its list.

## Traps

- The suite tries seeds 1 and 2 for every kind. A change to how a run is laid out changes every kind at once: bench several.
- A run's layout must not depend on the kind. Rank is allowed to change it; the kind is not.
- `feet` is a room's lowest floor. A jump is only tried by the walk check where a step fails: put a pool or a real drop under a
  crossing, never something a walker can step down into.
- The Lord's hall and a Warden's room stay level to five sixths of the way out. The Lords were built for a level floor.
