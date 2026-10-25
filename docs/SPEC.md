# AWAKEN — Implementation Spec (v1)

This is the source of truth for builders. The player-facing design and its reasoning live in `docs/plan.html`.
When this spec and the plan disagree, this spec wins. When something is unspecified, pick the simplest
behaviour that serves the five pillars and write it down here in the same commit.

Pillars (every feature must serve two): **Main character energy · Pulled, not pushed · Every door is different ·
Learn by touching · Awareness is power.**

Hard rules:
- Nothing runs on real-world time. Timers count **play-days** (in-game days that pass while at least one player is online).
- Never more than **3** active Directives. Never a quest list. Undiscovered things are never listed.
- A reward is granted at most once per claim id. Loot moves, it is never copied.
- **Patent rule:** something done to one hostile NPC never changes a *different* hostile NPC's stats.
  Consequences go into world state (gate rank, blight, timers, loot, spawn budgets). The Rival is authored and advances on
  the player's rank milestones only. Nightmares are chosen from authored templates by the player's own death counts.
- No language model, no network calls. Everything local and deterministic (seeded).
- Performance: p50 ≥ 110 fps in a full gate fight on an M4; server tick p95 ≤ 25 ms; ≤ 60 active combat entities;
  ≤ 3 ms/tick of main-thread work for any background job.

---------------------------------------------------------------------------------------------------------------------

## 1. Modules and ownership

| Module (package `dev.awaken.*`) | Owns |
|---|---|
| `core` | mod entry, config, shared registries helpers, `AwakenNet`, commands root `/awaken`, `Forces` tags, `PlayDays`, `Rng`, debug |
| `signal` | Signal windows (server API + client HUD), toast queue, Day's End card |
| `progression` | Level/XP/ranks/stats (`Profile` attachment), counters, Reassessment, Truths, Titles, Latents, Paths, Directives, Daily Regimen, Director (pacing + "new thing" rhythm), Status screen (4 tabs) |
| `powers` | power framework, all powers, Power Cores, energy, cooldowns, keybind casting, Five Forces interactions, combat feel (hit-stop, shake) |
| `mobs` | `AwakenMob` base, gate bestiary, Gate Lords / bosses, Hollow Court, mob scaling by level, renderers |
| `gates` | gate registry/SavedData, overworld gate block + pillar render, gate card, gate dimension + chunk generator, layouts, themes, modifiers, twists, runs, Satchel, Shrines/Seals/Caches, gate pressure, breaks + blight, claims, cleanup |
| `descent` | the Well, 10 strata dimensions + generators + rules, drops between strata, Waystones/camps, Strain, Grave Echo, the Endless |
| `lucidium` | Day Journal snapshots, dream plans, Lucidium dimension, lucidity, reality checks, dream signs, intention, WBTB alarm, manifest, nightmares, visions, Dream Journal item |
| `veil` | meditation, spirit projection (spectator-backed) + Body entity, cord, Awareness fog, spirit sight, spirits (lore NPCs), possession, Veil gates, Cord-Eaters, the Stillpoint dimension |
| `above` | Precursor ruins & sky platforms (worldgen), Scanner, Essence + cells + reactor, machines, Frames (exosuit), Clone Vat, Halo dimension |
| `hearth` | Hearth Anchor, room detection, trophies + Trophy Hall, town NPCs + Spire board, recognition, Echoes (bind/summon/roles), Training Grounds, rank trial arenas |
| `story` | prologue (Bell Crypt, death, Stillpoint, Accept), acts, Revelations, Codex board content, the Rival, endings, New Game+ / Ascension |
| `world` | shared overworld worldgen hooks (biome modifiers, structure sets) used by descent/above/story |
| `body` | the Awakened Body: soft landing, tactical sprint, slide, mantle; momentum hits and trips; `core.api.MovementHooks` |
| `terrain` | Awakened Lands (§15): the biome table, Awakened biomes and their placement, trees, ground cover, wind and falling leaves, biome looks and sound, the terrain studio commands |
| `wilds` | The Wilds (§17): Precursor wards, how you arrived (`core.api.ArrivalHooks`), biome discoveries; later spires, windways and chases |

Rules for builders:
- Touch only your module's package, your module's resources (namespaced file names), and your merge folder
  `src/main/merge/<module>/`. Shared JSON (lang, sounds.json, tags) is **merged at build time** from those folders.
- Each module has `XxxModule.init(IEventBus modBus)` (called from `Awaken`) and optionally `XxxClient.init(IEventBus)`.
  Register your own DeferredRegisters, payloads (own `RegisterPayloadHandlersEvent` listener), attachments, commands
  (as `/awaken <module> ...` subcommands via `AwakenCommands.register(...)`), and GameTests.
- Cross-module calls go through the small public API classes listed in §12. Never reach into another module's internals.
- Verify every Minecraft/NeoForge API against `/Volumes/MEDIA/Awaken/.ref` and `docs/api/*.md`. 26.3 differs from 1.21.1.

---------------------------------------------------------------------------------------------------------------------

## 2. Numbers (from `tools/sim/pacing_sim.py`, tuned 2026-10-23)

```
xpToNext(L)      = 90 * L^1.45 + 60
RANKS            = E, D, C, B, A, S, SOVEREIGN          (index 0..6)
RANK_LEVEL       = D 10, C 25, B 45, A 70, S 100, SOVEREIGN 135   (level needed to take the Reassessment)
RANK_MID         = E 5, D 17, C 35, B 57, A 85, S 118, SOVEREIGN 150  (gate content level)
SHARE            = E .28, D .16, C .065, B .22, A .60, S .55, SOVEREIGN .50   (v2, see §13; v1 was .81/.39/.25/.23/.18/.12/.12)
floorReward(r,f) = SHARE[r] * xpToNext(RANK_MID[r]) * 1.4^(f-1)
   40% arrives as kill XP during the floor (split across the floor's spawned mobs)
   60% is the floor bonus, carried as "Satchel XP" and banked only on extraction or boss kill
gate below your rank pays x0.3
home activity XP ~ 1.2% of a level per active minute (capped per minute; see §4.3)
Daily Regimen    = 0.25 * xpToNext(L) + 1 stat point
death            = lose Satchel (items + Satchel XP), keep gear & kill XP, gain Insight, Fracture 8 min (Hunter mode)
story length     = config multiplier on all XP: 2.0 (≈50 h), 1.0 (≈100 h, default), 0.67 (≈150 h)
```

Gate shape: floors = min(6, 3 + rank/2 + coin). Breaks in = max(1, 4 - rank/2 + (0|1)) play-days.
Modifier budget by gate rank E..S: 1,2,3,4,5,6; max count 1,1,2,2,3,3; Sovereign = S.
Twist: 1/8 chance, guaranteed on the 12th gate without one (per player).
Director gate rank roll: 70% player rank, 20% one below, 10% one above. Keep 1–4 open gates within 300–1500 blocks of each
online player. No theme from the player's last 3 gates, no layout from the last 2.

Second reward rhythm: the Director tracks minutes since the player's last *new thing* (new power, title, Truth true,
latent, depth record, trophy, scanned machine). After 20 min it biases: next elite drop → Power Core (+50%),
next Directive → one that completes quickly. Power Core pity: guaranteed from a Gate Lord if none in the last 3 bosses.

Mob scaling by content level `lv` (RANK_MID of the gate/stratum):
```
health = base * (1 + 0.075*lv)       damage = base * (1 + 0.04*lv)       armor += lv/12
```
Player scaling (stats, §3) and gate gear tiers keep an on-level player at roughly 3–6 hits per normal mob.

---------------------------------------------------------------------------------------------------------------------

## 3. Stats

Six stats, start at **5** each at awakening. **+3 points per level**, +1 per completed Regimen, some Truths give points.
Respec: Precursor "Recalibrator" machine (above), costs essence.

| Stat | Effect (per point unless noted) | Implementation |
|---|---|---|
| STR | +2.5% melee damage; Kinetic power scaling | ATTACK_DAMAGE multiply_base modifier `awaken:stat_str` |
| AGI | +0.4% move speed (cap +40%), +1% attack speed (cap +60%); Flow +0.05 s | MOVEMENT_SPEED / ATTACK_SPEED modifiers |
| VIT | +0.6 max health; +0.5% damage resistance (cap 35%) | MAX_HEALTH add_value; resistance in damage event |
| INT | energy pool 100 + 5/pt; Energy/Tech power damage +1.5% | read by powers |
| PER | crit chance 0.4% (cap 35%, crits ×1.6); secret glow radius 4 + PER/4 blocks; omen detail tiers at 20/40/60 | damage event + clients |
| SPI | cord length 32 + 6/pt (cap 1024); Veil time 30 s + 3 s/pt; lucidity gain +1%; Echo cap 3 + SPI/6 | read by veil/lucidium/hearth |

Energy regen: 4/s + 0.08 × INT per second, paused for 1 s after spending.

---------------------------------------------------------------------------------------------------------------------

## 4. Progression

### 4.1 Profile attachment (`progression`, synced to owner, copyOnDeath)
`level, xp, rank, unspent, stats[6], awakened(bool), path(id|null), truthsDeclared(≤3), truthsTrue(set), titles(set),
equippedTitle, latents(set), counters(map<id,long>), directives(≤3), regimen(today), lastNewThingTick, insight(map),
fractureUntil, playDay, schemaVersion`.

### 4.2 Counters
Any module calls `Progression.count(player, "awaken:<name>", amount)`. Counters drive Truths, Titles, Latents, Paths,
Directives and Regimen. Standard counter ids are listed in `progression/Counters.java`; modules add their own ids freely.

### 4.3 Home XP
Kills of overworld hostiles: 4% of a level × mob max health/20 (cap 1 level per 10 min from kills). Mining ores: 1% of a
level per ore (cap). Placing blocks: 0.05% per block (cap 1% per minute). Hard cap: 1.5% of a level per real minute
from home activity.

