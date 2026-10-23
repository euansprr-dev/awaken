# The Above (module `above`)

Precursor ruins and machines, Essence power, exosuit Frames, the Clone Vat and the Halo (SPEC section 10).
Package `dev.awaken.above`. Feature flag `AwakenConfig.ABOVE` (machines, the Scanner, Frames and the Clone Vat go
quiet when it is off; worldgen is data and stays).

The loop is **salvage → scan → build**. No tech tree screen, no pipes, no cables, no wiki: a broken machine in a ruin
is read with the Scanner, its blueprint shows up at the Fabricator with 3–5 visible parts, and every machine draws from
any Essence Reactor within 32 blocks.

## What's in it

### Ruins (overworld worldgen)
One code-built structure type `awaken:precursor_ruin` (single piece, deterministic from its seed, no template files) in
five data-driven structures, each with its own structure set:

| Structure | Where | Placement | What's inside |
|---|---|---|---|
| `precursor_vault` | forests, taiga, plains, hills... | random_spread 40/14 | squat bunker; sealed **Precursor Door** (rank by distance from 0,0: D < 1200, C < 2800, B < 5000, else A); 1–3 machines, loot, 50% log |
| `precursor_spawn_vault` | anywhere | `awaken:precursor_spawn_ring` 240–360 blocks from the dimension origin (where spawn is searched) | the guaranteed vault near spawn: door hums at **D-rank**; antechamber chest always holds a **Scanner**; Gate Scanner + one more machine; always a log |
| `precursor_sky_platform` | any land/ocean surface | random_spread 44/16 | disc at Y 262–269 with a **glowing underside** and keel, glass cabin, machines, loot, 60% Ascendant log |
| `precursor_ocean_lab` | oceans | random_spread 36/12 | void-glass dome on the sea floor, dry inside, **Pressure Membrane** airlock (walk through, water stays out), 50% Sleeper log |
| `precursor_monolith` | desert, badlands | random_spread 28/10 | brass-banded obelisk with amber lights; ladder down to a sealed chamber; 70% Keeper log |

Vaults and monoliths flatten the ground around themselves (NeoForge `PieceBeardifierModifier`). Machines in a ruin are
picked by weight without repeats (`MachineType.scanWeight`). All five are in `#awaken:precursor_ruins`.

Ruin blocks: `broken_machine` (state: machine, scanned, facing), `precursor_door` + `precursor_door_core` (state: rank),
`precursor_log_terminal` (state: pool = ascendant/keeper/sleeper/unknown/halo, facing), plating, panel, brass trim,
glow panel, warning light, void glass, membrane.

**Doors.** The core checks the nearest player every 2 s; if their rank is high enough it hums (sound + end-rod sparks).
Touching any panel asks the core: high enough and the whole door folds away (`awaken:precursor_doors_opened`, new
thing), otherwise "Come back at C-rank."

**Logs.** A terminal holds a pool, not a fixed log: reading it gives you the first log of that pool you haven't read
(else a random one again). The Ascendants' two Halo logs (`a04_halo`, `a05_regret`) are only in the Halo's archive.
Reading shows the log in a Signal window and adds it to the Codex (`awaken:precursor_logs_read`).

