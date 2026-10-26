# AWAKEN village audit: vanilla villages, Carillon's people and fabric, the people of the wilds

Read-only audit of `/Volumes/MEDIA/Awaken/.claude/worktrees/world-depth-e0a13d` (branch `claude/world-depth-e0a13d`,
HEAD c7846bc2, working tree clean). Paths are relative to `src/main/java/dev/awaken/` unless they start with `src/`,
`docs/`, `tools/`, `pack/` or `/Volumes`. The code is the truth; where SPEC and code differ, the code is quoted.
Written for the "living villages built on the city's logic" design (hamlets to towns, jobs and routes, varied homes,
village events): what exists, what is generic, what is hard-wired to Carillon.

**The ten facts that shape a village design**

1. Nothing in AWAKEN touches vanilla villages: no village structure, pool, trade, raid, siege or villager handler, no
   mixin. Only the lands pack's biome tags and villager types decide where they spawn (section a).
2. Awakened Lands removes most snowy villages: Frost Spires replaces snowy plains but carries no village tag. Glass
   Steppe keeps savanna villages. No pack mod adds or changes villages.
3. Nothing keeps a vanilla village out of Carillon's basin. The city's painter erases blocks within its reach, but
   entities and any pieces outside that reach stay.
4. Carillon's citizens are a mix: all 468 records are simulated on the server as an abstract ledger (24 records a
   tick); at most 96 server bodies exist (40 a player, within 56-64 blocks); animals, lantern light, festoons and sound
   are client-only.
