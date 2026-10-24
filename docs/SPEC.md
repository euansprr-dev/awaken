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
lucidium, veil, above, hearth, story (bools) · cameraShake · signalTypeSpeed · playtestMode (off).

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
- `dev.awaken.body.Body` — `unlocked(Player, Move)`, `isSliding`, `isTacticalSprinting`, `isMantling`, `lastMoveTick` (both sides).
- `dev.awaken.terrain.api.BiomeTable` — `archetype(Holder<Biome>)` (the vanilla biome an Awakened one stands in for, else
  its own key), `tag(String path)` (the table's `awaken:` biome tags: `fauna/`, `has_lair/`, `lair_style/`, `cottage/`).
- `dev.awaken.terrain.api.CityBasin` / `CitySite` — the city basin (§15): `request(Predicate<MinecraftServer>)` (the city's
  rule, once at mod init), `site(server)` (centre, core and blend radii, street Y, the river's centre line, whether the
  basin is carved), `reserved(x, z)` (inside the core plus 48: thread-safe, allocation-free; worldgen and runtime
  placement keep out), constants `STREET_Y` 70, `CORE_RADIUS` 330, `BLEND_RADIUS` 700, `RIVER` (the city's fixed path).
- `dev.awaken.core.api.*Hooks` — install-once bridges between modules: `GateHooks`, `HearthHooks` (`inside`), `MobHooks`,
  `StoryHooks`, `DescentHooks`, `DreamHooks`, `PowerCoreHooks`, `NowHooks` (the Now tracker), `WorldHooks` (landmark rumours),
  `MovementHooks` (the body's moves for powers), `PlayerClips` (client: full-body clips on a player when the Player
  Animation Library is installed; a no-op otherwise).

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
- The Now tracker names the nearest unreached landmark once there is a reason to go (Well from level 10, vault and
  monolith 4, spawn vault 2, ocean lab 6, sky platform 20); never inside a Hearth or a gate. Mira tells one landmark
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

Holo UI (details in docs/modules/holo.md; design docs/proposals/holo-ui.md, P1 and P2):
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
- Client settings (`awaken-holo-client.toml`): `statusStyle`, `hudMotion`, `reducedMotion`.

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

---------------------------------------------------------------------------------------------------------------------

## 14. The Awakened Pack (companion mods; plan in docs/plan-pack.html, approved 2026-10-24)

- Awaken stays mixin-free and plays the same with no companion mod installed. Integrations live in `dev.awaken.compat`;
  each class is loaded only when its mod is present (`ModList` check), compiled against the mod as `compileOnly` from
  Modrinth's Maven, never bundled or required.
- The pack is `pack/mods.json` (profiles: perf, horizon, feel, sound, comfort, together, ultra, tools), pinned by
  `tools/pack.py lock` into `pack/lock.json` (exact files and SHA-512, required dependencies, a dependency's own
  version pin). `tools/pack.py install <game-dir> <profiles>` puts exactly those files into a game folder, removes only
  what it installed, and refuses while a client runs there. GameTests never load the pack: they test Awaken alone.
- Far view (`compat.FarView`): far-terrain mods draw in vanilla dimensions and `awaken:stillpoint` only. In every other
  `awaken:` dimension (gate and dream cells, trials, the Halo, the Descent) Distant Horizons renders nothing and gets
  empty columns instead of running Awaken's chunk generators. Test: `compat_far_view_policy`.
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
  dither; five passes, three of them at 960×600 or smaller) and `awaken:lens_cinematic` (adds light shafts). It runs
  first in the player's post-effect list, so gameplay effects (dreams, blight, the Veil) still draw on top, and
  re-asserts itself every tick because the server replaces that list when it syncs. Its values travel in a 16×1
  texture (`awaken:lens_params`), written once a frame in `ViewportEvent.RenderFog` at LOWEST priority: the look, the
  depth range (26.3 is reversed-Z; zero-to-one depth differs between OpenGL and Vulkan), the final fog colour and
  distances, the sun's screen position and the sunrise colour. Looks per dimension live in
  `assets/awaken/lens/looks.json` (schemaVersion 1; exact id, then `prefix*`, then `default`) and ease into each other
  over about 1.5 s. Lens never lifts shadows (exposure ≤ 1.05) and backs off where the game's own fog is thick.
  It steps aside while an Iris shaderpack is on. Client config `lens` = OFF | SWIFT (default) | CINEMATIC.
  With Distant Horizons drawing, Lens hazes far terrain by DH's own depth (Lens and far terrain, below); only where
  it can't read that depth does it add no distance haze while DH draws, since hazing only the near terrain would draw
  a ring where it ends.
  Story moments override the place's look (`LensClient.override(lookId, fadeTicks)` / `clearOverride`) with named
  looks from the same files (`awaken:ritual`, `awaken:void`); texel 13 carries the tear from black (`LensClient.tear`,
  0 off, 1..255 its progress), crushed blacks and extra bloom (`LensClient.ritual`). Texel 14 carries the Holo UI
  focus (`UiHooks.focus()`, 0..1 while a holo panel is open): the world more than about 2–3 blocks away dims to ~60%
  and cools, while the panel's glass (it writes depth) and its text stay bright; the first-person arm dims with the
  world, since its depth lives in a separate target post chains can't read. Texel 15 is far terrain's planes. A story scene plays its look like a cutscene even while Lens is off: the chain runs for
  the scene's length and fades back to the plain frame (the look rests at neutral while Lens is off) before stepping
  aside again (client config `lensInScenes`, default true; never with an Iris shaderpack). Callers carry on without the
  look when Lens can't draw (`LensClient.active()`).
- Lens looks load from every `assets/<namespace>/lens/*.json`, merged by key in path order (modules add their own
  file, e.g. looks_lands.json, never edit looks.json). Key `biome:<id>` wins where the camera's biome matches (sampled
  every 10 ticks; biome-to-biome changes ease over ~3 s, dimension changes over ~1.5 s); a story override wins over
  both.
