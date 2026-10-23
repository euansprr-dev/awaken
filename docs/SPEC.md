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
SHARE            = E .81, D .39, C .25, B .23, A .18, S .12, SOVEREIGN .12
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
- Rank floor-reward shares E .28, D .16, C .08, B .07, A .30, S .25, Sovereign .25; Daily Regimen = 0.1 × xpToNext + 1 point.
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
- Pacing (sim, balanced player): first power ~8 min, level 5 ~47 min, 10 ~3.6 h, 25 ~16.8 h, 50 ~56 h.

Plan v2 · System and Hearth:
- Every Y/N decision is a choice card that says what each key does (`SignalWindow.choice`); information is a toast.
- The Now tracker (`core/api/NowHooks`) collects up to three concrete suggestions from every module.
- The Hearth Anchor is crafted from a Lord's Heart, 4 stone bricks and 4 copper ingots (no obsidian: stone tools suffice).
- Wandering gates (anomalies) form at the player's rank, fade after their lifetime unless entered, never break, and don't
  count toward the Director's 1–4.

Tests: GameTests that spawn mobs use still, fire-proof mobs and let a fresh test area tick a few frames before acting,
so results never depend on AI wandering, daylight or chunk-ticking timing.