### Materials and power
- `precursor_scrap` (ruin loot, salvage), `precursor_circuit` (2 scrap + 2 redstone + copper → 2), `precursor_alloy`
  (8 scrap around a Lord's Heart → 8), `precursor_void_glass` (8 glass + scrap → 8).
- `essence_cell`: holds 0–1000 essence (component `awaken:essence`). Use: feed it one essence shard (100); sneak-use:
  fill it from the shards you carry. Frames draw from the cells you carry.
- **Essence Reactor**: right-click with shards (whole stack) or a cell (empties into it; sneak-use fills the cell
  from it). Holds 20,000. Powers every machine within 32 blocks (straight line between block centres), nearest reactor
  first. Machines don't drain while idle; they pay per use. Reactors index themselves (`Power`), machines cache the
  reactors in range and rebuild only when a reactor is added or removed anywhere.

### The Scanner (`precursor_scanner`)
Crafted from vanilla parts (amethyst, copper, glass pane, redstone) and found in the spawn vault.
- On a broken machine: reads the blueprint (Codex + Fabricator), marks the block scanned, counts
  `awaken:machines_scanned`, new thing on a new blueprint. The first read also teaches the Essence Reactor.
- On a log terminal: reads the log.
- Either way the ruin is recorded (pylon destination, no more omens for it).
- Hold it up at the sky and sweep: beeps rise as you face the nearest undiscovered ruin (within 2,500 blocks); facing
  it shows the omen ("Something hums under the water.") with a vague distance and a compass point.

### Fabricator and machines
The **Fabricator** is crafted (2 scrap, a circuit, iron, a crafting table) and needs no power. Its screen lists only
blueprints you know, each with its parts as icons and have/need counts. Building counts `awaken:machines_built` (new
thing the first time per machine). Every machine is in `#awaken:machines`.

| Machine | Use | Cost |
|---|---|---|
| Gate Scanner | reads every gate within 2,000 blocks (`GateHooks.near`): rank, theme, bearing, breaks-in, omen; Signal summary; marks them on the Gate Map tab (radar + list) | 25 per reading |
| Gate Stabiliser | holds any gate a scanner read: up to 3 play-days per charge (`GateHooks.stabilise`), one charge per gate per play-day | 100 per day held |
| Waypoint Pylon | travel to your other pylons (any dimension), your Hearth (`HearthHooks.hearthOf`), and ruins you found | 50 + 1/32 blocks, max 400 |
| Deep Lift | rides to any stratum with a lit Waystone (`DescentHooks.litWaystones/ride`), no Strain | 100 |
| Frame Forge | builds Frame pieces and modules; sockets/removes modules on the Frame you wear | 50 per build |
| Clone Vat | bind it; grows a clone (300 essence) when powered; once per play-day a death anywhere wakes you in it | 300 per clone |
| Dream Recorder | must sit within 3 blocks of a bed; replays a recorded dream (`DreamHooks.replay/recordedCount`) | 100 |
| Signal Relay | within 48 blocks of your Hearth: once per play-day asks the Signal for a fresh Directive (`Directives.tryOffer`) | 150 |
| Recalibrator | returns every stat to 5 and refunds the points | 100 + 25 per point |
| Observatory | needs open sky: lists sky platforms within 3,000 blocks; at B-rank shows the Halo (`awaken:story/halo_seen`) | none |
| Launch Ring | core + 16 `launch_ring_frame` around it (5x5 outline, same height; missing spots sparkle); with the Halo seen and 2,000 essence, launches you | 2,000 |

All machine screens are one glass panel (`Panel` built on the server, `PanelScreen` on the client); every button is
re-validated on the server (distance, power, parts, rules).

### Frames (exosuit)
`precursor_frame_helmet/chestplate/leggings/boots`: armour (diamond-plus, toughness 2.5) with our own equipment asset
`awaken:precursor_frame` (64x32 generated textures). Slots: helm 1, others 2. Modules (built at the Forge):

| Module | Fits | Effect | Essence |
|---|---|---|---|
| Jet Boots | boots | hold jump in the air (after releasing it once) to thrust; client-predicted, server pays and forgives the fall; +6 safe fall | 1 per tick of thrust |
| Shield Emitter | chest | regrows an 8-point (4-heart) absorption shield every 20 s | 40 per charge |
| Stealth Field | legs, chest | sneak and hold still: invisible | 1 per 0.5 s |
| Magnet | chest, legs | pulls items within 7 blocks | 1 per pull |
| Night Vision | helm | night vision | 2 per 10 s |
| Step Assist | boots, legs | step height +0.6 | none |

### The Halo (`awaken:halo`)
Dimension type: fixed time, black sky with full stars and a low sun, no weather, End music, no beds. Generator
`awaken:halo_station` builds the station chunk by chunk as a pure function of position (`HaloLayout`): a glass-domed
hub with the **return pad** and the Ascendant **archive** (5 terminals, pool `halo`), four glass spokes, and a ring
corridor (radius 34–40) with a glowing underside, five broken machines to scan (Observatory, Launch Ring, Clone Vat,
Signal Relay, Recalibrator) and two loot caches (`chests/halo_cache`).
- Low gravity while there (`GRAVITY` ×0.38, +12 safe fall); removed on leaving, re-applied on login/respawn.
- Drift off the station and it catches you (below Y 60 → back to the arrival point).
- Client sky: the planet turning far below (textured disc that follows the camera, kept inside the fog), and Sky
  Leviathan sightings every 3–6 minutes (server-scheduled, `awaken:leviathan_sightings`).
- First arrival: `awaken:story/halo_reached`, new thing, and the Signal REVELATION from
  `revelation/halo_found.json` (guarded by counter `awaken:revelation/halo_found`).
- The return pad takes you back to the Launch Ring you came from (or world spawn).

## Public API (`dev.awaken.above.Above`)
- `consumeCloneSave(ServerPlayer)`: call from death handling. True when the player has a bound, grown Clone Vat and
  hasn't used it this play-day; consumes it, and they'll wake in the Vat. Repeated calls during the same death return
  true again without spending a second save. **Gates should keep the Satchel and Descent the Deep Pack when it returns
  true.** The Above also calls it itself (lowest priority) so plain deaths wake you in the Vat.
- `cloneSaveAvailable(ServerPlayer)` (UI only), `knows(player, MachineType)`, `data(player)` (blueprints, logs, ruins).
- Tags: `#awaken:machines` (blocks), `#awaken:precursor_ruins` (structures).

## Counters
`awaken:machines_built`, `awaken:machines_scanned` (per broken machine read), `awaken:story/halo_seen`,
`awaken:story/halo_reached`, plus `awaken:precursor_logs_read`, `awaken:ruins_found`, `awaken:precursor_doors_opened`,
`awaken:gates_stabilised`, `awaken:gate_scans`, `awaken:pylon_jumps`, `awaken:clone_saves`, `awaken:recalibrations`,
`awaken:frames_forged`, `awaken:frame_modules_socketed`, `awaken:leviathan_sightings`.

## Try it
- `/awaken admin blueprint all`: learn every blueprint. Craft a Fabricator, or take one from the creative tab.
- `/awaken above ruin locate`: nearest ruin of each kind and the spawn vault (click to fill a `/tp`). `/locate
  structure awaken:precursor_vault` also works (not for the spawn vault: custom placement).
- `/awaken admin halo`: reveal the Halo and go there.
- `/awaken admin rankup` to make a sealed door hum; `/awaken admin awaken` first if your profile isn't awakened.
- Reactor: place it, right-click with essence shards. Machines within 32 blocks light up within 2 s.

## Files
- Code: `src/main/java/dev/awaken/above/**` (client in `above/client`).
- Data/assets generator: `tools/content/above_content.py` (blockstates, models, item definitions, equipment, loot,
  recipes, worldgen, Halo dimension, tags, lang, sounds). Textures: `tools/art/specs/above.py`.
- GameTests (`AboveTests`, 10 tests): blueprint learning, Fabricator recipes (3–5 parts, no missing items, tag
  membership), reactor range and draw/cache rules, Recalibrator maths, Clone Vat once per play-day, essence cell
  charge/discharge, ruin layouts (machine counts, unique types, door core, airlock), all five ruins generated in-world
  through the `/place structure` path, Halo dimension data + generator column, AboveData codec + Halo layout.

## Decisions
- Machine screens are payload-driven panels instead of container menus: no slots needed (parts are counted from the
  inventory), one screen class for all, server re-checks every button.
- A broken machine counts towards `machines_scanned` once per block (so the Directive stays doable after you know every
  blueprint); a new blueprint is the "new thing".
- Machines pay essence per use rather than draining while idle: nothing to babysit.
- The Clone Vat stores whether its clone has grown on the block entity so a death far away only needs its chunk.
- The Observatory shows the Halo from B-rank ("late in Act II"). The Launch Ring needs the Halo seen.
- Terminals hold a faction pool so every reader finds new logs in any order.
- The Signal Relay can't add a fourth Directive slot without a progression API (see requests); it offers a fresh
  Directive once per play-day instead.
- Breaking a reactor drops it empty (essence isn't kept on the item).

## Known gaps and requests
- **progression**: no public way to add unspent points; the Recalibrator refunds through
  `RewardHooks.apply(Reward with stat_points)`. A `Progression.addUnspent(player, n)` would be cleaner.
- **progression**: no runtime perk source. Request `Progression.addPerkSource((player, perkId) -> amount)` so the
  Signal Relay can grant `awaken:extra_directive_slot` near the Hearth as designed.
- **story**: `revelation/halo_found` is fired here, guarded by counter `awaken:revelation/halo_found`. The story
  module should use the same guard (or skip that trigger) to avoid showing it twice.
- **gates / descent**: wire `Above.consumeCloneSave(player)` into Satchel / Deep Pack death handling.
- **lucidium**: logs are recorded in `Above.data(player).logs()` and the Codex; if the Dream Journal grows a text API,
  reading a log could also write a journal page.
- The GameTest server only builds vanilla dimensions, so the Halo station is tested through its generator and layout,
  not by loading the dimension.
- Worldgen isn't switched off by `AwakenConfig.ABOVE` (structures are datapack data); ruins still generate, but
  scanning and machines do nothing while it's off.
