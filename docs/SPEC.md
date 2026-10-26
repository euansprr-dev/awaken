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
| `powers` | power framework, all powers, Power Cores, energy, cooldowns, keybind casting, Five Forces interactions, a power's shake (its hit-stop is every blow's, `core.api.CombatFeedback`) |
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
| `anims` | Hands (§24): the first-person system (the world body, the view layer, view clips), `core.api.FirstPerson` |
| `living` | The Living Wilds (§21): regions, the Holo combat layer, Wayfarers and quests, bounties, the World Pulse and happenings, populations and camps, crafts, wonders, settlers and villages |
| `arms` | Weapons and combat (§25): the three verbs (strike, guard, dodge) on every melee weapon, fighting styles on one timing table, deflect, block, posture, Resonance, the feel of a blow (`core.api.CombatFeedback`), its sounds |

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

The band (key **K**, §30): six pages, Status · Powers · Journal · Truths · Codex · Map. Status shows level, rank, path,
title and XP, the stats with + buttons when points are unspent, then the Journal; Powers holds the loadout (provided by
`powers` via `StatusSections`); the Map page is the Atlas.

---------------------------------------------------------------------------------------------------------------------

## 6. Powers

Slots: 4 active (Z X C V), Ultimate (R), Movement (Left Alt). Passive powers apply when owned.
`PowerType` registry (custom registry `awaken:power_type`) with code; data numbers in the same class.
Families: Kinetic, Velocity, Flight, Energy, Shadow, Mind, Architect, Tech, Spirit. Forces: HEAT, CHARGE, GRAVITY, SPIRIT,
FROST (+ SPORES, WATER, VOLATILE, MIRROR as environmental tags).
Power Cores: item `awaken:power_core` with component `{power, variant}`. Using it learns the power (or upgrades a known one).
Icons (2026-10-25): 32x32 GUI sprites `power/<id>`, generated by `tools/art/specs/powers.py` (kit `powericons.py`,
glyphs `powerglyphs.py`). The frame's shape names the family: Kinetic shield, Velocity chevron, Flight ringed world,
Energy diamond, Shadow crescent cut, Mind eye, Architect notched block, Tech chip octagon, Spirit teardrop. One light
from the top-left shades every frame and glyph. A Power Core is a crystal with its power's glyph inside (item model
select `awaken:core_power` on the power's path; an emissive `_glow` layer per power; no power shows a blank crystal).
Movement powers are client-predicted (velocity applied locally, server validates and corrects).
Feel: hit-stop 40–80 ms on heavy hits (client time scale on the hit entity's render + camera kick), shake toggle in config.

Launch power list (≥45): see `powers/PowerCatalog.java` header for the authoritative list with numbers.

The Tech movement power `awaken:grapple` is shown as **Snap Cable** (2026-10-26, progression plan decision 11), so
"the grapple" only ever means the Tether; its id, and saved worlds, are unchanged.

---------------------------------------------------------------------------------------------------------------------

## 7. Gates

Overworld: `awaken:gate` block structure (frame + core) at a surface position; client renders a light pillar coloured by
rank (E #F4F6FB, D #5FD08F, C #6FB3FF, B #B48CFF, A #F2A541, S #FF5A6A, Sovereign black-gold), visible up to 1500 blocks
(drawn at clamped distance beyond render distance). Our own soft light, never vanilla's beacon beam: camera-facing
ribbons with a bell-shaped falloff across their width ((1 - x²)³, nothing and flat at the edge), a near-white core and
the rank's glow added as light, a wide haze in the rank's colour alpha-blended (it carries the pillar by day), all
thinning to nothing with height; no world fog (it counts height as distance and cut the column off flat), the top kept
inside the far plane. Drawn after the level in a pass of its own (depth-tested, no depth written), never through the
level's translucent passes (with improved transparency their composite writes a translucent's depth wherever it shows,
so Lens took each ribbon's whole quad for solid and left its clouds out there: a hard-edged slab). Admin `/awaken gate frame <dist> <lift>` puts the camera on the line from the nearest gate toward
Carillon's centre, facing the pillar (tours).
Gate card shows rank, breaks-in, omen; PER ≥20 adds layout, ≥40 modifiers, ≥60 floor count; Veil scouting shows all.
A gate of another rank than the hunter's says so in a "For you" row: "above your rank", or "below your rank · pays less"
(floors below your rank pay ×0.3, `Curve.BELOW_RANK_FACTOR`; §17.25).

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

- `dev.awaken.signal.Signal` — `send(ServerPlayer, SignalWindow)`, `toast(ServerPlayer, Component)`,
  `toast(ServerPlayer, Component, Notices.Tier, String topic)`; `SignalWindow.Builder.tier/topic/moment` (§26).
  `dev.awaken.signal.Finds` — `tell(ServerPlayer, Find)`: how a new find is told (lesson, moment, first of kind, filed).
- `dev.awaken.core.api.Notices` (§26) — `enum Tier {CRITICAL, IMPORTANT, AMBIENT}`; client: `claim(tier, topic)` before
  a module's own HUD line takes the screen, `file(tier, title, line)` into the Journal, `quiet()`, `hold(owner, bool)`.
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
  `core.api.MovementHooks.wallRunning(Player)` / `hanging(Player)` read the Runner's moves, `stairSliding(Player)` a
  slide down stairs (§17.22; `sliding` is true too). `core.api.AirHooks`
  (`register(Source)`, `Source.airAt(Level, x, y, z, double[] out)`): moving air the free states feel (§17.6).
  `core.api.CameraHooks` (client): `installToggle(BooleanSupplier)`, `toggleView()` (the View key asks it first; with
  Shoulder Surfing it flips first person and the shoulder view, §17.13).
- `dev.awaken.terrain.api.BiomeTable` — `archetype(Holder<Biome>)` (the vanilla biome an Awakened one stands in for, else
  its own key), `tag(String path)` (the table's `awaken:` biome tags: `fauna/`, `has_lair/`, `lair_style/`, `cottage/`).
- `dev.awaken.terrain.api.CityBasin` / `CitySite` — the city basin (§15): `request(Predicate<MinecraftServer>)` (the city's
  rule, once at mod init), `site(server)` (centre, core and blend radii, street Y, the river's centre line, whether the
  basin is carved), `reserved(x, z)` (inside the core plus 48: thread-safe, allocation-free; worldgen and runtime
  placement keep out), constants `STREET_Y` 70, `CORE_RADIUS` 330, `BLEND_RADIUS` 700, `RIVER` (the city's fixed path).
- `dev.awaken.terrain.alpine.Erosion` / `AlpineSurface` — Awakened Alpine's erosion and snow-and-rock rule as pure,
  allocation-free maths (§15): `Erosion.erode(x, z, gx, gz, strength, finestCell, out)`, `AlpineSurface.snowLine/snow/
  stone/erosionStrength`. Far Sight predicts and shades with the same numbers; its shaders mirror them.
- `dev.awaken.city.City.skyline(layout, chunkX, chunkZ, worldSeed, topY[256], top[256])` — the city as planned over one
  chunk, from the plan alone (any thread): each column's highest placed block and its world y, or none where the city
  leaves the land; false outside the city. Far Sight's far skyline reads it (§20).
- `dev.awaken.terrain.city.CityBasinFunction.Shape.ground(natural, x, z)` — the basin's ground height over a column
  whose natural ground stands at `natural` (what the density mode builds), for far terrain.
- `dev.awaken.hearth.Hearth` — a Hearth's owner and members ("Together"): `record HearthInfo(owner, ownerName, anchor,
  tier, radius, members, foundedFrom)` with `has(UUID)`, `hearthsOf(MinecraftServer, UUID)` / `hearthsOf(ServerPlayer)`,
  `home(ServerPlayer)`, `isMember(ServerPlayer, BlockPos anchor)`, `isMember(MinecraftServer, UUID player, UUID owner)`,
  `hearthAt(ServerLevel, BlockPos)`; plus `anchor`, `atHome`, `hasRoom`, `isSanctuary`, `inRoom`, `ownedEchoes`.
- `dev.awaken.citizens.api.Settlements` — the people engine's settlements (§28.4; overworld, server thread):
  `register(ServerLevel, SettlementSpec)` (or the running one with that key), `unregister(id)` (never Carillon),
  `at(Level, BlockPos)` (the smallest that contains it), `byId(id)`, `all()`, `bodies(id)`; `CARILLON` 0 / `"carillon"`.
  `record SettlementSpec(key, name, origin, radius, RosterSource source, plannedEvents)`. View `Settlement`: `id`, `key`,
  `name`, `origin`, `inside(BlockPos)`, `people`, `bodies`, `awake`, `standing(Player)` (RenownHooks tiers, its own
  memory), `stations(String placeKindOrLotTag)`, `district(BlockPos)`. `RosterSource.build()` → `Built(roster, graph,
  lots, water, districts)`; `RosterSource.jar()` is Carillon's; `RosterBuilder` builds one in code (nodes, streets, lots,
  places with spots, routes, smithy, wash place, lamp, bell, bakery, tavern, stall, households, groups, people and their
  days, extras).
  (W3, §28.6) `Settlements.onTalk(Talk)` (a settlement other than Carillon answers a player's talk first: `talk(ServerPlayer,
  Settlement, rec)`), `Settlements.installVoice(Voice)` (`standing(Settlement, ServerPlayer)` "stranger|known|friend|kin",
  `fill(Settlement, ServerPlayer, line)`); `Settlement.records()`, `job(rec)`, `personName(rec)`, `age(rec)`, `body(rec)`,
  `say(ServerPlayer, rec, line, ticks)`, `acquaint(ServerPlayer, amount, ceiling)`; `RosterBuilder.emitter(x, ry, z, kind)`
  (0 bakery, 1 house, 2 tavern, 3 forge, 6 mill, 7 well) and `RosterSource.Built.emitters`.
- `dev.awaken.village.api.Villages` (§28.6) — AWAKEN's settlements: `record View(key, name, kind, family, origin, radius,
  settlement, people)`, `settlementAt(Level, BlockPos)`, `nearest(Level, BlockPos, within)`, `all(Level)`,
  `standing(ServerPlayer, key)` (STRANGER 0, KNOWN 1, FRIEND 2, KIN 3), `addStanding(ServerPlayer, key, points, why)`,
  `damage(server, key)` (`record Damage(pos, original, day)`), `repaired(server, key, pos)`, `history(server, key)` and
  `history(server, key, line)`, `prosperity`/`addProsperity`, `mood`/`addMood` (0-100), `reserved(Level, BlockPos,
  margin)` (a settlement's ground, planned ones included: gates and Hearths keep off).
- `dev.awaken.city.SettlementPaint` / `PlanView` / `VillageStyles` (§28.6) — the city's grammar for other settlements:
  a `Layout` (a `PlanView` of ground kinds, heights and plots, its origin and street level, its own ground and pieces on a
  `Canvas`, who lives in each plot as an `Interior`) painted per chunk by `paintChunk` into a proto chunk (`sweep` after);
  `standingCells`, `chimneys`, `census`, `fingerprint`, `dump` for tests and the studio; styles keyed by land and wealth in
  `Plot.district` (`VillageStyles.district(family, wealth)`).
- `dev.awaken.core.audio.MusicLayers` — the layered music: `register(Slot, priority, name, Provider)` (a provider names a
  player's context `<ns>:<path>`, read from `assets/<ns>/music_layers/<path>.json`, or null; it may set the given
  `Intensity.Signals`' `pressure`, `forced` (0..3) and `arrival`), `current(ServerPlayer, Slot)`, `level(ServerPlayer,
  Slot)` (-1 without a context). `dev.awaken.gates.Gates.pressure(ServerPlayer)` — the floor's pressure stage (0..3).
- `dev.awaken.core.audio.client.Soundscape` (client, world sound): `hush(Object owner, Vec3 at, float radius, float
  depth, int ticks)` (birds and insects quiet within the radius; depth = what is left), `hushAt(x, y, z)` (what is left
  at a point; the wild's visible animals read it), `register(Conductor.Source)`
  (another module's ambient voices, `voices()`, counted in the Conductor's budget of 16), `yieldWeight(Conductor.Yield)`
  (`at(x, y, z)` 0..1: how much of the world bed gives way there; the city), `natureLevel()` (0..1, how much nature
  ambience to let through now; AmbientSounds follows it), `overlay(owner, Identifier soundscape, weight, ticks)` (a
  soundscape over everything while renewed; at most two), `muffle(owner, cutoffHz, natureLeft, ticks)` (the air's
  filter closed and nature held back while renewed). `/awaken audio world [off|on]` (client).
  Read by the Conductor: `terrain.client.ClientWind.field()` and `terrain.client.TerrainClientConfig.leafRustle()`.
- `dev.awaken.core.api.*Hooks` — install-once bridges between modules: `GateHooks`, `HearthHooks` (`inside`, `belongs`,
  `hearthsOf`, `household`, `shareHearth`, `within`), `PartyHooks` (`members`, `sameParty`; see "Together";
  `Parties.sharing(player, at)` is who of the party shares a kill), `MobHooks`,
  `StoryHooks`, `DescentHooks`, `DreamHooks`, `PowerCoreHooks`, `NowHooks` (the Now tracker), `WorldHooks` (landmark rumours),
  `MovementHooks` (the body's moves for powers), `PlayerClips` (client: full-body clips on a player when the Player
  Animation Library is installed; a no-op otherwise), `FirstPerson` (Hands, §24: the world body, view stances, view
  clips and their events; every module's first person goes through it; `View.gripMain`/`gripOff` [x, y, z, weight]:
  the arm drawn to a held thing's grip, additive, Arms 2026-10-26). The Wilds (§17): `WardHooks` (`register(Source)`, `at(level, pos)`:
  wards for sites placed by code; structures just join `#awaken:warded`), `ArrivalHooks` (`report(player, Way, ticks)` for
  movement systems, `byMovement(player)` for rewards, `history(player)`), `HorizonHooks` (`register(Source)` of sites,
  `addReach(Reach)` for needs such as `WINGS`, `collect(player)`, the pure `pick`).
- `dev.awaken.progression.Progression.xpLedger(player)` — XP by `XpSource` and by world-XP source key (§17 telemetry).
  `Progression.onXpGranted(listener)` (every grant after scaling), `Progression.worldXp(player, source, fraction,
  afterBudget)` (a multiplier applied after the budget), `XpBudget.register(source, perTenMinutes)` (§21.3).
- `dev.awaken.living.api.Regions` — the region map (§21.2): `levelAt`, `regionAt`, `creatureLevel`, `bandOf`.
- `dev.awaken.living.api.Quests` — the Wayfarer chains (§21.5): `advance(ServerPlayer, Identifier "awaken:<type>/<target>")`
  (owners report uses and discoveries a step may wait for: `awaken:use/ascent_zip`, `awaken:discover/<key>`; an
  `amount` overload), `talk(ServerPlayer, who)` (another module's person, e.g. "kestrel", "oren": call it first from your
  own interaction; true means a chain used the talk and you say nothing), `active`, `tracked`.
  `dev.awaken.core.api.LivingHooks` — install-once bridge: whether the living module pays kill XP, the level moment.
- `dev.awaken.signal.client.HudLook` (client) — how the System's cards draw: `card(g, x, y, w, h, accent, alpha, reveal,
  glitch)`, `text(...)`, `centered(...)`, `glow(...)`, `shake(glitch)`, `holo()`. Flat `Glass` unless the holo module
  installs its projected look; the flat paths make exactly the calls they made before. `ReadingScreen` marks a screen
  the player reads (the first-power choice): with the projected look the world dims behind it (`UiHooks.focus`).
- `dev.awaken.holo.client.HoloWorld` (client) — Holo glass in the world (§21.1's creature plates first): fill a reused
  `Spec` (bottom-centre x, y, z; w×h units; alpha; accent; scan) and call `plate(spec, content)` during
  `ExtractLevelRenderStateEvent`, `content` writing fills and text into the plate's `HoloCanvas` (units, y down). Drawn
  that frame by HoloRender exactly as a K-menu panel (same HoloPanel/HoloCanvas, glass shader, edge glow, halo, scan
  line, see-through text with its glow), facing the camera; world-sized up close (`PlateScale.WORLD_UNIT` 0.021 m a
  unit) and never under `PlateScale.MIN_PX` 2.1 framebuffer pixels a unit at 1080 lines however far. Writes depth like
  the panels (Lens leaves its light, AO and haze off it: `lensPlates(float[])` hands Lens the plates drawn this frame,
  9 floats each, the camera-relative centre and half-width and half-height vectors; §22), draws over walls, never counts toward `UiHooks.focus`. `on()` false with
  reduced motion or without the holo pipelines; `allowed()` false with reduced motion (fallbacks stay off too). At most
  `MAX` 6 a frame. Frame cost per plate: three custom-geometry submits plus five text submits per line; no allocation
  once a slot has drawn, a line's record rebuilt only when its position, colour or scale changes. Test `holo_plate_scale`.
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
- `dev.awaken.core.api.TipHooks` (v0, §16.0 decision 24; docs/carillon/m4/onboarding.md): just-in-time tips. Server:
  `offer(ServerPlayer, Identifier tip)`, `done(ServerPlayer, Identifier tip)`. Client: `client().offer(tip)` (idempotent,
  call while the context holds), `done(tip)`, `hold(owner, held)` (cinematics hold every tip), `wants(tip)` (false once
  retired or tips are off: skip the probe). Installed by the signal module in M4 B6; until then every call does nothing.
  Tip text and triggers are data; any module may offer its own tips (the Ascent for its tiers).
- `dev.awaken.mobs.client.life.CreatureLife` (client, §23) — the life layer for any module's bodies:
  `LifeRig rig(ModelPart root, String temperament)` once per model; `Life life(Object held, LifeRig rig, int seed)` once
  per creature (keep it on the entity's client data); `tick(Life, LivingEntityRenderState, boolean steerHead)` once a frame
  in `extractRenderState`; `apply(LifeRig, Life, LivingEntityRenderState)` at the end of `setupAnim` (safe to run once per
  layer); optional `rootPose(Life, PoseStack, state)` in `setupRotations`; `eyesClosed(Life)` 0..1 for a texture blink.
  Per-frame inputs are public fields on `Life` (`hurt`, `walk`, `stride`, `busy`, `lookWeight`, `windup`, `roar`, `dying`).
- `dev.awaken.core.api.Strikes` (§24) — how a blow can be answered: `enum Kind {LIGHT, HEAVY, PERILOUS, GRAB, NONE}`
  (`deflectable()`, `blockable()`, `dodgeable()`); `deliver(Entity by, Kind, BooleanSupplier blow)` names the class of
  the blow it runs (a mob's swing, an ability's hit); `of(DamageSource)` reads it back (defaults: an untold melee or a
  projectile LIGHT, a blast with an entity PERILOUS, fire, falls, drowning, freezing and the void NONE). Tells:
  `installTells(TellSource)`, `tell(Entity)` → `Tell(ticks, kind)` or null (both sides). Stagger: `installStaggers`,
  `stagger(LivingEntity, ticks)` (server). The mobs module installs both for its creatures.
- `dev.awaken.core.api.BodyMaterial` (§24) — what a blade meets: `FLESH, ARMOR, BONE, WOOD, STONE, SPIRIT, SLIME`;
  `of(Entity)` (both sides; armour ≥ 10 over flesh or bone answers ARMOR), `install(Source)` (the mobs module answers for
  its bodies through `MobDef.material()`), vanilla defaults by type.
- `dev.awaken.core.api.CombatFeedback` (client, §25) — every blow's one feel: `hitstop(entityId, ms)` (the body freezes
  and trembles), `hold(ms)` (the striker's hands and view), `kick(yaw, pitch, roll, ticks)` (follows camera shake).
  Installed by Arms; no-op before that and on a dedicated server. Powers' hits use it. Also `engaged` (the local player
  is in a fight: the action camera's COMBAT pose) and `blow(entity, dirX, dirZ, strength)` / `blowOn(entity, out)` (a
  blade's blow's way and weight, read by the Life layer's stagger).
- `dev.awaken.core.api.WeaveHooks` (server, §25) — `costScale(player)` (a cast's energy share: 0.7 in a Weave's beat)
  and `cast(player, family, force, level)` (a landed cast); Arms installs the weaver, the powers module's Casting calls
  both.
- `dev.awaken.core.api.Strikes` additions (§25): `delivering()` (the class of the blow being delivered now: a bolt
  fired inside it carries it) and `installClassifier(source -> kind)` (the mobs module classes blows that land after
  their ability ran: bolts, fangs).
- `dev.awaken.powers.client.fx.FxClient` (client) — the effects engine for other modules' weapons (§25; also
  `weaponMote(kind, …, near)`, the light off a weapon, and `tellMark(mark, …)`, an enemy tell's marks; `FxPool.NEAR`
  draws a sprite close to the eye):
  `slash(wielder, offset, axis0, axis90, startDeg, sweepDeg, radius, band, sweepTicks, argb)` (a blade's crescent that
  moves with the wielder), `thrust(wielder, offset, dir, from, to, width, sweepTicks, argb)`, `bladeHit(at, dir, size,
  BodyMaterial)`. Same budget and settings as the powers' own effects.
- `dev.awaken.core.api.Stances` (client, §24) — a creature's stance as this client knows it: `share(Entity)` 0..1,
  `broken(Entity)`, `focused(Entity)` (the local fighter's focus). Installed by Arms' client; zero until then.
- `dev.awaken.arms.Arms` (§24) — `styleOf(ItemStack|Item)` (the fighting style, or null for tools and bare hands),
  `guards(ItemStack)`, `fights(LivingEntity)`, `posture(LivingEntity)` (0..1, server), `broken(LivingEntity)` (server),
  `tellSound(Strikes.Kind)` (the class's one voice; the mobs module plays it with each tell), `untold(DamageSource)`
  (a creature's own melee with no wind-up to read: not ours, not `#awaken:told_melee`; its guard window is wider, §25).

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
- The Atlas marks every landmark reached (filled) or heard of (outlined): told by Mira, whispered by a spirit or first
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
  materials to these tags from their own merge folders. E-tier is craftable from first-hour creature drops. From D up
  every piece also needs its tier's metal from the deep (`#awaken:tier_metal_<t>`, §28.3).
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
- Off while riding, sleeping, projecting, meditating, flying or gliding. No invincibility frames anywhere, except Arms'
  dodge (§24, Euan 2026-10-25: its 5 evade ticks only).
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
  way up and over; never onto a collision taller than its block (fences, walls, gates: those are crossings, §17.27); 6 ticks; 0.05 exhaustion; not while
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
  gate's music). In use since Carillon M4 B3b, with `awaken:city/night` after dark (§16.0 decision 30).

Plan v2 · world sound (Euan approved all seven decisions 2026-10-25; details in docs/sound/PLAN.md, page
docs/sound/plan.html):
- Approach C, revised by Euan the same day ("rebuilding from scratch something that already exists very well is not
  smart"): **AmbientSounds** (in the pack's `sound` profile) plays the world's nature in every biome; AWAKEN's own
  layer, the **Conductor** (`core/audio`, client in `core.audio.client.world`), plays only what is ours (hushes,
  hums, magic, places, reactions) and lowers AmbientSounds while one of our moments sounds (`Soundscape.natureLevel()`,
  applied by `compat.client.AmbientSoundsBridge`). No field recordings of our own. Sound Physics Remastered gives
  positional sounds their room; Tread Lightly plays footsteps once its spike passes on the Mac (else the Conductor
  re-voices player steps by surface and armour). Raise Sound Limit Simplified and Sounds (IMB11) stay out.
- Four layers: **air** (ONE streamed, non-positional stereo sound for the whole Conductor; a Java mixer on vanilla's
  `JOrbisAudioStream`, the decoder `StemMixer` shares, plays up to six bed layers, two families crossfading, each with
  up to three layers of co-prime length, through a one-pole low-pass that closes indoors, underwater, in the Veil, in
  falling snow and in low-lucidity dreams); **textures** (≤ 6 positional mono loops anchored to real blocks,
  re-anchored with a short crossfade when the enclosure changes and every ~12 s, since Sound Physics judges a loop when
  it starts); **events** (≤ 10 positional one-shots, ≤ 4 new a second, Poisson timing, call and answer, lulls);
  **reactions and re-voicing** (`PlaySoundEvent`: rain by the surface it lands on, thunder delayed by distance at 343
  blocks a second). Event names: positional ones never `<ns>:ambient.*` (Sound Physics skips those).
- Every overworld biome is in exactly one family tag `#awaken:soundscape/<family>` (16 families: greenwood, blossom,
  duskwood, conifer, open_grass, dry_warmth, desert, canopy, mire, heights, snowfield, coast, open_sea, river,
  sporewild, hollows), written by `tools/terrain/biomes.py`; a biome in none falls back by climate. Awakened biomes are
  their family plus a variant. The Nether and the End keep vanilla's loops. Data: `assets/<ns>/soundscape/<id>.json`
  (schemaVersion 1); any module adds its own place files.
- Budgets: 1 streaming channel; ≤ 16 ambient static voices including other modules' registered sources (quietest
  and farthest stolen first); client main thread ≤ 0.10 ms a tick average, ≤ 0.4 ms worst, no allocation per tick;
  decoded static world ambience ≤ 48 MB; ≤ 60 MB of new Ogg. Loudness: beds and textures −28 ± 3 dBFS RMS, ≤ −6 dBTP;
  events −26 ± 3; magic −32 ± 3.
- Fit: the Conductor plays on the Ambient/Environment slider and never touches the music channel; layered combat or
  Lord music: beds −4 dB, events halved; a stage cue: beds −3 dB; calm music: no ducking. Inside Carillon the city's
  soundscape owns the walls and the Conductor yields by the city's weight.
- Reactions (first numbers, tuned on Euan's ears): a hostile within 24 hushes birds and insects within 32 of it to 10%
  in 3 s, back over 20 s; insects within 6 of a moving player fall silent and resume 6-10 s after; sprinting, sliding
  or landing hard within 8 of leaves or tall grass flushes 3-5 birds and silences that tree 15 s; drips for 60-90 s
  after rain; a power cast within 16 flushes and hushes 10 s.
- AWAKEN's touches: the hush around open gates (48-96 blocks by rank) spreading as a Break nears; gusts played along
  `terrain.Wind`'s fronts; the Awakened lands' faint harmony in D major pentatonic (never the System's glass timbre);
  Mirror Marsh's one delayed answer; PER widens what you hear; dream clarity follows lucidity.
- Sources: AmbientSounds for nature; for our own sounds vanilla events first (referenced, never copied), then
  synthesis (`tools/art/audiokit.py`). No recordings (Euan: field recordings would sound out of place with Minecraft).
- The nature level (0..1, eased down in ~1 s, up over ~5 s): the hush where the listener stands (an open gate 48
  blocks at E to 108 at S, deeper and 1.4× wider while a break is near, deepest once broken), layered combat or Lord
  music (0.55), a stage cue (0.5), and AWAKEN's beds sounding near (rift, grove, the Well, ruins, a falling star:
  × (1 − 0.6 × their level)). AmbientSounds' master volume = the player's own × the level; the player's value is
  restored on shutdown. Without AmbientSounds the Conductor plays its own nature fallback (Greenwood today); with it,
  none.
- Quality gate: five listening batches of ten minutes or less (forest; every land; reactions; below and beyond; the
  magic); an unmarked entry counts as keep once Euan has opened the page.
- AWAKEN's own places and moments (built 2026-10-25; sounds synthesised, `tools/art/specs/audio_world.py`):
  **place soundscapes** `<ns>:place/<biome path>` play wherever the camera's biome has one, with or without
  AmbientSounds: the Stillpoint (cloud-sea wind, a temple tone, chimes, a far bell about every 40 s), the Halo (station
  hum, airlocks, far metal, data chirps, lulls of silence), Lucidium's dream (a shimmering chord, reversed swells),
  Elderwood (the colossi groaning with the wind, gold glints by day), Lumen Forest (a low hum, specks chiming and
  answering at night). **The Veil** (while projecting): the overlay `awaken:state/veil` (a far choir on D and A, faint
  glints) and a muffle (1.1 kHz, nature 0.2). **Dreams**: clarity follows lucidity (the air's filter from 900 Hz at
  lucidity 0 to open at 80). **Living Wilds happenings** heard from afar (`living.client.PulseSound`, from the tells
  the server already sends): stampede rumble (260 blocks), murmuration wings (200), the Deep Tide's drone (320; a
  hush 0.3 over 200+), caravan bells (160), the Wild Hunt's horn (400) and its hounds baying (220; a hush 0.1 over 200),
  kites over a carrion circle (180), a migrating herd lowing (220), a beast duel's roars (260); level = (1 − d/range)²;
  a happening near lets nature step back (× (1 − 0.6 × 0.8 × its level)). Each air pair or trio repeats together no
  sooner than 30 minutes (census test). **The hush before a Lord**: a gate room's own one-shots (`gate.ambient.*`)
  are dropped while the layered music is at the Lord, half of them in combat. **Mirror Marsh answers** (a soundscape's
  `echo`: chance 0.35, at most one every 2.5 s): a sound you make within 4 blocks (players and blocks sources) comes
  back 0.6-1.2 s later at 0.35, pitch 0.97, from 14-22 blocks away toward the nearest water; an echo never echoes.
- AmbientSounds data in our jar (read only by AmbientSounds): `assets/ambientsounds/basic/dimensions/awaken_dimensions.json`
  mutes it in every `awaken:` dimension (gates, strata, the Veil's Stillpoint, dreams, the Halo: those are ours), and
  `blockgroups/{rock,grass_block,grass,ice}.json` add our Awakened ground and plants so its region detection hears
  our biomes (riven shale and faultstone as rock, mirepeat as ground, lumen moss and moonblooms as plants, hoarfrost
  and frostspar as ice). Awakened biomes inherit their archetype's tags, which AmbientSounds matches.

Plan v2 · overworld structures (details in docs/modules/structures.md):
- One visual language: three families (gates, Precursor ruins, lairs), each with its own light and palette.
- A surface gate is a vortex (ellipse radii by rank) drawn by a block entity renderer on an invisible anchor core over a
  pad and crater ring; placement checks the rank's footprint. Looks: forming, open, about to break, broken, calm,
  spent; Red Gates red-rimmed. The hum is two loops with a 48-block falloff, pitch by rank. Old gates and Wells are
  rebuilt in place when loaded.
- Ruin sizes: vault 23×24×23, monolith 17×46×17, ocean lab 31×22×31, sky platform 49×26×49; pieces carry a blueprint
  version. The Well: shaft radius 7, twelve pillars at radius 18, four lantern pylons.
- The Atlas shows lairs you've heard of or found (synced from `awaken:fauna_state`) beside gates.
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
- Panels: whole framebuffer pixels per unit (≈45% of the width, ≤80% of the height; the 288×228 K panel, §30, is 5
  px/unit at 2880×1800 and 3024×1964, 6 at 3840×2160), placed from the real projection every frame (FOV compensated, vanilla bob undone), 0.62 m ahead at
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
  The test world is superflat (grass at y -61) and tests stand at y 5, so any rock, water or roof a test meets there was
  left by another test. Every test is padded by 24 (`AwakenTests.PADDING`): plots sit 54 blocks apart instead of 6, and
  vanilla clears the padded box (blocks and creatures) as it places the test. A test that starts while no other AWAKEN
  test runs (every batch's first, at least: a batch's tests start one by one as their chunks come in) removes every player
  and every non-player entity left in the overworld and puts back the first test's game rules, time of day and clear sky
  (the runner keeps neither; before 2026-10-26 some 240 finished tests' players stood about the grid and the world's
  directors acted round them). On the GameTest server the world's own directors stay quiet and the tests drive them: no
  anomaly, gate, happening, ledger body, camp or scene comes to a test's players by itself. A test that builds above its
  padded box, or reads a column's top, clears its own site to the sky first (`AwakenTests.site`, `clearToSky`); state a
  test switches on goes back in `AwakenTests.onEnd`, which runs whether it passes or fails. A creature never despawns
  there because its nearest player is another test's, over 128 blocks off (vanilla discards it at once).
- `-Ptests=<name prefixes>` (comma-separated) registers only those GameTests; a filter that matches none fails.

Tours (autopilot screenshot runs, `dev.awaken.core.client.dev.Autopilot`, `tools/autopilot.sh`):
- Look tours run on GitHub's macOS runners (`.github/workflows/tours.yml`, dispatched by `tools/tour-ci.sh`, decided
  2026-10-26 when the Mac's one-at-a-time queue ran for hours). Perf and frame-time tours, Far Sight's tours and sound
  listening stay on the Mac under `slot.sh tour`.
- The runner is macos-15 on arm64 (3 cores of a virtual M1, 7 GB): Vulkan through MoltenVK 1.4.2 on Apple's
  paravirtual GPU, client heap 4 GB. macOS 14's paravirtual GPU aborts MoltenVK (`newArgumentEncoderWithLayout:`) or
  runs out of device memory; macOS 26 renders at half the frame rate and scrambles more. OpenGL there is Apple's
  software renderer (0.1 fps), and Lens Cinematic fails on OpenGL anyway.
- The autopilot run takes the graphics API as a launch argument (`--graphicsBackend`, tools/autopilot.sh `BACKEND`,
  Vulkan unless `default`): a fresh game folder read its new options.txt's Vulkan as "default", which tries OpenGL first
  (on the Mac, the first tour after a pack install).
- On the paravirtual GPU, draws that each bind another slice of one uniform buffer come out wrong: Far Sight's far mesh
  (slabs across the sky) and vanilla terrain without multi-draw-indirect (sections misplaced; it is on by default, and
  autopilot `mdi on|off` switches it). Chunks (vanilla and Sodium), entities, the HUD and holo screens, and Lens Swift
  and Cinematic render as on the Mac. So CI tours run with Far Sight off (`FARSIGHT=off`, the client config's
  farSight) and refuse a script with `farsight on`.
- Display: the runner boots a 1x 1024x768 screen. `tools/ci-retina.m` (CoreGraphics' private CGVirtualDisplay) adds a
  display like the owner's MacBook, 1512x982 points at 2x, as the main one, with the Dock hidden: the same pixels and
  GUI scale as the Mac, so a script's GUI clicks land as there. If macOS refuses it, the runner's display goes to
  1920x1080 (1x: the Mac's framing at half its pixels) and the summary says so.
- Shots settle (`SETTLE=<s>`, `-Dawaken.autopilot.settle`; CI 90 s): before each `shot` (not `shotnow`) the autopilot
  waits until every chunk within the render distance less one is on the client (or none has come for 10 s), then a
  second of built sections (`LevelRenderer.hasRenderedAllSections`, which Sodium answers too), past the fade-in. Waits
  written on the Mac run out on 3 cores. Moments on the game's own clock still differ: the opening's sky shots, toasts,
  a creature's tracks, anything after a fixed wait that isn't a shot.
- Every tour picks a starter power (`cmd awaken powers starter 1`): on a slower machine the first-power screen can open
  after the script's last `screen close` and cover every shot.
- The run's artifact (7 days) holds the shots, the client's log, options.txt, awaken-client.toml, summary.txt and
  tour.json (`tools/tour-report.py`: the verdict, renderer, display, settles, warnings, the first ERROR lines), and the
  clip recorder's frames as a second artifact. Never Mojang's assets: they are downloaded on the runner and cached.

---------------------------------------------------------------------------------------------------------------------

## 14. The Awakened Pack (companion mods; plan in docs/plan-pack.html, approved 2026-10-24)

- Awaken stays mixin-free and plays the same with no companion mod installed. Integrations live in `dev.awaken.compat`;
  each class is loaded only when its mod is present (`ModList` check), compiled against the mod as `compileOnly` from
  Modrinth's Maven, never bundled or required.
- The pack is `pack/mods.json` (profiles: perf, horizon, feel, sound, comfort, together, ultra, tools), pinned by
  `tools/pack.py lock` into `pack/lock.json` (exact files and SHA-512, required dependencies, a dependency's own
  version pin). Distant Horizons (the horizon profile) is off by default since 2026-10-25: `tools/play.sh --pack` leaves
  it out unless `--horizon` is given (its far generation in a new world was the lead CPU and garbage cost behind
  Carillon's hitches, docs/perf/CITY-FPS.md); our own far-terrain renderer is the plan. The play client (`runClient`, 8 GB) runs
  generational ZGC since the same day: in the city tour G1 paused 5.9 s in total (young pauses to 180 ms, a 417 ms
  full GC at join) and ZGC 2 ms (max 0.7 ms); `-Pg1` goes back to G1. `tools/pack.py install <game-dir> <profiles>` puts exactly those files into a game folder, removes only
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
  It steps aside while an Iris shaderpack is on. Client config `lens` = OFF | SWIFT (default) | CINEMATIC | ULTRA;
  Cinematic and Ultra are Lens 2 since 2026-10-25 (§22: sun shadows from a terrain heightfield, contact shadows, AO,
  warm/cool light, a filmic curve; chain `awaken:lens_ultra`).
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
  (Vulkan, 2880×1658): p50 +0.16 ms on Swift, +0.08 ms on Cinematic. Texels 17-18 are Lens 2's light (§22); 19-31 are free. A story scene plays its look like a cutscene even while Lens is off: the chain runs for
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
- Sound (world sound, 2026-10-25): the `sound` profile is Sound Physics Remastered (≥ 1.5.2) and **AmbientSounds**
  6.3.11 with CreativeCore (compiled against as `compileOnly` for `compat.client.AmbientSoundsBridge`); Tread Lightly
  (and Immersive Thunder Reforged) join once their spike passes on the Mac (profile `soundspike` until then); Raise
  Sound Limit Simplified and Sounds stay out (docs/sound/PLAN.md §4).
- Comfort (stage 5): the `comfort` profile is JEI, Jade, AppleSkin, Mouse Tweaks, Controlling and Dynamic FPS. Xaero's
  World Map left it when Carillon's own Atlas shipped (M5, §16.8; M opens the table); no minimap, ever (direction
  comes from the world). `compat.WorldMaps` stays for a player who adds Xaero's themselves, and is silent without it. AppleSkin, Mouse Tweaks and Controlling need nothing from Awaken (the comfort tour checks they load with no
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
  `compat map` reports it. Test: `compat_world_map_rule`. Kept for a player who adds Xaero's (it left the pack at M5).
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
  for the rest. Opened with O (`key.awaken.look`; U went to Sovereign on 2026-10-25, Awaken's key category), from Awaken's settings (Mods → AWAKEN →
  Config → the client section's "Look" row, under Lens) and by autopilot (`screen look`, `lookswitch <tier>`). The
  default stays SWIFT (client config `lens`). The current tier is read back from the settings (Lens, Iris's shaders
  switch), not stored twice. Drawn like the System's cards (`HudLook`: projected glass that scans in while the holo
  look is on, flat `Glass` otherwise); not a reading screen, so the world behind stays undimmed and a choice shows at
  once.

  | Owned key | SWIFT | CINEMATIC | ULTRA |
  |---|---|---|---|
  | Lens `lens` (awaken-client.toml) | SWIFT | CINEMATIC | ULTRA (Lens 2, §22; was CINEMATIC behind Iris) |
  | DH `lodChunkRenderDistanceRadius` | 128 | 192 | 192 |
  | DH `maxHorizontalResolution` | TWO_BLOCKS | BLOCK | BLOCK |
  | DH `verticalQuality`, `horizontalQuality` | LOW, LOW | MEDIUM, MEDIUM | MEDIUM, MEDIUM |
  | DH `enableSsao`, `enableTexturedLods` | false | true | true |
  | DH `enableAntiAliasing` (file only) | false | true | true |
  | Iris shaders switch | off | off | off (was on; Lens 2's Ultra replaced Iris, §22) |
  | Sodium, vanilla options | none | none | none |

  DH values are pack/dh/swift.json and cinematic.json (a GameTest compares them); keys the tiers agree on (threads,
  cloud layers) aren't owned. DH: the six keys its 3.3.4 config API offers are set at once
  (`IDhApiConfigValue.setValue(value, "AWAKEN Look")`, an API value DH never saves; while it's set, DH's own menu shows
  those six as "API LOCK", for the rest of that session), and all seven are written into config/DistantHorizons.toml
  (matched by table and key, every other byte kept; written again on `GameShuttingDownEvent`, since DH can save its
  in-memory settings over the file meanwhile), so after a restart they're plain DH settings and no API value is set.
  Anti-aliasing applies when DH next reads its file (its menu or a restart). Sodium: no supported route (its config API
  only adds option pages; its options file is in use while the game runs), so it keeps its own settings and the
  screen says so. Iris: `IrisApiConfig.setShadersEnabledAndApply`; the shaderpack stays the player's, and every tier
  turns Iris's shaders off (Lens draws instead). Since Lens 2 (§22) Ultra is Lens's own and offered on every backend;
  until then it meant Iris on OpenGL, and `Look.ultra` (Iris's availability and its reason, at most 180 characters:
  Iris needs OpenGL; Iris 1.11.7 pins Sodium 0.9.2, which crashes on NeoForge 26.3.0.95) stays for when a working Iris
  returns as a fourth choice. The IRIS row says whether a shaderpack draws (Lens steps aside). The frame-time line: the last 60 s of
  frame-to-frame times (`RenderFrameEvent.Post` into a 32768-int ring, no allocation), p50 and p99 from a 0.1 ms
  histogram twice a second while the screen is open, green while they fit the tier's budget (Swift p50 ≤ 9.1 ms and
  p99 ≤ 16 ms; Cinematic ≤ 11.1 and ≤ 20 ms; Ultra ≤ 16.7 and ≤ 25 ms, §22), and "HORIZON n CHUNKS" with DH ("off in this
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
- Soundscape (world sound): the biome table's `soundscape` column puts every biome in one family tag
  `awaken:soundscape/<family>`; the Conductor plays the Awakened biomes' beds, so their `minecraft:audio/ambient_sounds`
  keeps only `mood`.
- Leaf rustle (played by the world sound Conductor since 2026-10-25, soundscape `awaken:world`): `amb.lands_rustle` under canopy and `amb.lands_wind_high` high and in the
  open, volumes from the wind and a 16-column canopy ring; client setting `leaf_rustle` (default on).
- Waving leaves (spike done 2026-10-24, docs/spikes/waving-leaves.md: safe, cost within noise, at most ~0.05 ms p50 in
  a dense dark forest): `dev.awaken.terrain.client`. A built-in client pack (`resourcepacks/awaken_waving_leaves`)
  replaces Sodium's terrain vertex shader, offered only when Sodium is exactly 0.9.3-beta.1+mc26.3 with terrain shader
  sources of a known SHA-256, Iris is absent and client setting `waving_leaves` (`awaken-terrain-client.toml`, default
  on) is on; otherwise nothing is installed (vanilla renderer: no waving). Marker: every `LeavesBlock` model is wrapped
  after baking with vertex colour alpha 254 (textures untouched). Wind: texels at the block atlas's bottom-right
  corner written each frame (signature, the sway clock mod 2048 s, camera, wind, the gust profile along the wind from
  `terrain.Wind`; since swaying plants also their amplitudes, the player's feet and whether the world has weather
  wind): 45 texels, laid out by `terrain.SwayMath` (see "Smooth" below).
  Amplitude 0.11 blocks at full wind and gust. `/awaken terrain waving on|off|status` (client).
- Swaying plants (2026-10-26, Euan: "all things sway with the same wind the trees get"): the same pack, gate, texels
  and setting as the leaves (`waving_leaves` now covers both and keeps its key, so older settings still apply; nothing
  moves when the gate says no). Everything rooted that a breeze would move bends in the same wind and gust fronts as
  the canopy, so a front rolls across a meadow and on through the trees.
  - Marker (`terrain.PlantSway`, common; baked by `terrain.client.WavingModel` after model baking, parts cached per rule
    and variant, nothing allocated per block): vertex colour alpha `253 − code`, `code = mode × 8 + level − 1` (the
    vertex moves level/4 of its mode's amplitude, levels 1–8), 40 for a pad on water; vertices that must not move
    (a plant's base, a hanging plant's anchor) keep 255, leaves keep 254. Modes: rooted (tips dip as they lean),
    hanging (tips rise as they swing), water, and wall vines held in the wall's plane (normal x or z, from the quad's
    face; a vine's ceiling face stays put).
  - Weight from where a vertex is in its plant, never from the quad, so vertices two parts share agree: rooted
    `2 (h/2)^1.4` for height h over the root (short grass tip 0.75, tall grass middle 0.75 and top 2: the top half
    sways more and stays joined), hanging h below the anchor; times the rule's gain (sugar cane 0.45, bamboo 0.5,
    vines 0.5, azalea 0.5, hanging strands 0.9, twisting vines 0.8, flower beds 3). Stalks and strands count the same
    run up to 2 blocks below and above while meshing (Sodium's snapshot reaches 2 blocks past its section; further
    would disagree across section borders): kelp, sugar cane and twisting vines saturate 2 blocks over the root, weeping
    and cave vines, hanging moss and vines 2 blocks under the anchor, and only bamboo's top 2 blocks bend.
  - Which blocks (by class: tags are not bound when models bake): short and tall grass, ferns, every flower and tall
    flower, flower beds, dead and dry bushes, bushes, firefly bushes, saplings and propagules (hanging ones hang),
    azaleas, crops and stems, nether wart, roots and sprouts, sweet berries, small dripleaf, pitcher plants, cactus
    flowers, sugar cane, bamboo, vines, cave, weeping and twisting vines, hanging moss, hanging roots, spore blossoms,
    seagrass, tall seagrass, kelp, lily pads; Awaken's saplings, lumen vines, weepwood tendrils and moonblooms (they
    extend those classes). Waterlogged plants sway in the current. Still: mushrooms, fungi, sea pickles, leaf litter,
    corals, cactus, big dripleaf, potted plants, crystal clusters, lumen caps.
  - Motion (the shader): the offset is a function of world position, weight and time only (no cracks, the base never
    slides). Air: a lean downwind `s (0.35 + 0.65 s)` plus a swing `(0.3 + 0.5 s)` of 0.72 × 0.75 Hz + 0.28 × 1.3 Hz
    and a cross-sway at 0.57 Hz (strands 0.45, 0.78 and 0.34 Hz, rippling 0.7 rad a block down their length), × 0.2
    blocks × weight, s being `Wind.strength` with the gust from the profile the leaves read; phases are fixed in the
    world (about a radian from one clump to the next, warped, never tied to the turning wind); tips dip or rise by
    `d²/2w`. Below y 50, fading out by y 30, and in worlds without sky light the wind gives way to a faint draught
    (s 0.12, no gusts). Grass tips brighten by up to 10% while a gust bends them, a sheen that shows the front rolling
    across a meadow. Within 1.3 blocks of the player's feet (not a spectator) rooted tips part away (up to 0.32
    blocks) and press down. Water: a 0.2 Hz surge (and a 0.35 Hz roll) along an axis that turns slowly across the
    seabed, a wave climbing kelp, 0.16 blocks × weight; never the air wind. Pads bob 0.006–0.016 blocks, rocking a
    little. All amplitudes are a texel (`/awaken terrain waving plants <air> <water> <sheen> <push>` tunes them live).
  - Cost: plants sway out to 112 blocks from the camera, fading from 72 (cross models have no faces to cull, so every
    plant quad is drawn and far ones are most of a meadow's vertices; with no fade a sparse meadow cost +0.3 ms p50).
    A plant vertex past 112 reads no texel; one inside reads nine (four more within 10 blocks of the camera, for the
    feet); with both plant amplitudes 0 it stops after five. Texels 22 (air and water amplitude, sheen, weather wind
    here), 23–25 (the player's feet), 26 (the push). Measured 2026-10-26 (docs/spikes/plant-sway.md; Vulkan, 2880×1800,
    render distance 16, machine at load 12–18): in a studio meadow with every plant inside the sway radius, the sway
    within noise against the shader finding no wind (+0.05, −0.03 ms) and the whole override +0.13 and +0.19 ms against
    Sodium's own shader; in a flower forest +0.30 and +0.16 ms against no wind, leaves' sway included (it has no
    distance fade). `/awaken terrain waving debug 3` stills and skips the plants alone, to split the two.
  - Smooth (2026-10-26, Euan: "a bit jittery... as if they restarted the animation"). Three causes, all fixed for leaves
    and plants alike:
    - The clock: the client adds a tick per client tick and the server overwrites it every 20 server ticks, so a
      lagging integrated server pulled the client's game time back every second or two, and the sway rewound. Now
      `ClientWind` runs the wind (gust fronts, heading, steady strength; also the falling leaves and the rustle) on a
      `terrain.SwayClock`: real frame time eased toward the game's clock, its pace changing by at most 35% for a small
      error (up to 90% for one of seconds, so it follows a slow server), never backwards, snapping only on a jump of
      over 4 s, still while paused. The to-and-fro itself runs on a separate clock of unpaused real time alone (whole
      turns in 2048 s, so its wrap is seamless).
    - The wrap: world positions are rebuilt modulo 4096 and the camera is written modulo 4096, so it wraps at x = 0 and
      z = 0, right by spawn, and every spatial phase used to jump there. Spatial phases are now whole turns in 4096
      blocks (coefficients rounded to multiples of 2π/4096, under 0.1% from before), and the leaf flutter's hash takes x
      and z modulo 65536 sixteenths.
    - Precision: every sine takes a phase in turns reduced with `fract` first (a GPU's sine is imprecise far from
      zero); direction, steady strength and the 64 gust samples are 16-bit (they were 8-bit: tall grass ticked in
      small steps as a gust or a turn moved them); the gust profile sits on a grid fixed in the world (multiples of 4
      blocks along the wind; texel 7 holds its offset), so walking no longer slides it.
    - `terrain.SwayMath` is a line-by-line Java mirror of the shader's sway and texel decoding. The GameTest
      `terrain_sway_continuous` holds the clock against a server stuck at 15 ticks a second (it never rewinds or
      lurches, stays within a second), the sway equal at t = 0 and t = 2048 and across 4096-block shifts, the gust at a
      place still while the camera walks, and 16-bit round trips. `/awaken terrain waving probe frames <n> | seconds
      <s> | turn | stop` logs, frame by frame, the offsets the shader gives a few fixed vertices (and the same vertices on
      the raw game clock) to `sway-probe-<mode>.csv` (`frames` also grabs each frame at 60 fps);
      `docs/spikes/plant-sway/plot_probe.py` charts them; `tools/autopilot/plant-sway-smooth.txt` films the proof.
  - Commands: `/awaken terrain waving on|off|status|debug <0..3>|plants ...|probe ...` covers leaves and plants;
    `/awaken terrain wind front across|ahead` (showcase) launches one front across the view or from behind the camera,
    holding the heading the way it rolls, until `wind real`. Tests: `terrain_sway_markers` (every plant kind marked,
    leaves keep theirs, nothing else is), `terrain_sway_joined` (shared vertices agree for every run length; roots and
    anchors still).
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
- **Awakened Alpine** (Far Sight 2, 2026-10-25; package `terrain.alpine`, its own data pack and feature flag
  `awaken:awakened_alpine`, so new worlds only): real mountain form.
  - `awaken:eroded` wraps the terrain's 2D offset (`minecraft:overworld/offset`, and `tectonic:terrain_spline/offset/final`
    when Tectonic is loaded, through Lithostitched): ground stands at `128 · (1 + offset)`, so a change of `dh` blocks is
    `dh / 128` of offset and the chunks, the preliminary surface and Far Sight all follow. `Erosion`: 5 octaves of stripes
    running downhill (cells 256 to 16 blocks, height 40 shrinking by 0.38 an octave), blended over jittered cells (3×3,
    kernel 1.25 cells), V gullies (low half pulled 0.6 harder, 0.28 carve), each octave following 0.4 of the coarser
    ones' slope (branching), faded in on slopes 0.04-0.45 and on high ground (y 74-140). The slope it follows is the
    offset's own on a 64-block lattice (central differences, blended), cached per thread. Pure float maths with its own
    cosine and hash, mirrored in `farsight_erosion.glsl`. A range's body eases from y 190 toward 256 instead of
    flattening (2026-10-26: from 220 toward 286 it filled the room above and left table tops), so the peaks above it
    are Tectonic's ridgelines.
  - With Tectonic, the pack lifts Tectonic's offset clamp (`tectonic:__constants/max_offset` 3.0; its own cut every high
    range flat near y 303) and `awaken:ridge_room` holds Tectonic's ridge strength (`jaggedness/continents`) to the room
    under y 316 (the ridge term reaches about 1.6): no summit reaches the ceiling. On high ground the ridges stand up to
    1.6 times stronger (y 150-220, `RidgeRoomFunction.EMPHASIS`, 2026-10-26), still within that room, so a range's crest
    rises as ridgelines and horns. World height stays -64..320: raising it
    per world needs Tectonic's noise settings copied or a mixin (decision 3, PLAN-2).
  - `awaken:alpine` material condition (`AlpineSurface`, shared with Far Sight): snow above a snow line at y 186 that
    wanders ±9 (160-block noise), drops 22 on north faces and lifts 14 on south ones, on slopes under 1.25 (51°); scree
    from 1.45, bare rock from 1.8 (vanilla's two-block surface gradient, halved). Prepended to `minecraft:overworld/biome_surface`
    for `#awaken:alpine` (the peaks, slopes, grove, windswept biomes, Frost Spires, Shattered Highlands), and above y 150
    for `#awaken:upland` (grass and forest biomes the terrain lifts into the mountains: meadow, cherry grove, the
    taigas, forests, plains, Elderwood, Lumen Forest; 2026-10-26, so high grassland wears snow and rock like the peaks
    beside it, lowlands untouched). Far Sight flags those columns alpine the same way (`FarColumns.UPLAND_FROM`).
  - Mountain forests: spruce (`minecraft:trees_taiga`) 9-11 a chunk on windswept hills and forests, 1-3 on meadows
    (per-world biome modifiers), kept below the treeline (14 under the snow line) and off slopes over 1.1 by the
    `awaken:treeline` placement filter, `AlpineSurface.treeAllowed`, which Far Sight's far trees use too; the far forests
    add the matching density (`AlpineSurface.mountainForest`: windswept hills 190, windswept forest 60, meadow 60) and
    wear spruce's fixed evergreen tint (`FoliageColor.FOLIAGE_EVERGREEN`), as every far conifer does.
  - The snow edge breaks into drifts and bare patches over about 20 blocks (`AlpineSurface.patches`, value noise on 24-
    and 7-block lattices with farsight.glsl's hash), in the chunks and far alike.
  - `tools/sim/ErosionStudio.java` shows the erosion offline. Tests: `terrain_alpine_erosion` (gullies run downhill,
    bounded, smooth, deterministic; summits and ridges under the ceiling), `terrain_alpine_snow_and_rock`.


---------------------------------------------------------------------------------------------------------------------

## 20. Far Sight (Awaken's own far terrain; Far Sight 2 plan docs/perf/far-sight/PLAN-2.md, page https://claude.ai/artifact/1USNxEEEGNHTL1NAzfnKWN, approved in full 2026-10-25)

Far Sight 3 (plan docs/perf/far-sight/PLAN-3.md, page https://claude.ai/artifact/BYgaSCyxJsXY78CXNUzgaX, approved in full
2026-10-26): every far shape within about a pixel of the real thing, detail fading with distance on the air's own
curve, and Carillon drawn whole from its real blocks first (City Sight, below).

Far Sight 2 (2026-10-25) replaced stage 0's stepped columns: real far relief to 16 km, far forests, alpine materials
and air at real scale, at about 2 ms a frame (Lens Ultra, 2880×1800, `tools/autopilot/farsight2-perf.txt`). Its
terrain half is Awakened Alpine (§15). The bar is Euan's five reference shots (/Volumes/MEDIA/farsight-ref).
- **Setting** (client `farSight`: FULL 16 km, NEAR 4 km, OFF; default FULL). Singleplayer only (it predicts from the
  integrated server's generator; a guest on someone's server draws nothing until server tiles, decision 6), never
  while Distant Horizons is installed, only in dimensions with the overworld sky that aren't Instanced (`compat.FarView`).
  Autopilot `farsight on|off|setting|status|debug <n>|part <detail|trees|mist|bake> on|off`.
- **Rings**: geometry clipmaps, 7 square windows of 256×256 cells centred on the camera, ring k's cells `2 << k` blocks
  (2 to 128; ring 6 reaches about 16 km). Windows snap to patches of 16×16 cells and move once the camera is three
  quarters of a patch past their centre. A texel is a ring **corner** `(i·cell, j·cell)`, so a coarse ring's corners are
  every other corner of the finer ring. Atlases of 256 × (256·7) texels, toroidal: height R16 (`(top + 1024) × 16`,
  0 unknown), colour RGBA8 (flags in alpha: 1 alpine, 2 cold, 4 badlands, 8 stony, 128 generated), info RGBA8 (water
  depth, canopy height × 4, tree density), crown RGBA8 (crown colour, shape), the same four for the generated layer, and
  shade RGBA8 (baked: normal x and z, valley shade, sunlight). A slot whose patch left is zeroed at once; a patch is
  asked for when it can first show (one under the real chunks waits), a failed job releases its slot.
- **Height model** (`FarColumns`, workers only): the terrain density whose zero is the ground (`tectonic:sloped_cheese`,
  else `minecraft:overworld/sloped_cheese`) sampled with the generator's caches on (its 2D splines once per column),
  from the last column's ground, stepping 8 blocks to bracket the crossing and placing the ground on the bracket's
  straight line (the real terrain interpolates its density over 8-block cells in y, so this is closer than halving:
  median 0.3 blocks from the generator's exact ground, p90 1.2, test `farsight_columns_predict`), plus half a block (the
  top face). 2.5 probes a column; 59 µs a column on the Awakened Tectonic terrain on the M4 (stage 0: 650-870 µs and
  8-block steps). Inside the city basin's blend (2026-10-26, Euan: a far wall east of Carillon "that isn't really
  there", flat when he got close) the ground comes from the router's own final density, which the basin wraps
  (`CityBasinFunction` round `final_density`; the lands pack's Lithostitched wrap swaps it into the generator's settings,
  a router without it gets the same wrap): down from above the natural ground (or the street + 11) in 8-block steps to
  the first solid, then the bracket's line. The basin blends densities, and inside hills the final density is held
  small by the cave noise, so the real street runs flat out to about 560 blocks from the site and ends in a cliff
  (sometimes undercut); blending heights (the old `Shape.ground`, still used for a generator without a terrain density)
  drew a 230-block rise there. Test `farsight_columns_basin` (vanilla's overworld, seed 777, a hilly site, 910 columns
  through the blend against the wrapped final density block by block): median 0.21 blocks, p90 0.46, none over 3 too
  high (blending heights: median 2.1, p90 22.6); 86 µs and 7 probes a column, only in the ring 330–700 blocks from the
  site. Water fills to sea level. Biome from the biome
  source, sampled per patch every second corner and at least every 8 blocks, each corner taking the nearest sample
  (2026-10-26, a JFR profile of the pack's world: one lookup a corner, through Lithostitched's injector over Tectonic's
  climate, was 86% of the workers' time, 178 µs a column); colour, forest (density, canopy height, shape, crown
  colour) and flags from `FarPalette.look`; `ALPINE` where the world has Awakened Alpine and the biome is
  `#awaken:alpine`. Generator without a terrain density: its base height.
  Workers: a quarter of the cores, at most three (two on an M4: macOS ignores thread priorities), paced by frame time;
  while more than 64 patches are missing (a new world, a jump, the opening's card, fast flight) half the cores, at most
  six, unpaced. The horizon ring first, then by distance; a ring asks only for patches it draws (not those the next
  finer ring covers clear of its 16-cell easing band: asked for, they came first and the visible bands waited, so a
  minute after a jump rings 4 and 5 still lacked 66 and 209 of 256 patches and the coast read as see-through sea).
- **City Sight** (Far Sight 3, `farsight.voxel`, `client.VoxelLayer`/`VoxelLooks`): the city's real chunks drawn by Far
  Sight wherever Sodium doesn't draw them, at any distance. `VoxelCapture` snapshots each city chunk (`City.layout().chunks`,
  nearest the player first) on the server tick within its own 1.5 ms (`VoxelSnapshot`: copies of its sections from one
  below the lowest non-air to one above the highest, with their sky and block light; a chunk the city's hold has let go
  is read back from disk, one a tick), at most 96 waiting, and again after a save that changed it. Far Sight's workers
  mesh them (`VoxelMesher`, after the horizon ring's patches, before the nearer far land): `VoxelKinds` reduces every
  block to a cube (opaque cubes hide their neighbours' faces; glass, stairs and the like don't; leaves do, as fast
  leaves), its box when at least 0.4 of a block (slabs, beds), a flat top (carpets, snow layers), water's surface at
  14/16 and its open sides, or nothing (fences, panes, bars, plants, torches, doors: under a pixel there). Exposed cube
  faces merge into the largest rectangles of one look, tint and light (greedy meshing). Each face carries the sky and
  block light of the air in front of it. Levels 1-3 use cells of 2, 4 and 8 blocks: a cell is solid where at least half
  its blocks are (thin things fade, never swell), it wears its blocks' most common look, and on top the most common look
  of its columns' highest blocks. A quad is 16 bytes (least corner in sixteenths, face, extents, level, look, chunk
  slot, tint, light), pulled by index from 4 MB texel-buffer pages. Looks (`VoxelLooks`): each face's sprite from the
  game's own baked model (the quad facing that way, else the particle sprite), its tint (`BlockColors` with the
  chunk's biome; water by its biome's water colour), cut-out where the model isn't solid, glow where it emits light,
  and its texture's average colour (leaves' holes take it). Drawn after the far terrain into Far Sight's targets
  (`farsight_voxels`): the block atlas with its mipmaps (a texture fades to its colour as the real chunks' do), the
  game's lightmap at the face's own light (dark under roofs, lit windows by night), `farLight` and the same air.
  A level gives way to the next once its cells span 1.5 pixels or fewer, cross-fading over 15% of its reach
  (dithered). Hidden where Sodium draws the section (`farRealSection` and the chunk mask); chunks Sodium draws whole are
  not planned. The far terrain and far trees step aside where City Sight stands (a 256×256-chunk toroidal coverage mask,
  read within 2 km) and under it carry only the city's ground, a block under its streets (`FarCity.underCitySight`,
  `CityLayout.groundY`). The opening's card waits for the whole city to be meshed and up. Debug view 3 tints City
  Sight green; `farsight part city off` hides it. Test `farsight_voxels_mesh`.
  Also (2026-10-26, Euan's tours): every overworld chunk the server loads is captured too, after the city's (kept
  within 1.9 km, the city's at any distance, at most 16 M quads, farthest first; the coverage mask redrawn each second
  for chunks within 2 km, so its 4 km torus never stands a far chunk in for a near one). A snapshot starts just under
  the chunk's lowest ground (MOTION_BLOCKING_NO_LEAVES - 8; OCEAN_FLOOR counts leaves, and under a closed canopy the
  cut fell at the leaves: trees floating over a hole): deeper never shows. A chunk is taken again 2 s after its last block
  change (the neighbour-update event), not on save (the city's hold saves every chunk as it lets go: the city
  "loaded twice"), and a worker skips a snapshot that hashes as the mesh already up. Faces into dark rooms (no sky
  light, block light ≤ 2) aren't drawn; panes, doors and bars between such a room and the light draw their outward
  face as a window (dark glass), and where a dark room opens to the light (an undercut, a cave's mouth) its air gets an
  unlit stone curtain facing the light (past the chunk's side counts as light, so no slit at chunk borders): a hole
  through to the sky otherwise (2026-10-26, under the ledge at the basin's rim). Light steps by three for merging. Each chunk's quads are grouped by face; faces turned
  away across the whole chunk aren't drawn. Chunks clear of the render distance's edge and of a level's fade draw with
  a pipeline that never discards (cut-out holes take the room's darkness, leaves' their colour), so Apple GPUs'
  hidden-surface removal works. Measured at the Roost: about 1.1 M quads, 1.6 to 4.7 ms on a loaded M4.
- **Hand-off with the real chunks**: while Far Sight draws, the game's chunk fade-in is held at none (new chunks
  faded in from the fog colour, a white veil sweeping over the city; the far view already shows them), and the
  player's own value comes back when Far Sight stops. A loaded chunk's 2 s settling clock starts only once it is
  inside the camera's render distance (after the opening's flight the Roost's long-loaded, never-built chunks counted
  as drawn at the landing: the city vanished and reloaded), and a chunk counts as drawn 250 ms after it first does
  (Sodium draws a section a frame or two after it reports it compiled: a flash at the loading edge).
  City Sight hides per section: a 64×64-chunk × 32-section mask from the game's own `isSectionCompiledAndVisible`,
  which Sodium answers with its exact built state (so the approved Turbo hook into Sodium isn't needed); all-air
  sections count as built unasked, half the chunks are refreshed a call, a section counts 250 ms after it first is. A
  section Sodium hasn't built keeps City Sight's copy of the same blocks. The plan skips a chunk only when Sodium has
  built every section from the chunk's lowest ground (without leaves) to its top: not the sealed sections under the
  ground, which Sodium never builds (counting those drew most chunks, 2 ms more at the Roost), and not by the settling
  timer (on a busy machine Sodium builds late, and land it hadn't built yet stood empty). Same cost at the Roost as the
  timer (876 chunks, 0.75 M quads).
  City Sight's meshes stay across Far Sight off and on in the same world (the capture starts over; unchanged chunks
  hash the same and are skipped).
- **City Sight on disk** (`VoxelDisk`, schema 1): every snapshot taken is kept, deflated (sections in the game's own
  network form, light), in regions of 32×32 chunks at `<world>/awaken/farsight/voxels/overworld/r.<x>.<z>.bin`, written
  on its own thread at each level save and, waited for (at most 10 s), when the server stops. A start, and every 10 s
  after, reads back what lies within 1.7 km of the player and isn't tracked (the world's own copy wins), handed over
  as the client takes them.
- **Real land ahead** (`VoxelAhead`, Far Sight 3's shadow generator as built): where trees will stand can't be
  replayed without growing them (the game's placement and a tree's growth draw from one random stream in turn), so
  the exact way is the real chunks. While City Sight draws and the server's smoothed tick stays under 30 ms, every 10
  ticks it asks for the two best chunks (nearest, ahead before behind) in a band 3 chunks past the view distance and a
  35° cone 12 further along the player's heading, at most 6 waiting, held by Awaken's own ticket (`farsight_ahead`:
  loading only, never ticking, timing out 40 ticks after the chunk is ready to save). City Sight captures them on load,
  once the light engine has finished the chunk (`isLightCorrect`; else 2 s later): without its sky light every face
  reads as a dark room's and the chunk would stand as a hole.
- **Dissolves**: a chunk new to City Sight dissolves in over 1 s (its share in the chunk table) while the far terrain
  and far trees under it dissolve out in the complementary 4×4 dither (the coverage mask carries the same share), so
  wherever the far view's guess differed from the real land it changes gently, never pops.
- **Levels follow the air**: a City Sight level gives way once its cells span `PIXELS` / transmittance pixels (the air's
  haze at that distance, 4× in rain): hazier distance needs less detail.
- **Work ahead of the camera**: the far patches' queue ranks from where the camera will be in 1.5 s at its smoothed
  speed (at most 300 blocks ahead), so in fast flight the land ahead comes first.
- **The opening's card** waits, while City Sight draws, for the far rings and the whole city meshed and up, not for
  the real chunks round the player (the flight's camera is far from them and Sodium doesn't build them for it).
- **Seam audit** (`tools/farsight/seam_audit.py`): pairs of tour frames, normal and debug view 2 (far terrain and City
  Sight hidden for the real chunks paint red); red only in the debug frame, eroded by 5 pixels (outline slivers where
  City Sight's cubes are hidden for thinner real blocks are expected), is a hole. Tour `farsight3-city.txt` takes the
  pairs at the Roost, the quay and high over the walls; 0 hole pixels at all three (2026-10-26).
- **Generated layer** (stage 1, schema 2): `FarChunk` keeps per 2×2-block cell the ground's top (MOTION_BLOCKING_NO_LEAVES;
  water counts), water depth, the ground's colour, the canopy's height above it and its colour (leaves at
  MOTION_BLOCKING). `FarStore.patch` averages the cells within half a cell of each corner (known once a quarter is
  seen) into the same four texels, flagged generated; its trees get their biome's shape (looked up only where trees
  stand). It wins over the prediction.
- **Drawing** (`AfterSky`, before any chunk, its own D32 reversed-Z depth, planes 1 and 32768): each ring's window
  minus the next finer window is cut into pieces of up to 64×64 cells, culled on the CPU, each one draw of a continuous
  mesh: vertex `v` is the piece's corner `(v % 65, v / 65)`, one index buffer per piece width (8..64). A corner takes
  the generated layer, else the prediction, and over the window's outer 16 cells eases into the next coarser ring's
  surface (bilinear between its corners), exactly at the edge: no crack, no pop. A corner with no data takes the coarser
  rings' surface, else open sea at sea level (never a collapsed vertex). Far terrain is hidden exactly where the real
  chunks are drawn: where they are loaded and meshed (a 64×64 chunk mask: a chunk counts once its surface section and
  the three below are built, or 2 s after it loads, since Sodium never builds sections it can't see) and in a section
  Sodium draws, by its own rule (`farRealSection`, from Sodium 0.9.3's `OcclusionCuller`: the section's box grown by a
  block within the render distance horizontally, as a circle, and vertically). (2026-10-26, Euan: a smooth dark wedge
  on a loaded spire and a grey box behind a quay's tower: the mask was a sphere 48 blocks short of the render distance,
  so in that band far cells, wider than a spire, stood with the real chunks.) Seen from high up the real sea ends well inside the
  horizontal render distance, and its floor sooner, so far water tests its own floor's distance and stays under a real
  water surface whose floor is too far to draw. Far water is not hidden under the real chunks in the render distance's outer band (the last 80 blocks), where the
  mesh lies flat and the camera is above the sea (2026-10-26: a steep far triangle from a canal to a roof stood as a blue
  wall in the loaded city, and nearer far water showed as boxes on its canals; the real floor is always drawn nearer),
  unless the camera is
  under the sea (2026-10-26, Euan's play: sky-coloured slabs on the sea where Sodium skipped a section), and grazing far
  water reflects the sky by the horizon at every distance, as Lens's real water beside it does. The planner and the windows count as covered only the ground a
  sphere 48 blocks inside the render distance reaches at a sea floor (40 under sea level) below the camera, in 16-block steps. (2026-10-26: the pale squares
  over the sea after a jump and the pale frame along a flight were these holes, found with `farsight debug 2`, which
  paints masked far terrain red. They were first taken for water walls and for vanilla's chunk fade-in; a Turbo
  fluid-renderer mixin and a fade override were built for those, removed nothing, and were dropped.)
- **Water edges** (Turbo, `dev.awaken.turbo.wateredge`, 2026-10-26, orchestrator's approval): water sides facing a
  chunk the client hasn't received are meshed (it reads as air), so the loaded area's edge stands in water walls,
  thin pale lines over the far sea after a jump or in fast flight. The pack's chunks are meshed by Sodium, so the hook
  is in Sodium 0.9.3's `DefaultFluidRenderer.isSideExposedOffset` (a `@Pseudo` HEAD inject, own config
  `awaken.wateredge.mixins.json`, client only, not required): while Far Sight draws, a horizontal water side whose
  neighbouring chunk the client lacks is not exposed; the chunk's arrival marks its neighbours' sections dirty, so the
  face comes back where needed. Version lock Minecraft 26.3, NeoForge 26.3.0.95, Sodium 0.9.3-beta.1+mc26.3
  (`WaterEdge`); probe: the fluid renderer reports when built with the hook; client setting `farSightWaterEdges`
  (default on); off on the first failure; off, water meshes as Sodium's own. Measured (`farsight2-wateredge.txt`, the
  same jump off and on): the 10-second shot's pale edge pixels 0.96% off, 0.21% on. A first, vanilla `FluidRenderer`
  version reached nothing under Sodium and was dropped.
- **Look** (`farsight_terrain.fsh`; Euan's playtest, 2026-10-25: "it feels like an extension of the world"): far ground
  is drawn in blocks at every distance. Each pixel walks its view ray through columns standing on the mesh's local
  plane, at least a pixel and a half wide (1 block by the real chunks, then 2, 4 .. 64; between two widths each
  world cell of the wider picks one, anchored to the land: 2026-10-26, pixel-wide columns dithered on the screen made far
  slopes glitter in motion, Euan's play; part "steady" off restores them for measuring), tops rounded to
  whole blocks, and shows the face it meets: the top, or a side facing x or z, shaded as the game shades faces (1, 0.8,
  0.6) under a light matched to Lens 2's on the chunks (`farLight`: a warm key where the sun reaches, Lens's cool shade
  behind ridges, faces turned from the sun shaded only as it lowers). Grass tops stand over dirt risers (the dirt
  texture's average, a grass fringe along the top), cliffs show stone or an Awakened biome's own block (hoarfrost, riven
  shale, fused glass, mirepeat: palette rock kinds in colour flags bits 4-6), badlands their strata, every block its own
  grain. The biome's materials (its rock, badlands, stony, alpine) are weights blended between corners (`vRocks`,
  `vBiome`), and where biomes meet each block takes one rock with the blend as its odds (world-anchored): with the flags
  flat per triangle, a border across a steep face drew whole triangles in one rock or the other (2026-10-26, Euan: grey
  spikes on the basin's rim cliff and the mountains behind it, riven shale against stone; `docs/perf/far-sight/fs3-rim.jpg`,
  tour `farsight3-rim.txt`). Snow lies by the alpine snow line and by a smooth coldness (each corner's blocks above the game's own cold
  line, info alpha, found by bisecting `coldEnoughToSnow`), both broken into patches over a ~20-block band
  (`AlpineSurface.patches`, the same noise the chunks' snow uses), on tops and as a fringe on risers. Scree and bare
  rock by slope; water over its own sea floor (2026-10-26, Euan's idea: the real water shows its floor, the far water
  showed none, so the floor ended at the chunks): the floor lies the water's depth down along the view ray, blocks of
  sand, gravel in the deep and seagrass patches, anchored to the world, showing by the water's absorption over the path
  (shallows and steep views, not deep water or grazing ones) and fading out over 700 blocks past the render distance's
  edge, so the open sea further out is only water; forest floor tinted by its crowns, and from about 600 blocks out (full by 2.4 km)
  a forest is its canopy: crown colour over every face, speckled crown by crown.
- **Bake** (fragment pass, `FarGpu`): per corner the normal, valley shade, and sunlight: a march toward the sun across
  the ring (40 steps, growing), soft by how far under the ridge line, never sharper than a cell and a half (a shadow's
  edge stepping corner to corner drew a saw). Valley shade only where the ground rises on both sides (the lower side
  of four directions, one and two corners out): a cliff's foot or a face under a summit isn't a hollow. Changed rings at once, one more every other frame
  (the chunk mask refreshes on the frames between).
- **Far forests** (`FarTrees`): rings 1-4 (half a kilometre to four) plant jittered candidate spots per cell (1, 2, 3,
  1 by ring; ring 4's trees are dots, the canopy shading carries the forest there) taking a tree as often as the forest is dense (densest: about two in three), never on water, on slopes over
  1.1, or above the treeline (14 under the alpine snow line); each a little taller or wider than the mean. Up to 512 a
  patch, 16 bytes each, in a persistent texel buffer (two runs per slot: predicted, real), stood as opaque voxel trees
  (crown top, two camera-facing crown sides narrowing by shape, two trunk sides): broadleaf, conifer, jungle, acacia.
  One `drawIndexedIndirect` of a command per visible run on Vulkan (multi-draw indirect, public API); a draw per run on
  OpenGL 4.1, which has no multi-draw indirect. A predicted tree steps aside where the real chunks' trees are known.
  Every tree stands on the far mesh exactly as drawn under it (the tree shader rebuilds the cell's vertices as the
  terrain's do: real corners where known, eased into the coarser ring, split on the index buffer's diagonals), never at
  its planting height, and none is planted inside `CityBasin.reserved` (2026-10-26, Euan's play: trees hung over the
  basin's edge). Test `farsight_trees_basin`. A tree patch's view-culling box reaches the tallest far tree (72 over its highest ground;
  at 40, a patch whose ground sat just below the frame dropped its treetops: far trees flickered with the camera's pitch).
- **Air** (`farsight_air.glsl`): aerial perspective from an exponential atmosphere at real scale (1e-4 per block at sea
  level, ×4 in rain; scale height 1500), toward the sun tinted by the sunlight; valley cloud, a patchy slab 40-86 above
  sea level lying among the mountains (a sea of it at dawn, dusk and in rain), sunlit on top, over far ground only; the
  game's weather fog; the horizon melting into the sky at ring 6's reach less its lag (≈14.6 km). While Far Sight draws,
  vanilla's render-distance fog moves out to its horizon and clear-weather fog stretches to 1.6× that (rain brings 85%
  back; fog under 200 blocks is never thinned).
- **Lens**: Far Sight reports its depth through `compat.client.FarTerrain.report` and `FarTerrain.selfHazed`; Lens then
  hazes far terrain only as much as the end of the real chunks (flag 8 in params texel 0) and leaves the kilometres past
  them to Far Sight. Lens's light pass doesn't touch far pixels; its clouds composite over far terrain past their layer.
- **The city** (`FarCity`, 2026-10-26, Euan's play: the ring wall vanished past the render distance): the far store
  learns real chunks as they load or save while far terrain collects, and `FarCollector` keeps track of every loaded
  chunk all along, so those loaded before (Carillon's pregeneration holds ~1,700 through the join, never saving them)
  are taken in when collecting starts, nearest first. City chunks never seen are laid out from the plan
  (`City.skyline`, top block per column, its map colour) after the real ones, and a real chunk replaces its planned one.
  Cells the city builds on (real ones above the street + 3, planned ones) carry `FarCity.BUILT` in their colour's top
  byte, and a far corner over them takes the highest built cell and its colour, not the average, so walls, towers and
  spires keep their silhouette at any distance. Test `farsight_city`; tour `farsight2-city.txt`.
- **The city before its chunks** (2026-10-26, Euan's play: a far hill stood over Carillon in the opening's flight, the
  spire through it): the far prediction lays every column inside the city's reach from the plan (`FarCity.plannedAt`,
  `City.skyline` painted once per chunk and kept), its top block's height and colour, flagged as known, so the city is
  right from the first frame whether terrain carved its basin or the city levelled its own ground. Built cells keep
  their highest only where a far corner spans 8 blocks or less (rings 0-2); coarser corners average, or one spire's top
  would be taken by every corner round it and slope into a cone. Ring 0's pieces still loading are drawn from the
  coarser surface, and its land pieces are skipped under the chunks only where every chunk under them is shown in the
  chunk mask: a chunk still meshing leaves the far land to cover it, never a hole to the sky.
- **Landmarks** (`FarLandmarks`): unchanged from stage 1 (gate pillars from `Gates.near` from 1500 blocks, the city's
  towers), drawn after the terrain and trees.
- **Budgets** (measured): Lens Ultra at sunset on the jagged peaks, 2880×1800 (11% more pixels than 4K at 0.75): Far
  Sight off 12.74 ms p50, on 15.53 (64.4 fps; 2026-10-26 on the head with upland snow, mountain spruce and the 3D mask,
  measured while ring 0 still filled at load 6; Far Sight's own GPU time 2.9 ms): the mesh and air 2.4 ms, trees 0.16,
  bake 0.07, valley cloud and erosion detail within noise. Lens Cinematic on the A/B scenes (`farsight-ab.txt`, new
  Alpine world): vista 11.0-12.1 ms p50, low 12.4, flight 12.9 (77 fps). GPU memory 49 MB (atlases 15 MB, trees 17 MB, depth at screen size). Render thread 0.2-0.5 ms. Horizon
  (ring 6) in 0.2-1 s after a jump, all rings in 7-23 s while the server generates the new land.
- **Turbo** (raw Vulkan compute culling) was not built: the plan keeps it only on a measured win, and what it could cull
  costs about 0.2 ms; the multi-draw indirect path is in place for a compute culler later.
- Tests: `farsight_rings_nest` (windows nest with 16 cells of room), `farsight_columns_predict` (every ring's patch from
  vanilla's overworld, the model within the generator's ground), `farsight_generated_layer` (a pillar and a pool through
  the disk cache, read back as a ring 0 corner). Tours: `farsight2-baseline.txt`, `farsight2-refshots.txt`,
  `farsight2-perf.txt`; results in `docs/perf/far-sight/`.

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
  the spawn decides (then the nearest player within 128, as before). Since the Living Wilds (§21.0.1) this holds only
  where the region map is off; in the overworld the place decides and a Hearth calms its land (§21.0.2).
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
    (mixin or Shoulder Surfing) is decided at gate G1. Settled 25 Oct 2026: Shoulder Surfing Reloaded (the pack's feel
    profile), driven by the action camera (§17.13); without it, vanilla's behind view with the action camera's pull-back.
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
Milestone 4, approved by Euan on 2026-10-25 ("Go ahead with the full plan and all recommendations"), from the Phase A
package (docs/carillon/m4/: look-bible.md, story.md, onboarding.md; artifact
https://claude.ai/artifact/9DmvY3bCg8FGMbJyCnbYBm):
21. The look bible is the source of truth for the city's look: the city is the course, climbable because of its
    architecture (the climbing grid, running faces, the movement-tuned dimensions of look-bible §3.10, the seven
    districts, the height plan, the street-life layer, ~35 kit blocks, twelve landmarks). The opening's routes come from
    the city's own affordances; the placed stage shrinks to near zero.
22. The High Terrace is raised from 8 to 12. No jetties on the Quay (its walls are running faces). Market spokes are 5
    wide (were 6).
23. The story of the first hour is docs/carillon/m4/story.md: the Void's line stays; the wake says the gate put you back
    on the stones; new voices Kestrel (Roostmaster) and Bram (Association officer). docs/STORY.md gains a Carillon section.
24. Onboarding is docs/carillon/m4/onboarding.md: just-in-time tips with the player's real keys through one shared
    system, `core.api.TipHooks` (tips as data; retire after use; never during cinematics; client option `tips`), the
    Registry as a walk with Bram ending in a pan to the High Gate, and first-stretch tips that retire at rank D or after
    three play-days. This reverses §17.2's "the Runner's own moves are found, not told".
25. One builder at a time: Phase B runs B1 (facade grammar + core kit) → B2 (relief and heights) → B3 (street life,
    roster 400–480) → B4 (landmarks) → B5 (the opening from affordances) → B6 (story and onboarding) → B7 (quiet perf,
    tour, Euan's play 3), each step a fresh builder briefed from a file.
26. Vanilla banners, signs and barrels stay out of the city's fabric (block entities); the kit carries the look. Light at
    night is mostly emissive, within the light-source budget.
27. Additions to Phase B (Euan, 2026-10-25), all without costly entities where possible (animated textures, particles,
    emissive layers, weighted model variants):
    - The kit grows to about 60 blocks: furniture and wares (tables, shelves of goods, sacks, crates, market produce, shop
      signs with icons) and random texture variants so walls and roofs never tile (variants in B1 for facade and roof
      blocks; furniture and wares in B3).
    - Movement for free (B3): waving banners and laundry, flickering lanterns, turning water wheels, chimney smoke, lit
      windows at night.
    - Animals (B3): cats on the roofs, pigeon flocks that lift as you run past, dogs following their owners, horses
      pulling carts down the avenues, boats on the canals.
    - Interiors you can glimpse (B4): lit rooms behind windows at night, plus full interiors for the tavern, the smithy,
      the bakery and the Association.
    - City light (B6/B7): Carillon's Lens looks, warm lamp bloom at night and morning haze over the river
      (assets/awaken/lens/looks_carillon.json).
28. The city soundscape (Euan, 2026-10-25), built by B3's builder (no extra builder):
    - Sources, best first: vanilla sound events referenced and layered (pitch, volume, combinations; never copied);
      CC0 recordings only for what vanilla lacks (wordless crowd murmur, gulls, water at quays, carts on cobbles,
      distant hammering, a tavern's hum), each listed with its source and licence and downloaded only after Euan's yes;
      synthesis (tools/art/specs audio pipeline) only for bells, the Choir's hum, gates and UI.
    - The mix: a quiet bed per district plus positional one-shots; many variants with random pitch, volume and timing
      (nothing loops audibly); the crowd bed follows the citizen ledger's count in that district at that hour; dawn,
      noon, night and rain change it; every sound comes from a place (Sound Physics in the pack); it plugs into
      core/audio's layer system so it never fights the music.
    - The gate is Euan's ears: a listening page (each district at morning, noon and night, before/after) published for
      his approval before B3 merges.
29. The Living Wilds (SPEC §21, approved by Euan) meets Carillon at the High Gate (agreed 2026-10-25): the city lays out
    the gatehouse yard outside the High Gate with named spots (`CityHooks.spot`); the living module spawns Quartermaster
    Hale and the bounty post there. Its bounties (`living.bounty`) are the first slice of the deferred contracts board
    under decision 5's rules (three notices, known places only, no expiry offline, claim ids), shown at Hale's post and
    on the Association board's spot; coin through `EconomyHooks.pay` once an economy exists, renown through
    `RenownHooks.witness` at turn-in. Its chains "The Last Delivery" (the Roost's word, Kestrel) and "Bell Metal" (the
    C-rank spine) are drafted by the Living Wilds session and reviewed by the Carillon session against
    docs/carillon/m4/story.md. Its tips use TipHooks with tip files in the onboarding.md format.
    The city also gives "Bell Metal" a rooftop chase: B5 (the opening from affordances, which brings citizens' rooftop
    running) publishes named roof routes and a chase hook the Living Wilds calls for its fleeing buyer; until then that
    step waits.
30. The city's animals, drawn light and soundscape (M4 B3b, 2026-10-25; the B3b builder's calls, for the orchestrator's
    review and Euan's listening page, docs/audio/carillon-b3b/):
    - Animals and boats are client-only entities (`citizens.CityCritter`, discarded on a server, never saved): homes and
      routes from the plan, made within reach (pigeons 56 blocks, gulls 72, cats 40, dogs 48, carts 96, boats 88) and
      dropped 12 beyond; at most 40 pigeons, 16 gulls, 10 cats, 8 dogs, 4 carts and 8 boats near a player. Carts and
      rowed boats run on the world clock, so every client sees them in the same place. Dogs: one keeper in seven among
      residents, children, the Watch, porters, washers, shopkeepers and millers. Boats: one moored every 24 blocks along
      each quay inside the wall (a quarter left empty, none within 6 of a bridge); two rowed up and down the river, one
      round the canal; rowing from 23000 to 18000, bow lanterns lit after dusk (a third of the moored ones too).
    - Hand lanterns and lit street lamps are drawn light (`citizens.client.CityGlow`: an additive core, halo and pool on
      the ground), never light blocks; `/awaken citizens lanterns blocks|glow` keeps the old path for measuring only.
      Choir Square's festoons: paper lanterns strung between the bowl's eight lamp posts, lit 12200-19500.
    - The beds: `awaken:city/<district>` by day and `awaken:city/night` from 12500 to 23300, on the ambience slot. The
      level is the district's citizens outdoors (counted every 100 ticks) against its busiest hour: calm under 15%,
      light under 45%, busy under 80%, else packed; one lower in the rain. The night bed is busy 12500-14500 and light
      to 16500 (the taverns), then calm. Each level's mix sits at -37 to -26 dBFS gated RMS and under -6 dBTP
      (quieter than the -28 ± 3 ambience rule: the beds lie under the places that sound), and a crowd stem never falls
      as the level rises.
    - Every place's sound is mono (positional), the carillon too: it comes from the spire and fades over 320 blocks.
      At dawn, noon and dusk the Choir plays its tune first and strikes the hour 300 ticks later.
    - Sources: synthesised (tools/art/specs/audio_city_life.py) except the animals' voices (vanilla's cat, wolf and
      horse events, referenced by id). This departs from decision 28's "vanilla first": vanilla has no city sounds
      beyond water, fire and animals. Nature is AmbientSounds' (Euan, 25 Oct 2026, instead of downloading CC0
      recordings ourselves; the pack's sound profile, 6.3.11 with CreativeCore, client-only). Its own engine plays real
      birds, crickets and owls where there's greenery, wind 15 blocks above the ground and rain. The client tells the
      server at login whether it runs the mod (`CitizensNet.Ears`), and such a player hears `awaken:city/as/<district>`
      and `awaken:city/as/night`: the same beds without our synthetic birds and crickets (generated beside them). The city
      adds what its engine never does in a paved city, with its recordings by id (`citizens.client.CityNature`):
      gulls within 64 blocks of the river by day (ours stand down), a gull chorus within 24 now and then, doves over
      Bellwatch, the Terrace, the Garden Ward and the Wallring by day, owls within 48 of the wall or a bell tower at
      night. Its files are stereo, so these play unplaced, at a volume falling with distance. Nothing of it is copied.

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
- Decided in M4 B1 (2026-10-25; details docs/carillon/milestones/M4-B1.md): the climbing grid lives in `CityGrammar` v2
  (holds only in odd rows of the strip one block in front of a wall; hoods at row 3, the cornice at 5, a box under every
  upper window and a floor course every storey; courses run across party walls and round corners; climb lines within 5
  of each end of every street-face run). A face gets the grid only with 3 blocks of open ground in front. Pilasters stay
  flush (proud ones stop every shimmy). Bellwatch jetties only over lanes 6+ wide, or 4–5 wide on one side. Kit windows
  replace glass panes on closed houses. Roofs: Bellwatch mostly kit slate, the Market mostly clay. The chimney crown is
  `chimney_bricks` (the citizens' smoke finder). Avenue tree crowns fill only air. The kit's first 19 blocks carry no
  block entity and emit no light; walls and roofs use weighted texture variants.
- Decided in M4 B2 (2026-10-25; details docs/carillon/milestones/M4-B2.md): plan.json schemaVersion 2 carries a signed
  lift raster (every plot lift even; hash re-pinned). Streets follow the ground in single-step flights (no drop of 2+ on
  the street graph); every plot stands at its street's level and two cells clear of landmarks. The Knoll rings +2..+8
  to radius 46 round the bell square (the tower 54 tall); Choir Square's bowl is a level rim (the arcade's ground,
  radius 31–36) with three rings down to −3, and CityHooks.square stands on its floor; the High Terrace at 12 with
  its houses set back 3 (a parapet walk); Garden terraces +2/+4; the lower quay at −4 (raster lift 0). Parapets only
  over drops of 6+, coping below that. Crossings over every avenue stretch and the Ring at least every 48: 7 enclosed
  walkways built, 13 line sites in plan.json "crossings" for B3. Named spots placed (decision 29): high_gate_yard,
  quartermaster, bounty_post, association_board (Terrace +12), bellwatch_cellar (a 9×9×5 room under the bell tower).
  Ladders: a model override draws every minecraft:ladder as two rails and four round rungs inside vanilla's shape
  (collision unchanged); `awaken:hanging_ladder` extends a ladder down or up with no wall behind.
- Decided in M4 Walls (2026-10-26; details docs/carillon/milestones/M4-walls.md): the plan is unchanged (hash
  575dde8a…); the wall is painted from the wall polygon's own geometry (`CityWall`: each column's side, depth and place
  along it, the rhythm counted in face cells so diagonal sides keep it), the towers and gatehouses by `CityTowers`
  (parts paint a margin of 4). Outside: a talus in three steps, buttresses 3 wide every 8 with set-offs at 13 and 33,
  courses at rows 15 and 27, two-block loops and putlog holes, spouts under a machicolated parapet two deep on corbels
  (merlons 3 high, a breastwork 1 high, crenel sills are walls: a "no"; since §17.27 climbed onto, never crossed). Sides 1, 3, 10, 11, 19, 21 roof the walk over a
  timber hoarding. Inside: an arcade of bays 7 apart (deep 3, open to row 5) holding the wall's life, a course at row 9,
  recessed panels to row 31 with ivy, a drainpipe on every third pier and near each end of every stretch (the climb lines
  to the walk), banners, flagpoles at row 13, awnings at row 3 over stalls and workshops, a corbelled parapet band over
  the ring and a stone-wall parapet (a "no") with lanterns every 16. The west side carries a grand stair up the wall
  instead of bays. Every hold on the wall's faces sits in an odd row (stacks are one or three high). Towers take the
  district behind them (Bellwatch round with timber hoardings and slate needles, Terrace square with bartizans, Garden
  octagonal with verdigris spires, Works battlemented with a beacon and a clay-roofed watch room, the Quay's water gates
  octagonal under copper with a lantern); every tower has a door on open ground and a newel stair to a hall on the walk.
  Gate towers rise 4 over their plan height (eave 54) so their gallery clears the walk's hall; the walk passes every
  gatehouse through the room over the gate. No plan lamp stands in a gate passage or a drainpipe's column. Kit: arrow
  loop, wall ivy (climbable), portcullis, brazier (the kit's one light source, 14), wall spout, wall shield.
- Decided in M4 Commons (2026-10-26; details docs/carillon/milestones/M4-commons.md; Euan's playtest: the walk out read
  rough, too many monsters, banner poles broken): the plan is unchanged; the land outside the wall is painted by
  `CityCommons` (the roads and the ground), `CityFarms` (the fields), `CityFarmsteads` (six farms) and `CityTrail` (the
  High Gate road past the reach). The roads in keep their 8-wide line: setts to 22 from the gate, then cobble kerbs at
  3.5 from the centre, gravel shoulders, wheel ruts of coarse dirt at 2.5 and a trodden crown; grass verges to 5.5, a
  ditch (water a block down) at 6.5 and a hedge or spruce fence at 7.5 from 26 out, a field gate over a culvert every 48
  (by turns either side). Lamps and pennant lines by turns every 14 from 24: a pennant line is two line poles at 5.5
  with a bunting line across the road at row 7 and a district banner hung from it by each pole (the yard's two lines
  replace its masts; the floating flagpole arms are gone). Every line ends at its anchor (Euan: "ropes still don't
  connect to poles"): a line pole's top that ends a line (`rope=` the way the line leaves) has the line's rope tied
  through its far ring and out to the line at a sag-0 rope's height, its rings turned along the line; the cell beside
  a pole hangs at sag 0; a banner under a line hangs on two cords from it (`mount=line`); and a city line whose plan
  end stops short of the facade runs on as bare rope across up to 3 open cells to the building (GameTests
  `city_commons_poles`, `city_lines_anchored`: every end meets a pole facing it or a wall). Milestones every 64 from 40 on the right-hand verge. Fields
  keep the lattice (bands 21 out from 252, cells about 26 round): boundaries are hedges, farm tracks or grass balks by
  pair; fields are arable strips 5 wide (four rows of one crop at its own growth, then an irrigation channel or a balk;
  wheat, carrots, potatoes, beetroot, pumpkins, ploughed or fallow; a scarecrow on the seed of half), reaped (stooks
  every 4), orchards (apple, blossom, cherry, 5 apart), pasture (hay) or meadow, with lone oaks; bands 2 and out are more
  pasture and meadow. Six farmsteads (box 22 by 26, three by the first field gate of each road): a farmhouse from the
  grammar (use "farm": its faces over open ground are street faces), a timber barn with open doors and a loft, a
  haystack, a well, a hay cart, a paddock, a kitchen garden, a woodpile and a lamp at the track. Past the reach the
  High Gate road is laid on the ground as it lies to 424 north (a plank bridge over water, lamps and milestones, no
  ditch or hedges), a fingerpost, then a footpath bending to 26 short of the Wayfarers' fire (450 north, 6 east; the camp
  and its 32 of safe ground are the Wayfarers'). Monsters: outside the wall a natural spawn of anything hostile (MONSTER,
  `Enemy`, or an Awakened creature that isn't peaceful) goes ahead with chance 0.10 to 330 from the centre, rising to
  0.45 at 470 and 1 at 640, and never where three hostiles stand within 48 at about its height (to 470 out;
  `CityMonsters`, at the natural spawner's position check); inside the wall the lamps decide. Kit: milestone, fingerpost, scarecrow,
  stook (no light, no block entity). Dev: `/awaken city monsters [radius]`.
- Floating wood fix (2026-10-25): the city's vegetation sweep takes the lands' bark (elder wood) as well as leaves and
  logs, sweeps the whole neighbour chunk to full height, clears fields to the top, and removes tree groups left resting
  on nothing past the edge (`CityWriter.collectOrphans`). Dev: `/awaken city scan | floater <n> | tidy` (tidy repairs
  cities built before the fix).
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
  with the world; generator changes never touch a built city. A finished chunk keeps a timed `awaken:city_hold` ticket
  (1,200 game ticks) instead of none, so the city isn't unloaded, saved and read back between the pregen and the
  player's join (26.3 has no spawn-chunk ticket).
- Drawing (G0, spike S1: a 480-block city sits inside RD 16 from anywhere in it, so all of it is always drawn): houses
  have solid cores (dark fill behind windows, sealed attics) except the ~60 enterable buildings; geometry density
  varies by district (dense only where the run needs it); the painter writes straight into chunk sections (never
  setBlock), sets pane/fence/stair connections itself, keeps pieces ≤ 14×14 with a 2-block margin, places no block
  entities in bulk and ≤ 4–5 light sources per chunk.
- Terrain (agreed with Awakened Lands, 2026-10-24): `dev.awaken.terrain.api.CityBasin` picks the site deterministically
  from the seed at server start (land within ~1,500 of the origin) and flattens it: `CitySite(x, z, coreRadius 330,
  blendRadius 700, streetY 70, riverPath, basinCarved)`; `CityBasin.site(server)`, `CityBasin.reserved(x, z)` (core + 48,
  allocation-free). `CityBasin.reservedWithin(x, z, reach)`: true when any point within `reach` of (x, z) is reserved
  (a circle test); hero trees and landmarks use it so no part of a crown reaches the reserve. It needs Lithostitched (pack world profile); without it `basinCarved` is false and the painter
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
- Registration (2026-10-25): when the opening ends, or is skipped, the counter `awaken:carillon_registered` is counted
  once (`OpeningDirector.REGISTERED`). Chains and content that wait on "after the Registry" require it.
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
  flight is pre-warmed behind the opening card, and the card holds (up to 20 s more, `SkyShot.WARM_MAX`) until the view
  is warm (`FarSightClient.warm()`: every far ring's patches in and 97% of the chunks well inside the render distance
  loaded and meshed; at once when Far Sight is off), so the first frame of sky has no hole, seam or hill where the city
  stands (2026-10-26, Euan's play). The server allows the sky ninety seconds. Life seen from the sky is blocks and particles (lit windows, smoke,
  lamps, boats): citizens draw only within 128 blocks.

- **The Registry ceremony** (2026-10-26; Euan, playing: "they actually physically hand you the Holo band and the paper
  to sign. You sign first, and then they give you the Holo band. And then you actually have to open your inventory and
  put the Holo band on"). `OpeningRegistry` steps (schema 2 of `OpeningState`; an older save walks the Registry again):
  WALK (Bram leads from the bed through the atrium to the Lens: `OpeningPlaces.walk()`, straight lines through the
  doorways; he walks on when you're within 4.5 of him, waits and nudges past 9, says "Habit, I know" once if you run
  ahead) → READ (as before) → SIGN (at the desk, the counter's free south end; use signs) → BAND (Bram holds it out over
  the counter; use takes it) → WEAR (the band is in your hands, the Holo dark until you put it on the wrist slot) → BOOT
  (the boot on the wrist; the inventory closes itself 0.7 s after it goes on) → BRIEF (his three lines, then the camp: "The bells hold the city. Hunters hold the wilds. Ilse at the camp
  keeps a fire for new licences.", whose Ember founds a Hearth, §28) → OPEN ("Go on,
  open it": the K prompt and `TipHooks` tip `awaken:holo/open` show only now) → STATUS → ATLAS → POWER → DIRECTIVE →
  YOURS. Waiting steps nudge every 30 s (at most three); without a client every step moves on by itself.
  - Bram is staged through `CitizenHooks.stage(level, "bram", at, yaw, act, prop)` (citizens role STAGED; lapses if not
    renewed for 30 s; a party follows the first player in the Registry) and found with `CitizenHooks.where`. He is the
    infirmary's officer too (a Watch citizen only if he's busy). Props by item id: `holo_band`, `licence`.
  - Signing (`opening.client.SigningScene`, world source `opening_sign` 250): the view leans over the paper (×0.7 FOV);
    the main hand takes the quill (a feather) from the ink pot, dips, writes the player's name across the signature
    line letter by letter (4 ticks each, glossy while wet, a slanted hand, the cartographer's quill scratch), a flourish,
    a blot, the quill back in the pot; the off hand holds the paper's corner. Bram stamps it (the blue seal "E" lands in
    the ring with a thump, his name under it); the paper folds toward you. The licence is a real item,
    `awaken:hunters_licence` ("Signed: <name>", witnessed by Bram); old paper licences still count.
  - Taking the band (`opening.client.HandOver`, `opening_take` 250): the hand goes to his, closes on the band, brings it
    up into view and turns it once, then settles; the server puts it in the selected slot (else anywhere) at the grasp.
  - **The wrist slot** (`holo.band`): the band is an item, `awaken:holo_band` (a 3D model, tools/art/specs/registry.py);
    the wrist is one slot beside your figure in the survival inventory, above the off hand (inventory image 77, 44), in
    vanilla's look or the holo inventory's well. Why not a real slot: 26.3 and NeoForge have no API to add a slot to
    `InventoryMenu` (it builds its slots in its constructor; `addSlot` is protected and the remote-slot lists are
    private), so a real one needs a mixin; a slot in our own equipment screen would hide it from where players look.
    So the wrist is kept on the player (`holo.band.BandState`, attachment `awaken:wrist`, schemaVersion 1, kept through
    death, synced to whoever can see you), the client draws it and sends its clicks (`awaken:wrist_click`: a click with
    what the cursor carries, shift off into the inventory, shift on from a menu slot), and the server checks and moves
    the stack between the menu's carried stack, the inventory and the wrist. Creative's inventory has no wrist slot
    (use `/awaken band wear`).
  - Off the wrist the Holo is dark (`core.api.HoloBand.worn`): K opens nothing (an action-bar line says why), no
    projected look (System cards fall back to flat glass), no level readout, E opens vanilla's inventory (the one you put
    it on in). The empty wrist breathes blue while the band is in your inventory or on the cursor. Putting it on boots it
    (`holo.client.HoloBoot`, drawn by `Wrist`): the forearm comes up with the band dark on it, it clicks home, a line of
    light runs round it face by face, its marks light from the middle out, the diamond ignites with a ring of light
    spreading off the wrist, a breath, the arm goes down (about four seconds the first time in a session, under two
    after). Taking it off powers down (a falling chime); the K menu closes if it was open.
  - Edge cases: skipping (any way) puts a band on the wrist (the one in your inventory if you have it) and gives the
    licence; a Runner has no band (the opening's start takes it away); a player who never had the slot (an old save, a
    classic world) finds a worn band in it at login; bands in the inventory at a death are kept and handed back at the
    respawn (the worn one never leaves the wrist); a dropped band never despawns and doesn't burn. `/awaken band
    status|wear|dark|give`. GameTests: `holo_band_dark_until_worn`, `holo_band_wearing_toggles`, `holo_band_slot_saves`,
    and `opening_skip` / `opening_state_machine` check the band.
  - From Euan's watch of the tours (2026-10-26): Bram reaches the desk round the counter's far (north) end, never across
    its front where you stand, and is put straight behind it when he has no body near; a staged citizen can't be pushed
    (so never shoves you); the opening's speech shows one line at a time (a new line replaces the old, no stacking); the
    key prompt sits just above the speech; the licence goes into the pack so the band lands in your hand.
  - One thing on screen at a time (Euan, 2026-10-26): the opening's lines and titles wait in one queue and each takes
    the screen only when the one before has finished (a line stays 50 ticks to 40 + 2 per character; at most 6 wait,
    the oldest goes unsaid past that); a prompt shows only when nothing is speaking or waiting. Through the infirmary
    and the Registry the System's notices (`Notices` hold), the tip system (`TipHooks` hold; the scene's own prompts are
    its tips there) and vanilla toasts are held; the toasts come back one at a time, 2 s apart, after the scene. A
    staged citizen's name label doesn't show.
  - **Proposed (Euan to confirm): the Atlas reveal right after the band's first K.** The Holo's boot is the natural
    moment: when the K menu closes the Atlas opens by itself with the run you made this morning (the land prints in, the
    bronze thread draws itself), and Bram says "Your band keeps a map. The city, the way you've run it." The run is sent
    as soon as the Registry begins (`Discovery.trail`), so the reveal is ready; `AtlasClient.openReveal()`.

### 16.5 Citizens (W4)
- Since Between the Gates W0 (2026-10-26, §28.4) the engine is multi-settlement: Carillon is settlement 0 on it, built
  from its jar roster and plan as before and behaving as before; other settlements (W3's villages) run beside it on
  their own ledgers and ground, and every settlement shares the one pool of bodies below (96 a world, 40 a player,
  nearest first). Saved data `awaken:citizens` is schemaVersion 2 (a list of settlements); a v1 file loads as
  settlement 0.
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
- Table view: a Screen with a picture-in-picture renderer (depth, orbit, zoom, tilt); the band's Map page (§30).
  ≤ 2 ms/frame open, ≤ 150 ms to open, glance ≤ 0.3 ms. Markers only for what the player knows.
- Decided in M5 (2026-10-25; concept https://claude.ai/artifact/3QCvup43wuVW6t1wDfVroP, docs/carillon/m5/):
  - Discovery (`atlas.Discovery`, attachment `awaken:atlas`, `AtlasData` schemaVersion 1, copyOnDeath): per dimension,
    tiles of 32×32 cells (16 longs). A player's path is checked every 5 ticks once they've moved 4 blocks; every cell the
    64-block circle touches clears. Spectators explore nothing. Awaken's instanced dimensions (`compat.Instanced`:
    gates, dreams, trials) are never recorded and the table doesn't open there ("The Atlas can't see inside a gate").
    Changed tiles go to their owner in one `atlas_sync` batch every 10 ticks; login, respawn and a new dimension send
    the dimension whole. `AtlasHooks.reveal` clears at once and ripples out from its centre on the client.
  - Bells: a bell rung by a player (by hand or by their projectile) or a player standing in its belfry (within its
    tower's box ±1, at or above the belfry floor − 2) clears its district: every cell whose centre
    `CityHooks.district` gives that district, within 360 of the square. The Choir spire's bell (belfry 61) clears every
    cell in the city. Towers, belfry heights and district names come from `data/awaken/city/plan.json`. The clouds over
    a district part in a ring from the tower (160 blocks a second).
  - The opening run's path is kept (a point every 10 ticks of the run and arrival, ≥ 3 blocks apart, ≤ 400) and sent
    when the opening ends; the next time the table opens it plays the Registry reveal (the land prints in, then the
    run draws itself as a bronze thread over 2.4 s and settles to a faint line). An automatic open inside the Registry
    scene is the opening module's call (proposed to the orchestrator).
  - Party sharing on request ("Share with party" on the table, `/awaken atlas share`): the current dimension's cells are
    merged into each online member's record.
  - Cache: one deflated file per 512×512 tile, `t.<x>.<z>.bin` ("AWAT", version 1: heights, ARGB colours, an info byte
    per column with water depth, material class and a lit bit for night), under `sp-<save folder>` or `mp-<address>`.
    Saved every 30 s and on leaving; the whole cache is kept ≤ 64 MB by deleting the oldest tiles down to 56 MB.
    "Clear Atlas cache" deletes every world's tiles and the sampled columns; what you explored is the server's and
    stays. Roofed dimensions (the Nether) draw no terrain yet.
  - Mesh: three levels (a column is 1, 4 or 16 blocks; the level follows zoom: ≥ 0.9, ≥ 0.22, below), slices of 64×64
    columns, a 15×15-slice window per level, ≤ 96 MB of GPU buffers (farthest dropped first); a level not drawn for 20 s
    lets its slices go. Slices build on one worker thread from snapshots; the main thread only uploads, before the
    pass opens. The finest level is kept built round the player while the table is shut.
  - The table opens on M (Xaero's key), in the Holo's projected glass (`HudLook` FRAME | DRIFT, text as light), sets
    `UiHooks` focus while open, and plays the Holo's open and close sounds. C centres on you. Dev: `/awaken atlas
    stats|share|reveal <r>|ring <city|district>|clear` and the client's `/atlas open|view|spin|freeze|reveal|stats`.
  - The look (phase 2): night lights from the lit bit (a light-emitting block in the top 6 of a column) as warm soft
    sprites, flickering a little, at the two finer levels; cloud shadows on explored ground beside the blanket's edge
    (east and south of each puff: the sun is low in the west); ambient occlusion on the top faces (darker for each
    taller neighbour); three cloud sprites with sunlit rims and a tone per puff; zoomed out, a coarse cell that is
    mostly known carries no cloud. Rumours (`MapMarks` labelled "? …") hang in the clouds as an amber "?" with their
    name; every other mark shows only in an explored cell. You are a pin of light with a ring and a facing tick.
  - Cost: slices outside the picture aren't drawn, and while only the clouds drift the picture is redrawn about 30
    times a second (every frame while the view moves or the land prints in).
  - The wrist glance: the land round you (240 blocks across, where you look is up) painted into the texture
    `awaken:atlas/glance` (480×280) at most once a second while something asks for it, or sooner once you've moved 4
    blocks or turned 12°. It left the K menu with the band's pages (§30: the Map page is the table itself); its
    painter is kept for the map scene (§30, phase 4), which will drive it from its own camera.
  - Phase 3 (`AtlasData` schemaVersion 2): the Living Wilds region you stand in (`living.api.Regions`, checked every 40
    ticks, overworld only) joins your Atlas once; zoomed out, each known region's name is lettered at its heart in
    spaced capitals with its levels under it. Party pins: double-click the table to drop a pin on ground you know (or
    take back your own under the cursor); twelve per player, the oldest goes; each party member online sees the
    party's pins in their dimension, in the owner's own colour (a hue from their id), with the owner's name. Dev:
    `/awaken atlas pin`. Roofed dimensions (the Nether) are read from just under the roof (y 118) down through rock to
    the first air, then to the floor, so the table shows the highest floor of each column; those chunks are sampled
    from a queue about 1 ms a tick, the diorama's base drops to 24 and there is no night. Carillon's lit windows
    (`awaken:lit_window`, within 12 of a column's top) count as night lights.
  - A bell's ripple adds each cell to the client's record only when the ripple reaches it (so no land shows under
    clouds that haven't parted), a whole re-send drops every built slice, and the clouds draw over the land (no depth
    test: spike S5's poke-through left roofs and spires floating in the blanket at the known edge). While the view
    moves the picture redraws at most 60 times a second.
  - Measured on OpenGL, uncapped, interleaved pairs at the same spot (tour 2026-10-25, load 5–9): the table open and
    still +1.5–1.6 ms p50 (+1.4–1.5 of it is the open screen itself, re-blitting the frozen picture: the blit, the glass
    card, Lens dimming), clouds at 60 Hz +1.9–2.0, orbiting +1.9–2.0 after the 60 Hz cap (+2.6 before); open 12–20 ms
    cold, 2–8 ms warm; the glance 0.23 ms a paint; sampler 10–18 µs a chunk (120 µs under the Nether's roof).
  - Zoomed out (polish, 2026-10-25): a cloud cell of 64 or 256 blocks carries no cloud if any cell in it is known
    (counted exactly, a word of bits a row), and one beside known land carries a half-size cloud that keeps to its own
    cell, so the land you know shows at every zoom under a whole blanket. Region names keep clear of you, the pins, the
    marks and each other (moved up or down by a line or two, else left for a closer zoom) and slide inside the panel
    at its edges.
  - Readability (Euan, 2026-10-25: "colour-coded sections … know where one thing starts and ends"; names in the clouds
    read as "so what?"): every known column belongs to a zone, Carillon's district (block by block from the plan's
    raster, placed from the square the server sends, `atlas_city`) or outside it the Living Wilds region of its 16×16
    cell (the server names each explored cell with its region, 1-based into the known regions, sent with the tile;
    `atlas_sync` channel version 2; a region you have explored land in is known). Zones are baked into the mesh: a 30%
    wash of the zone's colour, and where the next known column is another zone a bright line of each side's colour.
    Regions are coloured by danger (by their lowest level: 1–8 green, 8–20 yellow, 20–45 orange, 45–80 red, 80+
    violet); districts each their own. Names are chips (dark glass, a bar in the zone's colour, the name, its levels or
    "Carillon") on the known cell nearest the middle of the zone's known land, largest zones first, moved a line or two
    to stay clear of you, pins, marks, the legend and each other; districts close in, regions at every zoom; the old
    spaced lettering and the bell-position district labels are gone. The legend ("KNOWN LANDS", top left, L hides it)
    lists the zone you're in first and the nearest others (districts close in, regions zoomed out) with the danger key;
    clicking a row glides the table there. Hovering the table names the zone under the cursor (unless its chip is
    right there). A region takes one of three shades of its band's colour (by its id), so two neighbours in one band
    still read as two lands; borders are the zone's colour half-way to white.
  - Cost after these changes (OpenGL, uncapped, east of the city, load quiet): closed 5.8 ms p50, open and still
    +2.0, orbiting +2.6 (every frame redraws while the view moves: the price of a table that keeps up with the mouse).
  - Back under budget (2026-10-26, profiled in Carillon with `/atlas prof <bits>`, each part switched off while
    orbiting between "all on" windows): the land cost ~1.3 ms a redraw, the names, legend and hover ~0.7, the clouds
    ~0.5; a lower-resolution picture alone barely helped (vertex-bound, not fill). Fixes: top faces merge greedily
    into rectangles where the height matches and the colour is within 5 of 255 a channel (395k → 246k quads in the
    city, no visible change); chips and the legend draw plain text on their dark plates (the title keeps the Holo
    glow); the hover waits until the table stops moving; while the view moves the picture is drawn at 60% resolution
    without cloud shadows, then once at full resolution 0.11 s after it stops. Result (load 5–7, closed 7.35–7.45 ms):
    open and still +0.9–1.0 ms, orbiting +1.5–1.8.
  - Cloud motion (Euan, 2026-10-26: "jittery … like a 1 FPS animation"): each puff no longer wobbles on its own sine
    (puffs swinging past each other flipped their draw order and popped); the whole blanket drifts on one slow wind
    (1.8 blocks a second from the west-south-west at the finest level, scaled with the cloud cell when zoomed out) on a
    lattice that slides with it, each puff taking its cover from the land under it, blended between cells, so it thins
    smoothly as it drifts out over known land (zoomed out, gone before it reaches it) and new cloud fills in over the
    unknown; each puff breathes 5% on its own long phase. Drifting clouds redraw 60 times a second (was 30). Measured
    on a busy machine (load 12–29): open and still about +1.4–1.7 ms, orbiting about +1.8–2.1.
  - Clouds (same day): a puff's tone comes from its cell's hash (from its drifting position it changed every redraw and
    the blanket flickered); the view redraws every frame while it moves (the 60 Hz cap made the table lag the mouse);
    zooming across a level keeps drawing the built level until the new one is ready round the view (it blinked out).
  - Frame cost is judged on OpenGL: on Vulkan, MoltenVK presents at the display's rate (120 Hz) even with vsync off, so
    a capped tour can't show it.

### 16.9 Public hooks (`core.api`, v0; signatures may change only through the orchestrator)
- `CityHooks`: `inCity(Level, BlockPos)`, `district(Level, BlockPos)` (id or null), `isCivic(Level, BlockPos)`,
  `startPoint(ServerPlayer)`, `square(Level)`, `spot(Level, String id)` (named spots the city lays out for other modules:
  `high_gate_yard`, `bounty_post`, `quartermaster` (the Living Wilds' Hale), `association_board`, `bellwatch_cellar` (the
  melting room under the Knoll's bell tower, for the Living Wilds' "Bell Metal"); null until placed).
  Graph types are added at G1.
- `CitizenHooks`: `witnesses(ServerLevel, Vec3, double)` (citizen ids), `post(ServerLevel, Identifier event, BlockPos)`,
  `stage(ServerLevel, String named, Vec3 at, float yaw, String act, String prop)` (null `at` releases; 2026-10-26, the
  Registry ceremony), `where(ServerLevel, String named)`.
- `HoloBand` (2026-10-26): `worn(Player)`, `fitted(Player)`, `takeAway`, `wear`, `give`, `has` (server), `listen`
  (server: on/off), `listenClient` (client: the boot finished, or it went dark).
- `RenownHooks`: `witness(ServerPlayer, Identifier deed, BlockPos, int weight)`, `tier(Player)`, `standing(Player,
  Identifier faction)`.
- `EconomyHooks`: `balance(Player)`, `pay(ServerPlayer, long, Identifier claimId)` (once per claim), `charge(ServerPlayer,
  long)`.
- `PartyHooks`: `members(Player)` (includes the player), `sameParty(Player, Player)`.
- `OpeningHooks`: `inOpening(Player)`, `stage(Player)`, `skip(ServerPlayer)`.
- `AtlasHooks`: `reveal(ServerPlayer, BlockPos, int radius)`, `explored(Player, BlockPos)`.
- `MovementHooks` gains `wallRunning`, `hanging` (W2 adds them).
- Movement block tags (the city's affordances for the Ascent's free-run and ledge reading; 2026-10-25): the city fills
  them with every kit and vanilla block it places for each role, B1 for facades and roofs, B3 for street life.
  `#awaken:runner_ledges` (hang and shimmy: cornices, string courses, sills, beam ends, balcony rails, trim),
  `#awaken:runner_climbs` (climb up: drainpipes, ivy and trellis, scaffold poles, ladders),
  `#awaken:runner_lines` (rope and Tether: laundry lines, bunting, crane ropes),
  `#awaken:runner_soft` (soft landings and bounces: awnings, hay carts),
  `#awaken:runner_swings` (swing points: bracket signs, flagpoles).
  The "no" language (fences, spikes) is in none of them. (Since §17.27, Euan 2026-10-26, fences and walls are crossed
  everywhere, the city included: still no ledge, line or swing, but a crossing.)

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
  before it. (Superseded by §16.0 decision 24, Euan 2026-10-25: the Runner's moves are taught just in time through
  `core.api.TipHooks`, with the player's real keys, once, then quiet.)
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
- At a **climb** (a Tether Spire, a sky platform, a hero tree's crown) a block is refused outright instead, and no
  Hearth suspends the ward or its walls (§17.25).
- Inside a ward, outside every Hearth's radius, a block a survival or adventure player places crumbles 3 s (60 ticks)
  later: still the same block, it is removed (water stays where it was waterlogged) and the item that placed it goes back
  to them (inventory, else at their feet; if they left, where it stood). Containers drop what was put in them, as
  breaking would; a shulker box keeps its contents in the returned item. Creative and spectators build freely. The first
  ward block a player places teaches it with a toast (flag `awaken:ward_noticed`). Pending crumbles all crumble when the
  server stops; a block whose chunk unloaded first stays.
- Inside a ward a block in `#awaken:ward_sealed` (every Precursor building block plus the deepslate and blackstone
  dressing ruins build with) can't be broken by a player, an explosion or a piston (pistons inside a ward never move).
  The vault's sealed chamber is enclosed in those blocks, so a rank door can't be dug around. Natural ground still digs.
  A Hearth's radius gives a site's walls to its owner, **except a vault's** (vault and spawn vault): those hold even
  inside a Hearth, or a Hearth Anchor beside a vault would let an under-ranked hunter dig round its rank door (2026-10-26,
  with the Tether Vow's rank lock, §17.7). Placed blocks still stay inside a Hearth.
- Its own look and sound, never vanilla: `awaken:ward_spark` (a thread of the Signal's #7FC3FF light) and `awaken:ward_grain`
  (a chip tinted by the block's map colour), built on the client from one `ward_fx` event (mark, crumble, hold); sounds
  `wilds.ward_mark`, `wilds.ward_crumble`, `wilds.ward_hold` (tools/art/specs/audio_wilds.py; sprites tools/art/specs/wilds.py).

How you arrived (`wilds.arrival`, behind `core.api.ArrivalHooks`; config `features.wilds`):
- Every tick the server reads each player's way of moving; a quarter second keeps its most telling way (in `Way` order:
  STILL < WALK < SPRINT < SWIM < CLIMB < RIDE < AIRBORNE < POWER < ASCENT < LINE < BUILT) and 80 of them (20 s) are kept.
  BUILT: standing on (any corner of the feet) or climbing a block the player placed in the last 10 minutes (last 512
  placements). POWER: 2 s after a cast of a movement-slot power, a dash or Hover, and every tick Flight or Hover runs.
  ASCENT: a Runner move (wall-run, ledge hang); LINE: the Ascent's own ladder (a rope out, a zip, the carry, wings;
  §17.25). The history is never saved.
- A visit: stepping into a ward opens it with the window's verdict (no BUILT in the 20 s that led there); a BUILT sample
  while inside spoils it; it ends after 5 s outside. Outside any ward the window is the verdict. No record: movement.
  A visit also knows if the line carried the player (`ArrivalHooks.byLine`); a visit open at logout is saved and comes
  back if they log in inside the same ward within 10 minutes (§17.25).
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
  - **Your ghost**: every run is sampled (every 2 ticks: room-local position and yaw); a finished run faster than your
    best on that course (gate, area, floor, room) becomes your ghost, kept while the server runs. Starting a run there
    again, the server sends it to you alone (`gate_rig_ghost`: eighths of a block from the room's origin, yaw in 256ths)
    and the clock shows "best" beside the par; your client runs a translucent you in pale gold (`GauntletGhost`, the
    Echo's figure in your own skin, lit from within, a soft glow about it and a short ribbon of gold light behind it)
    along it from the same start, interpolated between samples, its walk animated by its pace. It waits 1.5 s at its end,
    then goes; giving the run up takes it at once. Client only: never seen by anyone else, never saved.
  - Not yet: pistons.
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
- **Perks** (`Progression.perk`; Living Wilds' relics): `awaken:rope_reach` adds its blocks to the rope's reach on
  both sides (the throw in `AscentMoves.fire`, the server's anchor check; Wren's Hook gives 6). `awaken:sprint_recharge`
  takes its fraction off the tactical sprint's recharge for the rank, at most half and never under the S rank's 30
  (`BodyRules.tacRecharge(player)`, read alike by the client, the server's check and the sprint line; the Courier's
  Knot gives 0.25: 80 ticks at E become 60, an A rank's 40 the floor's 30).
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
- Who: awake, in the overworld, without the rope, the `ascent` option on, **at the vault's line rank: its own door
  rank, D at the least** (`TetherVow.RANK` = D since Euan approved the progression plan, decision 1, 2026-10-26; it was
  C for a few hours; the door rank is the piece's saved `rank`: D within 1200 of the origin and at the spawn vault),
  **with its sealed door open** (no door panel or core left at local x 10-12, y 1-3, z 12). Euan, 2026-10-26: "I got the grapple thing when my
  rank is too low to actually open the vault. I just walked into the vault and got it... you actually need the C rank
  to get into the vault to unlock it." Where: a Precursor vault's forecourt (local x 4-18, z 0-3, feet 1-5 over the
  floor: between the pylons; every vault qualifies until one is kept). So the order is: open the door, go in, and the
  vault calls as you come back out under its pylons. Checked once a second per player.
- Short of that, the vault stirs (stage `STIR`, at most once in 5 minutes per vault and state): short of the rank, a
  low, quiet version of the call's note, the vault's light rising off the paving in the rank's colour, stalling and
  settling back, "The vault stirs, and stills." then "Its line waits for a D-rank hunter."; ranked but with the door
  still sealed, the light drifts low toward the door: "The vault stirs. Its door will know you." No Lens look, no hush:
  it only half wakes. The rank is named; what the line is stays unsaid until it is given.
- The sealed door answers (`DoorCoreBlock`, all ruins with a rank door): someone short of its rank who comes within 5
  blocks (read at most every 20 s), touches it or strikes it is read: the ward's HOLD light on the core
  (`WardFx.hold`), the door's refusing tone, and "Sealed. It reads you and stays shut. Come back at C-rank." A strike
  used to do nothing (the door can't be broken), which read as a dead block. The hum for those who can open it is as
  before.
- Worlds where the rope was already given under the old rule keep it (nothing is taken back).
- Pacing (docs/progression/PLAN.md §4): D arrives about 5 h in (pacing_sim, balanced), so the rope is the first
  rank-up's reward, at the vault the player met in the first hour. The Leaper follows hours later (§17.10), Wings at C
  (§17.9).
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
  anchor (real to the server, the line clear) and the mast; then the lock: at E the vault only stirs and its door reads
  the player once and stays shut; at D a C door's line still waits, and the D door's vault waits until its door is
  open; then the director runs: call, line, kept.

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
  without Wings, **at C-rank or above** (`WingCoreBlockEntity.RANK`, progression plan decision 2, 2026-10-26), who came
  up by moving (`ArrivalHooks.byMovement` and `Ascent.arrivedByMovement(p, 20)`: a third of the
  last 20 s in a ladder state or a Runner move and no block placed) is given Wings (`Ascent.grant`, "sky platform core")
  with the gift's scene (`Remember` mode `GIFT_WINGS`: "Wings of light.", the wings unfold round the body, then the
  hint to open them), `ascent.vow_call`, counter `awaken:ascent_wings_core`. Anyone else is told why on arrival, then at
  most every 10 s (`Throttle`): "The core stays dark. It answers only to a climb up its rings." or "The core stirs, and
  stays dark. It waits for a C-rank hunter." (before: once a minute, on the minute, so a refused player could wait 60 s
  in silence). The Horizon's "wings" need is reached by Wings or the rope alone (the rings are a rope ladder) and reads
  "· needs a rope or wings". It breathes a ring of light every 3 s. The
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
- Over a forest, rigging opens the canopy under the first ring (leaves and vines within 2 of its column, from the
  ground up): a throw from the ground sees the ring (a tour stood under birches throwing into leaves).

### 17.10 Ascension trials: the Leaper's Gauntlet
- **Opened for you** (`AscentTrials`): once you hold the rope (not the leap) at **D-rank** and have swung enough to
  know it (Tether mastery **level 4: 400 points**, about 80-130 swings and launches; it was level 2 until the
  progression plan's decision 2, 2026-10-26, which made the leap its own rung hours after the rope), awake, in the overworld on the ground, checked every 5 s: a scripted gate opens in front
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

### 17.11 The Wilds: Tether Spires
- **The site** (`RuinKind.SPIRE`, structure `awaken:precursor_tether_spire`, set `precursor_tether_spires`: random spread 24
  chunks, separation 8): a Precursor pylon in forests, taigas, meadows, cherry groves, windswept forests and hills,
  stony peaks (`#awaken:has_structure/precursor_tether_spire`; with Awakened Lands also elderwood, lumen forest,
  shattered highlands, frost spires). Never jungle: its canopy hides the spire and leans into the climb. New chunks
  only; it is a landmark (`LandmarkKind.TETHER_SPIRE`, the last ordinal), the Scanner's and the Horizon's, and warded
  (`#awaken:warded`: blocks placed in it crumble).
- **The blueprint** (`RuinBuilder.spire`, 25 x 66 x 25): a plaza of panel 11 out (wide enough that trees rooted past it
  can't lean into the platforms) with a ring of light at 7 (the start line), brass lines, the outer cache and 1-3
  broken machines; a 3x3 column of plating with lit seams every 5 and a wider foot; five 3x3 platforms
  (`spirePetal(k)`) 5 out from the column, a sixth of a turn round and 10 higher each time (the first 9 over the
  plaza), each with a lit heart, a beam back to the column at its own level and an anchor ring under its outer edge
  (a swing, or a hang); and the crown: a 5x5 deck on the column's head at 59 over the plaza, a rim of trim with lights
  flush in its corners (nothing stands on the rim, so every edge of it can be vaulted onto), the crown block, the cache
  (`chests/precursor_spire`: essence, alloy, leads, string, pearls, wind charges, feathers; by movement one Power Core
  Shard always, then a 1000-essence cell or another shard 6 in 12) and, half the time, a terminal (Ascendant logs).
- **The climb** is the rope's ladder (§17.9): from the near rim of each floor, the next one's underside edge is a zip
  straight up (a rise of 7.45 from the hand, under 3 across), and arriving under its edge vaults you over the lip. A
  climber who stands on the far side of a platform first walks to its near rim (or swings from a ring).
- **The crown** (`awaken:spire_crown`, a block entity, unbreakable, light 14): ten times a second it watches the plaza
  (within 7 of the column, on the plaza's floor: the climb's start, the last moment you stood there) and its deck
  (within 2.6). Reaching the deck by movement (`ArrivalHooks.byMovement` and `Ascent.arrivedByMovement(p, 20)`; else
  "The crown stays dark. It answers only to a climb.") times the climb (under 2 min, else untimed), shows "Tether Spire
  climbed · 23.4 s" and a line with your best and the spire's record, gives 10 Tether mastery the first time and 2 each
  time after, counts `awaken:ascent_spires_climbed`, and keeps a faster time in the spire's records (its best five, one
  each; saved with the block, schema 1, with who has climbed it). It breathes a ring of light every 4 s.
- **The Horizon line**: a spire needs the rope (`HorizonHooks.TETHER`, met by the Tether tier); until then it is only
  ever a tease ("Tether Spire · needs Tether").
- Dev: `/awaken ascent spire goto` (the nearest spire that generated: you under its first platform); pilot `run spire`
  (from each floor, the next platform's rim cell nearest you; at the last, the deck); `tools/autopilot/ascent-spire.txt`
  (seed 20261026: the plaza to the crown in six vaults, about 10 s). Test: `ascent_tether_spire` (every step a straight
  zip from the near rim onto a vaultable edge; the crown times a climb from the plaza and keeps it, a tower gets
  nothing, the first climb is 10 mastery).
- Not yet: a race line's ghost on spires, and spires on cliffs and ravines.

### 17.12 The Wilds: windways
- **What**: streams of air between sky platforms (§17.9) that carry a glide from one deck to the next. Each platform
  is linked to its nearest within 1,100 (`Windways.MAX_LINK`); a pair has two lanes, one each way, each 10 to the
  right of the line between the centres (the two ways' tubes stay 20 apart). Never in Carillon's basin (no platform forms there).
- **A lane** (`Windways.between`): from 34 out of the first platform's centre (past its islands, 19 out), 6 under its
  deck, so a run off the deck's rim with wings open drops into it; to 5 short of the next platform's centre with the
  side step (11.2 out: over its deck's rim), 6 over its deck (its islands rise 4 over it). Decks' heights come from
  the seed alone (`SkyRig.deckY`: the structure's own random draws a seed, a turn, then the height), so a lane reaches
  platforms far beyond loaded chunks.
- **The air** (an AirHooks source, `Windways.airOf`; the glide takes it ×1.25, the carry ×1, flight ×0.6): a tube of
  radius 6; along it 0.018 b/t², up 0.022 against a glide's sink, in toward its heart 0.008 at the rim; full in the
  heart and 0.4 of it at the rim, rising from 0.15 over the first 40 blocks; over the last 64 it brakes (0.012 back),
  so the glide comes out under 0.8 b/t over the deck and settles onto it.
- **The hold** (`Windways.steer`, AscentMoves' glide): in a lane, a look within 35° of its way flies the line toward
  its heart 16 ahead, bent by 0.35 of the look's offset; a look further off breaks out (the air still pushes). So a
  glide is carried whatever its exact pitch: about 1.7 b/t (34 blocks a second), 1,000 blocks in about 30 s, set down at
  about 0.5.
- **Server**: every 5 s per player (staggered, overworld), the sky platforms within 1,400 (`RuinLocator`, 4 new terrain
  samples a scan; cached, so later scans finish the search) are linked; the lanes of pairs seen in the last 5 min are the
  server's, and the ones within 900 of a player (at most 64) go to them (`AscentPayloads.Lanes`) when that set changes.
  Nothing saved; forgotten when the server stops.
- **The look** (`WindwayRenderer`): along each lane within 280 of the camera, curls of pale mint-white air (5-11 long,
  one every 3.2 with a fifth missing) flow at 1.25 b/t, each wavering as it goes and turned to face you, fading with
  distance and at the lane's ends; a faint sheet of light down its heart shows the line from a deck's edge (gone within
  10 of you). Brighter round you while it holds you; a rush of air as it takes you, its breath every 3.5 s while it carries you.
- Dev: `/awaken ascent windway` (the lanes known, the nearest), `/awaken ascent windway goto` (onto the deck of the
  platform the nearest lane starts from, at its rim on the lane's side, facing along it; checks the deck's height
  against the seed's); `tools/autopilot/ascent-windway.txt` (Wings, the pilot's `glide` off the rim). Test:
  `ascent_windway` (the lane's ends; carried the whole way and set down slowly looking along it, 15° under or 15° over;
  a look 45° away breaks out; AirHooks hears the server's lanes).
  Tour (seed 4246): the lane took the glide 2 s after the wings opened and carried it 667 blocks in 21 s, out at 0.34
  b/t 7 over the next deck. From a deck in daylight the lane reads faintly (inside it, plainly).
- Not yet: windways round spires and other heights, weather that turns them, a ride counted toward Wings mastery.

### 17.13 The feel pass, part 1: the action camera
Approved 25 Oct 2026 (Euan: the movement should feel "really, really, really good"; first person reads flat and
vanilla's third person sits right behind you). The over-the-shoulder view follows what the body is doing.
- **The view**: Shoulder Surfing Reloaded (pack feel profile, client only; `shouldersurfing_plugin.json` names
  `body.client.feel.ShoulderSurfingPlugin`, the only class that names it). With it installed, a world starts in the
  shoulder view the first moment after you join (after the opening's shots), once a session, if you're in first person
  (`shoulderView`, feel settings, default on); first person stays one key away. The View key (the opening's, §16 decision
  10) asks `core.api.CameraHooks` first, so with Shoulder Surfing it flips first person and the shoulder view and never
  lands on vanilla's camera right behind you (before, it took every perspective press before Shoulder Surfing saw it).
- **The arm** (`body.ActionRig`, pure; `body.client.ActionCamera` reads the body once a tick): a pose from the Ascent's
  state, then the Runner's (a wall-run, a hang or climb), a slide, using an item (aim: nothing added), else the ground.
  Each pose shapes the shoulder offset (x left, y up, z back), in blocks over the shoulder view's own:
  ground: back 0.6 of the trail; slide: up -0.55, back half the trail less 0.2; wall-run: over to the open side (away
  from the wall, 1.2 times the shoulder's width), up 0.2, back 0.6 + 0.6 of the trail; hang and climb: up 0.45, back 0.5;
  rope: back 2.2 + trail, up 0.8, 0.6 of the way in behind you; zip: back 1.2 + half the trail, up 0.3, 0.3 in; the
  carry: back 1.5 + trail, up 0.4, 0.4 in; wings: back 2.6 + trail, up 0.6, 0.7 in; flight: back 3.0 + trail, up 0.5,
  0.8 in. The trail: 2 blocks per block a tick over 0.22 b/t, at most 3; never more than 5.5 back in all. A critically
  damped spring (0.22 out, 0.32 back) carries it there: it trails out, never overshoots, and is home in under 1.5 s.
  Reduced motion halves it. After Shoulder Surfing's own offset handlers (sprint, aim, flying), so its aim view holds.
- **Where you look** in Shoulder Surfing's decoupled camera (its default: the mouse turns the camera, the body faces
  where it walks): the rope aims along the camera (`ShoulderView.aim`), and in an Ascent state, or holding the rope
  key, the body takes the camera's look each tick and Shoulder Surfing's interaction state turns it to what you look
  at, so you steer a swing, a glide and flight with the view. (Found 25 Oct: the rope threw where the body faced.)
  The autopilot's look is given to the camera too.
- **Without Shoulder Surfing**: vanilla's third person (behind) takes the arm's pull-back as distance
  (`CalculateDetachedCameraDistanceEvent`); first person is never touched.
- **Stands aside** (the arm comes home): through the opening's shots (`OpeningCamera.active()`), as a Veil spirit or
  possessing, riding, spectating, or with `actionCamera` off (feel settings). Camera Overhaul still pauses in Ascent
  states (Feel Director); the Feel Director's roll and FOV and BodyCamera's first-person moves are unchanged.
- Test: `body_action_camera` (each pose's shape in order, the open side, the trail's cap, the spring trailing out
  without overshoot and coming home). Tour: `tools/autopilot/ascent-camera.txt` (PACK=feel).
- Next in the feel pass: one-button free-run (§17.14), then weight (landing dip, grab sounds and dust, wind with
  speed, footstep bob in the shoulder view, a slow beat at a big leap's apex).

### 17.14 The feel pass, part 2: the one-button free-run
Hold sprint with forward and the body reads the ground ahead and picks the move (`body.client.FreeRun`; the reading
and the pull are `RunnerRules.ahead` and `RunnerRules.bend`, both sides). The keys still do every move by hand, and the
server checks every move exactly as before (a free-run move is the same claim a key would make).
- **On the ground, sprinting** (moving within 37° of the look, over 0.12 b/t; once per 6 ticks), the column 0.45 past
  the body's front: a top 0.6-1.2 over the feet with two blocks of room over it and over the head is **vaulted** (the
  jump); a ledge 1.1-1.6 up is **mantled** (when the mantle is yours: sprint held counts as the jump); a wall taller than
  1.6 is **jumped at** when Wall Stride climbs it or its lip is a hang from the jump's height; with no floor 2 blocks
  down two ticks ahead (not water) the edge is **leapt from** (the sprint jump). A fence or wall (1.5) is crossed (§17.27), a
  step down of one is run down.
- **In the air**, sprint held counts as jump held: a wall beside you is run along (the wall-run's own rules: 1.5 off
  the ground, under 35°), a wall ahead climbed with Wall Stride; a ledge passed is caught as always.
- **Hanging**, sprint and forward held for 6 ticks climb (the ledge jump's own climb out, or up when looking up).
- **Climb lines** (`#minecraft:climbable`: Carillon's drainpipes, trellises and hoist ropes, every ladder): pressed
  against one with sprint held, the body goes up hand over hand at 0.3 a tick (a ladder's 0.2), a grip sound every
  few pulls; at the eave the lip is caught as a hang and the hold climbs out.
- **Jump magnetism**: as any jump leaves the ground (yours or the free-run's), the first lip within 4.5 ahead (a lower
  roof up to a hang over the jump's top) becomes its target; for 8 ticks the motion turns toward it at most 2.5° a tick,
  14° in all, never faster, and only while it's within 30° of the way you're going.
- Setting: `freeRun` (feel settings, default on). Dev: `/awaken body try freerun` (forward and sprint held 15 s).
  Test: `body_free_run` (a block vaulted, a wall jumped at, a fence not jumped at, an edge leapt, a step down not; the pull
  capped and only toward what's nearly ahead). Tour: `tools/autopilot/ascent-freerun.txt` (PACK=feel): with forward
  and sprint held, nothing else, the body vaulted a low wall, leapt a 3-block gap, jumped at a 2-high wall, caught its
  lip and climbed out (6 ticks after the grab), and leapt from the roof's far edge; then the rope zipped ring to ring
  in the shoulder view.
- Carillon's handhold roles (`#awaken:runner_ledges`, `runner_climbs`, `runner_lines`, `runner_soft`,
  `runner_swings`, SPEC §16.9 on carillon/main): ledges and swing points already work by their collision shapes (a
  sill, a cornice, a beam end is a lip; the rope bites anything solid: the hoist beam). Carillon's climbs are also in
  `#minecraft:climbable`, so the free-run's climb takes them. Lines without collision (runner_lines is empty yet) and
  soft landings (hay) keep vanilla's behaviour.
- Not yet: tuning against Euan's notes from his feel session; tips for the free-run (Carillon's TipHooks).

### 17.15 The feel pass, part 3: weight
- **Landings**: a fall of about two blocks or more (0.55 b/t down on the last airborne tick) outside an Ascent state
  lands in the view: the Feel Director's pitch kick (2.5° + 2.1° per b/t of fall, at most 7°, its own spring; first person
  and the shoulder view) and, in the shoulder view, the action camera's arm dropped 0.08-0.32 and sprung back. The
  Ascent's own landings keep theirs (sound, kick, a roll on hard ones).
- **Footsteps in the shoulder view**: vanilla's own bob (its view-bobbing setting, its stride and size), at 0.3 of its
  sway and 0.6 of its dip, added to the shoulder offset; first person keeps vanilla's. Off under reduced motion.
- **The hand plant**: a free-run vault slaps the top (the grab sound, higher) and puffs dust off it; a ledge caught by
  a hang already does.
- **Wind**: the rush of air follows the body's own speed too (a long fall, a fast run), not only an Ascent state's.
- **The slow beat**: a carry flying 0.7 b/t or faster, 6 or more over the ground, hangs at the top of its arc: 8 ticks
  at 0.45 of time (the same arc, slower; no steering for the beat), the wind holding its breath, once per 3 s. Big
  rope launches and the Leaper's air moves get it; never under reduced motion. Test: `ascent_slow_beat`.
- **The tactical sprint's line** (Euan, 25 Oct: "no way of knowing when your tactical sprint is recharged"):
  `body.client.SprintBar`, a 2-pixel Holo-blue (#7FC3FF) line in the free gap just above the hotbar (Aether's gold line
  sits over the XP bar, so the two never meet), filled from the middle like Aether's: it drains while the sprint
  runs, fills as it recharges (80 ticks less 10 a rank, at least 30), flashes once with a tick of light when it's
  ready (not under reduced motion), then fades; at rest, ready, nothing is drawn.
- Not yet: Euan's feel check of the whole pass (camera, free-run, weight).

### 17.16 The Wilds: the hero trees' crowns
The Elder Colossus and the Lumen Spire (Awakened Lands' hero trees, terrain.hero) each get a route and a crown, once,
when their chunks first load (`body.ascent.HeroCrowns`, like the sky platforms' rig): laid out from the tree's own
blueprint (`HeroWorldgen.blueprint`, the cached shape the tree was built from), placed into air only.
- **The crown**: on the highest cell over the trunk (within 6 of its foot), the Tether Spire's crown block (§17.11) with
  its site (`SITE_COLOSSUS`, `SITE_LUMEN`; schema 2 saves the site and the foot's height): a climb is timed from the
  tree's foot (within 22, from 1.5 under its ground to 4.5 over), reached only by moving, kept in the tree's records.
  The first time: 10 Tether mastery, a fifth of a level (discovery XP) and `awaken:ascent_crowns_reached`; "The Elder
  Colossus's crown · 23.4 s". A cache beside it (a chest on the same top or one lower; `chests/precursor_spire` for now).
- **The route**: glowing anchor rings up the trunk, each on a short chain stub 2 out from the bark: the Colossus from 18
  up every 13, the Lumen Spire from 12 up every 11, each turned round the trunk by the widest of 45°, 30°, 20°, 10°, 0
  that keeps it a zip from the last (under 0.4 of the rise across), with room to hang under it and three over it;
  past a tier or a branch it tries up to 6 higher, and it ends where the branches begin. At night the Lumen Spire's
  rings are a spiral of light to its pads (its night route); on the Colossus they carry you into the dome, whose limbs
  and masses are the swinging up to the crown. Eight seeds each: the Colossus gets 3-5 rings (to 44-70 up), the Lumen
  Spire 3-4 (to 34-45 up).
- **The ward**: placed blocks crumble in the column over the trunk (9 round the crown, from 10 over the foot to 6 over
  the crown; a `WardHooks.Source` filled as the crowns' block entities load, so it survives restarts).
- Dev: `/awaken ascent crown grow colossus|lumen [seed]` (grows the tree where you stand and crowns it).
  Test: `ascent_hero_crowns`. Tour: `tools/autopilot/ascent-crowns.txt` (both trees crowned: a Lumen Spire's crown 53 up
  over 3 rings, a Colossus's 74 up over 4; the night look of the Lumen's spiral still unseen on screen).
- Not yet: the Horizon line and landmark finds for hero trees (they are terrain structures, not ruins); the Shard
  Drift's stepping-stone course; a crown cache of its own.

### 17.17 The Ascent's tips
Carillon's one tip system (`core.api.TipHooks`, docs/carillon/m4/onboarding.md) carries the Ascent's tips; text in
`assets/awaken/tips/ascent/` (rope, leaper, wings, flight: `awake: after`; freerun: `any`), every key named by the
player's own binding. `body.ascent.AscentTips`: each tier's tip is offered as the tier is given (`Ascent.onGranted`:
the Vow's rope, the Leaper's trial, a sky core's wings, Skyborn's flight) and counted done each time its state begins
(the server sees TETHER or ZIP, GLIDE, SOAR) or a Leaper move goes off. The free-run's tip is the client's: running at
a low wall or an edge without holding sprint offers it (`FreeRun`, only while it can still teach), and a free-run move
counts it done. A gift's scene (`AscentScene`) holds every tip back while it plays. Nothing shows until Carillon
installs the system.

### 17.18 Ascension trials: the Sky Leviathan's trial (Skyborn)
Skyborn's earn path (the plan: "the Sky Archipelago Lord, rebuilt as a flight fight"); before it, a new world had none.
- **Offered** (`AscentTrials.skybornDue`, every 5 s like the Leaper's): awake, rank A or above, in the overworld on the
  ground, holding Wings and not Skyborn, Wings mastery 3+, not offered before (`AscentMemory.SKYBORN_TRIAL`). A gate opens
  20 ahead: "A gate opens onto open sky: the Sky Leviathan's trial."
- **The gate** (`Gates.skybornTrialPlan`, `Gates.openTrial`): an A-rank Sky Archipelago, one floor, marked by its boss
  (`GatePlan.SKYBORN_TRIAL`; `GatePlan.lordId()` names the theme's own Lord for the arena, insight and spawns). A real
  gate at its rank (not scripted: the story's first gate is fought at content level 0), which never breaks
  (`GateRecord.neverBreaks`) and, like the story's, always forms: on water or a build it moves to the nearest clear
  spot instead of failing (a tour's trial gate failed on water, and the offer, once spent, never came back). The floor (`FloorBuilder.skyTrialFloor`): the start, the antechamber, and the arena:
  4x4 slots of islands over the cloud sea (`SetPiece.ARENA_SKY`) at the floor's whole height (27), room to fly the
  fight. Its card and Now line say what it asks ("Fight it in the air").
- **The edge**: stepping into the arena (the boss room's entry, and its re-entry) gives everyone on the floor Skyborn
  (`TrialHooks.atEdge(p, TrialHooks.SKYBORN)`) with a short moment that plays at once: "The sky remembers you. · Open
  your wings, then jump again: fly." Then the Leviathan rises as any Lord does.
- **Done**: its fall (`Runs.lordDefeated`) completes the trial for everyone on the floor (`TrialHooks.done`): once, the
  gift's scene ("The sky is yours. · Gliding, jump to fly · forward thrusts · sprint boosts · tap left or right twice to
  roll"), 10 Skyborn mastery, a fifth of a level, `awaken:ascent_skyborn_trial`; the gate is cleared as any Lord's is.
- Dev: `/awaken ascent trial skyborn` (opens it in front of you, whatever you hold). Test: `gates_sky_trial` (the
  plan and its floor, never breaking, the edge's Skyborn and the Leviathan rising, the trial done at its fall). Tour:
  `tools/autopilot/ascent-skytrial.txt` (PACK=feel).
- **Aerial kills** (the plan's "Mastery: time aloft, aerial kills"): a hostile killed by a Skyborn player while soaring,
  gliding or carried gives 3 Skyborn mastery and `awaken:ascent_aerial_kills` (`AscentServer.onKill`; test
  `ascent_aerial_kill`). Powers cast in the air as on the ground; in the shoulder view they aim along the camera.
- Not yet: the Leviathan's own flight-fight kit (its dives and wingbeats were made for an island fight), aerial combat's
  own enemies, and casting tuned for the air.

### 17.19 The Sovereign: the god form
The ladder's top (the plan: "the angel overlord"). Euan chose its trigger on 25 Oct: its own Ascend key, default U,
needing full Aether, with a 10-minute cooldown.
- **Earned** at the story's end: any ending this player's story has reached (`StoryHooks.endingSeen`: the Break, the
  Wake or the Become; before the progression plan's decision 2 the Become never gave it), checked every 5 s; the tier with the gift's scene
  ("Sovereign. · Aether full, press U: thirty seconds as the sky's own. Press it again high up: Judgement."), its tip,
  every tier under it. Dev: `/awaken ascent grant sovereign`.
- **The form** (`body.ascent.Sovereign`, server-kept; `client.SovereignClient` draws it): the Ascend key with the tier,
  the Aether full (0.95+) and the cooldown passed (10 min, saved: `AscentMemory.sovereignAt`, schema 3; otherwise a
  toast says why: "The sky gathers itself: 4 min", "Not yet: the Aether must be full") takes the form for 30 s: the
  body lifts into flight (0.42 a tick up for its first 24 ticks, about ten blocks: a hover alone bleeds its rise away)
  that never tires (Aether pinned full on both sides), every hit you land (blows and powers)
  is struck 1.5 times as hard, and every hostile within 24 is slowed (Slowness III), loses you as its target and runs
  16 blocks away (refreshed twice a second). Lens turns gold (`awaken:sovereign`, looks_ascent.json), the action camera
  pulls far back and up (ActionRig's SOVEREIGN pose: 4.5 back, 1.4 up, in behind you), the wings stay open and a ring of
  light breathes round you; others see the wings and the aura. 5 Sovereign mastery, `awaken:ascent_sovereign_forms`.
- **The Judgement Fall**: in the form, Ascend again from 8 or more up: the body drops at 2.6 a tick (the Leaper's dive,
  harder); its landing strikes everything within 9 (20 at a landing of 1, scaled 0.6-2.5 and by distance; thrown up and
  away), the ground answers with rings of light and a low boom for everyone near. 4 Sovereign mastery.
- Test: `ascent_sovereign` (refused without the tier or the Aether; hits struck 1.5 times; the Judgement strikes; it
  ends on its clock; the cooldown holds). Tour: `tools/autopilot/ascent-sovereign.txt` (PACK=feel). Pilot `ascend`.
- The wings turn radiant gold while their bearer holds the form (your own and others' as seen: `WingLayer`, by
  `SovereignClient.radiant`).
- Not yet: slowed time for everything (only the field's hostiles are slowed), the ground shattering (the Judgement's
  mark is light, not broken blocks).

### 17.20 The Ascent's moments for quests
The Living Wilds' quests (`living.api.Quests.advance`, SPEC §21; a no-op unless a step waits on the id) hear seven of
the Ascent's moments (`body.ascent.AscentQuests`): `awaken:use/ascent_zip` (a zip that ended within 3.5 of its anchor:
a hang under it or the ledge vault, not a let-go), `awaken:discover/spire` and `awaken:discover/hero_crown` (a crown
reached by movement), `awaken:use/windway` (one glide holding a lane from its first 60 blocks to its last 30),
`awaken:use/gauntlet` (the Leaper's Gauntlet run to its far landing, every time) and `awaken:use/sky_trial` (the Sky
Leviathan fallen in its trial), and `awaken:use/ascent_glide` with its length (blocks across the ground from where the
wings opened to where the glide ended, glides of 8 or more; `Quests.advance(player, id, amount)`).

### 17.21 Roofs: climbing out onto a stair or slab eave (Euan's playtest, 2026-10-25)
Hanging from a roof's eave, the climb out (the ledge jump's, and the free-run's from the hang) lands on whatever is
walkable just past the face: the lip's own top or a step up to 0.6 over it (`RunnerRules.CLIMB_STEP`, vanilla's step),
so a stair eave puts you on its high back half and you walk on up the slope; onto a wall's coping over its middle, over a
fence's rail (§17.27); never down a drop behind a thin lip. The standing body (from that height to 1.8 over it) and the way over the lip must be
free, and the way up in front of the face from the hold to standing height. The server re-checks with the same
`climbOut`. Covered by `runner_roof_climb`: a stair eave on a 3-high wall under a stair slope, a slab eave under a
slab slope, Carillon's overhanging eave stair on a gentle and a steep gable (`CityGrammar.roof`), and a step with a
block over it (no climb).

### 17.22 Stair slide (Euan's playtest, 2026-10-25: "if I use the slide whilst I'm on a stair, it actually slides down")
A slide started on a run of stairs or stepped slabs, or one that reaches the top of a run, slides down it. No new key;
the slide's own controls (jump out, a power's motion, a push, water) end it as before.
- **A run** (`BodyRules.stairStart`): along the slide's way, at least two half-block steps down (0.25-0.6 each; a full
  block or more is an edge, a single step is just a step) ahead within 1.6 of the body's centre, nothing rising and no
  gap before them, and the run's fall line (the grid side the ground drops most to over 1.25 blocks) within 35° of the
  way. Read each tick on the line through the body's centre every 1/16 block, from 1.2 behind its rear to 1.6 ahead;
  each nose that matters is then found exactly (bisection, 1/512). Running down stairs the feet leave each step: a
  run under them counts as ground for starting a slide (client and server).
- **The glide**: the body moves along the way, then down onto the straight line through the step noses (its rear edge
  on the line: never inside a step, never a bump or a hop per step; up to 0.25 a tick back up onto it after a step
  dropped off), and vanilla's move does the vertical part (its collisions, its landing). On a stair run the drop per
  tick is exactly speed × slope. The way bends onto the fall line at up to 3° a tick. Speed tends to 0.42 b/t (a
  tactical sprint is ~0.35, a slide enters at 0.38 / 0.46) at up to 0.012 a tick: a faded slide builds, a tactical one
  eases down. No 14-tick limit while the run goes on; at most 120 ticks (6 s) of run. The ride is never a fall (fall
  distance reset on both sides each tick).
- **The bottom** (`StairProfile.last`): down the last step at the run's own slope onto the floor, then the run-out: the
  slide's last 8 ticks, its own decay from the speed it has (0.42 → ~0.27, ~2.8 blocks). A landing mid-flight is a
  bottom; the run-out slides on and, at the next flight's top, the stair slide takes over again. A turn in the run or a
  wall at its end ends it the same way (the run-out, then the wall's thud). Something in the way of the crawl box mid-
  run hands it to the run-out at once.
- **Jump out** keeps the run's speed + 0.15; on the noses (feet just off the steps) the slide jumps itself.
- **Server**: authoritative as for the slide. It reads the run itself from where it has the body (the same functions,
  the same bend): while it sees one, the slide's end moves to 16 ticks ahead (run-out 8 + grace 6 + 2), else the slide
  ends as before and the client is told. Vanilla's move check ignores the vertical difference of the client's line, so
  the glide is never corrected.
- **Look**: the third-person slide pose (or its clip) tipped forward to the slope it is going down (from the body's own
  motion, eased, up to 40°), ours and others' alike. First person: `MovementHooks.stairSliding(Player)` (both sides,
  `Body.isStairSliding`) for the animation plan to hook into.
- Test: `body_stair_slide` (six stone-brick stairs, a slide from the top: the bottom in 23 ticks at 0.38 b/t against a
  walk's 0.22, a 15-tick glide whose drop never changes by more than 0.014 a tick, the server sliding past 20 ticks
  and no fall; a way across the stairs is no run).

### 17.23 Mantling onto stair tops (Euan's play, 2026-10-25: "I can't mantle up blocks with a stair on it")
The mantle's lip is the lowest top along the face's edge across the body's width, by the real collision shapes (a
stair's low half, a slab, any kit block), within 1.1-1.6 over the feet; nothing on that edge more than a step (0.6,
`BodyRules.MANTLE_STEP`) over it. The feet land a body width past the face on whatever top is there, from the lip's
height up to a step over it, never lower (no drop behind a thin lip), never a fence or wall top (a crossing, §17.27), with 2 blocks of room
over it. So a 1-block wall topped with a stair facing away or sideways is climbed by its low half (1.5) and stood on by
its high half (2.0): a body 0.6 wide can't stand on a half 0.5 deep. A stair facing you is a 2-block face and an
upside-down one a 2-block top: not mantled (the wall-run, the hang and the climb-out take those). Client and server run
the same `findLedge`. Test: `body_mantle_stair_tops` (all four facings, the upper half, a slab-topped wall, the
server's check); `body_mantle_ledges` and `body_mantle_gate_walls` unchanged.

### 17.24 The landing slide (Euan's play, 2026-10-25: "sprinting, jump, shift while in air, slide when it touches floor")
Crouch pressed any time in the air and still held at touchdown slides from the landing (`ClientMoves`, the touchdown
tick). Needs a running jump: air speed at least 0.15 b/t having sprinted in the air, or 0.28 without the sprint flag (a
launch). Entry `BodyRules.landSlideEntry`: the slide's own (0.38, 0.46 out of a tactical sprint) or the landing's speed if
faster, plus a 0.06 kick for the landing, at most 0.6 (slide-hops can't snowball; the 20-tick cooldown holds too): a
sprint jump's ~0.32 lands into 0.44 (~4.6 blocks against a run's 4.0). The way is the jump's. A landing on a downward run
becomes a stair slide (§17.22). Not when crouch was let go before touchdown (a normal landing), not onto an edge with no
floor within 0.6 under a point 0.6 ahead (vanilla's edge-sneak stays), not in water, using an item, mid-move.
- Falls: a crouch-held landing already counts as the soft landing on the server (§6: safe fall +3, ×0.6 beyond), so a
  landing slide softens a fall exactly like the roll and never adds damage. On our own client the slide is the landing's
  look: the roll's animation and sound stay quiet. Others see the server's roll for a tick, then the slide.
- Server: as any slide, plus the sprint check takes a body that sprinted within 40 ticks (the flag can drop in the air)
  or whose last move was 0.2 b/t or more. Clips, the run's return after the slide, the Runner and free-run inputs: as
  before (the free-run's mantle still comes first).
- Test: `body_land_slide` (a sprint jump's landing speed slides farther than a run's slide, a walk's doesn't, an edge
  with a drop is no slide, the server takes a sprinted landing and refuses a body that never ran).

### 17.25 Dropping to the ledge below (Euan, 2026-10-25: "I'm on an edge already holding it, and there's one below")
In a hang, **Shift** lets go and catches the next ledge below when there is one (`RunnerRules.edgeBelow`): the same
face direction, its top 0.9-3.2 under the held one (the nearest), its face up to 1.4 out from the hanging body (a sill
or a balcony sticking out) or a little in, and the way to it free. The body goes out first when the lower face sticks
out, else down first then in, falling slow off the lip and fast into the catch (4-8 ticks by the drop), the grab's
clip and sound at the catch (first person: the hands slam onto the lower lip). **Shift + Back**, or Shift with no
ledge below, lets go as before. Lowering into a hang over an edge (Shift + forward) is also allowed when the ground
under it is near but there's a ledge below for the drop to catch (`RunnerRules.lowerWorth`), so a facade's ledges can be
worked down one by one. The server takes the drop as a ledge jump (`JUMP_DOWN`): the target edge found again near
the claimed hold, its top within the drop's band of the held one. Test: `runner_ledge_drop` (a wall's top over a sill
one out: the drop catches the sill from its own face and the server takes it; no sill, nothing to catch and the server
refuses).

### 17.26 Leaps with momentum (Euan, 2026-10-26: "it just feels like I'm basically floating up. So it doesn't feel like a jump")
Every jump out of a hold has beats: a load, a launch that slows like a throw against gravity, a moment at the top, and a
catch with weight. The body's own path (client, `RunnerMoves`; the server checks only where it lands, as before):
- **Ledge jump up and sideways** (`startLeap`): the load, the body dipping 0.12 under the hold over 2 ticks (none when
  something is under it); the launch, an ease-out (1 - (1 - s)², a constant deceleration) to the apex, 0.2 over the
  target hold up (0.35 over the higher hold sideways; the target's own height when there's no room over it), over 3
  ticks + one per block up (3 sideways), 85% of the way across by then; one tick of hang there; the fall into the hold
  over 2 ticks (3 sideways), accelerating (s²). A jump up of 2 is 10 ticks (was 10 on one smoothstep curve).
- **The catch**: the body jolts down on the hands and settles (0.08 × e^(-t/2.2) × cos(1.25 t) under the hold for 10
  ticks; 1.4× after a drop), the grab's sound a little deeper.
- **The climb out**: a 2-tick load (0.1 down) before the push up and over (10 ticks).
- **The pipe hop**: a load 0.08 down, a launch that slows to 0.12 over the 1.2, a settle (8 ticks).
- **The view** (`FeelDirector`, off with reduced motion): it sinks with the load (1.1°), lifts on the launch (2.4°, the
  frame opening 2.5%), jolts down on the catch (2.6°, a touch of shake); the jump back off a ledge and the kick off a
  pipe lift it (0.9, 0.8), the hop's launch too (0.7).
- **Sound**: a push on the launch (the wall jump's, quiet), the catch's grab.
- **Hands and figure**: first person's `fp_ledge_up` (10 ticks, played at the speed that lands its catch with the body)
  pulls down on the load, flies up past the lip, hangs, slams onto the new lip; third person's `body_ledge_up` (10
  ticks) pulls and tucks, kicks and stretches, holds at the apex, takes the catch. The kick, the swing out and the jump
  back were already thrown by real velocity.
- Tour: `tools/autopilot/anims-leaps.txt` (first person and the shoulder view).

### 17.27 Fences, gates and walls (Euan, 2026-10-26: "Make every fence and wall of every type mantleable, everywhere, the city included")
Every fence, fence gate and wall, of every wood, stone, brick and mud, vanilla's and any mod's (any collision taller than
its block), and a pane or bar row standing on a block, is movement you use: read by its real collision shape
(`body.VaultRules`, both sides), so a new kind needs nothing. AWAKEN has no fence or wall blocks of its own (the city
builds with vanilla's). Approved feel: the free-run is as before (§17.14), only faster over these.
- **A crossing** (`Move.VAULT`, appended; no new key): jump facing one, or the free-run held while running at it (moving
  within 37° of the look, as §17.14). It is what the legs would run into, above a step, within 0.6 of the body's front
  walking or 1.3 running (a running vault takes off early in its stride; the server 0.6 more), with its top 1.26-2.6
  over the feet (vanilla's jump clears 1.25): a top no wider than 0.62 across where your line crosses it (a fence 0.25,
  a wall 0.375-0.5; a slab-topped wall, 1.0, stays the mantle's ledge, §17.23), standing on something solid from a step's
  height (never a beam over open space), the whole no deeper than a rail on a block (1.1 past the body's own width),
  crossed within ~53° of square on (a fence beside you is never one).
- **What it does**, by shape and by what's beyond it:
  - a fence or gate 1.26-1.65 up (a rail narrower than 0.3) with floor beyond: running, **vaulted** (the off hand on
    the rail, the legs swung flat over to the main side; a 1.5 fence: up 4 ticks, over 3 at the run's own pace, down 5);
    walking, **hopped** (both hands, the knees tucked over; up 5, over 5, down 5);
  - a wall (a coping 0.3 wide or more): walked at, **climbed onto**, the body standing over its middle (a wall-walk);
    run at, vaulted;
  - a rail on a block (a garden wall, up to 2.6): **climbed over** in one motion (a foot up the block, the hands catch
    the rail, press, the legs swing over; 21 ticks running); a wall on a block walked at is climbed onto instead (the
    block juts out under it where a hanging body would be, so there'd be no hang to climb out of);
  - beyond, the floor must take the whole standing body, every corner within a step of the landing, from 0.6 higher to
    2.0 lower than the feet: a rail at a terrace's edge with a sliver of floor behind it, or over a drop, is **climbed
    onto** and held there, never crossed into the drop.
- **The path** (`VaultRules.Vault.at`, the same on both sides): rising fast and easing onto the top while the body's front
  stays 0.04 short of the face, the feet clearing it by 0.06; over it at about the run's own speed (0.28-0.42, walking 0.2, a
  rail on a block 0.24) with a small arc (0.08 running, 0.03 else); down beyond keeping 0.85 of that speed (0.45, or
  straight down, where only that fits), fast off the top and easing onto the floor. Up and over in the crawl box
  (`Pose.SWIMMING`, 0.6 high, forced on both sides): the eye rises only ~0.4 over a 1.5 rail, as a vaulter's does, not
  the 1.6 a standing body needs; down beyond standing again, so vanilla's camera eases the eye up as the feet drop and
  the view comes down level and lands without a dip. Every quarter tick is clear of blocks for the box it has, and the
  standing box at the end (nothing pushes the body out where it lands); the server's own move follows the same path and
  never corrects it (its box stays the crawl's to the end, never bigger than the client's). The last tick sets the
  feet into the floor (vanilla's move stops them on it). Running, the run goes on at that speed, the sprint kept.
- **The hold on top**: up onto a top, forward is held back (the keys' push cancelled) until it's let go or jump is
  pressed, so a parapet climbed at a run never walks you on off its far side; then it's yours. **Balance**: walking
  the top of a rail narrower than the body (a coping, a fence) along its run (within 45°), the body is drawn back over
  its middle, at most 0.05 a tick; crouching or turning off it is yours.
- **Edges** (§17.2's hang): a fence's, gate's or wall's top is a lip like any other. A rail standing back on what's
  under it (on a block, at a roof's edge: a fence 0.375 in, a wall 0.3125) is held from the face under it, the hands
  reaching in, up to 0.45 back (`RunnerRules.RAIL_INSET`); a fence on the floor you stand on never is (the hanging feet
  would be in that floor). From the hang the climb out stands on a wall's coping (over its middle) and goes **over** a
  fence's or gate's rail (`VaultRules.over`: up from the hold, over, down to the floor beyond, up to 3.5 under the top;
  18 ticks onto a roof 1.5 below), told to the server as a ledge jump (`JUMP_OVER`) and checked the same way.
- **Server** (`BodyServer.startVault`): the Runner's Kit, not riding, sleeping, in water, using an item or blocking, no
  other move running; the same probe from where it has the body (0.6 more reach), or from up to 3 blocks ahead of it
  along the way (a server running behind its client hasn't seen the run-up's last steps: the tour's integrated server
  ran a second behind once), must end within 1.0 (0.3 high) of the client's. A refused crossing is stopped, never put
  back: its path passes through nothing and vanilla's own move checks hold it to that. Then the crawl box and the fall forgotten each tick until the client's end or
  the path's length plus 4; 0.05 exhaustion, counted (`awaken:body_vault`), a Runner link, Flow +0.05. Others are sent
  `Seen` 19 + the kind (`VaultRules.VAULT` .. `OVER`). `Body.isMantling` (`MovementHooks.mantling`) is true through it.
- **The free-run** (§17.14) asks for a crossing before anything else on the ground; a fence or wall is no longer left
  alone. The accident lanes keep their purpose: a fence beside you is never a wall to run on, a hang or a crossing (the
  S3 course's fence lane), and a rail at foot height below you is vanilla's step, never a crossing.
- **Look**: third person (Player Animation Library), `body_vault` (a speed vault), `body_vault_hop`, `body_vault_high`,
  `body_vault_onto` (a low crouch on the top, arms out, rising), `body_vault_over` (out of the hang), `_bent` twins
  (tools/art/specs/body_moves.py): the figure lowered so its hips cross at the rail and the planted hand meets it.
  Without the library: standing (never a crawl), the arms reaching then braced, the figure lowered and rolled onto the
  planted hand. First person: the planting hands are drawn on the rail in the world (`BodyFp`, world source
  `body_moves`): up from under the frame onto the rail a hand's width to their side of your line, held there in the
  world as the body goes over it (the hand slides back under you), peeled off as you come down; the free hand over a
  running vault, empty, thrown up and out wide for balance, drawn in the world too (while the world body has an arm the
  view's own moves stand aside); a held item stays as it is. Up onto a top, the mantle's `fp_mantle`. The hands meet
  the rail as the body comes level with it, a rail on a block's at the eyes (2 ticks in: the body rises past them), a
  rail hung from at once. The view dips 16° toward a low rail as the hand meets it, so it's seen landing (the rail is
  about 45° under the eye then), rolls 4° toward the legs over a running vault and takes the landing with a 4° dip; at a
  rail on a block it looks up 12° to the catch, then down 10° onto the pressing hands (`BodyCamera`, all halved under
  reduced motion). Sound: `body.vault` (a palm on the rail, the legs' cloth whipping over it, two feet down
  running; tools/art/specs/audio_body.py), and as the hand meets the rail its own hit sound (a wooden fence's knock, a
  stone wall's) with chips and dust off its top.
- **City**: Carillon's fences and walls (the wall's parapets and crenel sills, the terraces' rails, the towers' rings,
  the stalls' posts) are crossed like any others; over the wall's or a tower's drop they're climbed onto and held, never
  crossed. The opening run's routes never cross one: its planner treats fences and walls as blocking, and its driver
  lets go of sprint 1.2 from each point, beyond a running crossing's reach of any rail next to it.
- Tests: `body_vault_rails` (a fence, a closed gate and a nether brick fence hopped and vaulted onto the floor beyond,
  never a post; a cobblestone wall climbed onto over its middle and vaulted running; a fence on a block climbed over; a
  fence over a drop climbed onto, running too; every path clear tick by tick with the eye ≤ 0.6 over the rail; the
  server takes the client's vault with the crawl box and refuses a landing that isn't there); `runner_rail_hang` (a
  fence and a wall on a roof's edge hung from the face under them; over the fence onto the roof, onto the wall's coping;
  the server takes the climb over); `body_mantle_gate_walls` (every crossing from a gate room ends inside one);
  `body_mantle_ledges`, `body_free_run` and `runner_course_probes` keep their fence lanes (no ledge, nothing to jump at,
  no hang). Tour: `tools/autopilot/fence-mantle.txt`.

### 17.28 The grip on a climb line (Hands P1; Euan, 2026-10-25: "you just float up")
A Runner move (`Move.GRIP`, appended; `GripRules`, client `body.client.GripMoves`) on every `#awaken:runner_climbs`
block: Carillon's drainpipes, trellises, hoist ropes and hanging ladders, and every ladder. Approved controls (decision
A of the Hands plan): "Shift takes you down, Jump takes you off".
- **Taking a line**: walking into it facing it (within 70° of its wall; a free rope from any side), or in the air with
  forward or the free-run held (a swing out to the side catches the next line without). The body stands centred in
  front of the line, 0.02 off its face (a rope: from the side it's on). A fall caught keeps its speed as a short slide
  that brakes into the hold.
- **On it**: no keys holds still (vanilla drifts down); forward climbs at 0.2 b/t, 0.3 with the free-run held (the
  free-run's pace, unchanged); Shift held slides, +0.06 a tick to 0.65 b/t; letting go of Shift brakes to a hold over 4
  ticks; Jump alone lunges 1.2 up the line (8 ticks, once per 12: a load 0.08 down, a launch that slows like a throw to
  0.12 over, a settle; §17.26); Jump + back kicks off the wall (0.5 out, 0.42 up,
  turning to face out over 5 ticks: the ledge back-jump's numbers); Jump + left/right swings out sideways (0.42 along,
  0.12 out, 0.36 up). The foot of the line lands you (no fall damage at any slide speed); with no keys at the foot you
  step off. **The top** always leads somewhere (Euan, 2026-10-25: "the last block doesn't let me through"): it is
  reached when there's no line where the hands would go (1.75 over the feet) or the head meets something over it (a
  cornice, an eave, a coping row). The body stands off the column where it sticks out most (a drainpipe's head is 2 px
  deeper than its pipe; off the pipe alone the climb ran into the head and stopped a block short: Euan, 2026-10-26).
  With forward held (or Jump): (1) a lip over the head, its top 1.6-3.2 over the feet, from right in front out to 1.5
  out, with the way out level and then up to the hold free, the nearest one the hang can climb out from
  (`GripRules.topLip`): the line's own head or a wall's top in front (a flat roof's balustrade gap, a terrace's
  parapet gap, the city wall's walk), or an eave or cornice it runs up under (block, stair or slab), is a hang (out,
  then up, 7 ticks, `HANG_EAVE`), and the hang's own climb out (§17.21) puts you on top; (2) else, open air over the
  head hands the body to vanilla's climb, whose eave is caught as a hang as before (§17.14); (3) else the hands hold
  still at the top (no climb, no jitter) until forward is let go. A jump at the top always does something: the lip, or
  a kick off the wall. The server takes the eave hang only from a grip held or ended within 6 ticks, with the same
  `topLip` from where it has the body (plus its tolerance). A screen open holds the grip. The rope key still cuts in.
- **Motion**: the grip moves the body itself (`move`), its velocity zeroed, so vanilla's ladder cap (0.15 down) doesn't
  hold back the slide on any line, vanilla's ladder included; sideways it eases onto the line at most 0.15 a tick.
- **Server** (`RunnerServer.grip`): the first Run takes a line in the claimed column where the server saw the body
  lately (flags HOLD, CLIMB, CLIMB_FAST); later Runs change what others see (SLIDE, HOP) or leave the line (KICK,
  SIDE_L, SIDE_R, LAND); while gripping its copy's motion is zeroed after its tick, fall distance reset, hunger at half a
  hang's; every 4 ticks the column must still hold a line within the body's reach, 3 misses end it (`Refuse`).
- **Look**: third person, `body_pipe_hold`, `body_pipe_climb` (a pair of pulls every 8 ticks: 1.6 blocks at 0.2),
  `body_pipe_climb_fast` (6 ticks), `body_pipe_slide` (loops with `_end`), `body_pipe_kick`, `body_pipe_side_l/_r`,
  `body_pipe_hop`, `body_pipe_land` (tools/art/specs/body_moves.py); the figure faces the line and leans into it (back
  off it in a slide). First person: both hands closed round the line in the world (§24, `body.client.GripView`; P1.1):
  each fist closed on its own side of the pipe, drawn at 0.85; the holds at the eyes +0.04 and -0.12 (the eyes are about
  0.3 from the pipe: both fists in the frame looking ahead, their arms rising from below). Climbing, a hand stays where
  it gripped while you rise past it (to -0.2), then lets go and reaches up past the other for a new hold (hand over
  hand); held still, look round them; sliding, both run down the line with you, shaking with the friction (0.018 up and
  down, 0.006 across at full speed); look down to see your feet clamped on it. The top-out: both hands go up onto the
  top of the line, stay there 12 ticks while vanilla's climb carries you up past them, and fade.
  Leaving: view clips `fp_pipe_kick`, `fp_pipe_side_l/_r`, `fp_pipe_land`. Sound: the grip's grab, `body.pipe_slide`
  (palms hissing on the line, every 8 ticks of a slide), `body.pipe_brake`; the line's own grit off the hands while
  sliding, sparks off a metal line at speed; a faint judder in the view.
- Tests: `runner_pipe_grip`; `runner_pipe_cornice` (a drainpipe under a one-block cornice with a roof behind: the lip is
  the way on, hung from its outer face, taken by the server only from the grip, climbed out onto the roof; a deep
  ceiling and open air give no lip). `runner_pipe_tops` (every kind of top Carillon paints, each climbed on a real drainpipe
  with its head from the street: a flat roof with its balustrade open at the pipe, a gable eave with the head in the
  eave stairs' row, block, upside-down stair and slab cornices, a retaining wall's parapet gap, the city wall's inner
  face with its corbel table, band and parapet gap: the climb reaches the top, there's a lip, and the hang climbs out on
  top). Tour: `tools/autopilot/anims-pipe.txt` (PACK=feel).

### 17.29 Locks that hold and speak (the progression plan §5.5, Phase 3; Euan approved it 2026-10-26)
Euan: "If I was someone who didn't know about the grapple, I'd just look at that and use blocks to build up. So we
should probably block that." · "I don't want a guidebook... you still get guided somehow." Name the need, never the
manual; stop the build at the first block; the payoff answers only the line; danger stays the gate for terrain (never a
"you need a higher level" text, which a stack of dirt disproves).
- **Climbs refuse, they don't crumble** (`wilds.ward.ClimbWard`; ward kinds `precursor_tether_spire`,
  `precursor_sky_platform`, `hero_crown`, `Wards.climb`). A survival player's block placement (at HIGH priority, so the
  arrival tracker never logs it) and a fluid bucket are cancelled at the first block; the ghost of the block falls away
  (`ward_fx` SHED: its twelve edges traced in the Signal's light, which hangs a moment, and in grains of its own colour
  that slip off and fall, a breath of light rising; the crumble's sound pitched down ×0.72). The 3 s crumble stays at walk-in sites (vaults, monoliths, labs). No Hearth suspends a climb's
  ward or its walls (`Wards.enforced`, `Wards.sealed`).
- **The place answers**, worded by what the player holds (`HorizonHooks.can(p, "tether")`), lang `awaken.wilds.climb.*`:
  without the rope, "The spire sheds what you build. Its platforms answer to a line." / "The platform sheds what you
  build. Its rings climb to it by rope." / "The great tree sheds what you build. Its crown answers to a line."; with it,
  "Climb it by rope: hold <the player's own rope key> at a ring." (a platform: "at the lowest ring"). Each line also
  comes in two halves (`.sheds`, `.way`; the tip `.short`), because a card's question and a toast's detail hold about 28
  letters a line (`SignalLayout.CARD_W` 170, `TOAST_W` 160).
  - The first refusal at each kind of climb, without the rope (flag `awaken:climb_shown/<spire|sky|tree>`): a choice
    card, the question on one line so its body shows ("The spire sheds all you build." / "Nothing you build holds
    here." / "The tree sheds all you build.": a card holds 20% of the screen, and a two-line question drops the body),
    the body "Its platforms answer to a line." (the platform's, the tree's way), one choice
    **Show me** (Y) / Not now (action `ward:show_me:<kind>`, 30 s), with "A vault keeps a line for a hunter it opens to."
    on the action bar under it. Y sends `awaken:ward_show_me`; the client opens the band (K › Powers, §30) scrolled to "THE
    ASCENT" (§27.2: the next rung as a silhouette, where it comes from), else "THE BODY"
    (`StatusScreen(Tab, String... titles)`).
  - After that: once a visit (a ward, until 5 minutes without a refusal there) a toast, the place's name and its way up
    (or the short tip), sent untagged so the director shows it at once as the answer to the click (§26.2), and the
    whole line on the action bar; further refusals in the visit: the shed and the action-bar line, at most every 3 s.
  - Without the rope each refusal records a promise (§27.1): key `landmark:<ward id>` (`landmark:ruin:x,z`,
    `landmark:hero/<crown>`), need `tier:tether`, "A Tether Spire" / "A sky platform" / "A great tree's crown". Kept
    when the climb is made: a spire's or great tree's crown (§27.1), or a climb cache's inner seal opening.
- **The payoff answers only the line** (`ArrivalHooks.byLine`: by movement, and a LINE sample in the visit or the 20 s
  before it, nothing built): a spire's or great tree's crown, a sky platform's Wings core, a climb cache's best roll.
  A clever climb (pearls, elytra, a power's flight, a Runner hang) still earns the view and the ordinary roll; the
  crown says "The crown stays dark. It answers only to a line."; the core "The core stays dark. It answers only to a
  line up its rings." (at once, then at most every 10 s). The Tether Vow is kept only at the mast by the line.
- **A climb cache's inner seal** (`wilds.ward.Caches`, attachment `awaken:inner_seal` on the chest: schema 1, the best
  table, who it opened to). A spire's, sky platform's or hero crown's chest keeps its ordinary roll in its own table
  (`chests/precursor_spire`, `chests/precursor_sky`) and its best roll in `…_best` (spire: 1 Power Core Shard + a
  1000-essence cell or a shard or nothing; sky: 2 shards + a cell, Jet Boots or nothing). Built arrival: the cache
  stays shut ("The cache's inner seal holds. It opens to a climb.", the hold light). Any other arrival: the ordinary
  roll unpacks (shared, once) and the seal is stamped; the best roll is filled into the chest for each climber the line
  carried, once each ("The inner seal opens to your climb.", a ring of light off the lid, the mark's glass low).
  Without the line the seal's line is said instead. Nobody's open spends anyone's best roll; the ward keeps the cache
  from breaks and blasts. Vault, monolith and lab chests keep `awaken:arrived_by_movement`.
- **Relog keeps the verdict**: the open visit (`wilds.arrival.Visit`, attachment `awaken:ward_visit`, schema 1) is saved at
  logout and resumed at login if under 10 minutes old (game time) and the player is still in that ward.
- **The hero trees**: the crown's ward covers the canopy (radius 32 round a Colossus's crown, 16 round a Lumen Spire's,
  from 10 over the foot to 6 over the crown; radii from the progression builder's draft), and each crowned tree
  within 640 blocks is on the Horizon line, "Elder Colossus · a light in its crown" / "Lumen Spire · …", need the
  Tether, id `landmark:hero/<crown>`, until the player has reached its crown.
- **Walls speak per site** (`awaken.wilds.ward.hold.*`): only a vault's say "These walls answer only to their door."; a
  spire "The spire keeps its stones.", a platform "…its plates.", a tree "The crown keeps its own.", a monolith or lab
  "The ruin keeps its walls."
- **Sealed doors show their rank from afar**: a door's panels carry its rank (`precursor_door` `rank` 0-6, 7 = not
  painted yet; the core paints its panels once each load, so older doors light up), and the panels' seams and the
  core's rune and ring are an emissive layer (`light_emission` 15, `precursor_door_glow`, `precursor_door_core_glow`)
  tinted with the rank's colour (`above.client.DoorTint`): E white, D green, C blue, B violet, A amber, S red. Within 24
  blocks the core also breathes four of the Ascent's motes in that colour every 2 s (`AscentFxBridge.doorBreath`,
  from the progression builder's draft).
- **Gate cards compare ranks** (§7).
- Tests: `wilds_ward_refuses_climb`, `wilds_arrival_survives_relog`, `wilds_cache_inner_seal` (and `wilds_ward_rules`,
  `wilds_arrival_history`, `progression_promises`).

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
- Defaults (decided 2026-10-24 from those numbers; 4K added 2026-10-25, Euan): `renderScale` 1.0 (off) on screens under
  4K, `renderScale4k` 0.75 on a game frame of 8 MP or more (the Dell at 3840×2160 is 8.3 MP; a 16" MacBook at
  3456×2234 is 7.7), `upscaler` SHARP. 1.0 on the MacBook's own screen (about 1 ms, and nothing once a scene sits at
  120 Hz or on the CPU, doesn't pay for the softer textures); at 4K Lens 2 and the world's pixel-bound part cost the
  most (estimated 3–6 ms saved on Cinematic/Ultra, docs/perf/CITY-FPS.md). The autopilot's `pixels <scale>` sets both.
- **Steady** (`PixelsSteady`, `core/pixels_steady.fsh`; 2026-10-26, Euan: distant edges, far trees and mountains
  crawl as the camera breathes, red dots flicker): a temporal stabiliser before the upscale, at the world's resolution.
  Each frame last frame's steady image is carried to where it now lies (this frame's NDC through the inverse
  view-projection, the camera's step since, last frame's view-projection: one matrix; the projection as Lens reads it,
  bob and hurt tilt included), sampled Catmull-Rom (five bilinear taps), clamped to this frame's pixel and its four
  neighbours (a moving mob or a newly seen place never smears), and this frame mixed in at 0.1. No jitter: it steadies,
  it adds no resolution. The hand (its own depth target) keeps no history; a teleport (24 blocks), a gap over 250 ms,
  a resize or a frame without the pass starts it afresh. History: two RGB10A2 targets (a tenth's steps round away in
  8 bits). Then the upscaler as before (SHARP's RCAS now sharpens a steady image); at native the world draws into a
  same-size target and is copied out unsharpened. Needs a post effect (Lens): without one the game's hand pass clears
  the main depth before Pixels' end hook, so the pass steps aside (`getRequestedPostEffects()` empty). Setting
  `steady` (default on); autopilot `pixels steady on|off`.
  Attribution first (tour `farsight3-shimmer.txt`, `tools/farsight/shimmer.py`: night over Carillon's east wall,
  12-frame bursts under a scripted sway, fine flicker / hard-flicker share in the far, mid and near bands): SHARP
  roughly quadrupled hard flicker against bilinear (far 0.42% vs 0.10%); Far Sight's far land and trees were most of
  the far band (Far Sight off: 0.04%), City Sight added some in the middle band, the near fields' crop rows (Sodium)
  flickered too, and native was worse than 0.75. The red dots (not in the tour's view): most likely the pale biome's
  one-block crystal spires, real blocks about two pixels wide, tinted by the moonlight; the pass steadies them too. Proof (tour `farsight3-steady.txt`, M4 at 2880×1800, Vulkan, RD 16, Lens
  Cinematic; `docs/perf/far-sight/fs3-steady.jpg`): under a slow continuous turn (as Camera Overhaul's breathing
  moves the camera every frame) hard-flicker pixels 0.064% → 0.010% at 0.75 SHARP and 0.170% → 0.012% at native;
  under the old tour's one-tick tp steps (a jump, then still frames) 0.106% → 0.086% and 0.351% → 0.235%. GPU: the
  upscale 0.27 → 0.64 ms at 0.75 (world 2160×1350), steady plus the copy 0.84 ms at native. A first cut at the
  screen's resolution with RCAS after cost 1.5 ms and sharpened native: dropped.
- Holo: panels draw inside the world (the level's always-on-top phase), so at render scale their text would be
  upscaled with it (measured: softer, with RCAS fringes at 0.67). While a panel holds the player's attention
  (`UiHooks.focus() > 0`: the K menu, a reading screen) the world renders native, so panel text, Lens's bloom on the
  glow and its focus dimming stay exactly as without Pixels; the low-resolution targets are kept for after. Toasts,
  cards and the HUD are GUI and always native.
- Safety: both hooks must be seen in one frame before the first swap; a frame that ends with the target still lent
  (`RenderFrameEvent.Post`) gets it back and turns Pixels off for the session, as does any exception in a pass.
- Client config `awaken-pixels-client.toml`: `renderScale` 0.5..1.0 (1.0 = native, off), `upscaler` SHARP |
  FSR | BILINEAR and `steady` (on). Autopilot `pixels <scale> [sharp|fsr|bilinear] | steady on|off | config | state`
  overrides them for a tour (not saved). Tours:
  `tools/autopilot/pixels.txt` (the measurement), `pixels-smoke.txt`, `pixels-dh.txt`; crops: `tools/pixels-crops.py`.
  `pixels gpu on|reset|<label>` (measurement only) writes GPU timestamps before the world, after the post effects and
  after the upscale, and logs the world pass's and the upscale's GPU time per label (`PixelsTiming`): on a busy Mac
  the frame time is the CPU's, so the GPU time is what shows what Pixels saves.

---------------------------------------------------------------------------------------------------------------------

## 21. The Living Wilds (plan docs/living-wilds/PLAN.md, page https://claude.ai/artifact/C97MPmz9gBFJMMjL7Eu8hT; approved 2026-10-25)

Ordinary survival in a world that's always up to something. Euan approved page v3 on 2026-10-25 with "Go with your
recommendations" (all nineteen decisions below). PLAN.md is the long form; this section is what builders must hold to.
Module `living` (package `dev.awaken.living`, same rules as §1): `region`, `combat` (+`client`), `wayfarer`, `quest`,
`bounty`, `pulse`, `population`, `camp`, `craft`, `wonder`, `village`. Phases in order, one builder at a time:
**B** combat layer, regions, XP · **C** Wayfarers, quests, bounties, rumours · **D** the World Pulse, happenings, living
creatures, camps · **E** crafts · **F** wonders, secrets, legends · **G** settlers, your village, roads.

### 21.0 Decisions (approved)
1. Regions replace follow-you scaling for every overworld creature (fauna and vanilla monsters); distance only tilts
   the odds (21.2).
2. A Hearth calms its land: within its radius + 96 blocks the level is min(region, the household's highest level).
3. XP: every creature pays by its own level and role (a sheep too); crafts, quests, bounties and camps pay; gate floor
   rewards ×0.80 (`Curve.WILDS_TRIM`); the balanced player's length holds (sim, 21.3).
4. Plates, damage numbers and the XP stream are on by default (client settings to reduce or turn off).
5. Level-up is a System moment, compact in a fight, seen by players nearby (never their pane).
6. Elites in the wild: 5% of neutral and hunter spawns.
7. The hard rule "Never a quest list" is amended: the Journal lists only chains the player found and took (≤ 5 active,
   1 tracked); undiscovered things are still never listed; nothing floats over anyone's head.
8. Twenty hand-written chains in two drops (levels 1-20 first); a relic per chain, bound to its player, kept on death.
9. Association bounties are the first slice of Carillon's deferred contracts board (3 notices; agreed with Carillon).
10. Creatures live in a ledger (herds, packs, roosts, camps); ignored nests raid with a play-day's warning.
11. Six crafts (Hunting, Foraging, Mining, Fishing, Smithing, Cooking), levels 1-50, perks, a Crafts tab.
12. Jade hides its creature lines while plates show them.
13. Game rule `awaken:pure_survival` (off): no plates, numbers, XP stream, quests or bounties; regions and crafts stay.
14. First hour (Carillon worlds): the Director's first gate opens at level 4 or after 45 minutes outside; Quartermaster
    Hale at the High Gate and a Wayfarer camp in the Commons (city parts agreed with Carillon).
15. The Nether and the End keep vanilla danger for now.
16. The World Pulse schedules every open-world happening (about 28 kinds on the moon calendar, missable, today's
    anomalies folded in); the Hilltop Test (PLAN "The feeling") is the bar.
17. Wonders (one per region, ~40 kinds), three secrets per region, eight legendary beasts (fall for good), treasure maps.
18. Settlers at your Hearth, a named village on the Atlas, road markers that caravans travel (agreed with hearth and
    Carillon owners).
19. Rumours mark a region "?" on the Atlas; the first witnessing of each happening kind is a discovery (the Almanac).

### 21.1 The Holo combat layer (Phase B)
- Plates: the creature under the crosshair (24 blocks; 32 at PER 20, 40 at PER 40) and every creature in a fight with
  the player (hurt either way in the last 6 s), plus elites and named beasts once seen; ≤ 4 at once, nearest threats
  first; fade 3 s after the fight, 0.6 s after looking away from a calm one. Content: name (or "Unknown" before the
  Codex has it), level chip in its band's rank colour (E 1-9, D 10-24, C 25-44, B 45-69, A 70-99, S 100+; grey 10+
  below the player, ember with a skull 5+ above), 10 glass segments (lost ones flash amber and drain over 300 ms),
  ELITE / beast title / CAMP tags, a rim warm for hostile, cyan for neutral, none for passive. No plates on players,
  Echoes, citizens or Wayfarers. Same in gates. Rules are pure (`living.combat.PlateRules`).
- Damage glyphs: numbers in strokes of light at the hit point, 700 ms, coloured by Force; crits ×1.6 in gold with a
  fracture flare; hits within 6 ticks on one target merge; damage taken is not printed.
- XP stream: on a kill, motes rise from the body and stream to the band (first person: the HUD's level line; third
  person: the left wrist) with "+N XP"; other XP streams from its source, smaller; grants within 1.5 s merge. Shown XP
  is the XP actually added. "Worn": once per source per 10 minutes past its budget, a toast says that source pays less.
- Level moment: band flare, a brief Lens look (`living:level` in `assets/awaken/lens/looks_living.json`), two ground
  rings and a light column (own particles), a pane typed in light ("LEVEL n · +3 STAT POINTS" and the rank line),
  sound `living.level_up`, ~2.4 s. In combat (hurt or hitting in the last 5 s) the server sends the old toast instead
  and the moment waits for the fight to end. Others within 48 blocks see rings and column.
- Loot pillars: drops of rarity RARE or EPIC (or `#awaken:loot_pillar`) from a creature's death stand in a pillar of
  their colour (rare blue, epic violet; Power Cores white-gold) visible 48 blocks until picked up.
- Client settings `awaken-living-client.toml`: `plates` (on), `damageNumbers` FULL|CRITS|OFF, `xpStream` (on),
  `levelMoment` FULL|COMPACT; `reducedMotion` from the Holo settings applies.
- Plates draw on the Holo's own glass through `HoloWorld` (§12; `living.client.LivingPlates`): name, the level on a
  chip in its rank's colour (grey ten or more below you, ember five or more above, pulsing), a named beast's title, the
  ten-segment bar with its amber drain, the panels' scan line when the band first reads a creature; readable at any
  distance. Off with reduced motion and in pure survival; LivingFx's fallback glass only when the holo shaders failed.

### 21.2 Regions (Phase B)
- A patchwork of named regions 600-1,400 blocks across: hearts on a jittered grid (pitch 900, jitter ±35%), borders
  from the nearest heart after a domain warp (amplitude 220, wavelength 700), seeded from the world seed.
- The Commons: the region round Carillon's site (`CityBasin.site`) or, in classic worlds, world spawn; everything
  within 700 of that centre belongs to it; levels 1-8. Its neighbours (regions whose heart is within 1,600 of the
  centre) draw only bands 1-8 (40%) and 8-20 (60%).
- Odds by heart distance from the centre (bands 1-8 / 8-20 / 20-45 / 45-80 / 80+): under 2,000: 25/45/25/5/0;
  2,000-5,000: 15/30/30/20/5; beyond: 10/20/25/25/20. Bands are dealt from a seeded shuffle per 3×3 block of grid
  cells so each block holds the spread. Biome tilt at the heart: harsh (pale garden, badlands, jagged and frozen peaks,
  dark forest) one band up, gentle (plains, sunflower plains, meadow, flower forest, cherry grove, mushroom fields)
  one band down, each 40%. A region's span is 6 levels inside its band (80+: up to 150), rising edge to heart.
  The deep dark adds 10.
- Creature level = region level + role offset (ambient −2, neutral 0, hunter +1, elite +2, camp heart +3, beast at
  its lair +3), clamped 1-150. Fauna use it (FaunaRules); vanilla monsters get it at spawn with the same scaling
  (MobScaling); passive vanilla animals get a level for their plate and XP but no scaling.
- Names from biome grammars (`living.region.Names`); the Commons is fixed. Entering a region: a place caption once per
  region per play-day; ≥ 5 above the player, an ember caption and an amber toast at most once per 10 minutes.
- API `dev.awaken.living.api.Regions`: `levelAt(ServerLevel, x, z)`, `regionAt(ServerLevel, x, z)` (`Region`: id, name,
  band low/high, heart), `creatureLevel(ServerLevel, BlockPos, Role)`, `bandOf(level)`. Pure apart from a per-server
  cache of region records; thread-safe.

### 21.3 XP (Phase B)
- Kills (overworld, any living non-player creature with a level): fraction of a level = 0.03 × role (hunter 1.0,
  neutral 0.5, ambient or passive 0.15, elite 4.0, warden 6, beast 0 here: beasts pay by their own first-kill rules)
  through the budget `hunt` (0.10 per 10 minutes), then × (1 + 0.08 × min(5, levels above)) or × 1 / (1 + levels
  below / 8). Paid via `Progression.worldXp(player, "hunt", raw, after)`; the old home kill XP is off while the
  living module pays (`LivingHooks`). Party sharing as before.
- Budgets registered by modules: `XpBudget.register(source, perTenMinutes)`.
- Gate floor rewards × `Curve.WILDS_TRIM` (0.80; everything priced from a floor reward follows: anomalies, camps).
  Hunting, crafts and camps pay × `LivingModule.wildsRank(rank)` after the budget: 1.0, except C 0.55 (C is the long
  climb in gates too). tools/sim/pacing_sim.py carries the same model; balanced player: level 5 0.98 h, 10 3.9 h,
  25 16.8 h, 50 51.2 h (the v2 target 55-60 moved to 50-60: the open world now pays through D and C), 135 101.8 h;
  main power level 5 at player level 48; gate checks unchanged.
- `Progression.onXpGranted(listener)`: every grant after scaling (player, XP, source, world key), for the stream.

### 21.4 Phase B as built (2026-10-25)
- Server: `living.region` (RegionMap, Names, RegionService saved data `awaken:living_regions`, RegionTracker),
  `living.combat` (Creatures, KillXp, Hits, XpStream, LevelMoments, LootPillars, PlateRules), payloads
  `living_region`, `living_hit_fx` (also a hit the player took, which marks that creature as fighting),
  `living_xp_fx`, `living_level_up`, `living_shown`; attachments `awaken:creature_tag` (core.api, synced),
  `awaken:loot_pillar` (synced, not saved); game rule `awaken:pure_survival`.
- Client (`living.client`): LivingClient (plate picking by PlateRules each tick, a 24-block ray with a wall check,
  four line-of-sight checks a tick for notables, pillars every 5 ticks, the level moment waiting for a fight to end:
  a hit either way in the last 5 s, at most 30 s), LivingFx (one additive mesh pass and one glass pass per frame,
  plates' words as text with a four-copy halo), LivingHud (region caption, level pane through HudLook, XP pops by the
  level line), StrokeFont (digits and "+XP LV" as light). Jade drops its creature level row while plates show.
- Plates draw through `HoloWorld` since Phase C (§21.1); LivingFx keeps a fallback for failed holo shaders.
- Sounds `living.scan|crit|xp_tick|level_up|region_enter` (tools/art/specs/audio_living.py, UI loudness); Lens look
  `awaken:living_level` (assets/awaken/lens/looks_living.json); tips `awaken:living/first_plate`, `first_region`
  (assets/awaken/tips/living/, read once Carillon's tip system is installed).
- Dev: `/awaken living region` (anyone), admin `regions [radius]`, `tag`, `strike [crit|kill]`, `pillar`, `moment`.
  Tour `tools/autopilot/living-b.txt`.

### 21.5 Wayfarers, quests and bounties (Phase C, as built 2026-10-25)
- **Chains** (`living.quest`): data `data/<ns>/awaken/quest/<chain>.json`, schema 1: `title`, `band` (0..4),
  `requires` (progression tokens), `scene` (`resident` for a fixed giver, else the scene kind it's found in: `cliff`,
  `road`, `crag`), `giver`, `ask` (the giver's lines before the choice card), `summary`, `steps[]`, `relic`, `finish`
  (progression `Reward`). A step: `id`, `objective` {`type` talk|reach|kill|collect|deliver|use|discover, `target`,
  `count`, `radius`}, `text` (its Journal line), `start`/`done` lines, `reward`, `needs` + `needs_text` (a step waits for
  it; `tier:<need>` asks the Horizon's reach answers, so the Ascent's tiers and movement powers count), `spawn`
  {`entity`, `count`, `at` marker} (brought when the step starts), `move` (the scene's Wayfarer goes to that marker when
  the step is done). Targets: `reach` names a scene marker (`scene`, `site`, `foot`); `deliver` is `<who>|<item or #tag>`;
  `*` ends a prefix; a reach target may also be `spot:<id>` (Carillon's `CityHooks.spot`) or `camp`. Lines have no em dashes (the loader skips a chain with one).
- State: attachment `awaken:quests` (schema 1, kept through death, synced to its owner): ≤ 5 active (chain, step,
  progress, scene id), one tracked, done, relics given. Every step reward and finish pays once (claim counters
  `awaken:claim/quest/<chain>/<step|finish>`); counters `awaken:quests_done`. `Quests.advance(player, "awaken:<type>/<target>")`
  (§12) is how other modules report uses and discoveries.
- **Scenes** (`living.wayfarer.Scenes`, saved data `awaken:living_scenes`, schema 1). Residents are placed once when a
  player first comes within 96 blocks: Ilse and Hettie at the Wayfarer camp (Carillon worlds: 450 north of the city's
  centre, up the High Gate road; classic worlds 300 north of spawn; on the nearest dry, level ground, found once and
  kept; the camp round Ilse's fire, its safe ground and Ilse's scene: §27.6), Hale on Carillon's `CityHooks.spot("quartermaster")` by the High Gate yard (classic: at the camp). A found scene: per player every 25 minutes outdoors (the first after 12), never inside a Hearth's radius or the
  city reserve, for the lowest-band chain they can take that isn't a resident's; a spot 64-160 blocks off in loaded
  chunks only (cliff: a top with a 6+ drop beside it, its foot marked; crag: y 100+; road/camp: flat ground), plus a
  `site` 40-80 from it (plain ground 56 off if the land offers none). An untaken scene fades after two play-days; a
  finished chain's scene goes and its Wayfarer leaves once nobody is within 16. The Horizon line names an open scene
  ("Wren, stranded"; a cliff scene needs the Tether), and it sends up a distress flare (the Hilltop Test): 20 s in every
  90, rising 48 blocks, then drifting down; drawn by the client from payload `living_tells` (the player's untaken
  scenes within 400 blocks, every 5 s), its core unfogged and a few pixels wide at any distance.
- **Wayfarer** entity (`awaken:wayfarer`): a player-shaped person in their own skin
  (`textures/entity/wayfarer/<who>.png`, tools/art/specs/wayfarers.py), never hurt, never despawns. Use to talk: a step
  waiting on them moves on (or says what it needs), a delivery changes hands, Hale reads the bounty board, a chain they
  give is offered on a choice card (Help / Not now), else one of their idle lines. People: Wren, Hettie, Ilse, Gus,
  Pell, Hale, Marta (`Wayfarers.PEOPLE`).
- **The first drop, all eight chains** (The Last Delivery is written and waits for Carillon's B3b to call
  `Quests.talk` for Kestrel; it opens on `counter:awaken:carillon_registered`): Ten Sheep Short (Hettie, 1-8), Moth Lights (Ilse, 1-8), Wren's Rope (cliff,
  8-20), The Caravan Road (Marta, road, 8-20), The Hermit's Horn (Gus, crag, 8-20), Pell's Message (Pell, road, 8-20).
  Oren's First Steel (Oren in Carillon via `Quests.talk`, ore, coal, a temper reported by hearth's
  `Upgrades.offerTemper` as `awaken:use/temper`) also waits for B3b.
- **Relics** (`living.quest.Relics`): stack of one, rare, kept through death (held back from the drops, returned on
  respawn), a perk while carried (`Progression.addPerkSource`): Shepherd's Lantern `awaken:night_ward` 8 (living reads
  it: at night hunters and monsters within 8 give up and back off; gate creatures, wardens and beasts don't), Wren's
  Hook `awaken:rope_reach` 6, Oren's Mark `awaken:temper_start` 1 (hearth: a fresh, untempered weapon starts at 1 + perk levels within the Forge's
  cap, at that level's price), Courier's Knot `awaken:sprint_recharge` 0.25 (the Ascent takes it off the rank's tactical-sprint
  recharge, perks capped at 0.5, never under S's 30 ticks), Caravan Writ `awaken:shop_discount` 0.15, Pell's Spurs `awaken:mount_sprint` 0.15 and
  `awaken:whistle_range` 80 (each read by its owning module when it adopts it). Used: Lumen Tonic (Night Vision 2 min,
  refills in 5), Herdcaller Horn (once a play-day: whatever targets you within 24 stops and slows; passive and neutral
  creatures within 32 run 24 blocks the way you face). Pell's Letter is an errand item.
- **Bounties** (`living.bounty`): data `data/<ns>/awaken/bounty/*.json`, schema 1 (`title`, `band`, `objective` kill or
  collect, `levels`, `essence`, `requires`, `text`). Attachment `awaken:bounties` (schema 1, kept through death, synced):
  three notices for the player's band; a taken notice is replaced the next play-day; at most three taken; no expiry.
  Read and turned in at Hale (choice card Take it / Next notice); a turn-in pays once (claim
  `awaken:claim/bounty/<id>/<n>`), counter `awaken:bounties_done`.
- **Journal**: payload `living_journal` (chains taken, tracked first, and bounties taken, each with its current line),
  sent on every change and on login; drawn as the JOURNAL · CHAINS section of the band's Journal page (§30).
  Nothing undiscovered is ever listed (§21.0.7).
- Tests: `living_quest_engine`, `living_bounty_board`, `living_scene_finder`. Dev (admin): `/awaken living wayfarer <who>`,
  `talk` (the nearest Wayfarer), `scene <chain>` (its scene now, close by), `view <distance>` (stand back from your
  newest scene, facing it), `camp`, `quests`. Tour `tools/autopilot/living-c.txt`.

### 21.6 The World Pulse and happenings (Phase D, as built 2026-10-25)
- **The Pulse** (`living.pulse.Pulse`, saved data `awaken:living_pulse`, schema 1): per player outdoors (overworld,
  awakened, not in a gate, a Hearth's radius or the city), checked every 10 s: none near (256 blocks) and 90 s since
  their last → one starts; one or two near → another after 10 minutes; never more than three near (the anomalies near
  count too). The first comes about two minutes outdoors. Kinds by weight × the moon (`moon_weight` on favoured phases)
  × 2 for a kind the player hasn't witnessed, never the same kind twice running; up to three kinds are tried for a
  place. Happenings are world state, shared, and end on their own; their creatures are tagged `awaken_happening`,
  persistent, and leave with them (any no player is within 24-48 of go; the rest stay as ordinary creatures).
- **The calendar**: moon phase = (clock / 24000) mod 8 (0 full, 4 new); time of day dawn 23000-1500, day, dusk
  11500-13500, night; weather clear/rain/thunder.
- **Data** `data/<ns>/awaken/happening/<id>.json`, schema 1: `title`, `family`, `behaviour` (code), `when` {`time`,
  `moon`, `moon_weight`, `moon_only`, `weather`}, `groups` (fauna biome groups), `bands`, `duration_s`, `weight`,
  `witness_radius`, `tell` (haze, dustline, flock, circle, storm, ghost, fog, lanterns, roar), `tell_radius`,
  `rumours` (with `{dir}`). No em dashes.
- **The launch set** (behaviours in `Herds`, `Skies`, `Hunts`, `Tides`, `People`):
  - The Great Migration (dawn/day, grazing lands): a herd of 10-14 of the land's grazer crosses 140 blocks in 5 min;
    hunters come at the stragglers at a third and two thirds of the way. Stampede (day): 8-10 at a full run, 90 blocks
    in 40 s; 4 damage and a shove to anyone in the way (once per 1.5 s).
  - Murmuration (dusk, over water): client-drawn only; witnessing it gives `awaken:lucidity_start` +10 for that
    play-day (a perk source). Carrion Circle: kites over a barrel of remains (bones, leather, arrows, 40% a creature
    part, 25% a worn iron sword) and scattered bone blocks; the remains stay.
  - The Rolling Storm (day/dusk): a cell crossing 180 blocks in 4 min; visual lightning out of its rain core (within 20
    blocks of a point 12 downwind of its centre) about every 3 s; 3 damage within 4 blocks of a strike; struck sand
    turns to glass. Its look, local weather and thunder: "The Rolling Storm, as a real storm" below.
  - The Wild Hunt (full moon only, night, bands 1+): five wisp hounds and the Huntmaster (an elite four levels up,
    titled "Huntmaster of the Pale Moon") run 220 blocks; they go for anyone within 18; each hound drops 2 essence, the
    Huntmaster 6. Beast Duel (bands 1+): two named beasts fight; the winner is left at 45% health.
  - The Deep Tide (new moon only, dusk/night, 10 min): fog out of a broken gate within 300 (else open ground); while it's
    up the night's hunters, elites and camps are two levels harder everywhere (`Regions.creatureLevel`).
  - Caravan (day, ×3 on the half moons): a wandering trader ("Caravan Master") and 2-3 trader llamas walk 140 blocks in
    6 min; trade as usual. 40% are ambushed halfway by 3-4 pillagers; if the master lives, everyone within 40 gets
    0.15 of a level (budget "pulse", 0.15 per 10 min) and 4 emeralds.
- **Tells** (payload `living_happenings`: id, tell, centre, radius, progress, line direction; to players within 512
  every 2 s, an empty list once when the last goes). `living.client.PulseFx` draws them in an alpha pass (unfogged,
  reads by day) and an additive pass, the centre eased over the 2 s; past 70% of the render distance a tell is pulled
  in along its sight line and shrunk to match (as the gate pillars). The storm tell is a cell of its own (below), never
  pulled in.
- **The Almanac**: within a kind's `witness_radius` a player witnesses it once: counter `awaken:happenings_witnessed`;
  the first time a discovery `happening/<id>` (0.1 of a level), a new thing for the Director, and "Witnessed · <title>".
  The Codex tab lists them ("HAPPENINGS WITNESSED · n / ??").
- **Rumours** (`Rumours`, attachment `awaken:rumours`, schema 1, synced to the owner, kept through death): half the
  time a Wayfarer with nothing for you tells of a happening on now 160-2500 blocks away that you haven't witnessed, with
  a compass direction; it leaves a "? <title>" mark on the Atlas (MapMarks) for two play-days (at most 8).
- Dev (admin): `/awaken living happen <kind>`, `happenings`, `calm`, `strike <distance>` (a storm strike that far
  ahead of you, for tours). Tests `living_pulse`, `living_storm`.
- **The Rolling Storm, as a real storm** (Euan's playtest, 2026-10-26: the cell read as round soft discs full of
  artifacts, its rain as moving white pillars of light, and it wasn't a real storm; he liked the bolts and their sound).
  One model both sides share, `living.pulse.StormCell` (sizes match `lens_storm.glsl`):
  - **The cell, a cumulonimbus**: its base 92 blocks over the ground its centre first stood on (140-215), 300 blocks
    from base to the anvil's top. A flat dark shelf at the base (the condensation level, flat to a block or two),
    125 in radius with a ragged rim, wider than the tower that rises from it and 14-34 deep (the orchestrator's review:
    the underside read as rounded and lumpy). A broad tower (95 radius at the base, narrowing a third toward the top,
    leaning 35 downwind) of cauliflower turrets (cellular "puffs" at 50 and 20 blocks, rising 0.35 blocks a second through it,
    so it boils), its axis wandering and its girth swelling and pinching with height, big lobes on some sides; an
    overshooting dome over the updraft; a flanking line of three smaller towers stepping down upwind (170, 118, 78
    high); a flat-topped anvil plate reaching 340 downwind, 70 upwind and 185 across, 78 deep where the tower spreads
    into it and thin at its rim (underside at 230 + 58 × r^0.55, top at 308 − 3r), its edges fibrous streaks along the
    wind (ice, not puffs); 260 by 150 read as a pale blob in profile. Its rain: a core of soft shafts 58 in radius, 12 downwind
    of the centre, slanting downwind as it falls (0.3 blocks a block), to the ground. Downwind is the way it travels.
    It builds over its first 12 s and rains itself out over its last 15 (the client times the end by the storm's 240 s).
  - **Drawn by Lens 2 on Cinematic and Ultra** (§22 "Storm cells"): marched in Lens's cloud volume. Elsewhere (Lens off,
    Swift, a shaderpack) by `living.client.StormFallback`: the same shape, built off-thread once per storm into 12-block
    cloud cells, 8 blocks (96 × 45 × 96, density over 0.45), opaque and face-shaded as the game's clouds are (top 1,
    sides 0.8-0.9, base 0.62), each lit by how much cloud lies eight cells toward the sun (exp(-0.45 per cell)), the sky's
    light mostly the game's cloud brightness (30% of its hue: its sunset tint on every face read as terracotta), dim and
    cold at the base, the sun's warm colour on faces turned to it, lit from inside by its lightning, hazed toward the sky colour
    with distance (1400 blocks); only outer faces turned to the camera are sent (about 3,000-4,000 quads). It grows from the
    base up as it builds and rains out from the top down (each cell's threshold by its height and a hash; faces rebuilt
    off-thread per 32nd of strength). Its rain: nine curved sheets fixed in the world round the core (arcs at set radii
    and angles), slanting downwind, the texture `living/storm_rain` (soft streaks, `tools/art/specs/storm_rain.py`)
    sliding down at about 19 blocks a second (Euan saw it flow up at first), faded at their ends, top and foot, dark grey, in the game's own fog (the weather pipeline). No
    camera-facing discs or billboards (the old `PulseFx.storm` is gone); nothing popped in from beyond the render
    distance either: the cell stands where it is, as the game's clouds do.
  - **Its local weather** (`living.client.StormSky`): this client's rain and thunder levels rise to
    `weatherAt(d)` (full within 45 blocks of the rain core's centre, none past 110, smooth between) × its strength,
    never below the server's own weather (followed if the server changes it while overriding) and handed back as you
    leave. So under and near it the game's own rain falls on you and sounds, the sky, fog, clouds and light darken as in
    a vanilla thunderstorm, terrain's wind steadies higher (rain and thunder lift it), the world sound hears rain, and
    Lens dims its sun. Lens's cloud layer doesn't thicken for this rain (`StormSky.localRain`): the cell is the cloud
    over you. Storms show only in the overworld while the Living Wilds are shown.
  - **Its thunder**: the server's visual bolts stay (and the game's sky flash). The game sends a bolt only to 256 blocks,
    so each strike also sends `living_storm_strike` (its position) to players within 640 (`StormCell.HEARD`): a far
    storm still flashes and thunders. On the client a bolt or that word within 90 blocks of a cell is its strike (the
    second of the two for one strike changes nothing): it lights the cell from inside (a burst and up to two restrikes over half a second, 25
    blocks over the base), and its own thunder and impact (played at once, everywhere, at full volume) are silenced and
    played again late by the distance at 130 blocks a second (the world's scale: a strike on the horizon rolls in a few
    seconds later). Within 70 blocks: the crack (the impact, pitch 0.55-0.7) and the thunder; farther: the thunder lower
    and softer (volume 1.15 - d/520, pitch 0.95 - d/1400) and a long low roll after it (`living.storm.roll`, three
    synthesised takes). Every 2.5-7 s lightning flickers inside the cloud (50-210 over the base, a quarter to two fifths
    of a strike's brightness) and rumbles far off. Each flash is a burst and its restrikes, each fading over 0.11 s.
    The storm's rain roars from its core for 340 blocks (`living.tell.storm`, a loop; PulseSound).

### 21.7 Creatures live somewhere: the ledger (Phase D, as built 2026-10-25)
- `living.population.Ledger` (saved data `awaken:living_world`, schema 1, only cells players reached): per 512×512 cell,
  made once from the world seed the first time a player is within a cell of it (one cell a tick at most), from its
  land at five points (the biome source; never loads a chunk): up to two herds (greenwild mossback, frostwild frost
  stag, sunscorch dune ray, crag crag ram, blossom honey bear; 6-10 of 12), one pack linked to the first herd
  (greenwild gloomstalker, frostwild wisp hound, canopy canopy prowler, duskwood thornling; 3-5 of 5), one roost
  (frostwild frostfinch, crag peak kite, duskwood hollow owl; 4-6 of 6). A record: kind, species, home, three pastures,
  count, max, last play-day, seed, linked herd.
- Where (pure, from seed, play-day, time): a herd at pasture (day+seed) mod 3 → the next by 2000 (dawn walk) → the one
  after from 12000-13500 (dusk walk), wandering ±10; a pack at its den 23000-13000, else 24 blocks off its herd toward
  the den; a roost at home, out circling at 40 blocks at dawn and dusk.
- Bodies: every 2 s, a population within 96 blocks of a player (its place loaded and dry) takes min(count, 12/5/6)
  bodies (tags `awaken_ledger`, `awaken_ledger_<id>`, persistent; at most 24 near a player); they walk toward where
  their population is (herds 0.9, packs 1.1; grazing within 12). No player within 128 of any body: they're discarded.
  A death lowers the count; a body loaded from disk that isn't current is discarded (after a restart).
- Catch-up (when a population takes bodies after days away): herds +1 a day (+2 while their pack is wiped out), packs
  +1 per 2 days (+1 per 4 once wiped), roosts +1 a day; each night a pack lived its herd lost one (never under 2).
- Natural spawning leaves the herd species to the ledger (`LivingHooks.ledgered`, read by `FaunaRules.verdict`:
  "its herds live in the Living Wilds' ledger"); hunters and ambient creatures keep spawning as before.
- Dev: `/awaken living ledger`. Test `living_ledger`.

### 21.8 Camps and nests (Phase D, as built 2026-10-25)
- `living.camp.Camps` (saved data `awaken:living_camps`, schema 1): a riftborn camp (45%; gate creatures that got out,
  an E-rank theme: Ember Mines, Bell Crypt, Fungal Hollow, Overgrown Temple or Hive) and a hollow den (30%; the
  Hollows) seeded with each ledger cell whose centre is level 8+, and a blight nest at a broken gate nobody cleared for
  two play-days (its theme). A seeded camp finds its ground (dry, level within 2 over 5 blocks, 120+ from a Hearth, 48
  from a gate, never the city) within 48 of its seed spot the first time a player is within 160, else it never was.
- Built (`CampPainter`, natural ground and air only, every block recorded) when a player is within 96: a riftborn camp
  (coarse dirt and paths, a crying-obsidian rift scar, a palisade of fence posts and dripstone points with gaps, 1-3
  wool tents, a lit campfire, two soul lanterns), a blight nest (blackstone, sculk and crying obsidian over the scar,
  a heart pillar), a hollow den (mossy stone, bone blocks, webs); radius 3 + tier; three lights at most.
- Awake within 40: the heart (an elite of the theme, "Heart of the <camp>", +2 levels) and a wave of 2 + tier
  (max 9) at `Regions.creatureLevel(CAMP)` + 2 per tier above 1, through `Mobs.spawn` (stats at that level), tagged
  `awaken_camp`, creature kind CAMP (kill XP by role); when a wave is down and the heart stands, another, tier - 1
  more in all. Everyone gone past 104: it sleeps (its creatures go; whole again next time).
- The heart killed: cleared. Its ground turns to coarse dirt (ash), what it stood up goes, for 5 play-days; then it
  can return at tier 1. Players within 56 get 0.4 × (1 + 0.25 per tier above 1) of a level (budget "camp", 0.10 per 10 min, as
  pacing_sim's CAMP_K), counter
  `awaken:camps_cleared`, "Cleared · <camp>".
- Each new play-day: with a player within 1500, a camp grows a tier every two days (max 5; rebuilt bigger next time).
  At tier 4+ it picks the nearest Hearth or the Wayfarer camp within 800, warns players within 800 of it ("it raids
  tomorrow night"), and the next night (14000-14400) 4 + tier of its creatures come 24 blocks short of the target,
  toward it.
- Dev: `/awaken living camps`, `nest <kind> <tier>`. Test `living_camp`.

### 21.9 Crafts (Phase E, as built 2026-10-25)
- Six crafts (`living.craft.Craft`), levels 1-50, craft XP to the next level 40·c^1.3 + 40; attachment `awaken:crafts`
  (schema 1, kept through death, synced to its owner: craft XP per craft, kinds hunted). A craft level is a toast; a
  perk opening is a toast that names it, what it does is in the Crafts tab (counter `awaken:craft_perks`).
- Each paying action also pays the Hunter level 0.008 of a level (pacing_sim CRAFT_ACTION) through its craft's budget
  (forage 0.012, mine 0.012, fish 0.012, cook 0.007, smith 0.007 per 10 min, × the rank's wild share), and the craft
  XP shrinks by the same share the budget kept back, so a farm levels off on both. Hunting rides the kill's "hunt"
  budget (its craft XP divided by how worn that is).
- Sources: Hunting, a kill: 4 + 0.3 × level (+20 an elite, +60 a beast, +30 the first of a kind). Foraging: a ripe
  plant 12 (and its herb), a flower or fern 2, a mushroom 4, berries picked 5. Mining: an ore 10 (deepslate 15).
  Fishing: a catch 15. Smithing: a temper at the Forge 40 (hearth's `awaken:use/temper`, through `Quests.advance`),
  an anvil job 12, a tool, weapon or armour crafted 8, a Field Forge mend 8. Cooking: food crafted 8, smelted 6 each.
- Herbs are no new blocks: a natural plant (short and tall grass, ferns, small flowers) is ripe on a day by a pure hash
  of its column and the clock's day: 3% (6% with Root Sense). Harvested, it gives the land's herb: sunleaf (most
  lands), frostcap (frostwild), emberroot (sunscorch), moonmoss (mire), duskbloom (duskwood, canopy), spiritgrass (10%
  anywhere). Nine meals from them (shapeless recipes, a bowl back where there was one): herb broth (Regeneration 20 s),
  frostcap tea (Resistance 60 s), ember skewer (Strength 60 s), moonmoss soup (Night Vision 120 s), hunter's stew
  (Strength and Speed 120 s), forager's salad (Regeneration 30 s), miner's pasty (Haste 180 s), angler's chowder (Water
  Breathing 120 s, Dolphin's Grace 60 s), spirit tea (Luck 120 s).
- Perks (5 · 10 · 20 · 30 · 40 · 50), all live:
  - Hunting: Field Dressing (animals: a hide or more meat 33%), Tracks (client: for 30 s after the band reads a creature it
    leaves glowing amber prints, paws or cloven hooves, lying on top of grass and flowers, fading over 30 s), Weak Spot (crits ×1.15 on kinds killed 20+), Stalker (sneak attacks on a creature not targeting you
    ×1.5), Trophy (beast parts twice), Apex (×1.1 to elites and beasts).
  - Foraging: Herb Glint (client: a sea-green shimmer on ripe plants within 16), Wild Larder (+1 berries, glow berries,
    mushrooms), Root Sense (6% ripe), Seed Keeper (grass 4%: pumpkin, melon, beetroot or torchflower seeds), Bloom (by
    day among 3+ flowers, a Bloom Sprite comes, 30% each 10 s, none within 16), Wildheart (Regeneration in forests).
  - Mining (fixed by W2, §28.3): Clean Strike (ores +1 15%), Ore Sense (client: every 4 s with a pick or Hunter mattock
    in hand, ores within 6 pulse through stone as glass outlines, 3 s), Vein Sight (each ore Ore Sense finds, its whole
    vein outlined: same ore through the 26 neighbours, 20 out, 192 blocks), Deep Ear (client: underground with a pick,
    every 3 s, an open cave or Deepglass behind two or more blocks of rock within 12 plays a soft sound from where it
    is), Deepglass Cutter (Deepglass breaks ×2 and gives a shard more; replaces Deep Vein and the planned "alloy from
    deepslate"), Heart of Stone (below y 0, damage ×0.85). Mining keeps its own pace: only its Hunter-level share is
    budgeted (`mine`), its craft XP is not cut; every craft's XP is × (1 + perk `awaken:craft_rate`).
  - Fishing: Calm Water (+1 fish 20%), Shimmer (a treasure 10%: name tag, saddle, nautilus shell, book, bottle o'
    enchanting), Deep Lungs (air lasts twice as long), Storm Caller (+1 in the rain), Pearl Diver (prismarine shard 8%,
    ender pearl 2%), Leviathan Line (catches pay the Hunter level twice).
  - Smithing: Patch (anvil jobs a level cheaper), Fine Edge (crafted gear +10% max durability), Field Forge (an iron
    ingot on a lit campfire mends the off-hand item by a quarter), Masterwork (Unbreaking I 10%), Keen (weapons
    Sharpness I 20%), Signature ("Made by <name>" lore).
  - Cooking: Hearty (+1 saturation from cooked food and meals), Campfire Feast (eating by a lit campfire feeds players
    within 6), Hunter's Stew (meals' strengths ×1.5 long), Long Meals (+2 saturation), Spirit Tea (`awaken:energy_regen`
    +0.25 for 2 min after a cooked meal), Banquet (+the food's saturation again).
  The plan's Tidewater bait, sigil slot, temper discount and an apex phase read moved out: they need other modules'
  systems; the replacements above are this module's own.
- UI: CRAFTS on the band's Powers page (§30; level, a bar, perks open, the next). Dev: `/awaken living craft <craft>
  <level> [announce]` (announce: with the level and perk toasts, for tours). Test `living_crafts`.
- Art: the 15 icons are drawn by `tools/art/specs/items_crafts.py` (16×16, our own palette, one wooden bowl shape shared
  by the bowl meals; the pasty stands on its flat side with a crimped rope; no vanilla pixels). Its `preview DIR` checks
  colour count, distinct colours and herb silhouettes.

## 22. Lens 2 (cinematic light on Vulkan and Apple Silicon; Euan's brief 2026-10-25, three steps, one builder)

Lens's Cinematic and Ultra tiers become a light model of their own: shadows, contact darkness and warm/cool light
without rendering the world a second time from the sun, built for the M4's tile GPU (few full-size passes, the heavy
work at reduced size, temporal accumulation). Target: his reference (a golden-hour valley), dialed down a little.
Swift is unchanged. L1 light and shadow (this section, below); L2 atmosphere and a look per gate theme; L3 water and
magic.

- **Tiers** (client config `lens` = OFF | SWIFT | CINEMATIC | ULTRA; chains `awaken:lens_swift`, `lens_cinematic`,
  `lens_ultra`). Budgets on the M4 at 2560×1440, Vulkan, RD 32: where Lens off holds 120 fps, Cinematic 90+ and Ultra
  60+. The Look switch's Ultra tier is now Lens Ultra (no Iris: Iris is OpenGL-only and its Sodium pin crashes on
  NeoForge 26.3.0.95), selectable on every backend; its budgets are Cinematic p50 ≤ 11.1 ms / p99 ≤ 20 ms and Ultra
  ≤ 16.7 / ≤ 25 ms. With an Iris shaderpack on, no tier is current and Lens steps aside as before.
- **Chain** (Cinematic, Ultra): far-depth probe and trust (two 1-px passes) → **sun map** (`lens_sunmap.fsh`, L2) →
  **light** (`lens_light.fsh`, 960×540 on Cinematic, 1440×810 on Ultra since the L2 tour; uniform `LensConfig.Quality`
  1 or 2) → blit
  into the persistent `awaken:lens_hist` → Lens's bloom and shafts passes → **composite** (`lens_composite_lit.fsh` =
  the Swift composite's body, `include/lens_composite.glsl`, with `LENS_LIGHT` defined) → blit. 13 passes against
  Swift's 9; full-size passes stay two (composite and its blit), as before. Measured at L1 (no sun map yet), the extra
  cost barely changed between Cinematic's and Ultra's light buffer, so it's per-pass and full-size work, not tracing:
  L2 folds volumetric light into the light pass and the fog glow into the composite instead of adding passes. 26.3 post targets are RGBA8 only and fixed-size (or screen-size), so values that need precision travel in
  float textures (below) and the light buffer packs its distance into two bytes.
- **Frame texture** `awaken:lens_frame` (`LensFrame`, 24×1 RGBA32F, written in `RenderLevelStageEvent.AfterLevel`,
  just before the post effects, nothing allocated): 0-3 inverse view-projection, 4-7 view-projection, 8-11 last
  frame's view-projection (all camera-relative; the projection carries the frame's own view bob, hurt tilt and nausea
  warp, rebuilt exactly as `GameRenderer.renderLevel` does from `CameraRenderState`, so reprojection doesn't swim while
  walking), 12 the camera in the heightfield ring (x, z modulo 1024; y absolute; w a frame counter), 13 the camera's
  move since last frame and whether history is usable (not after a teleport over 24 blocks, a gap over 0.25 s, a new
  dimension or the chain starting), 14 toward the light and its strength, 15 the heightfield (size, highest surface,
  trusted radius 496 blocks), 16 zero-to-one depth, moon, rain, near plane. Layout in `shaders/include/lens_frame.glsl`.
- **Heightfield** `awaken:lens_height` (`LensHeightfield`, 1024×1024 RG16F, mips 1-4 hold the max of their 2×2; about
  5.6 MB on the GPU and the same off-heap mirror): from the client chunks' own heightmaps (26.3 sends MOTION_BLOCKING
  and MOTION_BLOCKING_NO_LEAVES to the client): r the top of everything that blocks motion (leaves included), g without
  leaves. A ring addressed by world block modulo 1024, a 64×64-chunk window centred on the camera's chunk: a chunk
  entering the window clears its slot and is read nearest first (40 a tick), a loaded chunk is read when it arrives
  (`ChunkEvent.Load` on the client), the 5×5 chunks around the camera are re-read every 10 ticks and the rest of the
  window 12 a tick in turn (block changes show within about ten seconds; halved for the CPU after L3), and a slot uploads only when its hash changed,
  as the changed chunks only: a tick's changes as one box per level when they fit in 64 chunks (a recentre's new
  column or row), else up to 8 rows a tick, each the span of its changed chunks (packed through one staging buffer;
  whole-width strips cost 320 barriered copies per step east or west, docs/perf/CITY-FPS.md). Built only where the dimension has the overworld sky.
- **Light pass** (per pixel of the reduced buffer, from the full-size depth): position and normal from depth (of the
  two neighbours per axis the one continuing the centre's plane; a normal within 0.9 of an axis snaps to it, since
  block faces are axis-aligned), then:
  - **Sun shadow from the heightfield**: march from the surface (nudged 0.06 along its normal) toward the light, 24
    steps on Cinematic / 40 on Ultra, each longer than the last (to about 250 blocks), far steps testing a whole max
    cell (mip by the stride) so a stride can't skip a ridge; solid → shadow, through leaves the light fades
    (exp(-0.9 × stride)) for flecks under trees. Stops above the window's highest surface or past the trusted radius.
    A heightmap counts a slab, stair or carpet as a whole block, so a surface's own column only counts from a block
    above it; a point more than a block under its column's solid top (a roof, a cave) gets no sun.
  - **Contact shadows**: 6 / 12 steps through the depth buffer toward the light, 0.35 to 3 blocks (longer far off):
    mobs, grass, slabs and block edges the heightfield can't hold. Within 96 blocks.
  - **Ambient occlusion**: 6 / 10 cosine-weighted samples in a 0.9-block hemisphere against the depth buffer. Within
    96 blocks.
  - **Temporal accumulation**: each frame uses fresh noise (interleaved gradient noise stepped per frame) and blends
    into last frame's result reprojected by the frame matrices (0.14 new on Cinematic, 0.1 on Ultra); of the 2×2
    history texels only those that saw the same distance count (disocclusion), dithered into 8 bits so slow changes
    still move. Out: r sun, g ambient occlusion, b volumetric light (L2), a the distance in one byte (log2(d+1)/13,
    about 3.6% steps; 0 for sky); texels of the history and of the upsample count when within 1.5 steps.
  - Never within 1.25 blocks (the first-person hand, whose depth 26.3 merges into the main depth while post effects
    run, and the holo glass at 0.62 m): not shadowed, never an occluder.
- **Composite light** (before the haze; the frame is display-encoded, so a multiplier m on linear light is applied as
  √m): the light buffer upsampled by distance (of its 2×2 texels the ones that saw this surface), the normal again at
  full size (from screen derivatives where the pixel's 2×2 quad lies on one surface, the four-tap version only across
  an edge), then a warm key where the sun reaches (noon (1.04, 1.02, 0.97); golden hour, by the sunrise colour's
  strength × `keyWarmth`, (1.30, 1.03, 0.72); the moon (0.93, 0.98, 1.08)) and a cool shade where it doesn't
  (`shadeTint` × (1 − `shade`), deepened 30% at golden hour and up to 35% with distance, 24 to 160 blocks, so long
  shadows stay readable across the land; warmed a little at golden hour by light bounced off sunlit ground). Sunlit
  colour gains up to 16% saturation at golden hour, and the distance haze takes a share of the sunrise colour, so a
  golden-hour vista reads warmer and richer, not greyer (the orchestrator's note on L1's sheet). Faces turned from the sun shade only as it lowers (none
  at noon, when Minecraft's sun is straight overhead and the game's own face shading stays; all by golden hour). The
  model fades out under solid cover (1 to 7 blocks of it: a ledge is shade, a roofed room is left as the game lit it,
  torches and all), toward the end of the render distance (85-100%), in the game's thick fog (as the haze does) and
  within 1.45 blocks. Strength: the sun's height (none at the horizon crossing), a faint moon (0.4) by night, rain
  (−85%) and thunder (−50%); 0 where there's no overworld sky. Golden hour is the more of the sunrise colour's strength
  and the sun's height (full below sin 0.1, none above 0.5; the sunrise colour alone only peaks as the sun touches the
  horizon). Ambient occlusion applies everywhere, by day and night,
  fading out from 48 to 96 blocks. It only darkens, so the never-lift rule holds.
- **Filmic curve** (`filmic`, in place of the plain contrast clamp): past white, colour runs toward white instead of
  clipping its hue; the top tenth rolls off with tanh (white itself within 3% of white); contrast is an S around
  mid-grey with the look's slope there, then a black point of 0.45 × (contrast − 1), as deep as the plain contrast's,
  so night and shade keep their depth. Not on the open sky, where the sun's glow keeps its soft blow-out (a shoulder
  there showed the sun's square disc).
- **Look fields** (looks*.json, inherited from `default`; texels 17-19): `light` 1.0, `shade` 0.32, `shadeTint`
  [0.86, 0.93, 1.14], `keyWarmth` 0.8, `ao` 0.65, `filmic` 1.0; L2: `mist` 0.5, `mistHeight` 48 (blocks), `fogGlow`
  0.15, `skyGlow` 0.3. Texel 19: r mist, g mistHeight/256, b fogGlow/2, a skyGlow.
- **Debug views** (`lensdebug`, Cinematic/Ultra): 5 the light buffer (r sun, g AO), 6 normals with direct sun, 7 the
  light alone (grey 0.5 = unchanged), 8 the classic Cinematic (no Lens 2 light, plain contrast) on the same frame, for
  before/after shots.
- **Not used**: mediump. Desktop GLSL 330 through shaderc and SPIRV-Cross gives MoltenVK no half-precision path, so the
  bandwidth savings come from RGBA8 targets at reduced size instead. A real shadow map (rendering the world from the
  sun) is out of scope: the heightfield covers terrain, trees and buildings; contact shadows cover mobs.
- Tour: `tools/autopilot/lens2-l1.txt` (a lab plot with trees, pillars, a gazebo, slabs, carpets, mobs and a torch-lit
  room; Carillon at golden hour into and away from the sun; noon; night; the holo menu; debug views; a frame-time bench
  of off / Swift / Cinematic / Ultra at 2560×1440, RD 32). L1 measured (busy Carillon vista at golden hour, p50/p99
  ms): off 10.59/15.30, Swift 11.20/17.18, Cinematic 13.01/17.95, Ultra 13.16/18.36: Cinematic +2.4 ms over off,
  Ultra +2.6 ms, inside both budgets. A four-way diagnostic (`lens2-diag.txt`, one pillar, the light buffer facing N, E,
  S, W) proved the shadow direction from every side.
- The heightfield's scattered uploads go round the ring from where the last tick stopped (8 rows a tick), so a row
  that keeps changing near the camera can't starve the rest.

**L2, atmosphere**
- **Sun map** (`lens_sunmap.fsh`, `awaken:lens_sunmap`, 512×512 cells of 2 blocks on Cinematic, 1024×1024 of 1 block
  on Ultra, rebuilt every frame): for each column of the heightfield ring, the height above which the sun reaches it
  (the highest ridge, roof or canopy toward the sun less the light's drop over the distance), solid in r/g and with
  leaves in b/a (two bytes each, -256..768 blocks in 1/64 steps). Its march (24 / 32 steps, max mips by stride) stops
  as soon as nothing further toward the sun could rise above what it has, so most stop in a few steps.
- **Volumetric light** (in the light pass, its blue channel; it replaces adding a pass): along each view ray to the
  surface (or 192 / 320 blocks into the sky), 8 / 16 jittered samples of the sun map (lit, in a shadow, or under
  leaves at 35%) in a mist lying on the land: thickest at the height of the ground under the camera (its heightfield
  column; sea level 63 where unknown) and thinning above it, exp(-(y - ground) / mistHeight), so valleys fill and the
  air over a mountain stays clear (at sea level only, it vanished wherever the land is high); optical depth 0.0016 per
  block × `mist` × the look's `shafts` × the light's strength, sent toward the eye by a Henyey-Greenstein phase (g 0.6,
  plus a 0.06 floor), so beams show strongest toward the sun and the air away from it stays clear. Accumulated over
  frames (0.2 new on Cinematic, 0.15 on Ultra). The composite adds it (screen) in the light's colour × 1.6, and keeps
  the screen-space shafts at half (0.7) for what the sun map can't hold: clouds crossing the sun. Classic (view 8)
  shows the old shafts alone.
- **Fog glow** (lights glowing in haze, the gates' light pools): the shafts pass (quarter size, its g/b/a) spreads the
  wide bloom layer again over two rings of six taps (3.5% and 7.5% of the screen out) and the centre; the composite
  adds it tinted toward the haze colour, × `fogGlow` × 3. (At L2's tour it sat in the full-size composite with nine
  taps a pixel: too costly, and too diffuse to read as light pools.)
- **Sky scattering**: the sky, and the haze over terrain, brighten around the light's direction (pow(μ, 8) × 0.25 +
  pow(μ, 48) × 0.35 in the light's colour, × `skyGlow` (0.3 since L4: wider, it washed half the golden-hour sky
  white), warmer at golden hour). Clouds are in the depth
  buffer, so they take the key and shade like terrain (their sunlit tops warm, their undersides cool).
- **Gate looks**: each theme's biome (`awaken:gate_<theme>`) has a look of its own, `biome:awaken:gate_<theme>` in
  `assets/awaken/lens/looks_gates.json` (all 15 themes; the camera's biome picks it as for any biome, so no new sync).
  Every field is written out (a biome look inherits from `default`, the overworld). No sun in gates (`light` 0, `mist`
  0); their magic is the grade, haze toward the theme's colour, `ao` 0.5-0.85 and `fogGlow` 0.3 (Sky Archipelago) to 1.3
  (Lantern Night Market). Swift uses the same looks for its grade and haze (fogGlow and ao need Cinematic).
- **Ambient particles** (`LensParticles`, types `awaken:lens_mote|ember|frost|spore|wisp|breath` registered by
  `LensModule` on both sides, sprites from `tools/art/specs/lens_fx.py`: smooth white light sprites, tinted per place,
  never vanilla's): spawned on the client within about 12 blocks of the camera (more ahead), only on Cinematic (half)
  and Ultra, scaled by the Particles setting (Minimal: none), never during a story scene. Each gate theme has its mix
  (Ember Mines embers and ash, Fungal Hollow teal and violet spores, Hive honey spores, Frost Monastery falling frost,
  Spirit themes wisps, Drowned Archive rising motes, ...); the Nether has embers; the overworld by day has dust that
  spawns only in sunlit air (a CPU march of the heightfield mirror toward the sun) and glints when it's between you and
  the sun. Your breath shows every 3-4 s in first person in the Frost Monastery and wherever it snows. Embers, spores,
  wisps and dust are full bright (Lens's bloom picks them up); frost and breath take the world's light. Gate rates are
  ×1.5 the table's since the L2 tour, and every kind half as big again (they read as specks).
- L2 measured (same vista as L1, p50/p99 ms): off 9.00/13.61, Swift 8.94/14.12, Cinematic 13.13/23.75, Ultra
  16.14/23.66. Ultra holds 60; Cinematic (+4.1 ms) missed 90, so after that tour: the fog glow moved to the quarter-size
  shafts pass, normals from screen derivatives, Cinematic's light buffer 960×540 (24 sun, 6 contact, 8 volume steps),
  Ultra's 1440×810. Measured again in L3's tour.

**L3, water and magic** (looks: `water` 0.6, `heat` 0, `frost` 0, `glow` 0.6 by default; texel 20: r water, g heat,
b frost, a glow; the frame texture's texel 17 x is seconds, wrapping hourly)
- **Water**: the heightfield marks open-water columns (a solid top at the block's base + 0.75, exact in half floats up
  to y 512; the occluder loses nothing). A pixel whose surface lies on such a column's water level (base + 0.889) is
  water; where water draws without depth the pixel shows what's under it, and the reflection sits where the view ray
  crossed the water's surface. Rippling normals (two sines each way, by world position and time), Schlick Fresnel
  (F0 0.02). The reflection: the frame where the reflected ray meets open sky on screen, else the fog's colour; on
  Ultra a 20-step screen-space march of the reflected ray against the depth buffer (nearest first, fading at the
  frame's edge) reflects the scene itself. The sun's glint (pow(R·L, 240) × 5, none in the water's own shade) in the
  light's colour: the reference's glinting river. × `water` and the fog fade. Only where the heightfield is built (an
  overworld sky).
- **Emissive glow**: AWAKEN's glows (bright, saturated, not a warm hue: runes, vortices, the Signal's blue, eyes, the
  gate pillars) bloom up to 2.5× as much as lit surfaces of the same brightness (the prefilter, by `glow`; all tiers),
  and keep their light in Lens 2's shade (the shade's weight × (1 − glowness × `glow`)). They glow through fog
  because the bloom is made before the haze and the fog glow spreads it. No creature was repainted.
- **Heat shimmer** (`heat`: the Nether 0.45, Ember Mines 0.55, Glass Desert 0.4): the frame is read through rippling
  offsets (up to about 0.2% of the screen, more in the lower frame), Cinematic and Ultra only.
- **Frost** (`frost`: Frost Monastery 0.6): frost creeps in from the frame's edges (a band up to 11% of the height ×
  frost, deeper at the corners, crystalline refraction in it and a pale rime with fine streaks over it).
- `glow` 0.9 for the Spirit and Charge themes and the Mirror Palace, 0.8 for the Ember Mines.
- Tour: `tools/autopilot/lens2-l3.txt` (a pool in the lab into the sun, glowing creatures at dusk and night,
  Carillon's river, the Ember Mines' shimmer, the Frost Monastery's frost, the bench).
- L3 measured (with L2's fixes; same vista, p50/p99 ms): off 8.00/12.39 (122 fps), Swift 7.93/11.94, Cinematic
  11.02/16.67 (about 90 fps), Ultra 12.99/19.79 (77 fps): both tiers on budget. The tour harness's `gate interior
  enter` landed in other themes' gates, so heat and frost weren't in its shots.

**L4, volumetric clouds** (Euan, 2026-10-25: "the blocky clouds just aren't it")
- On Cinematic and Ultra Lens draws its own clouds wherever the game would draw clouds (the frame's cloud colour has
  alpha and the player's Clouds setting isn't Off) and the look's `clouds` (cover, default 0.45; 0 keeps the game's) is
  above 0. In `ExtractLevelRenderStateEvent` Lens takes the frame's cloud height and colour and clears that colour's
  alpha, so vanilla's clouds skip that frame; the setting itself is never touched (Swift, Off and a shaderpack keep the
  game's clouds). Rain thickens the cover by up to 0.4. Frame texels 18-19: base height (0: none), cover, the camera's
  x and z plus the wind's drift (0.04 blocks a game tick east) modulo 30720; the cloud colour.
- Shape (`include/lens_clouds.glsl`): a cumulus layer from 6 blocks under the game's cloud height, 84 blocks thick.
  Cover 0.45 leaves most of the sky open between distinct clouds (a coverage threshold of 1 − 0.72 × cover, edges 0.1
  wide), closing into an overcast deck toward 0.95; the threshold rises with height (0.16 × h²), so each cloud narrows
  upward into a dome and only the thickest rise tall (flat 2D cover read as vertical curtains). One tileable noise
  texture, `awaken:lens_cloud_noise` (256×256 RGBA, made in the frequency domain by
  `tools/art/specs/lens_clouds.py`: r coverage, g shape, b billows, a wisps), sampled at 3072, 960, 256 and 120 blocks
  a repeat (all dividing 30720) for where clouds stand and their outlines; their billows are two octaves of a cheap 3D
  value noise in world space (46 and 17 blocks on Ultra, one octave at 38 on Cinematic; flat noise slid with height
  drew vertical streaks), skipped deep inside a cloud. The cover slides up to 45 blocks with height (two noise channels,
  opposite ways; only in the main march, not the light-step and shadow look-ups), so a cloud's sides lean and break
  up instead of standing as straight walls. Flat-ish bases,
  domes that rise where a cloud is thick, billows eating the edges.
- Pass `lens_clouds.fsh` (560×315 on Cinematic, 800×450 on Ultra; then a blit into the persistent
  `awaken:lens_clouds_hist`): each ray marched through the layer (20 / 32 steps, up to 800 / 1100 blocks of it, never
  past nearer terrain), lit by 3 / 5 steps toward the light (doubling from 6 blocks): two octaves of Beer's law (the
  second, at 35% and a quarter of the extinction, stands in for light scattered many times inside, so bases stay soft
  grey-white), σ 0.03 per block, a powder term (thin sunward edges glow, dense cores darken), a phase of 0.85 + 0.9 × HG(0.6) (white from every side, a modest silver
  lining toward the sun; stronger forward lobes blew the sky round the sun out into one white mass), the sun's colour (warm at
  golden hour, the moon's at night) and the sky's light on them (a cool blue with a third of the horizon's colour, by
  the game's cloud colour but never below half and up to 1.95× toward overcast, so a grey sky stays bright grey; 60% at
  the base to 100% at the tops; the sun's light on them × 1.05). Distant cloud melts into the
  fog colour from 1500 blocks and is gone at 5000, so the deck never ends in a line. Accumulated over frames (0.15 /
  0.12 new), reprojected where the ray entered the layer. Out: rgb their light (premultiplied), a
  what shows through.
- The composite lays them over the sky and over anything farther than where the ray enters their layer, before the
  haze. The bloom prefilter (`lens_prefilter_lit.fsh`) folds them into the sky, so bright edges bloom, and the sky's
  alpha (what the screen-space shafts and the sun's halo read) carries 60% of their transmittance: rays break through
  gaps and the halo dims behind a cloud (all of it streaked the whole sky like rain curtains; the shafts also drop to
  0.35 in the composite). The shafts read the open sky's own brightness, not the clouds' (bright cloud tops seen from
  above streaked into rays). The light pass darkens the sun on the land under them (to 28% under thick cloud, along
  the light ray through the layer's middle) and, on Ultra at half strength, in the volumetric beams: moving cloud
  shadows and beams through gaps.
- First tour (bad8a542, 480×270, 20 steps, the first cover curve; p50 ms off 7.74, Swift 7.85, Cinematic 10.73, Ultra
  13.20: the clouds cost no net frame time, since the game's are no longer drawn): too much cover (a stormy deck),
  dark bellies, a hard line toward the horizon, a noise grid; tuned as above.
- Tour: `tools/autopilot/lens2-l4.txt` (Swift's clouds against Lens's: noon, golden hour into the sun, cloud shadows
  over Carillon, above the deck, rain, night; the bench). Final (f53b8bf4, p50 ms): off 6.80, Swift 6.79, Cinematic
  9.96 (100 fps), Ultra 13.46 (74 fps).

**Storm cells** (the Living Wilds' Rolling Storm, 2026-10-26; §21.6): Lens 2 marches each cell in its cloud pass.
- `living.client.StormSky.lensCells` hands Lens up to two cells a frame (as `HoloWorld.lensPlates` does the plates):
  frame texel 20 y their count; texels 42-47, three a cell (`lens_storm_frame.glsl`): centre x, z camera-relative,
  base y, strength; downwind x, z, the ground under it, noise time; the latest flash camera-relative and its strength.
  `LensClient.drawsStormCells()` says whether Lens draws them this frame (Cinematic or Ultra under an overworld sky);
  otherwise the fallback does. Storms draw whether or not the game's clouds are on.
- Shape (`lens_storm.glsl`, §21.6's numbers): cheap cellular puffs (the nearest of 8 jittered points; one octave on
  Cinematic, two on Ultra), the body's wobble, girth and lobes from the cloud noise texture, the anvil's fibres from its
  wisps channel. Light steps (and the land's shadow) keep the big forms only.
- March (`lens_clouds.fsh`): a ray meets a cell in bands, marched in the ray's order, each its own steps by its length:
  rain (ground to base; 18 / 16 blocks a step, at most 10 / 12), body (12 / 10.5, at most 22 / 26), anvil (8 / 7, at
  most 10 / 12), Cinematic / Ultra, so the thin anvil is never stepped over. A turret's surface turns solid within 4
  blocks (9 read as a soft ball at 200 blocks); σ 0.085 a block (a solid mass); 3 light steps (6 blocks, ×2.3); Beer's
  law with a weak multiple-scatter floor (0.22) so the base and the shadowed side go dark; the layer's phase; the sky's
  light as a cool grey-blue of the layer's ambient brightness (its sunset hue turned the shadowed side brown), by height
  from 0.16 at the base (cold and bruised, bluer) to 1 on the tops; rain σ 0.016, its colour that sky light × 0.4 (a
  grey veil, darker than the sky), fading in from 16 to 40 blocks from the camera (nearer, the game's rain falls).
  Lightning lights the cell from within: 3 × exp(-d/55) × exp(-τ) + 0.1 × exp(-d/180) of a cool white from the flash,
  where τ is the cloud between the flash and the point (its density at their midpoint × d × σ × 0.35, one sample, only
  while a flash shows), in the cloud and (half) in its rain; the core round the bolt glows and the cell's shape still
  reads (an even glow over the whole cell blew a night strike out to a white disc); while a flash shows, the pixel takes up to 85% of this
  frame instead of 12-15%, so it lights at once and fades as an afterglow. The layer's clouds thin to nothing within
  330-450 blocks of a cell (it clears the sky round it), so the layer's march can skip the cell's bounds without a cut.
  History reprojects where the ray first met cloud (was: where it entered the layer).
- The composite lays the cloud buffer over anything farther than where the ray enters a cell's bounds (320 radius
  round 60 downwind of its centre, ground to 48 over the top; the anvil band 245 round 150 downwind, from 72 under
  the top), never nearer than 16 blocks; the light pass darkens the
  sun on the land under a cell, sampled where the light ray crosses three heights of its body (long shadows at dusk).
  Its bright, flashing edges bloom and cut the screen-space shafts like the layer's clouds.
- It builds from its base up (the density cut at a height rising with its strength, softened over 40 blocks) and rains
  out from the top down, as the fallback's cells do; its rain starts once the base has formed (strength 0.1-0.4).
- Lens dims its sun by the world's own weather, not the storm's local rain (`StormSky.serverRain/serverThunder`): the
  sun still lights its towers and the land beside it, and the cell's own shadow darkens the land under it.
- **Wet surfaces** (Cinematic, Ultra; any rain, the storm's local rain included): in the composite, a surface open to the
  sky in its column (the heightfield's top with leaves; under leaves 40%) gets wet by the rain level: 16% darker, 10%
  richer, and what faces up a dull sheen of the haze colour at glancing angles (Schlick-like, power 4, × 0.22).

**Holo world plates** (the Living Wilds' creature plates, 2026-10-25): plates write their depth at a creature's
distance (4-40 blocks), beyond the 1.25-block rule that keeps the panels and the hand out. `HoloWorld.lensPlates`
hands Lens the plates drawn this frame (up to 6: centre, half-width and half-height vectors, camera-relative); the frame
texture carries them (texel 20 x the count, texels 24-41, three a plate; it grew to 48×1), and a surface point within
0.15 block of a plate's plane and inside its rectangle (`onPlate` in lens_frame.glsl) gets no shadow, AO, key or shade
and no haze. With the K panel open they dim with the rest of the world (the focus dim stays).

**Temporal clouds and light** (Euan, 2026-10-26: "Start all three ideas now", docs/perf/IDEAS-26OCT.md B; setting
`lensTemporal`, default on; autopilot `lens temporal on|off|setting`):
- Why: at the Roost, Lens Cinematic cost ~5.4 ms of a ~21 ms frame (docs/perf/PERF-26OCT.md). Its marches (the clouds
  at 560×315 / 800×450, the light buffer at 960×540 / 1440×810) already blend into a reprojected history, but every
  pixel marched every frame.
- The clouds: a pixel marches one frame in four, a 2×2 pattern turning with the frame counter (frame texel 12 w).
  - On the other three frames it keeps last frame's clouds, reprojected to where its ray enters the layer (or the
    nearer storm cell; 150 blocks when the camera is inside the layer).
  - A march blends in at 1 - (1 - f)^4 of the old weight f, so the clouds answer over time as before.
  - It marches anyway with no history (a cut, a teleport, a long gap), off screen, or while lightning flashes.
- The light buffer (sun visibility, contact shadows, AO, volumetric light): a checkerboard marches each frame,
  alternating.
  - On its off frame a pixel keeps its reprojected history where at least 0.4 of the 2×2 taps saw the same distance.
  - A new surface (disocclusion) marches.
  - A march blends in at 1 - (1 - f)^2.
- Frame texel 17 y carries the switch; with it off, both passes are exactly as before.

## 23. Creature life (Fresh Animations in the pack, and a life layer on every body of ours; Euan 2026-10-25)

Euan: every mob, ours included, "still feels a bit rigid and static". Approved: "Both: Fresh Animations + our life layer".

**Vanilla bodies: Fresh Animations.** The pack's `feel` profile adds Entity Model Features 3.3.12, Entity Texture
Features 7.2.6 and the Fresh Animations resource pack 1.11.1 (`tools/pack.py` now locks Modrinth resource packs like
mods, under the `minecraft` loader; `install` puts them in `resourcepacks/` and appends them to options.txt's
`resourcePacks` only if missing, never touching the player's own packs; `remove` switches off and deletes only what it
put there; `export` ships them under `resourcepacks/`). EMF finds models by the vanilla layer a renderer bakes, so our
gate monsters on vanilla bodies (zombie, wolf, hoglin, iron golem...) animate the Fresh Animations way too, which is
wanted: Fresh Animations keeps the vanilla UV layout (it only adds a few eyelid pixels), so our skin layer still fits.
Our own bodies live on `awaken:` layers with no .jem, so EMF leaves them alone; they get the life layer instead.

**Our bodies: the life layer** (`mobs/client/life`). One procedural layer on every model of ours (hero rigs, P3 rigs,
the 14 hand-built bodies), driven by bone names and a temperament, never by per-creature code:
- **Bones by name** (`LifeBones`, plain data): head, neck, jaw, eyes, lids, body/chest (breath), and the springy roles:
  ears (also tufts, crests, plumes, sails), antennae (whiskers, palps, a lure's rod), tails (flukes, streamers, fans),
  dangling parts (manes, beards, ruffs, skirts, tentacles, reeds, brambles, lamps, a bell's clapper), wings, fins and
  gills, arms (only where the temperament swings them: lords, spirits, the Mycelord, the Hushling, the Breathbell). A
  part named only "tip" or "end" takes its parent's role; links of one role chain (follow-through down a tail).
- **Temperaments** (`assets/awaken/life/creatures.json`, schemaVersion 1): cute, grazer, hunter, beast, lord, monster,
  spirit, insect, swimmer, citizen; from the fauna role (ambient cute, neutral grazer, hunter, beast) or the tier (gate
  monsters monster, wardens beast, lords and guardians lord, nightmares spirit), and per-creature entries on top. Fields:
  breath (Hz) and depth, blink interval (0 = never: insects, fish, serpents, spiders, the eyeless and the faceless), head
  speed and damping, eye lead, spring Hz, damping and gain, step bob (px), sway (deg), turn lean, fidget interval and
  weights, arms, stagger, mass. Cute creatures are quick and bouncy (head 14 rad/s, underdamped, springs 3.4 Hz), hunters
  smooth and still (critically damped, blink every 5-11 s), beasts slow and heavy (head 3.6, springs 1.4 Hz, bob 1.3 px).
- **Per creature, per frame** (`Life`, made once, nothing allocated after): motion from the render state (velocity and
  acceleration in model space, yaw rate); breathing (a quicker in-breath, faster and deeper after running or a blow,
  decaying over ~9 s; on hero rigs it also speeds up the creature's own idle clip); blinks (0.16 s, 15% double); the head
  follows its target on a damped spring (the AI's look, an idle's glance, a lead into a turn) and eye bones take the
  rest (eyes first); springs integrated at up to 120 Hz substeps from the body's acceleration, the parent bone's clip
  motion (a head toss swings the ears), the yaw, a blow and a fall, swing only (no twist), capped per role; the step's
  dip (twice a stride, from the gait clip's own stride length) and weight sway; a lean into turns.
- **Idle fidgets**, only when still, calm and within 32 blocks, chosen by weight from what the body has: look around (the
  head lags the eyes), sniff (head down, quick nods), flick (an ear, a tail, a feeler or a wing), shake (body roll at
  7.5 Hz, everything loose flops; a creature fresh out of water shakes itself dry once it stops), stretch, yawn (eyes
  squint shut), scratch (a hind leg kicks), perk (head up, alert), settle (a weight shift). Herds never move in step:
  each creature's timers, breath phase and rate (±10%) and idle clock start from its own seed.
- **Graph blending, no pops**: the angry stance, held poses, swimming and the glide ease in and out (0.05-0.3 s); swim
  and wing loops run on integrated clocks (age × speed jumped their phase when the speed changed); one-shot clips (bite,
  flinch, strike, roar, warning) hold their last frame and let go over 0.2 s; the fall crossfades from the live pose over
  0.25 s; a swimmer's pitch eases out of the water.
- **Hero clips on top** (named beasts, Gate Lords): a wind-up gathers the body low (0.7 px) and trembles at 17 Hz as it
  nears the strike (tension², the tell); a roar opens with a full chest and shudders through the head and every loose
  part, and bodies without a roar clip rear back (a Gate Lord turning on you: vanilla's aggressive flag); a blow knocks
  the body away from the camera and it sways back on its weight (a damped spring kicked once: heavy beasts reel less and
  slower); the fall: the eyes close at 55% of it, one last breath, and when the body lands (85% of the death clip's drop,
  `LifeRig.landing`) a thud: one bounce and every mane, ear and bramble flops. Bodies without a death clip (the hand-built
  ones, every boss on a vanilla body: lords, guardians, sovereigns, the Shadow, wardens) topple instead of vanilla's
  flip: a stagger back, an accelerating fall (90° by 0.7 s), one bounce, settled by 0.95 s, inside vanilla's 20 ticks.
- **Blinking**: eyelid bones close the way the rig's own clips close them (the Mire Mother, the Seated One); eye bones
  squash (the snails' stalks, the Lantern Maw); otherwise the closed-eye skin: `tools/art` records every pixel painted
  inside `with P.eyes():` (`fauna_rig.EyeRec`) and writes `<id>_blink.png` (the skin under the eye with a lash line)
  and `<id>_glow_blink.png` (the eyes' light out); the renderer draws them while the eyes are shut. 14 creatures have
  them; existing textures are byte-identical.
- **Cost and limits**: client only; nothing allocated per frame (render states carry a reference); springs and fidgets
  off past 32 blocks (springs settle), blinks off past 32; breath and the slow head everywhere (cheap). Reduced motion
  (`awaken-client` `reducedMotion`, the body module's) turns idle fidgets off and halves springs, tremble and stagger;
  breathing and blinking stay.
- **Other modules' bodies** use `CreatureLife` (§12). Carillon wires its citizens itself (M4 B3): temperament `citizen`
  (no step bob or sway, gentle springs, look and settle fidgets), `lookWeight` for "only look at the player up close or
  at whoever they talk to", clips win where they drive a bone (applied after the layer).
- **Not here**: the Ritual of the Bell's cast (its model plays exactly the clip its stage asks for); humanoid gate
  monsters on vanilla bodies (Fresh Animations' job, plus the boss topple and stagger above).
- Test: `creature_life_binds` (every body of ours gets a temperament, every rig and hand-built naming with ears or a tail
  gets springs on them, every creature that blinks has lids, eye bones or a closed-eye skin). Tour:
  `tools/autopilot/creature-life.txt` (PACK=perf,feel; clips in run-autopilot/clips/life_*).

## 24. Hands: the first-person system (plan docs/anims/PLAN.md, page https://claude.ai/artifact/5dQodY76zUGpn536Z34Lpy; approved 2026-10-25)

Euan: "right now for basically everything there is no first-person animation". One owner for everything drawn in first
person, as the Feel Director is for the camera (module `anims`; public face `core.api.FirstPerson`; built in phases P1-P6
of the plan). No mixins.
- **World layer**: your body in first person, posed in the world (`anims.client.WorldBody`): a `WorldPose` source fills a
  `FirstPerson.Rig` (each limb from its root toward its tip, its front, a weight; `Rig.reach` slides a rigid limb's root
  so its fist or foot lands on a spot); the highest priority source that poses this frame wins (movement 100,
  interactions 200, combat 300). Limbs are parts of a private copy of the player model (vanilla's own is posed by every
  player in the frame and drawn after submission), with your skin, sleeves and pants. Legs are drawn in the world with
  real depth (`SubmitCustomGeometryEvent`). Arms are viewmodels (P1 tour: a fist a third of a block from the eye filled
  half the frame): drawn in the hand pass, each fist where the world puts it on screen but at 0.85 from the eye, the arm
  rising to it from under the frame's corner on its side (view (±0.5, -0.85, -0.1)); vanilla's hands step aside. No
  torso (from inside the head it is only a slab under the camera). Never in third person. Like vanilla's hands, none of
  it shows with the HUD hidden (F1).
- **View layer** (`anims.client.Hands`, the one owner of `RenderHandEvent`, first for each hand and last for the off
  hand): every `ViewPose` stance adds moves (view space: x right, y up, z toward you, blocks; then pitch, yaw, roll in
  degrees) for both hands, the main or the off hand; the body's stances (sprint, slide, hang, mantle) and the rope's
  arm moved here from their own hand listeners. The casts (powers) still draw their own arms until P3.
- **View clips**: `assets/awaken/fp_clips/*.json` (`anims.FpClipSet`, schemaVersion 1) written by
  `tools/art/specs/fp_moves.py` (edit there; `preview DIR` draws vanilla's arms from the eye); keys in ticks eased by
  `anims.Ease` (the same curve names as the third-person clips), authored right-handed and mirrored for a left main arm;
  a one-shot ends at rest; `FirstPerson.play(name[, speed])`, `stop` (a 3-tick fade), `playing`; keyed events reach
  `FirstPerson.listen` listeners (sounds, hit-stops). Combat's animations use the same three layers.
- **Fists** (P2): a view clip (`"fists": true`) or a stance may key where each EMPTY hand is on screen (view space)
  instead of moving vanilla's arm by offsets: its arm is drawn rising to the fist from under the frame's corner (from
  view (±0.48, -0.84, -0.53), vanilla's own arm start), so a hand can be lifted anywhere without its forearm floating into
  view (offsets that lift a vanilla arm show its end: the P2 preview). Vanilla's resting fist is
  `FirstPerson.View.REST_FIST` (0.744, -0.455, -1.072); a clip's fists replace a stance's for that hand; a hand holding
  an item ignores fists (vanilla draws it). A fist clip gives the hands back at rest (or the off hand out under the
  frame), or, marked `toStance`, where its stance holds them (the hang's ledge jumps).
- **Grips** (Arms 2026-10-26, Euan: "it feels like the weapon is just floating and attacking on its own"): a source may
  key where a hand that HOLDS something closes on it (`View.gripMain`/`gripOff`, view space, weighted as fists): that
  hand's arm is drawn rising to the grip from under the frame's corner exactly as a fist's (after the moves for both
  hands, so it sways with what it holds), and the held thing still draws (a grip never hides the hand). Unset (weight 0)
  nothing changes. Arms keys them every frame a styled weapon is held: at rest, through each swing's turn, the guard
  and the equip rise, the off hand's on a pair's twin. Test `arms_grip`.
- **Look-lag** (P2): the hands trail fast turns of the view on a damped spring (up to 0.06 sideways, 0.045 up, 4° of
  roll), halved with reduced motion (`FirstPerson.reducedMotion`, the body's setting).
- **Near legs** (P2): a foot nearer the eye than 1.15 is drawn in the hand pass, pushed out along its line of sight to
  that distance, as the arms are; farther legs stay in the world. (No source puts one there since P2.1 cut the slide's
  legs: from eyes 0.4 up they could only be a slab across the frame.)
- **Clean fists** (P1.1; Euan, 2026-10-25): the hands are vanilla's own blocky fists, never modelled fingers (finger
  boxes read as loose bricks). A world arm rises to its fist from under it on its own side (its root follows the fist
  across by `WorldBody.RISE` 0.45), not from the frame's corner, so a hand high in the frame never drags its sleeve
  across it. A world rig sets `handDistance` (nearer reads bigger: touches 0.75, both hands 0.85, stations 0.8, the
  pipe 0.85, the slide and landing 0.78), `held[]` (an item at the fist: a plucked flower, food being laid) and `glow`.
  Vanilla's hand steps aside only on the side the world body has an arm (a touch with one hand leaves the sword in the
  other). In the hand pass the frame shows y down to about 0.7·|z|: keep keyed fists inside it.
- **The Runner's Kit in first person** (P2, `body.client.BodyFp`): each move's view clip starts from the same event as
  its third-person clip (BodyAnims hands the clip's name over): `fp_roll`, `fp_mantle` (and every climb out),
  `fp_grab`, `fp_lower`, `fp_ledge_up`, `fp_ledge_side_l/_r`, `fp_ledge_back`, `fp_walljump_l/_r` (the run's wall side),
  `fp_vault` and `fp_leap` (the free-run), `fp_slide_jump`, `fp_land_soft` / `fp_land_hard` (a fall over 0.55 b/t,
  hard over 0.95; never over a roll), `fp_stair_bump` (each half step a stair slide passes). Stances: the tactical
  sprint's lowered hands pumping in step (empty fists pumping up into the lower frame in turn, P2.1), the slide's brace, a
  wall-run's wall-side fist at the frame's edge brushing the wall with each footfall, a wall climb's fists slapping up in
  turn, a hang's fists on the lip (`BodyFp.HANG_*`, swaying; shimmying hand over hand). The wall jump's shove is well
  in the frame. World (source `body_moves`, 90; P2.1): the slide's trailing hand (the off side) on the
  ground where the eye's ray through view (±0.42, -0.34) meets it (the step under it on stairs), skimming it with a
  friction shake; a hard landing's fists slapped onto the ground under view (±0.2, -0.3) (down 3 ticks, held to 7, back
  by 12) instead of `fp_land_hard`;
  feet to the wall in a hang. No legs in a slide. The stair slide tips the view 9° down the run (`BodyCamera`).
- **The Ascent in first person** (P3, `body.ascent.client.AscentFp`): moments, each where the Ascent gives the Feel
  Director its beat: `fp_rope_fire` (the main arm flings the rope), `fp_rope_release`, `fp_rope_launch`, `fp_wings_open`,
  `fp_wing_beat` (every beat, the one that takes flight too), `fp_barrel_roll`, `fp_boom` (hands up against it),
  `fp_double_jump`, `fp_air_dash` (the lead fist punches through), `fp_dive` and `fp_slam_land` (fists up for a dive slam
  or the Judgement Fall, smashed down on landing), `fp_sovereign_ascend`. Stances: on a rope the main fist holds it at
  view (0.3, -0.02, -0.72), where AscentRender now starts the rope in first person, the other hand joining to pump
  (forward) and climbing over it to reel (sneak); a zip, both hands on it; flying free, arms out low for balance (fists
  up in a dive slam); wings, arms out flat at the frame's edges trembling with speed (swept out of the frame folding into
  a dive); flight, one fist forward on a boost, palms down in a hover, arms sweeping up to climb, swept back cruising; in
  the Sovereign's form the arms are tinted gold (`FirstPerson.View.tint`).
- **Reaches** (P4.1, `anims.client.Reaches`, world source `anims_touch`, 200): a touch's hand goes to the thing and does
  it there. Each touch is a few keys (tick, s across, u up, n out toward you) about where the hand meets the thing (its
  anchor, followed as it moves: a creature's flank, a lid's edge), from and back to rest under the frame's foot,
  smoothstepped; drawn at 0.75 (both hands 0.85). A door's palm meets it where you clicked (vanilla's door is open by
  then) and pushes on through. The player never moves (Euan, 2026-10-25: walking you to the thing
  would take control): past 2.5 blocks the hand only gets an arm's length (1.3) toward the thing, and while a hand is out
  the view narrows ×0.95 (a lean; off with reduced motion). A chest's screen waits 4 ticks for both hands to reach its
  lid (the menu is already open; the server is never held). The fist clips below stay for books (no world thing).
- **Touches** (P4, `anims.client.Touches`, `anims.AnimsClips`): quick hand moments from the game's own interaction
  events (client side), so every module's blocks get them by what they are: a door, trapdoor or gate (`DoorBlock`,
  `TrapDoorBlock`, `FenceGateBlock`) pushed with a palm (`fp_push`), a lever thrown (`fp_lever`), a button pressed
  (`fp_button`), a chest, barrel, ender chest or shulker box's lid lifted with both hands (`fp_lid`; the screen opens at
  once and covers it in single player), berries or a plant that breaks at a touch (no hardness, no collision: flowers,
  ferns, grass, the Living Wilds' herbs) pinched and plucked (`fp_pluck`), an animal stroked with an empty hand
  (`fp_pet`), a meal from a bowl held up with the free hand under it (a stance while eating); P5 adds the Forge (block
  id `awaken:forge`, not its class) or an anvil hammered three times (`fp_hammer`, 18 ticks: holding the blade being
  tempered, the off hand strikes) and food a campfire takes (`RecipePropertySet.CAMPFIRE_INPUT`) laid on it by the hand
  holding it (`fp_lay`). P6 adds: a palm pressed to a stone and held while the light answers (`fp_palm`, 18 ticks; the
  hand lit from within, `View.glow`, and cooled a touch, 5-18) on Awaken's waystone, conduit, ascent stone, Hearth anchor,
  rest and Stillpoint shrines, gate entrance, seal and rune (by id) and vanilla's lodestone, respawn anchor and beacon; a
  notice torn off the Spire board (`fp_tear`); a grindstone cranked (`fp_crank`); a page turned at a lectern, an
  enchanting table, or as a book or the Dream Journal opens (`fp_page`); the smithing table hammered (`fp_hammer`); both
  hands set to a stonecutter, loom, cartography table or brewing stand (`fp_set`, two-handed like the lid); any
  `awaken:*_cache` lifted like a chest. The Ritual of the Bell (`story.client.RitualFp`, a view stance): kneeling in the
  bow both palms go flat to the floor (eased in over 6 ticks), as the eyes open the main hand comes up against the bloom
  (50-132), at the End both hands rise palms out, shaking harder as the light drains (30-195), and drop at the error. At most 9 ticks each; the
  action never waits; a swing cuts them; with the main hand full the off hand does it (each has an `_off` twin), with both
  full nothing. Settings (feel): `firstPersonMoves` (P1-P3) and `firstPersonTouches` (P4), both on.
- **Station scenes** (P5, `anims.client.station`): at a crafting table (used within 10 ticks of its screen opening, not
  sneaking: sneak + use is the plain screen) the screen is replaced by `StationScreen` (kind `TABLE`), the vanilla
  crafting menu with its grid and result drawn on the table in the world instead of on the screen. The view turns to the table's top and
  narrows (×0.64) over 12 ticks (6 coming back within 12 s), out over 10; the player's own look never moves. Each
  ingredient lies on its square (the grid square to the block, row 0 the far one; the way in snapped to an axis), the
  result floats over the middle turning, the square under the cursor is outlined (Holo blue). A click on a square or on
  the result is the same click on that slot (`slotClicked`, the menu's own path: stacks, splits, shift-clicks, the server
  checks), and the grid's screen places are never hovered or drawn. Your inventory is a glass strip at the foot of the
  screen. Your hands (world source `anims_station`, 200) rest off the table's near corners and one goes out to every
  square whose contents change (and to the result as it is taken), over 9 ticks; moving, jumping, E or Esc leave.
  A hand moves only within 6 ticks of your own click on a slot, so a furnace cooking on its own moves none; taking a
  result moves the hand to the result only. Furnaces (kind `FURNACE`: furnace, smoker, blast furnace, any
  `AbstractFurnaceScreen`): worked from the front (the way in against `FACING`), the view turned to the front's middle
  (a little below its middle) and narrowed ×0.78; what cooks sits upright in the mouth (0.68 up, 0.06 out), the fuel in the firebox (0.24 up), the
  result floats out at the right (0.34 out, 0.36 across, 0.62 up) turning; a slot is picked by the nearest one the ray
  passes within 0.16 of, outlined flat to the front; the cooking (Holo blue) and the fuel left (fire) run as two thin
  lines over the strip; the hands wait at your sides under the frame (the fuel is the off hand's, the rest the main's). The
  recipe book is not in the scenes (sneak + use for it).
  Setting `stationScenes` (feel), on. Drawn for you only (others see you at the station as before).
- **The lens** (P5): the hand pass draws at a fixed 70°; anything the world anchors (arms on a pipe, at a table; near
  legs) has its view x and y scaled by tan(35°) / tan(world FOV / 2), so it lands where the world shows it on screen even
  with the view narrowed or widened (a station's zoom, the speed FOV).
- Test: `anims_fp_clips` (the file reads, every clip the body and the touches play is in it, every one-shot gives the
  hands back). Tours: `tools/autopilot/anims-pipe.txt` (P1), `anims-runner.txt` (P2), `anims-ascent.txt` (P3),
  `anims-touch.txt` (P4: filmed with the pilot's clip recorder, since a command reaches the client a few ticks late),
  `anims-station.txt` (P5), PACK=feel, HUD on. Pilot lines `use`, `attack`, `usehold <ticks>` press the real keys;
  `station <slot> [button]` clicks a slot of the open station scene.
- P1-P6 built (P5: the crafting table and furnace scenes, the Forge and campfire touches; P6: the rest of the touches, books, the Ritual's hands). Tour P6: `anims-p6.txt`. The casts (powers) still draw their own first-person
  arms (`CastAnims`); folding them into Hands is the powers module's step, coordinated with the combat plan.


---------------------------------------------------------------------------------------------------------------------

## 25. Arms (weapons and combat; plan docs/combat/PLAN.md, page docs/combat/plan.html; Euan 2026-10-25)

Euan: "Massively level up the weapons and combat system." Approved: "Go ahead with the full plan", every §7 decision
as recommended (i-frames on the dodge only; dodge = sprint + back/left/right/none; guard on right mouse; Hands grows to
weapons; vanilla swords get the verbs only; PAL optional; weapons carry an item level; a Lord-forged weapon goes to the
hand; S0 and S1 first). Then: "Do anything you can for the highest quality bar without me": the builder decides and
writes each decision here. Module `arms` (`dev.awaken.arms`), docs/modules/arms.md. Built so far: S0, S1, the sound kit.

**S0: every blow is told and has a class** (`core.api.Strikes`, §12).
- Classes: LIGHT (deflect, block or dodge), HEAVY (deflect or dodge; it breaks a guard), PERILOUS (dodge only), GRAB
  (dodge only), NONE (fire, falls, poison, the void: nothing answers it). An untold melee or projectile is LIGHT, a
  blast with an entity PERILOUS.
- Our creatures' melee is told (`MobGoals.ToldMelee` replaces `MeleeAttackGoal` everywhere): the body stops and winds up
  for 8 ticks (elites 9; bosses, wardens, breaks and nightmares 10), then swings; a target that stepped out of reach is
  a whiff. Big bodies make every third swing a HEAVY (+6 ticks, ×1.5 damage, transient `mobs_haymaker`). The flier's
  dive is told the same way (LIGHT). The tell is synced (`AwakenMob.DATA_TELL`) so the client can draw it by class.
- Abilities have a class (`Ability.strike()`): a heavy-flagged ability is PERILOUS; BOLT, ARROWS and NOVA LIGHT; SLAM,
  LEAP, CHARGE, BLINK, CONE, FANGS and FANGS_RING HEAVY; areas, beams, clouds, pulls, roots and darkness PERILOUS;
  summons, heals, guards, decoys and frenzies NONE. Every boss blow is told for at least 8 ticks (test).
- A mob's held weapon is only a look (`AwakenMob.gearStack` strips its attribute modifiers): a creature's damage is its
  def's, never its def's plus a diamond sword's.
- A broken stance staggers (`Strikes.stagger`): ours end the wind-up and stand still; any other mob is slowed to a stop.

**S1: the three verbs** (every `_sword` item and our `*_blade`s fight as the Longsword until their own style lands).
- One timing table, `data/awaken/arms/archetypes.json` (schemaVersion 1, read from the classpath on both sides at
  start: the server judges and the client predicts by the same rows, so it is not datapack-reloadable on purpose). Per
  style: guard, dodge, chain, mastery chain, input buffer (5) and late press (6); per move: wind-up, active, recovery,
  cadence window, motion (damage ×), posture, arc, reach, hit-stop ms, finisher, window, boss cap, charge, the
  first-person swing (from/to yaw, pitch, roll and lunge) and `swish` (light by default, heavy, thrust). The Longsword is
  the PLAN §4.2 table exactly; recovery scales with attack speed (×0.6..1.6 of the row).
- **Strike** (attack key): tap = the chain (strike 1, 2, 3; strike 4 with mastery); a press inside the cadence window
  earns a rhythm step (+8% damage and +1 posture each, up to 3); a press just early is buffered to the window and
  earns none; far ahead it counts for nothing. **One press is one press** (S2 fix, Euan's first play, 2026-10-25: a
  diamond sword showed nothing or one frame and a held heavy shook in place): vanilla asks again every tick the key
  stays down over a block (its mining, `Minecraft.continueAttack` through `onClickInput`), and each of those was taken
  as a new press, restarting the swing every tick; now only a press the key was not already down for counts, and the
  repeats are swallowed. Every new press strikes wherever it points; outside a fight (100 ticks) a block the weapon
  cuts faster than a bare hand (a web, bamboo, leaves; never in creative) is still cut. The old rule (any block under the
  crosshair outside a fight is mined) meant a click at the ground mined with no swing at all. Held = a heavy (charges from 6 ticks, the glint at 16, full at
  24; a release within 2 ticks of the glint is perfect: motion 2.4, posture 20, 110 ms hit-stop). **Tap or hold**
  (Euan 2026-10-26: "when you long click to power up your attack... on the first click, before letting go, it already
  attacks"): the press starts the chain's strike at once, so a tap is never late, but for a style whose heavy charges
  the strike's hit waits at its wind-up's end while the key stays down (the hands hold there, `ArmsPredict.waitHit`).
  Let go before the charge's 6 ticks and it is a tap (`PRESS_TAP`): the strike lands then. Still down at 6 ticks and
  the strike is dropped unlanded and the charge begins (`PRESS_CHARGE`; a strike the same press queued mid-combo is
  dropped too). The charge holds at full for as long as the key is down, trembling, and goes only on the release (no
  auto-release; held past full it counts as full). The server never holds a blow on the client's word alone: with no
  tap or charge heard it lands `ArmsServer.HOLD_WAIT` (4) ticks after the charge's mark. Until the press is a tap the
  waiting strike shows nothing but the first-person wind-up: no swish, no arc, no third-person clip, and nobody near
  sees the arm swing or hears it (`ArmsServer.shown`, `ArmsPredict.show`); a hold never shows it, and a tapped one is
  shown from the release, its arc on the blade's clock. A riposte, Slipcut, Undercut or Plunge starts as it always did.
  Test `arms_tap_or_hold`. A block under the
  crosshair outside a fight (100 ticks since the last) is still mined. A move strikes each creature in its arc and reach
  once, through vanilla's own attack at full strength (crits, enchantments, momentum and every damage listener still
  apply), scaled first by the move; vanilla's immunity is cleared between one fighter's blows; it never sweeps. A sweep
  only takes the aimed creature, hostiles and anything hunting the player: never a player, a villager or a pet. **What
  isn't alive** (Euan 2026-10-26: "rifts can only be broken at the core with your hands"; vanilla's own attack, which
  Arms stands aside from for its weapons, was the only way to strike one): what vanilla's attack would strike
  (`isAttackable`, not `skipAttackInteraction`: a rift's core, an end crystal, a boat, a minecart, an item frame) is
  struck when aimed, and in the arc only when tagged `#awaken:struck_by_sweeps` (the anomalies: the rift tear, the
  shimmer, the falling star), through `damageSources().playerAttack` at the move's strength (`ArmsServer.strikeThing`),
  with the impact sound, the hit-stop and the ribbon's flash; the thing decides what a blow does (a core loses a hit).
  Test `arms_strikes_things`.
- **Guard** (use key, the `blocks_attacks` component Arms gives every sword: no delay, 120° front): the first 4 ticks
  after the raise deflect a LIGHT or HEAVY (no damage, the attacker's stance takes 18 or 26, Resonance +20, a riposte
  for 12 ticks); after that LIGHT is blocked (70% less), HEAVY breaks the guard (50% less, the guard drops, 15 ticks
  before it can rise); PERILOUS and GRAB pass through. A raise within 8 ticks of lowering one that deflected nothing can
  only block (spamming is not deflecting). **Latency grace**: a guardable blow on an unguarded fighter waits up to
  latency/2 ticks (0 under 50 ms, at most 3) for a raise already on its way. **Untold blows** (Euan 2026-10-26, of
  zombies: "you don't know when it's going to hit you... so it's very hard to time a parry"): a creature's own melee
  (it strikes with its body, the direct attacker) with no wind-up to read is untold: anything not ours (every Awaken
  creature tells its blows) and not in `#awaken:told_melee` (the iron golem, the ravager, the warden): a zombie, husk,
  drowned, spider, cave spider, silverfish, endermite, piglin. Its deflect window is `ArmsRules.UNTOLD_DEFLECT` (4)
  ticks longer (the Longsword's 4 → 8; only on the early side: a raise after the blow still lets it land), and its
  deflect pays `UNTOLD_REWARD` (0.6) of a told one's Resonance and stance. Told blows keep the tight window, so reading
  a tell stays the skill; shots and players are never untold; nothing on screen foretells a blow. The combat sim's
  untold table (`combat_sim.py --untold`, docs/combat/untold-parry-2026-10-26.md): a Parrier against zombies, deflects
  40% → 70% and damage taken 117 → 58 per 100 blows once they raise into the wider window (Resonance per blow 8.0 →
  8.4); no Gate Lord or summon is untold, so the Lord table is unchanged. Test `arms_untold_parry`.
- **Dodge** (sprint pressed with back, left, right or no direction, on the ground; sprint + forward still runs): 6
  ticks, 2.6 blocks, client-predicted, server-validated (position within 1.5 blocks, charges, not in a wind-up or blow:
  a recovery can be dodged out of, which drops the rhythm). Ticks 0–4 evade any class (**the SPEC amendment: the only
  invincibility in AWAKEN**); a blow in ticks 0–2 is a perfect dodge: the attacker alone runs at half speed for 8
  ticks (every other tick; the plan's 0.45 rounded to the tick clock), Resonance +15, a Slipcut for 6 ticks. Two
  charges, 24 ticks each. A refused dodge stops the body where it is.
- **Context moves** (no input of their own): Riposte (strike ≤ 12 ticks after a deflect), Slipcut (motion 2.5, from
  1.2: the sim's reward for a dodge; strike ≤ 6 ticks
  after a dodge), Execution (a heavy on a broken creature in reach: motion 4, capped at 8% of a Lord's max health),
  **Undercut** (a strike out of a slide, the body module's own state: a low sweep, 160° wide, posture 8; the slide's
  trip still happens), **Plunge** (a strike in a fall of 2+ blocks arms it: the hands hold the blade high, the client
  dives, and it strikes all round where the fighter lands, motion × 1 + fall/6, at most × 2.8, the landing taking half
  its fall damage; it gives up in water or after 5 s).
- **The Art** (the Longsword's default): a heavy held through to full charge (2 ticks' grace) with Resonance full is
  the Art, not a heavy, and spends the whole meter (so holding past the glint is a choice). A great level sweep whose
  reach grows 2 blocks a tick through its 4 active ticks, out to 10, striking each creature once as the wave passes:
  motion 7 (3 as first built: the combat sim showed the Art dealt less than plain chaining in the same time, so a
  full meter made a fight longer; at 7 it pays), posture 30, 150 ms hit-stop, capped at 12% of a Lord's health. Its arc burns gold, a golden wave rolls on
  along the ground, and the kit's `arms.art` replaces the swish (the steel's ring swelling through the wind-up, the
  release, a wave of air rolling out). Lord-forged and legendary weapons bring their own Arts later (S8).
- **Posture** (creatures only; no player stamina): normal 20, elites, wardens and beasts 40, bosses, breaks and
  nightmares 100 (`CreatureTag`, `MobRole`). It drains at 6/s (Lords 8/s) after 2 s untouched; full, it breaks for 50
  ticks (Lords 40), then cannot break again for 6 s. On the struck creature only (the patent rule holds). Not saved.
- **Resonance** (per player, 0–100, not saved): deflect 20, perfect dodge 15, break 25, a finisher 6, a finisher in
  full rhythm (every press of the chain in its window: a cadence equal to its step; "rhythm 3" as first written was
  out of the 3-strike chain's reach) 12 more; drains 2/s after 5 s out of a fight. Full: the Art (S2).
- **Soft focus**: the creature you last struck turns your strikes toward it by at most 15°; looking more than 60°
  away from it for a second lets it go (server and client by one rule, `ArmsRules.lookedAt`).
- **Stances on the plates** (`core.api.Stances`, §12, read by the living module's plates): every stance change sends
  its snapshot (value, hold, broken ticks) to players within 48 blocks, the fighter included; their clients drain it by
  the server's own rule (`ArmsRules.drained`), so nothing is sent while it only recovers. Under the health bar, once
  strained: a thin amber strip filling from both ends to the middle (white-hot past three quarters), pulsing gold
  while broken. The fighter's focus wears the plate's bright rim.
- **Feel** (`core.api.CombatFeedback`, §12; one freeze for every blow, Powers' hits included): the struck body freezes
  and trembles for the move's hit-stop, the striker's hands and view hold for the same beat (the move's clock skips
  it), and the view kicks along the swing (≤ 2.4°) and settles; at most one freeze per 4 ticks in a sweep. A deflect
  holds both for 100 ms; a break freezes the body for 160 ms. Visual only: the game's clock never stops. Kicks follow camera shake.
- **The camera's voice and comfort** (S2, `ArmsFeel`, `ArmsComfort`: `config/awaken-arms-client.toml`). Arms owns the
  fight's camera: the kicks above, and the big moments as the field of view's push and pull, in fast over the first
  fifth and home on a long ease, a bigger moment taking the view from a smaller one: a deflect snaps in 3.5% (9
  ticks), a guard broken widens 2.5%, a break leans in 5% (16), an Execution 7.5% (22), the Art's release opens 6%
  (16), a perfect dodge breathes out 5% (18) and, with Lens on and no story moment playing, drains the frame for 12
  ticks (`awaken:arms_breath` in `lens/looks_arms.json`: colour at 0.42, cool, closed in; `LensClient.overriding()`
  says whose moment is on). Under 30% health (clear again over 36%) the heart beats (`arms.heartbeat`, 28 ticks a beat
  at 30%, 16 near nothing) and the frame's edges cool and darken (from 0.6 of full at 30% to full near nothing) (our own texture, `textures/gui/arms/low_health.png`
  from tools/art/specs/arms_hud.py), tightening on each beat; a Lens look was rejected for it: a look replaces the
  whole place's look and would flatten minutes of play. In the shoulder view the action camera takes a COMBAT pose
  while you fight (a blow either way in the last 3 s with a fighting weapon in hand, `CombatFeedback.engaged`): 0.45
  closer and 0.18 lower, the guard no longer read as an aim. Comfort, one switch per layer: `hitStop`, `viewKick`
  (0..1), `moments`, `combatCamera`, `lowHealth`; reduced motion is the body's one setting (`FirstPerson.reducedMotion`)
  and halves every kick and moment (and now the body roll's 32° dip and its twist too); Awaken's camera shake still
  turns every kick off.
- **The blow is centred on the hit** (S2): the server's sweep lands on the first active tick, so the blade's blow
  starts half the active before it (`ArmsHands.blowStart`: never before half a tick) and the recovery takes up the
  difference; the move ends when its row says. In first person, in third person (`arms_moves.py` keys the cocked pose
  there too) and in the trails. Before, the hit, its freeze and its red flash came while the blade was still cocked.
- **First person** (`ArmsHands`, a source on Hands' view layer, `FirstPerson.view`, every styled item; Euan
  2026-10-26: "like you're waving the sword instead of slicing... a bit too high"): every move plays a swing baked by
  the animation lab (tools/art/anim_lab.py `bake` -> `data/awaken/arms/fp_swings.json`, read by `FpSwings`): keys every
  0.25 ticks of the hand's move from vanilla's rest and its turn about its own place. A cut is a lever, the arm and
  blade turning as one about a pivot low and right of the view (0.80, -0.64, -0.48 from the eye), in the plane that
  holds the blade and the cut's direction on the screen (the row's swing.from -> swing.to: a slash flattened 0.6, a
  chop down, a rising cut up), the blade's flat across that plane so its edge leads (its cutting side toward the motion
  for an axe, a scythe or a glaive; a symmetric blade takes the side that turns least). On the hit tick (the server's
  first active tick; mid-blow for a released heavy) the blade crosses the target, a point just under the crosshair
  (0, -0.10, -1.20), at 78% along its length: seen along its length rising from the frame's lower right, not down it
  from the hilt. Drawn back to 50-74 degrees before it (heavier rows further), accelerating into the hit, the edge
  biting and slowing 14-22 degrees past it, a follow-through, then home. Thrusts drive straight along the aim, the flat
  level. The keys are for the style's forged weapon; a vanilla item in the style takes the same targets from its own
  hold (`vanilla` turn and grips in the file). A held heavy waits at its drawn-back start and trembles; an armed
  Plunge at the Plunge's; mirrored for a left main arm. Without keys a move falls back to the row's own slide (the old
  model). Lab numbers (the review set, 2026-10-26): every slash and chop crosses within 0.11 of the half-frame of the
  crosshair (most within 0.05; the Chain-blade's and War-staff's long blades +0.11..0.13), edge lead 0.87-1.00 through
  the blow (before: Longsword strikes 1 and 2 at 0.41 and 0.17, crossing 0.45 above). Test `arms_fp_swings`.
- **Forms** (tools/arms/forms.py writes them into archetypes.json's swing rows; Euan 2026-10-26: "a dagger should feel
  like you're actually stabbing forwards... a hammer... from up to down, or side to side... the big thing first...
  everything is just being swiped... very floaty"): every strike is done as its weapon is really used. Cuts turn the
  arm and weapon about the pivot: slash, chop (raised above the view, down), slam (the hammer raised overhead, crashed
  down head first), swing (level, a hammer's or axe's head leading), rise, whirl (the 360-degree finishers), reap (the
  scythe), lash (the chain-blade: the wrist whips twice as hard). Thrusts drive the point along the aim to the target
  (stab: a straight line, from below, overhand, or reverse-grip edge-down); the gauntlets punch (jab, hook on an arc,
  upper); the twinblades and war-staff spin about their middle; the shieldblade bashes with its shield. The combos:
  Daggers stab, rising stab, a reverse-grip rip, an overhand plunge, a spinning backhand (heavy: a deep stab); Maul an
  overhead slam, a head-first side swing, a ground-shaking slam, an uppercut; Axe a diagonal chop, a level backhand, an
  overhead chop, a wide swing; Longsword forehand, rising backhand, overhead chop, thrust; Greatblade a diagonal cleave,
  a level sweep, a whirl, an overhead chop; Spear and Rapier thrusts with a haft sweep or a flick cut third; Katana a
  level draw cut, a rising cut, a chop, a thrust; Glaive wide swings and a whirl; Scythe reaps, an overhead reap and a
  whirl; Gauntlets jab, cross, hook, uppercut, straight; Chain-blade lashes and a lasso whirl; Shieldblade cut, bash,
  chop, stab; War-staff swings, a chop and spins; Precursor Arm cuts and a thrust. A stab, jab or bash strikes narrow on
  the server (arc at most 30 degrees, the thrust's whoosh; the Daggers' stabs became single-target, their rip and
  backhand 90 degrees). Weight by style (snap: Daggers, Rapier, Gauntlets; blade; heavy: Axe, Maul, Greatblade, Glaive,
  Scythe, War-staff): how much of the wind-up reaches the cocked pose and how long it hangs there creeping back (heavy
  70%), the drive's curve (progress to the power 1.25 snap, 2.2 blade, 3.4 heavy: a heavy blow starts slow and
  arrives explosively on the hit), how far it carries past (0.45 / 1 / 1.5), the wrist's whip (6 / 18 / 7 degrees), the
  step in (0.04 / 0.07 / 0.13 blocks), how it comes home (snapped back, eased, hauled back after a beat). The view leans
  with every blow (FpSwings' camera keys, first person, scaled by the kick setting): a slam pitches it down on the
  crash (4.5 degrees heavy), a cut turns and leans it the way the blade travels, a whirl turns it furthest; heavy blows
  draw it the other way first. The third-person body of each move is picked by its form (arms_moves.py styled()).
- **First person, the hold** (tools/art/specs/arms_holds.py; Euan 2026-10-26: "a spear should still feel like you're
  holding a spear. Right now in first person, it just looks like a sword"): every style is held as that weapon really
  is, written in view terms (where the business end points: degrees left of ahead and up; its roll about its length;
  where the hand closes, from vanilla's resting hand; for an axe, a glaive or a scythe the side its working edge faces)
  and solved for each weapon from its own mesh, so each is seated in the hand. One scale for all (0.5, the Longsword's;
  the Daggers 0.575 so their blade reads): a spear is as much longer in hand than a longsword as in F5 and a dagger as
  much shorter, and a long point may leave the view. **Edge first** (Euan 2026-10-26: "You're never going to hold an
  axe on its side like that. You're going to hold the sharp ends downwards, similar to a sword"): the Longsword, Greatblade,
  Axe, Glaive and Daggers are held as the Katana is, turned a quarter about the length (roll 90), the working side down
  and toward the foe (`arms_holds.DOWN`), the flat to the side and never to the eye; the Rapier, Chain-blade and
  Precursor Arm are turned the same, an edge down; the Scythe's blade still reaches in across the top. Longsword up and forward to the left (18 left, 52
  up); Rapier en garde, the point forward at the target's height (20, 18); Katana two-handed at the middle (10, 36);
  Spear the shaft across the lower view, gripped part way, the head forward and up (46, 13); Glaive the same with the
  blade higher (54, 22); Scythe the snath up, the blade reaching in across the top (8, 64); War-staff at its middle
  across the body (58, 28); Greatblade two-handed, raised, near the middle (20, 60); Maul two hands low on the haft,
  the head up (6, 58); Axe the head up, its bit down toward the foe (10, 58); Daggers short, the
  point forward (14, 42); Twinblades at the middle, the blades either way across (62, 34); Chain-blade the head forward
  on its chain (18, 38); Shieldblade the shield to the front, the blade above it (30, 62); Precursor Arm forward like a
  drawn arm (14, 24); the Gauntlets keep their worn hold. `python3 tools/art/specs/arms_weapons.py
  holds` rewrites only the holds; the old check that kept every point inside the view (it shrank every style to the
  Longsword's outline) is gone. The swings are baked from these holds (they start and end at rest), and the lab's
  holds sheets (docs/combat/anim-lab/holds-*.png) show each style before, after and in F5.
- **The wrist and the arm** (Euan 2026-10-26: "it should actually turn it like as if you had a wrist... or a forearm
  that's turning... if you're gonna do an uppercut, you kind of turn it 180 degrees"): every cut turns the weapon about
  its own handle as the swing gathers and back square just as it lands (a slash 35 degrees, a chop 40, a slam 75, a
  hammer's swing 85, an uppercut 170: the head turned right over to drive it up), the wrist cocked so a heavy head
  trails the hands (16 degrees at least) and whips through. The arm that holds the weapon is drawn to its grip
  (§24 "Grips"): the hand closes where the style's hold puts it (arms_holds.py's grip, baked as fp_swings.json's
  `handGrip`), a vanilla item's on its sprite's handle. The lab's `strip` shows one move frame by frame (a turn in the
  hand reads there, not in an onion-skin).
- **A pair, and the left hand** (Euan 2026-10-26: "the left hand... looks like it's just floating near the hand"; "the
  gauntlet should be on the hand"): the Daggers and the Gauntlets are pairs (ArmsOffHand: the main hand's twin worn or
  held in the empty off hand, first and third person); the Gauntlets' combo alternates fists (jab, cross, hook, the other
  fist's uppercut, a straight; heavy both) and each fist closes inside its gauntlet (the mesh's centre), so the arm wears
  it rather than holding it. Every weapon's left-hand hold is its right hand's true mirror (arms_holds.mirror_display):
  26.3 mirrors any left-hand transform as it applies it (x translation, y and z rotation), so a left transform written
  pre-mirrored, as old vanilla JSON was, came out unmirrored and the twin sat beside the hand the arm reached for. A
  weapon's frame is its design's (along the item's diagonal, its flat across z), never a guess from its outline: a
  maul's head is longest along its striking faces, so the outline's thinnest direction was the way it strikes and its
  emblem side led; now the gold face leads in every direction it swings.
- **Working parts** (Euan 2026-10-26: "when it's a blade, it's the sharp end that's cutting. When it's a dagger, it's the
  sharp end that's stabbing. When it's the hammer, it's that end... think logically"): every move strikes with the part
  of its weapon that does the work, and the lab's `audit` holds it to that at the hit, on every weapon of the style in
  its own hold and in the hand that strikes (the off hand's twin in its left-hand hold, the move mirrored): a blade's
  edge, an axe's bit, a hammer's faces (never its emblem flat), a staff's ends, a point for a thrust, the knuckles for a
  punch, the shield's face for a bash, and a scythe's inner edge drawn back through. Which side cuts is read from each
  design (arms_holds.EDGES), never guessed from the outline: the katana's edge is on the outside of its curve, the tip
  curving back toward the spine, and the glaive's fluke weighs down its back, so the old guess (the heavier side) had
  every katana and glaive cut leading with the spine. Single-edged: Katana and Glaive (+x), Axe (the bit, -x), Scythe
  (the blade, -x), Twinblades (each blade's leading side, so the pair spins tips trailing), the Beastbound Longsword and
  Greatblade (tusk cleavers), the Wildhide skinning knife, the Beastbound tusk (honed on its inside) and the Veilwoven
  talon. A style whose forged (C) weapon is double-edged but has single-edged tiers (Longsword, Greatblade, Daggers)
  cuts with the side its hold's cut turns down toward the foe, and each single-edged tier is turned so its edge is
  there: one baked swing cuts with every tier's edge (the wrist turns a backhand over, as a single edge needs). A turn
  can mirror only two of a weapon's three ways, so a single-edged weapon's left-hand hold keeps its length and its
  edge mirrored and shows its other flat (before, the Wildhide twin's off-hand cut led with its back). The scythe is
  swept round, its blade's point leading, until the blade is past the target (0.12 blocks out), then hauled back 0.34
  blocks through it on the hit, its inside edge drawn through what it hooked; its blade, at the snath's top, is what
  crosses the target. Every punch lands knuckles first, square to the fist's travel (a jab and a hook palm down, an
  uppercut palm to the face, an overhand driven down); the Gauntlets' shared moves are punches too (riposte and undercut
  uppercuts, the dodge's cut a hook, the Art a straight, the plunge and execution overhands; their server blows kept;
  `ArmsClips.OWN_SHARED`: the Gauntlets play their own Slipcut, Undercut and Plunge clips, where every other style shares
  the Longsword's).
  The bash shoves the shield straight at the foe, its face square to the shove. Every move has a form in its row
  (tools/arms/forms.py writes the shared moves' too, read from their path as the lab always read them), so first and
  third person do the same thing (a spear's Art is a thrust in both).
- **Third person, the wrist** (Euan 2026-10-26: "hold third person to the same working parts"; then "it's
  literally going through his body"): vanilla's third-person hold sets a weapon across the fist, square to the
  forearm, and a vanilla arm has no wrist, so the forearm's roll swings the blade round rather than turning its edge,
  and the shared poses swept long blades through the head, the body and the legs (a chop led with the other side of
  the blade from a forehand, a katana's wind-up crossed the torso). Now through each move's clip the weapon turns in
  the fist, about its grip (`ArmsWrist`; FpSwings' `wrist3` keys: a quaternion per arm every 0.25 ticks, in the forged
  weapon's item frame; a weapon turned over in its hold, `flipped3`, takes them turned over too): about its own length,
  so its edge, bit or face lies square into the blow, and bent in its own plane and out of it (15-degree steps, 90 and
  60 at most), so it stays out of the head, the body, the legs and the other arm. The lab solves it per move and arm
  (`wrists`, `bake`): a path through the bends at every quarter tick (Viterbi; at most 45 degrees a quarter tick, held
  still through the hit, from the hand's rest and home to it; a heavy loosed from its held draw starts turned), the
  working part's lead at the hit before everything, then no weapon of the style (every tier's outline at once) more
  than 0.3 px into the figure in the straight clip and its bent twin (Bendable Cuboids, in the pack: the item rides
  the bent forearm), looking ahead or down, then the least bend. The fighter's hand items are resolved again marked
  with their arm while a move turns them (the player's render-state modifier, last), so only a hand's weapon in third
  person turns, never one in first person, the hotbar or a frame; a left main arm keeps its hold (the keys are
  right-handed). A single-edged tier on the other side from its style's lead (since the edge-first holds, the
  Beastbound Daggers; before them the Beastbound Longsword and Greatblade and the Wildhide and Veilwoven Daggers) is
  turned over in its third-person hold. The Gauntlets are worn in third person
  too (`arms_holds.worn_third_person`: the knuckles down the forearm, the back of the hand out, the cuff round the
  forearm, 0.7 scale), never held out in front of the fist, and punch their own clips, each found in the lab (knuckles
  first at the hit, the bent twin 0.8 or more, nothing into the head or body): a jab with the arm level and the body
  lunging along it, an overhand angled down with the body dropping in behind it, an uppercut from a low bent fist
  rising, a hook with the elbow bent at shoulder height, the chest turning it round. The thrust keeps its arm and
  chest on the line from cocked to through, leans in less than it lunges and bends its elbow only a little (a turn, a
  lean or a straightening forearm swept a long point's tip off its line); the shield bash raises the shield square in
  front of the chest. The lab's `audit` holds every clip to the working part on every weapon in the arm that strikes
  (the bent clip at the bar; without Bendable Cuboids the straight one at least leading with it) and measures how
  deep any weapon goes into the figure (`body` compares it with no wrist); `wrist` draws a move's blow in third person
  with and without the turn. Measured over every weapon, both clips, looking level: moves putting a weapon more than
  1.5 px into the figure went from 99 of 179 without the wrist to 53 (the Katana, Glaive, Scythe, Spear, War-staff
  and Gauntlets none). What still goes in: a few designs' own bulk at rest (the Hive Regent's glass-wing knuckle guard
  along the forearm, the Gearwright Colossus's fist against the hip) and the wind-ups of the shared one-handed sword
  poses for the longest and widest weapons (the Greatblade's, the Twinblades' far blade, some Longswords): those need
  their own third-person poses and two-handed grips, not a wrist. The scythe leads point first in third person; its haul back through
  the target is first person's.
- **Third person from first person's blade** (Katana and Greatblade so far; Euan 2026-10-26: "how are we gonna get
  that exactly the same as first person... so that it genuinely looks really, really good"; tools/art/specs/
  arms_rig4.py). First person is designed from the blade (its path, the edge leading, through the target on the hit);
  third person was designed from the arm (a few poses per kind, the weapon wherever the hand put it), so long blades
  swept through the body and a two-handed weapon was held in one hand. Now third person takes each move's weapon from
  first person's own baked keys (the grip's place and the blade's turn every quarter tick) and the body follows it:
  first person's arc about its pivot is carried onto a point before the chest's middle (both hands meet on the hilt
  there), turned as a whole so its line into the target runs from there to a point before the face, its lever scaled
  to the figure's reach, level with the view (a slide's or a fall's lean doesn't tilt it). Through the drive, the hit
  and the cut the blade is exactly first person's; in the wind-up and the follow-through only, the move may be turned
  as a whole about the pivot (+36 to -30 degrees: first person cocks an overhead chop back past the top of the view
  and carries a long blade down out of the frame, which in a figure are through the head and the legs). The grip
  shifts a little where it must (out, ahead, up, in toward the middle), chosen over the whole move at once (Viterbi):
  a blade by the face counts most, then any weapon in the body, then both hands reaching. The chest turns with the
  lever, half a tick ahead of it (70% of its turn for the Katana, 80% for the Greatblade, at most 55 degrees); the
  sword hand closes on the grip and the off hand on the handle toward the pommel by IK, elbows bent (Bendable Cuboids)
  or, for the straight clips, solved straight with their own wrist keys (`wrist3s`; `ArmsWrist` plays them when
  Bendable Cuboids isn't loaded); an arm may graze the body, never go into it. Where the off hand can't reach the
  handle (a follow-through carried round to the far side, the low sweep out of a slide) it lets go cleanly rather
  than float beside the hilt. These styles play their own Slipcut, Undercut and Plunge (`ArmsClips.OWN_SHARED`) and
  hold their own heavy's first pose while charging (`arms_<style>_heavy_charge`, `ArmsClips.charge`), so the
  release never snaps. Measured (`arms_rig4.py bake`, the lab's audits, every tier of the style, looking level and
  down): every move leads with its edge or point at the hit; both hands on the hilt through 81-100% of every Katana
  move and 73-100% of every Greatblade move but the Undercut (one-handed out of a slide); the blade more than 1.5 px
  into the head, body or legs in 5 of their 22 moves (8 before; the Greatblade's from up to 3.7 px to about 1.3). What
  still touches: a two-handed hilt's pommel by the belly (a figure's arms meet at most ~4 px before its chest, a hilt
  reaches 5-9 px behind the hands: in 16 moves, mostly the Greatblade's longest tiers, past the blow), and the
  Executions' first quarter tick out of rest, where the blade passes the head looking down. Review set:
  docs/combat/anim-lab/third-<style>-blade-first.png (shipped against new, every move from behind-right) and
  fp-third-<style>-<move>.png (first person over third person from behind-right and the side, at the same moments).
- **The animation lab** (tools/art/anim_lab.py; SPEC §25, Euan's ask for a way to see the motion): `sheet` / `all`
  draw each strike's first-person frames as the game composes them (the swing, Hands' view layer, vanilla's rest, the
  weapon's hold and mesh, 70 degrees) as an onion-skin with the blade tip's path (wind-up blue, blow gold, recovery
  grey), the hit ringed and the crosshair; under it the tip's height on screen and the edge lead over the move; beside
  it top and side views of the grip and tip paths against the view cone (`--model old` shows the row's slide).
  `third` draws the body's clip (arms_moves.py) with the blade in its hand as vanilla's ItemInHandLayer holds it, two
  views and the third-person edge lead (a thrust: the point lead). `audit` checks every move's working part ("Working
  parts" above; exit 1 when one fails). `film` overlays the game's own frames (the clip
  recorder's, in a tour) the same way. Sheets for the review set in docs/combat/anim-lab/.
- **Third person** (Player Animation Library, optional, through `core.api.PlayerClips`; `ArmsClips`, `ArmsBodies`):
  a full-body clip per move from tools/art/specs/arms_moves.py, its keys on the timing table's rows (cocked at the
  wind-up's end, through at the active ticks' end, home by the endTick), each with a bent twin; the guard held while
  it is up, the heavy's draw held while it charges (a blade-first style's own: its heavy's first pose), the dodge back
  or to a side by its way (mirrored for a left
  hand). The fighter's own from their prediction, everyone else's from `Seen` MOVE, CHARGE and DODGE. Every clip is
  checked for an arm through the head or body (under 1.25 px). Without the library: vanilla's arm swing.
- **The blade's own ribbon** (S2, `BladeTrail`; PLAN §4.8's SWEEP): while a blow is in the air the blade lays a
  ribbon over its outer part (the outer quarter of a vanilla blade in first person, where the blade fills a third of
  the view; ours from 78% of the way along to the point), sampled every frame from where it is drawn: the fighter's own in first
  person (any styled weapon; a vanilla sword through `item/handheld.json`'s display transform and its sprite's blade,
  pixel (5.5, 10.5) to (15.5, 1.5); ours from the mesh, the corners half way along to the furthest one), and our
  weapons in anyone's hand in third person. The samples live in the world 150 ms (a turn of the head leaves the ribbon
  behind), run on a Catmull-Rom curve between frames, and the head is carried on along the blade's last motion to the
  present frame so it never trails the steel. Drawn in two passes: a shade (the colour at two fifths, alpha-blended,
  `arms_trail_shade`) under the light (additive, the weapons' glow pipeline), so it reads on a noon sky as well as at
  night (additive alone vanished by day: the frozen-ribbon tour showed the geometry right and the light invisible);
  alpha 0.55 + half the rhythm's brightness, nothing at the root, a soft rise, a crisp whitened strip along the tip's
  path, out with age. `arms trailfreeze on` (autopilot) holds ribbons still for a frame; `arms ribbonlog N` logs
  samples and then draws them flat red to check the geometry. The hand-arc crescent below stands aside wherever a
  ribbon is laid; it stays for vanilla blades seen from outside. The first tour on the Mac showed the crescent floating
  off the blade ("laggy", Euan).
- **Trails and sparks** (`ArmsFx`, on the powers module's effects engine: one budget, one settings page). As a
  blow starts (the end of its wind-up), its arc: a crescent following the hand's own arc about the shoulder, pushed
  out 1.5 blocks ahead of the eye so it faces whoever looks (an arc in the plane of the look is seen edge-on, a line:
  found in the tour), nothing at the hand, a tinted body and a crisp bright rim along the blade's visible middle,
  its head crossing in the move's active ticks on the hands' curve, its tail chasing it. A thrust is a straight
  streak. The weapon tints it (diamond cyan, netherite violet, gold, copper, iron, stone, wood; our blades Holo blue),
  each rhythm step brightens it, a perfect heavy and an Execution burn gold; the fighter's own is softer in first
  person. Where a blade lands, on the body's near side: a small white flare and sparks by what it met (warm off plate
  and stone, pale chips off bone, brown off wood, wisps through a spirit, green spatter from slime, dark red from
  flesh). The fighter's from their prediction and the HIT result; everyone near from `Seen` MOVE and HIT (observers
  also see the struck body's freeze).
- **HUD** (`arms_hud`, above the crosshair layer): a 15-dot arc under the crosshair for Resonance or the heavy's charge
  (gold when full or at the glint), the dodge's two pips beside the sprint line, and a red shard on a ring round the
  crosshair pointing to where the last blow on you came from (any held item; 24 ticks). Nothing at rest.
- **Network**: `arms_press` (strike, charge, release; seq, look, aimed id), `arms_dodge`, `arms_result` (to the
  fighter: move, hit, deflect, block, guard break, perfect dodge, evade, break, refuse, dodge, resonance, hurt) and
  `arms_seen` (to players within 48 blocks). The client predicts every move and takes the server's word.
- **Config**: `awaken-arms.toml` `enabled` (server, synced; default on). Off: vanilla melee, and the guard Arms gave a
  sword never rises (a sword another mod made to block keeps its own).
- Off while riding, sleeping, projecting, meditating, flying or gliding; Arms never runs a spectator's melee.

**Styles** (S5 batch 1, the timing table's rows beside the Longsword's; PLAN §4.5): every style has the full set of
moves (a three-strike chain and a mastery strike, the heavy and its glint, riposte, slipcut, execution, the Art,
undercut, plunge), its own guard and dodge numbers and its base speed (recovery scales with the item's attack speed
against it, so a vanilla item plays its designed tempo).
- **Axe** (0.9; vanilla's axes): chops on a 3-tick wind-up, a finisher cleave from overhead (motion 2.0, posture 18),
  a hooking mastery strike; the heavy is a guard-breaking hew (posture 24, 34 on the glint); its Art a whirl all round.
- **Maul** (0.7; the mace): slow heavy swings (wind-up 4), the finisher a slam all round (arc 360, posture 26), the
  heavy an overhead crash all round (posture 30, 44 on the glint), a plunge that hits harder (motion 1.3); its Art the
  Earthbreaker's wave. Its blows land blunt (`arms.hit_blunt`) on every body but a spirit.
- **Spear** (1.1; our forged spears; vanilla's spears and the trident keep their own right mouse, the charge and the
  throw, until the style can share it): thrusts at 4.2 blocks on a 25° arc, the finisher a wide sweep, the heavy a
  lunge to 5 blocks; its Art a thrust whose wave runs 12 blocks.
- **Greatblade** (1.0): wide heavy cleaves (arc 130, reach 3.4), a spinning finisher all round, an overhead mastery
  strike; its Art a great sweep.
- **Rapier** (1.8): quick thrusts at 3.6 on a 20° arc, a slash finisher; a riposte of motion 3.4 and posture 24 with
  a 16-tick window, the deflect window 5 ticks: the parry weapon.
- **Daggers** (2.2): a four-strike chain (two cuts, a stab, a wide finisher) and a fifth stab with mastery; the
  shortest dodge (5 ticks, 2.2 blocks, recharging in 16) and a Slipcut of 2.8 with an 8-tick window; a blow from
  behind (the striker more than 120° round from where the body faces) lands ×1.8.
- **Katana** (1.5): a sweeping chain from the draw; the first strike after the blade has rested 2 s (40 ticks since
  the last move ended) is drawn from the sheath: ×1.7 motion, ×1.8 posture. The deflect window 5 ticks. Its first
  strike winds up 3 ticks and recovers 5 (1 and 7 as first built: Euan 2026-10-26, "it starts off low, and then all of a
  sudden he's already to the right side"; the cut from the right now reads before it lands, the move as long as
  before). Every style's first strike was checked: each slash winds up 2 ticks or more; only the thrusts and jabs
  (Rapier, Daggers, Gauntlets), short in travel, keep 1.
- **Twinblades** (2.0, a double-ended blade): a fast four-strike chain on tight rhythm windows closing on a spin all
  round, a fifth spinning back with mastery; its Art a whirl. Its first strike winds up 2 ticks and recovers 3 (1 and 4
  as first built: a wide slash in one tick was a blur).
- **Glaive** (1.0): sweeping arcs of 160° at 3.8 for crowds, a finisher all round, a thrust with mastery.
- **Scythe** (0.9): wide reaps (150°) that rise and fall, a finisher reaping down across; a spirit struck ×1.6 (it
  cuts the Veil's spirits).
- **Gauntlets** (2.4): fist strings (a four-punch chain on 1-tick wind-ups, an uppercut finisher, a fifth with mastery),
  a dodge of 5 ticks recharging in 18; a riposte that throws the body (knockback 1.3).
- **Chain-blade** (1.3): lashes at 5 blocks, a thrust to 6, the heavy to 6.5; each blow landed reels the body up to 2
  blocks in.
- **Shieldblade** (1.4): block-first, the guard taking 85% (70% of a HEAVY), 150° wide, broken for only 10 ticks; the
  second strike a shield bash; the riposte a bash that throws (1.0).
- **War-staff** (1.2): sweeping two-handed blows; each blow landed gives back 3 power energy (`Powers.restoreEnergy`),
  so a staff fighter's powers come round faster.
- **Precursor Arm** (1.5, Tech): an energy blade whose reach grows half a block with each step of the chain (2.6 to 3.6
  by the finisher), the heavy at 4; its Art a wave to 14 blocks.
- Style traits (`Archetype.Traits`, optional per style in the table): `backstab`, `drawIdle`, `drawMotion`,
  `drawPosture`, `spirit`, `pull`, `energy`, `reachPerStep`, `riposteThrow` (`ArmsRules.behind`, `ArmsRules.drawn`;
  the server applies the rest as the blow lands; a Lord, 150 health and up, is neither reeled in nor thrown; test
  `arms_style_traits`).
- Third person: `arms_<style>_<move>` clips on each style's own ticks, generated from the authored poses by the move's
  kind (a side cut, an overhead, a thrust, a sweep round), heavier styles leaning and sinking further; the slide's,
  the fall's and the dodge's strikes are every style's. Strike 1's follow-through passes out in front (a waypoint) so
  the arm never crosses the chest, and finishes out past the off side so the blow reads from behind.

**Sounds** (`ArmsSounds`, 26 events `arms.*`, 76 files; tools/art/specs/audio_arms.py).
- Hybrid by design: CC0 recordings (Kenney's Impact Sounds and RPG Audio, `tools/art/sources/kenney`, CC0 1.0, licence
  files kept) for the physical transients and bodies synthesis renders thin; the audio kit for the air, the steel's ring
  (inharmonic modes), the weight, the shimmer, the rooms and the tells' language. Every event is layered (transient,
  body, tail, sweetener) and has 2–5 seeded variants.
- Loudness category `arms` in audio_mix.py: −14 dBFS gated RMS ±3, true peak ≤ −1 (vanilla's own strong attacks sit at
  −11..−14 and its shield block near −9.5: at AWAKEN's general −20 a blade would sound weaker than a wooden sword).
  Every render is checked: loudness and true peak after encoding, a blow's transient in its first 6 ms (it meets the
  hit-stop's frame), no click at either end, no 2–5 kHz harshness past its family's, variants spectrally distinct. The
  checks catch a broken sound, not a dull one: the listening page (`tools/art/arms_listen.py` →
  `docs/combat/listening-<date>.html`: nine scenes at the game's own timing, read from the table, and every sound
  alone, each with keep / remake / cut) and Euan's ear are the last gate.
- Which sound a blow makes is chosen in one place for both sides: the swish by the move's `swish`, a semitone higher
  per rhythm step; the impact by what the blade meets (`core.api.BodyMaterial`, §12: flesh, armour, bone, wood, stone,
  spirit, slime; flesh lands heavy from motion 1.4: the finishers, a heavy, a riposte; a heavy on a hard body plays a
  little lower); an Execution is a blow of its own. The fighter hears their own client's copy on the frame it happens;
  everyone else the server's (`playSound` excluding the fighter). Vanilla's attack sounds are silenced while an Arms hit
  resolves. Deflect, block, guard break, break, perfect dodge, dodge, the heavy's draw and its glint each have theirs.
- **Tells by eye** (S3, `ArmsTells`, from the synced tell, `Strikes.tell`): LIGHT, a white glint on the striking
  hand 5 ticks before it lands (the deflect window is 4: a tick to see it, then the press); HEAVY, an amber flare and
  ring as the wind-up starts, then the hand burning amber, brighter as it nears; PERILOUS, a big red flare at the hand
  that keeps its red, the hand glowing, and a red rune ring flat on the ground under the creature for the whole wind-up,
  tightening from its reach to the body's own width as the blow nears (over the head, the first plan, it hid behind
  the name plate); GRAB the same in violet. The striking hand is ahead of the body
  at its reach, toward its main arm. On the effects engine with the new `FxPool.NEAR` (drawn even inside 1.5 blocks of
  the eye, where every other sprite fades: a blow is told at sword's length; the fighter's own first-person motes use
  it too), `FxClient.tellMark`.
- **Tells by ear**: each class has one voice on every creature (`Arms.tellSound`: light a bright rising tick, heavy an
  amber swell, perilous a two-tone shriek nothing else makes, grab a creak of reaching): the mobs module plays it with
  every told blow; a NONE ability keeps the mob's own wind-up (`mobs.telegraph`).
- **Hurt**: a blow of any class landing on a player plays a blunt body thump to that player only (`arms.hurt`, no
  voice), with the shard and a small view jolt from where it came (vanilla's own tilt stays).

**Weapons** (`ArmsItems` from the generated `ArmsData`; tools/art/specs/arms_weapons.py, its catalog the source).
- The six forged Longswords, one per Hunter tier (Wildhide, Beastbound, Gatewrought, Veilwoven, Lordscale, Sovereign):
  the tier's Hunter blade plus half a point of damage (4.5 to 9.5 over the player's 1), its uses, rarity and
  enchantability, the Longsword's speed (−2.4), mended by the tier's repair tag. Each fights with its row's style
  (`Arms.plainStyle` reads the row before the name rules), is in `#minecraft:swords` (vanilla's sword enchantments)
  and lights its swings in its tier's colour (the row's `trail`).
- Until the Forge's blueprints (S7), each is crafted from its tier's Hunter blade between the tier's core and bind
  (a column: core, blade, bind); the recipe unlocks when the blade is held, like every AWAKEN recipe.
- **Built to Euan's reference** (`/Volumes/MEDIA/arms-ref/weapon-style-ref.webp`, a Minecraft-Dungeons-style hero
  with a great hammer; the look bar for every Arms weapon). Two passes came before it: plain JSON cubes ("still very,
  very blocky... push it to the maximum") and lofted sculpted meshes at 4 texels a unit (2835ede4: "too thin and
  noisy against his reference"). Now (2026-10-26, `tools/art/voxel.py`): **designed box construction at the player's
  own density**, 1 texel = 1 item unit (a skin pixel), each face its own chart, buried texels dropped and visible ones
  merged; the style rules every archetype follows are in `docs/combat/weapon-style.md` (density, construction from
  bold parts and whole-angle turned boxes never staircases, a longsword about 28 texels with two thirds blade,
  painted bevels as one-texel bands lit toward the painted light and dark away with contact shadows at joins, three
  or four hue-shifted ramps with one dark and one warm, a few deliberate marks per part and no per-texel noise,
  stones in 45° frames standing proud with concentric painted faces, emissive by flow hilt to tip). Each weapon still
  writes the same `.mesh` (`WeaponMesh`: item-space corners with normal, uv and how far along the weapon each lies;
  the points that glow; then, under header flag 1, the streams of light that lift off it), 244–510 triangles (budget
  6,000, `ArmsTests.MESH_BUDGET`); `check()` repeats `arms_assets` and fails on two faces sharing a plane on different
  texel grids (they z-fight). Review: `python3 tools/art/specs/arms_weapons.py sheet OUT.png` puts the reference beside
  every weapon in three-quarter, back, hilt close-up, first person at rest and in vanilla's hold, night and the icon
  (`docs/combat/weapons-voxel-2026-10-26.png`).
- **Drawn by our shader** (`arms_weapon` pipeline; `WeaponRenderer`, the `awaken:weapon` special item model): vanilla's
  two item lights partly banded (painted light, not plastic), metals reflecting a soft studio (a cool sky, a warm
  floor, a strip light), a gloss highlight, a rim of the weapon's accent, the world's light on all but what glows; a
  sheen runs hilt to tip every five seconds; emissive parts pulse in order along their flow (runes light in turn up the
  blade, the circuit climbs, the molten edge runs to the point, the spirit edge flows); an enchantment is our own
  violet shimmer. Optional pipelines: if a shader pack or driver can't build them, the same mesh draws with vanilla's
  entity light. The stones carry a soft halo (additive, facing the eye, its falloff in the vertex colours) that
  breathes slowly and feeds the Lens bloom.
- **Light that lifts off it** (S2, `WeaponMotes`; the mesh's streams, below): Lordscale's molten edges shed embers
  that rise, slow and cool from white-hot through its gold to a dull red; the Veilwoven's spirit edge lets go of wisps
  that swell, curl and fade; motes hang round the Sovereign's stones and drift up off its burning rune channel (and
  round the Veilwoven's heart stone). The lower tiers shed nothing: the light is a reward of the high ones. Each stream
  lets particles go at its rate, two and a half times more at a full charge, from points along its line through the
  very transform the weapon is drawn with, into the shared effects engine (`FxClient.weaponMote`: its pool, budget and
  particle setting; our own EMBER, WISP and MOTE sprites), so a swing leaves them hanging behind the blade. In first
  person a point on screen is carried out to where the world's own field of view puts it (the hands draw at 70°).
  Nothing while a screen is open or the game is paused.
- **It carries the fight**: the fighter's own blade fills with its accent's light as a heavy charges, flares on the
  glint and on a perfect heavy, and hums while the Resonance is full (the Art ready); its halos swell with it.
- **First person**: a hold of its own (`<id>_in_hand.json`, which draws nothing itself; scale 0.5 for the voxel
  blades, whose points ran off the top at the lofted ones' 0.6): the whole blade standing at the right with its point
  in view, its flat turned a little to the eye (vanilla's sword sits so close a longsword
  shows a fragment); the swings move the hand as for any sword. Third person: vanilla's handheld hold. Size: a fifth
  longer than vanilla's sword (`SCALE` 0.9 of sword units).
- **Inventory icon** (32×32; `items/<id>.json` selects on the display context like the trident: the icon in the
  inventory, on the ground, in frames and on shelves): rendered from the same mesh, painted clean (no dither or
  grain), each pixel the surface's colour at its centre, the silhouette by coverage, vanilla's dark outline.
- Checked before writing (each weapon: budget, inside the item box, atlas fits, icon present); `arms_assets` reads
  every mesh with the game's reader (budget, bounds, whole normals, texels in the albedo half), the items, recipes,
  files and that the hands route to the weapon renderer. tools/art/mesh_render.py renders any weapon with the shader's
  own terms (turntables, close-ups, and first person with vanilla's exact hand transforms and 70° projection, which
  matches the game frame); tools/autopilot/arms-weapons.txt is the in-game tour.

**The combat sim** (S3, `tools/sim/combat_sim.py`, report `docs/combat/sim-2026-10-26.md`): every Lord at its home
rank against four profiles (Tank, Dodger, Parrier, Expert) and a diamond-sword Parrier at D, the numbers parsed from
the sources at start (timing table, bestiary, scaling), seeded. It found three bugs, fixed: every blow lands as the
class it was told as (a bolt carries its ability's class, our fangs are HEAVY, hazard and aura pulses PERILOUS,
through `Strikes.installClassifier` and `Strikes.deliver`; before, Force bolts landed as NONE and could not be
answered at all); an answered blow (deflected, evaded, blocked whole) brings none of its burn or effects; a broken
stance starts no ability until it stands. Tuning for the whole game's pacing (Lord damage by rank, Lord health,
fliers in reach, vanilla's flat burn and fang, per-Lord health) is recommended there and waits for Euan's three-Lord
session (PLAN §6.4 S3 gate): it moves powers players' fights too.

**Weaves** (S8, `ArmsWeaves`; `core.api.WeaveHooks`, §12: the powers module asks it for a cast's energy and tells it
of each landed cast): for 12 ticks after an opening the blade earned (a deflect, a perfect dodge, a Break, a finisher in
full rhythm) a power cast fuses with the weapon and costs 30% less. Four openings × nine families = 36 Weaves, each
named (the deflect's Ripostes: Iron, Gale, Weighted, Ember, Umbral, Mirror, Bulwark, Arc, Soul; the dodge's echoes:
Quake Step, Slipstream, Falling Star, Flashpoint, Afterimage, Phantom Step, Glass Wake, Ghost Circuit, Wisp Echo; the
Break's: Sundering, Whirlwind Break, Collapse, Meltdown, Eclipse, Shattered Will, Keystone, Overload, Severance; the
rhythm's edges: Hammer Rhythm, Tempest Edge, Heavy Hand, Burning Edge, Night Edge, Thought Edge, Mason's Edge, Charged
Edge, Spirit Edge). The opening is the shape: a deflect bursts a 4-block ring off the meeting of the blades (the
fighter's attack ×1.2, knocking back); a perfect dodge leaves an echo where the dodge began that strikes 6 ticks later
(3 blocks); a Break sends the power through the broken body (×1.6, at most 3% of a Lord's health) and holds the Break
a second longer; a rhythm finisher charges the next three blows within 5 s with 40% more as the power's force. All
scale a little with the power's level (+8% a level). The family is the element (the power's own Force, else the
family's: Energy and Tech Charge, Flight and Architect Gravity, Shadow, Mind and Spirit Spirit) and the look (the
effects engine's style of the family). The first time each is woven the System names it ("WEAVE · Ember Riposte", a
toast); after that the action bar does. Kept in `awaken:arms_memory` (schemaVersion 2, saved, kept through death; the
Codex's Weaves page counts it as N / ??, never naming the rest). Test `arms_weave`.

**Lord-forged** (S7, `LordForged`, hand-written beside the generated ArmsData rows): one weapon for each Gate Lord, in
the style that fits it, a step above the forged weapon of its home rank (that weapon's damage + 1, its speed, rarity
EPIC; it only drops, never crafts). A Lord killed by a player (the death event; a decoy's role is NONE, so a reflection
gives nothing) gives its weapon 35% of the time, and certainly on the third Lord in a row without one (the Power Cores'
pity; the count, `lordsWithout`, is in `awaken:arms_memory`, schemaVersion 2). The reveal comes on the next server
tick, after the kill is tallied (so a kill with an empty hand still counts as unarmed): a pillar, a ring and a nova of
the Lord's family light round the fighter, the totem and beacon sounds, the System's toast "LORD-FORGED · <name>", and
the weapon in the main hand if it is empty (else the inventory, else at the feet). Its Art is the style's own and then,
on the Art's first active tick, the Lord's signature move: a shape on the effects engine in the family's look, the
Lord's Force, a reach, ×power of the fighter's attack, what it leaves, and a pull in or a throw out (never on a body of
150+ health, and a Lord takes at most 5% of its health from one). The fifteen (home rank, style, Art): Bell Warden (E,
Maul, Grand Peal: a 9-block Spirit ring that slows), Mother of Spores (E, Scythe, Spore Rain: poison), Ember Tyrant (E,
Axe, Eruption: a Heat slam that burns and throws), Hive Regent (E, Daggers, Royal Dive: a poisoning cone), Root Saint (E,
War-staff, Root Lance: a Gravity ring that roots), Drowned Archivist (D, Spear, Tidal Page: a blinding cone that washes
them back), Gearwright Colossus (D, Gauntlets, Piston Slam: a Charge slam that staggers), Glass Serpent (D, Rapier, Glass
Storm: weakness), Lantern Broker (D, Chain-blade, Last Light: a Heat nova that burns and reels in), Abbot of Frost (C,
Glaive, Glacial Vow: a Frost nova, deep slow), Mirror Knight (C, Shieldblade, Reflections: a Spirit ring that marks),
Unit Zero (C, Precursor Arm, Zero Point: a Gravity vortex pulling in), Sleeper (C, Katana, Waking: a Spirit ring that
all but stops them), Sky Leviathan (B, Twinblades, Storm Eye: a Charge nova that lifts them), Hollow Herald (A,
Greatblade, Event Horizon: a Gravity vortex, darkness, the strongest pull). Test `arms_lord_forged`.
Each Lord-forged weapon also carries one Mark of its Lord (Bell Warden Rebuke, Mother of Spores Feast, Ember Tyrant
Chain Reaction, Hive Regent Windrunner, Root Saint Steady Breath, Drowned Archivist Echo Return, Gearwright Colossus
Earthsplitter, Glass Serpent Counterspark, Lantern Broker Harvest, Abbot of Frost Parry Ward, Mirror Knight Mercy, Unit
Zero Overcharge, Sleeper Phantom Cut, Sky Leviathan Crescendo, Hollow Herald Sunder).

**Mastery** (S7, `ArmsMastery`): five steps for each style, from the kills made with it (20, 60, 150, 350, 800; a kill
of a foe with the style in the main hand; kept in `awaken:arms_memory` `kills`, schemaVersion 3). Each adds to the same
presses: I the chain's mastery strike (the style's `masteryChain`, the 4th or the 5th: a press from 2 ticks after the
finisher's blow until its recovery ends chains on, a chain in full rhythm staying in it, so that strike can be a
full-rhythm finisher too), II a Slipcut window 3 ticks longer, III a charged deflect (posture to the attacker and the
deflect's Resonance ×1.5), IV every Resonance gain ×1.2, V the Art at 80 Resonance instead of 100. A new step is named
by the System ("MASTERY · Longsword II · a longer Slipcut"); the steps go to the client (`arms_mastery` payload, by
style index) for the prediction of I, II and V, and show under a weapon's name. Dev: `/awaken arms mastery <style>
<kills>`. Test `arms_mastery` (and `arms_timeline`'s mastered finisher).

**Marks** (S7, `Marks`): 48, every trigger with every effect, in `data/awaken/arms/marks.json` (written by
tools/arms/marks.py; read once on both sides, like the styles). Triggers: a landed hit, a finisher in full rhythm, a
deflect, a perfect dodge, a Break, a kill, a blow guarded (blocked, not deflected), a perfect heavy. Effects: a rider (×
the fighter's attack as a second blow of the weapon's attuned Force, else the Mark's own, else plain; on a Lord at most 2%
of its health), posture (points; a Break's: the Break held longer), a heal (share of max health), power energy, Speed I
or Resistance I (ticks). A kill's rider and posture go to the foes within 4 blocks. A weapon carries at most two, with
its attunement, in the item component `awaken:arms` (`Marks.Instance`, schemaVersion 1, saved and synced, moves with the
item). Until the named weapons (S9), an Elite a player kills drops a forged weapon of its rank (the content level's) in a
style at random 4% of the time (a Warden 10%), glowing, with one Mark (a second 20% of the time), and the System names
it ("MARKED · Gatewrought Glaive · Rebuke"). Tooltips list each Mark and what it does. Dev: `/awaken arms mark <id>`.
Test `arms_marks`.

**The Forge** (S7, `ArmsForge`): sneak and use Hearth's Forge (any placed `awaken:forge`; a plain use stays Hearth's
tempering and upgrades). Holding one of our weapons: attune it to a Force named by the catalyst in the off hand (since
W2, §28.3: Sunshard Heat, Frostspar Frost, a Fault Seam Gravity, a Copper Ingot Charge, a Deepglass Shard Spirit) for 6
essence, a Core Shard and the catalyst; re-attuning replaces it. An attuned weapon's Mark riders take its Force, and it primes both
ways: a landed hit makes the next power within 3 s cost 15% less (with a Weave's 30%: ×0.7×0.85), and a landed power of
its Force makes the next blow within 3 s land ×1.25 with the Force's touch (Heat burns 3 s, Frost slows, Charge 6
posture, Gravity lifts, Spirit heals 1.5). With an empty hand: the Blueprints, one design each use in turn, each for 4
paper, a lapis (ink, since W2) and an essence shard. Every style but the Longsword needs its Blueprint at the crafting table: the
Blueprint sits in a free cell of the style's own shape (tools/art/specs/arms_weapons.py `blueprint_pattern`; `python3
tools/art/specs/arms_weapons.py recipes` rewrites only the recipes) and comes back out of the grid. Blueprint art: the
style's Wildhide silhouette in white line on blueprint paper (tools/arms/blueprints.py). Dev: `/awaken arms attune
<force>`. Test `arms_forge`.

**Item level** (PLAN §7.7, decided by the sim): a weapon's level is its tier. The forged Longswords' damage climbs by
tier (4.5 to 9.5) and melee already rises with the player's strength, so on-level melee keeps pace with a Lord's
health; scaling weapons by level as powers scale (sim `--item-level on`) broke the ordinary fight (normal creatures fell
in 1–2 hits from D up) and let a Tank win at C and A. No extra level is carried.

**Tests** (`arms_`): timeline, defence rules, strike arc, damage stack, deflect, deflect latency, dodge, posture break,
kill switch, sounds, stance rules, clips, the Art, the Plunge, assets; `mobs_held_weapon_damage`, `mobs_told_melee`, `mobs_ability_classes`, `mobs_body_materials`;
`audio_events_resolve` covers the kit's files and subtitles. S2/S3: `arms_hand_pose` (the swing on Hands' view layer),
`arms_assets` (the streams), `mobs_bolt_class`, `mobs_answered_no_burn`, `mobs_broken_idle`.

---------------------------------------------------------------------------------------------------------------------

## 26. How AWAKEN tells you things (the notification director, discovery, the rift; Euan's playtest 2026-10-26)

Euan, at level 3 in a forest: a lair rumour, "Something tore open nearby" and a "New creature" toast whose hint named a
plant arrived seconds apart; he hadn't looked at anything, barely heard the rift and could hardly see it. The audit
(26.6) found ~400 Signal call sites, a dozen client HUD channels that ignore each other, and no global pace at all.
The rules below replace that.

### 26.1 Principles
- **World first, HUD second.** A happening announces itself in the world first: sound with distance and direction,
  light, motion. Text is the last resort. A rift's word follows its tear by 2.5 s (`AnomalyDirector.WORD_AFTER = 50`).
- **One director.** Every System message that isn't a decision, and every module's own HUD line that wants the
  player's eye, goes through the Signal client's `Herald` (pure pacing logic in `signal.ui.Pace`, GameTested).
- **Discovery is earned and understood.** Intent, never proximity alone; the first find teaches; the rest are quiet.
- **Nothing is lost.** What isn't shown is filed in the Journal (K), and finds stay in the Codex for good.

### 26.2 Tiers, pace and context (`core.api.Notices`, `SignalWindow.tier/topic/moment`)
- **CRITICAL** bypasses everything: a decision (any window with an action), a WARNING kind, a GATE panel, a dangerous
  region's caption, a level-up pane, an Ascent gift scene (those two claim the moment so what follows waits its gap).
- **IMPORTANT** (the default for everything else) is paced. **AMBIENT** never interrupts at Standard: filed with a cue.
- A **moment** (`Builder.moment()`): a first of its kind, a wonder, the first rift. Shown as the centre banner
  (`signal.client.Moment`: spaced label = the window's footer, a line of light, the name ×1.8, ≤2 lines; ~5 s +6 ticks a
  word, at most +6 s; sound `awaken:ui.moment`), at 20% of the screen height; shows at every density.
- **Pace** (ticks; density `notifications` in `awaken-client.toml`, `AwakenConfig.Client.NOTIFICATIONS`):

  | density | gap | calm gap | burst / window | ordinary waits | moment waits | IMPORTANT | AMBIENT |
  |---|---|---|---|---|---|---|---|
  | MINIMAL | 600 | 900 | 1 / 2400 | 600 | 3600 | filed | filed |
  | STANDARD (default) | 160 | 280 | 3 / 1800 | 900 | 3600 | shown | filed |
  | GUIDED | 80 | 120 | 5 / 1200 | 1200 | 3600 | shown | shown |

  Calm = no fight for 60 s. Waiting messages of the same `topic` merge into one toast ("Mossback · +2"). A message that
  waited past its wait is filed, not shown late. Moments go first, then the higher tier, then the oldest.
- **Held** (nothing paced shows): any screen open, a fight (hurt in 5 s or a hostile within 8), `UiHooks.focus > 0.2`,
  a choice card or info panel up, a moment up, a scene holding (`Notices.client().hold`, the Ascent gift scene), not yet
  awakened, or the world's first 5 minutes (`Herald.SETTLE`; onboarding speaks through story lines and its own HUD).
- **Responses and runs stay live.** An *untagged* message (IMPORTANT, no topic, not a moment: most of the ~400 call
  sites) that arrives within 60 ticks of the player's own click (attack/use/pick, or a click in a screen) answers it and
  shows at once; inside a gate run the server raises untagged messages to CRITICAL (`Signal.send/toast`). The pace is
  for what arrives unasked.
- **Look and place per tier:** CRITICAL = the choice/warning card or panel (right slot) and the warning sound;
  IMPORTANT = the System toast, top right, chime; moment = the centre banner; AMBIENT = the find cue (below).
- **Client lines through the director:** `Notices.client().claim(tier, topic)` before showing (true counts as shown);
  `file(...)` what can't wait. The region caption claims IMPORTANT (CRITICAL when dangerous), waits up to 200 ticks,
  then files "Entered · <name>"; the dangerous region's toast is IMPORTANT, topic `danger`.
- **Fleeting** (`Builder.fleeting()`, 2026-10-26): shown now or never. A fleeting message that waits past its wait is
  dropped, not filed: the System's first contact ("[ERROR] … candidate found.", the opening's and the prologue's), which
  in the Journal read as a real bug. The awakening's first moves ("Your body remembers") are no longer a toast at all:
  the checklist teaches them (§27.10). The Signal's quips (`signal_line/lines.json`, `story.Beats.lineOnce`: "That's your
  first power. Try it out once you're outside the city.") are fleeting too: a reaction read later in a list, out of its
  moment, meant nothing ("There it is."). Nothing is filed for the city's own region while you're inside its walls
  (§27.6).

### 26.3 Discovery (`signal.Finds`)
- **Intent.** A creature is studied when the crosshair rests on it (its angular size + 3°) for 3 looks in a row, 5
  ticks apart (~0.5 s), within 24 blocks (48 for a named beast), in line of sight; or when it is hit or touched
  (`AttackEntityEvent`, `EntityInteract`). Proximity alone never counts, except something big in plain view: a named
  beast or a creature ≥2.5 blocks tall, within 16 blocks and within 60° of the view (`Sightings`). Biomes and
  happenings are big and in plain view: standing in one, witnessing one counts (but they never carry the lesson).
  Lairs (reach the den), landmarks and gates (reach them) are found by going there.
- **Telling** (`Finds.tell(player, Find)` after `Progression.discover` returned true):
  1. the first find ever made on purpose (`intent`) is the **lesson**: a moment with the name, its hint, and "A
     discovery. You studied something new: it pays a little XP, and what you learned is kept in your Journal and Codex
     (K). Look closely at what you meet." (counter `awaken:finds_taught`);
  2. a **big** one (a named beast; later wonders) is a moment with its own label;
  3. the **first of each kind** (`awaken:finds/<kind>`: creature, biome, happening, lair, landmark, gate) is an
     IMPORTANT toast ending "kept in your Journal (K)";
  4. **every later one** is AMBIENT: filed with the cue.
- **The find cue** (`signal.client.FindCue`): a small diamond blooms 13 px right of the crosshair with the name in a
  whisper beside it (5 + 34 + 16 ticks), and `awaken:ui.find` at 0.32 (at most every 60 ticks); several at once read
  "N finds · Journal".
- **The Journal** (`signal.client.Journal`, the band's Status page right under the stats, and its Journal page, §30): filed lines newest first ("◆" unread), 60 kept per
  session, "JOURNAL · N NEW" until the K screen has shown them. The Codex keeps the finds themselves.
- A lair rumour ("Heard of · …") is AMBIENT, topic `lair` (the Now tracker points at it).

### 26.3b The city is not a discovery (Euan, 2026-10-26)
Carillon is where you start, not something you find: inside its walls (`CityHooks.inCity`, the wall included) nothing
counts as a discovery. One rule, `Finds.counts(player)`, checked by every source before it records anything, so the
first find outside is still a first:
- **Biomes** (`BiomeDiscovery`): no first steps inside; the remembered biome is dropped there, so the first step past
  the wall into the same biome is the discovery (the Commons' caption is the moment).
- **Creatures** (`Sightings`): the city's animals and citizens are never studied, by look or by touch.
- **Landmarks and gates** (`LandmarkWatch`): the city's own gate and places are not landmarks found.
- **Happenings** (`Pulse`): one seen from inside the walls isn't witnessed; stepping out while it runs still is.
- **Anomalies** (`AnomalyDirector.discovered`).
What the city keeps, because the city needs it: the Registry's band and licence, the first power, the city's own places
on the Atlas (its discovery and bells), the opening's story lines and revelations, Carillon's quests and requests, region
captions (a place name is not an achievement). Ores and lodes are found by mining, not by standing there, and stay.
The K menu in a fresh world, by this rule (checked 2026-10-26): Journal and Codex creatures empty until the first step
outside; Almanac empty; Truths none (they wait for level 5, `LevelRoad` flag `awaken:truths`); "Your body remembers"
never plays in a fresh world (old worlds only, from rank B); Records (time awake) and Recent messages (the opening's
own words) stay. Test `notice_city_discovers_nothing`.

### 26.4 "Nearby" alerts and the rift
- Anomaly words are IMPORTANT, topic `nearby` (merged, paced, never several at once), sent 50 ticks after the anomaly's
  own cue, and dropped if it ended meanwhile. The first rift a player meets is a moment ("Something tore open", label
  "Rift", what it is and how to close it).
- **The tear's sound:** `anomaly.rift_open` is our own `sfx_rift_tear` (tools/art/specs/audio_discovery.py: air pulled
  in, a falling rip with crackle, a sub boom 62→27 Hz, glass splinters, a crackling tail; mono), a fixed-range event
  heard to 128 blocks with linear falloff (`attenuation_distance` 128). Before: vanilla respawn-anchor sounds that a
  variable-range event at volume 3 sent only within 48 blocks of a tear that opens 32–96 away.
- **While open:** the near crackle bed (40 blocks, as before), a new far bed `amb.rift_rumble` (`amb_rift_rumble`,
  started within 72 blocks, attenuation 72), the hum, and a hush of birds and insects within 40 blocks to 20%
  (`Soundscape.hush`, renewed every 40 ticks).
- **The look** (`world.anomaly.client.RiftGlow`; the CityGlow way: one additive lightning-blend pass, depth-tested, no
  depth written, Lens-friendly; replaces the 0.8 × 3.6 textured quad and the vanilla portal particles): a jagged crack
  7 blocks tall (16 nodes whose edges crawl) in three ribbons (violet glow ×2.8, magenta body, white core ×0.3), four
  flickering side cracks, a halo of radius 6.5, a violet pool of radius 11 on the ground, a faint column 56 blocks into
  the sky (so it reads against the sky from 60 blocks by day), 28 motes spiralling in, the core at its foot. It opens
  over 30 ticks with a flash and a ring running out along the ground, flares when struck, folds as it closes. Drawn to
  160 blocks.

### 26.5 Tests
`notice_pace_gap_and_burst`, `notice_critical_bypasses_queue`, `notice_ambient_files_to_journal` (signal),
`notice_discovery_needs_intent` (fauna: beside the crosshair is never a find, a glance isn't, a rest is; the first find
teaches), `notice_city_discovers_nothing` (wilds: walking the city finds no biome; one step past the wall, it does). `fauna_sighting_discovery` studies for `Sightings.DWELL` looks.

### 26.6 The audit (2026-10-26) and what is still outside the director
Routed: every Signal toast/panel/window (server tier tags: anomalies, creatures, biomes, happenings, lairs, landmarks,
gates, crafts, the danger toast; the rest default IMPORTANT/CRITICAL by kind), the region caption, the level pane, the
Ascent gift scene. What Euan saw: reaching level 3 (and a first sighting) queues lair rumours for every family met
(`Lairs.onLevel`) → a toast "Heard of · <lair>"; a rift opened (toast at once, sound to 48 blocks only); a creature
in the 60° cone at 24 blocks counted as a sighting and its toast's hint named plants ("Plants mushrooms", "Crops it
hovers over grow"). Not yet routed (follow-ups): the vanilla action bar (102 server call sites, three throttled), boss
chat lines, the XP pop, Ascent notes and the Vow scene, citizens' speech; TipHooks has no implementation (the onboarding
spec's `TipHud`/`TipRules` were never built), so Carillon's and the Ascent's tips show nothing today.

---------------------------------------------------------------------------------------------------------------------

## 27. The Way Forward (progression and guidance; plan docs/progression/PLAN.md, page https://claude.ai/artifact/RjzQ2SNdst8vbXPrZrvx1P; Euan approved all of it 2026-10-26)

Euan, in survival: "I just got out of the city, I go into the forest, and I'm like, okay, so what's next?" The rules: every
lock is seen before it opens and names what it waits for (a rank, a level, a rung by its in-world name), never how to
play it; the world remembers the locks you met and calls you back once when you hold the key; three goals at all times
(Now, Next, Far); the world pulls first and the HUD confirms. Builds on §26 (the notification director) where it lands.

### 27.1 Promises (`core.api.Promises`, `progression.PromiseKeeper`, `PromiseBook`)
- **The book** (attachment `awaken:promises`, schema 1, saved, kept through death, synced to its owner): at most 64
  entries `{key, dim, where?, need kind, need value, what, answeredAt}`; the oldest go. A key is one per place
  (`door:x,y,z`, `landmark:ruin:x,z`, `wing_core:x,y,z`, `stratum:n`, `region:id`, `halo`, `possession`).
- **`seen(player, key, where, need, what)`**: recorded once, only for an awakened player who doesn't already meet the
  need. Silent, except the first lock a player ever meets: one NOTICE toast "Saved for later · <what>: you'll need
  <need> first. Your band (K) keeps a list of things like this, and you'll get a reminder when you're ready." (flag
  `awaken:promise_taught`). The band's list is **SAVED FOR LATER**, each row "<what> · needs <need>" ("· ready now" once
  answered); the call back says "<what> is open to you now". (Until 2026-10-26 it was "What waits · The Commons waits
  for level 4", which Euan couldn't read; plain words now, §27.6.)
- **Needs**: `rank:<label>` and `level:<n>` answered by progression; `tier:<tether|leaper|wings|skyborn>` answered by the
  Ascent (`Promises.addAnswer`). Words: "D-rank", "level 20", "the Tether".
- **The call back**: on a rank-up, a level reached or a rung granted, every waiting lock the player now meets is
  answered. The nearest one with a place in this dimension leads one NOTICE toast "Ready now · <what> answers you now ·
  640 m NE" (+ "and N more in your band (K)"), and for 10 minutes (`RAISED_FOR`) the Horizon line raises it over
  everything near as "<what> · answers you" (a raised site). `markNewThing("promise_answered")`.
- **`kept` / `keptAll(prefix)`**: a rank door opened (`door:`), a spire's crown climbed (`landmark:ruin:`), Wings from
  a sky core (`wing_core:`).
- **Sources today**: the Tether Vow's stir and every rank door's reading ("A sealed vault" / "A sealed door", the door's
  rank), a Tether Spire or sky platform reached without the rope (`LandmarkWatch.reach`: "A Tether Spire's crown" / "A
  sky platform", need the Tether), a sky core refusing a climber under C ("A sky core's wings"), a sealed Descent stratum
  ("<name>, in the Descent", its rank), the Observatory's Halo line ("Something past the sky", B), Veil possession
  refused ("Wearing a creature, in the Veil", C, no place), a dangerous region ("<region>", the level at which it stops
  being dangerous).
- **In the band** (K › Journal, §30, "WHAT WAITS", order 5, `progression.client.WaitingSection`): up to 8 rows, answered
  first then nearest: a chip with the need in its colour (rank colour; the Horizon's green for a rung) or READY, what,
  and "640 m NE". The same locks are pinned on the Atlas (`MapMarks`), coloured by need.
- **Far** (the next rank's promise): under "Next rank: D at level 10…" in the Spine section, "D-rank opens what waits
  for you: A sealed vault · Gardens, in the Descent." Only locks the player has met; never a list of spoilers.
- Test: `progression_promises`.

### 27.2 The band's Ascent page (`body.client.AscentSection`, K › Powers, §30, order 28)
"Runner · yours from the start"; each rung held with how to use it (keys in the player's own bindings,
`signal.client.KeyText`); the next rung as "◇ The Tether · next" with where it comes from ("A Precursor vault keeps a
line for a hunter it opens to: its door's rank, D at the least."); the rest "???". What a rung does is only told once
it's yours.

### 27.3 The ladder (decisions 1-2; details in §17.7, §17.9, §17.10, §17.19)
Tether at the vault's door rank, D at the least (~5 h, balanced); Leaper at D with Tether mastery 4 (~8-12 h); Wings
at C from a sky core (~20-25 h); Skyborn at A; the Sovereign form with any ending. Wren's Rope (`requires`
`tier:tether`, a new requirement token answered by the Horizon's reach) is no longer offered before the rope, so it
can't block every later found scene. Rungs, Lord-forged weapons and first Weaves now count as new things for the
Director's rhythm.

### 27.4 The Horizon line says why
A landmark's site name carries a few words of why (`LandmarkKind.why`): "Sealed Vault · something waits inside", "The
Well · the way down", "Tether Spire · a crown at the top". The sky's need reads "· needs a rope or wings" (the rig's
rings are a rope ladder), the Tether's "· needs the Tether".

### 27.5 Locks that hold and speak (phase 3)
Built by the locks builder on `pack/locks` (the orchestrator's split, 2026-10-26): climbs refusing the first block and
naming their key, the cache's inner seal, a relog keeping a built visit, the hero trees' canopy wards, the crown and core
by the line, door colours, gate cards comparing ranks, refusals on first contact. It uses `core.api.Promises` (§27.1)
and answers its "Show me" card through `Progression.BAND_ACTION` ("band:ascent", `ProgressionPayloads.OpenBand`).
A first draft of it is on `pack/progression-p3-wip` (48d09e19).

### 27.6 The first thread (phase 1)
- **Registered** (`core.api.OpeningHooks.onRegistered`, fired from `OpeningModule.afterOpening` when the opening ends or
  is skipped): Bram's errand, **The Last Delivery**, starts by itself and is tracked (`living.FirstThread`; once;
  never for a player who has it or did it). At the end of the Registry (its step `LETTERS`, after the Directive) Bram
  says what a hunter is for ("Oh, and one more thing. As a hunter, your job is to protect the city from whatever is out
  in the wilds, beyond the walls.") and where to go first ("Ilse runs a camp for new hunters up the north road. Take
  these letters to her, and she'll explain the rest."), and holds out **Letters for Ilse** (`awaken:ilse_letters`, an
  errand item; Bram's prop "letters"), which you take from his hand the way you took the band ("Take the letters").
  A player who skipped the Registry is given them at registration. The steps: take the letters from Bram (done the
  moment they're in your pack; it keeps the old first step's place, so saves hold), out through the High Gate, to the
  Wayfarer camp up the north road, deliver them to Ilse by her fire ("Deliver the letters · 0/1"; she takes them from
  your pack), and take the ember she gives you (the Ember, §28.2). The thread lets go there: its Now line ends once
  Ilse's step is done, and the Hearth's own line ("Place your Ember where you want your home", `hearth:ember`) takes
  over; the chain's last step (a `hearth` objective: done when you have a Hearth) waits in the Journal. Kestrel's
  word-from-the-square opening and return were cut (Euan, 2026-10-26: Bram hands you the letters). After it the player
  chooses: it is one errand, not a chain that keeps leading (BotW's "lines" railroaded 80% of testers). Euan, after
  playing it (2026-10-26): guide them exactly to the Ember, then let them go and explore.
- **Chains on the Now line**: the tracked chain's current step (else the first chain's), priority 68 (over points to
  spend 55 and the next level's unlock 15; under a waiting Reassessment 72, rifts and danger), with a compass to its
  place: a "reach" step's marker; a "talk" step's person (Kestrel: the Roost, `OpeningHooks.place("roost")`; Hale: his
  quartermaster spot; Ilse and Hettie: the camp; a Wayfarer: their scene). Before, tracked chains never reached the HUD.
- **The camp is findable**: its marker falls back to its nominal spot until someone has come near (the compass knows the
  way); a Horizon site "Wayfarer camp" until the player has stood within 24 of it (counter
  `awaken:camp_reached`); and a signal fire on a bale by Ilse (`Scenes.campFire`), its smoke a column seen from the High
  Gate (vanilla smoke rises only near you, so the client draws its own column to 1000 out, 110 blocks tall to clear the
  Lumen canopy: `LivingNet.CampSmoke`, `PulseFx.campfire`), lit once a player is within 256 and lit again if it's gone; the camp itself is now founded within 256 (it was 112,
  so from the gate there was nothing there yet).
- **The Registry's Directive fits** (`Directives.opening`, reason "opening"): "Clear a floor of any gate.", accepted for
  the player (4 play-days), "Your first gate will open somewhere outside the city walls." Accepted Directives show on the Now
  line with their progress ("· 50%", priority 58). Before, the Registry's was a random roll offered quietly, its waiting
  line gone in 30 s, and accepted ones never showed outside K.
- **The first gate, on purpose** (`Director.firstGate`, SPEC §21.0.14 finally built): a player's first gate waits until
  level 3 or about half an hour after awakening (36 Director passes), and until they're out of the city (or an hour),
  then forms 250-400 blocks further along the bearing from the city's centre to them (north from its middle; any way
  if that way is sea, a river or a beach), at their own rank, with "A gate has opened 320 m N · clear its floors to get stronger" (IMPORTANT, topic `gate`); the
  Horizon raises it ("First gate · E-rank") until they've cleared a floor. Before, the first gate
  was rolled the moment they awakened, in the infirmary, at random 300-1500 out, unannounced.
- **Out through the walls** is a moment: crossing the city reserve outward shows the region's caption ("THE COMMONS ·
  1-8") once a play-day (the Commons spans the city's edge, so leaving had none). Inside the walls no region caption,
  warning or promise is sent at all, and the Commons, the land everyone starts in, is never a "danger" region (it was:
  a level-1 player read "Danger · The Commons" and "The Commons waits for level 4" in the Journal before leaving the
  city). **The first time out through the High Gate, its shot names the land**: as the view lifts over the gate the
  title "THE COMMONS · THE LAND AROUND THE CITY · CREATURES LEVEL 1 TO 8" (the region at the camp, from the server),
  then the camp's caption ("THE WAYFARER CAMP · UP THE NORTH ROAD · 181 M AWAY · YOUR NEXT STOP"); the region caption
  is skipped that time. A danger region's warning says it plainly: "The creatures here are level 20 to 28. That's well
  above you, so be careful."
- **The High Gate's shot** (`living.FirstScenes.gateShot`, filmed by `living.client.FirstFilm.GateShot` with the
  opening's camera and letterbox through `opening.client.Cinema`): the first time a player goes out through the High
  Gate on the errand (the step "Out through the High Gate" completes), ten seconds, letterboxed, hold jump to skip: a
  breath in your own eyes turning to the smoke, then the view lifts over the gate (the first clear spot of a fan, up to
  15 up and 8 on) and narrows (FOV x0.6) onto the Wayfarers' smoke, which reads stronger while it's filmed; the lower
  bar names it ("THE WAYFARER CAMP · UP THE NORTH ROAD · 330 M AWAY") while Hale says his line; then back down into
  your eyes, left facing the camp. Once a player (counter `awaken:first/gate_shot`). Before the camp is founded the
  smoke rises from its nominal spot at the generator's height. The step's done line is said in the shot, not as a
  story line (`QuestEngine.Stager`: a scene that stages a step's ending takes its lines).
- **Ilse's scene by the fire** (`FirstScenes`, `FirstFilm.IlseShot`): coming within 7 of Ilse with the letters (or
  talking to her) starts it instead of a bare click. Letterboxed, over your shoulder on the side away from the fire, a
  slow push while she talks; her lines one at a time in the Registry's speech (name above, the line under it): she asks
  for the letters (the lower bar: "LETTERS DELIVERED 1/1"), sits you down, says why you'll want a home of your own ("It's
  getting dark, and nights out here are dangerous"), then holds an ember from
  hers out in her hand and brings it over to you (to within about two blocks). The view comes back to your own eyes
  facing her, the HUD stays out of the frame, "Take the ember" with the use key (within 3.6), and your hand takes it from
  hers (the Registry's hand-over, `HandOver`, now for any item): the step ends as your hand closes on
  it, and its reward is the Ember (W1's rules kept: one a player, kept through death, Ilse or Hettie give a lost one
  back). Her last line says plainly what it does ("Once it's placed, you can rest there, and if anything happens to you out
  here, that's where you'll wake up."), the bars lift, and the Now line says "Place your Ember where you want your home"
  (the Hearth's). About 20 s to the coal; hold jump to skip to it; after 30 s with her hand out she
  presses it into yours. Someone with a Hearth or a coal already is told "Looks like you already have a home fire" and the step ends there. Walking
  off (18 blocks) ends it; it starts again when you come back. Nothing hunts or hurts a player while either moment plays
  (target changes cancelled, damage cancelled but for what bypasses invulnerability; the gate's for 14 s).
- **The camp** (`living.wayfarer.Camp`): laid once round its fire when a player is within 96 (`Scenes.campBuilt`, saved,
  optional field): what lies on the ground within 5 cleared (grass, flowers, mushrooms, moss and carpets: anything
  that gives way underfoot), each piece set on the real ground under it, the ground within 3.5 trodden to coarse dirt
  and path; two
  **Wayfarer's Bedrolls** (`awaken:wayfarer_bedroll`, our own block on `AbstractBedBlock`, texture from
  `tools/art/specs/camp.py`) feet to the fire, west and south; stripped spruce logs to sit on (north, east); a barrel of
  packs with a lantern, a log of firewood; and the **Camp Pot** (`awaken:camp_pot`) hung by a chain from a bar on two
  A-frames over the fire. Only on open ground: a trunk or a rock where something goes, and it's left out. Neither block
  drops or has an item. **Its ground is safe**: no monster spawns (natural, chunk generation, patrols, reinforcements)
  within 32 of the fire; a monster within 30 stops hunting and is sent back out past the edge (every half second); no
  monster takes a player within 32 as its target. A **bedroll** sleeps the night through like a bed when it's dark (the
  rule `WHEN_DARK`), never sets your spawn (that's the Hearth's), and when a step you're on needs the dark (a quest step's
  `night`, e.g. Moth Lights' "The Lumen Moths only come out at night.") it says "You can't sleep yet. ..." instead. At night
  Hettie lies down on the far bedroll and gets up at dawn; Ilse keeps the fire.
- **Plain words, start to finish** (Euan, 2026-10-26, after playing it: "make it more human-like... not some poetic
  riddle"): every line in the first sequence says plainly what it means, the way a person would say it: the infirmary
  (Sun, Bram), Bram at the Registry and its cards, the ritual's prompts, the System's acceptance, Kestrel, Hale, Ilse,
  Hettie, the clerk, the crier's news of the wilds, the first tips, the Ember's messages and tooltip, and the toasts
  and captions on the way (e.g. Ilse: "Here, take an ember from our fire. Carry it with you, and when you find a spot
  you'd like to live, place it there. That'll be your home."). Lines that grew got time to be read (Bram's briefing:
  28 s, a line every 7; Ilse's beats 90 apart).
- **Quest fields** (`QuestDef.Step`, schema 1, both optional): `tally` shows a step's count even when it's one ("· 0/1");
  `night` says why a step needs the dark. Objective type `hearth`: done when the player has a Hearth.
- Dev and tours: `/awaken living first gate|letters|ilse|took|camp`; tour `tools/autopilot/first-scenes.txt` (CI).
- Tests: `progression_first_thread`, `progression_now_priorities`, `gates_first_gate_on_purpose`,
  `progression_first_scenes` (the gate's shot once; the letters counted; Ilse's scene to the coal; the Ember; the thread
  letting go), `living_camp_keeps` (laid round its fire; nothing hunts there; a monster sent out; the bedroll's rule; a
  step that needs the night).

### 27.7 Tips, switched on, and the Signal's thread (phase 4)
- **Tips** (docs/carillon/m4/onboarding.md §1, finally built; `TipHooks.install` had no caller, so every tip in the
  game, the Ascent's, the Living Wilds' and the free-run's, showed nothing):
  - Rules (`signal.ui.TipRules`, pure): one tip at a time, the highest priority whose context has held 6 ticks; 80 ticks
    between any two; `cooldown` between showings of one; retired after `retire_after_done` dones or `retire_after_shown`
    showings; the `short` text (just the keys) once it's been done.
  - Client (`signal.client.Tips`, `TipHooks.Client`): data from `assets/awaken/tips/<group>/<name>.json` (reloaded with
    resources), `{key.<mapping>}` in the player's own bindings (`KeyText`), other placeholders dropped; counts per person
    in `config/awaken-tips.json`; shown only when the notification director allows (`Notices.claim(IMPORTANT, "tip")`)
    and never over a screen, while held (`hold(owner)`: the Ascent's scenes) or while the director is quiet. Drawn
    centred above the story line's slot, one line (two at most), in the System's glass; it fades when its context ends,
    at once when done. Client option `tips` (on).
  - Server (`TipHooks.Server` in the signal module): `offer` and `done` are one small payload (`signal_tip`); a server
    offer waits up to 5 minutes for a quiet moment (the director holds tips in a fight, a scene or the world's first
    minutes; those first minutes don't count against the 5, or a tip offered at registration lapses as the world
    settles), then shows for its `show_for`.
  - The first stretch (`assets/awaken/tips/first/`): `band` (registration + 6 s: "K opens your band: what you hold, what
    waits for you, and the way up."; done when K opens), `now` (registration + 26 s: the two lines at the top), `atlas`
    (the first way out through the walls; done when the Atlas opens), `power` (the first power chosen; done at the first
    cast), `level` (a level-up with points; done when a point is spent: "You grow by finding new things, hunting, and
    clearing gate floors."), `gate` (the first gate placed; done on entering a gate), `night` (the first dusk outside
    the walls, once).
- **The Signal's thread** (`NowClient.thread`): toward the Now line's place, a thread of the Signal's light runs low
  along the ground, 26 blocks in a little over a second (`AscentFxBridge.threadMote`, two motes a tick), once when that
  place is new and once more if the player stands still outdoors for 8 s with it; at most every 2 minutes, never in a
  screen, never within 12 blocks of the place. No key, nothing that stays on screen (Ghost of Tsushima's guiding wind,
  in our blue).

### 27.8 People who point (phase 4)
- **The crier names real places** (`CityEvents.wildsNews`, lines in `tools/city/lines.py` → `citizen_lines.json`
  `news.wilds_*`): the nearest standing gate within 900 of the city ("Hear ye! A gate's light stands past the High Gate,
  to the north!"), else the spawn vault ("…hums again! Its door reads a hunter's rank, they say!"), else the
  Wayfarers' camp. Before, every line in the city was flavour.
- **Kestrel speaks** before you click her (`named.kestrel`: "Runner! You still haven't told me what happened in the square.").
- **"Anything for me?"** (`progression.Advice.next`, after Terraria's Guide): a free Registry clerk you right-click says
  the one next step that fits you: a Reassessment waiting, a lock that answers you now, points to spend, the Directive
  you've taken, else the next rank and what it opens for you ("D-rank at level 10. It opens a sealed vault for you, and
  more.").
- **A level-1 found scene**: Marta's Wheel (`quest/martas_wheel.json`, band 0, a road scene): eight sticks for her
  wagon, paid in road news (gates' light, vaults' doors, the camp). Found scenes all needed level 8, so a new player
  never saw a flare; now the first one can rise in the first quarter hour.
- **One Journal**: the Living Wilds' chains read "JOURNAL · CHAINS" beside the System's filed finds ("JOURNAL").

### 27.9 The long middle (phase 5)
- **The capability clock** (`Progression.CAPABILITY_AT`, set inside `markNewThing` for things you can *do*: a power
  learned, a rank, a Path, a rung, a Lord-forged weapon, a first Weave, a machine or blueprint, an Echo, a Hearth or its
  tier, a latent, a townsperson; never a sight or a core). After 75 minutes without one, outdoors and out of a gate, the
  Now line offers the nearest one in reach: a lock that answers you now, with its place (57), else "D-rank at level 10 ·
  gate floors pay best" (50).
- **The Reassessment says where**: "Reassessment ready · D-rank trial at your Spire" with a compass to your Hearth, or
  "· craft a Monolith" without one.
- **The level road goes on to 135** (it had two steps in levels 51-135): The Precursor Foundry (52), The Halo (55, goal:
  see it from an Observatory), Wings you know (58), The Inverted Sea (62, a core), A-rank trial (70), The open sky (73,
  goal: the Sky Leviathan's trial), Any creature (77), The Dreaming Stratum (80, a core), The Sovereigns (90, goal: one
  falls), S-rank trial (100), The Court opens (105, a core), Half the Court (112, goal: four), An empty court (122, goal:
  eight, a core), The Seed (135).
- **Weapon mastery** is wired by Arms S7 (`ArmsMastery.strikes`); Wilds content past level 20 belongs to Living Wilds
  phase F.
- Test: `progression_capability_clock`.

---------------------------------------------------------------------------------------------------------------------

### 27.10 The current task, top right (Euan's playtest, 2026-10-26)
"Every time you have a goal and things to do, you actually have a small checklist top right." One small card, top right
under the first two toast slots (`signal.client.TaskHud`; never over the crosshair, hidden with the HUD), in the band's
glass, a touch smaller than the Now card: the task's title and done/total, then its steps (a long one wraps to a second
line), each ticked when done (a soft green flash as it ticks), the
current one bright with ▸, keys drawn as key caps from "{key}" in the words, a count where a step has one ("0/10"),
and a compass to a place nearby to do it ("↗ 9 m"). The Now line, top left, keeps the where; the card keeps the what.
The server decides (`progression.Checklist`, sent only when it changes, payload `signal_task`; nothing while the
opening runs):
- **Your body remembers** first, from the awakening: "Hold [Shift] as you land to roll", "Double-tap [Ctrl] to sprint
  harder", "Hold [Shift] while sprinting to slide", "Press [Space] at a ledge to climb it",
  each ticked the first time you do it (the body module's counters `awaken:body_roll`, `_sprint`, `_slide`, `_mantle`);
  the ledge row points at the nearest chest-high ledge within 10 blocks while it's current (every 2 s: a two-block step
  up from standing ground with room on top). All four done: the list stays ticked for 4 s (`awaken:first/moves_done`),
  then goes for good. It replaces the "Your body remembers" toast, which the director filed unread.
- **Then the tracked chain** (`Checklist.register`; the living module's `FirstThread.task`): its title, its steps ticked
  as they're done (at most six: the last done, the current, what follows), a count on a step that has one or a `tally`.
  The first thread reads: Take the letters from Bram, Leave the city through the High Gate, Take the letters to the
  Wayfarer camp up the north road, Deliver the letters 0/1, Place your Ember where you want your home.
- Test: `progression_checklist` (the moves tick one by one, then the errand takes the card).

## 28. Between the Gates (home, people, below; plan docs/world-depth/PLAN.md, page https://claude.ai/artifact/ATZfZSczLg95kNSgnLF42M; Euan approved all of it 2026-10-26)

Euan, 2026-10-26: "Go ahead with all recommendations." PLAN.md is the long form and its audits (`docs/world-depth/
audit-*.md`) are the facts it was designed against; this section is what builders hold to. (§27 is The Way Forward, on
pack/progression.)

### 28.0 Decisions (approved)
1. The Hearth starts on the first evening: Ilse gives the **Hearth Ember** at the Wayfarer camp (the end of The Way
   Forward's first thread); setting it down founds a **Tier 0 Hearthfire** (radius 24). The Lord's Heart still makes the
   Anchor (Tier I) on that fire.
2. The Hearth reads what you build: every closed room read on six readings (closed, light, warmth, room to live, made
   with care, detail and comfort; best of each kind counts), graded Bare / Snug / Fine / Grand (the average, never more
   than one step above the weakest), named (grade, main material, purpose, or a sign inside), its name shown once as an
   AMBIENT caption; the glance reads the room you stand in; Comfort sets Rested; rooms do things (PLAN 4.6); people move
   in when you know them, a spare Snug bedroom stands and their trade has its room.
3. AWAKEN's own settlements replace vanilla villages in new worlds (hamlet, village, town, special kinds), built on
   Carillon's people engine made multi-settlement (with the Carillon session) and the city's grammar, with interiors
   (shared with Carillon B4); the first hamlet placed on purpose 700-1,100 from Carillon up the camp road.
4. Village people are citizens, not vanilla villagers; vanilla villagers leave new worlds (iron farms and trading halls
   with them); a town scribe sells enchanted books.
5. The emerald is the coin of the world; Carillon's planned bell marks are dropped; `EconomyHooks` is installed with it.
6. Troubles: caused by world state, found never listed, ≤ 1 per village and ≤ 2 within 1,500 of a player, none in a
   world's first two hours or inside a Hearth; missable with a lasting cost, never a dead end or a named death; always
   paid; world option "calm villages". Launch set of eight (PLAN 6.3).
7. Hide from the hunt, metal from the deep, light from the gates: from D up every forged weapon, Hunter armour piece and
   mattock needs its tier's mineral with no alternative: D iron, C diamond, B amethyst + Deepglass, A netherite,
   S starmetal; E stays hide and bone. Gatewrought rises to 20 armour and 2.0 toughness.
8. Gates pay no raw ore beyond their theme's flavour; the gate market stops selling diamonds; the Mine Echo works a real
   mine head (W6) instead of rolling dice.
9. New finds: Deepglass (seeps below y 0 and the Descent's walls; diamond edge to cut), starmetal (fallen stars), bell
   bronze (copper + amethyst), lodes with a heart (vanilla ores in 20-120-block veins). The Lands' crystals become
   attunement catalysts (Heat Sunshard, Frost Frostspar, Gravity Fault Seam; Charge copper; Spirit Deepglass); lapis is
   ink (Blueprints: 4 paper, 1 lapis, 1 essence).
10. Mining's feel: every ore its own strike sound, sparks, a hit on the last blow; the vein gives when a heart breaks;
    the charged heavy strike (bore three along the aim); rare, warned cave-ins (never in a Hearth or on placed blocks);
    the Tuning Fork, pitons and Runner's rope, the miner's lamp, rope fittings (W5).
11. Caves: vanilla's generator stays; cave places (sinkholes, the Delvers' mines, seeps, chutes, grottos, joined lairs,
    vents), the rappel (with the Ascent), cave sound and Lens looks, names, a way home; every place has a route that
    needs no rope and passes M44's five tests (a rule, a resource, a sensory set, a story entrance, a route).
12. This plan takes Living Wilds G (settlers, your named village, roads) and F's underground secrets and treasure maps;
    Living Wilds F keeps wonders and legendary beasts.
13. Order: W1 and W2 now as two builders; then W0 and W3 with Carillon, W5 alongside; W4, W6; W7 last.

### 28.1 Phases and who touches what (keep two builders off the same files)
| Phase | Owns (writes) | Reads / asks |
|---|---|---|
| **W1 The carried flame** | `hearth/**` (the Ember, Tier 0, the reading, room kinds of PLAN 4.6 except the Mine head, the room glance's data, Comfort, Rested, chests-at-home crafting, the 4.9 fixes, a coal per temper in `hearth/Upgrades`), `src/main/merge/hearth/`, the Ember's line and reward in `data/awaken/awaken/quest/the_last_delivery.json` (agreed with the progression session, which builds the first thread), the room glance's client panel | `core.api.Notices` (captions), `Progression.addPerkSource` (Rested: `awaken:energy_regen`, `awaken:craft_rate`), The Way Forward's glance when it exists |
| **W2 Metal from the deep** | new module `delve` (`dev.awaken.delve`, `src/main/merge/delve/`): lodes and hearts, Deepglass, starmetal, bell bronze, the strike's feel (client), Tuning Fork, pitons and Runner's rope, miner's lamp; tier metals in `gear/**` and Arms' rows and recipes (agreed with the Arms session); `arms/ArmsForge` catalysts and Blueprints; `gates/run/Loot.java`, `gates/run/Rooms.java` (handouts); ore XP in `progression/ProgressionModule.java`; `living/craft/**` and `living/client/CraftFx.java` (Mining craft fixes, reading `awaken:craft_rate`); fallen-star loot; Deepglass in `descent/world/Palette.java` | nothing of W1's |
| W0, W3 | `citizens`, `city` (with Carillon); new module `village` | |
| W4 | `village`; `living/pulse`, `living/camp` targets (with Living Wilds) | |
| W5 | `delve` cave places; `body/ascent` rappel and rope fittings (with the Ascent); Lens looks; core audio's cave soundscape | |
| W6 | `hearth` settlers, tiers by reading, the Mine head and the Mine Echo's rework, the named village, the kit craftable | |
| W7 | `village` roads, inns, towns | |

Shared ids (fixed now so parallel builders agree): items `awaken:hearth_ember`, `awaken:deepglass` (the block's item),
`awaken:deepglass_shard`, `awaken:raw_starmetal`, `awaken:starmetal_ingot`, `awaken:bell_bronze_ingot`,
`awaken:tuning_fork`, `awaken:piton`, `awaken:runner_rope`, `awaken:miners_lamp`; tags `#awaken:tier_metal_<d|c|b|a|s>`
(the signature minerals, used by gear and Arms recipes); perks `awaken:craft_rate` (a craft-XP multiplier, read by
`living.craft`) and the existing `awaken:energy_regen`; test prefixes `hearth_` (W1) and `delve_` (W2).

Wired after both W1 and W2 merge (one small follow-up, never inside either phase, so neither builder edits the
other's files): the Study halves the Blueprints' paper (`arms/ArmsForge`), the Greenhouse triples herb ripeness
(`living/craft/Herbs`). W2 reads the perk `awaken:craft_rate` by its id, so Rested's craft bonus needs no wiring.

### 28.2 W1 as built
Branch `pack/world-depth-w1`. Module doc: `docs/modules/hearth.md` ("The carried flame"). Tests: `hearth_ember_founds`,
`hearth_room_grades`, `hearth_rested` (and every older `hearth_` test, `hearth_guide_matches_roomtype` updated to the new
kinds). Tour: `tools/autopilot/world-depth-w1.txt`.

**The Hearth Ember** (`awaken:hearth_ember`, `hearth/ember/EmberItem`, `hearth/Embers`). A 3D item (vanilla lantern
proportions, its cage emissive: `models/item/hearth_ember.json`, texture from `tools/art/specs/hearth.py`). Carried in
either hand: a pool of light on the ground (radius 5.2, 0.34) and a halo at the lantern, drawn as a source of
`citizens.client.CityGlow` (`hearth/client/HomeClient.GLOW`; no light block). One per player: a spare goes out and none is
kept once your fire burns (checked every 2 s while carried); kept through death (`LivingDropsEvent`, back on respawn).
Given by: The Last Delivery's `ilse` step (reward `item: awaken:hearth_ember` and Ilse's line, the progression session told);
talking to Ilse or Hettie with no Ember and no Hearth, registered (classic worlds: always), and past that step or never on
the chain (`PlayerInteractEvent.EntityInteract` on a `living.wayfarer.Wayfarer`, so living is unchanged; Ilse: "Lost your
coal? Take another. The fire doesn't mind."); 60 ticks after awakening in a classic world (no `CityBasin` site). Now line
`hearth:ember`, priority 66 (under a tracked chain step ~68): "Place your Ember where you want your home". **Bram's line is not
added** (Carillon's code): for the orchestrator, Bram at the Registry: "The bells hold the city. Hunters hold the wilds.
Ilse at the camp keeps a fire for new licences."

**Setting it down** (use on the top of a block). Refused, each with its reason: soft ground or no room, the city reserve
(`CityBasin.reserved`), a Precursor ward (`WardHooks.at`), any gate not released within 64 (`GateHooks.near`), another
Hearth's radius, a household rule (`Household.refusesOwnAnchor`). Founds a **Tier 0 Hearthfire** (`awaken:hearthfire`,
`HearthfireBlock` + an empty block entity that lists loaded fires for the client's glow; light 14; strength 2 / 1200,
immovable, owner-only to break; loot table empty: the code returns the coal). `HearthRecord.tier` 0, radius 24
(`HEARTHFIRE_RADIUS`), respawn, rest heals (30 s), calm land and every rule that keys off any Hearth record (gates 64,
camps 120, happenings, wards, the Living Wilds' calm). No town (`Town.arrivals` returns), no Spire, no requests, no cottage,
no furniture recipes. The moment (`HomeNet.Kindled` to clients within 96): sparks drawn down into the coal for 14 ticks
and a burst; from tick 10 the warmth runs out over 44 ticks as rings of `hearth_warmth` motes on the ground's height
(eased out, brightness 1 → 0.45) with a ring of 40 light pools (radius 2.8, 0.30, and a fainter ring behind at 0.62 of
it) drawn on the ground at the front through `CityGlow`; fireflies if it's night; `hearth.take` (`sfx_hearth_take`: a settling thud, stone
clicks, a low hearth tone on D with its fifth, a faint D-A-F# bell chord; mono, 32 blocks). The grass tint itself is not
changed (that needs a mixin into the biome tint cache); the warm wash of motes over the ground stands in for it. 50 ticks
later the **YOUR GROUND** card (`client/NameScreen`, low on the screen, no dimming) with a name offered by
`Embers.suggestName` (first words by biome: forest, plains, snow, dry, wet, high, water; joined endings ford, stead,
holm, wick, cote, ley, by, worth, combe, field, hearth, burn, or apart: Rest, Hollow, End, Green, Hearth; deterministic per
place and owner); the name is cleaned (letters, digits, spaces, ' and -, 24 at most). Walking onto your land: a place
caption, the name spaced over "your hearth" (IMPORTANT, topic `hearth_place`, filed after 200 ticks). Fireflies over your
land at night (one every 8 ticks round you). **Lifting**: empty hand on your fire while no room is read → the record ends
(its household kept), the coal comes back, `hearth.lift`. Breaking it (owner) or losing it any other way returns the coal
(to an owner within 16, else dropped there). **Tier I**: a Lord's Heart or the Anchor used on your fire, or the guide's
button with a Heart in your pack, sets the Anchor block in place, tier 1, and runs what an Anchor placed runs
(`HearthService.anchorTookRoot`: the toast, the furniture recipes, the town, the cottage offer).

**The reading** (`room/Reading`, `room/Tally`, `room/Materials`, `RoomScanner.read`). As PLAN 4.5, numbers:
closed 3 with no see-through faces, 2 up to 15% (fences, bars, leaves), else 1; light 3 = every floor cell at block light
8+ (97%) and a window (glass or pane in a side wall), 2 = all lit, 1 = 60% lit or a window, 0; warmth = the best fire (lit
campfire, fire, lava, magma, the Hearthfire or the Anchor 3; lit furnace/smoker/blast furnace or the Forge 2; cold stove
or lit candles 1); room = floor / the purpose's target (Shelter 6, Bedroom and Sanctuary 12, Kitchen, Forge, Study,
Workshop 16, Greenhouse, Stable, Barracks 20, Trophy Hall 24, Hall 64, Bell tower 4, Lookout 9): <0.5 → 0, <1 → 1,
<1.75 → 2, else 3, capped at 1 when 2 high and 2 under the target height (3; halls and trophy halls 4); care = worked share
of wall, floor and roof faces (tags and the block's name: planks, stripped logs, glass, wool, bricks, polished, cut, smooth,
chiseled, tiles, terracotta, concrete, quartz, pillars, and every stair, slab, wall, fence, door and trapdoor) 25% → 1,
60% with 2 kinds → 2, 85% with 3 kinds → 3; detail = trim kinds (stairs, slabs, trapdoors, fence and wall posts, chains,
lanterns) ≥1 +1, ≥3 +1, soft kinds (carpets, banners, paintings or frames, flower pots, candles, bookshelves) ≥1 +1, ≥2 +1,
at most 3. Grade: mean ≥2.5 Grand, ≥1.75 Fine, ≥1.25 Snug, else Bare, never above the weakest + 1. Name: "the <grade>
<family word> <purpose noun>" (families: timber, log, stone, fieldstone, deepslate, brick, mud-brick, sandstone,
blackstone, tuff, quartz, copper, sea-stone, clay, concrete, glass, earthen, end-stone, nether-brick, wool, leaf, snow), or a
sign's words inside (front, else back; 40 characters). Lift: the weakest reading's fix, light first, then warmth, detail,
care, room, closed. Seeds: furniture, doors, lights, chests, signs, carpets, banners, pots, the Hearthfire and Anchor, and
the household's feet in the radius; a solid seed floods each open side on its own (a door no longer joins inside and
out). Only the old eight pieces of furniture get "why not" fills. Caps 48 a side and 20,000 cells (`TOO_WIDE`,
`TOO_BIG`, each worded); the explanation fill is capped at 6,144 cells. Every stage (fill, read, explain) stops at the
tick's budget (12,000 reads) and goes on next tick. Saved: `HearthRecord` part `home`, schema 3 (name, rooms without cells,
Comfort, Style, land); a loaded room answers by its box until the next scan, which comes 30 s to 4.5 min after the server
starts (spread by owner). The first time a player stands in a room after it's recognised or regraded (key = its box,
grade and name; 96 remembered per player in `HearthPlayer` schema 3), its name as a quiet caption (`HomeNet.RoomCaption`,
AMBIENT, topic `hearth_room`, `ui.hearth_room`), waiting while they stay in the room up to 600 ticks, else filed.

**Room kinds** (`RoomType`; effects in `RoomEffects`, `Breaks`, `Town`). Shelter (every closed room; the first: toast
"First shelter · Rest heals here. From now on the Hearth reads everything you build."), Bedroom (a bed), Sanctuary,
Kitchen (furnace or smoker, crafting table, barrel: food crafted or smelted while you stand in it gets component
`awaken:home_cooked`; eating one stretches every effect it gave or lengthened ×1.5; tooltip "Home-cooked"), Forge
(`ARMORY`, id kept, label "Forge"), Study (lectern, 6 bookshelves: recognised only; Blueprints after W2 merges),
Greenhouse (farmland or a planted pot under a ceiling at least half glass: recognised only; herbs after W2 merges), Stable
(hay bale, fence gate: perk `awaken:whistle_range` +160 within 512 of the home, read by `world.fauna.Mounts` (one line);
Pell's Spurs' +80 now counts too), Hall (6 seats = bottom stairs on the floor with room above, a table = a fence or wall
post with a carpet, pressure plate or bottom slab on it, floor 48+: a meal eaten in it feeds every other player in it
nutrition and half its saturation), Bell tower (a bell, the floor 8+ over the lowest ground 4 blocks outside: rings at
dawn and dusk; `Breaks` warns once of a standing gate within 256 due to break within 2 play-days; Deep Tide's +2 skipped
inside a Hearth with one, via `HearthHooks.tideCalm` in `living.api.Regions` (one line)), Lookout (12+ up, glass in three
directions: `AtlasHooks.reveal` 256 round home when first recognised and on login; Mira tells a second rumour a play-day),
Trophy Hall, Workshop (machines in it or in its walls pay ⌈×0.75⌉ essence: `HearthHooks.machineCost` in
`above.machine.MachineBlockEntity.draw`, one line), Barracks. Toasts per new kind, topic `hearth_room`. The Now line suggests a kind with two or more kinds of furniture
only once two of them are in the radius (never from one flower pot or one bell); a kind with all its furniture but not its
condition says so briefly ("Greenhouse: a glass roof").

**Comfort, Style, Rested** (`Comfort`, `Rested`). Comfort 0-12: best bedroom grade (0-3) + a room with warmth 2+ + a table
with 2+ seats + a room with two soft kinds + growing things indoors + the land: field (16 farmland), pen (2 untamed animals
and 8 fences), garden (a composter and 6 flowers), lit yard (6 placed lights outside rooms), wall (40 wall or fence
blocks), each 1. Style: the two families with the most faces, the second named when it is a fifth of the first ("timber and
fieldstone"). Rested: waking (not shaken awake) in a bed inside a read Bedroom of a Hearth you belong to: 8 + Comfort
minutes, perks `awaken:energy_regen` +0.25 and `awaken:craft_rate` +0.25 (a perk source on both sides reading the synced
`HearthView.home.restedUntil`); toast topic `rested`; "◆ RESTED Nm" over the level line, pulsing in its last minute.

**The room panel** (`client/RoomPanel`: `visible()`, `build(Ui)`; data: attachment `awaken:hearth_here`, `RoomHere`, synced
to its player when it changes, checked once a second). Name, grade, six readings as three pips each, purpose, "Lift it:
…". Shown on the band's Status page above the Journal (`RoomSection`, order 17, §30) while you stand in a read room at home (PLAN
said "hold the band key": K opens the band on press today, so the panel lives on that page until The Way Forward's glance
takes it, through the two calls above); and as every room's card in Your Hearth, which also gained a HOME section (Comfort
with its bar and the next point, Style, the land, Rested minutes), the kinds you have as chips, at most three suggestions
as full cards (what you've started first, then Shelter, Bedroom, Kitchen, Sanctuary) and the rest one dim line each; a
Hearthfire's header chip, town line and its Tier I step ("Feed the fire the Heart").

**Chests at home** (`HomeCrafting`): done without a mixin. A crafting table inside a Hearth you belong to opens
`HomeCrafting.HomeMenu` (a `CraftingMenu` on the vanilla menu type, so the client opens the vanilla screen); its
`handlePlacement` first moves what you lack for one craft (shift: a stack's worth) from chests, barrels and shulker boxes
you may open within 8 blocks of the table into your inventory, nearest first, then places as vanilla does. The client's
book can show such a recipe red (it can't see the chests) but clicking it always asks the server, so it crafts. A one-time
toast says so.

**Fixes**: caps and their reasons as above; pads a scan finds join the Hearth (`HearthService.registerPads`); Anchors
refused in the city reserve (and on your own Hearthfire's land: "Use the Anchor on your Hearthfire: it rises there.");
the town Spire's Board and Monolith are given as items when the first townsperson arrives (retried by upkeep while the
owner is away; the Board placed inside your Hearth becomes its board); a temper at the Forge costs a coal (`Items.COAL`;
Oren's tempers don't).

**Other modules touched (one line each)**: `core.api.HearthHooks` (+`tideCalm`, `machineCost`, defaults),
`living.api.Regions` (Deep Tide skip), `above.machine.MachineBlockEntity` (Workshop cost), `world.fauna.Mounts` (whistle
range perk). Read only: `citizens.client.CityGlow.source`, `living.wayfarer.Wayfarer.who()`, `living.api.Quests`.

**Not done / left**: the grass tint (mixin territory, see above); the Study's and the Greenhouse's effects (after W2, per
§28.1); Bram's line (Carillon); the room panel on a held band key (The Way Forward's glance); a stable's bonded mount
"coming home here" (the whistle range is in); the Hall's festival (W4/W6).

### 28.3 W2 as built
Module `delve` (`dev.awaken.delve`, `delve.client`, `delve.api.Lodes`; doc `docs/modules/delve.md`), and the seams it was
given. Branch `pack/world-depth-w2`. Tests `delve_lode_heart`, `delve_tier_metals`, `delve_tuning_fork`,
`progression_ore_xp`; tour `tools/autopilot/world-depth-w2.txt`.

**Hide, metal, light.** Item tags `#awaken:tier_metal_<d|c|b|a|s>` (in `src/main/merge/delve`): D `iron_ingot`, C
`diamond`, B `awaken:deepglass_shard`, A `netherite_scrap`, S `awaken:starmetal_ingot`, one item each. Every gear piece
from D up has a slot `M` that takes only its metal (tools/art/specs/gear.py RECIPES: 2 helmet, 3 chest, 2 legs, 2 boots,
1 blade, 1 focus, 2 mattock, about 13 a tier); every focus from D up a gold ingot (`G`, two from C); every Veilwoven piece
an amethyst shard (`Y`). Forged Arms weapons from D up take the metal in their bind cell (tools/art/specs/arms_weapons.py
`write_recipe`). E is unchanged. `hunter_hide_s` is starmetal (S's recipes take it through `M`). Gatewrought: 3/6/8/3 =
20 armour, toughness 2.0, durability multiplier 33 (diamond's); to keep every stat climbing, Veilwoven 3/7/8/3 = 21 and
Lordscale 3/7/9/3 = 22. `HunterTier.metalTag()`.

**Every ore's job.** Blueprints: 4 paper, 1 lapis, 1 essence (`ArmsForge.BLUEPRINT_LAPIS`). Attunement catalysts by id
(`ArmsForge.CATALYST_ID`, `catalystItem`): Heat `awaken:sunshard`, Frost `awaken:frostspar`, Gravity `awaken:fault_seam`,
Charge `copper_ingot`, Spirit `awaken:deepglass_shard` (each Lands crystal is its own block item, dropped by its block).
Bell bronze: 3 copper + 1 amethyst = 3 ingots (shapeless); a vanilla bell from 5 bell bronze and a stick.

**The handouts stop.** `gates/run/Loot.java`: the table pays no iron, gold, emerald, lapis, diamond or netherite; their
weight went to essence shards (weight 14, 1-2+rank), torches, and a Core Shard from C (weight 4); the theme item stays
(the Night Market's emerald, the Foundry's copper...). The Treasure Gate pays emeralds (the coin) and more essence, no
gold. `gates/run/Rooms.java` market: the diamond (5 essence) became a coil of Runner's rope (2 essence), the netherite
scrap (6) four pitons (2); selling gold and emeralds for essence stays. The Mine Echo is W6's.

**Ore XP** (`ProgressionModule.OreXp`, `XpBudget.DEEP`): 0.01 of a level × coal 0.5, copper 0.7, iron 1, gold/redstone/
lapis 1.5, emerald 4, diamond/Deepglass/ancient debris 6 (quartz 1; amethyst 0, a discovery only). Under y 0 (and
Deepglass always) through the budget `deep_mining` (0.08 a 10 min) instead of `ore` (0.06), so the deep isn't worn by the
coal on the way down; the taper starts at content level 15 under y 0 and 25 for diamond and Deepglass. The first of each
ore is a discovery (`awaken:discovered/ore/<id>`, 0.1 of a level; a `Finds` toast, kind "ore", no lesson). A lode's heart
pays its own lump (below).

**The Mining craft** (`living/craft`, `living/client/CraftFx`): see §21.9 (Ore Sense with mattocks via
`#awaken:mining_tools`, Vein Sight's whole vein, Deep Ear's sound, Deepglass Cutter). Craft XP per ore: 10 (15 in
deepslate or Deepglass) × the ore's weight's square root clamped 0.7-2.5 (coal 7, diamond 25), a heart 60.

**Lodes** (`LodePlans`, `LodeFeature`, `Veins`, `api.Lodes`): 256 x 256 regions; per region coal 2 (y 6..40, 50-110
blocks), copper 2 (0..40, 50-110), iron 3 (-20..36, 40-100), gold 1 (-54..16, 30-70), redstone 1 (-58..-4, 40-90),
lapis 1 (-48..8, 30-60), emerald 35% (-8..30, 20-32), diamond 80% (-58..-18, 20-40), Deepglass 1 (-58..-12, 25-50)
(`delve.Ore`). A lode is a seam walked out from its heart in two arms along a gently wandering, mostly level heading, a
cell and a cell beside it each step (1-2 thick), within 48 of the heart; pure SplitMix64 from the seed, cached per
region. `awaken:lodes` (feature, `worldgen/feature/delve/lodes`, biome modifier `delve` on `#minecraft:is_overworld` at
`underground_ores`) writes each chunk's cells into stone (the ore), deepslate (its deep twin) or another ore, never air,
water or a build; the heart at the centre. New chunks only. `Lodes.nearest(level, pos, ore[, radius])` answers from the
plan; a planned lode whose loaded blocks are plainly not its ore (old terrain, mined out) is passed over. One block per
ore's heart (`<ore>_lode_heart`, its ore's tool tier, light 4, a slow pulse, its core and lit facets an overlay at full
light so it glows in the dark, glinting), loot a lump (iron 4-7 raw, diamond
2-4, Deepglass 4-7 shards..., Fortune applies), in `#c:ores`, `#c:ores/<ore>` and `#minecraft:<ore>_ores`.

**The vein gives** (`Veins.give`, client `VeinFx`): breaking a planned heart (after every protection, LOW) opens its
lode once for the world (`DelveData`, schemaVersion 1): `LODE · <ore> · <blocks left>` through `Finds` (the first a
moment), a lump of 0.08 of a level × the ore weight's square root × the taper, counters `awaken:lodes_opened`, the
client's reveal (no outline, no boxes: light runs out from the heart along the vein at 32 blocks a second with a
travelling crackle; each vein block it reaches flashes from inside its own texture, its faces redrawn additively in the
ore's colour, whiter in the 7-tick flash, with glowing cracks (`vein_cracks_0..3`) across them and a soft pool on the
face; rock in front of a buried vein block shows the light bleeding through, its own face lit 42/20/9% through 1/2/3
blocks, cracked too through one; 3 s full, settling over 2.5 s to a 30% pulse that lasts the window, fading over its
last 1.2 s; grit and a glint off each open block as the front passes; a kick and hold), and for 30 s the breaker's blows take the lode's blocks in two (speed =
hardness × 30 / 8, mirrored on the client). The lodes within 64 are sent to each client when the set changes, for the
glints: a lode block open to the air glints in its ore's colour where block light ≥ 8 or the miner's lamp's beam falls.

**Cave-ins** (`CaveIns`, client `CaveInFx`): after a heart breaks, 60% of the time if the chamber qualifies: air reached
from beside the heart (box ±12 x, -5..+12 y) at least 160 blocks, at least 7 across both ways, 30% of its columns 3+ high;
roof = the natural rock over the top air of each such column within 6 of the heart (stone, deepslate and kin, ores, dirt,
gravel, calcite, dripstone), 10 to 40 of them, nearest first. Never inside a Hearth, a ward, or the city's footprint
within 48 under its square (its foundations); never if any
roof block's section holds a solid block a player placed underground (`DelveData` keeps sections below y 64). Warning:
3 s: cracks spread over those stones (the game's breaking stages, the first to fall cracking first, all broken through
by the fall), dust pours from up to 14 seams between them in streams that open and thicken as it nears, grit, a creak
and a second higher one at 55%, a tremble within 20, "The roof is giving way". Then they let go one after another over
1.5 s (`SPREAD` 30 ticks, stone i of n at i × 30 / n, nearest the heart first), each still unchanged as a falling block
(1.5 damage a block fallen, at most 8): a rumble at the first and the last, and on the client a crack and a burst of
dust where each tore free, a thud and a cloud rolling out along the floor where it lands (its landing time from the
fall's height), and the view shaking within 20 (hardest as stones hit; the player's camera-kick comfort setting
scales it). Dust takes at least block light 6 so the cloud reads in an unlit cave.

**The strike's feel** (client `StrikeFeel`): a bite every 4 ticks held (vanilla's rhythm, from NeoForge's client mine
hold): each ore its own sound (`delve.strike.<ore>`), sparks off hard rock with a pick or mattock (white-hot cooling to
the ore's colour), grit off soft ground (tinted by the block), the last blow a crack (`delve.strike_last`), a 50-70 ms
hold and a small kick on ores (`CombatFeedback`). **The heavy strike** (`HeavyStrikes`, client `HeavyCharge`): sneak and
hold the attack with a pick or mattock (holding attack alone is vanilla mining, so the heavy takes sneak); the Maul's
charge (starts at 6 ticks, glints at 20, 30 at most): a strain, grit off the head, a ring on the glint; let go to bore
3 blocks along the aim. Server: a mining tool, charge ≥ 6, 14 ticks since the last, food ≥ 6, not in a gate; each block
breakable by that tool (and dropping), outside the city's build (its civic blocks, the city's own protection rule; the
rock under its streets is free), a ward and anyone else's Hearth, broken through the game's own
`destroyBlock` (so every protection still applies); 2 more durability a block (1 on the glint), 1.0 exhaustion a block.

**New finds.** `awaken:deepglass` (strength 5.5, diamond tool, light 3, its facets' violet edges an overlay at full
light, hums now and then, motes off open faces; drops 1-2
shards, Fortune, silk touch the block; in `#c:ores`, so the Veil sees it violet) as veins below y 0
(`worldgen/feature/delve/deepglass`: ore, size 5, 3 a chunk from 4 above the bottom to y 0, open to caves) and in the
Descent's strata walls from the Choir (form 3) down, one of each wall's ores (`descent/world/Palette`). Starmetal: the
fallen star's loot table adds 1-3 `awaken:raw_starmetal`; smelts (400 ticks) or blasts (200) to `starmetal_ingot`.
Bell bronze as above.

**Tools.** The Tuning Fork (2 amethyst shards and 3 copper ingots; 256 uses): touched to an ore or
heart it takes that ore's note (`awaken:tuned`); struck on stone (1.5 s cooldown) it rings toward the nearest lode of
that ore within 48 (`TuningForkItem.target`): the client's ring climbs from the ore's note ×0.72 to ×1.34 as you face
it (cubed alignment), lasting 4.5 s; a soft arc on the band with a bead of the ore's colour at its bearing (pinned at
the end when behind you), a mark over or under when it's up or down; nothing within 48, a short flat note. Pitons (4 from
2 iron and a nugget) go on a wall; a coil of Runner's rope (8 string and a nugget) used on one hangs a rope straight
down to 32 blocks or the ground (`awaken:runner_line`, a free rope in `#awaken:runner_climbs`: the pipe grip climbs it);
breaking any of it or the piton gives the coil back. **No two-piton zip line**: the Ascent's line traversal (the pipe
grip) rides vertical climb lines only, and its zip is the Tether pulling to a point, so there is nothing to ride a run of
blocks with yet. The miner's lamp (leather, copper, glowstone) is worn on the head (its equipment layer: a band and a
brass lamp); its light is drawn, never placed (no light blocks): 17 rays in a 24° cone from the brow find the faces
ahead (each ray's face and its eight neighbours on that plane, at most 260); each lit face is drawn again over itself
with its own block quads (the model variant the world chose there, so it lines up), additively in a warm colour, ×0.95
at the beam's 9° core falling to its edge and to nothing at 22 blocks, so the rock's own texture comes up out of the
dark; a faint additive haze on the faces the inner rays hit (`CityGlow`'s pass) and, seen from outside, a small flame
on the brow; for every wearer within 40 (`LampGlow`; the lit pass is skipped if its pipeline doesn't compile).

**Sounds and art.** 25 events `delve.*` synthesised by `tools/art/specs/audio_delve.py`; textures, models, loot,
recipes, tags, worldgen and the star's loot by `tools/art/specs/delve.py` (`build_all.py delve`, `delve.py content`,
`delve.py preview DIR`). Own particles `delve_spark`, `delve_grit`, `delve_dust`, `delve_glint`, `delve_mote`.

**Pacing** (`tools/sim/pacing_sim.py`, the deep's metal; balanced player, medians of 300 runs): the D kit's iron (14 and
a gold) is in hand at 1.1 h, long before level 13 (6.3 h); the C kit's diamonds (14 and 2 gold) at 12.9 h against level
22 at 13.8 h, wait after the level 0 h median, 2.1 h at p90 (careful 0 / 2.0, greedy 0 / 1.6, builder 0 / 2.1). That
assumes the player goes after the metal once within 3 levels of the tier (15-30 min a cycle, the Tuning Fork tuned to
diamond: a diamond lode found every ~50 deep minutes with the fork, every ~150 without). Every level target still passes
(level 25 at 16.8 h, 50 at 51.5 h, 135 at 102 h).

**Not done in W2** (left for later phases or a follow-up): the deep's soundscape below y 0 ("the deep presses") is W5's
cave audio; the rope fittings and rappel are W5's; the Mine head and the Mine Echo's rework are W6's; the Tuning Fork
from the hamlet's smith waits for villages (W3), it's craftable now; no zip line (above).

### 28.4 W0 as built (many settlements, 2026-10-26)
Carillon's people engine (`dev.awaken.citizens`) is one engine with many settlements; Carillon is settlement 0 and
behaves as before (its `citizens_` and `city_` tests unchanged in what they assert).
- **Settlements.** `citizens.Settlement` is one place's people: its `Site` (origin, districts and names, ground, water,
  tagged lots), roster, street graph (`StreetNet`), saved state (`CitizensData`), `Ledger`, `CitizenWork`, `CityEvents`,
  `Recognition`, `Lanterns`. `citizens.Citizens` is the per-server registry that replaces the `Citizens.active`
  singleton: it starts Carillon on the first tick after the city exists (jar roster, CityData or plan graph, plan hash
  check), registers the others, and each tick runs every settlement's ledger, then the crowd once, then every
  settlement's work, events and memory. Overworld only.
- **One body pool.** `Crowd` is one per world: every 10 ticks it gathers every settlement's candidates, sorts them
  nearest first whichever settlement they belong to, and applies the caps once (40 a player, 96 a world, 8 spawns a
  pass); path slots (4, 6 above 60 bodies) and the next-hop bake budget (3 a tick, `StreetNet.newServerTick`) are shared
  the same way. `CitizenBody` carries its settlement (`home`, synced `DATA_SETTLEMENT`; 0 Carillon). Each settlement's
  people are compared only with the players within its reach (its furthest node + 40 + 64), any number of them: the
  old pass looked at the first 16 players in the list wherever they were (a 17th player in the city got no one near
  them; the multiplayer server can't have that). With 16 or fewer players nothing changes.
- **Saved data v2.** `awaken:citizens` (`CitizensSave`, schemaVersion 2) is a list of `{key, id, schemaVersion 1,
  records, districts, data}`; `data` is the per-settlement `CitizensData` exactly as schema 1 wrote it (safety and
  gratitude sized by the settlement's own district count). A schemaVersion 1 file (Carillon's alone) is read as entry
  `carillon` #0 and written back as v2. Ids: Carillon 0, others from 1 in order of first registration, kept by key,
  never reused. Entries of settlements not running this session are kept.
- **Coordinates.** Each settlement has an origin (x, street y, z); its roster and graph are offsets from it; node y is
  per node as before. `Site.ground(x, z)` replaces the plan's lift (Carillon: the plan; built: the nearest node's y).
- **Rosters from code.** `citizens.api.RosterSource` (`jar()` is Carillon's); `citizens.api.RosterBuilder` lays out a
  settlement in code and writes its roster in citizens.json's shape (schema 1, plus `extraCount`: how many event actors
  follow the citizens; absent = Carillon's 8), parsed by `Roster.parse`; its sha256 ties the saved records as the jar's
  does. The builder checks indices at registration (`Citizens.check`). The test hamlet (`SettlementTests.hamlet`) is the
  smallest example; W3 writes the village builder on it.
- **Events and work by tag.** Events pick by `Site` tags: `fire` (a lot that is a house with a door; Carillon: the Works'
  houses), `cart` (a lane a cart can spill across; Carillon: the Quay's), `stall` (Carillon: the Market's). Each reports
  in `Site.districtFor(tag, …)`'s district (Carillon: the tag's district, so its safety, news and lines are as before;
  others: where it happened). Burglaries need households and an event actor, pickpockets a stall; bucket lines draw on
  `Site.water` (a built settlement's water cells: its well). The gate tremor, the opening's posts, the Bell Bridge, the
  crier's wilds news, chimneys and the Site packet stay Carillon's. Planned daily events run only where the spec asks
  (`plannedEvents`: Carillon yes; villages no until W4's director); started events work in any settlement.
- **Names and memory per settlement.** Each settlement's `CitizensData` holds its own memories; `{district}`, deeds'
  districts and the crier read its `Site` names (a built settlement's district 0 is its own name). Its standing tiers
  need a quarter of its people where that is fewer than Carillon's 30 and 24 (a hamlet of six: two). `RenownHooks.tier`
  stays Carillon's; `RenownHooks.witness` and `CitizenHooks.witnesses` reach every awake settlement within reach
  (citizen UUIDs: Carillon's unchanged, others `awaken:citizen/<key>/<rec>`). A stolen bag remembers its settlement.
- **Skins.** A body outside Carillon wears a Carillon skin picked by hash of (key, record) from those of its job, age
  class and build, else residents', else anyone's (`citizens.Looks`), synced as `DATA_LOOK`; its name and job reach the
  client as `DATA_LABEL` (no village roster on the client). No dogs follow them.
- **Predicates.** Carillon's spawn rule, civic repairs and lamps' monster rule stay Carillon's (`CityHooks`); a
  settlement exposes `inside(pos)` (Carillon: the plan's inside; built: its radius).
- **Sleeping.** A settlement other than Carillon ticks only while a player is within radius + 192 (it sleeps again past
  + 256); waking (and re-registering a saved one) seats everyone where the hour wants them (`Ledger.reseat`, like a
  clock jump), so a world of villages costs only the ones near players.
- **Also.** Citizens' payloads go through `AwakenNet.send` (a test or fake player is skipped, not thrown at); the hurt
  go home where a settlement has no infirmary; `/awaken citizens settlements` lists them and the pool.
- **API** (§12): `dev.awaken.citizens.api.Settlements`, `Settlement`, `SettlementSpec`, `RosterSource`, `RosterBuilder`.
- **Measured** (GameTest: Carillon's plan with 40 bodies round one player at its square at noon, 600-tick windows,
  machine load 9-10 from other builds both times): before W0 ledger p50 0.011 / avg 0.030 ms, bodies p50 0.96 / avg
  1.57 ms; after, Carillon alone, ledger 0.010 / 0.033, bodies 0.88 / 1.38; with the hamlet beside it (40 bodies
  between them), ledger 0.007 / 0.019 (the hamlet's own 0.0003 ms a tick), bodies 0.51 / 0.69. At load 17-21 single
  windows spread 2-3x run to run, both codes alike. The quiet in-game measure stays with Carillon's next perf tour.
- **Tests:** `citizens_many_settlements` (the hamlet beside Carillon's engine: shared caps nearest first, come and go,
  sleep and wake, events by tag, both saved and read back; logs the costs; it sets `Citizens.onlyPlayers` to its own
  players, as the shared test world holds every earlier test's), `citizens_save_v1_loads`.
- **Left for W3:** the village builder on `RosterBuilder` (households from plots, jobs from stations, day templates,
  village jobs appended to `Roster.JOBS`), village speech pools and soundscape beds (Soundscape is Carillon's),
  chimneys and client life for built settlements (`CitizenWork.chimneys` reads Carillon's plan), village skins if the
  Carillon pool reads wrong, station furniture (`CitizenWork.furnish` places a smithy's anvil and a wash tub once, in
  loaded chunks, over air: W3's painter may place them itself), and a re-sent lamp list on re-registration (appended
  twice on clients).

### 28.5 W5 as built
Caves worth entering (PLAN section 8). Packages `delve.cave` (the places, their run-time rules, the Delvers' things),
`delve.home` (a way home, the vents), `delve.sense` and `delve.client.Sense*`/`CaveSound`/`DeepHum`/`CaveLook` (sound and
light below), the rappel and rope fittings in `body/ascent`, the seep heart in `mobs`. Branch `pack/world-depth-w5`. Tests
`delve_cave_routes`, `delve_seep`, `ascent_rappel`, `delve_way_home`, `mobs_seep_heart`; tour `tools/autopilot/world-depth-w5.txt`.

**The places** (`delve.cave`; structure type `awaken:cave_place`, one code-built `CavePiece` whose `Spec` builds the part in
each chunk, drawn in the same order in every chunk; `Spec.STYLE` 1). Placed with the lair's cave placement and what the
lairs never had: natural cave air nearby, read from the generator's own base columns (noise caves and aquifers, no chunk
needed: `CaveSites.air`, rings at 8/14/20/27 out, 6 a ring, a floor under three of air), and a short tunnel carved to it
(the joint). Every place keeps out of water and lava (its columns are read for fluid), out of the city's reserve, and
inside 8 chunks of its start chunk (the generator links a structure only that far: `CaveSites.fits`). Structure sets
(`data/awaken/worldgen/structure_set/delve/`, from `tools/art/specs/caves.py`): sinkholes spacing 44 / separation 18
chunks, mines 52/22, seeps 26/10, chutes 34/13, grottos 28/11; biomes `#minecraft:is_overworld` (the sinkhole and the
mine also refuse oceans, rivers and beaches at the surface). Names by seed (`CaveNames`).
- **Sinkhole** (`Sinkhole`): a great round shaft radius 10-17, its floor 40-110 under the rim, laid beside a planned lode (W2's
  `LodePlans`, any ore but Deepglass) with its floor one under the heart: at the bottom a passage to a grotto lit by glow
  lichen and glow berries where the heart glints in a niche of its far wall. The wall has noise (±1.2); its top three blocks lean in (0.6 a block) so the lip is a
  crisp edge with the dark opening under it, and the hole is cut 12 over the rim (a bump at its edge never caps it).
  **The shelves** (the E route down): rows every 3 blocks stacked in one sector, a row one block thick where the next runs
  under it (two high to walk), each row's end over the next one's start, alternating sides: walk a few blocks, drop 3.
  **The cleft** (the way back up): a crack cut into the wall 100° round from the shelves, vines in one straight column from
  the floor to the ground, step off the top onto it; vines and a mossy step stone at every drop too. **The dive**: opposite
  the shelves the wall falls sheer (±0.45 rad) from a smooth jutting lip beside a spring in a notch under the rim, whose
  water falls into a pool 5 deep. **The old rope**: a roped piton under the rim a quarter turn from the shelves, its line
  32 down (`#awaken:runner_climbs`). The way in is a stony path (cobble, mossy cobble, stone, gravel) cut down from the ground onto
  the top row; `goto` stands you on it at the edge of the drop. Vines hang in curtains from the lip down its first 14
  blocks, then in strands through the upper half; hanging roots under the shelves, moss on the shelves and
  floor. Mist by day and bats at dusk (below). The first sinkhole (`awaken:delve/first_sinkhole`, placement
  `awaken:near_city`): 600-900 from Carillon's centre (world origin without the city), never within 40° of north (the camp
  road), radius 14-16, "The Opening"; its joint may be missing (it ends in its grotto).
- **The Delvers' mine** (`Mine`): an adit (20-27 long, timber sets every 4, rails) into a hillside that rises 9+ in 18,
  the mine head (the winch over a 3x3 shaft, a foreman's office with a chest: the first Delver's log, the lode map, the old
  pick, lapis), a ladder 21 down to the first gallery (3 wide, 5 high, 40 long: dressed walls of polished andesite or
  deepslate, flat and on the grid, timber flush in them, so the Runner runs them), a drowned stope across it (9 long,
  water 2 deep, 4 down: run its wall, or drop in and take the ladder out), a rail incline 14 down along its end with a
  minecart at the top and stairs beside the rails, and the second gallery folding back under the hill (a fall of roof to
  slide under, 5 long, one high; a camp with the second log; a chasm 26 deep with water at its bottom and a rope bridge,
  planks that sag a block, fence rails, chains to the roof), ending in the breakthrough: stained rock, Deepglass coming
  through, a half-built seal and the last log. Not every place ends in a fight: this one ends in a log. A site is taken
  only when its galleries read dry in the generator's base columns: the head, the first gallery, five points along the
  second and the chasm's ends and middle down to its floor (the tour's first real mine had its second gallery under an
  aquifer near sea level, flooded to the roof).
- **Seep** (`Seep`): round a region's Deepglass lode (the Tuning Fork tuned to Deepglass finds it): a hall 26 x 14 x 17
  laid toward the lode, the rock that touches its air stained (blackstone, obsidian, crying obsidian, tuff), nine
  Deepglass veins along its walls toward the heart, four Deepglass spires 5-9 high you climb by a ring of steps, a magma
  vent with the Ascent's updraft in its floor, a ruined timber set where the Delvers' drift came in, a Hollows nest in a
  side alcove (bone, shed chitin, dripstone), and through a curving throat the heart chamber: a Deepglass dais with the
  socket, veins out across the floor, the lode's heart glinting in the wall behind.
- **Chute** (`Chute`): from a timbered mouth joined to the caves, 48 steps falling half a block each (slab, block, slab:
  the stair slide's rhythm), 3 wide between dressed walls, to a lip over a lake 4 deep in a cavern 5.5 below: the slide's
  run-out throws you out over the water. The way back: a landing at the water and a stair up the cavern's side.
- **Grotto** (`Grotto`): a passage from the caves to a curtain of falling water across a doorway (a slot of sources in the
  roof, a trench under it; walk through, wade out), a room 14 across lit by lichen and glow berries, a pool glowing from
  sea pickles in its bed, a geode half out of the wall (basalt, calcite, amethyst, clusters) cracked open, a fallen stone
  to climb into it, and a cache under suspicious gravel by the pool (loot `awaken:chests/delve/grotto_cache`; opening it
  first counts `awaken:secrets_found` and is a find). A third of grottos (by seed) are a legendary fishing spot: a third
  of catches there bring something more up (glow ink, amethyst, Deepglass, lapis, prismarine crystals, glow berries or a
  nautilus shell) and the first catch is a secret. **Its own light**, so it reads without a lamp: glow lichen on 16% of
  the open cells against rock, glow berries hanging from 4% of the roof cells, sea pickles in 60% of the pool bed (85% at
  a fishing spot) two to four to a cluster; and our drawn light (`delve.client.CaveFx`, sprites `delve_grotto_glow`
  and `delve_grotto_glint` (the mist's soft lobes), full bright, never a light block): glints sliding down the curtain with a pale aqua glow behind it, a
  sea-green halo over the pool with motes rising off it, for players within 40 (sites sent within 64). A grotto is taken
  only when nine columns across its span (the room's rim, the geode, the passage) read dry, not only its middle (the
  second tour's grotto was black: flooded to the roof by an aquifer beside it).
- **Routes** (`Routes`, G46): a search over a built place's feet cells with only the Runner's Kit and the Ascent's numbers
  (walk/step 0.6, jump 1.25 with three clear over you, grab and climb a lip 2.4, drop 3, lower into a hang and let go 4.5,
  a running jump over a gap of one or two, ladders, vines and the place's own ropes, swim, falling into water from any
  height, the slide's crawl under a one-high gap); never the Tether. `delve_cave_routes` builds every kind for four seeds
  and turns, one chunk at a time as worldgen does, and needs a route from its way in to its end and back out (the mine also
  to its office, stope, incline, crawl and bridge; the sinkhole's dive must land in the pool).
- **Cost** (the test, M4 under load): building a place averages 0.25-0.9 ms a chunk it touches (sinkhole 0.9, worst chunk
  ~6-8 ms; mine 0.3; seep 0.5-0.8; chute 0.2-0.4; grotto 0.35); one place in 800-2,700 chunks, so under 0.005 ms a
  chunk across the world. A candidate's site search in a real (vanilla) overworld: 9-18 ms (20-42 base columns), once per candidate chunk
  (one in 676-2,704 chunks); sites found for sinkholes 19 of 40 candidates, mines 7, seeps 13, chutes 23, grottos 27
  (`delve_cave_routes` asserts every kind finds some): about one place every 500 blocks of cave.

**At run time** (`CavePlaces`): places are known as their start chunks load, and a player's place is the structure piece
round their feet (`StructureManager.getStructureWithPieceAt`; a place laid by a command or a test is `STAGED`). Twice a
second each player's place is sent to their client (`CaveNet.Now`: kind, name, how strongly a live seep presses, 1 within
15 of its hall or chamber fading over 9, quiet). The first time in a place is a discovery (`awaken:discovered/cave/...`,
0.15 of a level for a big place, 0.08 small; counter `awaken:cave_places_found`); the first time each play-day, a caption.
**A seep's rule**: inside a live seep creatures are 5 levels over the region (`living/api/Regions.addLevelExtra`, a hook
added for this); within 40 of the socket the seep heart is rooted (`mobs.SeepHearts.spawn`, idempotent), and the Hollows'
nest keeps up to three Hushlings or Drip Lurkers about it, one more every 30 s, while someone is within 28. The heart's
death quiets the seep for everyone (`CaveData`, schemaVersion 1): no +5, no nest, no hum or violet; "<name> falls
quiet" (IMPORTANT); counter `awaken:seeps_quieted`. **A sinkhole's bats**: at dusk (12,400-13,200) 7-12 come up from
mid-shaft, once a play-day. Places within 160 are sent for their look from outside (`CaveNet.Near`): a sinkhole's mist
rising by day (at dawn 0.3 + radius/28 wisps a tick, thinning through the day, gone at night; most born in the shaft's
top so they clear the rim; 1.3-2.3 across, swelling 2.2x, peak alpha 0.28-0.38 on a soft sprite; fading out within
2.5-8.5 of the camera so it never walls the view; our sprites `delve_mist`), a chute's draught at its mouth (`delve_draught`).
Carving turns sand or gravel left hanging over the new air into sandstone or stone (a roof never pours in).
`/awaken delve goto <kind>` (`CaveLocate`) stands you at the nearest place of a kind the world really made: each
candidate's start chunk is generated to its structure starts and the start the world holds is taken (a disagreement with
the search is logged, `[delve] locate ... the search says ..., the world holds ...`), and it passes (logging `[delve] goto
passes`) a place whose goal is not its own, is solid rock or is under water. A sinkhole walls off a lava lake at the
ground beside it (lava within 2.5 of the wall from 8 under the rim up is set to rock), so lava never falls down the shaft.

**Names and captions** (`delve.client.CaveHud`): "THE DELVERS' DEEP" in spaced capitals under one hairline, "old mine"
under it, quieter than a region's (smaller, dimmer, rising as it fades), through the notification director (topic
"region"; IMPORTANT for sinkholes, mines and seeps, AMBIENT for chutes and grottos, filed in the Journal when it can't
show); its cue `delve.cave.name` is the region caption's chime lower and quieter.

**The Delvers' things** (`CaveItems`): three Delver's logs (use: the pages in the book screen; read, it joins the Codex as
"The Delvers", attachment `awaken:delver_logs`): the foreman's (the Carillon's bell metal, the copper running down, the
seams written in lapis), a Delver's notes (the lamps burn blue past the chasm, black glass in the wall), the last entry
(they broke into it and sealed the drift; the Well goes down to where this comes from). The lode map: read with a lapis
(used up), the three nearest lodes within 224 come up on the Gate Map and in a toast. The Delver's old pick (a relic):
carried, the Tuning Fork reaches 16 further (perk `awaken:fork_reach`, read by `TuningForkItem`).

**Joined lairs**: the Crystal Nest finds cave air within 20 past either tunnel (`delve.cave.CaveJoints`) and bores on to
it (`DeepLairs.joinTunnel`; `LairPiece` saves the joint; older pieces keep none); one laid by `/awaken fauna lair place`
opens into a pocket. The lair line says how far down or up ("Lair of the Thousandfoot · 1.2 km NE · 40 below", nothing
within 8: `FaunaNow.depth`). **The Horizon** (`CaveHorizon`): from awakening, within 900, never at home or in a gate, until
you've stood at one: "Sinkhole · a way down" ("The Opening · a way down", raised, for the first), found by walking the
sinkholes' placement grid and running each candidate's own site search (the same answer worldgen gets; two new
candidates a call, cached).

**Moving through rock** (`body/ascent`): **the rappel** is its own state (`AscentState.RAPPEL`, ordinal 6: watchers get the
pose from `Seen` alone, the server can bound a rope that grows, TETHER untouched). On a line, sneak then jump starts it;
sneak held holds you, jump held with it pays out; a fresh jump with your feet on the wall kicks off (cooldown 8 ticks:
0.26 out, 0.08 up; a lean of 0.02 and damping 0.015 bring a bound 1.4-1.7 out back in 15-20 ticks at any length, about 3
blocks down); let go of sneak and you swing again; let go of the line by a roofed floor within 2 and the zip's vault
steps you in. Pay-out without Deepglass in bites (0.24, 0.21, 0.05: half a block every 3 ticks, 3.3 b/s, a catch on the
third), a Deepglass line smooth at 0.21 (4.2 b/s); down to the floor plus 0.3, six ticks standing lets go. Falls stay
free. The server: length ≤ reach + 1, the hand within len + 2 of the anchor, a pay bank refilled at the line's rate x1.15
(capped at 30 ticks' worth + 1, counting the larger of game and wall time so a lagging server doesn't refuse), a per-tick
astray check; refusals go through Stop and the refusal counter and never move the body; a kick is a chain link.
**Rope fittings** (crafting-table recipes: the Forge has no data-driven ones): an iron line (4 iron, 4 nuggets, a coil
of Runner's rope: the rope reaches 6 further), a gold reel (4 gold, 1 iron: winds 1.5x, reel-in 0.51, fit-reel 1.8), a
Deepglass line (4 shards, 4 iron, Runner's rope: smooth pay-out, keeps the +6); fitted by using them, one line and one
reel (the old one comes back), saved as `awaken:rope_kit` (schema 1, kept through death), synced (`Kit`, and `Seen.kit`
for watchers); the line drawn steel-grey or with a violet glint, the reel at the hand. Sounds `ascent.rappel_*`.

**The senses** (`delve.client`): **sound** (`CaveSound`, `DeepHum`, a Conductor `Source` in its voice budget, honouring
its hushes, ducking and `natureLevel()`; played beside AmbientSounds, whose cave ambience it doesn't double; and
`soundscape/family/hollows.json` for players without it). Underground ("under": no sky, real ground 3-12 over you; full
at sky light ≤ 4, none at 10; eases over ~2.5 s): drips at real dripstone tips and wet roofs (4 + 2.5 a tip found, capped
at 4; dripstone x1.3, lush x1.1, deep dark x0.35; 40% fall again from the same tip 1.2-3.5 s later), stone settling (0.45
a minute, 6-26 off, lower with depth), far water toward real water 8-32 off (a loop), a breath of air from the brightest
sky-lit air 3-32 off at least 4 brighter than you (a loop, lower with depth); sulfur caves add a vent's hiss and acid's
bubbles and their drips sizzle; every event x(1 - 0.45 depth) (depth 0 at y 48, 1 at y -64), lulls every 2-4 min.
At most 10 block reads a tick. **The deep's hum** below y 0: nothing at 0, in over 8, 0.28 at y -30, 0.52 at y -60, plus
0.48 x a seep's weight (deep dark x0.6); the seep's second voice at 0.85 x seep^1.3, and the world muffles (the air's
filter to 20000 x (600/20000)^seep Hz, nature to 1 - 0.7 x seep). Ten events `delve.cave.*`, `delve.deep.*`
(`tools/art/specs/audio_caves.py`). **Light**: a Lens look per cave biome (`assets/awaken/lens/looks_delve.json`: lush
caves' green glow, dripstone's amber, the deep dark's ink, sulfur caves' yellow haze) and a seep's violet-black shadows and
lilac Deepglass bloom through a new place-look hook (`LensClient.placeLook(layer, id, weight)`: two layers, nothing while
Lens is off, a story override always wins); a plain cave under any other biome takes an underground look by "under", so
it no longer wears its surface biome's grade. Dripstone's amber is opened up (it read only dark in the second tour):
exposure 1.05 (the cap), contrast 1.03, vignette 0.16, bloom 1.1 from 0.46, an amber haze mixed 0.75 that lights glow
into (fogGlow 1.1); shadows are still never lifted.

**A way home** (`delve.home`): the server keeps your trail (a crumb every 4 blocks while out of the sky, at most 96, the
older half thinned when full, loops cut; attachment `awaken:delve_home`, schemaVersion 1, not kept on death). After 15 s
out of the sky with sky light ≤ 6, 6+ from where you last stood under open sky and 12+ below it or 32+ walked, a Now line
"Surface ↑ 20 · NE 40" (priority 56; never outside the overworld, in a gate, the Descent, a Hearth or the opening) until
you see the sky again. The Signal's thread runs back along the crumbs (never through rock), a block a tick for 40, when
the line appears and when you stand still 5 s, at most every 30 s; NowClient's straight thread and arrow leave it alone
(`NowClient.ownWay`). The Way Forward's glance plugs in with `delve.client.HomeClient.thread()`. **Vents** (`awaken:delve/vents`,
biome modifier `delve_vents` at `underground_decoration`): a chunk in 8 tries 3 columns; over a floor in sulfur caves, or
at y ≤ -28 with lava within 4, with 20+ of air above: the Ascent's updraft vent on a magma crust (a cinnabar ring in sulfur
caves); 0.011 ms a chunk. **The Descent's tunnels**: five small grottos per tunnel stratum (a pool lit from below by the
stratum's glow block, Deepglass in floor and roof, lichen, half with a cache from the stratum's loot table) and Deepglass
seams along the tunnel walls in bands (the Rim and the Gardens get iron and gold seams and amethyst grottos); the tunnel
forms lose W2's scattered Deepglass; no overworld cave reaches a stratum.

**The seep heart** (`mobs`, `awaken:seep_heart`, `MobDef.placed()`: fauna kept out of the census, beast registry, codex and
lairs): our own body (Deepglass and black-violet growth fused into the rock, a beating violet core, plates that open as it
wakes, roots in the walls), rooted, hp 34, damage 4, armour 4, box 2.0 x 2.6, at a beast's level (the seep's +5 with it).
Wakes for a survival player within 12 (7 up or down) or a blow (2 s); fights within 20, sleeps after 30 s alone and heals.
Phase 1: Deep Shards, Rootfall (cracks, then roots), Root Lash; at 65%: Deep Pulse (radius 9, knockback, 4 s darkness),
Call of the Deep (two Hushlings); at 30%: Shard Storm, Heartburst. Its own light (5 asleep, 10-12 awake, dark after death),
its own sounds; drops Deepglass shards, a Chime Crystal, essence, a Heartglass part, 45% a Power Core.

**Decisions made in W5** (not among the plan's 13; each the recommendation): the rappel as its own state; fittings as
crafting-table recipes (the Forge has none data-driven); the Deepglass line keeps the iron line's +6; the way home's glance
has no key (as The Way Forward's): its thread runs when the line appears and when you stand still; the thread follows the
trail you came by; the seep heart's level is a beast's in the seep (region + 5 + 3); seeps form round a region's Deepglass
lode, sinkholes beside a planned lode (so their grottos hold one); the climb back out of a sinkhole is a vine cleft (the
shelves are the way down); the mine folds back into a U (a structure reaches only 8 chunks from its start); the first
sinkhole may end in its grotto when no cave is near; a plain cave's own underground look; bats at dusk are vanilla bats
(Minecraft's own life), the mist and the draught our own; the caption's cue reuses the region chime, lower.

**Not done in W5**: the Hollows den in a dripstone hall and the beast's cave under a hill (PLAN 8.2's other lairs); the
Taken trouble's destination (W4); an ice chute (the stair slide runs on steps only); the cave sound judged by ear (a Mac
listening check: `delve.cave.*`, `delve.deep.*`, `ascent.rappel_*`, the seep heart's voice). Not yet seen in a shot
(changed after the second tour, 40313392914): the great shaft's shape (radius 10-17, the leaning lip, the vine curtains,
the stony way in), the mist's latest numbers, the place caption, the draught; the dripstone and deep-dark looks read
only dimly (lush reads). The third tour (40316525458) saw the great shaft read from over its lip (the waterfall to the
pool, the rings, mist), but its grotto, The Hush Pool, again came out black but for our drawn light: goto stood in solid
rock where the search put the room (the search and the built world apparently disagree there; goto now takes the world's
own start and refuses a goal that is rock), so a grotto in its own light is still unseen. The way home's Now line ranks under a tracked quest's line, so the tours saw its
thread only.

### 28.6 W3 as built (living villages, 2026-10-26)
Branch `pack/world-depth-w3`. Module `village` (`dev.awaken.village`, `src/main/merge/village/`, `/awaken village ...`);
module doc `docs/modules/village.md`. Tests `village_plan_deterministic`, `village_day`, `village_trade`. Tour
`tools/autopilot/world-depth-w3.txt` (CI). Towns and mining towns wait for W7, troubles and growth for W4.

**New worlds only.** A built-in pack, **Awakened Villages** (`datapacks/awakened_villages`, feature flag
`awaken:awakened_villages`), holds the settlement structure, its set and biome tag, and empties vanilla's
`minecraft:villages` set (no vanilla village, no villager, no iron golem of a village). Its own flag rather than the Lands
pack's: worlds already made with the Lands keep vanilla's villages, and no settlement ever starts half-built at the edge
of an old world's generated ground (decision recorded here). A cured zombie villager is untouched (W6 can settle it).

**Where** (`village.world.SettlementGrid`, pure seed arithmetic). Cells of 640 (about two to a region of 900): one
anchor each, a hamlet 65%, a village 25%, none 10%; none within 760 of the centre (Carillon's site, else the origin) or
440 of the first hamlet. The planner moves an anchor up to 32 to the best ground; a village with no room becomes a hamlet;
rough land drops it. On real overworld noise (seed 26102610, the 41 anchors within 2,600) 21 hold a settlement: about
one per region, hamlets two times in three. **The first hamlet** (`FirstHamlet`) is found at the server's start, after
terrain picks the city's site and before any chunk generates: up the road north 760-1,060 (the camp is at 450), then
96 and 192 to either side, then the roads east, west and south, then 240 further out, the first spot whose land takes a
hamlet of four homes or more. Clear of the city reserve (+8) by the planner; gates never open on a settlement's ground
or within 64 of it, planned ones included (`Villages.reserved`, read by `gates/Director`); a Hearth can't be founded
within 8 (`hearth/Embers`, `HearthService`).

**Placed** as a structure (`awaken:settlement`, placement `awaken:settlement_grid`, step `top_layer_modification`:
after the trees, which it clears; snow falls on its roofs after). The start is the anchor's chunk; the whole plan stays
inside its eight-chunk reach (villages radius ≤ 88, hamlets ≤ 58). Pieces: one painter piece (the plan's box: each
chunk it touches paints its share straight into the sections through the city's writer) and a thin-beard marker per
building (reaching two past its walls: no bank of earth at a back window), field, pen, every four-block segment of every
street, and the green (the land eases up or down to their floors and the paving instead of meeting them in a step).
**The clearing:** before a chunk paints, the land's trees go from every free column within six of the settlement's
ground (logs, leaves and what hangs on them; never grass, flowers, snow or a block entity; a felled trunk's dirt grows
grass), so houses stand in a clearing, never in a wood; the planned trees are bark and persistent leaves and stay. The piece keeps only its anchor and the planner's
version: after a restart the first chunk to paint makes the same plan again (`Plans`, cached per server). Pens get two to
five beasts of the land (sheep, pigs, cows, chickens, goats), kept. A tree cut at the edge leaves nothing hanging
(`SettlementPaint.sweep`, the city's sweep and orphan rule).

**The planner** (`village.plan.VillagePlanner`, pure; reads the generator's estimate `GenTerrain`: each column once, a
6-block grid, bilinear between). Site: the best-scoring of 25 centres (81 for the first hamlet) by flatness, dryness,
water within 40 of the core, a moderate height. A green (paved, radius 5 or 7) with the well, the bell (a post in a
hamlet, a stone tower with an open belfry and a lightning rod in a village), the notice board facing the street, benches
round the well and, in a village, a market stall. A main street along the contour through the green (24-38 blocks each
way in a hamlet, 40-65 in a village), lanes branching off it (0-1, 2-4); street heights never change more than one a
four-block step, and where the paving climbs one the lower cell takes a slab of its own stone (a half step, never a
cube of cobble with earth at its sides). Plots on the frontages, each facing its street with a yard round it, level with the paving at its door,
a path out to the street; never more than 4.5 of cut or fill. A village has the inn (11-13 x 9, two storeys), the smithy
with its forge shed (anvil, a lit blast furnace), the elder's two-storey house, often a granary and a barn, 8-17 homes;
a hamlet 3-6 homes (the first 4-5), sometimes a smithy and a barn. Longhouses in the frost. The mill (9 x 9, two storeys)
on the nearest water with its wheel turning in it; a pier where water is two deep near a street. Strip fields (2-3 a
hamlet, 4-7 a village) with a channel down the middle and the land's crops; pens; kitchen gardens behind half the homes
(fenced, a water hole, a gate to the house); 2-7 trees of the land's wood; washing lines in a third of the side yards;
carts, crates, sacks, planters, a timber stack; street lamps (5 a hamlet, 14 a village). Names from the land
(`Names`: "Kettleby", "Longvik", "Hollowstrand").

**Painted by the city's grammar** (`dev.awaken.city`: `PlanView`, `SettlementPaint`, `VillageStyles`, `CityInteriors`).
The grammar reads any `PlanView` (Carillon's plan is one); Carillon paints byte for byte as before (a fingerprint of all
1,388 of its chunks, 7d384bdd423e85f6, the same before and after; its plan-hash test unchanged). A settlement's plot
carries its style in `Plot.district` (16 + land x 4 + wealth): ten lands (temperate, frost, desert, savanna, mire,
highland, elder, coast, jungle, cherry) by three wealths (poor, modest, prosperous), every house drawn on its own:
rubble or ashlar below and plaster and timber above in the heartland, log longhouses under steep spruce in the frost,
sandstone under flat roofs in the desert, whitewash and blue shutters by the sea. Lower and plainer than the city (one or
two storeys, no jetties or shopfronts), with the same climbing grid. Every house is enterable (panes, real doors of the
land's wood) and **furnished** by `CityInteriors` from who lives there (`SettlementPaint.Interior(adults, children,
trade, seed)`): a bed each (a couple's double, the children's upstairs), a hearth under the chimney, a table and seats,
storage, the trade's blocks (the smith's anvil and blast furnace, the inn's bar, tables and guest beds, the scribe's
lectern and shelves, the miller's sacks), rugs, pots; a real staircase (five steps, the floor cut over it) or a ladder;
at most two lights a house. Generic over plots, for Carillon's B4 to adopt.

**The people** (`VillageRoster` on W0's `RosterBuilder`): a graph from the streets (a node every four blocks, lanes
joined at their branch), a door for every building, places for the work (each field's edge, the pen's gate, the forge,
the well's trough, the bell, the benches, the green's play, the woodlot, the pier's end, the mill, the inn's taproom
inside its door with the guests' floor found by painting the inn alone, the lamps, the Watch's posts and the roads
out). Errands spread over the whole place, never round the well: the board, the benches, the stall (buying), every other
home's door (a visit), the green itself and a corner every sixteen blocks of street. A place holds as many people as it
has spots (past them they'd stand in a heap), so the children's game spreads over twelve spots of the green, the green
holds ten, the stall's front four, the board three. Households of 1-3 adults and 0-3 children per home, 8-16 people in a hamlet, 20-50 in a village. Village jobs
appended to `Roster.JOBS` (21 farmer, 22 shepherd, 23 woodcutter, 24 miner, 25 fisher, 26 hunter, 27 carter, 28 elder,
29 scribe) beside the city's smith, tavernkeeper, miller, washer, lamplighter, bellringer, Watch, healer, stallkeeper
and children. **The day** (0 = 06:00): the bell at dawn and dusk (the ringer at the bell 05:18-06:42 and 17:30-18:36;
it rings six with Minecraft's own bell sound); work 07:00-11:30 and 12:50-17:50 (farmers hoeing, the woodcutter's axe,
the smith at the anvil, the fisher at the pier with his rod, the shepherd leaning on his crook); the midday meal at home;
from half past six half the grown-ups at the inn or on the green till ten; hunters and miners out by the road at dawn and
back at dusk, the hunter with a kill on his pole; the carter's round of the barn, granary, inn and mill with a crate;
the lamplighter's round lighting the lamps at 18:10 and putting them out at 05:00; the Watch at the posts and walking
the edge 17:30-04:50; the elder on a bench and, of an evening, at the inn. Clothes from the city's trades by hash (a
farmer wears a porter's, a fisher a boatman's, a scribe a clerk's). Ledger cost with a village of 38 awake: 0.003-0.005
ms a tick (`village_day`).

**Speech** (`data/awaken/village/village_lines.json`, 729 lines, read by `citizens.Lines` under `village.`): greetings by
job and standing (`village.greet.<job>.<stranger|known|friend|kin>`), calls at work, news of the village and the region,
trade, the board, the inn's offer, room and rumours, the scribe, the elder, the bell, and what people say when struck,
robbed or their fields trampled; `{village}`, `{region}`, `{elder}`, `{gate}` (the compass way to the nearest gate) filled
by the village's `Settlements.Voice`. Talking to a trader opens their goods (`Settlements.Talk`); the elder tells news.

**Trade in emeralds** (decision 5; `Trade`, `Emeralds`): `EconomyHooks` is installed with the emerald (balance,
charge, and pay once per claim id; the claims are kept in `awaken:villages`). Every trade sells what it makes and buys
what it needs (vanilla's merchant screen); every land's villages make something of their own (the frost's furs and
berries, the desert's glass, the mire's slime and clay, the highland's copper, the coast's ink, prismarine and nautilus
shells, honey in the heartland...). The scribe sells three enchanted books a day (the tradeable enchantments, 9-25
emeralds by level), paper, books, maps, name tags. Prices show standing on the screen itself (the old price struck
through): a stranger pays one over on anything of three or more, a friend a fifth under, kin a third under; what a
village buys asks a stranger an eighth more goods and kin a fifth fewer, and is capped a play-day per player (nothing
ever locks for good).

**Standing** (`VillageState`, per settlement and player): points, Known at 10, Friend at 35, Kin at 80; a trade +1
(four a day), a filled notice +4 to +8, a night at the inn +1, two blocks of a house broken -1 (three a day); the
people's own recognition (W0) lifts a stranger to Known. Rising, every one of its people knows you better
(`Settlement.acquaint`) and a line says so.

**The notice board** (`Requests`, `client.NoticeBoardScreen`): right-click the board on the green: a timber board with up
to three pinned notes from the village's real state (damage: timber and stone to mend it; a smithy: iron; the inn:
bread; fields: bone meal; a pen: wheat; a frightened village: torches for the edge; a bell: copper; a pier: string;
lamps: glass; leather), renewed every other play-day, kept six days at most, counts shown, "Hand over" gives what you
carry; filled, it pays its emeralds once and its standing, lifts prosperity and mood, and goes into the history.

**The inn** (`Inn`): the innkeeper sells a room for three emeralds (the key is the receipt): the inn's guest bed becomes
where you wake if you fall, once (given back after you wake there, or after two play-days), and the night's rumour is
told over the fire.

**Sound and light.** The ambience slot in a settlement (`MusicLayers`, priority 90, under Carillon's):
`awaken:village/day` (birds over a murmur that grows with the people out of doors) and `village/night` (crickets, the
inn's voices till late), AmbientSounds' players the same without our birds and crickets; beds made from the city's
stems. The client sounds the forge's hammer through the working day, the mill wheel and the inn of an evening
(`citizens.client.VillageSound`), and puffs smoke from every chimney the grammar built (the roster's emitters, by hour
as in the city). Street lamps are the citizens' lamp heads (no light block; their light drawn, decision 26).

**Saved** (`awaken:villages`, schemaVersion 1): each settlement met (key, name, kind, land, origin, radius),
prosperity and mood (0-100), each player's standing, damage (every block broken in its planned ground by a player or an
explosion, with the block that stood there and the day, up to 2,048), buildings added, a short history (32 lines), the
board's notices, the day's counts; the paid claims; tonight's inn beds. The people's records are W0's.

**API** (§12): `dev.awaken.village.api.Villages` — `settlementAt`, `nearest`, `all`, `standing`/`addStanding`,
`damage`/`repaired`, `history`, `prosperity`/`addProsperity`, `mood`/`addMood`, `reserved(level, pos, margin)`. Citizens
gained `Settlements.onTalk(Talk)` and `installVoice(Voice)`, `Settlement.records/job/personName/age/body/say/acquaint`,
`RosterBuilder.emitter(x, ry, z, kind)` and `RosterSource.Built.emitters`. City gained `PlanView`, `SettlementPaint`
(`Layout`, `Canvas`, `Interior`, `paintChunk`, `sweep`, `standingCells`, `chimneys`, `dump`), `VillageStyles`.

**Measured.** Planning on real overworld noise: 0.3-0.6 s a hamlet, 0.9-1.3 s a village, once per settlement on the
worldgen thread that meets its start (the first hamlet's search at server start: a few tries). Painting: 0.35-1.5 ms a
chunk (about 1,600 cells; the grammar, the rooms and the ground), at most 4 light sources a chunk. Ledger with a village
awake: 0.0008-0.005 ms a tick. On GitHub's macOS runner (slower cores) the first hamlet planned in 5.2 s and a village
in 2.2 s; look tour 40313778099.

**Decisions taken here** (none of the plan's 13): the own pack and flag (above); the 640 grid and its odds; the first
hamlet's search order; the structure's step and the plan rebuilt from its anchor; the clearing of six; beards on the
streets and two past each house; half steps in the paving; spots spread over the green; styles keyed 16 + land x 4 + wealth;
two lights a house; the standing thresholds and the price rule; the board's needs; the inn's one fall; the claims kept
in the villages' file; village jobs appended to the city's list; the day's hours; the kit's civic blocks (they drop
nothing) used in village walls and furniture; gates 64 and Hearths 8 off a settlement's ground.

**Left.** W4: troubles (W0's director, `plannedEvents`), growth (prosperity raising buildings, `added`), the Rebuild
from the damage record, the history in the Journal, festivals. W6: settlers to the Hearth (Friend and Kin), a refugee or a
cured villager settling. W7: towns, mining towns, roads and caravans between settlements. Smaller: the Mire's houses
want stilts and the caravanserai a courtyard (styles only today); village clothes are the city's; a house smokes by the
hour, not by who is in; `/locate` can't find a settlement (`/awaken village list` does).

---------------------------------------------------------------------------------------------------------------------

## 29. Wild life (the wild's small ambient life, client-only; Euan 2026-10-26, built without an approval page)

Euan, after a forest walk: "I barely saw any insects or birds, so the sounds felt a bit off ... when you're exploring and
walking in the forest, it should feel very real, very alive. Not too much either." Module `wildlife` (package
`dev.awaken.wildlife`, client half `wildlife.client`). Everything that moves lives on the client: the server registers
one entity type (`awaken:wild_critter`, never saved, never tracked, never summoned) and 18 sound events, and pays
nothing. Nothing is huntable. Herds, packs, roosts and deer are the Living Wilds' (§21) server mobs; this layer never
makes a deer.

**Who.** Twelve bodies, hero rigs with the life layer (§23), from `tools/art/specs/wild_fauna.py` (textures
`textures/entity/wild/`, data `wildlife.client.WildFaunaData`): songbirds (robin, blue tit, chaffinch, wren, blackbird;
drawn at 0.42), woodpeckers (great spotted, green), crows, owls (tawny, barn; eyes catch the light at night), buzzards
and kestrels, gulls (Carillon's gull design), ducks (mallard drake and hen), grey herons, squirrels (red, grey),
rabbits, red foxes (eyeshine), frogs. Folded wings show their upper side; a spread wing hangs from the shoulder (the
spec's `FOLD` and wing lift). Seven insects drawn as real little bodies from one sprite sheet (`insects.png`): butterflies
(white, brimstone, peacock, common blue), bees, dragonflies (blue, green), fireflies (Lumen Forest's burn blue), moths,
gnat swarms, water striders; plus ripples on water and far flocks (finches by day, a small murmuration of starlings at
dusk) crossing the sky.

**Where and how many** (`Density`, pure; numbers are first numbers, tuned on tours). Each chunk near the player is read
once from the client's own heightmaps (`Sites`, one chunk a tick, re-read after 2 minutes): leaf tops (and the canopy's
edge), fences and walls, bare rock, trunks (a log under the canopy with 2+ blocks of clear bark), open ground, flowers,
still water, its shore (land beside water, facing it), lights. Expected animals per chunk = peak (per kind) × the
family's affinity (the 16 sound families, `#awaken:soundscape/<family>`) × the hour × the weather × the cold × the
habitat × an Awakened biome's flavour (Elderwood: woodpeckers ×2, owls ×1.5; Lumen Forest: fireflies ×1.6; Glass
Steppe and Shattered Highlands: raptors ×1.6; Mirror Marsh: herons and dragonflies ×1.6). Peaks: songbird 0.55,
woodpecker 0.06, crow 0.12, owl 0.05, raptor 0.006, gull 0.5, duck 0.6, heron 0.12, squirrel 0.22, rabbit 0.25, fox
0.025, frog 0.8; butterfly 2.5, bee 1.5, dragonfly 1.5, firefly 7, moth 2.5 a light, gnats 1 swarm, strider 2. The
hours (0 = 06:00): songbirds busiest at dawn (×1.5) and dusk, 0.75 at noon, none from 19:20 to 04:20; owls, moths and
fireflies at night (fireflies from 18:12, full by 20:00); rabbits and foxes at dawn and dusk; raptors 07:30-17:00;
butterflies 07:30-16:30; gnats at dusk. Rain: insects none (none in thunder), small birds ×0.4 and sheltering (fluffed
up, no song), raptors none, frogs ×1.5. Cold land (biome temperature < 0.15): no insects or frogs, songbirds ×0.5.
Targets the test holds: a temperate forest at noon 0.2-0.6 songbirds a chunk (one to three in view), a flowery meadow
1-3 butterflies a chunk, nothing but owls and fireflies in a forest at night. The count is the expected number made
whole by the chunk's fixed draw (`Density.count`), so a place keeps the same animals as the hours turn and looks alike
when you return; each animal's spot and coat come from the chunk's draw too.

**Reach and caps** (`WildKinds`, `Census`): made within (blocks) songbird 40, woodpecker 40, crow 64, owl 52, raptor 120,
gull 80, duck 56, heron 64, squirrel 32, rabbit 40, fox 56, frog 22; butterfly 28, bee 16, dragonfly 28, firefly 40,
moth 24, gnats 24, strider 16; dropped 16 past that (out of sight). At most 9 songbirds, 2 woodpeckers, 4 crows, 2 owls,
2 raptors, 6 gulls, 6 ducks, 2 herons, 3 squirrels, 4 rabbits, 1 fox, 4 frogs, 14 butterflies, 10 bees, 8 dragonflies,
64 fireflies, 12 moths, 5 swarms, 10 striders; 18 bodies and 140 insects in all. Chunks are walked nearest first, so the
caps keep what is close. Never inside Carillon's walls (+24 blocks; `CityLife.inside`, the city's animals are B3b's),
never outside the overworld.

**Never popping.** A body appears only out of view or 26+ blocks off (48 for one in the sky); insects fade in and out.
An animal no longer wanted leaves the way it would (a bird flies off, a rabbit bolts, a frog plops in, a squirrel climbs
away) and is dropped once out of sight.

**Seen, not just near** (the tours, 2026-10-26): in a forest the census logged nine songbirds near and none in view,
all on the canopy's roof, above the leaf ceiling a walker stands under. So songbirds take the lowest of three perches
(the outer rings of the trees' lower leaves, fences), and by day 75% of those in a closed forest (45% under an open
canopy) start on the forest floor, forage there, flit low through the understory to another patch of floor (the flight
hugs the ground ahead instead of climbing over the trees), and only now and then go up. Insects are drawn near twice
life size (a butterfly 0.3 blocks a wing, a dragonfly 0.46 long), as the birds are: at life size a butterfly was a
few pixels at ten blocks. Fireflies glow two fifths of their rhythm with a 0.75-block halo.

**Behaviour** (`Birds`, `Critters`, `Insects`). Songbirds never still on a perch (glances, tail and wing flicks, a bow,
hops along the branch), sing from it (the dawn chorus three times as often), preen, flit to the next tree in a bounding
flight (three beats, wings shut, a dip), drop to forage on open ground and hop about pecking. Woodpeckers hitch up a
trunk, peck, drum (more in the morning), slip round to the far side as you come within 9, fly off at 3. Crows stride,
peck and caw on open ground, wary at 10. Owls perch at night, follow you with their heads, hoot, and fly off without a
sound. Buzzards circle on a drifting thermal 24-40 above the ground, banked, beating now and then, mewing; kestrels drop
to hover over the meadow (head still, quick shallow beats) and stoop. Gulls ride the air over the shore. Ducks paddle,
dabble tail up, preen, quack, leave rings, paddle away from you and fly off at 3.5. Herons stand like statues at the
shore, stalk, strike, turn alert at 20 and leave at 12 with a croak and slow heavy wings. Squirrels run up and down
their trunk in bursts, scold, forage at its foot and race back up when you come. Rabbits graze, groom, sit up tall
with ears high at 11 and bolt in a zigzag at 5. A fox trots its ground at dusk, sits and watches you, listens and
pounces on something in the grass, curls up at night, slips away at 10. Frogs croak at dusk and in the rain and plop
into the water at 3.5. Flushes are away from you, a burst of wings; sprinting doubles the distance, sneaking halves it.
Birds bank into their turns and pitch with their climb. Butterflies flutter (seven beats a second) between flowers and
settle with wings opening and closing slowly; bees zip flower to flower; dragonflies hover and dart low over still
water; fireflies drift and blink each to its own rhythm (some answering with a second flash) in an additive glow pass;
moths circle lamps; gnat swarms bob by the water at dusk; striders skate in strokes, a ring at each.

**Sound** (`WildSound`; voices from `tools/art/specs/audio_wild.py`, synthesised, mono, placed; 18 events `wild.*`): a
call comes from the animal you can see calling, its clip in step: songs (robin, tit, finch, wren; a blackbird sings the
robin's phrases lower), contact chips, a flush's whirr, big wings, the woodpecker's drum and taps, caws, the buzzard's
mew, hoots, quacks, the heron's croak, a squirrel's scolding, a frog's plop (its croak is vanilla's `entity.frog.ambient`,
a bee's buzz vanilla's `entity.bee.loop`), rustles; the gulls use Carillon's `city.gull.call`. At most 6 at once and 3
new a second, counted in the Conductor's voice budget, quieter where the world is hushed. AmbientSounds' bed plays as
before; these sit on top. Crickets at night: three voices placed in the grass 7-16 blocks off, each silent while you walk
within 6 of it and singing again 6-10 s after. Predators: every monster within 24 of the player hushes the birds and
insects within 24 of it to a quarter (the Conductor's hush, renewed every second; docs/sound/PLAN.md's reaction), and
every visible animal reads `Soundscape.hushAt`: no song below 0.67, leaving below 0.35, none made below 0.6 (so open
gates and the Living Wilds' happenings quiet them too).

**Budget.** Client only; the server pays nothing. Per tick: one chunk read (256 columns, three heightmaps), the brains
of at most 18 bodies, the insects' arrays (no allocation per tick or frame; the director's half-second pass allocates
nothing but a new animal), at most 6 sounds. Per frame: at most 18 small hero models, about 900 insect quads and a few
hundred glow quads, culled by distance and view.

**API additions** (§12): `core.audio.client.Soundscape.hushAt(x, y, z)` (what is left of birds and insects at a point);
`citizens.client.CityLife.inside(x, z, margin)` (within Carillon's wall plus a margin). Client command
`/awaken wildlife [on|off|count|dial <x>|lineup [clip|insects|off] [variant]]` (`count`: what's near, what the camera
can see, and where the songbirds are; `lineup`: every body, or every insect, posed before the camera on a stage); tours
log the census every 30 s as `[wildlife] in view N of M near: ...`. Tours: `tools/autopilot/wild-life.txt` (the stage by
day and night, then forest, rain, flowers, river, Mirror Marsh, Lumen Forest), `wild-forest.txt` (the quick loop).

**Tests** (`wildlife_`): `wildlife_density` (the tables reach every sound family, the hours run in order, the walk's
targets hold, rain and cold clear the insects, a chunk's draw is fixed and the land averages the expected number),
`wildlife_census` (the caps hold over 400 rich chunks at four hours; nothing is made inside the city and anything there
leaves; nothing out of reach), `wildlife_rigs` (every rig parses and carries every clip its brain plays, every coat,
blink and the insects' sheet exist); `audio_events_resolve` covers the 18 events.

---------------------------------------------------------------------------------------------------------------------

## 30. The band's pages (the K menu; plan docs/k-menu/PLAN.md, page https://claude.ai/artifact/49cQxv5y3CzXcJ3SG4jDN9; Euan approved all of it 2026-10-26)

Euan, 2026-10-26: the K menu "just feels like a dashboard"; the Journal sat above the stats ("you have to scroll down a
ton just to get to your stats"); merge the Gate Map and the Atlas. The Status tab stacked 22 sections in a 246×192
window (stats 1.9 to 3.4 screens down, Powers 5 to 7). Approved: direction B, every recommended answer (PLAN §7):
six pages; K always opens on Status; points placed then confirmed (phase 2); the panel 288 units wide; M opens the
band's Map page; a denser tint by day (phase 3); your face on Status (phase 2); "Clear Atlas cache" to the settings
(phase 4); phase 1 first; the map opens in the world (phase 4); others see a content-free projection (phase 4).

### 30.1 Pages (phase 1, built)
- **Six pages, in the rail's order:** Status · Powers · Journal · Truths · Codex · Map (`StatusSections.Tab`;
  `Tab.label()`). `Tab.GATE_MAP` is gone.
- **The rail** (holo, `holo.ui.BandLayout`, `HoloStatusScreen`):
  - It runs down the panel's wrist edge, 30 units wide: six nodes 31 units tall, one every 34 from y 9.
  - Each node has a 12×12 pixel icon and its label in spaced capitals at two framebuffer pixels fewer per font pixel
    than the body text (3 at 5 px a unit, 4 at 6).
  - The open page is lit (a fill and a bright bar on the wrist edge).
  - Badges: points to spend on Status (amber), new Journal lines on Journal.
  - The K key sits at its foot.
- **The panel:** 288×228 units (was 272×228). That is still 5 px a unit at 2880×1800 and 3024×1964 and 6 at 3840×2160
  (`holo_band_layout` checks it); the content is x 40, y 10, 232×208, with its scroll bar beside it.
- **The flat fallback** (`StatusScreen`) has the same pages on its tab row.
- **Keys:** K always opens on Status unless a link names a page. 1–6 open a page; Tab and Shift+Tab step through the
  pages that show here; M opens the Map (the Atlas key, `StatusSections.pageForKey`).
- **Map:** a page with an action (`StatusSections.setPageAction`; the atlas module registers `MapScene.openFromKey`,
  key M). Picking it closes the band and opens the map in the world (§30.5), or the table where the scene can't open.
- **Where each section lives** (order in brackets). Earlier sections of this SPEC that say "K › Status" mean these
  pages now.

  | Page | Sections |
  |---|---|
  | Status | STATUS (0), STATS (10), WAITING FOR YOU (14, only with a decision waiting), THIS GATE (15, only in a run: `gates.client.RunSection`, the run summary and View Satchel that were on the Gate Map), DEEP PACK (16), THIS ROOM (17), JOURNAL (20, the filed lines) |
  | Powers | POWERS (0), THE ASCENT (28), THE BODY (30), CRAFTS (160), THE VEIL (320) |
  | Journal | NOW (−30), JOURNAL · CHAINS (−20), DIRECTIVES (−15), DAILY REGIMEN (−12), WHAT WAITS / SAVED FOR LATER (5), HEARTH (40), ANCHOR REQUESTS (41), JOURNAL (700, the filed lines again), RECENT MESSAGES (800), RECORDS (900) |
  | Truths | the Path choice (0), Truths (10–30), THE ROAD (35), TITLES (40), LATENTS (50) |
  | Codex | THE BOARD (0), HAPPENINGS WITNESSED (460) |
  | Map | the map in the world (§30.5), or the Atlas table |

- **Gates on the Atlas** (`MapMarks.Gate`, `MapMarks.registerGates`, `MapMarks.gates`):
  - Every gate whose pillar you can see (the gates module, from the synced beacons) and each gate your scanner read (the
    above module, marked as a reading) stands on the table as a pillar of its rank's colour, with a dark core so it
    reads over cloud.
  - Its name ("D-rank gate", or the scanner's name) and how long it holds ("breaks in 2 days", "broken: its monsters
    are out" in red, "calm") hang over it from zoom 0.3.
  - Where you've explored, the pillar stands on the ground; elsewhere it rides the clouds, because you saw its light
    from afar.
  - A reading within 24 blocks of a pillar you can see is dropped. Overworld only.
  - The Gate Map's radar and lists (gates and scanner) are gone.
- **The Atlas picture** (the glance) left the Status page. `AtlasGlance` keeps its painter for the phase-4 scene.
- **Links:**
  - `ProgressionClient.openBand(Tab, String... focus)` opens the band on a page in the player's style (`BandOpener`,
    installed by holo), scrolled to the first named section, only while the band is worn.
  - `OpenBand(page)` carries the page (`Progression.openBand(player, page)`; `BAND_PAGE_ASCENT` = −1 opens Powers at THE
    ASCENT).
  - The ascent's "Show me" card and the climb ward's ShowMe both open Powers at THE ASCENT (else THE BODY).
- **Words:** Bram's line at the Registry is now "That's your band. Your status and your stats are on the first page, and
  the map is the last one. Press the same key to close it."
- **Autopilot:** `screen holo status|powers|journal|truths|codex|map`; flat `screen status|powers|journal|codex`;
  `screen map` (and `screen holo map`) opens what the Map page opens (the scene); `screen table` the flat table.
  `holo clicktab <i>` clicks rail node i.
- **Tests:** `holo_band_layout` (nodes inside the rail and apart, the K key clear, content clear of the rail, labels on
  whole pixels, 5 and 6 px a unit kept on the owner's screens); `signal_status_screen_frame` now lays out the six
  labels.

### 30.2 Still to build (PLAN §6)
- Nothing from the plan's five phases. Later: the Journal entry opening in place ("Show on map", "Open in the Codex"),
  the full step list of the tracked chain (the living module sends only the step you're on), the adaptive tint sampled
  from the backdrop (today it follows daylight and the camera's pitch), a controller map, and the map scene's later
  items (§30.5).

### 30.3 The Status sheet (phase 2, built: `progression.client.StatusSheet`)
- **The hero** (Status order 0, no title row): your face (the skin's face and hat layers, 8×8 scaled up, in corner
  ticks), your name, your title in amber, then LEVEL in spaced capitals with the number at twice the body size, the XP
  to the next level as segments (3 units each, 1 apart, the one filling half lit), and "Level N opens X" from the level
  road (`Content.LEVEL_ROAD`). The XP gained since you last looked fills in when the band opens (not with reduced
  motion). Your rank stands at the right as a pixel diamond with its letter; hovering it says what the next rank opens,
  or "TRIAL READY" when the trial is.
- **The six stats in one line** (order 10): STR AGI VIT INT PER SEN, each a column with its value; the header reads
  STATS with the points to place (amber) and any fracture chip.
  - While points wait, a + diamond sits under each stat. Click it (or arrows to focus, Space) to place a point there.
    Placed points show at once as +N in amber and in a PLACED list; nothing is sent yet.
  - **Confirm** (Enter) sends one request for all of them (`StatusAction("spend_many", "STR:1,AGI:2")`); the server
    places each through `Progression.spendPoint` and skips what you don't have (at most 500). **Undo** (Backspace)
    takes the last one back. Placed points are kept while the band is shut. On a confirm the stats that rose flash amber
    for 600 ms.
  - The focused stat's line under the row says what it does in plain words.
- **The context row:** the top Now suggestion with its compass sits under the stats (`NowRow`, order 18: "NOW · n more
  on the Journal page ›"), then the decision waiting (14), this gate (15), the Deep Pack (16), this room (17), and the
  newest Journal lines (20, four of them, "ALL n ›").
- **Labels** (`BandHeader`): small capitals in the labels' colour, a hairline to the edge and a note on the right
  ("ALL 23 ›", a click opens its page), instead of loud titles.
- **Sounds:** `holo.spend` (a rising step per point), `holo.confirm`, `holo.undo` (`StatusSections.Cue`).
- **Test:** `progression_spend_many` (a mixed request places what it can, skips nonsense and what you can't afford).

### 30.4 The look and the motion (phase 3, built)
- **Tint by daylight:** the band's glass is denser by day: 0.46 at night or under a roof, up to 0.60 in full daylight
  looking at the sky (`HoloRender.tint`, from sky darkening and the camera's pitch), so text holds against bright sky
  and snow.
- **Light from the wrist:** a soft blob of the band's light (150 units, 0.10 alpha) at the panel's wrist corner, as if
  the band lit the glass from below.
- **Pages print in:** changing page sweeps the scan line down the content (40 ms, then 140 ms; `HoloPanel.rescan`), and
  the rail's lit bead slides to the new page in 120 ms. Off with reduced motion.
- **Type:** body text at the panel's pixel size, `small()` one framebuffer pixel fewer, `micro()` (labels) two fewer,
  never under 2 (`StatusSections`).
- **Sounds:** `holo.page` (one soft glass tick, a pitch step per page), `holo.spend`, `holo.confirm`, `holo.undo`, from
  `tools/art/specs/audio_holo.py`, on the Interface slider.

### 30.5 The map in the world (phase 4, built: `atlas.client.MapScene`, `MapCamera`, `AtlasScreen` scene mode)
Euan's idea: the camera pulls back and you see your character looking at the map, a hologram from the band, "still
really sharp, with the clouds and everything".
- **Opening:** M (or the band's Map page) raises your wrist (120 ms), then the camera rises out of your eyes to a shot
  2.4 m behind your feet, 2.2 m over your right shoulder and 4.6 m up, looking the way you face and 38° down
  (`atlas.MapShot`), easing in over 320 ms after a 60 ms wait (190 ms after 30 ms when it opened in the last 12 s). The
  map is usable at once; it is settled by 420 ms (220 ms again).
  - The camera is a client-only marker entity the game's camera follows (as the opening's, `OpeningCamera`), placed
    every frame from `ComputeCameraAngles` at LOWEST. It follows you if you're pushed; it never turns with you. While it
    holds the view the hands, the block outline, your name tag and the HUD stand aside (chat, subtitles and the action
    bar stay). The world never pauses.
  - **Room:** the line from your eyes to a little past the camera's place must be clear; else the left shoulder (the
    layout mirrors), then a higher, steeper shot (1.4 m back, 6 m up, 50° down), then the table.
- **The card:** the Atlas's own picture (the same renderer as the table: land, zones, clouds, pins, gates, rumours) on
  the band's projected glass (78% navy), on the far side of the frame from your shoulder (33.5% to 97.5% of the width,
  7.5% to 68% of the height: it ends above and right of your raised wrist, so the beams show between them). Up the map is the way the camera looks. The cut edge fades into the glass; the world
  behind dims (Lens through the focus, plus a soft veil that is darker at the top). Beams of the band's light fan from
  your wrist to the card's near bottom edge (`holo.client.HoloScene`, drawn by the wrist layer). The card scans in from 120 ms
  and the land prints in from the middle, faster than on the table.
- **The legend** floats on its own glass over your head, on the near side: GATES (the four nearest you know, with
  their distance; Tab steps through them, a click picks one and the map glides to it), the picked gate's **Point me
  there** (T), KNOWN LANDS (click to fly there) and the DANGER key. It never reaches lower than 56% of the height.
- **Point me there** (scene and table): the Now line leads with "Go to the D-rank gate" (`NowClient.pin`, id
  `awaken:map_pin`, priority 86, client-side), with its compass and the Signal's thread, until you're within 12 blocks.
- **Keys** (one line along the bottom): drag turns the map, right-drag or WASD moves across it (your body stays still),
  scroll or +/- zooms, Q/E turn, R/F tilt, C comes back to you, Tab gates, T point me there, 1–5 open a band page, M,
  Esc, K or Space close (250 ms back to your eyes). The table takes the same held keys and Tab/T.
- **The table instead,** at once: Shift+M, or M again within 0.3 s of opening (from the scene); in a fight
  (`SystemHud.inCombat`: hurt in the last 5 s or a hostile within 8); with reduced motion; with the setting "Map opens
  as: Table" (`config/awaken-atlas-client.toml`, `mapOpens`); without the band; while another camera holds the view;
  asleep or spectating; with no room. Inside a gate the Atlas's own message stands.
- **The fight rule:** hit, or a hostile within 8 blocks, while the scene is open: it folds in 150 ms with a sharp glass
  tick.
- **What others see** (`holo_state` kind MAP = 3, relayed and rate-limited like STATUS and INVENTORY): your raised
  wrist and, before you at the waist, a content-free disc of light (a ring with compass ticks, a few drifting points,
  a cone from the wrist). Never your land, pins or labels.
- **Settings moved:** "Clear Atlas cache" is a setting now (`clearAtlasCache`, turns itself off once done); "Share with
  party" shows only while other players are online.
- **Cost:** the Atlas picture costs what the table does; the scene adds your player model, the beams (two thin quads
  and a fan) and the GUI glass. Nothing is loaded for the camera move: the same place, seen from 5 m back.
- **Test:** `atlas_map_shot` (behind you, over a shoulder, 38° down, the left mirrors the right, the eases, the short
  way round, the promised timings, the MAP kind).
- **Later:** a real 3D mesh in the world you could walk round; party members within 8 blocks seeing the real map;
  holding Shoulder Surfing and Camera Overhaul off while the scene runs (today they may fight the camera).

### 30.6 The other pages, narration and Band size (phase 5, built)
- **Powers** (`powers.client.PowersSection`, the page's lead): ON YOUR KEYS with your energy at the right ("ENERGY 120 /
  120 · 6.0 A SECOND"); your six keys as 30-unit sockets with your own bindings on keycaps under them (R and Alt set
  apart); YOUR POWERS · n, the grid (level pips, mastery under them, a blue dot when on a key, green when passive); then
  a card for the power picked or under the cursor: its icon, name, KIND · SLOT · ENERGY · COOLDOWN in small capitals,
  mastery as twelve segments and "Mastery 34% to level 4", what it does, and what to press next in amber ("Press Z, X,
  C or V to put it on that key."). Pick a power, then press the key it should go on (your bindings) or click a socket;
  the sockets it fits light amber with corner ticks. Esc lets go of a pick before it closes the band. The card's height
  fits the longest description you own, so the page never jumps.
- **Journal:**
  - **Filters** across the top (`progression.client.JournalFilters`): EVERYTHING, TASKS, FINDS, MESSAGES; click one or
    Q / E. Each section names its filter (`StatusSections.Section.shelf()`, TASKS by default; the filed lines and
    RECORDS are FINDS, RECENT MESSAGES is MESSAGES; null shows under every filter). Screens ask
    `StatusSections.shows(tab, section)`.
  - **TRACKING** leads (`living.client.JournalSection`, order −40): the tracked chain's name in white on a lit horizon
    bar and "▸" the step you're on; the other chains and bounties follow under ALSO ON, one line each with their step
    in small type. With nothing tracked the heading is CHAINS.
  - The page's filed lines are its **LOG** ("LOG · 2 NEW"); the Status page keeps "JOURNAL".
- **Truths:** each Truth on its own card of glass, its bar amber while it waits on you and green once true; "Gives:"
  instead of "Reward:".
- **Narration** (`StatusSections.narrate`, spoken only when the vanilla narrator is on): a page change ("Journal
  page"), a stat's focus ("Agility 22. You move faster, and Flow builds sooner. 3 points to place."), a power picked
  (its name, what it does, what to press), a gate picked on the map (its name, distance, when it breaks, "Press T to be
  pointed there.").
- **Band size** (`config/awaken-holo-client.toml`, `bandSize`: SMALL, NORMAL, LARGE): one whole framebuffer pixel a
  unit less or more than Normal (`HoloPanel.sizeStep`), never past 80% of the screen's height: 4 / 5 / 6 px a unit on
  the MacBook, 5 / 6 / 7 on the 4K Dell. Every type size steps with it, so text stays sharp.
- **The map's legend** says "You haven't seen a gate yet. Their light shows from far off: look for a pillar in the
  sky." with no gates seen, "+ n further out" past the four nearest, and puts the danger key's colours on a line of
  their own when they don't fit beside it.
- **Autopilot:** `screenkey tab|t|m|c|space|q|e` (or `shift+` one) sends a key to the open screen.
- **Tests:** `holo_band_layout` also checks the Band size steps on the owner's screens.
