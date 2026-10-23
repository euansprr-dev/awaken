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

A quick tour: `/awaken admin awaken`, `/awaken gate open C clockwork_foundry`, walk into the core (or
`/awaken gate enter <id>`), clear floors, read the run HUD on the left, open K → Gate Map for the run summary and the
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

The last floor is always the Lord's: start → hall → antechamber → a 3×3 arena (round or octagonal, 17 high).
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

Run HUD (left: gate, floor, Satchel, kills, floor clock, pressure, noise, collapse countdown, twist hint), Satchel screen
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
The GameTest server has no datapack dimensions, so the inside of a run is covered by the pure tests above, not by a
walking test.

## Decisions (not in SPEC yet)

- Director spawn chance per pass: 100% / 30% / 8% / 0% at 0 / 1 / 2–3 / 4 standing gates; spots ≥ 96 blocks apart.
- SOVEREIGN players can roll SOVEREIGN gates (the "one above" of S).
- Claim ids carry a run index after the first run (`gateId:floor:player#n`) so a re-entered gate can pay again.
- Retreating through floor 1's entrance forfeits the Satchel (the gate keeps it).
- Twists are counted as "seen" when you come out of that gate alive (extract or clear), once per gate.
- "Comfort" for `floors_pushed_past_comfort` is the median of your last five extraction floors (2 before any).
- Gate interiors are protected: blocks the themes are built from can't be broken by non-creative players; explosions
  never break blocks in the gate dimension.
- Volatile and Echoing behaviour (burst / rise once) is implemented here for gate monsters (`MobRole.gateId` set).
- Breaks are deferred while an online player is inside that gate.

## Known gaps / requests

- `Profile.day` has no public mutator: the Day's End gate tally round-trips the profile through `Profile.CODEC`
  (`Claims.countGateToday`). Request: `Progression.addGateToday(ServerPlayer)`.
- Mobs: a BREAK-role spawn from the fallback spawner is a plain zombie; gates scale it up (×8 health, ×1.5 size) only when
  its max health is under 80. The mobs module should make BREAK a real roaming Lord and must not duplicate the gates'
  Volatile/Echoing death behaviour or add a second boss bar for BREAK.
- Hearth: overflow from claims drops at the player's feet (no Hearth chest API yet).
- Veil: scouting should call `Gates.markScouted(player, id)` (counts `gates_scouted`) and may show `Gates.preview(id)`.
