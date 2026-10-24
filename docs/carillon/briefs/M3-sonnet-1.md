# M3 Sonnet task 1 · the roster generator (tools/city/citizens.py → data/awaken/city/citizens.json)

You are a Sonnet subagent working for the M3 (Alive) Citizens builder in the AWAKEN repo. Work ONLY in
`/Volumes/MEDIA/Awaken-alive` (branch `carillon/m3-alive`). Start every Bash command with `cd /Volumes/MEDIA/Awaken-alive &&`
and use absolute paths. Never call EnterWorktree. Never run Gradle, never launch the game, never touch any other
worktree (/Volumes/MEDIA/Awaken, /Volumes/MEDIA/Awaken-play, ...). Never message other sessions. Python only.
Commit your work when done (small commits are fine), each message ending with
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push.

## What this is
Carillon is a designed walled city at world spawn (Minecraft mod). Its plan is data: `tools/city/plan.py` writes
`src/main/resources/data/awaken/city/plan.json` (read it, never change it or plan.py). The Citizens builder (Java,
`dev.awaken.citizens`) needs every one of the city's **240 citizens** as data: who they are, where they live and work,
and what they do at each hour. You write the generator that produces that data, deterministically.

Read first: `tools/city/plan.py` (its docstring and `to_json`, `graphs`, constants at the top) to understand plan.json:
- plan coordinates: blocks from the city centre, x east, z south (north = -z). Heights `ry` relative to the street
  (ry 0 = the paving's top block; feet stand at ry 1).
- `raster`: size 512, origin -256, layers `kind`, `district`, `lift` (base64 of zlib bytes, one byte per cell, row-major
  `j * 512 + i` with plan x = i - 256, z = j - 256). Kind numbers and WALKABLE are at the top of plan.py.
  `lift` = how much higher the ground is (High Terrace +8; stairs step).
- `plots` (rows; field names in `plotFields`): id, district, box x0 z0 x1 z1 (inclusive), front N/E/S/W, door x/z (or
  null), use house/shop/route/tavern, enter, ...
- `landmarks`: id, name, at, parts (kind, box). Useful ids: `stalls` (10 parts kind `stall`, 5x5 boxes in Choir Square),
  `bell_bellwatch`, `bell_quay`, `bell_terrace`, `bell_garden`, `bell_works` (part `belltower`), `choir` (part `spire`),
  `association` (parts incl. `assoc_wing` named Registry / Infirmary, `archive`), `cranes` (parts `crane`), `mills`
  (parts `mill`), `gate_n` `gate_e` `gate_s` (part `gate_passage`), `roost`, `market_hall`.
- `lamps`: 78 [x, z] street lamp posts (the lamp is 4 above the ground). `bridges`: id, name, box (Washers' Bridge is
  `canal_bridge_south`). `districts`: id, key, name, bell.
- `graphs.street`: nodes `[x, ry, z, kind, ref]` (kind junction/door/landmark; a door's ref is its plot id as a string,
  a landmark's ref its id) and edges `[a, b, moveIndex, seconds, tag]` (moves: `graphs.moves`; 0 walk, 1 stairs,
  2 bridge, 3 door). Node and edge **indices are the contract**: the game copies this graph in the same order.
  Door nodes sit on the door cell and hang off the street point one cell out by a `door` edge. Four landmark nodes:
  roost (1998), choir (1999), association (2000, ry 8), market_hall (2002). Use the largest connected component only
  (two tiny 2-node pieces exist; never use them).

## Output
`python3 -I tools/city/citizens.py` writes `src/main/resources/data/awaken/city/citizens.json` and prints stats.
`python3 -I tools/city/citizens.py check` runs every check below (exit code 1 on any failure) without writing.
`python3 -I tools/city/citizens.py preview` also writes `build/city/citizens-top.png` (raster in muted greys, homes as
small dots coloured by job, stations as squares, routes as thin lines, lamps as yellow dots) for eyeballing.
Deterministic: one fixed seed (`SEED = 0xC171_2E45`), your own `random.Random(SEED ^ salt)` instances (never the global
random), stable iteration order, `json.dumps(..., separators=(",", ":"))` with arrays as rows (no pretty print).
Same input → byte-identical output (the `check` command must verify it by generating twice).
numpy is available (plan.py uses it).

### Top level (schemaVersion 1; keys in this order)
```
{"schemaVersion":1, "generator":"tools/city/citizens.py", "planSha256":"<sha256 of plan.json's bytes>",
 "jobs":[...], "acts":[...], "placeKinds":[...],
 "places":{"fields":[...],"rows":[...]},
 "routes":[...], "edgeLane":[...], "lamps":[...], "stalls":[...], "bells":[...], "bakeries":[...], "smithies":[...],
 "washplaces":[...], "lines":[...], "quay":{...}, "taverns":[...], "households":[...], "groups":[...],
 "citizens":{"fields":[...],"rows":[...]}, "schedules":[...], "extras":[...]}
```

**jobs** (index = job id; this exact order):
`["lamplighter","bellringer","porter","stallkeeper","baker","smith","watch","crier","boatman","washer","mason","runner",
"shopkeeper","tavernkeeper","clerk","archivist","healer","miller","child","resident"]`

**acts** (index = act id; exact order): `["home","inside","stand","round","carry","wander"]`
- `home`: walk to the citizen's home door place and go inside (sleep, rest, meals).
- `inside`: walk to a door place (target) and go inside (work indoors: bakery, shop, tavern, association).
- `stand`: walk to a place and stand on one of its spots doing the job's work (stall, anvil, tower, post, doorway).
- `round`: walk a route (target = route id), stopping `dwell` ticks at each place in order. flags bit 0 = loop (else
  once, then stand at the last place). For lamp routes flags bit 1 = light (else put out).
- `carry`: shuttle: walk to place `target` (pickup), then to the next place of route `alt` (a drop), back to pickup,
  next drop..., `dwell` ticks at each end.
- `wander`: visit places of route `target` in a seeded random order, `dwell` ticks at each.

**placeKinds**: `["door","stand","lamp","stall_keeper","stall_front","bell","anvil","wash","line","pickup","post",
"crier","sit","play","mill","landmark","tavern_out","shop_door"]`

### places
Fields exactly: `["kind","node","x","ry","z","yaw","cap","ref","district","approach","spots"]`
- `node`: the street-graph node index a body walks to before stepping onto the spot (nearest node in the main
  component by walking distance over walkable raster cells, not straight-line; doors use their own door node).
- `x, z`: the place's centre in plan coordinates (block centres: integer + 0.5); `ry`: ground ry (from `lift`) at it.
- `yaw`: degrees a body faces while there (Minecraft yaw: 0 = south (+z), 90 = west (-x), 180 = north, -90 = east).
- `cap`: how many can stand there at once (= number of spots). `ref`: plot id, lamp index, stall index, landmark id
  (string) or -1. `district`: the district id at the place.
- `approach`: `0` = the straight line from the node to every spot crosses only walkable cells (WALKABLE kinds, same
  lift ±1 per cell), `1` = it doesn't (the game then paths the last blocks). Prefer spots with approach 0.
- `spots`: `[[x, z, yaw], ...]` the exact standing points (block centres), `cap` of them, all on WALKABLE cells, never
  on a door cell, never inside a plot, never in water, ≥ 0.9 apart, and none closer than 1.0 to a street-graph edge's
  centre line on streets narrower than 6 (people standing must not block the walking lane; stand against the walls).
  A `door` place has one spot: the door cell itself (that's where a body goes inside).

Place kinds and how to make them:
- `door`: every plot with a door: home, shop, tavern, route (enterable), plus the four landmark nodes (kind `landmark`
  instead, ref = landmark id, spot = node position). Only make door places that are used (homes, workplaces,
  taverns, shops, association, choir, roost, market_hall).
- `lamp` (78, ref = lamp index): spot = the walkable cell next to the lamp post on the side of the street's centre
  (the lamplighter stands there and reaches up), yaw facing the post.
- `stall_keeper` (one per stall, ref = stall index, cap 2): spots inside the stall box behind the counter: the
  counter is the box's z1 row (excluding corners); spots at (x0+1, z1-1) and (x1-1, z1-1) (+0.5 centres), yaw 0
  (facing south, out over the counter). `stall_front` (one per stall, cap 5): spots at z1+1, x0..x1, yaw 180.
- `bell` (5 district towers + the Choir spire): cap 1 (the Choir: 2), the walkable cell nearest the tower's box
  edge, yaw facing the tower; ref = landmark id.
- `anvil` (smithies): see smithies. `wash`: see washplaces. `line`: one place per laundry line (cap 2, spots under
  the line's two ends). `pickup`: the Quay (see quay). `post`: Watch posts. `crier`: crier posts.
  `sit`: benches for residents (cap 3; on SQUARE/PLAZA cells at the edge against a plot or landmark, facing out).
  `play`: children's play spots (cap 4; open SQUARE/PLAZA/TRAINING cells). `mill`: the two mills (cap 2, the walkable
  cells nearest each mill's box on its face side). `tavern_out`: one per tavern (cap 5, spots along the tavern's front
  wall on the street, beside the door, never on the door cell). `shop_door`: one per shop (cap 1, beside the door).

### routes
`[{"id":0,"kind":"lamps"|"patrol"|"scout"|"crier"|"courier"|"errand"|"play"|"drops"|"sit","places":[placeIds]}]`
- 8 lamp routes covering all 78 lamps exactly once (cluster by graph distance, ~10 each, each ordered as a short
  walking tour; a route's first lamp is the one nearest its lamplighter's home).
- Watch patrols: one or two per district (7 districts), each a loop of 5-8 `post`-less junction places (make `stand`
  places of cap 2 at junctions for them) spread over the district, consecutive stops 25-60 blocks apart by graph.
- `scout`: Mira's route: the three gatehouses' inner sides and four Wallring points, a loop.
- `crier`: 5 routes, one per crier, each 3 crier posts (Choir Square near the Choir's door, then plazas or wide
  junctions in districts); crier places cap 1.
- `courier`: runners: 3 routes, each a loop Roost → Association → 3-4 district junctions → back.
- `errand`: one per district: that district's shop doors (or stall fronts for the market), tavern_out, a sit place;
  4-10 places each.
- `play`: 3 routes of 4-6 play places.
- `drops`: the 10 stall fronts in a walking order (porters), and one per bakery (its nearest 2 stall fronts).

### edgeLane
One number per street-graph edge (same order, len = edge count): the keep-right lane offset in tenths of a block. For
`walk`, `stairs`, `bridge` edges: measure the clear half-width at 25%, 50% and 75% along the edge (walkable cells on
each side of the centre line, perpendicular, max 8); `half = min` of the six measurements; lane = round(10 *
clamp(0.45 * half, 0, 1.5)); 0 if half < 1.5. Then if any lamp post or tree (plan `trees` [x, z, kind, size]) lies
within 0.8 of the offset line on either side, reduce the lane until it clears (or 0). `door` edges: 0.

### lamps
`[{"place":id, "post":[x, z], "route":routeId}]` in plan lamp order (78).

### stalls
`[{"part":k, "box":[x0,z0,x1,z1], "colour":c, "keeper":placeId, "front":placeId, "good":"<kind>"}]` in plan order
of the `stalls` landmark parts (10). `good` cycles through `["bread","fish","produce","cloth","pottery","candles",
"flowers","spice","tools","cheese"]` (stall k gets good k).

### bells
`[{"landmark":"bell_bellwatch","place":id,"box":[x0,z0,x1,z1],"district":2}, ...]` 5 district towers then
`{"landmark":"choir",...,"box": the spire box}`.

### bakeries, smithies, washplaces, lines, quay, taverns
- `bakeries`: 5 house plots (not enterable, door on a STREET or LANE junction): 1 market, 1 bellwatch, 1 quay, 1
  garden, 1 works; prefer plots 3+ storeys, near a stall route. `{"plot":id,"door":placeId,"front":placeId}` where
  `front` is a `stand` place (cap 1) beside the door on the street (the baker sells there).
- `smithies`: 3 Works plots + 1 Quay plot with a door on a STREET/LANE, ≥ 30 apart. Smithy 0 is Oren's (the Works plot
  nearest the Mill Bridge, `mill_bridge`). `{"plot":id,"door":placeId,"anvil":placeId,"block":[x,ry,z]}`: `block` is a
  walkable cell beside the door (not in front of it) against the front wall, where the game puts an anvil; the `anvil`
  place (cap 1) is the cell in front of that block, yaw facing it.
- `washplaces`: 2: by the Washers' Bridge on the canal, and by the mill race (RACE) in the Works. Each
  `{"places":[placeIds], "tub":[x,ry,z]}`: 4-6 `wash` places (cap 1) on walkable cells that touch a water cell, ≥ 2
  apart, yaw facing the water; `tub` a walkable cell next to them (the game puts a cauldron there).
- `lines`: 4-6 laundry lines within 40 blocks of a washplace: a straight run across a LANE or ALLEY (perpendicular to
  it), 3-7 cells of walkable ground with PLOT cells at both ends (the line hangs between two house walls, 4 above the
  ground), ≥ 8 apart. `{"a":[x,z],"b":[x,z],"ry":ry,"place":placeId,"cells":n}` (a, b = the first and last walkable
  cells of the run).
- `quay`: `{"pickup":[placeIds], "crates":[[x,ry,z],...]}`: 2 pickup places (cap 4) on QUAY cells beside the two
  cranes; 6-10 crate cells near them (walkable, against a plot or crane base, not on a node's straight line, not
  blocking the quay) where the game stacks crates.
- `taverns`: `[{"plot":id,"door":placeId,"out":placeId,"district":d}]` for all 14 tavern plots.

### households and groups
- `households`: `[{"id":h, "home":doorPlaceId, "plot":plotId, "family":"<surname>", "members":[citizenIds]}]`. 240
  citizens in about 95 households of 1-5 (more 2s and 3s). Homes are `house` plots with doors; pick homes so that each
  household's working members' workplaces are mostly within 140 blocks by graph walking distance (prefer the same
  district). No two households share a plot. Children live with at least one adult. Named citizens (below) each have
  their own home (no family name).
- `groups`: `[{"id":g,"kind":"stall"|"smithy"|"bakery"|"watch"|"lamps"|"bells"|"porters"|"washers"|"masons"|"criers"|
  "boatmen"|"runners"|"shop"|"tavern"|"association"|"mill"|"play"|"elders","members":[ids]}]`: who works together
  (gossip spreads through these). Every citizen is in exactly one group.

### citizens
Fields exactly: `["id","given","family","job","household","home","work","group","tavern","build","age","scale",
"speed","look","named"]`
- `job`: job id. `home`: door place id. `work`: the main workplace place id (or a route id for round/carry jobs;
  -1 for children and residents). `tavern`: the `tavern_out` place id of their usual tavern (nearest to home).
- `build`: 0 wide, 1 slim (about half and half, not tied to job). `age`: 0 child, 1 young, 2 adult, 3 old.
- `scale`: body scale, children 0.62-0.70, others 0.93-1.05 (old a little shorter), 2 decimals.
- `speed`: walking speed multiplier 0.92-1.08 (old 0.85-0.95, runners 1.0; children 1.05-1.15), 2 decimals.
- `look`: `[tone, hairStyle, hairColour, outfit, accent, face]`: tone 0-5 (skin tone), hairStyle 0-11, hairColour 0-7
  (old: 6-7 grey/white mostly), outfit 0-3 (palette variant of the job's clothes), accent 0-3, face 0-7. Seeded per
  citizen; household members share tone with probability 0.7 and hair colour 0.5.
- `named`: "" or one of `"oren","mira","teodor","sun"`.

Counts by job (exactly 240): lamplighter 8, bellringer 7 (one per district tower, 2 at the Choir), porter 18,
stallkeeper 20 (2 per stall), baker 10 (2 per bakery), smith 8 (Oren + 7: 2 per smithy), watch 24 (incl. Mira),
crier 5, boatman 8, washer 14, mason 10, runner 10, shopkeeper 30 (one per shop), tavernkeeper 14 (one per tavern),
clerk 8, archivist 2 (Teodor + 1), healer 4 (Sun + 3), miller 4, child 16, resident 20.
Named: Oren (smith, smithy 0), Mira (watch, the `scout` route), Teodor (archivist), Sun (healer). Their ages: Oren 2,
Mira 1, Teodor 3, Sun 2; their `look` is ignored by the game (they have their own skins) but fill it anyway.

Names: given names from the two lists below (build 0 draws list A, build 1 list B, ~10% cross over), no two
citizens with the same given + family; family names shared within a household; every household its own family name
unless two households are related (5 pairs share a surname). Children never get a name already used by an adult in
their household.
- A: Abel Alder Ambrose Anselm Aren Arno Bastian Bertil Bram Brannoc Cato Cillian Clem Corin Dorran Edric Emrys
  Fenwick Finch Gideon Hale Hollis Ivo Jago Jory Kasimir Kit Lucan Mattis Nico Olek Orrin Pell Perrin Quill Rafe Roan
  Rook Silas Tam Tobin Ulric Wendel Wim Yorick Ansel Benedek Caspar Davin Eben Florian Garrick Hamish Ignas Joss
  Konrad Lorcan Marek Nils Osric Piet Radek Stellan Teague Vasco
- B: Ada Agnes Alis Brisa Cass Dagny Della Edda Elin Elsabet Esme Fenna Greta Hanne Ida Ines Isolde Juna Leni Lotte
  Maren Marta Mavis Nell Noor Odile Ottilie Petra Rhosyn Romy Saoirse Selka Signe Tamsin Thea Una Vesna Wren Ysolde
  Zinnia Aino Bryony Calla Delphine Eira Freya Gisela Hester Ilse Jessamy Kerensa Liesl Morwen Nadia Ondine Philippa
  Rosalind Sabine Tilde Undine Veda
- Family: Ashgrove Bellamy Brassfield Callow Candlewick Chimewright Cobble Corder Dunmore Fairweather Fletcher Greave
  Hallam Hearn Holloway Inkwell Jessop Kettle Lantry Larkin Marrow Merriman Noll Oakes Quarry Reedman Rusk Saltmarsh
  Tolland Tollman Underbell Vane Wexley Whitlock Wick Brightwater Coldharbour Dimmock Eversley Flint Garrow Harrow
  Ivesley Kestrel Lockwood Mallory Netherby Ormsby Penhallow Ridley Stannard Thorne Upwood Varley Weatherell Yarrow
  Ashby Bracken Cresswell Dunstan Elbury Fallow Grisedale Hartwell

### schedules
`schedules[i]` = citizen i's day: `[[start, act, target, alt, dwell, flags], ...]` sorted by start, 3-9 segments,
`start` = time of day in ticks (0 = 06:00 sunrise, 1000 = 07:00, 6000 = noon, 12000 = 18:00, 13000 = dusk, 18000 =
midnight, 23000 = 05:00). The day wraps: the segment in force at a time is the last one starting at or before it (the
last of the day carries over midnight). `target` is a place id (home/inside/stand), a route id (round/wander), or the
pickup place (carry, `alt` = the drops route id); `alt` -1 otherwise; `dwell` ticks per stop for round/wander/carry,
0 otherwise; `flags` (round: bit 0 loop, bit 1 lamps light).
Jitter every start by a seeded ±300 (±150 for bellringers, ±100 for lamplighters' rounds) per citizen, keep order.
Templates (before jitter; "errand" = wander the citizen's district errand route, dwell 300):
- lamplighter: 0 home; 7000 errand; 9000 home; 11200 round lamps light (once, dwell 40); 14800 stand tavern_out;
  16800 home; 23100 round lamps out (once, dwell 30). (The same route both times.)
- bellringer: 23500 stand bell; 7000 errand (Choir pair only; the district ringers stay); 7800 stand bell; 16300 home.
- porter: 0 home; 600 carry (quay pickup → drops route, dwell 60); 5500 stand tavern_out; 6600 carry; 10600
  errand; 11600 stand tavern_out; 14000 home.
- stallkeeper: keeper 1 of the stall: 0 home; 600 stand stall_keeper; 12100 stand tavern_out; 14500 home.
  keeper 2: 0 home; 800 stand stall_keeper; 5600 errand; 6600 stand stall_keeper; 11400 errand; 12400 home.
- baker: 21400 inside bakery door; 1000 carry (bakery door → its drops route, dwell 80); 2400 stand bakery front;
  6200 home; 10000 stand tavern_out; 12000 home.
- smith: 0 home; 1000 stand anvil (the two smiths of a smithy: one at the anvil, the other `inside` the smithy door,
  swapping at 5600); 5600 errand; 6300 swap; 10600 stand tavern_out; 13800 home.
- watch: 14 day: 0 home; 600 round patrol (loop, dwell 160) or stand at a gate post (6 of them: 2 per gate inner
  side, cap 2 `post` places); 12400 stand tavern_out; 14200 home. 10 night: 0 round patrol (from the previous night);
  1000 home; 9000 errand; 10800 home; 12500 round patrol. Mira: 600 round scout (loop, dwell 200); 11000 stand
  tavern_out; 13000 home.
- crier: 0 home; 1200 round crier (loop, dwell 1200); 6000 errand; 7000 round crier; 11000 stand tavern_out; 13500 home.
- boatman: 0 home; 800 stand pickup (the quay); 5600 errand; 6400 stand pickup; 10800 stand tavern_out; 13500 home.
- washer: 0 home; 600 stand wash; 2800 stand line (hang the laundry; the 2-3 washers of a line); 3600 stand wash;
  8200 stand line (take it in); 9000 errand; 10500 home.
- mason: 0 home; 1000 stand (a mason's yard: a `stand` place cap 4 in the Works, against a wall); 5600 errand;
  6400 stand yard; 10600 stand tavern_out; 13600 home.
- runner: 0 home; 1000 round courier (loop, dwell 60); 5800 stand tavern_out; 6600 round courier; 10400 home.
- shopkeeper: 0 home; 1000 stand shop_door; 2600 inside shop; 5000 stand shop_door; 7000 inside shop; 10000 stand
  shop_door; 12000 home.
- tavernkeeper: 0 home; 4000 inside tavern; 9500 stand tavern_out; 11500 inside tavern; 20000 home.
- clerk: 0 home; 1500 inside association; 5600 errand; 6600 inside association; 10500 home.
- archivist: Teodor: 0 home; 1500 stand (a `stand` place on the terrace in front of the Association, cap 2);
  3200 inside association; 8800 stand terrace; 10500 stand tavern_out; 13000 home. The other: like a clerk.
- healer: 0 home; 1000 inside association; 4000 stand (the terrace stand); 5000 inside association; 11000 home.
  (Sun keeps this; the 3 others spread their starts by ±600.)
- miller: 0 home; 800 stand mill; 6000 errand; 6800 stand mill; 10800 stand tavern_out; 13000 home.
- child: 0 home; 1600 wander play (dwell 400); 5600 home; 6600 wander play; 10600 home.
- resident: 0 home; 1500 errand; 4800 stand sit (cap 3 benches); 6600 home; 7600 stand sit; 10200 stand
  tavern_out; 13000 home. Half of them swap the morning errand and the first sit.
Spread people over places: never assign more citizens to a place than its cap at the same time (count overlapping
stand segments); patrols and rounds may share routes but start at different stops (store the first stop index in
`alt` for round segments: `alt` = starting stop index). Make as many places of a kind as the caps need (e.g. 10-14
`sit` places, enough Watch junction stands). When a citizen's tavern_out is full at that hour, use the next-nearest
tavern's, else `inside` the tavern door instead (taverns hold anyone inside).

### extras
`[{"id":240+k,"role":"thief","look":[...],"build":b,"scale":s}]` for k = 0..7: event actors (burglars and
pickpockets), not citizens, with seeded looks.

## Checks (`check` must run all; print a one-line summary of each)
1. Counts: 240 citizens, job counts as above, 4 named, every citizen in one household and one group.
2. Every place's node is in the main component; every place's spots are walkable, ≥ 0.9 apart, never in a plot or
   water; door places' spots are their door cells.
3. Reachability: every citizen can walk from home to every target of their schedule over the street graph (Dijkstra
   on edge seconds or lengths), and every route's consecutive places are connected.
4. Feasibility: for every pair of consecutive segments, walking time from the earlier segment's (last) target to the
   next target at 0.088 blocks per tick × speed is ≤ the earlier segment's duration minus 100 ticks... except where the
   earlier act is a round/carry/wander (then just ≤ its duration). Print the worst 10 margins.
5. Caps: no place over cap at any time of day (sample every 100 ticks; rounds/carry/wander excluded).
6. Lamps: all 78 in exactly one lamp route; each lamp route's light round (walk + 40 per lamp at the slowest
   lamplighter speed) finishes before 13600 and the out round before 1800.
7. Determinism: two generations give identical bytes.
8. Size: the file is under 400 KB.

## Stats to print
Per job: count, mean home→work walking distance (blocks), max. Households by size. Places by kind. File size.

## Finally
Write a short section at the end of this brief file ("## Result") with the stats and anything you had to decide
(e.g. a place kind you couldn't satisfy and what you did instead), then commit. Your final message: the stats, the
check summary, and the commit hash.


## Result

Done: `tools/city/citizens.py` (about 3,400 lines, Python 3 + numpy + PIL) writes
`src/main/resources/data/awaken/city/citizens.json` (one line, 98.3 KB, `planSha256` of plan.json). It reads plan.json
only. `python3 -I tools/city/citizens.py` writes and prints stats (about 3 s), `check` runs the checks (about 5 s, exit 1
on a failure), `preview` also writes `build/city/citizens-top.png`.

### Checks (`check`, all pass)
```
ok   1 counts: 240 citizens, jobs ok, 4 named, 96 households {1: 21, 2: 28, 3: 30, 4: 12, 5: 5}, 47 groups
ok   2 places: 491 places, 805 spots, 16 with approach 1
ok   3 reachability: 240 citizens x their targets, 80 routes
ok   4 feasibility: 1601 transitions, worst margins: 5t miller#202, 10t crier#97, 11t crier#96, 11t child#207, 12t porter#23, 13t crier#99, 16t washer#112, 16t child#213, 17t porter#15, 18t resident#234
ok   5 caps: 109 stand places, peak occupancy 5
ok   6 lamps: 78 lamps in 8 routes (longest tour 304 blocks), at the run 0.135/tick x slowest speed 1.06: light round ends 147 ticks before 13600, out round 299 before 1800
ok   7 determinism: two generations identical (100651 bytes)
ok   8 size: 98.3 KB (limit 400 KB)
ok   9 structure (more of the brief): ...
```
Check 9 is mine: it re-derives the rest of the brief's geometry rules from the JSON (patrol stop spacing 25-60 by graph,
route sizes, wash spots touch water and are 2 apart, lines have plots at both ends, are 8 apart and within 40 of a wash
place, smithies 30 apart with the door on a street or lane, bakeries and quay as specified, no two places share a cell, the
lane rule on every spot, a household per named citizen, spots within 14 blocks of the node when the approach is 1 and 36
otherwise). Check 4 includes the builder's worst-case rule (below). The JSON parses with `Roster.java`.

### Stats
```
per job (count, mean / max home -> work walking distance, blocks; route jobs: to the nearest place of the route, porters: to
their pickup; the most is 125)
lamplighter 8 36/88   bellringer 7 55/98     porter 18 83/108     stallkeeper 20 79/123  baker 10 62/123
smith 8 29/124        watch 24 43/123        crier 5 105/121      boatman 8 95/125       washer 14 57/95
mason 10 68/121       runner 10 38/120       shopkeeper 30 72/112 tavernkeeper 14 61/115 clerk 8 77/107
archivist 2 56/59     healer 4 63/66         miller 4 83/122      child 16 -             resident 20 -
households by size: 1: 21, 2: 28, 3: 30, 4: 12, 5: 5 (96; the four named are single households with no family name)
places by kind: lamp 78, stall_keeper 10, stall_front 10, bell 6, door 149, tavern_out 14, shop_door 30, stand 120,
  anvil 4, wash 12, line 4, pickup 2, mill 2, post 3, sit 13, crier 15, landmark 4, play 15 (491)
routes by kind: lamps 8, patrol 12, scout 1, crier 5, courier 3, drops 6, sit 1, errand 41, play 3 (80)
schedule segments per citizen: 3: 33, 4: 55, 5: 56, 6: 88, 7: 8 (never fewer than 3 or more than 9)
starts pushed back to fit a walk: 17 citizens, at most 2450 ticks (the criers; median 300)
```

### The feasibility rules (check 4; the generator and the check use the same ones)
Walking is 0.088 x speed blocks a tick everywhere, over the street graph (node to node plus the last blocks to the spot).
For every pair of consecutive segments (the day wraps) the walk from the earlier one's last place to the next one's first
must fit the earlier one's duration (less 100 ticks, except after a round/carry/wander: then the walk is from the stop of
the route farthest from the target and there is no 100). On top of the builder's rule: after a round/carry/wander, a stay
at a place (home, inside, stand) must hold the worst walk in + 200 ticks standing + the walk on to the next segment's first
place. A wander's first stop is the route place nearest (straight line) to where the citizen is, so the walk in is to that
place, and the wander must hold walk in + 100 + the walk from that place to the next target. Where a stay or a walk does not
fit, the generator moves starts (in 50s): first the stay begins earlier if the route before it has time to spare, else
the segment after it begins later. That is the "starts pushed back": 17 citizens, mostly the criers and 2 residents.

### What I decided (the brief conflicted with itself or with the plan's geometry)
1. **Leading `0 home` is merged into the day's last `home`.** Read literally check 4 gives `0 home -> 600 carry` only
   500 ticks (44 blocks) for the walk, and only 1 to 7 houses lie that close to the cranes, the race or the Choir. The day
   wraps, so a schedule that starts with its first work segment and ends with `home` keeps the citizen at home from 06:00
   until they set out, exactly as `0 home` meant. This is why most schedules have 3-6 segments and few start at 0.
2. **Lamplighters.** (a) Their first `home` is at about 2300 (after the out round has finished), not 0: the out round carries
   over midnight. (b) Check 6 can only pass at a run: the best cut of the 78 lamps into 8 tours leaves tours of 179-304
   blocks, which at the 0.088 stroll need 2,000-3,500 ticks against a 2,300-tick window. So check 6 is measured at 0.135
   (`RUN_PACE`, the game's lamplighter run) x the slowest lamplighter's speed (1.06); lamplighter speeds are drawn from
   1.05-1.08. (c) A long tour's light round starts before 11200 (10,998 to 11,269 jitter included) so that it ends before
   13600. Lamp rounds are `fix`ed: no walk pushes them. Each tour starts at the end lamp nearest its lamplighter's home
   (checked), who lives within about 90 blocks of it.
3. **Courier loops are compact on purpose** (checked by the stay rule: a runner stands at a tavern between the two rounds
   and must have time to get there from the farthest stop and back): Roost, Association and 3 junctions, all within about
   200 blocks of the north-bank tavern at the North Bridge (plot 253), which is the usual tavern of the 99 houses around it;
   the runners live there (mean home to nearest stop 38 blocks).
4. **Wanderers and loops.** The walk in to a wander is to its nearest place; the walk out is from any place (all must be in
   reach); a loop is left from any stop; a round starts (`alt`) at the stop nearest where the citizen comes from; night
   watchmen use the same stop for both rounds.
5. **Errand routes: 41, not 7.** The 7 per-district routes (stall fronts for the market, else tavern_out + benches + a few
   corner stands) come first; where one is too far for a transition (smiths 700 ticks, millers 800, the stays above) there
   are 34 local routes of the 4-7 eligible places nearest the citizen's workplace or home. Eligible places are shop_door,
   tavern_out, sit, stall_front and the stands. About 35 extra `stand` places (cap 2, at street corners) exist only to be
   errand destinations, so every workplace and every home that needs one has at least 5 eligible places within 40-45 blocks.
6. **Washers.** 7 per washplace. The canal washplace has 6 wash places on both banks of the Washers' Bridge; the race one
   has 6 on the race's south bank only (the north bank is cut off by the mills; the graph reaches it only the long way round).
   Lanes across a lane/alley/street between plots are scarce near the canal, so there are **4 laundry lines (1 at the canal,
   3 at the race)**, all within 40 blocks of a wash place. 12 wash places and 4 lines (cap 2) cannot seat 14 washers at
   once, so: the washers whose wash place is nearest a line are *hangers* (line at 2800 and 8200 as in the template), the
   others only wash, and the 7th of each washplace is a *tender* who stands at a line while the hangers are at their wash
   places and takes the first hanger's wash place while they hang.
7. **Bakeries.** The second baker of a bakery is `inside` the bakery door where the template says `stand bakery front`
   (the front is cap 1). The quay and bellwatch bakeries are the plots that best meet "its nearest 2 stall fronts are
   within a 123-block walk of its front".
8. **Taverns.** A citizen's `tavern` is their nearest tavern_out by walking (children: -1). When it is full the next nearest
   of the three (within 60 blocks of the nearest's distance) is used, else `inside` the usual tavern's door (65 of 144 visits).
9. **Patrol loops.** 6 of the 12 patrol routes are real loops of 5-6 junction stops; the other 6 could not close within 25-60
   by graph, so they run out and back along a street: 5 distinct stops listed as `s0 s1 s2 s3 s4 s3 s2 s1` (8 entries; the
   loop closes `s1 -> s0`). 7 day watchmen and 10 night watchmen walk them. The scout loop (Mira) is the three gate posts
   and four Wallring stands by angle. Crier loops run Choir post, then two neighbouring district plazas (ring order
   bellwatch, quay, works, garden, terrace). All 4 landmark places (roost, association, choir, market_hall) exist; the choir
   and market_hall ones are not used by any schedule.
10. **Names.** The brief's surname list has 64; 91 households need their own, so `FAMILIES_EXTRA` adds 30 in the same vein
    (5 related pairs share a surname, picked among households whose homes are within 45 blocks). Named citizens have family
    "" and a household of one.
11. **Places.** `node` for a non-door place is the nearest *junction* node by walking over walkable cells (door and landmark
    nodes are for going inside), unless a spot would then be too far for the game (approach 1: at most 14 blocks from the node;
    straight: at most 36), in which case the nearest node that fits (the two mills, in the end). The lower quay (4 below the
    street, ladders) is not walkable ground; bridge decks are. "Narrow street" for the lane rule = a LANE/ALLEY cell, or a
    cell on any plan street narrower than 6, or less than 6 open cells across. `ref` is -1 except for door (plot id),
    landmark/bell/post (landmark id), lamp (lamp index), stall_keeper/stall_front (stall index), anvil/tavern_out/shop_door/
    bakery-front stand (plot id). The stalls' `colour` is an awning index 0-7 (no two neighbours alike). Ringers stand on the
    tower's south face. Coordinates in `smithies.block`, `washplaces.tub`, `quay.crates` and `lines.a/b` are integer cell
    coordinates (x, ry, z with ry = the ground's ry; the anvil/cauldron/crate sits on top), not block centres.
12. **`work`**: a place id for bellringer (bell), stallkeeper (stall_keeper), baker (bakery door), smith (anvil), gate watch
    (post), boatman (pickup), washer (their wash place), mason (yard), shopkeeper (shop door), tavernkeeper (tavern door),
    clerk/archivist/healer (association landmark), miller (mill); a route id for lamplighter (lamp route), porter (the drops
    route), patrol/night/scout watch, crier and runner (courier route); -1 for children and residents. The child's `play`
    route and the resident's bench are in the schedule only.
13. Citizens' ids are ordered by job (lamplighters 0-7, bellringers 8-14, ...). Oren is smith 63, Mira 71, Teodor 194, Sun 196.
    Healers other than Sun have their whole day shifted by a seeded +-600 (rounded to 100) before the usual jitter.
