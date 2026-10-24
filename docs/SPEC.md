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
- `dev.awaken.core.api.*Hooks` — install-once bridges between modules: `GateHooks`, `HearthHooks` (`inside`), `MobHooks`,
  `StoryHooks`, `DescentHooks`, `DreamHooks`, `PowerCoreHooks`, `NowHooks` (the Now tracker), `WorldHooks` (landmark rumours),
  `MovementHooks` (the body's moves for powers), `PlayerClips` (client: full-body clips on a player when the Player
  Animation Library is installed; a no-op otherwise).
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
  baseline), Ultra (Iris, experimental: OpenGL only and unsupported on Apple Silicon by Iris).
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
  With Distant Horizons drawing, Lens adds no distance haze (DH fogs its own terrain and turns vanilla fog off; Lens
  hazing only the near terrain drew a ring where it ends): the same-moment A/B tour (tools/autopilot/lens-dh.txt)
  showed far terrain intact with Lens on, and DH's depth texture already cleared by post-effect time.
  Story moments override the place's look (`LensClient.override(lookId, fadeTicks)` / `clearOverride`) with named
  looks from the same files (`awaken:ritual`, `awaken:void`); texel 13 carries the tear from black (`LensClient.tear`,
  0 off, 1..255 its progress), crushed blacks and extra bloom (`LensClient.ritual`). Texel 14 is the Holo UI focus's;
  15 is far terrain's planes. A story scene plays its look like a cutscene even while Lens is off: the chain runs for
  the scene's length and fades back to the plain frame (the look rests at neutral while Lens is off) before stepping
  aside again (client config `lensInScenes`, default true; never with an Iris shaderpack). Callers carry on without the
  look when Lens can't draw (`LensClient.active()`).
- Lens looks load from every `assets/<namespace>/lens/*.json`, merged by key in path order (modules add their own
  file, e.g. looks_lands.json, never edit looks.json). Key `biome:<id>` wins where the camera's biome matches (sampled
  every 10 ticks; biome-to-biome changes ease over ~3 s, dimension changes over ~1.5 s); a story override wins over
  both.
- Sodium is pinned to 0.9.3-beta.1: 0.9.2 crashes at startup on NeoForge 26.3.0.95 (a LevelExtractor mixin no longer
  finds its target). Iris 1.11.7 requires exactly 0.9.2, so the Ultra look waits for an Iris update.
- Lens and far terrain: Distant Horizons draws without the game's depth, so Lens reads DH's own depth through
  `compat.client.FarTerrain` (texture `awaken:lens_far_depth`; DH's clip planes and depth convention in params texel 15
  and texel 0's b/a). Far terrain is hazed by its real distance (aerial perspective deepening to 8× the render
  distance), never counts as open sky for bloom, light shafts or the sun halo. Params texels: 13 the ritual, 14 Holo.

---------------------------------------------------------------------------------------------------------------------

## 16. Carillon (the walled city, the opening run, MMO-lite; plan in docs/plan-carillon.html, approved 2026-10-24)

Euan approved the plan on 2026-10-24 with every recommendation ("Go with all recommendations and the full plan"), and
confirmed the movement change in the same message ("ranks improving the moves … everyone a 'Runner' from the first
second … the Wall Run power becomes 'Wall Stride'"). Numbers marked (G0) are provisional until Stage 0 measures them;
the orchestrator updates them here at gate G0.

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
- Climbable language: bronze trim, dark beams and drainpipes can be held; plain plaster and glass can't.
- Pregenerated at world creation (~1,250 chunks, ≤ 30 s on the M4 (G0)) behind the opening card; graphs saved with the
  world; generator changes never touch a built city.
- Terrain (agreed with Awakened Lands, 2026-10-24): `dev.awaken.terrain.api.CityBasin` picks the site deterministically
  from the seed at server start (land within ~1,500 of the origin) and flattens it: `CitySite(x, z, coreRadius 330,
  blendRadius 700, streetY 70, riverPath, basinCarved)`; `CityBasin.site(server)`, `CityBasin.reserved(x, z)` (core + 48,
  allocation-free). It needs Lithostitched (pack world profile); without it `basinCarved` is false and the painter
  flattens its own ground. Gated by the feature flag `awaken:carillon` (Classic start leaves it off; old worlds never
  have it). The river inside the core follows the city's fixed path (offsets from the site centre, north = −z). Terrain
  keeps lairs, ruins, hero trees and biome features out; Carillon wires runtime systems (gate Director, anomalies,
  landmarks, the spawn vault ring, the Well) to `reserved()`. Spawn is placed in the city by Carillon through
  `LevelEvent.CreateSpawnPosition`; terrain never sets spawn.

### 16.3 The Runner's Kit (W2; base numbers, ranks improve them)
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
- Camera: a client-only camera entity through `Minecraft.setCameraEntity` (post effects still come from the player).

### 16.5 Citizens (W4)
- Ledger: 240 records (G0), sliced 12 per tick; ≤ 0.5 ms/tick. Bodies within ~56 blocks: ≤ 40 per player, ≤ 96 per
  world; walk the street graph; brains tick every max(1, d²/256) ticks; ≤ 4 pathfinding at once; ≤ 1.5 ms/tick.
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
- Perf profile, RD 16, busiest market at noon: p50 ≤ 10 ms (platform 5.3 measured + city blocks 1.5 + 40 citizens 2.0 +
  signs of life 0.4 + Holo 0.3 (G0)); rest of the city ≥ 110 fps p50. Horizon-swift: city ≤ +3 ms over the same spot
  without it. Golden run p99 ≤ 20 ms, no frame > 50 ms. Sky shot ≥ 60 fps p99. Server: whole city ≤ 2.5 ms/tick avg;
  tick p95 ≤ 25 ms holds.

### 16.11 Process
- Stages and gates: 0 spikes → G0; 1 foundations in blockout → G1; 2 alive → G2; 3 detail → G3 (art bar); 4 MMO-lite →
  G4; 5 polish → G5 (ship to build/awaken-v1). Cut line: Stage 4 slips before the opening does.
- Branches: carillon/main (integration), carillon/<workstream>, carillon/spike-<name> (never merged). Briefs in
  docs/carillon/briefs/, status in docs/carillon/STATUS.md, spike reports in docs/carillon/spikes/.
- Machine share: Carillon agents run Gradle and tours through `tools/carillon/slot.sh` (≤ 3 builds at once; a tour runs
  alone with no Carillon build beside it).