5. The people engine (`Ledger`, `StreetNet`, `Crowd`, `CitizenBody`, `CitizenWork`'s stations) is data-driven by a
   roster and a graph. But the whole module is one static singleton for one plan in the overworld, and the roster is
   made offline by Python from `plan.json`.
6. The city's grammar is a kit for filling plots, not a settlement generator. The layout comes from an offline Python
   script (`tools/city/plan.py`), one 512 x 512 raster, one street height, seven hard-coded districts.
7. No building has an interior. Enterable plots are hollow, unlit, stacked rooms with no stairs. The only furnished
   house in the codebase is Oren's 9 x 9 cottage, which has ten biome palettes.
8. The quests are data: 7 objective types, spawns at scene markers, a `Quests.talk` hook any NPC can call. The World
   Pulse has 9 happenings, and only one involves people (the Caravan). There are no refugees, festivals or kidnappings.
9. Settlement raids already exist: a tier-4 camp raids the nearest Hearth or the Wayfarer camp, warned a play-day
   ahead. Named-beast lairs exist as code-built structures, about one per 1,800 blocks.
10. There is no coin. `EconomyHooks` is never installed. Trade exists only as vanilla `MerchantOffers` (gate markets,
    the Broker, the Caravan's trader) in emeralds and essence shards.

---

## (a) Vanilla villages today

### What AWAKEN does to villages

| Touch point | Where | Effect |
|---|---|---|
| Villager types for Awakened biomes | `src/main/resources/datapacks/awakened_lands/data/neoforge/data_maps/worldgen/biome/villager_types.json:3-14` | `frost_spires` → `minecraft:snow`, `glass_steppe` → `savanna`, `mirror_marsh` → `swamp`, `shattered_highlands` → `taiga`. Elderwood and Lumen Forest get none (the vanilla default) |
| Village biome tags | `src/main/resources/datapacks/awakened_lands/data/minecraft/tags/worldgen/biome/has_structure/village_savanna.json:2-4` | Only `awaken:glass_steppe` is added to any village tag. No other `village_*` file exists in the pack |
| How the tags are made | `tools/terrain/biomes.py` (SPEC §15, lines 1282-1296) | Each Awakened biome gets every `minecraft:`, `c:` and `awaken:` tag of its archetype, so it carries vanilla structures only if its archetype does |
| Archetypes | `src/main/resources/datapacks/awakened_lands/data/awaken/tags/worldgen/biome/archetype/*.json`; read by `terrain/api/BiomeTable.java:38-47` | elderwood = old_growth_birch_forest, glass_steppe = savanna, frost_spires = snowy_taiga, lumen_forest = dark_forest, mirror_marsh = swamp, shattered_highlands = windswept_hills |
| Zombie villagers in spawn lists | e.g. `src/main/resources/datapacks/awakened_lands/data/awaken/worldgen/biome/glass_steppe.json:80`, `elderwood.json:55` | Copied from the archetypes |
| Village structure sets, template pools, jigsaw overrides | none | No `structure_set`, `template_pool`, `processor_list` or `structure_modifier` for villages anywhere in `src/main` |
| Villager, trade, raid, siege or golem events | none | No `VillagerTradesEvent`, `WandererTradesEvent`, `Raid`, siege or `Villager` handler in `src/main/java` |
| Mixins or access transformers | `src/main/resources/awaken.pixels.mixins.json`, `awaken.wateredge.mixins.json` | Two client render mixins. No access transformer. Nothing touches villages |

### Where villages still spawn (lands injectors × village tags)

The injectors are in `src/main/resources/datapacks/awakened_lands/data/awaken/lithostitched/biome_injector/lands/*.json`
(Lithostitched `replace_partially` on the `awaken:lands/awakening` field). SPEC §15 (1271-1277): "−0.27 claims about
80%"; "about a fifth to a third of each archetype stays vanilla".

| Awakened biome | Replaces (targets) | Threshold | Village tag it carries | Effect on villages |
|---|---|---|---|---|
| Glass Steppe | savanna, savanna_plateau, windswept_savanna | −0.27 | `village_savanna` | Savanna villages keep spawning, now on steppe ground |
| Frost Spires | **snowy_plains**, snowy_taiga, grove, ice_spikes | −0.27 | none (vanilla `village_snowy` = snowy_plains only) | **Snowy villages lose roughly 65-80% of their land.** Villagers born here are still snow type |
| Elderwood | forest, birch_forest, old_growth_birch_forest, flower_forest | −0.22 | none | No change (no vanilla village in forests) |
| Lumen Forest | dark_forest | −0.34 | none (gets `woodland_mansion`) | No change |
| Mirror Marsh | swamp, mangrove_swamp | −0.41 | none (gets `swamp_hut`) | No change |
| Shattered Highlands | windswept_hills, windswept_gravelly_hills, windswept_forest, stony_peaks | −0.34 | none | No change |

Plains, meadow, desert and taiga are no injector's target, so their villages are untouched. Every vanilla
`has_structure` tag an Awakened biome joins is listed in
`src/main/resources/datapacks/awakened_lands/data/minecraft/tags/worldgen/biome/has_structure/`.

**Awakened Alpine** (`src/main/resources/datapacks/awakened_alpine/`) changes no biome. It adds two tags, `#awaken:alpine`
and `#awaken:upland`; upland includes plains, meadow, taiga and snowy_plains. It also adds erosion on the terrain offset,
faded in on slopes of 0.04-0.45 and at y 74-140 (SPEC 1360-1368), and a snow-and-rock surface. No structure rule
changes, so villages generate where vanilla puts them. On high upland their ground is eroded terrain.

### The city basin and villages

- `terrain/api/CityBasin.java:52-55` `reserved(x, z)` (core 330 + margin 48) is checked by Awaken's own content only: lairs
  (`world/fauna/lair/LairStructure.java:46`), Precursor ruins (`above/ruin/PrecursorRuinStructure.java:43`), hero trees,
  landmarks, Lands features, the Well, the gate Director, anomalies, windways, the Pulse and the Scenes.
  `terrain/city/SitePicker.java:39-105` scores the site by terrain only. **Nothing keeps a vanilla village out of the
  reserve.**
- The city feature runs at `top_layer_modification` in every `#minecraft:is_overworld` biome
  (`src/main/resources/datapacks/carillon/data/awaken/neoforge/biome_modifier/city.json:2-5`), after structures. Within
  the city's reach it clears every column to `CLEAR_TOP` = 152 above the street (`city/CityPainter.java:22, 536-564`).
  The reach is min(core − 4, fieldsTo + 8) = 326 when terrain carved the basin, 262 when it didn't
  (`city/CityLayout.java:51`). `CityWriter` drops the block entities in the cells it replaces
  (`city/CityWriter.java:25-28`). It does not remove entities. **Risk (not seen in game):** a village that started
  inside the reach keeps its villagers and golems on Carillon's fields. Its pieces between the reach and the edge of
  the reserve stay standing.

### Code that already reads villages (reusable checks)

| What | Where | Note |
|---|---|---|
| "Could a settlement start near here", with no chunk loads | `descent/well/Well.java:108-123` (`KEEP_CLEAR` = VILLAGES, PILLAGER_OUTPOSTS, WOODLAND_MANSIONS; `CLEAR_CHUNKS` 6; `hasStructureChunkInRange`) | The pattern for keeping a new settlement away from vanilla ones |
| Gates never carve into a base or a village | `gates/Overworld.java:446-488` (`untouched` / `built`: any block entity, planks, beds, doors, stairs, slabs, glass, torches, lanterns, farmland, dirt path...) | Villages (ours or vanilla) are protected from gate craters for free |
| Spirit sight ignores village chests | `veil/SpiritSight.java:220-222` (`StructureTags.VILLAGE`) | |
| The Day Journal names "a village" | `lucidium/journal/Describe.java:86` | |
| Rift hints | `world/anomaly/Rifts.java:339` (`HAS_VILLAGE_SNOWY`) | |
| Hearth why-lines ignore village beds and anvils | `hearth/HearthNow.java:89`, `hearth/room/RoomScanner.java:47` | |
| Powers never hurt an `Npc` (villagers, citizens, Wayfarers, townsfolk) | `powers/Combat.java:53-66` | |
| Region levels skip `Npc` | `living/combat/Creatures.java:43-47, 50-63` | Raid pillagers and siege zombies take their region's level (and 1 in 20 is an elite). Villagers keep vanilla bodies. Golems get a level for XP only |
| Fauna treat golems as allies, except night hunters | `mobs/entity/FaunaMob.java:648-652` | "Golems guard villages from night hunters only." |

### Pack mods (`pack/mods.json`, 31 mods; `pack/lock.json` adds Lithostitched as a dependency)

None is a village mod: no Towns and Towers, MCA, YUNG's, CTOV or structure pack.

| Mod | What it does to villages |
|---|---|
| tectonic 3.0.31 (`world` profile) | Reshapes terrain (mountains, valleys, deep oceans), so villages sit on new ground. Keeps vanilla biomes. `pack/tectonic/*.json` has no structure keys |
| lithostitched 2.0.6 (dependency) | Runs our biome injectors and density modifiers. Adds no village changes of its own |
| fresh-animations + entity-model-features + entitytexturefeatures (`feel`, client) | Livelier vanilla villagers, golems and animals (breathing, blinking, idles). Looks only |
| jade, sound-physics-remastered, ambientsounds | Labels, reverb, nature beds. Nothing structural |
| the rest (perf, camera, HUD, voice, profiling) | Nothing |

---

## (b) Carillon's citizens (`citizens/` + `citizens/client/`)

### Server entities, client bodies or both? Both, in three layers

| Layer | What | Count / cap | Where |
|---|---|---|---|
| Ledger (server, abstract) | Every record's day: segment, node, edge progress, goal, spot, state | 460 citizens + `EXTRAS` 8 thieves = 468 records. `slice = (n+19)/20`: 24 records a tick, each about once a second | `citizens/Roster.java:33`, `citizens/Ledger.java:79, 257-264` |
| Bodies (server entities) | `CitizenBody extends PathfinderMob implements Npc`, `noSave().noSummon().clientTrackingRange(5)` | ≤ 40 per player, ≤ 96 per world, made within `EMBODY` 56 and kept to `KEEP` 64, ≤ 8 new a pass, every 10 ticks; `MAX_PLAYERS` 16 | `citizens/CitizenBody.java:34`, `citizens/CitizensContent.java:38-39`, `citizens/Crowd.java:26-32, 137, 152`, `citizens/Citizens.java:148` |
| Client-only life | Animals and boats (`CityCritter`, `noSave`, discarded on a server), drawn lantern and lamp light, festoons, district sound | Pigeons 40, gulls 16, cats 10, dogs 8, carts 4, boats 8. Ranges 56/72/40/48/96/88 | `citizens/CitizensContent.java:42-43`, `citizens/client/CityCritters.java:49-50`, `citizens/client/CityGlow.java:91-94` |

Measured (M3, 240 + 8 records, `docs/carillon/milestones/M3-alive.md:57-61`): ledger p50 0.005-0.012 ms and p95
0.019-0.06 ms a tick. 40 bodies at the noon square: p50 0.36, p95 0.59 ms. Next-hop columns about 0.2 ms each, at most 3
a tick. M4 B3a: "460 citizens up in 6.7 ms"; 2,236 segments, 0 missed (`docs/carillon/milestones/M4-B3a.md:69`).

### The pieces

| Piece | Where | How it works (key constants) | Generic or Carillon-wired |
|---|---|---|---|
| Lifecycle | `citizens/Citizens.java:23, 82-106` | `static @Nullable Citizens active`: one per server. Starts on a tick once `City.layout()` exists (84), only in `server.overworld()` (89), and only if the built city's plan hash equals the roster's `planSha256` (93-97). Clock = overworld clock (66-68) | **Wired**: a singleton for one city |
| Roster | `citizens/Roster.java:19-31, 145-160, 445-475` | `data/awaken/city/citizens.json`, schema 1, read once from the jar, "never from data packs: the roster belongs to the one designed city" (24). Per record: name, job, household, home, work, group, tavern, build, age, scale, speed, `look[6]`, named. Coordinates are plan-relative (26-27) | Engine generic; **data and loader Carillon-only** |
| Jobs | `citizens/Roster.java:36-41` | 21 job ids: lamplighter, bellringer, porter, stallkeeper, baker, smith, watch, crier, boatman, washer, mason, runner, shopkeeper, tavernkeeper, clerk, archivist, healer, miller, child, resident, thief. SPEC §16.5 says "12 jobs"; the code has 21. Roster mix: resident 140, stallkeeper 48, child 36, watch 32, shopkeeper 30, tavernkeeper 28, porter 26, lamplighter 20, washer 14, mason 12, runner 11, clerk 11, baker 10, boatman 10, smith 8, bellringer 7, crier 7, healer 4, miller 4, archivist 2 | Generic ids |
| Roster generator | `tools/city/citizens.py` (4,382 lines, Python + numpy) | Derives households, homes, workplaces, routes, stands and schedules from `plan.json`. 180 households, 101 groups, 14 taverns, 937 places, 143 routes, 1,417 spots | **Offline, one city.** Not runnable in game |
| Places and stations | `citizens/Roster.java:45-135` | 20 place kinds (door, stand, lamp, stall_keeper, stall_front, bell, anvil, wash, line, pickup, post, crier, sit, play, mill, landmark, tavern_out, shop_door, mooring, net), each with spots and a capacity. Station lists: lamps, stalls, bells, bakeries, smithies, wash places, laundry lines, moorings, taverns, households, groups | **Generic vocabulary** for any settlement |
| A citizen's day | `citizens/Roster.java:445-468`; `citizens/Ledger.java:137-208, 266-335` | A day is a list of segments `[start tick, act, place, alt, dwell, flags]`. Acts: HOME, INSIDE, STAND, ROUND, CARRY, WANDER (`Roster.java:43`). The segment in force is the last one starting at or before the time of day. A citizen leaves early by route distance / speed + `LEAVE_MARGIN` 80 (`Ledger.java:28, 313-335`). `WALK_SPEED` 0.088 and `RUN_SPEED` 0.135 blocks a tick (25). Lamplighters and runners run their rounds (341-346). A clock jump seats everyone where the hour wants them (290-297). Hurt citizens go to the `association` landmark (82, 277-288) | Generic |
| Abstract walking | `citizens/Ledger.java:348-385` | Records with no body advance along the street graph by speed × ticks. An unreachable goal snaps them there ("never seen") | Generic |
| Saved state | `citizens/CitizensData.java:20-27, 110-170` | `awaken:citizens`, world-global SavedData, schemaVersion 1. Struct of arrays per record. City state: lamps, stalls, stock, laundry, **`safety` and `gratitude` byte[8] by district id** (150), repairs, furnished bits, bell positions, chimneys. Per player `Memory`: `know[n]`, `regard[n]`, deeds. A roster sha mismatch reseats everyone (`Ledger.java:87-104`) | **One record set for one roster**; districts fixed at 8 |
| Street graph | `citizens/StreetNet.java:10-23, 51, 113-160, 212-306` | Built from the world's `CityData` graph or the plan. One Dijkstra per destination, baked on demand, `BAKES_PER_TICK` 3. `Column(short[] next, int[] dm)` holds at most 32,767 nodes. Keep-right lanes per edge. Edges can be closed (`eoff`, a cart jam). Wall walk joined with hidden tower stairs (`MOVE_HIDDEN` 9, `TOWER_CLIMB` 45). Carillon: 1,955 street nodes, 754 roof nodes (`plan.json` graphs) | **Generic** graph runtime |
| Bodies | `citizens/Crowd.java:17-62`; `citizens/CitizenBody.java:34-39, 410-456` | Nearest-first embodiment. Path slots 4, or 6 above 60 bodies (`Crowd.java:62`). The brain thinks every `max(1, min(40, d²/256))` ticks (`CitizenBody.java:423`). Stuck 200 ticks → teleport, only out of every player's view (452-456). Bodies use real doors. Synced rec, act and flags (35-39). Bodies read `Citizens.active` (228, 400, 504, 525, 539) | Generic, apart from the singleton |
| Work (jobs that change the world) | `citizens/CitizenWork.java:36-44, 70, 187-237` | Every job sets a wanted state in `CitizensData`, which is reconciled a little each tick in loaded chunks only (`edits`, 63-67). Lamps lit 13000-23200 (`NIGHT_FROM`/`NIGHT_TO`). Stalls open 700, close 11800. Hour bells 06:00-22:00 (197-199). Laundry 3600-8200 (232-233). Masons repair queued breaks (611-686). Furniture placed once: laundry, anvils, tubs (456-510). Chimneys found per plot (716-790). Stock and `sales` (160) | Mostly generic over stations. **Wired**: the `"choir"` bell (552, 566, 596); chimneys, props and lamps read `CityPlan.get()` (82, 717-751, 809) |
| The lamps' monster rule | `citizens/CitizensModule.java:75-82`; `citizens/CitizenWork.java:315-337` | Inside the wall, natural monster spawns fail unless near a dark lamp | Needs `CityHooks.inCity` |
| What a body shows | `citizens/Act.java:10-20`; `citizens/Props.java:11-31`; `citizens/CitizenWork.java:839-1017` | 41 clips (an ordinal, append only). 12 held props. Picked by job, state and place kind | Generic |
| Speech | `citizens/Lines.java:16-24, 46-99`; `src/main/resources/data/awaken/city/citizen_lines.json` (from `tools/city/lines.py`) | Pools `greet.<job>.<tier>` (20 jobs + `any`), `named`, `calls`, `news`, `event`. A seeded pick, never random; time tags `[m] [d] [e] [n]`. Fills `{name}`, `{district}`, `{gate}`, `{hour}`. Sent as a `Speech` payload (`citizens/CitizensNet.java:25, 102`) | Generic mechanism; Carillon text |
| Recognition | `citizens/Recognition.java:22-37, 150-162, 174-216, 237-266` | Familiarity per citizen per player. A deed counts if a citizen has line of sight within 24 (200). `SEEN_CAP` 35, `FAMILIAR` 20, `KNOWN` 55, city tier at 30 familiar / 24 who know you (33-37). Gossip once a play-day through households, workplaces and taverns. **District names hard-coded** (156-162). Installs a minimal `RenownHooks` (witness and tier only: `citizens/CitizensModule.java:58, 156-168`) | Generic per record; wired names |
| City events | `citizens/CityEvents.java:38-112, 121-138, 655-860, 933-988` | Kinds: burglary, pickpocket, fire, cart, tremor, plus the opening's rush and infirmary (47). Each play-day is planned from a seed: burglary 85% (15500-20500), pickpockets 0-2, fire 20%, cart 14%, tremor 33% (100-112). Planned events start only while players are on (83). `MAX_TICKS` 12000 (55). Fire: a Works house with a door (666); `CityFireBlock` flames never spread; a bucket line from water within 40; a household member trapped behind the door; scorched blocks (coal, blackstone) left for the masons over two play-days; Works safety −6 (826-838). **No Bell Festival in code** (SPEC §16.5 lists it). `CitizenHooks.post` takes bell_wrong, gate_rush, gate_burst, bell_bridge_down, infirmary_wake, infirmary_done, tremor (121-138) | **Wired** to districts: fire → `WORKS`, cart → `QUAY_D`, pickpocket → `MARKET` (645, 666, 722, 837, 933, 987) |
| Lanterns | `citizens/Lanterns.java:12-21`; `citizens/client/CityGlow.java` | Lantern light is drawn on the client; the server places no light blocks (each moved light block re-meshed 60-100 sections). The legacy path is kept for measuring only | Generic and cheap |
| Critters | `citizens/client/CityCritters.java:31-50, 126-190` | Client-only. Homes from the plan's props, districts, plots and river. Carts and rowed boats run on the world clock | **Wired** to `CityPlan` and the river |
| Soundscape | `citizens/Soundscape.java:12-27, 66, 101`; `citizens/client/CitySound.java`, `CityNature.java` | Beds `awaken:city/<district>` and `city/night`; the level is outdoor citizens against `PEAK = {21, 169, 42, 55, 21, 41, 20, 21}` by district id; `KEYS` hard-coded | **Wired** |
| Skins | `citizens/client/CitizenRenderer.java:61-81`; `tools/art/specs/citizens.py` | One pre-baked PNG per record index (`textures/entity/citizen/c%03d.png` plus `_blink`, 924 files); named ones use `townsfolk/<name>`; missing → fallback | **Tied to record index** |

### Where SPEC and code differ (the code is quoted)

| SPEC | Code |
|---|---|
| §16.5 "Ledger: 240 records, sliced 12 per tick" | 468 records, `slice = Math.max(1, (n + 19) / 20)` = 24 a tick (`citizens/Ledger.java:79`) |
| §16.5 "12 jobs" | 21 job ids (`citizens/Roster.java:36-41`) |
| §16.5 "Bell Festival every 10 play-days" | `KINDS = List.of("burglary", "pickpocket", "fire", "cart", "tremor", "rush", "infirmary")` (`citizens/CityEvents.java:47`); no festival |
| §16.4 "citizens draw only within 128 blocks" | Bodies exist only within `KEEP = 64` of a player (`citizens/Crowd.java:27`); tracking range 5 chunks (`citizens/CitizensContent.java:39`) |
| §16.0.8 townsfolk "at Trusted renown each can move to your Hearth" | Arrival by household rank only (`hearth/town/Town.java:107-122`); no renown link |

### Could it run a 6-house hamlet or a 40-house town?

The core is cheap and data-driven. For scale: 248 records cost p95 0.06 ms a tick in M3, so a 40-house town of 80-150
records costs nothing in the ledger. The blockers are structural:

| Needed for many settlements | Today | Change needed |
|---|---|---|
| Several settlements at once | `Citizens.active` singleton (`Citizens.java:23`); `CitizenBody` reads it | A `Settlement` instance per place (ledger, net, data, work, events). Bodies carry their settlement id |
| Rosters made in game | Offline `tools/city/citizens.py` → one jar resource (`Roster.java:31`) | A Java roster builder from a generated layout (households from plots, jobs from stations, schedules from templates), or data templates per settlement size |
| Saved state per settlement | One `awaken:citizens` with `byte[8]` districts and per-player arrays of size n | A schema with a list of settlements, each versioned, with its own sizes and district count |
| Places | One `cx, cz, streetY` and plan-relative coordinates (`Ledger.java:45`) | World coordinates or a per-settlement origin. Per-node y already exists (`StreetNet.y`) |
| Body budget | `capWorld` 96 shared by everything (`Crowd.java:32`) | One world pool across Carillon and the villages, nearest first (village bodies would otherwise eat Carillon's) |
| Events by district | `WORKS`, `QUAY_D`, `MARKET` constants | Events pick by station or plot tags ("a house with a door", "near water") |
| Skins | Per record index | A pool by hash (reuse the 460 by `look`), or a runtime skin composer |
| Districts, names, sound | `Recognition.java:156-162`, `Soundscape.java:26-27` | Per settlement |
| Spawn rule and civic repairs | `CityHooks.inCity` / `isCivic` (one city) | A village predicate; no civic protection in the wild |

---

## (c) The city's generation (`city/` + `city/kit/`)

### A grammar for plots, not for settlements

| Stage | Where | What |
|---|---|---|
| Layout (offline) | `tools/city/plan.py` (2,417 lines, numpy, `SEED = 0xCA2111_0A7E`) | Semi-procedural but tuned for one city. `district_of` is hard-coded by radius, river and angle (plan.py:161-178). Street lattices per district (469), plots packed along frontages (1023-1240), storey targets, merchant towers, courts, crossings, graphs, relief. Wall radius 240, river from `CityBasin.RIVER` (`terrain/api/CityBasin.java:29-36`: "Carillon is one designed city, so this is fixed, never per seed") |
| Props (offline) | `tools/city/props.py` (1,128 lines) | 841 props: line 261, stack 197, planter 86, display 61, bench 60, cart 53, stall 34, heap 30, well 17, hay_cart 16, net 10, notice 8, tavern_table 6, fountain 2 |
| The plan in game | `city/CityPlan.java:22-41, 77-140, 142-163` | `data/awaken/city/plan.json`, `SCHEMA` 2, read once from the jar, hash pinned by a GameTest. A 512 x 512 raster at origin −256 (kind, district, lift per cell; 22 ground kinds, 34-36). 754 plots (house 680, shop 30, route 30, tavern 14; 74 enterable), 28 landmarks, 7 districts (38). `Plot` record: id, district, box, front, storeys, roof, ridge, pitch, eave, top, lift, door, use, enter, variant (77-83). Roofs: GABLE, HIP, MANSARD, FLAT, SAW (85) |
| Placing | `city/City.java:52-67`; `city/CityLayout.java:10-63` | Plan + site → layout; pieces indexed per chunk with margins (`PLOT_MARGIN` 3, `MARGIN` 2, towers 4). Immutable, so worldgen threads may read it |
| Painting | `city/CityFeature.java:27-41`; `city/CityPainter.java:25-85`; `city/CityBuffer.java:14` | A feature at `top_layer_modification`; a no-op outside the reach. Pure painter: the same layout, area and seed always give the same buffer. Buffer ry −16..152 relative to the street |
| Writing | `city/CityWriter.java:25-97, 110-190` | Straight into proto-chunk sections, never `setBlock` (G0 measured it 2-14x slower). Carries the plate down to rock (`MAX_FILL` 48). Sweeps neighbour vegetation, removes orphaned tree groups |
| Pregen | `city/CityPregen.java:41-47` | Window 320 chunks, deadline 300 s, margin 2 chunks, then a 1,200-tick hold ticket. SPEC: 1,708 chunks in about 14-15 s |

### Houses and facades (`city/CityGrammar.java`)

- `style(Plot)` (53-127) picks one of seven district palettes, plus `millStyle` (130-134), by variant and id hash. A
  `Style` is a record of materials, three course profiles, window vocabulary, boxes, shutters, roof and cap, jetties,
  chimney, crow steps, dormers, trellis share, shopfront and loading door (45-49). Bellwatch: plaster and timber, slate.
  Quay: brick and mud brick warehouses. Market: coloured plaster, clay, copper and bronze roofs. Terrace: calcite and
  quartz. Works: brick and cinnabar. Garden: white plaster and birch, green roofs. Wallring: cobble and timber.
- `plot()` (396-427): a masonry ground storey (rows 1-5), 1-5 upper storeys of 4 rows each (`plan.json` storeys: 3 → 325
  plots, 2 → 181, 4 → 179, 5 → 55, 6+ → 14), a roof, a chimney, then `CityFacadeLife.paint` (awnings, trade signs on
  brackets, banners, flagpoles, wall lanterns: `city/CityFacadeLife.java:22-33`).
- `faceClass` (376-394) reads the **plan raster in front of each face** (street, alley, yard, garden, a narrow slot) to
  choose F_NONE, F_BLIND, F_RUN, F_BACK or F_STREET. So the grammar needs a plan-like raster round every plot, not
  just the plot.
- **Interiors are not built.** An enterable plot gets air in rows 1-4 with a floor at row 5 (585-588), the same for each
  upper storey (636-638), panes instead of kit windows (575, 669). It gets no stairs, furniture or light. Closed plots are
  solid `CORE` (589, 640). B4's four full interiors and lit glimpses (SPEC §16.0 decision 27) are not done
  (`docs/carillon/STATUS.md:21`). The only furniture the city places is the citizens' outdoor stations
  (`citizens/CitizenWork.java:456-510`).
- Street life: `city/CityProps.java:10-35` paints props into open air only. Stalls are painted shut and opened by their
  keepers. Lit windows: `city/CityLights.java` (a seeded evening per house; the kit's `lit_window` has no light source;
  about 0.05 ms a tick).
- Protection: `city/CityProtection.java:54-` denies break, place, use and explosions where `CityHooks.isCivic`, which
  means inside the wall (`city/CityModule.java:103-104`). It has a config switch and spares creative players.
- Kit: 51 blocks in `city/kit/` (roof tiles, plaster, timber, ashlar, city bricks, flower box, shutter, drainpipe,
  trellis, lit window, loading door, hoist beam, rope, chimney bricks, awning, signboard, sign bracket, stall counter,
  wares, crates, sacks, benches, planters, wells, carts, nets, hang line, wall ivy, arrow loop, portcullis, brazier...).
  All are `noLootTable` (`city/kit/CityKit.java:28-30`) and none has a block entity.

### Climbing affordances (what the Ascent reads)

Tags are filled by the city (`src/main/merge/city/data/awaken/tags/block/`): `runner_ledges` (19: flower_box, awning,
dark_oak_slab, spruce_trapdoor and 15 vanilla stairs, e.g. stone brick, spruce, dark oak, brick, cobblestone, mud brick),
`runner_climbs` (7: drainpipe, trellis, rope, ladder, hanging_ladder, line_pole, wall_ivy), `runner_lines` (hang_line),
`runner_soft` (hay_block, awning, hay_cart) and `runner_swings` (hoist_beam, sign_bracket, flagpole). The grammar puts
holds only in odd rows: hoods at row 3, the cornice at 5, a window box and a floor course every storey. Courses run
unbroken across a row of houses. Every street face has a drainpipe, trellis or hoist rope at most 12 apart
(`city/CityGrammar.java:11-30`). Because vanilla stairs are in `runner_ledges`, the stair eaves of vanilla village
houses are already ledges.

### What a village can take from it

Reusable as they are: the painter → buffer → writer pattern, the `Style` record idea, the kit blocks, `CityFacadeLife`,
`CityProps`, `CityLights` and the climbing-grid rules. Reusable with changes: `CityGrammar.plot()`, if a small plan-like
raster (kinds and lift) is generated round each village and `Style` is keyed by biome as well as district.

Not reusable: `CityPlan` (a singleton from the jar), `tools/city/plan.py` (offline, one city), the single `streetY`, the
fixed 512 raster, and the civic-only kit (it drops nothing when broken in the wild). A village needs a Java layout
generator (a few streets, plots on frontages, a square) that writes the same records, and terrain-following lift
instead of a carved basin.

---

## (d) Other people in the world

### Wayfarers and their "camp" (`living/wayfarer/`)

- Entity `living/wayfarer/Wayfarer.java:34-126`: a `PathfinderMob implements Npc`, registered in
  `living/LivingContent.java:80`. **A persistent, saved entity** (`setPersistenceRequired`, 41; saves `awaken_who` and
  `awaken_scene`, 115-126). Vanilla goals: float, a water-avoiding stroll at 0.6, look at the player, look around
  (55-61). It strolls a few blocks round a home (`become`, 72). Invulnerable (100) and never despawns. It leaves once its
  scene is over and no player is within 16 (94-98).
- The people are in code (`living/wayfarer/Wayfarers.java:26-35`): Wren, Hettie, Ilse, Gus, Pell, Hale, Marta, each with
  two idle lines.
- Scenes (`living/wayfarer/Scenes.java:42-81`, saved `awaken:living_scenes`, schema 1). **Residents:** Ilse and Hettie at
  "the Wayfarer camp", which is only two entities on the dry land nearest a nominal column, 450 up the High Gate road in a
  Carillon world or 300 north in a classic one (59, 353-431). **No blocks are built there.** Hale stands on Carillon's
  `quartermaster` spot (428-431). **Found scenes:** every 25 minutes outside (57), 64-160 away (58), kinds cliff, crag,
  site, road or camp (256-280), in loaded chunks only, never in a Hearth, the city reserve or water (265). Markers are
  `scene`, `site`, `foot`, `camp` and `spot:<city spot>` (110-122).

### Quests are data (`living/quest/`)

- `data/<ns>/awaken/quest/*.json`, loaded by a reload listener (`living/quest/QuestBook.java`, `FileToIdConverter.json("awaken/quest")`).
  There are 8 chains: caravan_road, hermits_horn, moth_lights, orens_first_steel, pells_message, ten_sheep_short,
  the_last_delivery, wrens_rope.
- `living/quest/QuestDef.java`: a chain has title, band 0..4, requires, scene kind, giver, steps, relic, finish, summary
  and ask. A step has objective, text, start and done lines, reward, `needs` + `needs_text`, `spawn` (entity, count, at
  a marker) and `move`. **Objective types:** talk, reach, kill, collect, deliver, use, discover (69). A chain is
  rejected if a line contains an em dash (79-82).
- The reward is `progression/Reward.java:13-24`: levels, stat points, a stat, a power, an item, perks. **No coin.**
- `QuestState.MAX_ACTIVE` 5 (`living/quest/QuestState.java:19`). `reach` uses scene markers (`living/quest/QuestEngine.java:285-291`).
- Any module's NPC can give or advance a chain through `living/api/Quests.talk(player, who)` (35-43). Carillon's named
  citizens already do (`citizens/CitizensModule.java:108-113`).
- Example: `caravan_road.json` (Marta: kill 3 pillagers spawned at the scene, reach the site, kill a vindicator spawned
  at the site, talk to Marta; relic `caravan_writ`).

### Bounties (`living/bounty/`)

There are 10 notice templates (`data/awaken/awaken/bounty/`). `BountyDef`: a band, one kill or collect objective, a
share of a level and essence, "coin once Carillon's economy exists". Three notices at Hale's post, no expiry
(`living/bounty/Bounties.java`).

### World Pulse happenings (`living/pulse/`)

- `living/pulse/Pulse.java:41-46`: saved `awaken:living_pulse`, schema 1. `NEAR` 256, `TELL_RANGE` 512, the first one
  after 2 min, gaps of 90 s when empty and 10 min when busy, at most 3 near a player. It stays quiet in gates, in a
  Hearth and in the city reserve (172).
- Kinds are data (`data/awaken/awaken/happening/*.json`, `living/pulse/HappeningDef.java`); behaviour is code
  (`living/pulse/Behaviours.java:36-44`). **Nine are built:** beast_duel, caravan, carrion_circle, deep_tide,
  great_migration, murmuration, rolling_storm, stampede, wild_hunt. Tells: haze, dustline, flock, circle, storm, ghost,
  fog, lanterns, roar (`HappeningDef.java:55`).
- **The only people happening is the Caravan** (`living/pulse/People.java:30-80`): a vanilla `WanderingTrader` named
  "Caravan Master" and 2-3 leashed trader llamas walk 140 blocks over 6 minutes. 40% of the time 3-4 pillagers ambush
  it halfway, with a raid horn. If the raiders die and the master lives, each player within 40 gets 0.15 of a level and
  4 emeralds.
- Not built (planned in `docs/living-wilds/PLAN.md:376-392`): Travelling Merchant, Rival Hunters, Ambush, Refugees,
  Festival, Pilgrims, Runners' Relay. Phase G (settlers, your village's name, roads, caravans on roads:
  `docs/living-wilds/PLAN.md:480-495`) has no code (no `living/village` package).

### Camps, raids and lairs (event primitives)

- `living/camp/Camps.java:43-53`: riftborn camps, hollow dens and blight nests. `BUILD` 96, `WAKE` 40, `SLEEP` 104,
  `CLEAR_DAYS` 5, `MAX_TIER` 5. **A tier-4 camp raids the nearest Hearth, or the Wayfarer camp, within 800** (402-408).
  It is warned a play-day ahead by toast (387-393). The raid comes at 14000-14400 (226): 4 + tier creatures appear 24
  blocks short and path to the target (411-428).
- `living/camp/CampPainter.java`: builds over natural ground and into air only, records every block so a cleared camp
  turns to ash, at most 3 light sources. This is a template for "a burned village" that can be undone.
- Lairs: `world/fauna/lair/LairStructure.java:23` (a real `Structure`, one set per beast, about one per 1,800 blocks,
  11 `has_lair/<beast>` tags) and `world/fauna/lair/LairBuilder.java:37` (code-built per chunk from one seeded
  blueprint). This is the destination for a kidnapping.
- Regions: `living/region/RegionMap.java:16-28`. Hearts on a 900-block grid, the Commons within 700, bands 1-8, 8-20,
  20-45, 45-80, 80-150. A natural slot for "one settlement per region".

### The Hearth's town (`hearth/town/`) as an NPC system

- `hearth/town/TownNpc.java:41, 88-94`: a `PathfinderMob implements Npc` (registered `townsfolk`,
  `hearth/HearthContent.java:155`). Vanilla goals: float, gather, **open doors**, move towards its restriction, stroll at
  0.45, look. Home radius 20 round the Anchor (`hearth/town/Town.java:71, 185`). It cannot be hurt and is saved. "No
  beds, no jobs, no assignments."
- `hearth/town/Townsfolk.java:28-40`: 5 data files (mira_scout, oren_blacksmith, sun_healer, teodor_archivist,
  vael_rival). Each has a name, role, `arrives_at` rank, `leaves_at`, a line per rank and chatter. They arrive by the
  household's highest online rank (`Town.java:94-122`). **Nothing links this to Carillon renown**; SPEC §16.0 decision 8
  ("at Trusted renown each can move to your Hearth") is not built. Services: Oren tempers gear, Mira gives a rumour
  (`hearth/town/Rumours.java`), Teodor reads the Codex, Sun heals (`Town.java:338-393`).
- Requests: 96 request files; town standing Newcomer 0, Known 6, Trusted 18, Valued 40, Beloved 80
  (`hearth/request/RequestRules.java:170-175`).
- Oren's cottage: `hearth/guide/CottagePlan.java:24-36, 50-86`. A 9 x 9 timber cottage with a bed, chest, crafting
  table, stove, books and lanterns, in **10 biome palettes** (meadow, taiga, birch, jungle, savanna, desert, badlands,
  swamp, mangrove, cherry). The palette is chosen by `#awaken:cottage/<plan>`, a tag every overworld and Awakened biome
  has (`hearth/guide/Cottage.java:306-313`). It is raised a few blocks a tick. `hearth/room/RoomScanner.java` finds rooms
  in a budget.

### Opening and Registry NPCs

Kestrel, Bram, Oren, Mira, Teodor and Sun are **named records in Carillon's roster** (`citizens.json` "named"): ordinary
`CitizenBody` with townsfolk skins, who answer chains through `Quests.talk`. The Registry scene
(`opening/OpeningRegistry.java`) is a timed stub with no Bram entity (no match for "bram" or "kestrel" in `opening/`).
Hale is a Wayfarer resident. Oren, Mira, Teodor and Sun can exist twice: as Carillon citizens and as Hearth `TownNpc`s.

### Renown, economy, trade

- `core/api/EconomyHooks.java`: **never installed** (no `EconomyHooks.install` call). `balance` returns 0 and `pay`
  returns false.
- `core/api/RenownHooks.java`: installed by citizens with `witness` and `tier` only (`citizens/CitizensModule.java:58,
  156-168`). `standing(faction)` always returns 0.
- Trade exists only as vanilla `MerchantOffers`: the gate market rooms (`gates/run/Rooms.java:506-560`: essence shards
  for food, golden apples and torches; emeralds or gold for shards), the Broker boss (`mobs/entity/BrokerBoss.java:44`
  implements `Merchant`) and the Caravan's trader. Carillon's stalls are scenery with a stock byte; citizens "buy",
  players can't.

---

## (e) Reuse map for villages

| Piece | Where | Verdict | Why |
|---|---|---|---|
| Ledger (schedules, abstract walking, leave-early) | `citizens/Ledger.java` | With changes | Generic and very cheap. Needs per-settlement instances and world coordinates |
| Schedule format (segments, acts) | `citizens/Roster.java:43, 445-468` | As is | A clean data shape for any job |
| Place kinds, spots, capacities, stations | `citizens/Roster.java:45-135` | As is | The vocabulary a village needs (door, stall, anvil, wash, line, well, tavern, mill, mooring) |
| Roster generator | `tools/city/citizens.py` | Not reusable | Offline Python for one plan. Port its rules to Java |
| Saved data | `citizens/CitizensData.java` | With changes | One set per world, `byte[8]` districts. Needs a settlement list and a new schema version |
| Street graph runtime | `citizens/StreetNet.java` | As is (per instance) | Any node and edge graph; baked columns; closable edges |
| Bodies and crowd | `citizens/Crowd.java`, `CitizenBody.java` | With changes | Remove the singleton; share one world cap |
| Jobs that change the world | `citizens/CitizenWork.java` | With changes | Lamps, stalls, laundry, bells, masons and furnishing are generic over stations; the Choir bell and `CityPlan` lookups are not |
| Clips, props, renderer | `citizens/Act.java`, `Props.java`, `client/CitizenRenderer.java` | As is (skins with changes) | Skins are per record index |
| Speech | `citizens/Lines.java` | As is | Add village pools. Note `{district}` |
| Recognition and gossip | `citizens/Recognition.java` | With changes | Per-settlement memory; district names |
| City events (burglary, fire, cart, pickpocket) | `citizens/CityEvents.java` | With changes | The fire (trapped, bucket line, scorch, rebuild) is the half-burned village in miniature, but it picks by Carillon district |
| Client critters, glow, sound | `citizens/client/` | With changes | Built from `CityPlan`; CityGlow is generic |
| Plot grammar and styles | `city/CityGrammar.java` | With changes | Needs a plan-like raster round the plot; key styles by biome |
| Painter → buffer → writer, pregen | `city/CityPainter.java`, `CityBuffer.java`, `CityWriter.java` | With changes | The pattern is right (pure, per chunk, straight to sections); it is bound to one `CityLayout` |
| Layout generator | `tools/city/plan.py` | Not reusable | Offline, one designed city |
| Kit blocks | `city/kit/` | With changes | They look right but drop nothing (civic) |
| Facade life, props, lit windows | `city/CityFacadeLife.java`, `CityProps.java`, `CityLights.java` | As is | They read `Prop` and `Plot` records |
| Climbing tags | `src/main/merge/city/.../runner_*.json` | As is | Any build using these blocks is climbable |
| Civic protection | `city/CityProtection.java` | Not for the wild | All or nothing inside the wall |
| Oren's cottage (biome palettes, furnished) | `hearth/guide/CottagePlan.java`, `Cottage.java` | As is | The one furnished home; 10 palettes for every biome |
| Room recognition | `hearth/room/RoomScanner.java` | As is | "Does this house have a room" for settlers |
| Wayfarer entity and scenes | `living/wayfarer/` | As is | Saved, persistent NPCs with markers and Horizon lines |
| Quest engine | `living/quest/` | As is | Data chains; `Quests.talk` for any NPC; spawns at markers |
| Pulse | `living/pulse/` | As is (add behaviours) | Data kinds, code behaviours. Caravan exists; refugees and festival do not |
| Camp raid | `living/camp/Camps.java:387-428` | With changes | Targets a Hearth or the Wayfarer camp; add villages as targets |
| Camp painter (reversible blocks) | `living/camp/CampPainter.java` | As is | Records what it sets; can be undone |
| Lairs | `world/fauna/lair/` | As is | The kidnapping destination |
| Village-nearby check | `descent/well/Well.java:113-123` | As is | `hasStructureChunkInRange`, no chunk loads |
| Townsfolk | `hearth/town/` | With changes | Rank-gated arrivals and services; no jobs or schedule |
| Economy | `core/api/EconomyHooks.java` | Not built | No coin anywhere |

### Hard constraints a village design must respect

- **One city singleton.** `Citizens.active` (`citizens/Citizens.java:23`), `City.layout()` (`city/City.java:32-37`),
  `CityPlan.get()` and `Roster.get()` all come from jar resources, never data packs. The citizens start only when the
  built plan's hash equals the roster's (`citizens/Citizens.java:93-97`). Changing `plan.json` re-pins a GameTest hash.
- **Overworld only:** `server.overworld()` (`citizens/Citizens.java:89`); `CityFeature` checks `Level.OVERWORLD`
  (`city/CityFeature.java:29`).
- **Entity budgets:** bodies ≤ 40 per player and ≤ 96 per world, made at 56, kept to 64, ≤ 8 spawns a pass,
  `MAX_PLAYERS` 16 (`citizens/Crowd.java:26-32`). Path slots 4 or 6 (62). The brain throttle (`CitizenBody.java:423`).
  Critter caps (`client/CityCritters.java:50`). Camps have ≤ 3 lights. Pulse ≤ 3 happenings near a player
  (`living/pulse/Pulse.java:46`).
- **Server budgets (SPEC §16.10):** the whole city ≤ 2.5 ms a tick on average; the ledger ≤ 0.5 ms a tick; bodies
  ≤ 1.5 ms a tick (§16.5). Client: 40 citizens ≤ 1.2 ms of a ≤ 10 ms p50 frame at the busiest market. Villages share
  these.
- **No per-tick allocation in hot paths** (CLAUDE.md). The ledger, nets and work "allocate nothing per tick"
  (`citizens/Ledger.java:20-21`, `StreetNet.java:10-15`). The next-hop bake is capped at 3 a tick.
- **Worldgen thread rules:** the painter is pure and per chunk. A piece is ≤ 14 x 14 with a 2-block margin. Write into
  sections, never `setBlock` (SPEC §16.2; `city/CityWriter.java:25-28`). No block entities in bulk. ≤ 4-5 light
  sources a chunk; light at night drawn or emissive (SPEC decision 26). The layout is immutable once built
  (`city/CityLayout.java:10-17`).
- **Loaded chunks only** for runtime edits and searches: `CitizenWork` reconciles in loaded chunks
  (`citizens/CitizenWork.java:36-44`); Scenes "never generates terrain" (`living/wayfarer/Scenes.java:52`).
- **Saved data is versioned and world-global:** `awaken:citizens` v1, `awaken:city` v1, `awaken:living_scenes` v1,
  `awaken:living_pulse` v1, `awaken:living_camps` v1. Bodies and critters are never saved; Wayfarers and townsfolk are.
  New structures need a `schemaVersion` (CLAUDE.md).
- **Keep-outs other systems expect:** the city reserve (`CityBasin.reserved`), a Hearth's radius (48 + 8 per tier above
  I, `hearth/HearthRecord.java:241-242`; Pulse and Scenes stay out, the Hearth calms its land to + 96, SPEC §21.0.2),
  gates (`Overworld.untouched` refuses built ground).
- **Citizens are hurt, never killed** (SPEC §16.0.7; `citizens/Citizens.java:186-198`). Wayfarers and townsfolk are
  invulnerable. Powers never hurt an `Npc` (`powers/Combat.java:53-66`). A kidnapping must be a role or a scene, not a
  death.
- **Events need players present:** planned city events start only while someone is online (`citizens/CityEvents.java:83`).
  Every timer counts play-days (`core/PlayDays.java`).
- **Data rules:** quest and bounty lines have no em dash (`living/quest/QuestDef.java:79-82`); `Act` and `Move` ordinals
  are append-only (`citizens/Act.java:8`); quest bands 0..4 (`QuestDef.java:83`).
- **Server authority and multiplayer:** carts and boats run on the world clock so every client agrees
  (`client/CityCritters.java:31-43`). Keep any village client-only life clock-driven, and multiplayer-safe (the
  owner's guild-server ambition).