### 4.4 Reassessment (rank-up)
At RANK_LEVEL the Spine says "Reassessment available". Trial is taken at the Spire (hearth town) or any Reassessment
Monolith. Trials are handcrafted arenas (in the Stillpoint dimension's trial wing): D survive 3 waves; C Second Awakening
(waves + Path reveal); B boss duel; A timed descent (5 rooms, 4 min); S fight a Sovereign's echo; SOVEREIGN after the Seed.
Pass → rank++, Signal Reassessment window, town celebration, new tier of gates, aura tier.

### 4.5 Truths
Data: `data/awaken/awaken/truth/*.json` `{id, text, hint?, condition, reward}`. Offered 3 at awakening and 3 at each rank
(plus emergent ones when a hidden condition is met). Declare up to 3 → "NOT TRUE YET" → condition met → TRUE (permanent).
Conditions (`progression/Condition`): `counter_at_least`, `streak_at_least` (counter that resets on a reset-counter),
`event` (one-shot flag), `all/any`.

### 4.6 Titles & Latents
Same condition system. Titles: equip one (perk). Latents: hidden, granted automatically, announced by a Notice, recorded in
the Codex only once found. Codex shows counts like `Latents 7 / ??`.

### 4.7 Paths (Second Awakening, C-rank)
12 paths; 6 at launch + 3 later + 3 hidden. Each has `scoring` (weighted counters normalised per hour) and `hidden`
condition for hidden ones. Offer top 3 + qualifying hidden. Evidence lines quote real counters. Pick grants a signature
power + passive + aura colour.

### 4.8 Directives
Rule tables in data `directive/*.json` with `when` (context filters), `text`, `condition`, `reward`, `expires_days`.
One new offer per morning or notable event, max 3 active, no repeats within 5 play-days, ≥1 in 4 must be "fresh".
Accept with Y, ignore and it fades.

### 4.9 Daily Regimen
Each in-game morning: 3 drills tuned to Path (sprint distance, Flow seconds, kills, Training Grounds lap, meditate 30 s,
mine N). Reward per §2. Skip = nothing, unless Hardline (Penalty Zone: survive 4 min in a desert night arena).

### 4.10 Director
Runs every 1000 ticks per online player: gates upkeep, directive offers, new-thing rhythm, break warnings.
`/awaken why` prints the last 10 decisions with inputs. Novelty memory stored per player.

---------------------------------------------------------------------------------------------------------------------

## 5. Signal (UI)

Server: `Signal.send(ServerPlayer, SignalWindow)`. `SignalWindow{kind, title, body?, rows[(label,value)], footer?,
actions[(id,label)], ttlTicks, priority}`; kinds NOTICE, GATE, DIRECTIVE, TRUTH, WARNING, REASSESSMENT, REVELATION, DAYS_END.
Client: one window at a time, top-centre glass panel, letters type in fast, chime. In combat (hurt in last 5 s or hostile
within 8 blocks) windows become a compact toast and queue. Actions answered with **Y** (accept) / **N** (dismiss).
Visual: deep glass `#0F1724` at 88% alpha, edge `#7FC3FF`, corner brackets, title white, labels pale, "NOT TRUE YET" amber
`#FFCF7A`, warnings `#FF8A7A`.

Status screen (key **K**): tabs Status · Truths · Codex · Gate Map. Status shows level, rank, path, title, stats with
+ buttons when points unspent, energy, Echoes, the Spine line, active Directives, and the power loadout section
(provided by `powers` via `StatusSections`).

---------------------------------------------------------------------------------------------------------------------

## 6. Powers

Slots: 4 active (Z X C V), Ultimate (R), Movement (Left Alt). Passive powers apply when owned.
`PowerType` registry (custom registry `awaken:power_type`) with code; data numbers in the same class.
Families: Kinetic, Velocity, Flight, Energy, Shadow, Mind, Architect, Tech, Spirit. Forces: HEAT, CHARGE, GRAVITY, SPIRIT,
FROST (+ SPORES, WATER, VOLATILE, MIRROR as environmental tags).
Power Cores: item `awaken:power_core` with component `{power, variant}`. Using it learns the power (or upgrades a known one).
Movement powers are client-predicted (velocity applied locally, server validates and corrects).
Feel: hit-stop 40–80 ms on heavy hits (client time scale on the hit entity's render + camera kick), shake toggle in config.

Launch power list (≥45): see `powers/PowerCatalog.java` header for the authoritative list with numbers.

---------------------------------------------------------------------------------------------------------------------

## 7. Gates

Overworld: `awaken:gate` block structure (frame + core) at a surface position; client renders a light pillar coloured by
rank (E #F4F6FB, D #5FD08F, C #6FB3FF, B #B48CFF, A #F2A541, S #FF5A6A, Sovereign black-gold), visible up to 1500 blocks
(drawn at clamped distance beyond render distance).
Gate card shows rank, breaks-in, omen; PER ≥20 adds layout, ≥40 modifiers, ≥60 floor count; Veil scouting shows all.

Dimension `awaken:gates`: cells of 512×512 aligned to region files, cell origin = ((i mod 64)·1024, (i div 64)·1024).
Floors stacked vertically: floor f at y = 200 − 32·(f−1) (f = 1..6), each floor a grid of room slots (16-block pitch).
Generation is deterministic from `GatePlan{seed, theme, layout, modifiers, twist, floors, boss, contentVersion}`.
Rooms: start, normal, elite, cache, puzzle (some layouts), shrine (floor end), boss arena (last floor).
Everything picked up inside a gate goes to the **Satchel** (27 slots + Satchel XP). Shrine: Extract (bank + return to
gate mouth) or continue via the Seal to the next floor. Boss kill: claim + exit portal.
Gate pressure: after 4 min on a floor, a wave every 45 s, scaling; at 8 min the gate's Warden (elite) arrives.
States: BUILDING → READY → ACTIVE ⇄ OPEN → CLEARED | BROKEN → RELEASED (cell region files deleted on server stop).
Break: spill wave + roaming Gate Lord with boss bar at the gate; blight (desaturation + grey fog) within 96 blocks until
cleared; better loot. Warning one play-day ahead when within 256 blocks of a Hearth. The Gate Stabiliser pushes a break
back by play-days. After the "Break the Signal" ending no gate breaks; after "Wake up" there is no blight.

Themes (15), layouts (8), modifiers (14), twists (8), bosses: exactly as in `docs/plan.html` gate generator.

---------------------------------------------------------------------------------------------------------------------

## 8. The Descent

The Well: landmark structure within 400–700 blocks of world spawn (a Precursor ring around a deep shaft). Jumping in (or the
Well's lift) enters stratum 1. Strata = dimensions `awaken:stratum_1` … `awaken:stratum_10`, each its own generator +
biome + one rule; drops (portal pits) lead to the next. Endless = `awaken:endless` (cells, stacking rules).
Rules: 1 none · 2 wind gusts · 3 rising water every 5 min · 4 the dead rise once · 5 sound draws Listeners ·
6 machines wake (power puzzles) · 7 gravity flips with the tides · 8 rooms change when unobserved · 9 your shadow fights you ·
10 the Seed (finale). Waystone per stratum: checkpoint + fast travel. Strain when climbing up without the Deep Lift:
−(5% × strata climbed) to all stats for 10 min, stacking. Grave Echo on death in the Descent holds the Satchel.

---------------------------------------------------------------------------------------------------------------------

## 9. Lucidium, Veil, Stillpoint

Lucidium: Day Journal captures ≤10 snapshots/day (≤64 kept) as structure templates ≤16×12×16 around salient events. At
dusk a DreamPlan is built; on sleep (dream chance 40% + SPI%, +bed/Sanctuary) the player enters a Lucidium cell for 3–8
min. Lucidity 0–100: reality check (H) +12 in dreams, dream sign spotted +15, intention set at bed +10 start, WBTB
(alarm block wakes you, sleep again within 1 in-game hour) +25 start, Recall stat (journal entries) +0.5/entry.
≥50 lucid: flight, intent-manifest (hold H on a wall: door/stair/bridge from snapshot palette). ≥80 summon from memory.
Dying in a dream: wake "Shaken" (no penalty). Not ready → normal sleep. Hold sneak while sleeping: dreamless.

Veil: sneak + G held 10 s, still, not in combat → Body (mannequin-like entity with skin + gear, chunk ticket) +
player switched to spectator-backed spirit mode with cord radius (SPI). Awareness fog radius = PER/SPI derived. Ores and
secrets glow within Awareness (client-only highlight, server-sent positions). Only blue-tagged `veil_interactable` things
can be used. Body hit → Warning + snap back offered; body killed → player killed. Logout → snap back first.
Stillpoint dimension `awaken:stillpoint`: void + cloud sea + dawn temple platform; time ×10 slower feel (no hunger, no
mobs). Hub for stats, Truths, Codex; doors Out (Veil) and In (Lucidium, only while asleep). Hosts trial arenas.

---------------------------------------------------------------------------------------------------------------------

## 10. Above, Hearth, Story

Above: Precursor Vaults/sky platforms/ocean labs/monoliths; Scanner reads broken machines → Codex blueprint; machines
built at home from 3–5 parts; Essence power from a reactor within 32 blocks. Machines: Gate Scanner, Gate Stabiliser,
Waypoint Pylon, Deep Lift, Frame Forge, Clone Vat, Dream Recorder, Signal Relay, Recalibrator, Observatory, Launch Ring.
Halo dimension `awaken:halo`: orbital station, low gravity, archive with the truth.

Hearth: Anchor block (crafted from first boss essence) defines a 48-block radius base. Rooms detected by contents:
Sanctuary (bed + meditation mat), Armory (anvil + forge + rack), Trophy Hall (≥1 trophy), Workshop (≥1 machine),
Barracks (role pads). Town NPCs arrive by rank: Blacksmith (E), Scout (D), Archivist (C), Healer (B), Rival (A).
Echoes: bind fallen elites/bosses (B key within 10 s, chance by SPI and rank), cap by SPI, roles at home via role pads
(Guard, Haul, Farm, Mine, Train). Costs of Hearth upgrades fall 15% per rank above the upgrade's tier.

Story: prologue (scripted Bell Crypt → death → Stillpoint "Will you accept?" → Accept/Accept), Acts I–III gated by rank,
Revelations via Signal, Codex rumour board, the Rival's beats at D/C/B/A/S, endings at the Seed (Become / Break / Wake),
then New Game+ "Ascension" tiers.

---------------------------------------------------------------------------------------------------------------------

## 11. Config (`awaken-common.toml` / server)

difficulty: STORY | HUNTER (default) | HARDLINE | HARDCORE · storyLength: 0.5 | 1.0 | 1.5 · features: gates, descent,
lucidium, veil, above, hearth, story, anomalies, landmarks, wards, wilds (bools) · cameraShake · signalTypeSpeed ·
playtestMode (off). Client: nowTracker, horizonLine (§17), systemScale and the rest of `AwakenConfig.Client`.

---------------------------------------------------------------------------------------------------------------------

## 12. Cross-module public API (stable)

- `dev.awaken.signal.Signal` — `send(ServerPlayer, SignalWindow)`, `toast(ServerPlayer, Component)`.
- `dev.awaken.progression.Progression` — `profile(Player)`, `grantXp(ServerPlayer, double, XpSource)`, `count(ServerPlayer,
  Identifier, long)`, `markNewThing(ServerPlayer, String what)`, `rank(Player)`, `stat(Player, Stat)`, `contentLevel(rank)`.
- `dev.awaken.powers.Powers` — `learn(ServerPlayer, Identifier power, int variant)`, `energy(Player)`, `forcesOf(DamageSource)`.
- `dev.awaken.gates.Gates` — `gateAt(...)`, `activeRun(ServerPlayer)`, `isInGate(Entity)`, `satchelAdd(...)`, `preview(id)`.
- `dev.awaken.core.Forces` — force/environment tags on entities and damage.
- `dev.awaken.core.PlayDays` — current play-day counter.
- `dev.awaken.mobs.Mobs` — `spawnScaled(ServerLevel, EntityType, pos, level, modifiers)`, rosters by theme.
- `dev.awaken.mobs.Ritual` — the Ritual of the Bell's cast: `spawnSeated`, `spawnStatue` (stages set on the entities).
- `dev.awaken.gates.Gates` (ritual) — `ritualPlan(seed)`, `ritualHall(server, gateId)` (`RitualHall`: every position in
  the hall), `isRitual(server, gateId)`.
- `dev.awaken.gates.Gates` (trials) — `leaperTrialPlan(seed)`: the Leaper's Gauntlet's plan, opened with
  `openScripted(player, plan)` (§17.10). `dev.awaken.core.api.TrialHooks` — `atEdge(player, trial)`, `done(player, trial,
  ticks, medal)`, installed by the Ascent.
- `dev.awaken.body.Body` — `unlocked(Player, Move)`, `isSliding`, `isTacticalSprinting`, `isMantling`, `lastMoveTick` (both sides).
- `dev.awaken.body.ascent.Ascent` — the Ascent's ladder (§17): `enum Tier {TETHER, LEAPER, WINGS, SKYBORN, SOVEREIGN}`,
  `has(Player, Tier)` (both sides), `highest(Player)`, `grant(ServerPlayer, Tier, String source)` (grants that tier and
  every one under it, saved, synced; true when new), `onGranted(BiConsumer<ServerPlayer, Tier>)` (each new tier once,
  lowest first), `state(ServerPlayer)` (`AscentState`), `arrivedByMovement(ServerPlayer, int seconds ≤ 20)` (a third
  of the stretch in a rope, glide, flight, wall-run or hang, and no block placed: a tower never counts).
  `core.api.MovementHooks.wallRunning(Player)` / `hanging(Player)` read the Runner's moves. `core.api.AirHooks`
  (`register(Source)`, `Source.airAt(Level, x, y, z, double[] out)`): moving air the free states feel (§17.6).
- `dev.awaken.terrain.api.BiomeTable` — `archetype(Holder<Biome>)` (the vanilla biome an Awakened one stands in for, else
  its own key), `tag(String path)` (the table's `awaken:` biome tags: `fauna/`, `has_lair/`, `lair_style/`, `cottage/`).
- `dev.awaken.terrain.api.CityBasin` / `CitySite` — the city basin (§15): `request(Predicate<MinecraftServer>)` (the city's
  rule, once at mod init), `site(server)` (centre, core and blend radii, street Y, the river's centre line, whether the
  basin is carved), `reserved(x, z)` (inside the core plus 48: thread-safe, allocation-free; worldgen and runtime
  placement keep out), constants `STREET_Y` 70, `CORE_RADIUS` 330, `BLEND_RADIUS` 700, `RIVER` (the city's fixed path).
- `dev.awaken.hearth.Hearth` — a Hearth's owner and members ("Together"): `record HearthInfo(owner, ownerName, anchor,
  tier, radius, members, foundedFrom)` with `has(UUID)`, `hearthsOf(MinecraftServer, UUID)` / `hearthsOf(ServerPlayer)`,
  `home(ServerPlayer)`, `isMember(ServerPlayer, BlockPos anchor)`, `isMember(MinecraftServer, UUID player, UUID owner)`,
  `hearthAt(ServerLevel, BlockPos)`; plus `anchor`, `atHome`, `hasRoom`, `isSanctuary`, `inRoom`, `ownedEchoes`.
- `dev.awaken.core.audio.MusicLayers` — the layered music: `register(Slot, priority, name, Provider)` (a provider names a
  player's context `<ns>:<path>`, read from `assets/<ns>/music_layers/<path>.json`, or null; it may set the given
  `Intensity.Signals`' `pressure`, `forced` (0..3) and `arrival`), `current(ServerPlayer, Slot)`, `level(ServerPlayer,
  Slot)` (-1 without a context). `dev.awaken.gates.Gates.pressure(ServerPlayer)` — the floor's pressure stage (0..3).
- `dev.awaken.core.api.*Hooks` — install-once bridges between modules: `GateHooks`, `HearthHooks` (`inside`, `belongs`,
  `hearthsOf`, `household`, `shareHearth`, `within`), `PartyHooks` (`members`, `sameParty`; see "Together";
  `Parties.sharing(player, at)` is who of the party shares a kill), `MobHooks`,
  `StoryHooks`, `DescentHooks`, `DreamHooks`, `PowerCoreHooks`, `NowHooks` (the Now tracker), `WorldHooks` (landmark rumours),
  `MovementHooks` (the body's moves for powers), `PlayerClips` (client: full-body clips on a player when the Player
  Animation Library is installed; a no-op otherwise). The Wilds (§17): `WardHooks` (`register(Source)`, `at(level, pos)`:
  wards for sites placed by code; structures just join `#awaken:warded`), `ArrivalHooks` (`report(player, Way, ticks)` for
  movement systems, `byMovement(player)` for rewards, `history(player)`), `HorizonHooks` (`register(Source)` of sites,
  `addReach(Reach)` for needs such as `WINGS`, `collect(player)`, the pure `pick`).
- `dev.awaken.progression.Progression.xpLedger(player)` — XP by `XpSource` and by world-XP source key (§17 telemetry).
- `dev.awaken.signal.client.HudLook` (client) — how the System's cards draw: `card(g, x, y, w, h, accent, alpha, reveal,
  glitch)`, `text(...)`, `centered(...)`, `glow(...)`, `shake(glitch)`, `holo()`. Flat `Glass` unless the holo module
  installs its projected look; the flat paths make exactly the calls they made before. `ReadingScreen` marks a screen
  the player reads (the first-power choice): with the projected look the world dims behind it (`UiHooks.focus`).
- `dev.awaken.signal.client.UiCanvas.texture(Identifier, x, y, w, h, u0, v0, u1, v1, argb)` (client) — part of a whole
  texture (a DynamicTexture or a registered render target) on a panel or a flat screen, one tinted quad.
- `dev.awaken.core.api.UiHooks` (client) — `setFocus(Object owner, float value)`, `clearFocus(Object owner)`, `focus()`
  (the largest source, 0..1; Lens reads it). Reading panels only; toasts, cards and HUD lines never report.
- `dev.awaken.core.client.skin` (client) — `SkinnedMobRenderer<T extends Mob, S extends SkinRenderState>`, `SkinModel`,
  `SkinRenderState` (`wear(PlayerSkin)`, `wear(texture, slim)`, overlay toggles): the renderer for every non-player body
  with a player-format skin (townsfolk, training ghost, gate shades, Shadow Self, HUMAN and MIMIC mobs). The player mesh
  is picked per state (wide or slim, as the skin says); subclasses add their own tint, see-through, scale and layers.
  **Rule: a non-player entity never uses `AvatarRenderState` in 26.3.** `EntityRenderDispatcher.getRenderer(state)`
  sends any `AvatarRenderState` to the vanilla player renderer chosen by `state.skin`, so the entity's own renderer
  extracts the state and then never draws it: its texture, tint, translucency, scale and layers are all lost (the
  townsfolk drew as default Steve). `AvatarRenderState` is for hooks on real players only.
- Carillon hooks (§16.9, v0): `CityHooks`, `CitizenHooks`, `RenownHooks`, `EconomyHooks`, `PartyHooks`, `OpeningHooks`,
  `AtlasHooks`. Defaults behave as a classic world with no city.

---------------------------------------------------------------------------------------------------------------------

## 13. Decisions made while building (details in docs/modules/*.md)

Descent:
- Strata open by rank: 1 E · 2–3 D · 4–5 C · 6–7 B · 8 A · 9 S · 10 (the Seed) S-rank at level 135 **and** all eight
  Sovereigns of the Hollow Court defeated (story gate via `StoryHooks.seedSealed`). The Endless opens after the Seed.
- Stratum content level = content level of rank index min(6, (n+1)/2): stratum 1 already plays at D (17).
- STORY-mode deaths in the Descent keep half the Deep Pack at once; the other half waits in a Grave Echo.
- Waystone travel *upward* costs Strain; only the Deep Lift climbs for free.

Mobs:
- 154 creatures; vanilla models re-tinted (vanilla textures referenced, never copied) plus 5 custom models.
- Scaling: normals ×1, elites ×2.5, wardens ×5, bosses ×14 health (bosses ×1.6 damage); nightmares ×6 health / ×1.25
  damage; Sovereigns ×1.5 and the Signal's Shadow ×2 on top of boss scaling. All scaling uses permanent modifiers.
- Boss phase changes knock one boss part loose (once per boss). Essence per kill scales with content tier.
- `bosses_killed` and `bosses_killed_unarmed` are counted by mobs (not gates).

Story:
- The Hollow Court spawns the next undefeated Sovereign (fixed order: Hunger, Silence, Sleep, Machines, Mirrors,
  Storms, Roots, Ash) near an S-rank player in stratum 9 when none is alive nearby; in the Seed the story starts the
  finale (`Descent.beginFinale`), and the chosen ending calls `Descent.completeSeed`.

Powers:
- 63 powers in a static registry (`powers/PowerCatalog`), not a datapack registry: every power needs code, and a static
  table keeps ids stable and testable. Full catalog in docs/modules/powers.md (generated by tools/content/powers_catalog.py).
- Power damage scales with player level the way mob health does, so hits-to-kill stays steady across ranks; no single
  power hit takes more than 20% of a boss's health. Cooldown reduction is capped at 50%.
- `awaken:dash_chain_airborne_best_session` holds the *current* airborne chain (resets on landing), so Directive
  baselines measure a fresh chain.

Hearth:
- Reassessment trials run in their own dimension `awaken:trials` (not the Stillpoint). Upgrade costs ×0.85 per rank above
  the upgrade's tier, compounding. Vael lives in town from A until S (`townsfolk/vael_rival.json`).
- Echo kills are credited to the owner (for gate kill XP) but never count as bare-handed kills.

Lucidium:
- Day Journal snapshots are vanilla structure files in `<world>/awaken/lucidium/<uuid>/`; chest contents stripped.
- First night always dreams. Dream chance gets the Sanctuary bonus from `Hearth.isSanctuary` (or a meditation mat
  within 6 blocks of the bed). Weekly death counters (`awaken:killed_by_week/*`) reset every 7 play-days.

Above:
- Five ruin kinds from one code-built structure type; the spawn vault (240–360 blocks from spawn) opens at D and holds
  a Scanner. A powered Signal Relay near your Hearth links for 7 play-days and grants +1 Directive slot. The
  Recalibrator uses `Progression.respec`. The Clone Vat (`Above.consumeCloneSave`) keeps the Deep Pack / Satchel once
  per play-day. Revelations fired by modules go through `Story.revelation` so they show once and reach the Codex.

Veil:
- Meditation: sneak + hold G still for 10 s, release G to step out; keep holding to 20 s for the Stillpoint; N to simply
  sit. Fracture heals 3× faster while meditating. Energy regen ×2 while meditating, ×1.5 in the Stillpoint.
- Awareness radius = 16 + 2×PER + SPI blocks. Cord = 32 + 6×SPI (+perk, cap 1024); time = 30 s + 3 s×SPI.
- Closed Ghost Doors and Veil stone block spirits; the Out door sends the spirit to your return point while the body
  rests safely in the temple. Survival players can't edit the temple or Veil Gate pockets.
- Cord-Eaters are driven by the Veil (the mobs module's AI is bypassed); a bite ends the projection with Spirit Shock.

Gates:
- Director spawn chance per pass: 100% / 30% / 8% / 0% at 0 / 1 / 2–3 / 4 standing gates; spots ≥ 96 blocks apart.
  SOVEREIGN players can roll SOVEREIGN gates. Breaks are deferred while an online player is inside that gate.
- Claim ids carry a run index after the first run, so a re-entered gate can pay again. Retreating through floor 1's
  entrance forfeits the Satchel. A Clone Vat save banks the whole Satchel; pockets-full loot drops at your feet and the
  claim window says so.
- Twists count as "seen" when you come out of that gate alive. "Comfort" for `floors_pushed_past_comfort` is the median
  of your last five extraction floors (2 before any). Gate interiors can't be broken or blown up.
- Echoing (`Forces.Tag.ECHO`) means "gets up once": Awaken creatures fall and stand up by themselves (mobs), anything
  else is replaced once by the gate; `Forces.markRisen` records that it already rose (shared with the Descent).
  Player-owned Echoes are `MobRole.Role.ECHO`, never this tag.
- A broken gate's Lord is the theme's Lord body with role BREAK (×8 health, ×1.3 damage), shown with the gates' boss bar.
- The prologue's Bell Crypt is a scripted E-rank gate of three floors placed where the story asks (`GateHooks.openScripted`);
  scripted gates never break.

Story effects on the world:
- "Break the Signal": no gate breaks again (cards say "never · the gates are calm", the map says "calm").
- "Wake up": the blight never greys the land again.
- "Become the Signal": each Ascension tier gives hostile creatures +30% health and +15% damage and the player +15% XP
  (up to 10 tiers); allies are not scaled.

Plan v2 · onboarding and progression (details in docs/modules/onboarding.md):
- Rank floor-reward shares E .28, D .16, C .065, B .22, A .60, S .55, Sovereign .50 (sim: level 50 ≈ 56 h, 135 ≈ 101 h for a
  balanced player, 120 h careful); Daily Regimen = 0.1 × xpToNext + 1 point.
- §4.3 is replaced: home XP = kill 4% × min(2, hp/20), ore 1%, block 0.05% of a level, each through diminishing returns per
  source (10-minute tally; budgets kill .15, ore .06, build .03, other .25 of a level per 10 min), tapered by
  1/(1 + levels above the content / 10). Discoveries (`Progression.discover`) are uncapped.
- Profile schema 2 adds `banked_xp` and `unlocks`. XP earned before awakening is banked and paid on Accept, which tops the
  player up to level 2. The Accept card is a choice card.
- Truths are first offered at level 5 (level-road flag `awaken:truths`), then at each rank-up.
- Directives and drills have `requires`; unmet ones are never offered.
- §4.11 Level road: `level_road/*.json` {level, title, text, grants[power|recipe|item|power_core|flag], quiet}; unlock ids
  are saved, missed steps are granted on login, flags read with `Progression.hasFlag` (`awaken:road/<name>`).
- The first power is a choice of three E-tier actives from different families (`powers/Starter.POOL`), saved in
  `awaken:first_power`; Dash comes at level 3.
- Mod recipes unlock in the recipe book when an ingredient is first held.
- Pacing (sim, balanced player): first power ~8 min, level 5 ~47 min, 10 ~3.6 h, 25 ~17 h, 50 ~56 h, 70 ~73 h, 135 ~101 h.

Plan v2 · System and Hearth:
- Every Y/N decision is a choice card that says what each key does (`SignalWindow.choice`); information is a toast.
- The Now tracker (`core/api/NowHooks`) collects up to three concrete suggestions from every module.
- The Hearth Anchor is crafted from a Lord's Heart, 4 stone bricks and 4 copper ingots (no obsidian: stone tools suffice).
- Wandering gates (anomalies) form at the player's rank, fade after their lifetime unless entered, never break, and don't
  count toward the Director's 1–4.

Plan v2 · System v2 (details in docs/modules/system-ui.md):
- Any message with an action is a choice card whatever its presentation; a CHOICE without an action is a toast.
- A card is armed after 8 ticks without a Y/N press (stray presses are ignored) and folds after 20 s on screen (paused
  while a screen is open); 10 ticks between cards; warnings and priority ≥ 9 pre-empt.
- Pending decisions persist per player (max 12) until answered, withdrawn or past their ttl; on login they come back
  folded (tracker and Status), never as cards.
- Optional offers (Directives) never card over the world: `Signal.offerQuietly` sends a toast and the decision waits
  folded. Cards are for decisions that matter now (gates, extraction, Echo binding, travel, the Stillpoint).
- A lesson step just reached outranks standing reminders on the one-line tracker for five minutes.
- Toasts are 160 wide and vanilla height (32), or 43 when the detail needs a second line (a level-road step); at most 3
  of ours; a burst merges into "N new things". Cards sit right of the
  crosshair and never exceed 20% of the screen height. Client settings: `systemStyle`, `nowTracker`, `systemScale`
  (0.75–1.5, toasts don't scale). The Now tracker syncs every 40 ticks, only when changed, and steps below the boss
  bars when they would overlap it.

Plan v2 · overworld creatures (details in docs/modules/fauna.md):
- Spawn category `awaken:fauna` (16 per player, NeoForge enum extension). Role caps per player: ambient 8 (3 before
  awakening), neutral 5, hunter 5, named beast 1. Game rule `awaken:fauna_spawning` switches them all off.
- Hunters follow the vanilla monster light rule, are hostile only while the player stands in block light < 8 and not
  under open daytime sky, and melt away after 10 s of open daylight. Inside a Hearth's radius only ambient creatures spawn.
- Content level = player level − 1, capped at the rank's gate content level (beasts: player level, cap +2).
- Named beasts: from level 3, 40+ blocks away, 30-minute world cooldown, 384 blocks apart, at most 12; 45% Power Core;
  first kill pays 0.5 level, first sighting 0.1 (beasts 0.25) through `Progression.discover`. Their boss bar shows only
  while they have a target.
- The reveal pulse plays once per player at awakening (old awakened saves get it once).
- P2: six more families (the Mire, the Canopy, Duskwood, Blossomwood, the Crags, Sporewild; 22 creatures). Greenwild
  hands over dark forest, cherry grove, flower and birch forests; Sunscorch keeps savanna and badlands.
- Every named beast has a lair placed by world generation (one structure set each; spacing 112 chunks for P1's three,
  96 for P2's, 40 for the Mycelord). The lair's beast appears when an awakened level-3+ player is within 96 blocks and
  returns an hour of play after it falls. Roaming beasts spawn at weight 1.
- Meeting a family's creature at level 3+ tells of its nearest lair (within 3,000 blocks); finding a lair pays 0.15 of
  a level once per beast kind. Block-changing talents (lichen, flowers, mushrooms) never act inside a Hearth radius.
- Twelve family materials (`<family>_<thing>`) and the beasts' parts join the Hunter gear tags (E hides, cores and
  bindings; D cores) from `src/main/merge/fauna/`.

Plan v2 · water, caves and mounts (details in docs/modules/fauna.md, P3):
- Two more families, the Tidewater (5) and the Hollows (5): 44 overworld creatures in all, with two more named beasts
  (the Lantern Maw, lair the Sunken Shrine on the sea floor; the Thousandfoot, lair the Crystal Nest in rock; lair
  placements SEABED and CAVE, spacing 96, separation 40).
- Water creatures have their own spawn category `awaken:fauna_water` (10 per player; role caps 7/3/0/1), so they never
  take vanilla fish or squid slots. Cave creatures share `awaken:fauna` but need a roof; Hollows hunters keep the light rule.
- Mounts (Frost Stag, Dune Ray, Tidesteed) from level 11: trust by sneaking and feeding the favourite food (3–4 feeds),
  then the Hunter's Bridle (level 11 recipe); the Hunter's Whistle (level 13 recipe) calls the mount from 160 blocks
  (10 s cooldown), sneak-use sends it home. One bonded mount per player; the bond is saved on both sides (schema 1);
  bonded mounts never despawn.

Plan v2 · anomalies (details in docs/modules/anomalies.md):
- One anomaly at a time per player, after 6–12 minutes of outdoor play (3–6 after awakening), ×1.5 faster at night;
  paused in gates, outside the overworld, asleep, at home (inside a Hearth's radius) and in the first 2 minutes.
- Kinds: rift tear (2 min of waves, reward once: shards, 0.3 × a floor reward, 12% Power Core), spirit procession (day
  only; follow five lights to a cache) and wandering gate (from level 3; 1–2 floors, 5–8 minutes). Weights day 55/15/30,
  night 80/20/0; the first is always a rift; no rifts on Peaceful. Config switch `features.anomalies`.
- Spots: 32–96 blocks away, ≥ 24 blocks outside any Hearth's radius, ≥ 24 from a gate, ≥ 48 from another anomaly, dry, not built on.
- Each kind is a discovery (0.1 of a level). Interrupted anomalies are cleaned up on server start.
- P2 kinds: **fallen star** (mostly at night: a crater in natural ground with a sealed meteor; a guardian of your rank
  rises within 16 blocks for 90 s; opened once it cools for loot, shards, 0.2 × a floor reward (0.3 if the guardian was
  slain) and a 10% (25%) Power Core; burns out after 6 min) and **shimmer grove** (mostly by day, peaceful: 2½ minutes
  of light steps, ore sight, a spirit's landmark hint or healing flowers). Weights day 30/15/20/6/25, night 45/15/0/35/5
  (rift/gate/procession/star/grove); no kind twice running; kinds never met weigh double. Saved state schema 2.

Plan v2 · landmarks (details in docs/modules/landmarks.md):
- Kinds: the Well, vaults (the spawn vault too), monoliths, ocean labs, sky platforms, gates by theme. Reaching the
  first of each kind is a discovery (0.15 of a level). Memory `awaken:landmark_memory` (schema 2, copied on death,
  synced to its owner).
- The Gate Map marks every landmark reached (filled) or heard of (outlined): told by Mira, whispered by a spirit or first
  named by the Now tracker.
- The Horizon line (§17; until then the Now tracker) names the nearest unreached landmark once there is a reason to go
  (Well from level 10, vault and monolith 4, spawn vault 2, ocean lab 6; a sky platform once you have a way up, teased
  before); never inside a Hearth or a gate. Mira tells one landmark
  rumour a day when no gate is worth one (`core.api.WorldHooks.landmarkRumour`).
- Faint light shafts over ruins at dusk (fainter at night) and the Well's pillars at night, within 288 blocks; nothing
  by day. Every ruin has an outer cache (2–4 essence). The Scanner hears the Well and skips ruins already reached.
- Config `features.landmarks` switches discovery, hints, rumours and markers off.

Plan v2 · the Hearth guide (details in docs/modules/hearth.md):
- Using your own Anchor opens the "Your Hearth" screen (a QuietScreen): tier, radius, rest healing and respawn; a card per
  room (what it gives, a ticked furniture checklist, the missing furniture's recipe, the one-line rule, why it isn't
  recognised); the town; the next upgrade with have/need, built from the screen or the accept key (re-validated on the
  server). The radius shows for 8 s as a ground-level ember ring.
- Stairs and slabs close gaps like doors and panes (a stair or slab roof is a roof). Why a room isn't recognised comes from
  a second bounded fill inside the scan's budget: sees the sky, a hole in the roof, an opening at x y z, too big, missing X.
- The Anchor teaches the Hearth furniture recipes when it takes root. The Now tracker teaches one line at a time: "Use the
  Anchor for a guide", then the next room with live ticks, then why a room isn't counted yet.
- Oren's cottage: offered once per player (a choice card after the first Anchor), free, never built over anything a player
  made (only terrain, plants and up to three natural trees give way); 9×9 outside, 7×7×4 inside, ten biome palettes,
  raised over ~7 s; its room is a Sanctuary on completion.

Plan v2 · Anchor requests (details in docs/modules/requests.md):
- Once a player has a Hearth Anchor, the System asks for materials they can really get (sightings, vanilla stats, depth,
  rank): one request at a time, two from C-rank, offered quietly with a one play-day ttl, renewed per free slot each
  play-day and after each delivery; nothing lost for ignoring, declining or dropping one.
- Delivery at the Anchor (part loads count) pays essence, a share of a level through `Progression.worldXp`
  (source `hearth_request`, its own budget), Forge credit (a refund on the next temper or upgrade, up to 50%), sometimes
  a Power Core Shard (four fuse into a core) or an early recipe, and town reputation (Known 6, Trusted 18, Valued 40,
  Beloved 80) with town touches. Streaks: +10% a delivery day after the first, up to +50%, lapsing after 3 play-days.
- Pools in `data/*/awaken/request/*.json` (schema 1) with tokens `seen:`, `killed:`, `mined:`, `got:`, `depth:`.

Plan v2 · late Hunter gear: A Lordscale (Gravity/Charge ward; every fifth power hit of one Force bursts onto nearby foes)
and S Sovereign Weave (no Fracture from dying in a gate below your rank; your highest stat counts half again for gear
and powers), each with its own silhouette parts; level-road steps at 67 and 97.

Plan v2 · the road to 50 (details in docs/modules/road.md):
- Every level 1–50 has a level-road step. Steps (schema 2) may carry a goal {now, done, requires, toast, priority}: a Now
  line that stays until a real counter says it's done, then a toast; only the newest open goal shows (priority 35); met
  goals are saved as `awaken:goal/<step>`. Requirement tokens add `rank:X` and `path`.
- Power mastery: a power gains points from casts that land on a living target (their energy cost), half the energy spent
  in a real fight that missed, and kills; nothing from casting at the air. Levels at 7,000 / 13,000 / 32,000 / 50,000
  points, the same effect as a duplicate core. Loadout schema 2 adds `mastery`. Sim: a main power reaches level 3 at
  player level ~20 and 5 at ~44.
- Lessons taught in play: meditation (level 11), dreams (12), the Veil (15), each with Now lines, toasts and a choice card
  where the decision is real; progress in `awaken:lesson/*` counters.
- API: `Powers.restoreEnergy`, `Powers.masteryProgress`, `Progression.addPerkSource`, `Lessons.define`. A Hunter focus
  refunds 0.5/1/1.5/2 energy (E–B) per power hit, at most once a second.

Plan v2 · Hunter gear (details in docs/modules/gear.md):
- Module `gear`: tiers E Wildhide, D Beastbound, C Gatewrought, B Veilwoven; 4 armour pieces, a blade, a power focus
  and a mattock each, at vanilla stone/copper, iron, diamond and netherite strength. A and S are designed only.
- Set bonuses at 2 and 4 pieces per tier (E Perception: quiet sneaking, Hunter's Sense; D Strength/Vitality: hearts,
  Cornered; C Five Forces: Force ward, primed strikes; B Veil/Spirit: Spirit ward, meditation heal, Veil Flicker).
  Numbers live in `gear/SetBonus`.
- A focus adds +6/9/12/15% power damage in either hand (+2% per temper level), under the boss cap.
- Crafting-table recipes with hook tags `#awaken:hunter_<hide|core|bind>_<e|d|c|b>`; new creature families add their
  materials to these tags from their own merge folders. E-tier is craftable from first-hour creature drops.
- Level road `hunter_gear_e/d/c/b` at 3, 13, 22, 42. Now line priority 25, only for an upgrade.

Plan v2 · power targeting and visuals (details in docs/modules/powers.md, power-fx.md):
- One rule for every area power (`powers/Combat`): a foe is anything targeting you or your allies, a creature you last
  hurt, an angry neutral or an `Enemy` (monsters, slimes, ghasts) that isn't a peaceful overworld creature; never a
  villager-like `Npc`, your Echoes or tamed pets. Aimed powers hit whatever you aim at that isn't on your side.
  Bystanders in an area are pushed, never hurt. Reach is measured to the target's box, so big
  bodies are hit at their edge; cones get slack for wide bodies. Knockback resistance never cuts a power's push below 30%.
  GameTests line up a mixed crowd for every damaging power (`-PpowerAudit` runs them all).
- Power visuals are client-built from compact events (`awaken:power_fx`); the server sends no particle spam for powers.
  Client settings `powerEffects` (FULL/REDUCED/MINIMAL), `castAnimations`, `screenFlashes`; effects thin past 24 blocks
  and aren't built past 72; one effect spawns at most 220 sprites. An effect flagged REACH shows the true hit volume.
- Optional client dependency: Player Animation Library (`player_animation_library`, compileOnly, never required or
  bundled). With it, third-person casts play a full-body clip per power family (wind-up, release, follow-through; held
  wind-ups loop), driven by the same `awaken:power_fx` events and prediction, with the same timing; dashes, hover,
  slams and the flurry share body clips. First person and the vanilla-pose fallback are unchanged; `castAnimations`
  switches both off. Clips are generated by tools/art/specs/power_casts.py. With Bendable Cuboids (`bendable_cuboids`,
  also optional) each clip's `_bent` twin plays instead: elbows and knees flex, hands and feet stay where the straight
  clip puts them.

Awakened Body (module `body`, docs/modules/body.md; approved 2026-10-24, widened the same day):
- (Superseded by §16.0.1 when the Carillon Runner workstream lands: everyone moves like a Runner and ranks improve moves.)
- Unlocks: awakening → soft landing + tactical sprint; D → slide; C → mantle. Never-awakened players move like vanilla. Each
  unlock is one toast in the same tick as the awakening or rank-up (it joins that burst), remembered in `awaken:body_memory`
  (schema 1, kept through death); old saves hear theirs once after login. Status › THE BODY lists the moves you have.
- Off while riding, sleeping, projecting, meditating, flying or gliding. No invincibility frames anywhere.
- Looks with the Player Animation Library (optional): each move plays a full-body clip (tools/art/specs/body_moves.py;
  bent elbows and knees with Bendable Cuboids) through `core.api.PlayerClips`, from the same prediction and `body_seen`
  signals, no new packets; without it the vanilla-model poses stay. First person unchanged.
- Soft landing: landing from ≥ 3 blocks while sprinting or crouching rolls: safe fall +3, ×0.6 beyond. One LivingFallEvent
  listener (LOW, never sees a cancelled fall), so a power's landing, a mount, an elytra never roll; where Low Gravity or jet
  boots already give ≥ +3 safe fall, or a perk ≤ ×0.6, the kinder rule wins and nothing stacks.
- Tactical sprint: double-tap sprint (≤ 7 ticks apart) while sprinting: speed ×1.25 (transient modifier
  `awaken:body_tactical_sprint`) for ≤ 60 ticks, 0.4 exhaustion, recharge 80 ticks after it ends. A stop, a swing, a cast,
  an item or water ends it; the swing or cast goes off at once.
- Slide: crouch while sprinting on the ground: 14 ticks at v0·(1 − 0.5·t/14), v0 = max(0.38, entry), 0.46 out of a tactical
  sprint (~4.1 / ~5.0 blocks, ~4.6 / ~5.5 with forward held); crawl pose forced on both sides, the run paused; ends at a wall, off an edge, in water; jumping out adds 0.15
  (the slide-cancel); 0.1 exhaustion; cooldown 20 ticks after it ends. A cast mid-slide keeps it; a power's motion ends it
  (snapped onto the slide's way within 60°; a Velocity power's ×1.3).
- Mantle: jump facing a ledge whose top is 1.1–1.6 above the feet, face within 0.6 (server 1.1), 2 free blocks over it, a clear
  way up and over; never onto a collision taller than its block (fences, walls, gates); 6 ticks; 0.05 exhaustion; not while
  using an item or a shield. The server finds the ledge again and teleports a claim > 1 block or 0.25 high off back.
- Momentum hit: a melee hit at attack strength ≥ 0.9 while sprinting or sliding deals ×(1 + m), m = clamp((v − 0.2)/0.25, 0, 1)
  × 0.3 of horizontal speed v (sprint ~+10%, tactical ~+18%, slide +30%), knockback +2m; the swing's own target only.
  Trip: a slide staggers hostile creatures ≤ 1 block tall (Slowness VI 16 ticks, a hop; once per slide; not bosses, Echoes).
- Powers (`powers/MoveTwists`, body state via `MovementHooks` only): a cast ≤ 10 ticks after a move refunds clamp(0.1 × cost,
  0.5, 2) energy, at most once per 20 ticks. Mid-slide: Kinetic → a 4.5-block, 40° cone, dmg(3) and a throw (0.9, 0.3);
  Velocity dash → ×1.3 and no-fall 40 ticks; Shadow → smoke along the rest of the slide for 60 ticks, pursuers within 1.8 lose you.

Plan v2 · hero bodies and voices (details in docs/modules/heroes.md):
- Named beasts are modelled near true size (entity scale 1.0–1.8) on their own rigs; collision boxes unchanged. A
  beast's death takes 36 ticks (the fall); loot and counters still happen on death. Each signature move, phase change
  and provocation opens with the beast's roar.
- Overworld creatures speak with their own sounds; ambient intervals 900/600/420/360 ticks (ambient, neutral, hunter,
  beast), volumes 0.45/0.8/0.9/1.5. All mod sounds are stored at 44.1 or 48 kHz (a GameTest checks).

Plan v2 · audio (details in docs/modules/audio.md):
- Music cues play through vanilla's music manager (Music slider, Now Playing toast). Stage cues: awakening (once),
  rank-up (5 min), gate broke (10 min); ordinary cues: beast (15), gate forming (20), shrine (8), lair (15); 3 minutes
  between any two (stage cues excepted); ordinary cues never play over a song.
- Gate tracks play 3 : 1 against the biome's vanilla music, 3–10 minutes apart; Descent 2 : 1, 5–15 minutes; the Abyssal
  Shrine is silent. Loudness: music −19 ± 2 dBFS RMS / −1 dBTP, UI −24 ± 3 / −3, ambience −28 ± 3 / −6.
- The System's sounds share one glass voice in D major pentatonic; the toast chime yields to a specific System sound.
- Layered music (Awakened Pack stage 4): in a gate run the music is four phase-aligned stems of the gate's theme
  (bed, tension, combat, lord; each theme in its track's mode, tempo and voices), played as one stream mixed sample by
  sample. Levels, decided by the server every 10 ticks: **lord** (a boss-role mob alive within 40 blocks that hunts
  you, or you within 20 of it, or blows traded with it in the last 15 s; holds 10 s), **combat** (you hit a hostile or
  one hurt you in the last 5 s, or 3+ hostiles hunt you within 16; holds 12 s), **tension** (a hostile hunts you within
  24, any hostile within 10, the gate's pressure at waves or the Warden, or a Lord alive within 40; holds 8 s), else
  **calm**. A level rises at once and falls when its hold ends; a boss dying within 64 blocks ends Lord and combat at
  once. Hostile = an AWAKEN monster role or a vanilla monster (or anything hunting a player), never Echoes, the
  Ritual's cast or peaceful fauna.
- Layer gains (bed, tension, combat, lord): calm 1/0/0/0, tension 0.85/1/0/0, combat 0.6/0.8/1/0, lord 0.45/0.6/0.9/1.
  Per-stem fades, seconds for 0 to 1 (attack / duck when intensity rises / release when it falls): bed 6/2/10, tension
  3/1.5/8, combat 0.6/1.5/6, lord 1.5/1.5/4; a Lord's fall lets Lord and combat go in 3 s; leaving fades 4 s, death
  2.5 s. A change is heard within about a second (quarter-second stream reads).
- The layers take the music channel: a vanilla song playing fades out in 1.5 s first, vanilla sees no situational
  music while they sound, and its next song waits its full delay after. After 150 s of calm the bed rests (12 s fade,
  the channel goes back to vanilla, so the gate's composed track may come later) and returns at the next tension or a
  new floor. A stage cue takes the channel from the layers (1.5 s fade); an ordinary cue only while they are calm
  (3 s), never during tension, combat or a Lord. The Music slider and the place's music volume apply. No layers in the
  Abyssal Shrine or the Ritual of the Bell; an annex floor keeps its gate's music.
- Layer loudness: the music rule applies to each level's mix, not to a stem: calm -20.5, tension -19.5, combat -18.5,
  Lord -17.5 dBFS gated RMS, every mix ≤ -1 dBTP after encoding; a stem alone ≤ -1 dBTP. Stems are equal-length loops
  of whole bars (Vorbis q1). GameTests decode the shipped files in Java and check every mix.
- Contexts are data, open to any module: the context `<ns>:<path>` plays `assets/<ns>/music_layers/<path>.json`
  (schemaVersion 1: `source` music or ambient, the slider it follows; `frames`; 1-8 equal-length stems each with
  attack / duck / release; one gain per stem for levels 0-3, a missing level repeating the last). Two slots, each with
  its own context and level: **music** (takes the music channel, follows the place's music volume, rests and yields to
  cues as above) and **ambience** (never touches the music channel, never rests or yields). Providers register per
  slot with a priority (`MusicLayers.register`); every 10 ticks the highest that names a context wins. A provider may
  report the gate's pressure, pin the level (0-3, instead of the player's intensity) or flag an arrival (the bed
  returns from its rest). Ids: `awaken:gate/<theme>` (music; provider `gate`, priority 100; none in the Abyssal Shrine
  or the Ritual). Reserved: `awaken:descent/stratum_<n>` (music); `awaken:city/<district>` for the city's ambience
  (ambience slot, `source` ambient, priority 100; its levels are the district's crowd, e.g. night / quiet / busy /
  crowd, pinned by the provider from the district's density; it answers only inside the city site, so it never meets a
  gate's music).

Plan v2 · overworld structures (details in docs/modules/structures.md):
- One visual language: three families (gates, Precursor ruins, lairs), each with its own light and palette.
- A surface gate is a vortex (ellipse radii by rank) drawn by a block entity renderer on an invisible anchor core over a
  pad and crater ring; placement checks the rank's footprint. Looks: forming, open, about to break, broken, calm,
  spent; Red Gates red-rimmed. The hum is two loops with a 48-block falloff, pitch by rank. Old gates and Wells are
  rebuilt in place when loaded.
- Ruin sizes: vault 23×24×23, monolith 17×46×17, ocean lab 31×22×31, sky platform 49×26×49; pieces carry a blueprint
  version. The Well: shaft radius 7, twelve pillars at radius 18, four lantern pylons.
- The Gate Map shows lairs you've heard of or found (synced from `awaken:fauna_state`) beside gates.
- Doorways inside a gate (the exit after the Lord, the way back, inner gates, rifts) are one opening drawn as a single
  vortex in the gate's rank colours (rifts red-rimmed) by the anchor block's renderer (`awaken:gate_portal`, the bottom of
  the middle column); the other blocks are invisible and untargetable but still let you walk in. Doorways placed before
  this (state `role=legacy`) redraw themselves on a random tick.

Plan v2 · gate interiors and D–B tuning (details in docs/modules/gates.md "Interiors", "Tuning"):
- Gate content version 2 (`GatePlan.CONTENT_VERSION`): every theme has its own room dressing, 3–4 set pieces and a Lord's
  arena; rooms are taller. Version 1 gates already entered keep their rooms; READY unentered ones rebuild.
- The way on is lit: guide-lit lintels on the critical path, the System's blue on the door into the Rest Shrine, braziers
  by the arena doors. Set pieces keep 5-wide door axes level and clear; no block entities in worldgen; liquids only in
  basins; fire only on soul soil.
- Lord arenas stay 3×3 slots and 17 high, round (radius 22), so no standing spot is outside the Lord's arena radius; the
  Lord's spot is lit 9+.
- Depth: +1 monster per room from floor 3, +2 from floor 5; a second arena-wave elite from floor 3; extra elite chance
  0.04 × (floor − 1) + 0.01 × rank − 0.02. Targets (simulator on real generated gates, balanced player): 1–3 deaths per
  10 gates at D, C and B; the best floor to stop after is 3–5, and stopping one floor before the Lord keeps ~98% of the
  XP at about half the risk. `gates_tuning_matches_sim` fails if the game and the simulator drift apart.

Plan v2 · first launch: vanilla's "experimental settings" prompt (shown because the mod adds worldgen) is answered for
the player (client config `skipExperimentalWarning`, default on).

Holo UI (details in docs/modules/holo.md; design docs/proposals/holo-ui.md, P1, P2, P3 and P5's sounds and HUD fade):
- K opens the menu on the wrist when `statusStyle = HOLO` (default) and both holo pipelines compiled; otherwise the
  flat StatusScreen. `screen status|map|codex` in the autopilot stay flat; `screen holo` opens the holo one.
- Timeline: arm 0–120 ms, band flare 60–200 (peak ×2.5 at 140), beams from the wrist with an opening surge 110–360
  and a faint steady link from 260, the panel grows out of the beams' origin 140–370, unfolds Y 160–280 then X 220–360,
  glass 230–380, scan line 250–390, text types from 300 (Signal speed ×4, all lines done by 450); clicks from 250;
  closing replays it backward in 250 ms; K while opening skips to open.
- Panels: whole framebuffer pixels per unit (≈45% of the width, ≤80% of the height; the 272×228 K panel is 5 px/unit
  at 3024×1964), placed from the real projection every frame (FOV compensated, vanilla bob undone), 0.62 m ahead at
  (-0.455, +0.19) of the half-screen (left of the centre, up and right of the wrist, clear of the forearm; the crosshair
  is hidden while the holo menu is open), yawed 7° toward the wrist and
  leaning 3° back (third person: at least -0.50 aside, 80% size). Material: 38–50% glass (denser toward the wrist),
  static scan lines, a slow shimmer (no strobing), a hairline edge with brackets and an additive halo. Text is light,
  not print: no drop shadow, a soft cyan halo (four faint offset copies), cyan-white body text, System-blue headers
  with a glow. Readability overrides sheerness: one even feathered tint (38% over the glass) across the content area,
  never per-line boxes; it must read over sky, grass and night without Lens.
  Drawn in the main pass's translucent custom geometry (the glass writes depth, so later water, particles, clouds and
  weather stay behind it); under Improved Transparency in the always-on-top phase. Budget: the holo K menu is within
  0.06–0.25 ms of the flat one at 3024×1898.
- Spring: critically damped, settles in 200 ms, shown lag ≤ 4° (soft clamp); `reducedMotion` follows directly.
- Wrist: the off-hand forearm from the lower left at 0.64 scale, the inside of the wrist to the eye, with the System's
  band (a flush dark strip, two emissive lines, segment nodes with an idle pulse, the diamond emitter; it flares on
  open). First person replaces both hands (RenderHandEvent HIGH, cancelled) only while opening or open; from the first
  tick of a close the hands are vanilla's again (re-equip from the lowered height), so casts, Awakened Body moves and
  other hand hooks right after the menu draw their own hands; only the lowering forearm is still drawn. Third person
  uses the `AWAKEN_HOLO_WRIST` arm pose (enum extension, forearm across the chest). Others see the pose, a brighter
  wrist glow and a small content-free hologram card beside the body (depth-tested), never the panel's content.
- `awaken:holo_state`: kind NONE/STATUS/INVENTORY, unknown ids refused, ≤ 4 changes per 20 ticks per player (later
  ones wait, the last always arrives), relayed only to trackers that know the channel; reset on respawn/dimension.
- HUD motion: `awaken:*` layers except full-screen/crosshair ones (`power_fx_screen`, `fauna_reveal_pulse`, `veil`)
  drift 0.34 px per degree of trailing offset, soft-limited to 2 GUI px (SUBTLE, default) or 4 (FULL), × Screen Effect
  Scale, off without View Bobbing, snapped to framebuffer pixels. Vanilla layers never move.
- `UiHooks.focus()` (0..1) eases with the open panel for Lens. Lens may also tell panels apart by depth: the glass writes
  its real distance (≈0.6 m) into the main depth.
- System cards (`HudLook`, drawn in the GUI pass with the panel's own shaders on GUI pipelines): toasts, the choice and
  gate cards, story lines, the Now line and the first-power choice are projected glass in the K panel's family (its rim
  glow and corner brackets, static scan lines, one even 40% tint, a faint halo) with text as light; notices also carry
  a slow interference band (one pass per 6.5 s, still with reduced motion) and toasts a projection edge on their screen
  side that flares on arrival. The Now line has the frame only (no band, no flicker). Positions, sizes, timing and every System v2 rule are
  unchanged (the look never moves or resizes a card). A card scans in from the top behind a bright line over the time
  it used to fade in (toasts: vanilla's 600 ms show, done by 270 ms, without the slide), and glitches out in three bands
  that slip ≤ 3.5 GUI px and fade (toasts: in 300 ms of vanilla's hide); reduced motion makes both a fade. The Now line
  is a still card (no scan, no glitch). Only reading screens raise `UiHooks.focus`; toasts, cards and the Now line never
  dim the world. `statusStyle = FLAT` (or GUI shaders that fail) keeps everything flat. With a first power pending, K
  opens the first-power choice in either style.
- The inventory (P3): E opens vanilla's survival `InventoryScreen` as a holo panel when `inventoryStyle = HOLO` and the
  projected look draws (`statusStyle = HOLO`, GUI holo shaders compiled); creative and other mods' inventory screens
  are never swapped. It is a subclass of vanilla's screen (swapped on `ScreenEvent.Opening`), so every click, drag,
  shift-click, number key, the recipe book and other mods' container hooks are vanilla's, live from the first frame.
  It plays the K menu's choreography at 2.5× (open in 180 ms, closed in 100 ms): the wrist rises, a 3D panel grows out
  of the band and lands on the flat screen's rectangle pixel for pixel (one unit = one GUI pixel), and the flat face
  crossfades in at 148–180 ms (items, figure and title switch halfway); closing, the 3D panel folds back holding what
  the inventory holds then. Reduced motion (or no world pipelines): the flat face fades in place. The face is the K
  panel's glass, tint, halo, rim and brackets drawn unit for unit in the GUI pass, projected wells instead of slot
  squares (18×18, the result 26×26; the hotbar brighter, the hand's slot lit), the figure on a projected floor, an
  arrow of light to the result, the title as light, the recipe button a glass key; no dim or blur (Lens dims through
  `UiHooks.focus`). Others see the wrist (`holo_state` INVENTORY). `inventoryStyle = VANILLA` (or the flat look) leaves
  vanilla's screen untouched. Not restyled yet: the recipe book's panel and the status-effect list.
- Sounds (P5): `holo.open` (K menu, volume 0.5), `holo.open_quick` (inventory, 0.3), `holo.close` (0.4 / 0.22),
  `holo.hover` (0.18), `holo.card`; synthesised by `tools/art/specs/audio_holo.py` in the System's glass voice (D major
  pentatonic), UI loudness (−24 ± 3 dBFS RMS, true peak ≤ −3), Interface slider. While the projected look draws,
  `signal.open` (a card arriving) plays as `holo.card` instead, never both. The hover tick sounds once per new target
  (a K-menu tab, an inventory slot holding something, the recipe key), at most every 55 ms, never while a panel still
  unfolds or a drag spreads items.
- Calm HUD tier 1 (P5): `awaken:power_bar` and `awaken:level` fade to 35% after 3 s with nothing needing them (out of
  combat as `SystemHud.inCombat` judges it, energy full, no cooldown or active power, no slot, level, XP or point
  change), over 1.2 s, and return in 150 ms. Drawn through an alpha-multiplying graphics around the unchanged layers;
  the gate run HUD never fades (its collapse timer must stay readable).
- Client settings (`awaken-holo-client.toml`): `statusStyle` (the projected look for the K menu, the System's cards and
  the first-power choice), `inventoryStyle` (HOLO | VANILLA), `hudMotion`, `hudFade` (on), `reducedMotion`.

Plan v2 · the Ritual of the Bell (the awakening as a cinematic; design docs/proposals/the-ritual.md, details in
docs/modules/onboarding.md "The Ritual of the Bell"):
- The story's Bell Crypt is one floor: an antechamber (the start room, the entrance sigil) and, through its doors, the
  hall (3x3 slots, vault 27, room type RITUAL, set piece RITUAL_HALL, painted by `gates.world.PiecesRitual`). The plan is
  marked by its boss `awaken:the_seated_one` (`GatePlan.RITUAL_BOSS`, content version 3); nothing rolls it. Scripted
  Bell Crypts from before (the Bell Warden, three floors) keep their floors and today's death. `GateHooks.openScripted`
  with 1 floor asks for the ritual plan.
- The ritual director (`story.Ritual`, saved in `data/awaken/ritual.dat`, schema 1) runs one timeline per hall: WAITING
  (until every unawakened player in the story who is online is in the hall, or 90 s after the first came in; 3 s grace
  for the last one), DOORS 9.5 s, BOW 9.5 s (kneel within 6 s or the nearest statue strikes), RING 18 s (three plates,
  4 s each to stand on), BE JUDGED 4 s, WAKING 8.5 s, JUDGEMENT 40 s (the gaze every ~8.5 s, statue swings every 2.25 s,
  at most 2 + players/2 at once, rune-ring pits every 6 s), END 11.5 s (the hand closes at 11.1 s, everyone dies at
  11.5 s), DONE. Time is processed by elapsed ticks with everything in between fired in order; the beat holds while the
  whole party is away and plays again from its start when someone is back.
- Nothing kills before the End: every hit on an unawakened player in a ritual gate (or sealed in a ritual) is clamped in
  `LivingDamageEvent.Pre` to leave half a heart; `/kill` still works. The old "killing blow inside the gate becomes the
  question" path is kept for old crypts and the no-gates fallback.
- StoryProgress schema 2 adds the prologue state RITUAL (sealed in). The story starts together: the first trigger opens
  one ritual crypt and points every other unawakened player online at it; a later trigger joins a crypt whose doors are
  still open; whoever isn't inside when the doors close gets their own crypt 15 s later. Awakened players, and players
  whose story points elsewhere, can't walk in (`StoryHooks.refusesScriptedGate`).
- The Seated One and the twelve statues (`mobs.Ritual`, role RITUAL) have no AI of their own, can't be hurt and never
  count as kills, trophies, Echoes or bosses. The gate is released once nobody is at the question for it any more.
- The god is seen at every beat (2026-10-24 review: a player who has never seen the hall knows within two seconds a
  colossal seated god is there, before its eyes open). `SeatedOne` carries a synced key light (0..15, eased on the
  client) and runes (0..100) set by the director: key 11 while waiting, 2 when every flame dies, back to 15 as the
  candle line reaches the dais (2 + 13·(t-80)/90 ticks into DOORS), 15 through the Judgement, down to 7 as the End
  drains it, 0 after (block light only). Runes (the emissive layer `the_seated_one_runes.png`: halo, crown, collar,
  pendant, chest sigil, veins of light) 30 waiting, 60 in the black, then 62 / 70 / 82 for BOW / RING / BE JUDGED, 82→100
  as the eyes open, 100 through the Judgement (breathing), drained to 15 by the hand's close. The renderer lights the
  colossus with the key light, never darker than the dais candles. Client effects, all without Lens: a great warm glow
  on the wall behind the throne (the god stands dark against it), two dusty shafts from the vault onto its lap (once the
  hall is lit), a flame on every candle of the candle line, eyes a block across (4x3 model px at x4) with a face halo
  and a long glint, and the camera framing the face as they open (FOV x0.4 at the peak, pull 0.95; while the candle
  line reaches it the view is drawn up to the whole colossus, pull 0.55). The bell over the altar hangs from 17 (was
  13) so its bronze never hides the face from the doors. The black and the tear show no hand. The hall's hidden
  fill lamps light with the candle line (3 ticks after the candles beside them), so the line lights the nave.
- The scene is the whole screen: from the doors closing until 4 s after the tear, toasts wait (re-shown after, one
  every half second, at most 12, one recipe toast) and AWAKEN's HUD layers step aside in the hall (vanilla's hearts stay for the
  Judgement); while the camera is held everything does. In the black only the System's words (the revelation's body
  without its question, drawn large and typed by the scene) and the question's card (a TRUTH card: the question and
  its keys, no caption) show; the tear frame is the tear and the world only. The gate card says nothing for a spent
  ritual hall and shows no "Breaks in" row for scripted gates.

Tests: GameTests that spawn mobs use still, fire-proof mobs and let a fresh test area tick a few frames before acting,
so results never depend on AI wandering, daylight or chunk-ticking timing.
- Ticks are not time on the GameTest server (it never waits between ticks), so no test waits a tick count for work
  done on other threads. Every test starts once its force-loaded 5x5 chunks entity-tick, and light levels are read
  after `AwakenTests.awaitLight` (the light engine has taken every block change so far); both wait in real time, at
  most 30 s, by running the chunk system's main-thread queue. Timing budgets assert on the fastest of a few runs.
  A test that needs untouched ground owns fresh ground away from the test grid (plots sit 6 blocks apart).
- `-Ptests=<name prefixes>` (comma-separated) registers only those GameTests; a filter that matches none fails.

---------------------------------------------------------------------------------------------------------------------

## 14. The Awakened Pack (companion mods; plan in docs/plan-pack.html, approved 2026-10-24)

- Awaken stays mixin-free and plays the same with no companion mod installed. Integrations live in `dev.awaken.compat`;
  each class is loaded only when its mod is present (`ModList` check), compiled against the mod as `compileOnly` from
  Modrinth's Maven, never bundled or required.
- The pack is `pack/mods.json` (profiles: perf, horizon, feel, sound, comfort, together, ultra, tools), pinned by
  `tools/pack.py lock` into `pack/lock.json` (exact files and SHA-512, required dependencies, a dependency's own
  version pin). `tools/pack.py install <game-dir> <profiles>` puts exactly those files into a game folder, removes only
  what it installed, and refuses while a client runs there. GameTests never load the pack: they test Awaken alone.
- Instanced dimensions (`compat.Instanced`): every `awaken:` dimension but `awaken:stillpoint` (gate and dream cells,
  trials, the Halo, the Descent's strata, the Endless). Companions that draw or remember a place stay out of them.
  Far view (`compat.FarView`): far-terrain mods draw in vanilla dimensions and the Stillpoint only; in instanced ones
  Distant Horizons renders nothing and gets empty columns instead of running Awaken's chunk generators. Test:
  `compat_instanced_dimensions`.
- Frame time is measured, not guessed: autopilot `perf <label> <s>`, `perfstart`/`perfend`, `info`; fixed scenes in
  `tools/autopilot/perf-pack.txt` at a 1440×900-point window; results and comparisons in `docs/perf/`
  (`tools/perf-report.py`). Every perf line carries the machine's load; runs that disagree by more than 5% on p50 are
  re-run.
- Looks: Swift (default; the SPEC budget above), Cinematic (p50 ≥ 60 fps, p99 ≤ 25 ms at 4K, to confirm after the
  baseline), Ultra (Iris, experimental: OpenGL only and unsupported on Apple Silicon by Iris). Chosen with the Look
  switch (below).
- Terrain mods (Tectonic) only in new worlds; existing worlds keep vanilla terrain. Maps: a world map, no minimap.
- Profile config files: `pack/mods.json` "configs" maps a profile to files the installer copies into the game folder
  (e.g. world → pack/tectonic/awakened.json as config/tectonic.json) only when missing, so a player's tuning (or a
  mod's own rewrite) is kept; `install ... --reset-configs` overwrites. The .mrpack export carries them as overrides.
- Lens (`dev.awaken.lens`), AWAKEN's light layer: the post chains `awaken:lens_swift` (haze, bloom, grade, vignette,
  dither; five passes, three of them at 960×600 or smaller, after two one-pixel passes for the far-depth trust) and
  `awaken:lens_cinematic` (adds light shafts). It runs
  first in the player's post-effect list, so gameplay effects (dreams, blight, the Veil) still draw on top, and
  re-asserts itself every tick because the server replaces that list when it syncs. Its values travel in a 32×1
  texture (`awaken:lens_params`), written once a frame in `ViewportEvent.RenderFog` at LOWEST priority: the look, the
  depth range (26.3 is reversed-Z; zero-to-one depth differs between OpenGL and Vulkan), the final fog colour and
  distances, the sun's screen position and the sunrise colour. Looks per dimension live in
  `assets/awaken/lens/looks.json` (schemaVersion 1; exact id, then `prefix*`, then `default`) and ease into each other
  over about 1.5 s. Lens never lifts shadows (exposure ≤ 1.05) and backs off where the game's own fog is thick.
  It steps aside while an Iris shaderpack is on. Client config `lens` = OFF | SWIFT (default) | CINEMATIC.
  With Distant Horizons drawing, Lens hazes far terrain by DH's own depth (Lens and far terrain, below); only without
  a trusted far depth (none shared, or not yet proven) does it add no distance haze while DH draws, since hazing only
  the near terrain would draw a ring where it ends.
  Story moments override the place's look (`LensClient.override(lookId, fadeTicks)` / `clearOverride`) with named
  looks from the same files (`awaken:ritual`, `awaken:void`); texel 13 carries the tear from black (`LensClient.tear`,
  0 off, 1..255 its progress), crushed blacks and extra bloom (`LensClient.ritual`). Texel 14 carries the Holo UI
  focus (`UiHooks.focus()`, 0..1 while a holo panel is open): the world more than about 2–3 blocks away dims to ~60%
  and cools, while the panel's glass (it writes depth) and its text stay bright; the first-person arm dims with the
  world, since its depth lives in a separate target post chains can't read. Texel 15 is far terrain's planes. Texel 16
  is motion (`LensClient.motion(strength, dirX, dirY)`, fed by the Ascent's Feel Director, §17.6): r strength 0..1,
  g/b the heading on screen (0..1 from the bottom left). The composite pass, only while strength > 0, takes 6 taps
  along the line to the heading (sharp at the heading, the smear growing toward the frame's edges) and 2 more for a
  touch of chroma at the rim; strength is eased per frame by real time, scaled by the FOV-effects slider, and 0 while
  a story override or the tear runs (Lens off: nothing; FxScreen's speed lines stay the fallback). Measured on the M4
  (Vulkan, 2880×1658): p50 +0.16 ms on Swift, +0.08 ms on Cinematic. Texels 17-31 are free. A story scene plays its look like a cutscene even while Lens is off: the chain runs for
  the scene's length and fades back to the plain frame (the look rests at neutral while Lens is off) before stepping
  aside again (client config `lensInScenes`, default true; never with an Iris shaderpack). Callers carry on without the
  look when Lens can't draw (`LensClient.active()`).
- Lens looks load from every `assets/<namespace>/lens/*.json`, merged by key in path order (modules add their own
  file, e.g. looks_lands.json, never edit looks.json). Key `biome:<id>` wins where the camera's biome matches (sampled
  every 10 ticks; biome-to-biome changes ease over ~3 s, dimension changes over ~1.5 s); a story override wins over
  both.
- The pack runs on Vulkan (2026-10-24, Euan: "just apply this if it's better"). `tools/pack.py install` writes
  `preferredGraphicsBackend:"vulkan"` into options.txt unless the player chose one (left at "default" or missing);
  `pack.py backend <game-dir> vulkan|opengl|default` sets it outright, and players switch back in Video Settings →
  Graphics API. Measured on the M4 with every pack profile's mods (perf, horizon, feel), back to back under the same
  load: p50 15-33% lower than OpenGL, Holo, Lens and the whole Ritual matching (docs/perf/vulkan/REPORT.md). The
  .mrpack doesn't ship options.txt (a launcher would overwrite the player's settings), so its players pick Vulkan in
  Video Settings. On Vulkan, Distant Horizons keeps its depth from Lens until it's checked there, so Lens adds no
  distance haze while DH draws (Lens and far terrain, below); Far Sight replaces DH and owns its depth.
- Sodium is pinned to 0.9.3-beta.1: 0.9.2 crashes at startup on NeoForge 26.3.0.95 (a LevelExtractor mixin no longer
  finds its target). Iris 1.11.7 requires exactly 0.9.2, so the Ultra look waits for an Iris update.
- Lens and far terrain: Distant Horizons draws without the game's depth, so Lens reads DH's own depth through
  `compat.client.FarTerrain` (texture `awaken:lens_far_depth`; DH's clip planes and depth convention in params texel 15
  and texel 0's b/a). Far terrain is hazed by its real distance (aerial perspective deepening to 8× the render
  distance), never counts as open sky for bloom, light shafts or the sun halo. Params texels: 13 the ritual, 14 Holo.
- Comfort (stage 5): the `comfort` profile is JEI, Jade, AppleSkin, Mouse Tweaks, Controlling, Dynamic FPS and Xaero's
  World Map. Xaero's World Map stays only until Carillon's own Atlas ships; no minimap, ever (direction comes from the
  world). AppleSkin, Mouse Tweaks and Controlling need nothing from Awaken (the comfort tour checks they load with no
  log errors). Tours turn Dynamic FPS off in `run-autopilot/config/dynamic_fps.json` (it drops a background window to
  1 fps or none). JEI and Jade are compiled against as `compileOnly` from Modrinth's Maven by version id (the builds
  `pack/lock.json` pins); their plugins (`compat.client.jei`, `compat.jade`, `compat.client.jade`) are found by their
  annotations and nothing else in Awaken refers to them.
- The spoiler rule (`compat.Discovered`, attachment `awaken:discovered`, schemaVersion 1, copyOnDeath, synced to its
  owner): an Awaken item is **discovered**, for good, once (1) it has been in the player's inventory or on their cursor,
  (2) it is the result of a recipe in their recipe book (Awaken recipes unlock when you hold an ingredient, by the level
  road, the Hearth Anchor and town requests), or (3) Awaken's own records name it: a scanned blueprint names its
  machine, the Frame Forge's blueprint names the four Frame pieces and six modules. Checked every 40 ticks per player
  (staggered, allocation-free) and at login; the set only grows. Vanilla and other mods' items are never guarded. Items
  looted into the Satchel or Deep Pack count once they reach the inventory. The guard is off in creative and spectator
  and when `spoiler_guard` (`awaken-compat-client.toml`) is off. Every viewer asks the same rule (`compat.SpoilerRule`,
  plain Awaken code taking any player): an item shows when it isn't Awaken's or is discovered, a recipe when any of its
  key items shows, a creature's details once the Codex has it, and disguises are never named. `compat.Reveal` keeps a
  viewer's hidden set in step and reports only what changed, so a discovery appears at once and nothing else is
  touched. `/awaken compat discovered [check|reset|all]`. Tests: `compat_spoiler_rule` (the record),
  `compat_spoiler_guard` (the rule and the reveal as the record grows, game modes, the Codex, disguises).
- JEI (`compat.client.jei`): Awaken's categories draw the System's glass (`signal.client.Glass.panel`, dark glass slots,
  a framed result in the recipe's accent, no JEI border): **Awaken Crafting** (every `awaken:` crafting-table recipe,
  moved out of JEI's Crafting tab, with the level road step that teaches it and, for Hunter gear, rank, set and set
  bonus; the crafting table's transfer works), **Fabricator** (the twelve blueprints, parts from `MachineType`),
  **Frame Forge** (Frames and modules, 50 essence), **Tempering** (levels I–VI at the Forge, Oren's terms), **Hearth
  Upgrades** (Anchor tiers II–V, Forge tiers II–III, rank and discount), **Echo Duty** (a Mine Echo's haul and chances by
  Anchor tier), **Essence** (charging a cell, feeding the reactor), **Core Fusing** (four shards into a core) and
  **Codex** (pages for key items and the six Hunter sets). The recipes are read from those systems' public constants
  (`compat.client.jei.Recipes`, the only place compat reads them). The guard (`SpoilerGuard`): undiscovered Awaken items
  are hidden from the list and from recipe slots (JEI's ingredient visibility, both contexts; a slot whose every
  choice is hidden still shows them, as vanilla's recipe book does); Awaken's recipes are hidden until their key item is
  discovered (a machine by itself, tempering by the Forge, a Codex page by its item); recipes in other categories (JEI's
  anvil, other mods') until any of their Awaken outputs is; a category whose stations are all hidden hides itself.
  Applied on the client tick only when the record, the game mode or the setting changes; a new client player (respawn,
  another dimension) keeps the current view up to 40 ticks until the server re-sends its record, which never shrinks.
- Jade (`compat.jade` server data, `compat.client.jade`): Awaken targets wear the Jade theme `awaken:system` (the
  System's glass as a nine-slice sprite, `tools/art/compat_ui.py`) and an accent bar in the target's colour (setting
  `jade_system_style`). Gate cores: "D-rank gate", state, break clock, omen, then exactly what the gate card shows that
  player (layout from PER 20, modifiers from 40, floors from 60, theme and Lord once scouted; the ritual hall only its
  bell) and the next PER threshold. Precursor machines: who built it, power (reactor essence or the reactors in range),
  a clone ready or growing, Launch Ring frames; the Fabricator counts known blueprints. Broken machines keep their name
  until the blueprint is yours; doors say the rank they open for; Hearth Anchor: owner, tier, reach, Forge; role pads;
  Trophy Mounts. Creatures: temperament from the fauna role (and "Hostile now"), level and rank (from the spawn's
  content level) and role (Elite, Warden, Gate Lord...); a creature the Codex doesn't have yet shows only "Not in your
  Codex yet". Townsfolk: job, whose town, the rank they came at. Jade never names mimics, dormant statues, the
  ritual's Seated One and statues or Veil marks, and decoys show their name only. Server facts come from
  `compat.LookAt` (plain Awaken code); without Jade on the server the client-only lines remain. Tests:
  `compat_look_at_gate_rule`, `compat_look_at_machines`, `compat_look_at_creatures`.
- Xaero's World Map is shut in instanced dimensions (`compat.WorldMaps`, 2026-10-24): a map of every chunk in view
  would read a gate's layout before PER 20 may, and a reused cell would show an earlier gate's rooms. 1.47.1 has no
  setting to skip a dimension (its ignore list is hard-coded, `MapProcessor.dimensionsToIgnore`; "load/update chunks"
  are global per config profile; per-player server profiles need a permissions mod; no API), so Awaken uses the
  control Xaero supports for servers: its own No World Map effect (`xaeroworldmap:no_world_map`, registered while
  Xaero's common option `register_status_effects` is on, its default). A player in an instanced dimension carries it
  (ambient, no particles, no HUD icon; the inventory lists it as "No World Map"), renewed every 40 ticks for 100 and
  taken off when they change dimension; an effect an operator gave is never touched. Xaero's map screen then reads
  "The map is disabled with a potion effect". Xaero still records those chunks into that dimension's own cache (the
  overworld map is never touched). The effect lives in the server's registry: single player always has it, a dedicated
  server needs Xaero's World Map installed (it is server-optional). Without Xaero nothing registers. Autopilot
  `compat map` reports it. Test: `compat_world_map_rule`. Until Carillon's Atlas replaces Xaero.
- Lens and far terrain: Distant Horizons draws without the game's depth, so Lens samples DH's own depth texture
  through `compat.client.FarTerrain` (texture `awaken:lens_far_depth`), with no copy: DH 3.3.4's Blaze renderer (the
  default engine on 26.3) clears that texture only at the start of its own pass (right after
  `DhApiBeforeTextureClearEvent`), so after it draws, the texture holds this frame's far terrain through the post
  effects. (The earlier reading "DH clears its depth before post effects" came from Lens sampling its own 1×1
  placeholder: `IDhApiRenderProxy.getDhDepthTextureBlazeWrapper().getWrappedMcObject()` is a reused `Object[]`
  {GpuTexture, GpuTextureView, GpuSampler}, which FarTerrain didn't unpack.) FarTerrain re-points from
  `DhApiBeforeRenderEvent` every frame without allocating and from `DhApiAfterColorDepthTextureCreatedEvent` (DH's
  resize, inside its pass setup, before anything samples), and never hands a post pass a closed view. DH's clip
  planes come from its projection matrix (`dhProjectionMatrix` m22/m23), not the event's `nearClipPlane` (DH clamps the
  matrix's near plane to 7.5 blocks). DH builds a reverse-Z matrix for 0..1 depth even where the device maps -1..1
  (OpenGL without clip control), so the shader turns stored depth back to NDC with the device's range (texel 0 g)
  before DH's convention. Params: texel 15 the planes (near in 1/16 block, far in blocks); texel 0 b far terrain drawn
  in the last 0.2 s, texel 0 a bit 0 reverse-Z, bit 1 zero-to-one, bit 2 a depth is shared. Far terrain is hazed by
  its real distance (aerial perspective deepening to 8× the render distance: DH keeps the fog's `renderDistanceEnd` at
  the vanilla value and pushes only the start and the environmental fog out of reach, so the haze runs on across the
  end of the vanilla render distance without a step), and never counts as open sky for bloom, light shafts or the sun
  halo. DH's own clouds (it turns vanilla's off) write its depth too, so they haze by their distance like far terrain.
  A shared depth must prove itself before Lens trusts it: a depth that reads as one value everywhere (a placeholder,
  a texture that doesn't sample on this graphics API, one that stays cleared) can't be told from sky, and trusting it
  would haze the sky as far terrain or leave a ring. Pass `lens_far_probe` (one pixel, first in both chains) samples
  the far depth at 8×6 points and trusts it once two of them where far terrain drew differ by more than 1e-5; the
  answer is latched in a persistent 1×1 target (`awaken:lens_far_trust`, written back by a blit) while a depth stays
  shared (bit 2) and cleared when none is (or when a resource reload rebuilds the chains), so a newly shared depth
  proves itself again. Once proven, the haze over far terrain eases in over 32 frames. Without a trusted depth (none
  shared, or not proven yet) Lens adds no distance haze while DH draws and far terrain counts as sky for bloom and
  shafts. The shaders share these rules in `shaders/include/lens_far.glsl`.
  Where DH shares its depth: only on the graphics API it was checked on (`DistantHorizonsClient.CHECKED_ON`, OpenGL),
  and never from its OpenGL engine (none to share). On Vulkan DH keeps it to itself (logged once), so there Lens adds
  no distance haze while DH draws: never a ring, never a wrong haze. Why unchecked: the Vulkan check
  (docs/perf/vulkan/REPORT.md, "reads as one constant value") ran on a build without the unpacking fix above (ce91c76
  predates e719e4d), so it read Lens's own 1×1 placeholder, created without data (0 on OpenGL, whatever the memory
  held on Vulkan), not DH's depth. Autopilot `fardepth share|auto` shares it on every API for the check: the last
  shots of tools/autopilot/lens-dh.txt on Vulkan show bands and blue 1 in view 3 if DH's depth works there (then
  CHECKED_ON takes Vulkan too), blue 0.6 if it reads as one value. `FarTerrain` stays generic (a depth texture, its
  planes and convention, the frame it drew; no companion types): Awaken's own far terrain reports the same way and
  proves itself the same way.
  Debug views (`lensdebug`): 3 raw far depth (blue while far terrain draws: 1 Lens trusts its depth, 0.6 shared but
  not proven, 0.3 none shared), 4 the haze each pixel gets (a ring would show as a step in brightness). Proof:
  tools/autopilot/lens-dh.txt (2026-10-24, OpenGL on the M4, DH Swift, render distance 16: depth read, far terrain
  intact, haze across the vanilla edge within ~0.03 of itself away from silhouettes; logs/lens-dh-sheet.jpg). Params
  texels: 13 the ritual, 14 Holo.
- The Look switch, AWAKEN · LOOK (`compat.client.LookScreen`; the rules in `compat.Look`): three tiers, and one choice
  sets every companion that offers a supported way, writing only the keys below, so each mod's own menu still works
  for the rest. Opened with U (`key.awaken.look`, Awaken's key category), from Awaken's settings (Mods → AWAKEN →
  Config → the client section's "Look" row, under Lens) and by autopilot (`screen look`, `lookswitch <tier>`). The
  default stays SWIFT (client config `lens`). The current tier is read back from the settings (Lens, Iris's shaders
  switch), not stored twice. Drawn like the System's cards (`HudLook`: projected glass that scans in while the holo
  look is on, flat `Glass` otherwise); not a reading screen, so the world behind stays undimmed and a choice shows at
  once.

  | Owned key | SWIFT | CINEMATIC | ULTRA |
  |---|---|---|---|
  | Lens `lens` (awaken-client.toml) | SWIFT | CINEMATIC | CINEMATIC (steps aside while the shaderpack draws) |
  | DH `lodChunkRenderDistanceRadius` | 128 | 192 | 192 |
  | DH `maxHorizontalResolution` | TWO_BLOCKS | BLOCK | BLOCK |
  | DH `verticalQuality`, `horizontalQuality` | LOW, LOW | MEDIUM, MEDIUM | MEDIUM, MEDIUM |
  | DH `enableSsao`, `enableTexturedLods` | false | true | true |
  | DH `enableAntiAliasing` (file only) | false | true | true |
  | Iris shaders switch | off | off | on |
  | Sodium, vanilla options | none | none | none |

  DH values are pack/dh/swift.json and cinematic.json (a GameTest compares them); keys the tiers agree on (threads,
  cloud layers) aren't owned. DH: the six keys its 3.3.4 config API offers are set at once
  (`IDhApiConfigValue.setValue(value, "AWAKEN Look")`, an API value DH never saves; while it's set, DH's own menu shows
  those six as "API LOCK", for the rest of that session), and all seven are written into config/DistantHorizons.toml
  (matched by table and key, every other byte kept; written again on `GameShuttingDownEvent`, since DH can save its
  in-memory settings over the file meanwhile), so after a restart they're plain DH settings and no API value is set.
  Anti-aliasing applies when DH next reads its file (its menu or a restart). Sodium: no supported route (its config API
  only adds option pages; its options file is in use while the game runs), so it keeps its own settings and the
  screen says so. Iris: `IrisApiConfig.setShadersEnabledAndApply`; the shaderpack stays the player's. Ultra is offered
  only with an Iris newer than 1.11.7 on the OpenGL renderer; otherwise its card is disabled with the reason, which
  always says Iris needs OpenGL (and names the API the game runs on, Vulkan for the pack) and, until a newer Iris is
  installed, Iris's Sodium pin: Iris 1.11.7 pins Sodium 0.9.2, which crashes on NeoForge 26.3.0.95. With a newer Iris
  on Vulkan it says where to switch (Video Settings → Graphics API, then restart). At most 180 characters, the Look
  screen's three note lines. The frame-time line: the last 60 s of
  frame-to-frame times (`RenderFrameEvent.Post` into a 32768-int ring, no allocation), p50 and p99 from a 0.1 ms
  histogram twice a second while the screen is open, green while they fit the tier's budget (Swift p50 ≤ 9.1 ms and
  p99 ≤ 16 ms; Cinematic ≤ 16.7 and ≤ 25 ms; Ultra promises none), and "HORIZON n CHUNKS" with DH ("off in this
  place" in instanced dimensions). Tests: `compat_look_tiers`, `compat_look_dh_file`, `compat_look_ultra_gate`,
  `compat_look_frame_stats`; tour tools/autopilot/look.txt.

---------------------------------------------------------------------------------------------------------------------

## 15. Awakened Lands (terrain, biomes, trees, living foliage; plan in docs/plan-lands.html, approved 2026-10-24)

- Shape comes from Tectonic (MIT), pinned in the pack's `world` profile with the Awakened preset `pack/tectonic/awakened.json`
  (chosen in the studio 2026-10-24: scale 1.35, boost 0.25, erosion 0.2, ridges 0.22, continents 0.08, ocean offset
  −0.55; results in pack/tectonic/README.md); Awaken never forks it
  unless the studio demands it. Terralith and Continents are never used (their licence bans AI projects). World height
  stays vanilla (−64 to 320); 384 only if the studio shows peaks capped.
- Awakened biomes take about 40% of the land. The first six: Elderwood (Greenwild), Lumen Forest (Duskwood), Glass
  Steppe (Sunscorch), Shattered Highlands (Crag), Frost Spires (Frostwild), Mirror Marsh (Mire). Rift Canyons are a
  landmark feature across biomes. Placement: Lithostitched biome injectors first, TerraBlender if they fall short.
- One biome table (stage 1) gives every biome, vanilla or Awakened, its family, spirit hint, lair and ruin rights,
  cottage palette, music, ambience and Lens look; every Awaken system reads tags generated from it. A census test fails
  the build if any biome lacks a row.
- New worlds only, by feature flag: everything Awakened (biomes, their placement, their tag memberships, trees) lives
  in the built-in data pack `datapacks/awakened_lands` (`dev.awaken.terrain.LandsPack`), which asks for the feature flag
  `awaken:awakened_lands` (declared in META-INF/feature_flags.json). A world being created may turn on any flag, so it
  loads the pack and records the flag; a world made before the flag existed lacks it, so the game skips the pack there
  and its terrain and tags never change. The pack shows in the Data Packs screen at world creation (players may turn
  it off). Test: `terrain_lands_pack_new_worlds_only`.
- Placed today (2026-10-24): Elderwood (replaces forest, birch, old-growth birch and flower forest), Lumen Forest
  (dark forest), Glass Steppe (savanna, savanna plateau, windswept savanna), Shattered Highlands (windswept hills,
  windswept gravelly hills, windswept forest, stony peaks), Frost Spires (snowy plains, snowy taiga, grove, ice
  spikes) and Mirror Marsh (swamp, mangrove swamp), each where the awakening field passes its threshold (below).
- An Awakened biome's own ground is a material rule in the lands pack (`worldgen/material_rule/lands/<biome>_surface`
  and `_under`), spliced first into the overworld's `biome_surface` and `under_biome_surface` by Lithostitched
  `set_material_rule` modifiers (`injection_type: prepend`, guarded by its biome), so the surface pass lays it and far
  terrain shows it; a biome that owns its whole floor, under water too, has a `_floor` rule spliced first into the
  overworld's `surface` instead. Glass Steppe: fused glass where the noise `awaken:lands/glass` ≥ 0.3, else sun-gold
  grass; Shattered Highlands: riven shale on steep ground and where the `minecraft:surface` noise ≥ −0.05, else grass;
  Frost Spires: hoarfrost where `awaken:lands/rime` ≥ −0.3, else grass (snow never settles on hoarfrost); Mirror
  Marsh (`_floor`): still water at y 62 where `minecraft:surface_swamp` ≥ −0.2 (vanilla's swamp rule with more of the
  noise), mirepeat on dry floor where `awaken:lands/peat` ≥ −0.45, else grass, mirepeat under water and under it all.
  A block in a material rule is a block id or an `{"id", "properties"}` map (26.3's `BlockState` codec; never
  `Name`/`Properties`), and 26.3 has no `all_of` condition: conditions nest.
- Shattered Highlands' faults (`terrain.fault.FaultLinesFeature`, `awaken:fault_lines`, local modifications, once a
  chunk over its own columns): the zero line of `awaken:lands/fault` (octave −8, five octaves); a column's distance to
  it is the noise over its slope (sampled on a 2-block lattice, bilinear), faultstone within 1.6 blocks (three deep),
  the seam within 0.55; where `awaken:lands/fault_trench` passes 0.15 the fault widens (by up to 2.8 a side) and opens
  into a V trench up to 9 deep. Decisions come from the noises and the column only (continuous across chunk borders);
  never into water or down to the sea. Faultstone and the seam are in `#minecraft:cannot_support_snow_layer`.
- Landmarks that are not trees are structures of type `awaken:landmark` (`"landmark": "gate_scar" | "shard_drift" |
  "aurora_spire"`, `terrain.landmark`), biomes from the table's `awaken:has_structure/<landmark>`: the Gate Scar (Glass
  Steppe; a glassed bowl 30–50 across, spires round its rim, the gate's stumps; spacing 28, separation 10) reshapes its
  ground column by column from each column's own ground and its distance from the centre; the Shard Drift (Shattered
  Highlands; 7–12 floating shards along a fault; spacing 22, separation 8) walks its site onto the fault line and
  samples the ground under each shard once at the start; the Aurora Spire (Frost Spires; a frostspar needle 42–58 tall
  on a frozen tarn 17–27 across, 4–6 lesser needles and rim sheaves, branches at about 55°; spacing 26, separation 9)
  freezes its tarn level with the centre's ground column by column (cut or built up at most 5, packed ice two layers
  at least, a hoarfrost ring). The Gate Scar and the Aurora Spire share one level-site search
  (`LandmarkStructure.levelSite`: dry, at least 12 / 2 over the sea, within 5 / 4 of the centre's height at 16 / 12
  out and 8 / 6 at 28 / 20 out, 50 / 70 under the build limit). All place voxels into air, plants, snow and water only
  and never write outside their box; shapes are cached (8 each). Budgets as hero trees (shape ≤ 2 ms on the fastest of eight seeds, as
  `terrain_landmark_shapes` asserts; features ≤ 0.5 ms a chunk on average);
  `/awaken terrain landscost` reports what they cost on the worldgen threads.
- Air for the second and third pairs: an air.json biome rule's `motes.kind` hands the spot to
  `terrain.client.LandsMotes` (cap 120 × the leaves' frame-budget scale): `glint` (Glass Steppe, by day: a star
  blinking on sunlit glass of `#awaken:lands/glints`), `fault` (Shattered Highlands: sparks off `#awaken:lands/faults`
  at night, dust by day), `frost` (Frost Spires: snow blown sideways on the wind, thicker in gusts; at night three in
  ten spots a cold glint on `#awaken:lands/frost_glints`) and `mist` (Mirror Marsh: wisps just over open water, 0.35 +
  0.65 × night of the spots).
- Hero trees may stand in water: `HeroTree.wetDepth` (0 for dry-land trees; the Weeping Matriarch 3) lets the site
  search accept ground under at most that much water and no deeper than that below the sea (`HeroStructure.standing`);
  the piece ends hanging strands at the water's surface (the last one above it a tip) and asks the tree what floats on
  open water under its crown (`HeroTree.onWater`: the Matriarch's moonblooms). The Weeping Matriarch (Mirror Marsh;
  trunk 4–6 across and 22–30 tall on 6–9 prop roots, 5–7 bowing limbs under low domes, a curtain of tendrils, about
  45 across; spacing 24, separation 9) keeps every tube at least 0.9 thick: within 0.87 of an axis every cell joins the
  axis face to face, so no block touches the tree only at an edge.
- Placement: Lithostitched `replace_partially` injectors swap a vanilla biome for an Awakened one where the
  `awaken:lands/awakening` density function (a low-frequency noise, patches roughly 500–1,500 blocks across) passes a
  threshold, so Awakened lands come in regions you walk into, each in the climate of the biome it replaces. Thresholds
  are tuned with the studio until Awakened biomes cover about 40% of land on the five studio seeds. Tuned 2026-10-24
  (`AWAKENING` in `tools/content/lands_content.py`): Elderwood −0.22, Glass Steppe and Frost Spires −0.27, Lumen
  Forest and Shattered Highlands −0.34, Mirror Marsh −0.41 (the field's deciles over the archetypes' land run −0.41,
  −0.27, −0.17, −0.09, −0.01, 0.07, 0.15, 0.25, 0.40, so −0.27 claims about 80%). Forests are the commonest archetype,
  so Elderwood takes less of them and stays one Awakened biome among six; the rare archetypes give more. Measured:
  40.6% of land under Tectonic's `awakened` preset (28–50% a seed; 24.2% when every threshold was 0), 41.5% on
  vanilla shapes; about a fifth to a third of each archetype stays vanilla. `terrain_lands_placement` fails outside
  34–48% on vanilla shapes.
  `terrain_lands_placement` holds the injectors to the biome table (one per Awakened biome, its archetype among its
  vanilla targets, no target claimed twice, ground splices guarded by their biome) and measures the share on the
  five studio seeds by applying the injectors to the vanilla generator as Lithostitched does.
- The biome table (stage 1): `tools/terrain/biomes.py` is the one source (`write`; `check` fails on a stale file, a
  vanilla overworld biome without a row or a lands-pack biome without one). Base Awaken data refers to biomes only
  through the tags it writes, listing vanilla biomes exactly as before: `awaken:fauna/<group>` (one per fauna spawn
  modifier, which names `#awaken:fauna/<group>`), `awaken:has_lair/<beast>`, `awaken:lair_style/<style>` (where a lair
  wears its second look: red sand, snow, pale oak, coral, moss; the Glass Hollow's keeps `#minecraft:is_badlands`) and
  `awaken:cottage/<plan>` (every overworld biome has one; another mod's biome falls back to the old vanilla and `c:`
  tag checks). The lands pack adds each Awakened biome to the same tags (tags merge across packs) plus every
  `minecraft:`, `c:` and `awaken:` biome tag its archetype is in (nested tags resolved, so vanilla structures and the
  Precursor ruins come too), to `awaken:archetype/<vanilla biome>`, and gives it the archetype's villager type; the
  script owns every biome tag of the lands pack. An Awakened row is its archetype, its family (the fauna group it joins,
  even where the archetype lacks it) and tags to add or drop: Elderwood is the old growth birch forest plus the
  Greenwild and the Briar Sow's lair. Code that still reasons by biome name (spirits) asks
  `dev.awaken.terrain.api.BiomeTable.archetype(biome)` first. `terrain_biome_table_census` fails if an Awakened biome
  lacks an archetype, a fauna group (and live spawns), a cottage plan or spirits, or is not in `#minecraft:is_overworld`;
  `terrain_biome_table_parity` holds every vanilla overworld biome to the spawners, lairs, cottage plan and lair looks
  it had before the table.
- Trees: data trees keep canopies within about 12 blocks of the trunk (a feature may write one chunk beyond its own);
  giant hero trees are structures. No ticking or growing trees. Awaken logs and leaves join `#minecraft:logs` and
  `#minecraft:leaves`. Vanilla leaf textures are untouched; Awaken trees get their own art.
- Life: wind from world time and seed (same for every player, no packets) drives falling leaves (a fixed client pool,
  about 150 at once, scaled by the frame budget), petals, dust and leaf rustle. Waving leaves only if the Sodium spike
  proves them safe; if not, wind, leaves and sound ship without them.
- Wind (`dev.awaken.terrain.Wind`, common): salt = fingerprint of the hashed world seed through the level's
  BiomeManager (server and clients agree); a heading per 2000 ticks with a 400-tick turn, headings from a slow noise;
  steady 0..1 (0.2 + 0.45 noise over 170 s, + 0.3 rain, + 0.3 thunder); gust fronts 0..1 rolling downwind at
  7 blocks/s; strength = min(1, steady (0.6 + 0.8 gust) + 0.25 gust). One field per client feeds the waving shader,
  the falling leaves and the rustle. Tests: `terrain_wind_deterministic`, `terrain_wind_ranges`.
- Falling leaves (`terrain.client.FallingLeaves`): Awaken's own particle, spawned from leaves with air below 6-32
  blocks out (70% toward the view), chance 0.06 × biome rate × leaf rate × (0.25 + 1.5 strength); caps 60/150/300 by
  client setting `falling_leaves` = off|low|normal|high (default normal) × a frame-budget scale (1 at ≥ 50 fps,
  0.25 by 30 fps); tinted from the leaf's BlockColors tint over its texture's average; tumble, flutter, wind drift,
  1-3 s rest, 1 s fade. Per-block and per-biome rules in `assets/awaken/lands/air.json` (schemaVersion 1): forests
  1.0, elsewhere 0.5, cherry 0 (vanilla petals), Elderwood 3.0 gold, Lumen Forest motes at night only.
- Leaf rustle (`terrain.client.LeafRustle`): `amb.lands_rustle` under canopy and `amb.lands_wind_high` high and in the
  open, volumes from the wind and a 16-column canopy ring; client setting `leaf_rustle` (default on).
- Waving leaves (spike done 2026-10-24, docs/spikes/waving-leaves.md: safe, cost within noise, at most ~0.05 ms p50 in
  a dense dark forest): `dev.awaken.terrain.client`. A built-in client pack (`resourcepacks/awaken_waving_leaves`)
  replaces Sodium's terrain vertex shader, offered only when Sodium is exactly 0.9.3-beta.1+mc26.3 with terrain shader
  sources of a known SHA-256, Iris is absent and client setting `waving_leaves` (`awaken-terrain-client.toml`, default
  on) is on; otherwise nothing is installed (vanilla renderer: no waving). Marker: every `LeavesBlock` model is wrapped
  after baking with vertex colour alpha 254 (textures untouched). Wind: texels at the block atlas's bottom-right
  corner written each frame (signature, time mod 2048 s, camera, wind, the gust profile along the wind from
  `terrain.Wind`): 22 texels.
  Amplitude 0.11 blocks at full wind and gust. `/awaken terrain waving on|off|status` (client).
- Budgets: forest p50 within the Swift budget at 2880×1800 (the darkest Tectonic forest already measured 8.7 ms on
  2026-10-24, so new foliage pays for itself); any feature ≤ 0.5 ms per chunk on average, hero-tree structures ≤ 2 ms;
  chunk generation and a 64-chunk Distant Horizons build within 20% of plain Tectonic. Features are safe off the main
  thread (Distant Horizons runs them in 4×4-chunk batches). Far terrain shows ground blocks, logs and leaves only, so
  each Awakened biome has a recognisable ground block and canopy shape.
- Gates between stages are judged from the terrain studio (contact sheets on fixed seeds and survey numbers), not by
  playtests; Euan gets the sheets as progress.
- Terrain studio (stage 0): `/awaken terrain survey <name> [radius] [step]` samples the generator's surface height and
  surface biome on a grid (no chunks generated, worker threads) and writes `terrain/<name>.json` in the game folder;
  `tools/terrain/survey.py` turns it into a biome and relief map plus numbers (biomes per km on eight transects,
  relief per 512-block window, land share, biome shares). `/awaken terrain visit <biome|#tag>` stands you in the
  heart of the nearest surface patch of that biome; `/awaken terrain lookout [radius]` takes you to the highest ground
  nearby; `/awaken terrain at` prints the biome, height and climate where you stand. Tectonic presets live in
  `pack/tectonic/<preset>.json` and an autopilot run picks one with `TECTONIC=<preset>`.
- City basin (Carillon, requested 2026-10-24): the city calls `CityBasin.request(rule)` (its rule: the world has the
  feature flag `awaken:carillon`, which its pack asks for and Classic start turns off). At `ServerAboutToStart`, before
  any level exists, terrain picks the site from the overworld generator and the seed (`terrain.city.SitePicker`: land
  within ~1,440 blocks of the origin, ground near y 70, gentle across the core, low along the city's river, near street
  level on the ring at 520; about 2 s). The lands pack wraps the overworld's `final_density` (router) and
  `minecraft:overworld/preliminary_surface_level` (what aquifers and surface rules read) with `awaken:city_basin`:
  flat at y 70 out to 330 blocks with 20 solid blocks under the street (caves and ores carry on below), the city's fixed
  river carved to y 57 and 18 wide (water to sea level 63: the surface estimate reads 8 below the floor there, the aquifer's test for open
  sea-level water),
  continuing straight past the core and fading out 160 blocks beyond it; everything eases back to the natural terrain
  by 700. No site (old worlds, Classic start, no Lithostitched) means the function is its input. Lairs and Precursor
  ruins skip `reserved(x, z)`; the city wires its runtime systems (gates, anomalies, landmarks, spawn vault, the Well).
  Terrain never sets spawn (the city does, through `LevelEvent.CreateSpawnPosition`). Dev switch for tours before the
  city exists: `CITY=on` (writes `config/awaken-terrain-dev.properties`); `/awaken terrain city` goes to the site.
  Tests: `terrain_city_basin_shape`, `terrain_city_site_deterministic`.

---------------------------------------------------------------------------------------------------------------------

## Together (multiplayer; pack plan stage 6, Euan's decisions 2026-10-24)

"Story starts together, shared Hearth with members with options to expand one into multiple over time." The audit and
what was fixed: docs/multiplayer-audit.md. Every rule here holds for one player alone exactly as before (a party of
one, a Hearth with no members, a world whose story only they reach); `together_old_saves_load_the_same` checks it.

Parties and the bridges:
- `core.api.PartyHooks` (the city's v0 hook, verbatim): `members(Player)` (the player included), `sameParty(Player,
  Player)`. Until the guild module installs real parties, gates installs the rule: a player's party is everyone inside
  the gate run they are in, plus everyone who shares a Hearth with them; alone, a party of one. Sealing and shared
  rewards ask `PartyHooks` only.
- `core.api.HearthHooks` gains `belongs`, `hearthsOf`, `household`, `shareHearth`, `within`; `hearthOf` is the
  player's home (their own Hearth, else the first they share). `core.api.StoryHooks` gains `gatesCalm(server, owner)`
  and `ascension(player)` / `ascensionOf(player)`. `core.api.MobHooks` gains `bondedTo(entity)`.
- Dedicated server: client-sent actions are rate-limited per player (`core.Throttle`); `/awaken as <player> <any awaken
  command>` runs it for that player (game masters, the console, command blocks; `<player>` is a name or a selector, as
  vanilla's player arguments take it, never a UUID); `core.RegionRecycler` erases a batch
  of chunks from region files while running, once none is in memory on two checks at least 10 s apart (written as "no
  data" through the level's own chunk IO worker), so gate and dream cells are reused without a restart.

A shared Hearth (members, and growing into several):
- A Hearth has one owner and any number of members (`HearthRecord` household, schema 1; a Hearth saved before it
  loads with none). A player owns at most one Hearth and may be a member of any number.
- Joining: the owner runs `/awaken hearth invite <player>` (a choice card, open 2 minutes; `/awaken hearth join
  <owner>` answers it too), or someone who doesn't share a Hearth uses its Anchor and the owner, if online, gets a
  choice card "<name> asks to share your Hearth" (once per 30 s per asker). Leaving: `/awaken hearth leave [owner]`;
  the owner takes it back with `/awaken hearth remove <member>`; `/awaken hearth members` lists them.
- Members, like the owner: use the Anchor (the guide, request deliveries, waking there: the Hearth whose Anchor you used
  last, `HearthPlayer` schema 2 `respawn_hearth`), rest and heal there, build there with powers, see there with spirit
  sight (and their household's beds are not hidden), get its Trophy Hall bonus, are served by its townsfolk, get its
  break warnings, "The Hearth held" and the Hearth Shield of anyone online who holds the perk, and may build its Anchor
  and Forge upgrades (paying themselves). Only the owner moves the Anchor, invites and removes, and has Oren's cottage;
  role pads are worked by the owner's Echoes.
- The town follows the household: townsfolk arrive and leave by the highest rank among its owner and members online,
  every member online hears who came, and a member's rank-up is celebrated when they are home (`cheers`).
- When an owner breaks their Anchor, the household is kept (`HearthData` schema 2 `kept`) and shares it again where the
  Anchor next takes root. An owner moving home this way may always place it again, and it never becomes a sister home.
- Growing into several Hearths: from Hearth tier III a member's own Anchor founds a sister home: its founder owns it and
  everyone else of the first Hearth (owner and members) is a member of it (`founded_from`). One sister at tier III,
  two at IV, three at V. Before that, a member who owns no Hearth is told the rule when placing an Anchor: share until
  tier III, or leave to found an unrelated one.
- Public API (`dev.awaken.hearth.Hearth`): `record HearthInfo(UUID owner, String ownerName, BlockPos anchor, int tier,
  int radius, List<UUID> members, Optional<UUID> foundedFrom)` with `has(UUID)`; `hearthsOf(MinecraftServer, UUID)` and
  `hearthsOf(ServerPlayer)` (own first, then shared, in join order; works for offline players); `home(ServerPlayer)`;
  `isMember(ServerPlayer, BlockPos anchor)`; `isMember(MinecraftServer, UUID player, UUID hearthOwner)`;
  `hearthAt(ServerLevel, BlockPos)`. `hasRoom`, `anchor` and `atHome` read the home Hearth.
- Above machines answer to their owner, anyone who shares a Hearth with the owner, and anyone using one that stands
  inside a Hearth they belong to (machines with no owner, ruins and old saves, answer to everyone); binding a Clone
  Vat never changes its owner, so a household shares one clone.
- A Hearth member's kill near friends: see "Shared rewards" below (Echo binding is one bind roll per player).

The world's story (one story, shared; friends who join late go at their own pace):
- One ending per world. The first player to choose at the Seed decides it (`StoryWorld` schema 2: who chose, the
  play-day, and every player who reached it with the Ascension tier they took part in). Anyone who reaches the Seed
  after that gets no choice card: they are told the world's ending (its lines, "This world's ending: <name> chose it",
  the credits) and their own story finishes as before (the Sovereign Reassessment, the Endless, a new thing). Players
  still choosing at the same Seed when the first one chooses are told at once.
- The ending's effect applies once: "Become the Signal" raises the world's Ascension tier by one (the world's only
  milestone; nothing raises it per player), "Break the Signal" calms the gates, "Wake up" restores the colour.
- Own tier: the Ascension tier a player meets is min(world tier, the tier recorded when they reached the ending), 0
  before that. A creature is scaled when it appears for the nearest player within 64 blocks (the world's tier when
  nobody is near) and keeps that tier (`awaken_ascension`); every hit between it and another player is evened out to
  that player's own tier: its melee ×(1 + 0.15·mine)/(1 + 0.15·its), the player's hits on it ×(1 + 0.3·its)/(1 +
  0.3·mine). XP is ×(1 + 0.15·own tier). Projectiles were never scaled and aren't evened out.
- Calm gates: a gate never breaks once the world's gates are calm, unless it was rolled for a player who hasn't reached
  the world's ending: theirs still break (and look it). A gate with no owner follows the world.
- Old saves: a schema 1 world records each player who chose an ending as having reached it at the world's tier when
  they log in, so a single-player world plays exactly as before.
- The Rival's beats are one player's scenes: only that player sees Vael (`VaelEntity` keeps its viewer).
- Admin: `/awaken admin story ending` tells the ending once the world has one; `/awaken admin story ending reset` gives
  the world its story back (tests and tours).

Gates in a party:
- Sealing: when an arena or the Lord's room seals behind someone, party members of the same run on the same floor
  within 48 blocks step through with them; a party member who reaches the bars of a sealed room their party is fighting
  in is let through.
- Size: n = players of the gate on that floor (1 to 4). Room groups get +35% monsters per extra player (rounded) and +1
  elite per two extra players; the Lord +50% health per extra player (its hoard is already one per player). Scripted
  gates (the story's) never scale.
- "Solo" (the `gates_solo_no_echo` counter) means nobody else was inside the gate at any point of your run
  (`RunState` schema 2 `company`), not that the gate's all-time party set has one name.
- The Director rolls per owner: the 1 to 4 standing gates are counted per player (a gate with no owner on record counts
  for whoever is near, as before); a gate rolled for someone never forms within 256 blocks of a Hearth they don't own
  or share, and a gate whose break comes due within 256 blocks of a Hearth its owner doesn't share (one placed after
  the gate formed) folds away instead of breaking. A veteran's A-rank gates and breaks never land on a newcomer's home.
- Cells: a released gate cell is erased and reused while the server runs (`RegionRecycler`); the registry (schema 2)
  keeps each cell's generation, entities stamp theirs (`awaken_cell_gen`), and one saved with an older generation of
  its cell leaves when loaded. The stop-time cleanup still deletes whatever was left.

Shared rewards (credit the party in range; separate rolls per player; `core.api.Parties.sharing` is the one rule for
who: party members online, alive, not spectating, in the same level within 32 blocks of the kill):
- A gate kill: every party member of that run on the same floor within 32 blocks gets their own kill XP share (from
  their own floor budget, so a party never earns more per player than alone) and their own elite loot roll into their
  own Satchel. Credit falls back to whoever last hurt the creature (fire, a fall, an Echo finishing it).
- Descent kills and overworld (home) kills: party members within 32 blocks get the same XP, each through their own
  budget. A bindable kill leaves each party member within 32 blocks their own lingering Echo (their own bind roll).

The Veil, the Lucidium and the rest:
- A projected Body takes no damage from players: never from its own spirit or a party member, and from anyone else only
  while the world's `pvp` game rule is on. A Body felled by a player who may not hurt it never takes its owner along.
- Only its owner possesses a bonded mount (as with tamed pets; `MobHooks.bondedTo`).
- Overworld creatures' content level follows the group: the lowest level among awakened players within 48 blocks of
  the spawn decides (then the nearest player within 128, as before).
- The cast-fail sound plays to the caster only.
- Rate limits: Journal actions 4 a second, reality checks one per 10 ticks, the playtest "unfair death" flag one per
  2 s; background dream builds at most 3 per player per 1000 ticks, whatever asks for them.
- Lucidium cells are reused: when a dream ends its cell is erased (the chunks it generated, once out of memory) and
  handed to the next dream; anything but a player loaded from disk in the Lucidium stays only if it belongs to the
  dream running in its cell. A plan that is never dreamed gives its cell back the same way: thrown away at morning,
  replaced when its player changes their intention (a build still compiling gives it back when it completes), or taken
  by someone who then couldn't enter it. A sleeper about to dream a plan the planner lets go keeps it, and their night
  gives it back if they never dream it. A cell is handed out again only once every chunk it generated is erased,
  including chunks that were still generating when it was let go.
- Statics cleared on server stop: Echo duty bookkeeping, breaks near Hearths, Bodies' owed deaths, Hearth offers and
  invitations, throttles, queued region erasures.

---------------------------------------------------------------------------------------------------------------------

## 16. Carillon (the walled city, the opening run, MMO-lite; plan in docs/plan-carillon.html, approved 2026-10-24)

Euan approved the plan on 2026-10-24 with every recommendation ("Go with all recommendations and the full plan"), and
confirmed the movement change in the same message ("ranks improving the moves … everyone a 'Runner' from the first
second … the Wall Run power becomes 'Wall Stride'"). Revised the same day for a lean build (docs/plan-carillon-lean.html,
decisions 14–19). Stage 0 closed at gate G0 on 2026-10-24; the measured values replaced the provisional ones (G0 marks
below now cite what was measured; details in docs/carillon/spikes/G0-notes.md).

### 16.0 Decisions (approved)
1. The Runner's Kit works from the first second; ranks improve moves instead of unlocking them; Wall Run → Wall Stride;
   server option `runnersKit=false` restores vanilla movement (supersedes the §13 Awakened Body "Unlocks" rule and
   docs/modules/body.md "grounded moves only" once W2 lands).
2. Carillon sits at world spawn in **new worlds only**. World option "Classic start" = wild spawn, no opening (today's
   prologue). Existing worlds never change.
3. One designed Carillon: the same layout in every world; seeds vary details only (laundry, flower boxes, lit windows,
   stock).
4. Friends start together at the Runners' Roost. Stragglers: the gate reaches for a player 5 min after the first
   arrival, or after 2 min idle.
5. The Association board shows 3 notices at a time.
6. Civic blocks are protected; your own plots are free; server option `cityProtection=false`.
7. Citizens are hurt, never killed permanently; they recover at the infirmary.
8. Hearth townsfolk (Oren, Mira, Teodor, Sun) live in Carillon first; at Trusted renown each can move to your Hearth.
9. Map: our own Atlas on the Holo. Diorama may join the pack as an optional client trial; Xaero's leaves the pack plan
   when the Atlas ships.
10. Third person: vanilla's behind view plus our View key (flips first ↔ third, skips the front view); the shoulder view
    (mixin or Shoulder Surfing) is decided at gate G1.
11. Wall-run camera roll 5° by default, client option 0–7.5°.
12. The forked planning session ("btw: kind of a side question…") is the orchestrator; docs/carillon/STATUS.md lets a
    fresh session take over at any gate.
13. pack/ritual, pack/body-anims, pack/holo-ui and pack/awakened-lands merge into build/awaken-v1 first; Carillon
    workstreams branch from carillon/main rebased on that. Stage 0 spikes are throwaway and start at once.
Added for the lean build (approved by Euan, 2026-10-24):
14. Stage 0 is closed with its verdicts (S1 adjust, S2 go within limits, S4 go, S5 go and build our own, S6 done).
15. The Runner's Kit moves to The Ascent (docs/plan-ascent.html), which builds all movement once; §16.3 is Carillon's
    requirement for the run, and spike S3 (carillon/spike-runner) is the Ascent's starting point.
16. MMO-lite (§16.6–16.7 beyond witnessing) is deferred until the opening, the city, its people and the Atlas ship.
    Party gates and shared credit already landed through `PartyHooks` (see "Together").
17. One builder at a time, a fresh session per milestone, briefed from a file; the Carillon session reviews each diff
    and merges into carillon/main; each playable milestone merges into build/awaken-v1 through the pack session.
18. Models: Opus at extra-high effort for everything that decides the look or the feel (the city's plan, painter,
    grammar, landmarks and detail kit; the opening; citizens' visible life; the Atlas; art direction). Sonnet for data,
    generators (tools/city/), tests, docs and reports.
19. Tests: GameTests only where breakage is silent, on GitHub CI (push carillon/* branches); locally only a builder's
    own prefix (-Ptests=<prefix>). One short `slot.sh light` screenshot tour per milestone. Perf once, at the end of
    Detail. Euan's three play sessions (the opening in blockout, the city alive, the detail) are the quality gate.
20. Order (Euan, 2026-10-24, via the pack session: "Sounds good. Do that."): if M1 finishes before the Ascent's Runner
    tier is on build/awaken-v1, M3 (Alive) runs next; M2 (the opening in blockout) starts as soon as the Runner tier
    lands. Still one builder at a time.

### 16.1 Modules (new packages; same module rules as §1)
| Module | Owns |
|---|---|
| `city` | the authored plan (`data/awaken/city`), per-chunk painter, building grammar, Carillon kit blocks, street and roof graphs, pregeneration, civic protection, spawn placement, `CityHooks` |
| `citizens` | ledger SavedData, bodies (embodiment), jobs and schedules, city events, job clips, `CitizenHooks` |
| `opening` | sky camera, run director, arrival (solo/party wait), infirmary wake, Registry scene, skip; new `StoryProgress` states appended; `OpeningHooks` |
| `renown` | witnessing, gossip, tiers, factions, broadsheet; `RenownHooks` |
| `economy` | coin (bell marks), shops, Hunter Exchange (escrow), contracts board, housing plots; `EconomyHooks` |
| `guild` | parties, guilds, halls, Rooftop Circuit, Wall Breach; `PartyHooks` |
| `atlas` | discovery (server bits), client tile cache, diorama renderer (picture-in-picture), clouds, markers; `AtlasHooks` |
`body` (W2) adds the Runner's Kit; `powers` turns Wall Run into Wall Stride with its owner; `gates` uses `PartyHooks`
for sealing and shared rewards; `story` hands the Ritual's end to `opening` in Carillon worlds.

### 16.2 The city
- Wall about 480 blocks across (radius 240), ~40 high, 10 thick, walkway on top, 24 towers, 4 gatehouses (High Gate N,
  Dawn Gate E, Mill Gate S, the river gates). Districts: Choir Square + Market Ward (centre; Choir spire ~140 tall),
  Bellwatch (W, old town), Lantern Quay (the river), High Terrace (N; the Hunter Association, Registry, infirmary,
  archive, training yard), the Works (S), Garden Ward (E), the Wallring (edge; the Runners' Roost on the NW wall).
- ~700–900 buildings from the grammar, ~12 hand-made landmarks, ≥ 60 enterable buildings (every building on the run's
  routes, every shop, taverns, the Association).
- Generator: authored plan → per-chunk painter (`GatePainter`/`Sink` pattern; each chunk builds only the pieces that
  touch it) → grammar fills plots (seed varies details, never shape). The same pass writes the street graph and the roof
  graph (edges carry the move they need; door and stair links). Plan hash checked by GameTest.
- Placed only in vanilla blocks + the Carillon kit (~35 blocks of our own); no third-party blocks.
- Decided in M1 (2026-10-25): Choir Square sits at the plan's centre with the Choir on its north edge; the High Terrace is
  raised 8 blocks (five stairs up); the river has a lower quay at y 66 above water at y 63, with ladders; gatehouses at
  N, E and S plus two water gates, 24 tower places; fields outside the wall only in carved (basin) worlds; the street
  graph, roof graph and the opening's street route are generated with the plan (tools/city/plan.py → data/awaken/city/
  plan.json, schemaVersion 1, hash pinned) and copied into SavedData `awaken:city` at creation; "civic" means
  everything inside the wall; `respawn_radius` 0 in Carillon worlds (you respawn on the Roost); the pregen covers the
  painted area plus 2 chunks (1,708 chunks, ~14–15 s with Tectonic + Lithostitched); the spawn vault ring moves to
  402–522 blocks round the city. Carillon is on by default in new worlds through a built-in pack that requests
  `awaken:carillon`; turning it off on the Data Packs screen is the Classic start. Until M2, the prologue opens its
  Bell Crypt gate in Choir Square in Carillon worlds (CityHooks.square).
- Climbable language: bronze trim, dark beams and drainpipes can be held; plain plaster and glass can't.
- Pregenerated at world creation (~1,250 chunks, ≤ 30 s on a quiet M4; G0 measured 11–28 s for terrain + city, the
  city 2–5% of it; Distant Horizons holds its LOD building until the pregen ends) behind the opening card; graphs saved
  with the world; generator changes never touch a built city.
- Drawing (G0, spike S1: a 480-block city sits inside RD 16 from anywhere in it, so all of it is always drawn): houses
  have solid cores (dark fill behind windows, sealed attics) except the ~60 enterable buildings; geometry density
  varies by district (dense only where the run needs it); the painter writes straight into chunk sections (never
  setBlock), sets pane/fence/stair connections itself, keeps pieces ≤ 14×14 with a 2-block margin, places no block
  entities in bulk and ≤ 4–5 light sources per chunk.
- Terrain (agreed with Awakened Lands, 2026-10-24): `dev.awaken.terrain.api.CityBasin` picks the site deterministically
  from the seed at server start (land within ~1,500 of the origin) and flattens it: `CitySite(x, z, coreRadius 330,
  blendRadius 700, streetY 70, riverPath, basinCarved)`; `CityBasin.site(server)`, `CityBasin.reserved(x, z)` (core + 48,
  allocation-free). It needs Lithostitched (pack world profile); without it `basinCarved` is false and the painter
  flattens its own ground. Gated by the feature flag `awaken:carillon` (Classic start leaves it off; old worlds never
  have it). The river inside the core follows the city's fixed path (offsets from the site centre, north = −z). Terrain
  keeps lairs, ruins, hero trees and biome features out; Carillon wires runtime systems (gate Director, anomalies,
  landmarks, the spawn vault ring, the Well) to `reserved()`. Spawn is placed in the city by Carillon through
  `LevelEvent.CreateSpawnPosition`; terrain never sets spawn.

### 16.3 The Runner's Kit (built by The Ascent; Carillon's requirements for the run)
- G0 handover from spike S3 (carillon/spike-runner, report docs/carillon/spikes/S3-runner.md there): every move works
  on the body framework; checking a claimed start against the server's last 12 positions (not the current one) gave zero
  rubber-banding at 150 ms; the wall-run trigger must be a jump PRESS, not held (held fires off any 1-block step beside a
  wall); hangs reached from below need the server-Y fix (0.58 low after an upward approach).
- No new keys: sprint, jump, sneak and movement keys only. Moves are appended at the end of `Move` (saved ordinals).
- Wall-run: sprint + jump along a wall within 35° of parallel, ≥ 1.5 blocks above ground, wall ≥ 2 tall. 35 ticks:
  10-tick grip (vertical target +0.06 easing to 0), then the target eases (smoothstep) to −0.26 b/t (approach 0.35 per
  tick); ends ~2 blocks lower after ~11 blocks. Never the same wall twice before landing. Ends when a screen opens.
- Wall jump: jump during a run: +0.42 up, 0.5 out; landing on another wall starts a run 80% as long; 4 links before
  you must land.
- Ledge hang: airborne toward a ledge whose top is 1.2–2.4 above the feet, or sneak at an edge with a drop ≥ 4. Holds
  indefinitely (sprint-rate hunger); holds while a screen is open. Server checks a solid edge with headroom; hanging
  cannot trip the floating kick (`noBlocksAround`, verified).
- Shimmy 1.6 b/s along a continuous edge (turns outside corners). Ledge jump: up ≤ 2.5, sideways ≤ 3 (left/right
  held), back off the wall; climb out when open above.
- Jump help: 3 ticks coyote time, 4-tick early-press buffer, edge snap ≤ 0.3.
- Ranks: D wall-run 40 ticks; C 5 links; B shimmy 2.2 b/s, ledge reach +1; cooldowns of tactical sprint and slide
  shorten by rank (W2 sets the table here).
- Wall Stride (replaces the Wall Run power, rank-D passive): 80 ticks, grip 50 (target +0.04), then sink to −0.14;
  short vertical runs; works on glass and leaves.
- Camera roll on a wall: 5° default, client option `wallRunRoll` 0–7.5.

### 16.4 The opening (W3)
- New `StoryProgress` states are appended after the existing ones; players already at DONE never see the opening.
- Beats: card (city generates) → bells at dawn → sky (≤ 25 s from join to control; skippable after first view by holding
  jump 1 s) → into your eyes (first-run choice of first/third person) → the bell rings wrong (light column in Choir
  Square; no waypoint) → the run (3 acts; 3 routes each; street route always ≤ 30 s away) → arrival (solo: 12 s
  cinematic; party: hold in the square, cinematic 3 s after the last arrival; stragglers per 16.0.4) → the Ritual
  (approved proposal; the gate opens in the square via `GateHooks.openScripted`) → the Void → the infirmary → the
  Registry (licence E-rank, the Holo wristband, the Atlas opens with the run revealed, first Directive) → free play.
- No death in the opening (falls leave ≥ 1 heart). Relog at any beat resumes it. `/awaken opening skip`.
- Decided in M2 (2026-10-25; details docs/carillon/milestones/M2-opening.md): the opening's beats SKY, RUN, ARRIVAL,
  WAKE, REGISTRY are appended to `StoryProgress.Prologue` after DONE (schema 3; the story's GATE_OPEN, RITUAL and
  ACCEPTING sit in between); in Carillon worlds the opening owns NOT_STARTED/GLITCHED and the prologue's own first beats
  never run; Accept leads to WAKE (the infirmary) only during the opening. Skipping is a game rule plus hold-jump (after
  one viewing) plus `/awaken opening skip`; `/awaken opening restart|beat` for testing. F5 skips the front view. Dawn is
  set once per world. The run's stage (414 blocks, tools/opening/routes.py) is placed once, at version 1. The Bell Bridge
  falls when a runner comes within 22 blocks across the ground, or on reaching the market. The Starter power offer and
  the fauna reveal wait until the opening ends. The Ritual's end-black is cleared by `RitualClient.handOff`. Music is
  borrowed from the Bell Crypt for now; the Atlas reveal at the Registry waits for M5. Proposed: `GateHooks.enter`.
- Camera (G0, from spike S2): one client-only `OpeningCamera` on the marker entity type, never added to the level,
  carrying the player's eye height, set through `Minecraft.setCameraEntity` and placed every frame from
  `ViewportEvent.ComputeCameraAngles` at LOWEST priority (no mixin). Hands and the player's own name tag are hidden and
  the mouse is held (`CalculatePlayerTurnEvent`). One `finish(reason)` path restores the player as camera on
  completion, skip, camera taken, logout, clone or any exception. The server owns the timeline and sends one message
  with the start age (a relog resumes mid-flight); the player is never dragged along. The camera stays within ~215
  blocks of the player, below y 320 and at most ~200 above the ground; the shot flies over the north-west half looking
  south-east across the spire, and Distant Horizons (or fog, Lens haze and clouds) carries the far half. The first
  flight is pre-warmed behind the opening card. Life seen from the sky is blocks and particles (lit windows, smoke,
  lamps, boats): citizens draw only within 128 blocks.

### 16.5 Citizens (W4)
- Ledger: 240 records, sliced 12 per tick; ≤ 0.5 ms/tick (G0: p95 0.015–0.04 ms in game). Bodies within ~56 blocks: ≤ 40 per player, ≤ 96 per
  world; walk the street graph; brains tick every max(1, d²/256) ticks; ≤ 4 pathfinding at once; ≤ 1.5 ms/tick.
- Movement (G0, from spike S4): bodies are never saved (the ledger is the truth); they walk the street graph on
  keep-right lanes with no vanilla goals, using vanilla pathing only for the last few blocks or to get unstuck; they
  always return to the street node before the next edge; they stand on reserved spots sized to each station's staff;
  they leave early by route distance. Path slots: 4 at once, 6 above ~60 bodies in a world, walk straight after 2 s
  without one. Route tables are baked per destination on first use. Rescue teleport after 10 s stuck, only out of every
  player's view. Spawn at 56 blocks, remove past 64, ≤ 8 spawns per 10-tick pass. Witness line-of-sight checks run per
  event or in the ledger, never per tick. Rendering uses `core.client.skin.SkinnedMobRenderer` + `SkinRenderState` (never AvatarRenderState).
- Decided in M3 (2026-10-25; details docs/carillon/milestones/M3-alive.md): the roster is generated data
  (tools/city/citizens.py → data/awaken/city/citizens.json: 240 citizens, 96 households, 47 workplace groups, 14 taverns,
  8 thieves; Oren, Mira, Teodor and Sun named). Inside the wall at night a monster can spawn only near a dark lamp (a
  lamp the lamplighter didn't light, or one broken). Until boats and rooftop routes exist, boatmen work as quay hands
  and runners run street errands. Recognition tiers Stranger / Familiar / Known have provisional thresholds; a minimal
  `RenownHooks` lives in citizens until a renown module replaces it. `core.client.skin.SkinnedMobRenderer` has a
  protected constructor taking the subclass's own wide/slim models (citizens pose their own model). Planned for M4: the
  roster grows to ~400–480 records (density away from the market), and the 46 house plots M1 left with unusable doors are
  fixed in the plan. Known bug: a gate tremor's gate id isn't saved with the event.
- 12 jobs (lamplighter, bellringer, porter, stallkeeper, baker, smith, watch, crier, boatman, washer, mason, runner);
  schedules on the vanilla day clock, seeded per citizen.
- Events per play-day budget: burglary ~1 (night; thief flees on the roof graph), pickpocket 0–2, fire ~1/5 play-days,
  runaway cart rare, gate tremor from D-rank, Bell Festival every 10 play-days. Each resolves with or without the player;
  consequences are world state (patent rule).

### 16.6 Renown (W5)
- A deed counts only if witnessed (citizens with line of sight within 24 blocks). Gossip spreads once per play-day
  through households, workplaces and taverns, with decay. Tiers: Stranger, Familiar face, Known, Hunter of note, Hero of
  Carillon, Legend (thresholds set by W5 from the pacing sim). Factions: Association, Watch, Merchants' Guild, Runners'
  Guild, Commons; shown on the Holo only after registration and only once met. Bad deeds count.

### 16.7 MMO-lite (W5, W7)
- Contracts: 3 notices at a time, for your rank and known places only; one taken → a new one next play-day; no expiry.
- Coin (bell marks), shops stocked by the city's jobs, the Hunter Exchange (escrow; one place at a time), housing (the
  Roost room from the start = respawn; townhouse plots; guild halls), parties (shared gate entry, credit, Atlas pins),
  guilds (local to the server), Rooftop Circuit (Training ghost reused), Wall Breach (B-rank set piece, never while
  nobody is there). Rewards pay once per claim id. Prices hold ±30% over 100 simulated play-days.

### 16.8 Atlas (W6)
- Server keeps what each player explored: 1 bit per 16×16 cell (attachment, schemaVersion); clears ~64 blocks around
  the path; a district's bell tower clears the district, the Choir spire the city. Party sharing on request.
- Client tile cache `<game>/awaken/atlas/<world>/<dim>/` (height + top colour per column; versioned; ≤ 64 MB, oldest
  first; "Clear Atlas cache" button).
- Table view: a Screen with a picture-in-picture renderer (depth, orbit, zoom, tilt); wrist glance cached on the Holo.
  ≤ 2 ms/frame open, ≤ 150 ms to open, glance ≤ 0.3 ms. Markers only for what the player knows.

### 16.9 Public hooks (`core.api`, v0; signatures may change only through the orchestrator)
- `CityHooks`: `inCity(Level, BlockPos)`, `district(Level, BlockPos)` (id or null), `isCivic(Level, BlockPos)`,
  `startPoint(ServerPlayer)`, `square(Level)`. Graph types are added at G1.
- `CitizenHooks`: `witnesses(ServerLevel, Vec3, double)` (citizen ids), `post(ServerLevel, Identifier event, BlockPos)`.
- `RenownHooks`: `witness(ServerPlayer, Identifier deed, BlockPos, int weight)`, `tier(Player)`, `standing(Player,
  Identifier faction)`.
- `EconomyHooks`: `balance(Player)`, `pay(ServerPlayer, long, Identifier claimId)` (once per claim), `charge(ServerPlayer,
  long)`.
- `PartyHooks`: `members(Player)` (includes the player), `sameParty(Player, Player)`.
- `OpeningHooks`: `inOpening(Player)`, `stage(Player)`, `skip(ServerPlayer)`.
- `AtlasHooks`: `reveal(ServerPlayer, BlockPos, int radius)`, `explored(Player, BlockPos)`.
- `MovementHooks` gains `wallRunning`, `hanging` (W2 adds them).

### 16.10 Budgets
- Perf profile, RD 16, busiest market at noon: p50 ≤ 10 ms (platform 5.3 measured + city blocks ≤ 2.8 with solid cores
  and district density (G0 measured +2.1–3.5 on a loaded machine) + 40 citizens ≤ 1.2 (G0 measured) + signs of life 0.4
  + Holo 0.3); rest of the city ≥ 110 fps p50. Measured once, quiet, at the end of Detail. Horizon-swift: city ≤ +3 ms over the same spot
  without it. Golden run p99 ≤ 20 ms, no frame > 50 ms. Sky shot ≥ 60 fps p99. Server: whole city ≤ 2.5 ms/tick avg;
  tick p95 ≤ 25 ms holds.

### 16.11 Process
- Milestones (lean, decisions 14–19): M1 the city in blockout → M2 the opening in blockout (after the Ascent's Runner
  tier lands; Euan's play 1) → M3 alive (Euan's play 2) → M4 detail (perf; Euan's play 3) → M5 the Atlas → later
  MMO-lite → polish. Each playable milestone merges into build/awaken-v1 through the pack session.
- Branches: carillon/main (integration), carillon/m<N>-<name> per milestone, carillon/spike-<name> (never merged).
  Briefs in docs/carillon/briefs/, status in docs/carillon/STATUS.md, spike reports in docs/carillon/spikes/.
- Machine share: Carillon agents run Gradle and tours through `tools/carillon/slot.sh` (≤ 3 builds at once; a tour runs
  alone with no Carillon build beside it).


---------------------------------------------------------------------------------------------------------------------

## 17. The Ascent (movement as the reward; plan in docs/plan-ascent.html, brief docs/ascent/BRIEF.md, approved 2026-10-24)

The body's states above the ground, climbed as a ladder (Runner → Tether → Leaper → Wings → Skyborn → Sovereign), the
world built to be crossed, and gate rooms that move. Built once, in the body module (`dev.awaken.body.ascent`, client
`...ascent.client`, the Feel Director in `dev.awaken.body.client.feel`). The bar every part must pass is BRIEF.md's
nine-point "genuinely magical" list.

### 17.1 Phase 0: the feel spike (2026-10-24)
Throwaway in its tuning, not in its structure: the states, physics and feel below are the skeleton Phase 1 builds on.

States (`AscentState`, ordinal = network id, new ones only at the end): NONE, TETHER (on a rope), ZIP (pulled along a
rope), CARRY (flying free with a move's momentum), GLIDE (wings), SOAR (momentum flight). Each airborne state keeps its
own velocity and sets the body's motion every tick in the movement-input event (HIGH priority, before the Awakened
Body's moves, which stand aside while one runs); vanilla's collision moves the body; what vanilla's air control would add
from the keys is taken off first. A collision shows up next tick as the body ending short: that part of the velocity is
gone; a landing ends the state; a teleport (over 3 blocks off) ends it.

Controls (no new keys): the movement key (Left Alt) fires and holds the rope once the Tether tier is held, and the
movement slot's power steps aside (`MovementHooks.ClientAccess.ownsMovementKey`, default false). Letting go flies on
(CARRY); jump on the rope jumps off it; forward pumps the swing near the bottom of the arc; sideways turns it; sneak
reels in. Jump in the air (falling, 2.2+ above the floor) opens the wings; jump while gliding takes flight; sneak while
gliding folds them into a dive. In flight: forward cruises along the look, sprint boosts, jump climbs, sneak sinks, no
forward hovers; a jump with no forward is a wing beat. A double-tap of jump in the air without wings takes flight.

Numbers (`AscentRules`, blocks and ticks; gravity 0.08 everywhere):
- Rope: reach 44, shortest 3, pulls on the hand 1.55 above the feet. Air drag 0.0025 a tick. Pump 0.016 a tick at
  forward, full from 30° below the anchor's level down to the bottom. Steer 0.012. Sneak reels in 0.34 a tick. Top
  speed 2.6. The arc's lowest point keeps 1.8 above the floor (the higher of the floor under you and under the arc's
  bottom, taken 1.5 off the anchor toward you so its own trunk isn't the floor); a rope longer than that reels in at
  1.2 a tick as it bites (the yank into the swing). The bite tugs 0.22 along the swing in the way you look. A swing
  may scrape the floor for 5 ticks; the 6th on the ground lands it. An anchor less than 2 above the hand zips instead
  of swinging (accel 0.24, top 1.45, lets go within 2.2 with a hop up of at least 0.48). A rope flies out for 1-3
  ticks (distance / 16) before it bites. The integrator moves the hand along the rope's sphere on a great circle
  (energy kept but for drag and input: GameTest `ascent_swing_energy`).
- Anchors: the look's own ray (reach 44) wins when it hits 6+ above the eye (aimed swing) or under 2 above and at
  least 4 away (aimed zip). Anything else (sky, a trunk a few blocks up, a wall closer than 4) asks the assist: 25 rays
  climbing 20-62° above the horizon, straight ahead and up to 28° either side, scored ahead (≥0.45 of the facing) ×
  rise near 14 × reach near 13 (gaussian, 9 wide) × off to one side of your line (full from 3.5 to 9 out, a quarter
  dead ahead: the arc's bottom passes under the anchor, so a trunk dead ahead would swing you into itself). A low
  direct hit 4+ away swings as it is only when the assist finds nothing.
- Release window: letting go while rising in the front half of the arc, best 52° past the bottom (±30°), adds up to
  ×1.06. Jumping off: ×1.12 horizontal and +0.46 up. A rope is cut only by something solid across its middle (from
  1.5 off the hand to 3 short of the anchor; checked every 4 ticks: it wraps its own trunk as you swing round it) or
  by its anchor block going. Leaves never cut a rope.
- Carry: horizontal drag 0.988, vertical 0.98 (vanilla's 0.91 would eat the swing); input bends the path by 0.02 a
  tick and never adds speed beyond what you had (or 0.24 when slow).
- Glide: speed changes by -dir.y × 0.08 - 0.0062 × speed² a tick (dives gain, climbs pay); the path turns 16% of the
  way to the look a tick at full authority (25% of that below 0.9 b/t); below 0.42 it stalls and sags. Opening the
  wings keeps 45% of a fall's descent and turns half the rest forward. A 6° glide holds ~1.16 b/t (ratio ~9.5).
- Sonic boom: crossing 2.2 b/t in a glide or flight; arms again below 1.6.
- Soar: thrust 0.0098 (cruise ≈ 1.25), boost 0.032 (≈ 2.25), drag 0.0063 × speed², path turn 0.2, top 3.2; hover bleeds
  ×0.88 a tick, climbs at 0.32, sinks at 0.36; a wing beat lifts 0.38.

Server (Phase 0): takes the client's state (`AscentPayloads.State`) when the tiers allow it (else NONE), shows it to
players within 160 (`Seen`, `SeenEvent` for one-off moments: fire, launch, deploy, boom, land, flap, catch). While a
state is airborne it resets fall distance and `connection.resetFlyingTicks()` each tick (never mayFly, which would give
the client creative flight on a double jump); a landing in an airborne state or within 15 ticks after one takes no
fall damage (soft failure; Phase 1 turns hard landings into rolls with the body's roll). A body on the ground 20 ticks
while its client still says airborne is ended by the server. Phase 1 adds the checks (anchors really there, speed caps
per tier, history-based position checks as Carillon's S3 proved).

Tiers: a bit per `AscentPayloads.Tier` (TETHER, LEAPER, WINGS, SKYBORN, SOVEREIGN) in `AscentMemory` (saved, kept
through death, schema 1). Phase 0 grants them only by `/awaken ascent grant <all|none|tether|wings|skyborn>` (game
masters); `/awaken ascent sandbox` grows 14 giant trees (26-38 tall, branches, persistent leaves) down a lane ahead and a
46-tall launch tower under you. `/awaken ascent pilot <line>` drives the tour pilot (scripted keys) and the clip recorder.

Feel (the Feel Director, one client voice): camera roll capped at the comfort setting (5° default) (banked into turns at 1.5 × the yaw rate; on a rope,
leaning with the rope's angle across the view, 9° per unit), halved in third person; FOV × (1 + 0.17 × smoothstep of
speed 0.4-2.4) times vanilla's FOV-effects slider, plus punches; a damped pitch spring for moments (bite, catch, launch,
deploy, flap, land, bonk); a faint tremor above 1.7 b/t and shake on the boom and hard landings, both off with Awaken's
camera-shake setting; light streaks at the frame's edge above 1.0 b/t (first person); the boom flashes the frame, cuts
the wind out for 7 ticks, then cracks. Everything starts on the predicted state, never the server's answer. Wind is one
listener-relative loop, louder and higher with speed. Comfort lives in `config/awaken-feel-client.toml`: cameraRoll
0-7.5 (5), speedFov 0-1 (1, times vanilla's FOV-effects slider), movementShake (on; also off with Awaken's
cameraShake), reducedMotion (off: on removes streaks, air motes, the boom flash and the tremor, and halves the roll).
Camera Overhaul (pack, optional) is switched off through its own public settings (`Configuration.get().general
.enabled`, never saved) while an Ascent state is airborne, and put back after (decision 6; compileOnly, the one class
`body.client.feel.CameraOverhaulPause`).

Look (no vanilla particles or render types reused for the shapes): the rope is a ribbon of `textures/ascent/rope.png`
(white core, braided cyan strands, a deep-blue rim for daylight) plus an additive glow; it flies out rippling, sags
while slack, and falls away from the hand when let go. Contrails trail the wingtips (wings open, over 0.55 b/t) or the
hands (over 0.85). Motes of air stream past your eyes above 0.7 b/t. A rope's bite flashes a four-point flare and throws
`awaken:ascent_mote` sparks; the boom throws a ring of shocked air across the line of flight and a cone of vapour. Wings
are a render layer on the player model (`WingLayer`): three jointed bones a side, 21 overlapping flight feathers and two
rows of coverts (`textures/ascent/wing.png`), unfolding from the back, beating, swept back by speed, folding on landing;
drawn translucent (a Signal-cyan tint) plus a faint additive glow; the trailing edge is fourteen feathers whose
outer ones alternate short, so the primaries part at the tip; hovering, they beat slowly every 18 ticks. Your own wings
and contrails are not drawn in first person (whole, they fill the frame's lower corners; Phase 1 gives first person
light at the edges instead). The whole figure turns with the state
(`AscentAnims`): along the rope while swinging (pivot at the shoulder), belly down along the line of flight on wings,
banked into turns, upright when hovering, one forward flip after letting go of a rope; the limbs come from
`player_animations/ascent_moves.json` (tools/art/specs/ascent_moves.py: swing, zip, flip, fall, glide, soar, hover, each
with a `_bent` twin). Sounds: tools/art/specs/audio_ascent.py, eleven events under `ascent.*`.

Tours: tools/autopilot/ascent-spike.txt records clips (run-autopilot/clips/<name>/ frames with their game times;
`python3 -I tools/ascent/clip.py <dir>` makes a real-time mp4 and a contact sheet).

### 17.2 Phase 1a: the Runner tier (the ground floor of the ladder; Carillon's §16.3 built here)
Carillon's spike S3 (carillon/spike-runner) is folded in: `RunnerRules`, `RunnerServer`, `client.RunnerMoves`, its tests
and course (`/awaken body course`, the latency knob and bench stay as dev tools). §16.3 is the requirement; what follows
is what the Ascent decided while building it.
- The Runner's Kit is a world option, `config/awaken-body.toml` (SYNCED): `runnersKit` (on). On: every body move for
  everyone from the first second (roll, tactical sprint, slide, mantle, wall-run, wall jump, ledge hang, shimmy, ledge
  jump), ranks improve them. Off: the old rule (vanilla until the awakening, ranks unlock roll/sprint E, slide D,
  mantle C; no Runner moves; the server refuses them). `ascent` (on) is the Ascent's feature flag in the same file.
- The System announces the body's moves at the awakening, once, in one toast (roll, sprint, slide, mantle); never
  before it, and the Runner's own moves are found, not told.
- Ranks (`Body.runnerRank`: the rank, E before the awakening): wall-run 35 ticks, 40 from D; wall-jump links 4, 5 from
  C; shimmy 1.6 b/s, 2.2 from B; ledge jump reach up 2.5 / sideways 3, +1 each from B; tactical sprint recharge
  80 − 10 a rank (to 30); slide cooldown 20 − 2 a rank (to 10). Client and server use the same table.
- Wall Stride: the power `awaken:wall_run` (id kept for saves) is now "Wall Stride", a rank-D passive with no effect
  of its own: the body reads it (`Body.wallStride`: `Powers.owns` on the server, the client's loadout) and its runs last
  80 ticks (links ×0.8 as usual), grip 50 lifting +0.04, then sink to −0.14. Every Runner runs on any collision face,
  glass and leaves included. The old client lift (`ClientCasting.wallRun`) is gone.
- Wall Stride's vertical run (`Move.WALL_CLIMB`, appended; clip `body_wallclimb`): with the power, facing a wall (within
  35° of its normal, within 0.45), early in a jump (falling no faster than 0.15) and holding forward: 12 ticks up it at
  0.34 b/t easing off over the last three (about 3.7 blocks: a lip 6 above the floor is caught; 10 ticks left it 2.4
  over the feet, out of reach), once an airtime and never on the same wall twice before
  landing. A lip in reach on the way is grabbed (the climb carries into the hang); jump kicks back off it (0.42 out,
  0.40 up) and turns the view round over 6 ticks, a wall jump that can link into a run on the wall behind. The server
  checks the power and the wall in front where it saw the body; grit and footfalls every 4 ticks; a Runner link.
- The hold: feet 1.55 under the lip (S3's 1.85 put first-person eyes under the lip, staring at the face). The lip is
  found by `RunnerRules.ledgeTop`: of the collision tops in the band with 0.5 of air over them, the one nearest the
  hold's height (S3's mantle-style probe refused any ledge with another ledge a block above it).
- S3's open bug (the server's hanging body 0.58 low after an upward grab) is the server's own physics: 26.3 players are
  `AUTHORITATIVE_SIDE_AND_SERVER`, so the server runs `travel()` (gravity) on its copy between packets, and a still
  client only sends a reminder every 20 ticks. While a body hangs (and in every airborne Ascent state) the server zeroes
  its copy's motion after its tick: no drift, no bob for watchers.
- Look: the moves play Player Animation Library clips from tools/art/specs/body_moves.py (`BodyClips.RUNNER`, network
  index = `Seen` code − 32): `body_wallrun_l/_r` (loops, with `_end` settles), `body_walljump`, `body_grab`,
  `body_hang` (loop), `body_shimmy_l/_r` (loops), `body_ledge_up`, `body_ledge_side_l/_r`, `body_ledge_back`,
  `body_lower`, `body_mantle` for the climb out. `_l`/`_r`: the wall (or the way) on the left or right. Others see the
  variant the server picks from the move's geometry; `Seen` 31 ends a Runner move (its loop settles); a catch settles
  into the hang loop after its clip. Every wall-run footfall (each 4 ticks) knocks grit of the wall's own colour off the
  face; sounds: `body.wallstep`, `body.walljump`, `body.grab`, `body.shimmy`, `body.climb`, `body.drop`
  (tools/art/specs/audio_body.py). The wall-run's camera roll is the client option `wallRunRoll` (0-7.5, 5) in
  `awaken-feel-client.toml`.
- One movement system: while a Runner move holds the body the Ascent's wings and flight don't start (the rope still
  cuts in, ending the Runner move); a jump at a runnable wall goes to the wall-run, not the wings; flying free off a
  rope (CARRY), a jump at a wall hands the body to the wall-run with the rope's speed.
- `core.api.MovementHooks` gains `wallRunning(Player)` and `hanging(Player)` (both sides; existing methods unchanged).
- The Ascent's public API is `dev.awaken.body.ascent.Ascent` (§12). The ladder's record stays the movement module's own
  attachment (`AscentMemory`, saved, kept through death; schema 1 holds the tiers) rather than a field of progression's
  Profile: the module that moves the body owns what it may do. "Arrived by movement" is a 20-second trail of bits per
  player (moving by Ascent state or Runner move, or not) and the last block placed (`BlockEvent.EntityPlaceEvent`).

### 17.3 Phase 1b: the Leaper and the server's checks
- The Leaper (tier D-C), no new keys, each once an airtime (back on landing and when a rope bites), from any airborne
  body that isn't on a wall or a ledge: jump in the air = a double jump (+0.52 on top of any rise; it comes before the
  wings: a second jump opens them), sprint in the air = an air dash along the look's heading at max(0.95, 1.33 × your
  horizontal speed) with a lift of 0.08, sneak with 4+ blocks under you = a dive slam (horizontal speed ×0.25, straight
  down at 1.7). Each goes into the carry with the swing's momentum, so a release can chain into a dash and a slam.
  A slam's landing: within 3.5 blocks, creatures take 3 + 3 × the landing speed (falling off to 40% at the edge) and
  are thrown out; players only where the world's PvP rule allows; your own tamed creatures never. The server applies it
  only with the Leaper tier, out of an airborne state a moment ago.
- The server's checks (more to come with the latency tours): a rope's anchor must be within 50 (54 since mastery, §17.6) of where the server has
  the body and sit on a block's collision (`AscentServer.anchorReal`); a state the tiers don't allow, the `ascent` world
  option off, riding or spectating are refused with `AscentPayloads.Stop`, and the client lets go (a refused rope
  becomes the carry: never a yank back).

### 17.4 The Wilds (plan §6; module `wilds`, first part: what doesn't wait on the movement tiers)

Precursor wards (`wilds.ward`, config `features.wards`):
- A site in the structure tag `#awaken:warded` (vaults, the spawn vault, ocean labs, monoliths) has a ward: its structure
  box grown by 12 blocks on every side. `#awaken:warded_sky` (sky platforms) reaches 48 further down, so a tower stalls
  well under the deck. New structure sites (Tether Spires) join a tag; sites placed by code (hero-tree caches) register
  a `core.api.WardHooks.Source`. Existing ruins have wards at once: the box comes from the saved structure start.
- Inside a ward, outside every Hearth's radius, a block a survival or adventure player places crumbles 3 s (60 ticks)
  later: still the same block, it is removed (water stays where it was waterlogged) and the item that placed it goes back
  to them (inventory, else at their feet; if they left, where it stood). Containers drop what was put in them, as
  breaking would; a shulker box keeps its contents in the returned item. Creative and spectators build freely. The first
  ward block a player places teaches it with a toast (flag `awaken:ward_noticed`). Pending crumbles all crumble when the
  server stops; a block whose chunk unloaded first stays.
- Inside a ward a block in `#awaken:ward_sealed` (every Precursor building block plus the deepslate and blackstone
  dressing ruins build with) can't be broken by a player, an explosion or a piston (pistons inside a ward never move).
  The vault's sealed chamber is enclosed in those blocks, so a rank door can't be dug around. Natural ground still digs.
- Its own look and sound, never vanilla: `awaken:ward_spark` (a thread of the Signal's #7FC3FF light) and `awaken:ward_grain`
  (a chip tinted by the block's map colour), built on the client from one `ward_fx` event (mark, crumble, hold); sounds
  `wilds.ward_mark`, `wilds.ward_crumble`, `wilds.ward_hold` (tools/art/specs/audio_wilds.py; sprites tools/art/specs/wilds.py).

How you arrived (`wilds.arrival`, behind `core.api.ArrivalHooks`; config `features.wilds`):
- Every tick the server reads each player's way of moving; a quarter second keeps its most telling way (in `Way` order:
  STILL < WALK < SPRINT < SWIM < CLIMB < RIDE < AIRBORNE < POWER < ASCENT < BUILT) and 80 of them (20 s) are kept.
  BUILT: standing on (any corner of the feet) or climbing a block the player placed in the last 10 minutes (last 512
  placements). POWER: 2 s after a cast of a movement-slot power, a dash or Hover, and every tick Flight or Hover runs.
  ASCENT is for the movement tiers to report. Nothing is saved.
- A visit: stepping into a ward opens it with the window's verdict (no BUILT in the 20 s that led there); a BUILT sample
  while inside spoils it; it ends after 5 s outside. Outside any ward the window is the verdict. No record: movement.
- `awaken:arrived_by_movement` (loot condition, the chest's opener) gates a site's best roll. Route records and Flow XP
  pay through `ArrivalHooks.byMovement` too when they land.

The Horizon line (`core.api.HorizonHooks`; signal `HorizonSync`, `TrackerHud`):
- A second HUD card under the Now tracker (or in its place), in the Horizon's pale sea-green (#9FE6D6), drawn through
  `HudLook` (the calm FRAME card with the projected look): "△ Sealed Vault  ↗ 420 m". Its own client setting
  `horizonLine`; nothing in the Now tracker (gates 45, requests 42, unspent points 55) can take it.
- Sources list the sites near a player; each names a need (empty: on foot; `wings` for the sky). The line is the nearest
  site the player can reach (`Reach` answers: the Flight power, a Frame with Jet Boots and essence; later the tiers),
  a raised one (a spirit's whisper) first. A tease (the nearest site they can't reach: "Sky Platform · needs Wings",
  dimmer, the need in amber) takes the line 15 s in every 5 minutes, staggered per player. Synced every 40 ticks when
  changed; the client draws the compass.
- Landmarks moved from the Now tracker (priority 30) to the Horizon line: the same reason-to-go levels, except a sky
  platform waits for its need instead of level 20 (teased until then). Never inside a Hearth, never in a gate.

Discovery that pays:
- Biome discoveries: the first time an awakened player stands in a biome of a vanilla dimension (overworld, Nether,
  End), `Progression.discover("biome/<ns>/<path>", 0.1)`, a toast with the biome's name, counter
  `awaken:biomes_discovered`. Checked every 2 s per player, only when the biome underfoot changed.
- `awaken:lairs_heard` counts each beast kind whose lair a player learns (`awaken:lair_heard/<beast>` stays the flag).
- The counters that fed nothing now feed content (data/awaken/awaken/): Truths `i_go_where_the_map_ends` (8 landmarks),
  `i_close_what_tears_open` (5 rifts), `i_follow_the_lights` (3 spirit caches + 3 groves), `i_read_what_they_left` (8 logs +
  a door, from D), `when_the_sky_falls_i_am_there` (3 stars + a guardian); Titles Wayfarer (12 biomes), Cartographer (15
  landmarks), Riftwarden (10 rifts), Starcatcher (5 stars), Star-Hunter (3 guardians), Grove-Keeper (8 groves), The
  Spirits' Guest (5 caches), Anomalist (20 anomalies), Lorekeeper (12 logs), Keybearer (4 doors), Tracker (4 lairs heard);
  eleven exploration directives (a new landmark, two new biomes, a rift, a star, a star's guardian, a procession, a grove,
  two anomalies, a log, a door from D, a lair heard of).
- Sky platforms have their own table `chests/precursor_sky`: charged essence cells, alloy, wind charges, phantom
  membranes, feathers, slow falling, ender pearls, Jet Boots (4 in 105), a little diamond; never the ground vaults'
  scrap, redstone, copper or other Frame modules. Its best rolls (by movement only): 2 Power Core Shards always, then a
  1000-essence cell or Jet Boots (8 in 12). The vault, monolith and ocean lab tables each gain a best roll of 1 shard.

Telemetry: `Progression.grantXp` records every grant (after scaling) in the player's `awaken:xp_ledger` (schema 1, saved,
kept through death, not synced) by `XpSource`, and world XP again by its own source key. `/awaken xp` prints the split;
with `playtestMode` each logout writes it to playtest.jsonl (`xp_sources`). XP banked before awakening shows as STORY.

Tests: `wilds_ward_rules`, `wilds_arrival_history`, `wilds_discovery_pays`, `landmark_horizon_line`, and the Horizon box
in the System's layout test.

### 17.5 Gate Rig (gates, plan section 7 "Gate rooms: the room is the puzzle"; branch pack/gate-rig)

A small layer under the painter that gives a room a staged timeline. Pure geometry in `gates.world` (`RigRoom`,
`RigRooms`, `RigCollapse`, `RigMirrors`, `RigWells`, `RigBeam`), shared by the painter, the server and the solvability
checks; the timeline in `gates.run.RigDirector`, modelled on the Ritual's director.

- **Content version 4** (`GatePlan.CONTENT_VERSION`): each ordinary floor (never the Lord's floor or the ritual hall) of
  a theme with an archetype gets one rig room. Ember Mines, Glass Desert, Bell Crypt: Collapse Run. Mirror Palace:
  Mirror Hall. Precursor Lab: Mirror Hall or Gravity Wells (per floor, by hash). Sky Archipelago, Nightmare: Gravity
  Wells. The room is a NORMAL or ELITE room on the critical path (not the start or the end), at most 2x2 slots, every
  door at its floor; the biggest that fits, ties by the room's seed. It loses its set piece and becomes RECT. Every
  choice is a per-room hash after the version 3 rooms are built, never the builder's random stream: version 3 plans
  build exactly as before, and in a version 4 plan every room without a rig is what version 3 built. READY gates
  nobody entered rebuild on version 4 at start, as for every version bump.
- **Content version 5:** the Gauntlet on the ordinary floors of the Clockwork Foundry and the Frost Monastery (which had
  no rig room); `RigRooms.kinds(theme, version)`, so a version 4 plan of those themes builds no Gauntlet and every other
  theme builds exactly as version 4 (`gates_rig_gauntlet` checks both).
- **The model:** a *group* is a set of blocks swapped together between their painted (rest) state and an active state
  (a group with no blocks is a flag the archetype reads); a *track* is a timeline of beats (WARN, APPLY, RESTORE) on
  groups, looping or not; a *trigger* starts a track: a player in a box (ZONE, PLATE), the light reaching a block
  (BEAM) or a fight in the room (ENCOUNTER: its monsters alive). A once trigger latches for good; the others fire again
  after their track ends and their cooldown passes.
- **The director** runs every tick for rig rooms with someone in them or with a track running. Beats play by elapsed
  game time, everything between two ticks in order (catch-up after lag, up to 8 loops a tick). A RESET track whose
  room empties (or a fight's track when the fight ends) stops and puts back every group it changed; KEEP tracks play
  on. A block that rises back (RISE) or a mirror that swings (SPIN) is drawn by the client first and placed after
  6 or 5 ticks, never inside a player (it waits for them to step off).
- **Saved:** `FloorState.rig` (`RigState`, schema 1): per rig room the tracks running (start time, last tick played),
  the groups applied, the latched triggers and when the others may fire again; the floor's cave-in waves. Blocks are
  saved with their chunks. After a start, rooms saved mid-track come back to life and finish or reset. Positions
  and states follow from the plan, so a relog or a restart continues exactly.
- **What the client sees:** `gate_rig_scene` (the rig rooms of your floor: lenses, seals, wells, whether a room surges;
  sent on arrival, login and leaving) and `gate_rig_cue` (one moment: WARN, FALL, RISE, SPIN, SHATTER, IGNITE, DROP,
  SURGE, PIT, CAVE with cells and block states), sent to everyone within 96 blocks. Moving blocks are drawn with the
  game's own block models (`submitMovingBlock`), light with the rig's render type `awaken_rig_light` (additive,
  depth-tested, no depth write, the lightning shaders with their improved-transparency set; cores near white so Lens
  blooms them; vanilla lightning if the pipeline fails). Own particles only: `gate_rig_grit`, `gate_rig_spark`,
  `gate_rig_glint`, `gate_rig_shard`, `gate_rig_mote`. Own sounds `gate.rig.*` (tools/art/specs/audio_rig.py).
- **Collapse Run:** the room is a pit (its floor at the band's bottom, 4 below the room's floor: magma in the Ember
  Mines, sand in the Glass Desert, void glass in the Bell Crypt) crossed by a 4-wide causeway along the two door axes
  (only rooms with exactly those two doors). Rows are steps along it from the entry; 2 rows at each end are landings
  that never fall. Stepping onto the first falling row from either end starts that direction's run: row k falls at
  lead + (k - 2) x beat (beat 5 ticks at E and D, 4 from C; lead 24 / 20), its cracks glowing 10 ticks before.
  Bridges (2 rows, from D, never at E) drop for 12 ticks (16 from B) at the end of every 40-tick period while the run
  lasts; gaps (1 row, 2 from B; from D) are pre-cut and jumped. The far end holds 40 ticks, then the causeway knits
  back from the far end, a row a tick, and the run can start again after 40 ticks. The room has no monsters and no
  cache. Falling in: 4 ticks in the pit and it throws you back to the landing your run started from, facing the way
  on, for 3 + 8% of your health (`awaken:gate_rubble`); monsters that fall in take 4 every second.
  Solvability (`RigCollapse.solve`): a runner at 0.26 blocks a tick (a sprint is 0.28) from the first falling row,
  jumping gaps of at most 2, waiting on the row before a bridge for a window it can cross (up from 8 ticks into the
  period to 4 ticks before it drops), must leave every row 6 ticks before it falls, both ways. The builder tries 4
  variants, each gentler (no gaps or bridges, a longer lead), and drops the room if none passes.
- **Mirror Hall:** a lens in a wall throws a beam one block above the floor; the seal sits in the wall beside the door
  that leads on (slot offset 3 or 12), and that door is barred with light. Mirrors stand on pedestals at the hall's
  lattice (slot offsets 3, 6, 9, 12; never on a door axis or near a feature); a mirror is one-sided, its face to a
  diagonal (turn 0..3: NE, SE, SW, NW); its back stops the light. Using one turns it a quarter clockwise (at most
  every 7 ticks); the light is traced through the real blocks every tick, stopped by anything with a collision shape
  and by the room's walls. When it reaches the seal the hall latches: the seal lights (IGNITE), the bars fall 24 ticks
  later. The puzzle is built from the seed by walking a path of turns from the lens to the seal (2 mirrors at E and D,
  3 at C, 4 from B, at most 3 in a 1x1 room); every mirror on it starts wrong, 1 to 3 decoys stand off the light's
  path, and the hall never starts solved. The beam burns monsters in it every half second for 3 + 1.5 x rank and 3 s
  of fire (`awaken:gate_light`, credited to whoever last turned a mirror). Players walk through it unharmed.
  In halls of 2 slots or more a pavilion stands against a wall with no doorway near it: 5 by 9 inside, mirror walls 4
  high, its front of cracked mirror (`gate_mirror_cracked`), a cache inside. `gate_mirror` and `gate_mirror_cracked` are
  in `awaken:mirror`, so Ghost Step walks through them (its mirror range); 9 deep, so a step from anywhere within its
  reach lands inside. Someone inside latches the pavilion: 30 ticks later its cracked front shatters for everyone.
  Solvability: the solution witness routes the light to the seal, traced by the same `RigBeam`.
- **Gravity Wells:** only rooms whose way through is straight (the door you come in by faces the door that leads on),
  every side door opening onto a ledge, at least 7 high. Chasms 5 to 6 wide (7 from B), their floor 4 down: one in a
  1-slot room, two in a 2-slot room with 4-row ledges at the ends. Over each chasm a well, the chasm and a row of
  ledge either side, floor to a flat ceiling: inside it gravity turns over (`gate_well_gravity`, -2 x base, stratum
  7's attribute way), so you fall up, walk the ceiling across and drop softly as you step out (safe fall +24 for
  3 s). Its rims glow on the floor and on the ceiling (`gate_well_rim`), and the ceiling over a well is laid like a
  floor. While the room's monsters live, it surges: warned 30 ticks ahead, then everything in the room falls to the
  ceiling for 60 ticks, every 280 ticks, monsters too. Someone falling up has their eyes at the other end of their
  body (the camera hangs 1.62 below the ceiling) and is drawn upside down for everyone; the client predicts your own
  flip (it sets the same modifier the moment you step in), and the camera tips 5 degrees and back over 0.7 s
  (none with camera shake off; the FOV-effects slider scales it and a 7% FOV breath). Solvability: a breadth-first walk
  over the floor and the ceiling reaches the exit's ledge, and without the wells it can't.
- **Gauntlet** (`RigGauntlet`, `RigGauntletLive`; plan §7 "Gauntlet"): the room is a pit (slag glow in the Foundry,
  cloud in the Monastery, lit from below by light set in it every few columns) crossed by a course along the two door
  axes, 4 wide, from the door you came in by (only rooms with exactly those two doors, at their floor). Rows are steps
  from the entry as in the Collapse Run; 2 rows at each end are landings. A course needs 14 rows between them (a
  single slot's room is too short to be worth a clock: about one gate in five of these themes has one).
  - Obstacles, in an order of the room's own (each rotation tried, the one placing most kept), round again in a long
    room, a floor row before the first and after each (two in a course of 20+ rows); a smaller version where the full
    one won't fit:
    - **gap**: 2 rows of open pit (3 from B), a sprint jump;
    - **ledges**: 2 (3 from B), each 2 rows over the pit after a 1-row gap; one cracks the moment you land on it, falls
      14 ticks later and knits back 60 after that;
    - **presses**: 1 at D, 2 at C, 3 from B, each 2 rows with 2 floor rows between, in a straight stretch, in rooms 6+
      high: a block of iron (`gate_press`, amber hazard bands) or a ram of ice (`gate_frost_ram`) 3 high, parked in the
      ceiling over its rows. On a loop of 48 ticks (40 from B, 56 in a gentler variant) each comes down for 16 ticks (18
      from B), alternate presses half a period apart. 12 ticks before, the floor under it glows with chevrons and a
      ratchet winds (`gate.rig.windup`); it is drawn coming down out of the ceiling for 4 ticks, then its blocks land
      (`gate.rig.press`), and whoever is under it is hit (4 + 12% of health) and thrown back. It lifts back into the
      ceiling over 14 ticks (`gate.rig.lift`). The loop starts with the clock (a run started alone starts it fresh, so
      its beat is the one the par was set to) and stops, putting everything back, when the room empties;
    - **wall-run**: 5 rows of open pit (6 from B) in a straight stretch, with a wall (`gate_run_wall`, dark panels ruled
      with lit grooves) beside them on one side, a row past either end, from the pit to 4 over the floor: run along it.
  - **Rings**: after every second obstacle, over a straight floor row with 8+ rows still to go, a hoop of the theme's
    light stands over the course (drawn by the client from the scene, its foot under the floor); passing it is your
    checkpoint (`gate.rig.ring`, a pip on the clock lights, the split shows).
  - **The clock** (yours only, `gate_rig_run`): stepping from the start landing onto the first row starts it; back on
    the start landing gives the run up; the far landing stops it. Top centre: the time, a pip a ring, the par; at the
    end the time in the medal's colour: gold within par × 1.1 + 1 s, silver within par × 1.5 + 2 s, else bronze
    (`gate.rig.medal`, pitched by medal). A run pays world XP once in a while (`awaken:gauntlet`, 6% / 3.5% / 2% of a
    level) and counts `awaken:gauntlet_runs` (and `awaken:gauntlet_gold`).
  - **The pit**: 4 ticks in it and it throws you back to your last ring (the start before the first) facing the way on,
    for 3 + 8% of health. No monsters on the course; no cache.
  - **Solvability** (`RigGauntlet.solve`): from the first row (the clock and the presses start together) a runner at 0.26
    a tick, gaps of up to 3 jumped, a wall-run of up to 7 rows at 0.32, off every ledge before it falls, through every
    press only while it is up with 3 ticks clear either side (waiting on the row before for its window). Its time is the
    course's par. The builder tries 4 variants, each gentler (fewer ledges and presses, a slower beat, no wall-run, no
    presses), and drops the room if none passes.
  - Not yet: your ghost (the best run replayed) and pistons.
- **Collapse Timer** (the modifier) now drops real blocks: once a floor's time is up, every 2 s for each player still on
  it and not in a safe room, three blocks of ceiling (one where they are heading, two within 5) crack for a second,
  fall, and land as rubble (never on a door axis, a doorway or a feature): 2 + 6% of health to a player under one,
  4 to a monster. Waves are counted in the floor's rig state.
- **Protection:** inside a gate a player who isn't in creative breaks nothing but their powers' constructs (block tag
  `awaken:gate_breakable`): every painted block is protected, the Kit's dressing and the set pieces as much as the
  palette. Nothing is placed in a gate by a player (the placement event, which also covers fire, stripped logs and
  other block changes an item makes), and buckets neither pour nor scoop there. Powers' constructs still form.
- **Tours:** `/awaken gate interior goto rig` stands in the rig room's doorway (on whichever floor has one);
  `/awaken gate rig start` starts its first track, `/awaken gate rig solve` turns a Mirror Hall's mirrors to their
  solution, `/awaken gate rig at <start|first|gap|ledge|press|wall|ring|last>` stands you on that part of the gate's
  fullest Gauntlet; `/awaken gate open <rank> <theme> rig` rolls until the gate has a rig room (a Gauntlet with a press
  and a ring). `tools/autopilot/gauntlet.txt`.
- **Tests:** `gates_rig_collapse_run`, `gates_rig_mirror_hall`, `gates_rig_gravity_wells`, `gates_rig_gauntlet` (each:
  the archetype's solvability check on every rig room of 1,000 seeded gates, every rank and layout; the same plan builds
  the same rig, version 3 builds none; the server running it in the test level), `gates_rig_protection`.

### 17.6 Phase 1c: Flow, chains and mastery
- **Flow** (client, 0..1; `AscentFlow`): each move adds to it, less as it fills (+a × (1 − flow/2), ×1.25 once a chain
  is three links long): a rope's bite 0.08, a release 0.04 + 0.12 × the release window, a launch 0.10 + 0.06 × the
  window, the wings opening 0.06, a flap 0.02, the boom 0.25, a Leaper move 0.08, a slam's landing 0.10, a wall-run
  0.07, a wall jump 0.09, a ledge grab 0.03, a ledge jump 0.06. A wall taken hard (a bonk) takes 0.35 and the chain.
  Standing still on the ground drains it 0.015 a tick after 25 idle ticks (full to empty in ~3 s); in the air or on a
  wall it holds, then drains 0.002 a tick after 80 idle ticks. What it pays (small on purpose, never a wall): the
  pump ×(1 + 0.25 × flow), the carry's horizontal drag 0.988 → 0.988 + 0.004 × flow. Shown as one line of light between
  the XP bar and the hotbar (grows from the middle, whiter as it fills, flares at each link, its ends burn while a chain
  runs); hidden before awakening.
- **Chains** (server; `AscentServer.link`): links are a rope that bit (counted when the server accepts its state), a launch, the wings opening (Wings
  held), the boom, a double jump, an air dash, a dive, a slam's landing (Leaper held), a flap (Skyborn held), and the
  Runner's wall-run, wall jump, ledge jump (2 points each) and ledge grab (1). Links more than 50 ticks apart end the
  chain. A finished chain of 3+ links pays style XP once awake: xpToNext × 0.0012 × min(links, 25), at most 1.5% of a
  level a minute (`XpSource.WORLD`; counter `awaken:ascent_chains`). The longest chain is kept (`chainBest`); beating
  it from 6 links up is told (`AscentPayloads.Record`): "Longest chain yet · 12 moves" over the hotbar and a lift of
  Flow.
- **Mastery** per track (RUNNER, TETHER, LEAPER, WINGS, SKYBORN, SOVEREIGN): each link adds its points to its track
  (rope fire 2, launch 3, wings 2, boom 6, Leaper moves 2, slam 3, flap 1, Runner 1-2); level = floor(√(points / 25)),
  max 10 (level 10 at 2,500 points). Synced as six 4-bit levels in `AscentPayloads.Tiers`. Effects (predicted, the
  server agreeing where it checks; the Runner's wall-run +1 tick a level, below):
  rope reach +0.4 a Tether level (44 → 48; the server's anchor check allows 48 + 6 = 54), pump +3% a Tether level, double jump +0.012 and
  dash +2% a Leaper level, glide drag −2% a Wings level.
- **A level gained** shows as one quiet line over the hotbar for 5.5 s ("Tether mastery III · the line reaches
  further"; `AscentNotes`), never a toast; the first sync of a session only fills the client's copy. Runner mastery:
  a wall-run lasts a tick longer a level (both sides; ranks and Wall Stride still set the base).
- **The record**: `AscentMemory` schema 2 = tiers + mastery points (list, Track order) + chainBest + flags (1 = settled);
  schema 1 reads as no mastery yet and not settled. Kept through death.
- **Old worlds, first load** (`AscentServer.settle`, once per player, on login): an awakened player of rank B or above is
  given Wings (with Tether and Leaper), A or above Skyborn; anyone else is settled with nothing (later tiers come from
  their deeds and trials, §17.4-17.5). Three seconds after login the client is sent `Remember`, and it plays
  **"your body remembers"** once the player is free (no screen, on the ground, not moving by rope or wings; at most
  30 s later wherever they are): the wind ducks, a synthesised swell (`ascent.remember`: an inhale of air under a
  rising chord of glass), light rising around the body for 4 s, at 1.7 s the view lifts, the frame breathes out and the
  wings unfold for 2.3 s with one slow beat, then "Your body remembers." and the tiers given ("The line · The leap ·
  Wings · The sky") in the Holo look, about 6.5 s in all. Others nearby see the light and the wings and hear the swell
  (`EV_REMEMBER`). `/awaken ascent remember` replays it (dev).
- **A zip to a top** (any zip, the vow's too): when the block the rope bit has two cells of room over it (a ledge, a
  pylon, a branch), arriving pulls the body up and onto it: the rise needed (+0.5) as upward speed (at most 1.1), the
  zip's sideways speed dropped, and for 14 ticks a pull toward the block's centre, gentle against the face while the
  feet are under its top, then over the edge (one push at arrival was stopped by the face below the top). A face with
  more wall over it still gets the old hop off it.
- **The rope wraps** (soft failure, not a cut): checked every 2 ticks, something solid (never leaves) across the rope's
  middle becomes the new anchor (the face it meets from the hand, 0.06 off it) and the rope shortens to the hand's
  distance from it; the swing goes on about the new point (a strain, a small spark; the server re-checks the anchor).
  Only a snag below the hand, or one closer than 4, lets go into the carry; the rope is "gone" only when the block it
  bit (remembered at the bite and at each wrap, `AscentAnchors.blockOf`) has no collision any more. (Before: 5 of 8
  swings in the sandbox lane were cut, mostly by a false "gone" once the swing passed its anchor; after: none, six
  releases chained without touching the ground, 197 blocks in 12.7 s, so 300 in about 19 s.)
- **0 kicks** (`ascent_no_kicks`): the server's real movement checks (skipped for a singleplayer host, so tours can't
  show them) on a survival player flying an Ascent state for 15 s at 1.25, 2.25 and 3.2 blocks a tick, back and forth,
  one position a client tick and then in lag bursts of up to five client ticks: no move is sent back, no disconnect.
  Known limit (vanilla): six or more client ticks arriving in one server tick (a 300 ms stall) count as one packet's
  allowance, so above ~1.7 b/t that move is sent back once (a rubber band, not a kick). The floating kick exists only
  on dedicated servers with allow-flight off; AscentServer resets its count every tick a state runs, before vanilla
  counts (to be seen on a dedicated server).
- **Latency** (`ascent_latency`, the gate's latency tours as a test): a real swing from a block 20 up, a release and a
  30-block fall to a hard landing, every state message reaching the server 300 ms (6 ticks) after its positions: no
  refusal, no move sent back, no fall damage, the state ends, the arrival still counts as by movement.
- **The Lens's sense of speed**: from 1.2 blocks a tick in an airborne state, smoothstepped to full at 3, the Feel
  Director hands the Lens the strength and the heading on screen (projected by the camera's own matrix); none with
  reduced motion, on the ground, or when heading away from the view (§14, texel 16).
- **Wings in first person**: your own wings are drawn from the body, only the outer half of the span (the root would
  sit on the camera; Phase 0 drew it and got white wedges), the cut edge fading in over 0.15 of the span, the body at
  60% of its alpha and the glow at 45% (this close, full strength read as solid blades). They show at the edges of
  view: the tips flick into the top corners on a beat, the outer wing rises into view in a banked turn.
- **Moving air** (`core.api.AirHooks`, for the Wilds' updraft pylons and windways): sources registered on both sides
  add an acceleration (blocks a tick²) at the feet; the client's free states take it after their own step (the carry
  ×1, wings ×1.25, flight ×0.6; a rope none). Sources read only what both sides have (blocks, block entities,
  entities, synced data) and allocate nothing. Scale (applied every tick, so it adds up): an updraft of 0.045 up climbs
  open wings 2-4 blocks a second (0.13 shot them up 58 in 5 s); a windway of 0.02-0.03 along carries a glide.
- **The updraft vent** (`awaken:updraft_vent`, a Precursor grate, light 10, pickaxe): a column of rising air, radius
  3.5, 34 high from its top, lift 0.045 to 24 up then fading to 0 at the top, 35% less at the rim; columns don't stack
  (the strongest counts). Drawn as 24 soft filaments of pale air rising up the column (6-10 long, 0.3 a tick), turned
  to face you, over a faint haze its width, both fading where the lift fades (additive: strength in the colour, not
  alpha; one draw a vent, seen from 160); heard as a soft rising rush every 2.5-4 s near it (`ascent.updraft`).
  Client side the loaded vents are a list their block entities join, so the air costs a list walk. The sandbox puts
  one on a 13-high pillar off the tower's side (its column reaches past the deck). The Wilds' pylons stand on them.
- **The Wilds' hooks** (§17.4): every server tick a ladder state runs (a rope, the carry, wings, flight, a wall-run, a
  hang) reports `ArrivalHooks.Way.ASCENT` for 5 ticks; the Horizon's need "wings" is met by Wings, or by Tether and
  Leaper together (`HorizonHooks.addReach`).
- **Routes** (dev; golden runs for Carillon's TryDriver and the Ascent's regressions; `AscentRoutes`): pilot lines
  `route rec <name>` / `route play <name>` / `route stop` record a run tick by tick (movement keys, the movement key,
  yaw and pitch) to `<game dir>/awaken-routes/<name>.route` (text: a start line, one line a tick, an end line) and play
  it back through the same keys from the same start (a `tp` command, then 20 ticks to settle). Playing logs where the
  run ended against the recording's end ("off by d"), so a tour can tell a route that still works from one a change
  broke. Record from standing still; at most five minutes.

### 17.7 The Tether Vow (the rope, earned at the first vault)
- Who: awake, in the overworld, without the rope, the `ascent` option on. Where: a Precursor vault's forecourt (local
  x 4-18, z 0-3, feet 1-5 over the floor: between the pylons, before the sealed door, which no low rank can open; every
  vault qualifies until one is kept, so the first one reached is where it happens). Checked once a second per player.
- The vault's turn is read from its pylons' own blocks at the front corners (the amber lights at local y 20, the trim at
  19, the polished cornice at 11: at least 4 of the 6); terrain can't fake them, and a vault dug into a hill keeps them
  (open air at the back corners, the first try, failed on the first real spawn vault). The vault's box is its piece's
  (the structure start's is padded by 12). A layout from before the vault redesign reads as none and is skipped. `TetherVow.Site` maps local to world like
  `PrecursorRuinPiece`.
- The call (3 s): the Lens takes the vow's look (`awaken:vow` in `lens/looks_ascent.json`: cold light, deep blue
  shadows, vignette, bloom), the wind ducks, a low glass note swells (`ascent.vow_call`), the vault's light gathers off
  the paving and drifts toward the mast; "The vault knows you."
- The line: the server gives TETHER (`Ascent.grant(..., "tether vow")`) and names the line's end: a plate round the
  nearer pylon's spire (inner one first; weathering may have taken some). The client throws the rope there for the
  player (`AscentMoves.zipTo`, a zip held until it arrives, key or no key; the server checks its anchor as always),
  with `ascent.vow_line` and a launch kick: "The line is yours.", then "Hold <key> to throw the line. Let go to fly."
  (the key's own name), the look easing back, then "Reach the beacon mast." while a ring of light pulses at the mast.
  No plate left: the rope is given without the pull.
- Kept: feet 15+ over the floor within 5 of the mast's line, within 2 minutes and 64 blocks: `ascent.remember`, full
  Flow, "Vow kept.", 10 Tether mastery, a fifth of a level's XP (DISCOVERY), counter `awaken:ascent_vow_kept`, flag
  `VOW_KEPT` in the record. Lapsed: "The mast will wait." (the rope stays; nothing is lost).
- `ascent_tether_vow` builds a vault from its blueprint in each turn and checks the turn, the forecourt, the pylon
  anchor (real to the server, the line clear) and the mast, then runs the director: call, line, kept.

### 17.8 Phase 4a: Aether (Skyborn's flight meter)
- Flight (SOAR) runs on Aether, 0..1: cruising drains a full meter in 60 s, boosting in ~19 s, hovering in ~38 s, each
  wingbeat 1.2%. Out of flight it fills in the sun (sky over the eyes, a bright sky) in 20 s, in shade or at night in
  90 s; inside an updraft's column it fills in 6 s, even while flying. Flight needs 8% to begin (less: a weak beat that
  catches nothing). Skyborn mastery trims the drain 3% a level.
- Empty in flight: the wings stop beating and hold a glide (never a drop). The client predicts its meter; the server
  keeps its own at the cheapest drain (it can't see boosts), so a fair flight is never refused. It refuses a flight
  start it can't afford (under 8% by more than 0.15) and folds a flight 0.15 past empty into a glide (`Stop(SOAR)`,
  which the client takes as its own empty). Time aloft earns Skyborn mastery (a point every 5 s).
- Shown as a 2-pixel gold line in the gap between the hearts and the XP bar while flying or not full (over the XP
  bar's own edge it was too faint to read) (fading out once full on the ground),
  flickering under 20% in flight, dimmed for a moment when a flight is refused for want of it.
- `ascent_aether` holds those numbers and the server's refusals.
- **The barrel roll** (Skyborn): in flight, a second tap of left or right within 7 ticks (vanilla's double-tap window)
  throws the body 0.85 a tick that way and 0.1 up (about 3.5 blocks before the flight's drag takes it), for 3% Aether,
  once a second. Others see the figure turn once about its line of flight (10 ticks, eased; `EV_ROLL`) and hear it;
  your own view only tilts into it within the comfort cap (at most 4°) with a small kick and a breath of FOV. A roll
  is a Flow link and 2 Skyborn mastery. (Pilot `double <key>` taps like a player for tours; the autopilot's own command
  round trip put taps 11 ticks apart.)

### 17.9 Phase 2: Wings, the sky platform's way up and its core
- **The rig** (`above.ruin.SkyRig`): placed once on each sky platform the first time its start chunk loads (queued on
  `ChunkEvent.Load`, built on a later server tick once the core's and the chain's columns are loaded; done when the
  core is there), so old worlds' platforms get it too and the blueprint never changes. From the deck's centre (cx, deck,
  cz): a pier of trim three cells wide on the (+x, +z) diagonal from 7 to 12 out (local a, b in 7..12, |a - b| <= 1),
  the rim's trim studs on it cleared; a chain from under its end (12, 12) down to the lowest ring. The diagonal: the
  blueprint's four torn-up islands hang 19 out on each side (up to 3 off the line, 3.8 wide) with their tether chains
  along that line back to the rim, in any turn; the first rig ran out along +x, under the east island and its tether,
  which cut ropes, blocked zips and left no room to stand on the pier. `ascent_sky_rig` checks every rig cell is 3+
  clear of any island's edge and under no tether.
- **Rings** (`awaken:anchor_ring`, unbreakable, light 12): from 9 under the deck down to 26 over the ground under the
  chain (the lowest of four columns 5 out diagonally), evenly spaced at most 22 apart, each 3 off the chain a quarter
  turn round from the last (+z, +x, -z, -x), joined to it by a chain stub. Each is nearly overhead from a hang under the
  one below it (a vertical zip, below). Over water the lowest ring gets a 5x5 buoy at the surface to stand and throw from
  (the Ascent stands aside while swimming).
- **The perch**: the top ring stands in a 3x3 of plating (trim corners), its stub on the chain's side. From the hang
  under the ring below, a zip to the perch's underside near its edge vaults the body onto it; from it, a zip to the side
  of the pier's end (7 up) pulls the body onto the pier.
- **The core** (`awaken:wing_core`, unbreakable, in the cabin's middle on the deck): twice a second, a player within 3
  without Wings who came up by moving (`ArrivalHooks.byMovement` and `Ascent.arrivedByMovement(p, 20)`: a third of the
  last 20 s in a ladder state or a Runner move and no block placed) is given Wings (`Ascent.grant`, "sky platform core")
  with the gift's scene (`Remember` mode `GIFT_WINGS`: "Wings of light.", the wings unfold round the body, then the
  hint to open them), `ascent.vow_call`, counter `awaken:ascent_wings_core`. Anyone else is told, at most once a minute,
  that the core answers only to the long way (`awaken.ascent.core.dark`). It breathes a ring of light every 3 s. The
  platform's `#awaken:warded_sky` ward, its loot table and the Horizon's "wings" reach are unchanged.
- **The rope's moves for a ladder** (§17.6 rope, both sides):
  - *Vertical zip*: a throw at something more than 6 above and nearly overhead (sideways under 0.45 of the rise) zips
    up to it rather than swinging.
  - *Ceiling hang*: a zip that bites an underside hangs there on a rope 2.6 long, still, ready to throw again; unless
  - *the ledge vault*: the underside is a floor's (full blocks with two cells of room on them, at least two together)
    with an open edge within 3 cells, tried first toward the side the zip came up from. Then the body vaults it: out
    past the edge's face (the head kept under the ledge), up beside the face until the feet clear the top, then in
    onto it (up to 40 ticks; any other move takes over). A zip to a face with room over it vaults the same way over that
    face's edge (a ledge, a pylon, the pier). Lone rings and branches still give the hang. The vault ends with the
    landing (left set, the next carry pulled toward the old ledge).
  - *Pass-through*: for 20 ticks after letting go of a rope, a throw passes through the block it held (you throw past
    the ring you hung under to the next one up).
  - *Zip stall*: a zip pressed against something, moving under 0.05 a tick for 10 ticks, lets go (never pinned).
- Dev: `/awaken ascent sky goto` (the nearest platform that really generated, by vanilla's own search; rigs it if not
  yet; puts you on the ground under the lowest ring); pilot `run climb` climbs the rings, the perch and the pier, walks
  round the cabin to its door and in, and ends when the core gives Wings (`tools/autopilot/ascent-sky.txt`: from a buoy
  at sea level to the pier in about 21 s, the core 7 s later; seed 4246).

### 17.10 Ascension trials: the Leaper's Gauntlet
- **Opened for you** (`AscentTrials`): once you hold the rope (not the leap) and have swung enough to know it (Tether
  mastery level 2: 100 points), awake, in the overworld on the ground, checked every 5 s: a scripted gate opens in front
  of you (`Gates.openScripted`, `Gates.leaperTrialPlan`), once (`AscentMemory` flag `LEAPER_TRIAL` = 4), with "A gate
  is beating like a running heart: the Leaper's Gauntlet." It never breaks. Its card: "The Leaper's Gauntlet. A course
  of the Foundry, and at its end a chasm no running jump will cross." · Trial: run it to the far side.
- **The gate** (`GatePlan.LEAPER_TRIAL` boss marker, content version 5): a D-rank Clockwork Foundry of one floor, the
  antechamber, the course's hall (1x3 slots, its doors on one line) and a calm room past it (`ANNEX_EXIT`: no monsters,
  no pressure). The objective line: "The Leaper's Gauntlet: run the course to the far side", then "The way out is open
  past the course".
- **The course** (`RigGauntlet.trial`, laid by hand, a Gauntlet in every other way: the clock, rings, medals, the pit):
  a floor row and a 2-row gap, two ledges, a ring, two presses (if the hall is tall enough; a gentle 56-tick beat, down
  16), a 5-row wall-run, a ring, four rows of run-up to the **edge**, then the **chasm**: 5 rows of open pit (no sprint
  jump crosses more than 3; a double jump or an air dash crosses it with room to spare), and on to the far landing. The
  check's runner crosses the chasm at a dash's 0.6 a tick (up to 7 rows).
- **The edge** (`TrialHooks.atEdge`, gates → the Ascent): stepping on the edge row without the leap gives it (LEAPER)
  with a short moment at once (`Remember` mode `LEAP_EDGE`, about 3 s, no waiting for stillness): the remember swell a
  little higher, light rising at the feet, "Your legs remember the air." and "Jump. In the air, jump again."
- **The far landing** (`TrialHooks.done`): the clock stops (its medal as any Gauntlet's), then once a player (flag
  `LEAPER_TRIAL_DONE` = 8) the gift's scene (`GIFT_LEAP`: "The leap is yours." · "In the air: jump again · sprint to
  dash · sneak high up to slam."), 10 Leaper mastery, a fifth of a level's XP (DISCOVERY), counter
  `awaken:ascent_leaper_trial`. The gate is done: cleared (as after a Lord), the exit opens in the room past the course,
  and it comes down once everyone has left.
- `core.api.TrialHooks` (installed by the Ascent; the defaults give nothing): `atEdge(player, trial)`,
  `done(player, trial, ticks, medal)`; `TrialHooks.LEAPER` names this trial.
- Dev: `/awaken ascent trial leaper` opens it now; `/awaken gate rig at runup|edge`; pilot `run leap` (sprint, jump at the
  edge, dash, jump again); `tools/autopilot/leaper-trial.txt`. Test: `gates_leaper_trial` (the plan's floor, the course
  and its chasm, the check with the leap, the same every time; run for real: the edge gives the leap, the far landing
  clears the gate and opens the way out).

---------------------------------------------------------------------------------------------------------------------

## 18. Pixels (render scale and upscaling; Far Sight path 4, approved 2026-10-24)

The world is drawn at a lower resolution and rebuilt to the screen's; menus, the HUD and all GUI text stay native.
Plan: the Far Sight page, path 4 (https://claude.ai/artifact/LvAoTbYB82o6ggcHS73rX1). Measurements and crops:
`docs/perf/pixels/REPORT.md`.
- A Turbo feature (the Turbo contract: internals only in `dev.awaken.turbo`, a public-API fallback, version-locked,
  kept only with a measured win, access transformer before mixin, never reflection). Code: `dev.awaken.turbo.pixels`;
  mixins `dev.awaken.turbo.pixels.mixin` in its own config `awaken.pixels.mixins.json` (client only, not required,
  `defaultRequire` 0). Version lock: Minecraft 26.3 with NeoForge 26.3.0.95 exactly (`Pixels.MINECRAFT/NEOFORGE`);
  any other: off, one log line. Until turbo-bridge's shared probe lands on build/awaken-v1 this check is local.
- Why a mixin: 26.3 has no render-scale option and no event at the two moments that matter. `FrameGraphSetupEvent`
  fires after `LevelRenderer.render` has sized its own targets (OIT, depth bounds) from the main target, and render
  passes demand attachments of one size (`FrontendCommandEncoder.createRenderPass`). So `GameRendererMixin` injects
  two calls into `GameRenderer.render()`: before `renderLevel()` and after `applyPostEffects()`. An accessor
  (`RenderTargetAccess`) sets a render target's protected textures; turbo-bridge owns the shared access-transformer
  file, so Pixels keeps to its own mixin config instead of adding lines there.
- How: for the world pass the main target and the hand's depth target (`hud3DTarget`) lend themselves smaller
  textures: their own colour and depth are set aside and the low-resolution ones put in their place, on the same
  objects, with `width`/`height` to match. Everything that holds the main target (the level renderer and its frame
  graph, the sky renderer, Sodium, Distant Horizons (checked: pixels-dh tour), NeoForge's render stages, post chains)
  draws at the lower resolution without knowing. After the post effects the real textures go back and the upscaler fills the main
  colour; the GUI then clears depth and draws on top, native. The `Globals` UBO's `ScreenSize` stays the window's
  (line widths keep their screen-pixel width).
- Lens runs before the upscale, at render scale, with every other post effect: its depth is the world's own, its
  bloom and haze get cheaper with the pixels, and FSR expects a finished, display-referred frame.
- Upscalers (`PixelsPipelines`, full-screen passes on vanilla's `core/screenquad`, both graphics APIs, registered
  optional; each pass clears its target rather than loading it, which is free on a tile-based GPU):
  SHARP (default): one pass, bilinear plus FSR 1's RCAS (contrast-adaptive sharpening with noise detection,
  strength exp2(-0.2) = 0.87) from a ring one world pixel apart. FSR: a port of AMD FidelityFX FSR 1.0 (MIT; the
  notice is in each shader), with EASU (edge-adaptive Lanczos-2, 12 texel taps, dering clamp) into a screen-sized
  intermediate, then RCAS into the main target. BILINEAR: one linear tap. A pipeline that doesn't compile falls back:
  FSR and SHARP to bilinear, bilinear to native.
- Measured (docs/perf/pixels/REPORT.md, M4 at 2880×1658, Vulkan, RD 32, Lens Swift): GPU time saved a frame with
  SHARP: 0.75 saves 0.9–1.6 ms (10–14%), 0.67 saves 1.2–2.1 ms (13–19%), 0.5 saves 1.5–2.2 ms (22–25%). Frame p50
  fell 0.8–1.8 ms where the GPU was the limit; p99 doesn't move (its spikes are CPU work). The upscale costs bilinear
  0.12 ms, SHARP 0.24 ms, FSR 1.21 ms, so on Apple's GPU FSR eats most of what 0.67 saves (only 16–23% of the world
  pass scales with pixels; the rest is geometry). Fine block textures soften at every scale; GUI text never changes.
- Defaults (decided 2026-10-24 from those numbers): `renderScale` 1.0 (off), `upscaler` SHARP. Recommended:
  1.0 on the MacBook's own screen (about 1 ms, and nothing once a scene sits at 120 Hz or on the CPU, doesn't pay
  for the softer textures); 0.75 with SHARP on a 4K screen (the pixel-bound part grows 1.73×: an estimate until a
  Dell run, which needs Euan's OK).
- Holo: panels draw inside the world (the level's always-on-top phase), so at render scale their text would be
  upscaled with it (measured: softer, with RCAS fringes at 0.67). While a panel holds the player's attention
  (`UiHooks.focus() > 0`: the K menu, a reading screen) the world renders native, so panel text, Lens's bloom on the
  glow and its focus dimming stay exactly as without Pixels; the low-resolution targets are kept for after. Toasts,
  cards and the HUD are GUI and always native.
- Safety: both hooks must be seen in one frame before the first swap; a frame that ends with the target still lent
  (`RenderFrameEvent.Post`) gets it back and turns Pixels off for the session, as does any exception in a pass.
- Client config `awaken-pixels-client.toml`: `renderScale` 0.5..1.0 (1.0 = native, off) and `upscaler` SHARP |
  FSR | BILINEAR. Autopilot `pixels <scale> [sharp|fsr|bilinear] | config | state` overrides them for a tour (not
  saved). Tours:
  `tools/autopilot/pixels.txt` (the measurement), `pixels-smoke.txt`, `pixels-dh.txt`; crops: `tools/pixels-crops.py`.
  `pixels gpu on|reset|<label>` (measurement only) writes GPU timestamps before the world, after the post effects and
  after the upscale, and logs the world pass's and the upscale's GPU time per label (`PixelsTiming`): on a busy Mac
  the frame time is the CPU's, so the GPU time is what shows what Pixels saves.