- Sodium is pinned to 0.9.3-beta.1: 0.9.2 crashes at startup on NeoForge 26.3.0.95 (a LevelExtractor mixin no longer
  finds its target). Iris 1.11.7 requires exactly 0.9.2, so the Ultra look waits for an Iris update.
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
  in the last 0.2 s, texel 0 a bit 0 reverse-Z, bit 1 zero-to-one, bit 2 Lens reads the depth. Far terrain is hazed by
  its real distance (aerial perspective deepening to 8× the render distance: DH keeps the fog's `renderDistanceEnd` at
  the vanilla value and pushes only the start and the environmental fog out of reach, so the haze runs on across the
  end of the vanilla render distance without a step), and never counts as open sky for bloom, light shafts or the sun
  halo. Where DH has no texture to share (its OpenGL engine; bit 2 clear) Lens adds no distance haze while DH draws.
  Debug views (`lensdebug`): 3 raw far depth (blue while Lens reads it), 4 the haze each pixel gets (a ring would show
  as a step in brightness). Proof: tools/autopilot/lens-dh.txt. Params texels: 13 the ritual, 14 Holo.
- The Look switch, AWAKEN · LOOK (`compat.client.LookScreen`; the rules in `compat.Look`): three tiers, and one choice
  sets every companion that offers a supported way, writing only the keys below, so each mod's own menu still works
  for the rest. Opened with U (`key.awaken.look`, Awaken's key category), from Awaken's settings (Mods → AWAKEN →
  Config → the client section's "Look" row, under Lens) and by autopilot (`screen look`, `lookswitch <tier>`). The
  default stays SWIFT (client config `lens`). The current tier is read back from the settings (Lens, Iris's shaders
  switch), not stored twice.

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
  only with an Iris newer than 1.11.7 on the OpenGL renderer; otherwise its card is disabled with the reason (today:
  Iris 1.11.7 requires Sodium 0.9.2, which crashes on NeoForge 26.3.0.95). The frame-time line: the last 60 s of
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
- Until the vertical slice lands, no Awakened biome is placed (the stage-0 placeholder Elderwood exists in the
  registry and the table but has no injector), so new worlds never show an unfinished biome.
- Placement: Lithostitched `replace_partially` injectors swap a vanilla biome for an Awakened one where the
  `awaken:lands/awakening` density function (a low-frequency noise, patches roughly 500–1,500 blocks across) passes a
  threshold, so Awakened lands come in regions you walk into, each in the climate of the biome it replaces. Thresholds
  are tuned with the studio until Awakened biomes cover about 40% of land on the five studio seeds.
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

