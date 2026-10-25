# AWAKEN audit: enemies, bosses, gear and loot (for the weapons/combat overhaul)

Read-only audit of `/home/user/awaken` at `d12ea34` (2026-10-25). Nothing was edited, built or committed.
All paths are relative to `src/main/java/dev/awaken/` unless they start with `docs/`, `tools/` or `src/`.

**How to read the labels**
- **VERIFIED**: read in the mod's code, data or a passing GameTest (CI green at d12ea34, 461 tests per the merge message).
- **ASSUMED**: vanilla 1.21 behaviour that the 26.3 sources would confirm. The reference sources in CLAUDE.md
  (`/Volumes/MEDIA/Awaken/.ref`) are **not present in this container**, so the vanilla combat formulas (armour, MeleeAttackGoal
  interval, mob equipment attributes, shields) could not be grepped. Each one is flagged where it is used.
- **SUSPECTED**: an inference from code that a GameTest should confirm before anyone builds on it.

---------------------------------------------------------------------------------------------------------------------

## 0. The short version

1. **Bosses are normal mobs ×14 health, ×1.6 damage, plus lv/12 armour. That is the whole of the scaling.** VERIFIED
   (`mobs/MobScaling.java:30-84`, tested by `mobs/MobsTests.java:46-66`). Every Gate Lord's base stats are almost the same:
   28-34 HP and 4-5 damage (`mobs/def/GateBestiary.java:55-327`). Lords have **no base armour and no toughness**: the
   only armour is lv/12 (1.4 at D, 4.75 at B), which takes 1-4% off a sword hit. So Lords are "big health pools", not
   armoured threats.
2. **The damage that kills you is the untelegraphed basic melee, not the telegraphed abilities.** Every ground Lord
   gets vanilla `MeleeAttackGoal` (`mobs/entity/AwakenMob.java:175`). That swing has **no wind-up, no telegraph, no
   life-layer tell**, and fires every 20 ticks (ASSUMED vanilla interval) whenever you are in reach. Fliers dive-swing
   every 30-49 ticks (`mobs/entity/MobGoals.java:113-123`). With a D-rank Colossus the swing is ~74% of the incoming
   damage per second if you stand in melee (computed below). The telegraphed ability system (`AbilityRunner`) is good,
   but it is the minority of the threat.
3. **The repro: a diamond-sword player needs 55-90 s of uninterrupted full-charge swinging to kill a Lord, and dies in
   1-7 s standing in its reach.** D-rank Gearwright Colossus: 103 hits, 61.8 s to kill; the player dies in ~6 s (7
   melee hits) after landing 11% of its health. B-rank Colossus: 130 hits, 65 s; dead in ~6 s after 11%. To finish, the
   player has to avoid about **89%** of the basic melee alone, with no parry, no i-frame dodge and no way to stagger.
   Full arithmetic in section 4.
4. **Three Lords (and more elites/Wardens) hold vanilla weapons whose attack damage is very likely added to their own
   and then multiplied by the level scaling.** SUSPECTED (high confidence: vanilla adds equipment modifiers to any
   LivingEntity, then `ADD_MULTIPLIED_BASE` multiplies base + added). The Ember Tyrant's netherite axe takes it from
   13.4 to **37.6** ATTACK_DAMAGE at D, so its melee deals 19 through full diamond and its Magma Fist 38: a **one-shot**
   on a level-17 player. The Drowned Archivist (trident) and the Mirror Knight (diamond sword) do the same. This is
   probably unintended and should be checked with a 10-line GameTest before any tuning.
5. **Nothing on the player's side answers a boss.** Melee scales only with STR (+2.5% a point above 5); powers scale
   ×(1 + 0.075·level) exactly like mob health (`powers/PowerMath.java:6-8, 39-41`) but are capped at 20% of a boss's
   health per hit (`powers/PowerMath.java:16`, `powers/Combat.java:112`). There is no player parry, no block of our
   own, no dodge with i-frames ("No invincibility frames anywhere", `docs/SPEC.md:623`; "Dash stays the dodge",
   `docs/modules/body.md:19`). Bosses have knockback resistance 1.0, can't be pushed, lifted or dominated, and take a
   third of stuns (`mobs/def/MobDef.java:71`, `mobs/entity/AwakenMob.java:762-765`, `powers/Combat.java:85-91, 272-275`).
   Nothing a player does interrupts a wind-up (`AbilityRunner.interrupt` is called only on phase change, death or a
   fall: `mobs/entity/AbilityRunner.java:58-64, 171-174`, `mobs/entity/AwakenBoss.java:72-75`).
6. **Weapons today: 18 items in the whole mod** (6 Hunter blades, 6 foci, 6 mattocks), all flat vanilla-twin stat
   sticks with 2D handheld sprites, no abilities, never dropped (only crafted). There are **no 3D item models anywhere**
   (no `elements` or special renderers in any of 393 item models). Mobs never drop the weapons they hold
   (`mobs/entity/AwakenMob.java:132`).
7. **The pacing simulator never models a fight.** `tools/sim/pacing_sim.py:193-197, 238` assume a Lord costs a balanced
   on-level player a 6% death chance (×1.3 per floor deeper) and adds 4 minutes ("ASSUMED (a combat model, not game
   code)"). The DPS race above is invisible to it.
8. **Strong foundations to keep**: the telegraph state machine (wind-up, marked ground, last-6-ticks aim lock, heavy
   tells on the action bar), boss phases with an invulnerable roar and part breaks, the light-pillar rare-drop look, the
   Power Core pattern (one item + a data component + an item-model select), the Codex/Almanac "N / ??" discovery
   pattern, the Forge's temper component, and the Ritual of the Bell's rule-based, readable fight.

---------------------------------------------------------------------------------------------------------------------

## 1. Component map

Format: **Component** → what it actually does → evidence → consequence for a combat overhaul → **verdict**.

### 1.1 mobs: the creature base and the telegraph system

| Component | What it actually does | Evidence | Consequence for the overhaul | Verdict |
|---|---|---|---|---|
| `MobScaling` (SPEC §2 numbers) | Max health ×(1+0.075·lv)·role·tier-extra, ATTACK_DAMAGE ×(1+0.04·lv)·role, armour +lv/12, as **permanent** ADD_MULTIPLIED_BASE / ADD_VALUE modifiers. Roles: elite 2.5, Warden 5, break 8 (dmg 1.3), boss 14 (dmg 1.6), nightmare 6 (dmg 1.25). Tier extra health: guardian 1.15, Sovereign 1.5, final 2.0. Ascension adds +30% health per NG+ tier. | `mobs/MobScaling.java:30-84`; `mobs/def/Tier.java:8-20`; `core/api/MobHooks.java:61-110`; test `mobs/MobsTests.java:46-66` (lv5 ×1.375, lv150 ×7.0, lv57 armour 4.75; elite toll ghoul at lv35 = 22·3.625·2.5) | SPEC §2's formula is implemented exactly. The boss ×14 is what makes a Lord "14 normal mobs in one body"; the ×1.6 makes it hit like a C-rank normal at D. Any weapon tier plan must target this multiplier, not the per-level slope. | **keep** the slope, **investigate** the ×14 / ×1.6 once players have parry, dodge and weapon skills |
| Base stats per creature (`MobDef`) | `hp` default 20, `dmg` 3, armour 0. Every Gate Lord is 28-34 HP, 4-5 dmg, **no armour, no toughness**. Bosses: knockback resistance forced to 1.0, Wardens 0.8; follow range 40. | `mobs/def/MobDef.java:71-72, 134`; `mobs/def/GateBestiary.java:55,75,96,117,136,155,174,193,211,232,251,270,289,308,327`; attributes `mobs/MobTypes.java:122-134` | Lords differ by moves, not stats. A weapon "armour-pierce" or "shred" stat has nothing to bite on today (1.4-4.75 armour). | **extend**: give Lords real armour/toughness or weak points if weapons are to differ |
| Held weapons and armour on mobs (`.hold(...)`, `.wear(...)`) | Set into equipment slots at `finalizeSpawn`; drop chance 0. Vanilla adds the item's attribute modifiers to the mob's ATTACK_DAMAGE / ARMOR; our ADD_MULTIPLIED_BASE scaling then multiplies base + item. Lords: Ember Tyrant netherite axe (+9) + golden helmet (+2 armour); Drowned Archivist trident (+8); Mirror Knight diamond sword (+6) + diamond chest and helm (+11 armour, +4 toughness). Also Shadow Regent (netherite sword +7), Ossuary King (stone sword +3), Sovereign of Mirrors (diamond sword +6), the Overseer Warden (netherite axe), elites with iron/golden/stone swords and an iron axe. | `mobs/entity/AwakenMob.java:132, 202`; `GateBestiary.java:96, 117, 251, 94`; `mobs/def/DeepBestiary.java:133, 158, 195` | **SUSPECTED** 2.2-2.8× the bestiary's damage for those Lords (see 4.3). It also means abilities (damage = ATTACK_DAMAGE × power) inherit the weapon. Probably cosmetic intent. | **investigate** first (GameTest: spawn `ember_tyrant` at lv17, read ATTACK_DAMAGE; designed 13.44, suspected 37.63) |
| Basic melee | Ground non-ranged creatures (all ground Lords) run vanilla `MeleeAttackGoal(this, 1.0, false)`: walk in, swing on contact via vanilla `doHurtTarget` (ATTACK_DAMAGE, `mob_attack` damage type, scales with world difficulty). Fliers use `FlyHunt`: hover, dive, `doHurtTarget` at reach (w+w)/2+1.3, then 30-49 ticks cooldown. No telegraph, no sound cue, no glow, no life-layer tension. Trait riders: IGNITE 4 s, poison, slowness, wither; FROSTED slows and freezes; CHARGED adds 25% Charge damage **with i-frames reset**. | `mobs/entity/AwakenMob.java:170-179, 526-551` (i-frame reset :544); `mobs/entity/MobGoals.java:93-125`; interval 20 ticks ASSUMED (vanilla 1.21 `MeleeAttackGoal`) | The unreadable attack is the dominant damage (section 4). This is the first thing a parry/dodge system must target: give the swing a wind-up (or replace it with a short telegraphed "jab" ability) so it can be read, parried and dodged. | **replace** for bosses (telegraphed melee), **keep** for normals |
| `Ability` (25 kinds) | Record: kind, name, windup, cooldown, range, power, radius, count, bolt, effect, summon, inner, roots, heavy. Defaults per kind, e.g. SLAM wind-up 22 / cd 110 / ×1.5 / r3.5; BURST 40 / 260 / ×2.2 / r12 safe <3.5; BEAM 30 / 140 / ×1.6; CHARGE 18 / 120 / ×1.4; BOLT 14 / 50. Rooted unless BOLT, ARROWS, HEAL, FRENZY or `.moving()`. | `mobs/def/Ability.java:12-66 (kinds), 76-111 (defaults), 114-133 (builder)` | A clean data shape: a weapon-ability system for players could reuse Kind + windup + power + radius + bolt verbatim (the executors already exist and already scale off ATTACK_DAMAGE). | **keep, extend** (add `parryable`, `unblockable`, `stagger` flags) |
| `AbilityRunner` (the telegraph state machine) | Polls every 4 ticks for a ready ability that fits range and line of sight. Wind-up = `windup` (+8 ticks if heavy). During it: holds still if rooted, re-aims every tick **until 6 ticks before the strike** (then the aim is locked), draws the landing zone every 3 ticks (rings, lines, cone, rain marks, blink sparks), syncs the countdown to clients, plays `mobs.telegraph`/`telegraph_heavy`, and prints a heavy ability's name on the action bar to players within 24 blocks. After it: cooldown = cd + 0-19 ticks, then a global gap of **14 ticks for bosses** (28 otherwise). A phase change re-staggers cooldowns 20 + 0-39 + 15·i ticks. | `mobs/entity/AbilityRunner.java:49-56, 74-103, 121-149 (begin, heavy +8 at :124, action bar :144-148), 168-194 (lock at :176, gap at :192), 196-225 (shapes)` | This already *is* a readable-attack system: a dodge window exists (the last 6 ticks the aim is fixed). It is what a parry window would key off. But nothing a player does can interrupt it, and the tells are 0.7-2.9 s long with no "perfect window" concept. | **keep and extend** (parry window = last N ticks of a flagged melee ability; stagger = `interrupt()` on a poise break) |
| `Abilities` (executors) | Damage = ATTACK_DAMAGE × power. Area: slam/burst/leap ring (+knockback 0.9 / 0.6 / 0.7), cone (dot > 0.7), beam (block-clipped ray), charge (14 ticks at 0.85 b/t, each target once), bolts (`AwBolt`, 0.9 b/t, 5 s life), nova (0.6 b/t), rain (marks under players first), fangs (vanilla evoker fangs 6 flat + a top-up strike), pull ×0.4, gust (big knockback), root ×0.5 + Slowness V 2 s, lift ×0.3 + Levitation, aura pulses every 20 ticks, heal (allies by power × max HP), guard, decoys, summons (budgeted). **Status riders and knockback are applied even if the damage didn't land** (shield, i-frames, Foresight). | `mobs/entity/Abilities.java:71-80 (riders after hurt), 91-93, 109-118, 170-191, 210-251 (fangs top-up :248-250), 253-273, 340-405` | Effects ignore blocks today: a parry/block design must decide whether it also cancels the rider. Evoker fangs (vanilla `indirect_magic`) very likely bypass armour and shields (ASSUMED vanilla tags), so Root Lance / Root Ring / Shifting Floor are the only de-facto "unblockable" attacks, by accident. | **extend** (route riders through the same "did it land" result) |
| Damage types of mob attacks | Force bolts use `awaken:<force>` damage types (`heat`, `frost`, `spirit`, `charge`, `gravity`; scaling "never"); everything else uses vanilla `mob_attack` / `mob_projectile`. **No damage-type tags exist in the mod** (no bypasses_armor, bypasses_shield, bypasses_cooldown, no_knockback). | `mobs/entity/Abilities.java:64-69`; `mobs/def/BoltKind.java:15-28`; `src/main/resources/data/awaken/damage_type/*.json`; no `tags/damage_type` folder anywhere in `src/` | Armour, i-frames and (ASSUMED) the vanilla shield apply to every boss attack. A designed "unblockable" or "parry-only" attack needs a new tag. Note world difficulty (Hard ×1.5) scales `mob_attack` but not Force damage. | **extend** (`#awaken:unparryable`, `#awaken:unblockable`) |
| Boss defences | Phase change: 30 ticks invulnerable ("roar") + telegraph flash. HAUNTED (the Sleeper): ×0.75 from non-Spirit, phased (immune) 30 of every 140 ticks. GUARD: 60 ticks, next direct melee ×0.2 and a riposte `doHurtTarget` within 4 blocks. REFLECT: 60% of projectiles turned back. Decoys pop on any hit. RISES (Ossuary King, Hollow Crowd): falls at 0 HP, rises at 50%. | `mobs/entity/AwakenBoss.java:50-58, 84-94, 141-148`; `mobs/entity/AwakenMob.java:431-438, 490-523, 582-598, 632-639` | The *enemy* parry already exists (GUARD/riposte on the Mirror Knight, Masked Duelist, Silver Knight, Shadow Regent, Sovereign of Mirrors). It is the template for a *player* parry; and these fights become "wait out the guard" for a sword player today. | **keep** (mirror it for the player) |
| Life layer `windup` | Client-only. `lf.windup = telegraph > 0 ? 1 − min(1, ticksLeft/20) : -1`: set by `AwMobRenderer` from the synced telegraph countdown of the **ability** being wound up; it ramps 0→1 over the last second. `Life` turns it into body tension (want = 0.35 + 0.65·windup), a 0.7 px gather and a 17 Hz tremble (tension²). Every Lord gets at least a root-level rig (`LifeRig.rootOnly` for bosses on vanilla bodies). Named beasts (hero rigs) also play authored `<move>_windup` / `_strike` clips; **Gate Lords do not** (they are vanilla bodies or our five hand-built models). | `mobs/client/AwMobRenderer.java:55, 86-99`; `mobs/client/life/Life.java:26-27, 206-209, 237-242`; `mobs/client/AwRenderers.java:285-290`; SPEC `docs/SPEC.md:3139-3146` | The visual tell exists for abilities only. The basic melee never sets the telegraph, so it never trembles. For parry/dodge, every boss melee needs (a) a synced wind-up and (b) authored windup/strike clips like the named beasts have. | **extend** |
| Boss arena logic | Home = spawn point, radius per boss (18-30); kited beyond radius+10 → teleports home; drops a target beyond radius+14; nobody within radius+16 for 600 ticks → full heal, phase 0 (no drops). | `mobs/entity/AwakenBoss.java:102-139` | No "chip across lives": a death resets the fight, and the retry carries an 8-minute Fracture (−15% melee). Kiting is bounded. | **keep** |
| Boss bar, music, voice | `ServerBossEvent` (notched 6 for Lords/guardians, 10 for Sovereigns/final), colour per phase (red, purple, white), shown only within 24 blocks of height. Sounds: `boss_intro` on first target, `boss_phase` on each phase, `boss_toll` on every burst, `boss_defeated`. Music: the Lord layer is on while a boss within 40 blocks hunts you, you are within 20, or you traded blows in the last 15 s; a Lord's death ends the Lord and combat layers in 3 s. **No phase-aware music.** | `mobs/entity/AwakenMob.java:690-728`; `mobs/def/Tier.java:45-51`; `mobs/entity/AwakenBoss.java:82-87, 122-126`; `mobs/entity/AbilityRunner.java:236`; `mobs/MobLoot.java:182`; `core/audio/Intensity.java:7-30`; `docs/modules/audio.md:87-102` | Hooks exist for "parry landed", "stagger" and "phase 3" stingers but none fire today. | **extend** |

### 1.2 Gate Lords, guardians, the Hollow Court (summary; the full table is section 3)

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| 15 Gate Lords | 3 phases each (100/66/33%), 2-4 abilities per phase, 1 intro line, 2 parts. 10 ground Lords melee every 20 ticks; 5 fly (Mother of Spores, Glass Serpent, Hive Regent, Sky Leviathan, Hollow Herald) and are reachable by a sword only when they dive. | `mobs/def/GateBestiary.java:55-333`; fliers by model `mobs/def/Model.java:54-67`; doc `docs/modules/gates.md:219-237` | A sword can't reach a third of the Lords most of the time: weapon design needs reach, ranged or "pull it down" answers. | **extend** |
| Where Lords are met | A gate rolls rank 70% yours / 20% below / 10% above; theme = any whose **minimum** rank ≤ gate rank; boss = theme's Lord at the gate's content level (RANK_MID). So the E-rank Bell Warden can appear at B (2,215 HP). | `gates/Director.java:51-82, 133-136`; `gates/content/Theme.java:18-33, 71-73`; `gates/run/Rooms.java:110-115` | Every Lord must stay fightable at every rank above its first: weapon tiers cannot be tuned per Lord. | **keep** |
| Party scaling | Each extra player on the floor: Lord +50% health (ADD_MULTIPLIED_TOTAL); its hoard is per player. | `gates/run/Rooms.java:63, 138-144` | | **keep** |
| First-gate tuning | Scripted gates soften a Lord to −55% health and −25% damage: "a Lord you can beat with iron and one power (about 190 health)". But the story now opens the one-floor **Ritual** (no Lord), so the softening only applies to old/multi-floor scripted Bell Crypts. | `gates/run/Rooms.java:146-154`; `gates/GateAccess.java:88-93`; `story/Prologue.java:133` | **SUSPECTED**: the first real Lord a player meets is a Director-rolled E gate's at full scaling, 539-578 HP, i.e. ~3× the stated "beatable with iron" target. | **investigate** |
| Stratum guardians (10) | 2 phases (100/50%), spawned at the stratum's content level (strata 1-2 D, 3-4 C, 5-6 B, 7-8 A, 9-10 S). | `mobs/def/DeepBestiary.java:118-163`; `descent/spawn/DescentSpawner.java:113-124`; `descent/Strata.java:205-208` | | **keep** |
| Sovereigns (8) + the Signal's Shadow | Sovereigns ×1.5 extra health at lv 118 (6,619-7,447 HP), met one at a time in stratum 9 by S-rank players; the final boss ×2 extra at lv 118 (11,032 HP). | `mobs/def/DeepBestiary.java:171-219`; `story/Court.java:95-131`; `descent/Descent.java:54` | At S a sword needs ~150-250 hits per Sovereign. | **investigate** |
| The Ritual of the Bell (prologue set piece) | A rule fight: kneel when the word appears, stand on the lit plate, the gaze fills for 45 ticks along a marked line, statues swing with a 25-tick wind-up / 22 recovery / every 45, pits warn 30 ticks. Every hit leaves at least half a heart. | `story/Ritual.java:79-101, 196-197`; `mobs/entity/SeatedOne.java:1-40` | The mod's best example of a readable, dodge-able fight; the cadence (25 tell / 22 recover) is a ready-made baseline for telegraphed boss melee. | **keep**, use as the pattern |

### 1.3 gates: boss rooms, the run, endings, rewards, Satchel, death

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Boss room | Last floor: start → hall → antechamber → round 3×3-slot arena (radius 22, 17-26 high), sealed with Rune Bars while the Lord lives. Lord spawned with `MobHooks.spawnBoss(level, lordId, pos, contentLevel, gateKey)` when a player enters. | `docs/modules/gates.md:111, 211-237`; `gates/run/Rooms.java:104-136, 407-416` | Arenas are flat, round, fully lit: good for dodge/parry readability; no cover or terrain mechanics. | **keep** |
| Pressure on the Lord's floor | After 4 minutes on a floor, a wave every 45 s; the 8-minute Warden **never comes on the boss floor**, but the waves do. | `gates/run/Pressure.java:11-35, 94` | **SUSPECTED**: a slow sword fight (section 4: you must dodge ~90% of the time) easily passes 4 minutes on that floor, and waves join the Lord. | **investigate** |
| How the fight ends | Lord death → `RunEvents` → `Runs.lordDefeated` → for every player on the floor `claimLord`: 60% of the floor reward as Satchel XP, the hoard (`Loot.boss`) into the Satchel, bank the Satchel as a claim, counters, Signal window "Gate cleared · Claimed: the Satchel and the hoard", `gates.cleared` sound; then the exit portal (and the Double Gate rift). | `gates/run/RunEvents.java:86-87`; `gates/run/Runs.java:437-510`; `gates/run/Twists.java:553-560` | One clean "victory" hook to hang a weapon reveal on. | **keep, extend** |
| The hoard | Lord's Heart, essence 6+2·rank+0..2, 3+rank/2 rolls of the rank table (iron, gold, diamonds from C, netherite scrap from A), theme items, **Power Core 50%** (guaranteed if none in the last 3 Lords, per-player `GateMemory.bossesSinceCore`). Fortune doubles rolls. | `gates/run/Loot.java:21-26, 89-99`; `gates/run/Runs.java:472-476` | **No equipment in any gate table.** A weapon drop needs a new table entry and a reveal. | **extend** |
| Two Power Core rolls and two Hearts per Lord | The mobs module also drops a Lord's Heart and rolls a Power Core at 40% with its *own* pity (`BossRecord`, last 3 bosses) on every boss kill, gate or not. | `mobs/MobLoot.java:123-143`; `mobs/entity/AwakenBoss.java:156-160` | **SUSPECTED overlap**: in a gate the killer gets 2 Hearts and a ~70% core chance (1 − 0.6·0.5), with two independent pity timers. Matters for weapon-drop budgeting. | **investigate** |
| Satchel | 27 slots + Satchel XP. **Every pickup inside a gate goes into it** (except what you threw), and the Satchel screen is **view-only**. Banked on Shrine extraction or Lord kill; claims delivered outside. | `gates/run/Satchel.java:13`; `gates/run/Runs.java:692-711`; `gates/client/SatchelScreen.java:14-60` | **VERIFIED and important**: a weapon found on floor 1 cannot be wielded against the Lord on floor 4. A weapon system must decide (equip-on-pickup for weapons, a "draw one" slot, or drops only on the Lord). | **extend** (rule change, SPEC decision) |
| Death in a gate | Story: keep half the Satchel stacks and half the Satchel XP, no Fracture. Hunter/Hardline/Hardcore: lose the Satchel, **Fracture 8 min** (−15% melee damage, −10% speed). Gear and vanilla XP are stashed and returned. Insight +1 for the Lord on its floor. Cheat-death perk once per run (40% HP, Resistance IV 3 s). AWAKEN difficulty changes only these loss rules, never combat numbers. | `gates/run/Runs.java:513-570`; `progression/Progression.java:291-294`; `gates/run/Runs.java:62` | Retrying a hard Lord right away means doing it at −15%. | **keep** (maybe waive Fracture on the Lord floor) |
| Insight | +1 per death to a boss; its only effect is a line on the Lord's warning ("You know its tells now"). It is counted **twice** for a gate-Lord death (mobs `onDeath` and gates `onDeath`). | `mobs/MobsEvents.java:39-45`; `gates/run/Runs.java:555-558`; `gates/run/Twists.java:544-545`; `progression/Progression.java:689-690` | A free hook for "learn the tells": e.g. Insight widens your parry window against that Lord (allowed by the patent rule, it changes the player, not the NPC). Double count is a small bug (SUSPECTED). | **extend** |
| Double Gate law | In the annex, hits that break the rolled law (strike while still / in the light / from behind) are cancelled with a shield-block sound. | `gates/run/Twists.java:476-490` | A working "conditional hit" system weapons could reuse (e.g. backstab weapons). | **keep** |

### 1.4 gear: what Hunter gear really is

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| `HunterTier` E-S | Per tier: 4 armour pieces, a **blade** (vanilla `.sword(...)`, +4/5/6/7/8/9 attack = 5/6/7/8/9/10 with the player's 1; speed −2.2/−2.3 → 1.8/1.7 per second), a **focus** (club 3-8 melee, +6/9/12/15/18/22% power damage in either hand), a **mattock** (pick+axe+shovel). Armour 10/15/19/20/21/24, toughness per piece 0/0.5/1.5/2.5/3/3.5, kb 0/0/0/0.05/0.1/0.15 per piece. | `gear/HunterTier.java:31-44`; `gear/GearItems.java:66-106`; anchors `gear/GearTests.java:283-302` | Blades are vanilla swords with +0.1/s speed; the C blade equals the diamond sword exactly (test-enforced). Nothing about a blade differs but the number. | **replace** the blade concept with the weapon system; **keep** armour and foci |
| `SetBonus` | 2/4-piece bonuses: sneak speed + Hunter's Sense (E); +1/+2 hearts, Cornered +15% melee (+1% per 2 STR over 5, max 30%) below half HP (D); Force ward 15/25% + Force-primed strike +35% on the next melee within 4 s after a power hit (C); Spirit ward 30% + Veil Flicker (B: below 40% HP a hit can pass through, 10% + 1%/SPI, max 35%, once per 8 s); Gravity/Charge ward 20% + Overflow (A); Unbowed + Sovereign's Measure (S). Refreshed every 10 ticks. | `gear/SetBonus.java:45-65, 114, 194-197`; `gear/GearCombat.java:49-120, 231-251` | Gatewrought's primed strike is the only existing "magic → melee" fusion; Veil Flicker is the only existing i-frame-like dodge (random, not skill). | **keep** and **extend** (primed strike is the seed of weapon/power fusion) |
| `GearCombat` | One `LivingIncomingDamageEvent` listener at LOW: focus bonus on power hits (still capped at 20% of a boss's health), primed strike, Overflow bursts, wards, Flicker. | `gear/GearCombat.java:31-120, 224-251` | Shows the house pattern for weapon effects: one listener, attachments for state (`GearState`), no mixins. | **keep** (pattern) |
| `Silhouette` / `Silhouettes` | A and S armour (helm, chest, boots) get extra cubes from `assets/awaken/gear_silhouettes/<set>.json` baked onto vanilla's armour mesh at 128×64, via `IClientItemExtensions`; falls back to the plain model. | `gear/Silhouette.java`, `gear/client/Silhouettes.java`; `docs/modules/gear.md` "Silhouettes" | The only custom 3D geometry on items in the mod; a working, data-driven pipeline that could carry 3D weapon meshes. | **extend** (to weapons) |
| Item models | Every blade/focus/mattock is `minecraft:item/handheld` with a 16 px layer and an emissive `_glow` layer (light 15). Of 393 item models, **none** has `elements`, and no item uses a special renderer. | `src/main/resources/assets/awaken/models/item/gatewrought_blade.json`; `assets/awaken/items/*.json` | "Hundreds of distinct 3D weapons" is entirely new pipeline work (the art pipeline audit covers it). | **replace** for weapons |
| Tempering (hearth) | +1 attack damage per temper level on anything with attack damage (armour +0.5 armour, +0.25 toughness), max VI; one level anywhere, two per Forge tier in an Armory; Oren's Mark starts a fresh blade a level up. | `hearth/Upgrades.java:39, 212-240, 287-296`; `living/quest/Relics.java:62` | A per-item upgrade track already stored as a data component (`awaken:temper`). A weapon "level" or "awakening" could reuse it. | **keep, extend** |

### 1.5 living, hearth, progression, lens

| Component | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Creature levels by region | Vanilla monsters take the region's level (+1 as hunters; 5% elites +2 and ×2.5 HP, +12% size), SPEC §2 scaling as permanent modifiers; their projectiles are multiplied to match; Deep Tide +2 at new moon; Deep Dark bonus; a Hearth caps its land to the household's level. | `living/combat/Creatures.java:42-100`; `living/api/Regions.java:59-67`; `living/LivingModule.java:44` | The open world uses the same curve, so a weapon tier that works in gates works outside. | **keep** |
| Kill XP | Per kill: 3% of a level × role (elite 4, Warden/boss 6, camp 8, beast 0), ±40% by level difference, through the hunt budget. | `living/combat/KillXp.java:14-68` | | **keep** |
| Hit glyphs | Server sends each landed hit to the attacker (damage, crit, Force) and each hit taken to the victim, for the Holo damage numbers; "in a fight" = 100 ticks. | `living/combat/Hits.java:30-69` | Ready channel for "PARRY", "STAGGER", "WEAK POINT" glyphs. | **extend** |
| Loot pillars | Drops from player kills get a light pillar: Power Core white-gold `#FFF0C0`, EPIC violet `#B48CFF`, RARE blue `#6FB3FF` (beam stacks to 8 blocks, pulsing ground ring). The `#awaken:loot_pillar` tag has **no file** (empty). Lord parts are UNCOMMON → **no pillar**; the Lord's Heart (RARE) and cores get one. The gate hoard goes straight into the Satchel → **no pillar, no moment**. | `living/combat/LootPillars.java:16-40`; `living/client/LivingFx.java:626-639`; `mobs/MobTypes.java:92`; `core/api/CoreApi.java:33` | The rare-drop *look* exists; a weapon rarity ladder only needs more tiers/colours and a reveal for Satchel-bound loot. | **keep, extend** |
| Relics | Quest-chain rewards, bound, kept through death; passive perks while carried (night ward, rope reach, shop discount, mount sprint, sprint recharge, temper start); two usables. **None is a weapon or a combat perk.** | `living/quest/Relics.java:47-67` | | **keep** |
| Crafts (SPEC §21.9) | Hunting: Weak Spot (crits +15% vs kinds you've killed 20 of), Stalker (sneak attack ×1.5 on an unaware creature), Apex (+10% vs elites and named beasts, **not** bosses). Smithing: durability, Unbreaking I 10%, **Keen: Sharpness I a fifth of the time** on crafted weapons, Signature (name on items). | `living/craft/Craft.java:10-48`; `living/craft/CraftEvents.java:89-107, 234` | Smithing is a natural home for weapon crafting/reforging; Signature already puts a name on items. | **extend** |
| Training Grounds | Start Gate, Checkpoint Rings, Finish Gate, **Target Dummies** (blocks that count as hit in a timed lap). Ghost of your best lap. No enemy that attacks, no damage readout. | `hearth/training/Training.java:37-60, 115, 248-251`; `docs/modules/hearth.md:185-189` | No place to practise parry/dodge. A "sparring Lord echo" would fit here. | **extend** |
| Rank trials | D 3 waves, C waves + Path, **B: duel a Gate Lord alone at lv 45, 5-minute limit**, A 5 rooms in 4 min, S a Sovereign of Mirrors echo at −50% HP. Trials never kill (death → 50% HP, attempt ends). One attempt per play-day. The B Lord is picked from the player's UUID. | `hearth/trial/Trials.java:69-109, 344-371, 579-590, 641-662` | The B duel is the cleanest "can a player beat a Lord with a weapon" test bed in the game: no Satchel, no death cost. | **keep** (and use as a balancing harness) |
| Trophy Hall | +1% melee damage and +0.5 max HP per distinct boss trophy, cap 20. | `hearth/trophy/Trophies.java:42, 97-107` | | **keep** |
| Stats (SPEC §3) | STR: ATTACK_DAMAGE ADD_MULTIPLIED_BASE +0.025 per point **above 5** (Fracture −0.15, Strain −x). AGI: speed +0.4%/pt (cap 40%), attack speed +1%/pt (cap 60%). VIT: +0.6 HP/pt, resistance 0.5%/pt (cap 35%, applied before armour). PER: crit 0.4%/pt (cap 35%), ×1.6, only rolled when vanilla didn't already crit. +3 points per level. | `progression/Progression.java:285-302`; `progression/ProgressionModule.java:320-352`; `progression/Curve.java:10` | Matches SPEC §3 except that every stat counts points above the starting 5 (SPEC says "per point"). The AGI cap is partly wasted: against one target the Lord's 10-tick i-frames stop more than 2 full hits a second, so attack speed above 2.0/s (AGI +25 on a sword) does nothing on a boss (ASSUMED vanilla i-frame rule, `docs/api/entities.md:338`). | **keep**, note the i-frame ceiling |
| Combat perks from Truths / Titles / Latents / Paths | `crit_chance` +0.02 to +0.05 (Centurion, Star Hunter, Thousand Hands, Sun Eyes, I Strike First, When the Sky Falls); `damage_above_half_hp` +0.05 to +0.25 (Barehanded, I Never Flee, I Do Not Retreat, Stormbreaker, Monk); `max_health` (Warden +10, I Endure +4, Hollow −4); `cheat_death_per_gate` (Nothing Can Touch Me); explosion resist. | `progression/Perks.java:7-28`; `src/main/resources/data/awaken/awaken/{title,truth,latent,path}/*.json` | Small percentage perks; nothing changes *how* you fight. | **extend** (weapon-style Titles) |
| Powers vs bosses | Power damage ×(1+0.075·level) ×(1+1.5%/stat pt) ×(1+12%/power level); **any one power hit ≤ 20% of a boss's max health**; bosses can't be lifted, thrown or dominated (`isHeavy`), stuns last a third; Stunned is −100% move, −60% attack but **doesn't stop a wind-up** (AbilityRunner never checks it, and charges set velocity directly). | `powers/PowerMath.java:6-8, 16, 39-58`; `powers/Combat.java:85-91, 102-120, 272-276`; `powers/content/PowerContent.java:77-81` | Melee doesn't scale with level at all; powers do. Fusion should probably give weapons the power curve or let weapons channel it. | (player side, other agent) |
| lens | Only re-implements vanilla's hurt tilt and view bob in its own projection; nothing combat-specific. | `lens/client/LensFrame.java:66-68, 139-151` | No hit flash, no damage tint, no slow-mo hook. | **n/a** |
| Hit-stop / shake | `Fx.feel(player, hit, ms, shake)` exists for powers (hit-stop 40-80 ms and a camera kick); melee doesn't call it. | `powers/Fx.java:222-231`; `docs/SPEC.md:192` | A parry/perfect-dodge "feel" can reuse it directly. | **extend** |

---------------------------------------------------------------------------------------------------------------------

## 2. Tools the player has today to avoid damage (VERIFIED unless noted)

| Tool | What it does | Evidence | I-frames? |
|---|---|---|---|
| Vanilla shield | Not touched by the mod. In vanilla 1.21.5+ it blocks all frontal damage that isn't `#bypasses_shield`; our damage types have no tags, and area attacks carry the Lord's position, so a raised shield should block slams, bursts, beams and even rain marks if you face the Lord. Axes (the Ember Tyrant's) disable it for 5 s. Status riders still apply (`Abilities.java:76-79`). | no shield code in the mod (grep); ASSUMED vanilla | n/a (SUSPECTED: the strongest defence in the game is an unchanged vanilla shield) |
| Dash (Velocity, E, movement slot) | 10 energy, 14-tick cooldown, a launch of ~1.9 b/t (Blink variant: teleport 6 + 0.5·level blocks; Double: shorter, cheaper). | `powers/catalog/VelocityPowers.java:45-51, 96-125` | **No** ("Dash stays the dodge", `docs/modules/body.md:19`) |
| Slide (body, D) | 14 ticks low and fast (~4.6 blocks), crawl-height box, trips small hostiles (not bosses). | `body/BodyRules.java:75-85`; `docs/modules/body.md:13, 16-19` | **No** (`docs/SPEC.md:623` "No invincibility frames anywhere") |
| Roll (body) | Only a landing roll for fall damage (safe fall +3, ×0.6 beyond). | `body/BodyRules.java:27-32`; `docs/modules/body.md:11` | No |
| Momentum hits | A full swing while sprinting/sliding: up to +30% damage and more knockback (needs Roll unlocked). Offence, not defence. | `body/BodyServer.java:364-400`; `body/BodyRules.java:99-103` | – |
| Foresight (Mind, C, hidden) | 3 s (5 s Long): the world at a tenth speed and **the first blow that reaches you misses** (cancelled, side-step). 35 energy, 600-tick cooldown. | `powers/catalog/MindPowers.java:75-79, 226-250`; `powers/PowerEvents.java:120-125` | the nearest thing to a perfect dodge, one hit per 30 s |
| Brace (Kinetic, D) | 3 s: −60% damage (Iron −75%), no knockback, reflect 50% to melee attackers. 240-tick cooldown. | `powers/catalog/KineticPowers.java:84-89, 309-329`; `powers/PowerEvents.java:128-131, 135-146` | No |
| Barrier (Energy, E), Bulwark (Spirit, C), Read Intent (Mind) | Absorption 6 + INT/4 for 10 s; damage −50% in a zone; −15% from mobs and +15% to enemies. | `powers/catalog/EnergyPowers.java:67-68, 201`; `powers/catalog/SpiritPowers.java:99-100, 325`; `powers/catalog/MindPowers.java:61, 190`; `powers/PowerEvents.java:126-133, 165` | No |
| Flow (Velocity, E) | The world at a quarter speed for 4 s + 0.05 s/AGI. 400-tick cooldown. | `powers/catalog/VelocityPowers.java:53-58` | No |
| Veil Flicker (Veilwoven 4 pc) | Below 40% HP a hit can pass through: 10% + 1%/SPI over 5 (max 35%), once per 8 s. | `gear/GearCombat.java:231-243`; `gear/SetBonus.java:57-58` | random, not skill |
| Frame Shield Emitter (Above) | An 8-point absorption shield regrown every 20 s (40 essence a charge). | `docs/modules/above.md:88` | No |
| Reading the telegraphs | Rings/lines/cones/marks, the heavy name on the action bar, the last-6-ticks aim lock on beams, bolts and charges, the safe centre of bursts. | `mobs/entity/AbilityRunner.java:124, 144-148, 176-225`; `mobs/def/Ability.java:20` (BURST "step inside to dodge") | – |

There is **no player parry, no block of our own, and no input that turns a well-timed reaction into a reward**. The
only "perfect" defence (Foresight) is a 30-second cooldown power behind a hidden C-rank Path.

---------------------------------------------------------------------------------------------------------------------

## 3. Every boss: stats, attacks and timings

Generated from the bestiary source by a parser over `mobs/def/GateBestiary.java` and `mobs/def/DeepBestiary.java`
with the builder defaults of `mobs/def/Ability.java:76-111` (scratchpad `bosses.py`, `table.py`; the parse matches the
docs' ability lists one for one).

How to read it:
- **met at**: Gate Lords at their theme's *minimum* gate rank (`gates/content/Theme.java:18-33`; they also appear at
  every rank above), guardians at their stratum's level (`descent/Strata.java:205-208`), Sovereigns and the final boss
  at lv 118 (`story/Court.java:128`, `descent/Descent.java:54`), nightmares at an assumed lv 17.
- **HP there** = base × (1 + 0.075·lv) × 14 × tier extra (guardian 1.15, Sovereign 1.5, final 2.0); nightmares ×6.
- **ATTACK_DAMAGE there** = (base + held weapon) × (1 + 0.04·lv) × 1.6 (nightmares ×1.25). The held-weapon part is
  **SUSPECTED** (section 1.1); without it the Ember Tyrant is 9.6 at lv 5, the Archivist 12.1 at lv 17, the Mirror
  Knight 19.2 at lv 35, the Shadow Regent 62.9 and the Sovereign of Mirrors 50.3 at lv 118.
- **armour** = lv/12 + worn vanilla armour (Mirror Knight diamond chest + helm 11 and 4 toughness; Ember Tyrant golden
  helm 2). No Lord has base armour or toughness.
- Abilities: `name (kind ×damage factor·count, rN radius, safe<inner, tell = wind-up ticks incl. +8 for heavy, cd =
  cooldown ticks before the +0..19 random)`. Damage factor = ability power × the executor's share (pull ×0.4, root
  ×0.5, lift ×0.3, cloud ×0.2 a pulse, arrows ×0.42). Nova/bolt/rain/fang counts are separate projectiles or marks;
  the player's 10-tick i-frames (ASSUMED vanilla) make more than one of a volley rarely land in full.

Common to every boss (VERIFIED):
- **Basic melee** on top of the abilities: ground bodies every 20 ticks in reach (vanilla `MeleeAttackGoal`, interval
  ASSUMED), fliers a dive every 30-49 ticks (`mobs/entity/MobGoals.java:113-123`). Damage = ATTACK_DAMAGE ×1, **no
  telegraph**. During a *rooted* wind-up the `Hold` goal owns MOVE and LOOK (`MobGoals.java:25-48`), so a rooted boss
  doesn't melee while it winds up; during BOLT/ARROWS/HEAL/FRENZY (not rooted) it can keep swinging.
- Abilities are chosen every 4 ticks at random among those off cooldown and in range (SLAM only within its radius
  + 0.5, CONE within its radius, FANGS_RING and GUARD within 5 blocks, BOLT/ARROWS/BEAM/CHARGE need line of sight)
  (`mobs/entity/AbilityRunner.java:81-119`). A boss waits 14 ticks after any ability (`:192`).
- Aim tracks until 6 ticks before the strike, then locks (`:176`): the dodge window for beams, bolts, charges, cones,
  fangs and leaps is the last 0.3 s. Rings (slam, burst, aura, pull, gust) are centred on the boss; rain marks are
  placed under players first (`:151-166`).
- Phases at 66/33% (guardians and nightmares 50%): 30 ticks invulnerable, ability list swapped, cooldowns re-staggered,
  one part dropped, move speed +10% in phase 2 and +20% in phase 3 (`mobs/def/MobDef.java:188-190`, `mobs/entity/AwakenBoss.java:60-94`).
- Typical cadence, Gearwright Colossus phase 1: Piston Slam every ~150 ticks (110 cd + ~10 random + 30 tell), Spark Halo
  every ~174 ticks, melee every 20. Standing in melee at D that is 4.06 + 7.26/7.5 + 4.06/8.7 ≈ **5.5 damage/s, 74% of
  it from the untelegraphed swing** (SUSPECTED: assumes the player stays in reach and every ability lands).

| Boss (file:line) | tier / body | base hp, dmg (+weapon) | met at | HP there | ATTACK_DAMAGE there | armour | phases and abilities (damage = ATTACK_DAMAGE x factor; tell = wind-up ticks) |
|---|---|---|---|---|---|---|---|
| The Bell Warden (`GateBestiary.java:55`) | lord / bell ground, arena 22 | 30, 4.5 | gate E+ (lv 5) | 578 | 8.6 | 0.4 | P1<100%: Toll Wave (burst x1.6 r10/safe<3, tell 40t, cd 260t); Clapper Strike (slam x1.5 r4, tell 22t, cd 110t); Echo Bolts (bolt x1·3, tell 14t, cd 50t)<br>P2<66%: The Statues Move (summon, tell 30t, cd 400t); Bell Rush (charge x1.4, tell 26t, cd 120t, HEAVY); Toll Wave (burst x2.2 r11/safe<3, tell 40t, cd 260t)<br>P3<33%: Grand Peal (burst x2.6 r16/safe<4, tell 58t, cd 260t, HEAVY); Shattered Bronze (nova x1·12, tell 24t, cd 140t); Clapper Strike (slam x1.5 r4, tell 22t, cd 110t) |
| Mother of Spores (`GateBestiary.java:75`) | lord / ghast fly, arena 24 | 28, 4 | gate E+ (lv 5) | 539 | 7.7 | 0.4 | P1<100%: Spore Rain (rain x1·4 r2.5, tell 34t, cd 180t); Spore Lob (bolt x1·3, tell 14t, cd 50t)<br>P2<66%: Birth (summon, tell 30t, cd 400t); Spore Halo (nova x1·14, tell 24t, cd 140t)<br>P3<33%: Bloom (burst x2.2 r14/safe<4, tell 48t, cd 260t, HEAVY); Spore Rain (rain x1·6 r2.5, tell 34t, cd 180t); Birth (summon, tell 30t, cd 400t) |
| The Ember Tyrant (`GateBestiary.java:96`) | lord / piglin_brute ground; FIRE_IMMUNE,IGNITE, arena 22 | 30, 5 (+9 netherite_axe) | gate E+ (lv 5) | 578 | 26.9 | 2.4 | P1<100%: Magma Fist (slam x1.5 r4, tell 30t, cd 110t, HEAVY); Tyrant's Advance (charge x1.4, tell 18t, cd 120t)<br>P2<66%: Eruption (rain x1·5 r2.5, tell 34t, cd 180t); Stoke the Imps (summon, tell 30t, cd 400t); Magma Fist (slam x1.5 r4, tell 22t, cd 110t)<br>P3<33%: Meltdown (aura x0.5 r7, tell 44t, cd 320t, HEAVY); Eruption (rain x1·7 r2.5, tell 34t, cd 180t); Tyrant's Advance (charge x1.4, tell 18t, cd 120t) |
| The Drowned Archivist (`GateBestiary.java:117`) | lord / drowned ground, arena 22 | 30, 4.5 (+8 trident) | gate D+ (lv 17) | 956 | 33.6 | 1.4 | P1<100%: Tidal Page (cone x1.4 r7, tell 26t, cd 120t, HEAVY); Ink Volley (bolt x1·3, tell 14t, cd 50t)<br>P2<66%: Loose Pages (summon, tell 30t, cd 400t); Ink Cloud (cloud x0.2, tell 18t, cd 200t); Tidal Page (cone x1 r7, tell 18t, cd 120t)<br>P3<33%: Flood (aura x0.5 r8, tell 44t, cd 320t, HEAVY); Undertow (pull x0.2 r12, tell 24t, cd 200t); Tidal Page (cone x1 r8, tell 18t, cd 120t) |
| The Gearwright Colossus (`GateBestiary.java:136`) | lord / iron_golem ground, arena 24 | 34, 5 | gate D+ (lv 17) | 1,083 | 13.4 | 1.4 | P1<100%: Piston Slam (slam x1.5 r4.5, tell 30t, cd 110t, HEAVY); Spark Halo (nova x1·12, tell 24t, cd 140t)<br>P2<66%: Release Drones (summon, tell 30t, cd 400t); Colossal Stride (charge x1.4, tell 18t, cd 120t); Piston Slam (slam x1.5 r4.5, tell 22t, cd 110t)<br>P3<33%: Overload Arc (beam x2.2, tell 38t, cd 140t, HEAVY); Arc Strikes (rain x1·6 r2.5, tell 34t, cd 180t); Piston Slam (slam x1.5 r5, tell 22t, cd 110t) |
| The Glass Serpent (`GateBestiary.java:155`) | lord / serpent fly, arena 26 | 32, 5 | gate D+ (lv 17) | 1,019 | 13.4 | 1.4 | P1<100%: Glass Lunge (charge x1.4, tell 26t, cd 120t, HEAVY); Shard Spray (nova x1·10, tell 24t, cd 140t)<br>P2<66%: Burrow Strike (blink x1.6, tell 22t, cd 120t, HEAVY); Erupting Glass (rain x1·5 r2.5, tell 34t, cd 180t); Glass Lunge (charge x1.4, tell 18t, cd 120t)<br>P3<33%: Glass Storm (nova x1·20, tell 32t, cd 140t, HEAVY); Glass Lunge (charge x1.4, tell 18t, cd 120t); Erupting Glass (rain x1·6 r2.5, tell 34t, cd 180t) |
| The Hive Regent (`GateBestiary.java:174`) | lord / bee fly, arena 22 | 28, 4.5 | gate E+ (lv 5) | 539 | 8.6 | 0.4 | P1<100%: Royal Dive (charge x1.4, tell 26t, cd 120t, HEAVY); Brood (summon, tell 30t, cd 400t)<br>P2<66%: Honey Rain (rain x1·5 r2.5, tell 34t, cd 180t); Stinger Volley (bolt x1·4, tell 14t, cd 50t); Royal Dive (charge x1.4, tell 18t, cd 120t)<br>P3<33%: Royal Jelly (heal, tell 30t, cd 260t); Frenzy (frenzy, tell 20t, cd 400t); Brood (summon, tell 30t, cd 400t); Royal Dive (charge x1.4, tell 26t, cd 120t, HEAVY) |
| The Root Saint (`GateBestiary.java:193`) | lord / creaking ground, arena 22 | 30, 4.5 | gate E+ (lv 5) | 578 | 8.6 | 0.4 | P1<100%: Root Lance (fangs x1.2·14, tell 20t, cd 160t); Saint's Palm (slam x1.5 r4, tell 22t, cd 110t)<br>P2<66%: Grow Creakers (summon, tell 30t, cd 400t); Root Grasp (root x0.5 r3, tell 26t, cd 180t); Root Ring (fangs_ring x1.2·10, tell 20t, cd 200t)<br>P3<33%: Thorn Halo (nova x1·16, tell 32t, cd 140t, HEAVY); Root Lance (fangs x1.2·18, tell 20t, cd 160t); Root Grasp (root x0.5 r3, tell 26t, cd 180t) |
| The Lantern Broker (`GateBestiary.java:211`) | lord / lantern ground; FRIENDLY, arena 18 | 28, 4.5 | gate D+ (lv 17) | 892 | 12.1 | 1.4 | P1<100%: Lantern Bolts (bolt x1·3, tell 14t, cd 50t); Market Step (blink x1.2, tell 14t, cd 120t)<br>P2<66%: Release the Moths (summon, tell 30t, cd 400t); Paper Lanterns (nova x1·10, tell 24t, cd 140t)<br>P3<33%: Lights Out (darkness, tell 34t, cd 260t, HEAVY); Market Step (blink x1.2, tell 14t, cd 120t); Lantern Bolts (bolt x1·5, tell 14t, cd 50t) |
| The Abbot of Frost (`GateBestiary.java:232`) | lord / evoker ground, arena 22 | 28, 4.5 | gate C+ (lv 35) | 1,421 | 17.3 | 2.9 | P1<100%: Frost Hymn (bolt x1·5, tell 14t, cd 50t); Ice Spikes (rain x1·4 r2.5, tell 34t, cd 180t)<br>P2<66%: The Choir (summon, tell 30t, cd 400t); Blizzard (aura x0.5 r9, tell 36t, cd 320t); Ice Spikes (rain x1·5 r2.5, tell 34t, cd 180t)<br>P3<33%: Glacial Vow (burst x2.2 r14/safe<4, tell 48t, cd 260t, HEAVY); Ice Spikes (rain x1·6 r2.5, tell 34t, cd 180t); Frost Nova (nova x1·14, tell 24t, cd 140t) |
| The Mirror Knight (`GateBestiary.java:251`) | lord / mimic ground; MIMIC,REFLECT, arena 22 | 30, 5 (+6 diamond_sword) | gate C+ (lv 35) | 1,522 | 42.2 | 13.9 | P1<100%: Perfect Parry (guard, tell 8t, cd 160t); Mirror Lunge (charge x1.4, tell 18t, cd 120t)<br>P2<66%: Mirror Split (decoy, tell 24t, cd 360t); Through the Glass (blink x1.2, tell 14t, cd 120t); Mirror Lunge (charge x1.4, tell 18t, cd 120t)<br>P3<33%: Shatter (nova x1·18, tell 32t, cd 140t, HEAVY); Mirror Split (decoy, tell 24t, cd 360t); Perfect Parry (guard, tell 8t, cd 160t); Mirror Lunge (charge x1.4, tell 18t, cd 120t) |
| Unit Zero (`GateBestiary.java:270`) | lord / human ground, arena 22 | 28, 4.5 | gate C+ (lv 35) | 1,421 | 17.3 | 2.9 | P1<100%: Zero Lance (beam x1.6, tell 30t, cd 140t); Phase Step (blink x1.2, tell 14t, cd 120t)<br>P2<66%: Deploy Coils (summon, tell 30t, cd 400t); Orbital Mark (rain x1·4 r2.5, tell 34t, cd 180t); Zero Lance (beam x1.6, tell 30t, cd 140t)<br>P3<33%: Zero Point (burst x2.2 r14/safe<3.5, tell 48t, cd 260t, HEAVY); Zero Lance (beam x1.6, tell 30t, cd 140t); Phase Step (blink x1.2, tell 14t, cd 120t) |
| The Sleeper (`GateBestiary.java:289`) | lord / zombie ground; HAUNTED, arena 22 | 30, 4.5 | gate C+ (lv 35) | 1,522 | 17.3 | 2.9 | P1<100%: Restless Fist (slam x1.5 r4.5, tell 22t, cd 110t); Heavy Eyelids (darkness, tell 26t, cd 260t)<br>P2<66%: Night Terrors (summon, tell 30t, cd 400t); Dreamstep (blink x1.2, tell 14t, cd 120t); Restless Fist (slam x1.5 r4.5, tell 22t, cd 110t)<br>P3<33%: Rude Awakening (burst x2.2 r14/safe<3.5, tell 48t, cd 260t, HEAVY); Sleepwalkers (summon, tell 30t, cd 400t); Restless Fist (slam x1.5 r5, tell 22t, cd 110t) |
| The Sky Leviathan (`GateBestiary.java:308`) | lord / serpent fly, arena 28 | 34, 5 | gate B+ (lv 57) | 2,511 | 26.2 | 4.8 | P1<100%: Leviathan Dive (charge x1.4, tell 26t, cd 120t, HEAVY); Wingbeat (gust x0.6 r10, tell 24t, cd 200t)<br>P2<66%: Gravity Tide (lift x0.3 r4, tell 30t, cd 220t); Pearl Barrage (bolt x1·6, tell 14t, cd 50t); Leviathan Dive (charge x1.4, tell 18t, cd 120t)<br>P3<33%: Storm Eye (rain x1·7 r2.5, tell 42t, cd 180t, HEAVY); Leviathan Dive (charge x1.4, tell 18t, cd 120t); Wingbeat (gust x0.7 r10, tell 24t, cd 200t) |
| The Hollow Herald (`GateBestiary.java:327`) | lord / shade fly, arena 24 | 32, 5 | gate A+ (lv 85) | 3,304 | 35.2 | 7.1 | P1<100%: Void Bolts (bolt x1·5, tell 14t, cd 50t); Draw Near (pull x0.2 r10, tell 24t, cd 200t)<br>P2<66%: Spawn of Nothing (summon, tell 30t, cd 400t); Unlit (darkness, tell 26t, cd 260t); Void Bolts (bolt x1·5, tell 14t, cd 50t)<br>P3<33%: Event Horizon (pull x0.32 r16, tell 32t, cd 200t, HEAVY); Collapse (burst x2.2 r13/safe<3.5, tell 40t, cd 260t); Void Halo (nova x1·16, tell 24t, cd 140t) |
| The Rootmother (`DeepBestiary.java:118`) | guardian / spider ground, arena 22 | 30, 4.5 | stratum 1 (lv 17) | 1,099 | 12.1 | 1.4 | P1<100%: Moss Web (root x0.5 r3, tell 26t, cd 180t); Rootfall (leap x1.3, tell 24t, cd 120t, HEAVY)<br>P2<50%: Brood (summon, tell 30t, cd 400t); Root Lances (fangs x1.2·14, tell 20t, cd 160t); Rootfall (leap x1.3, tell 16t, cd 120t) |
| The Thornback (`DeepBestiary.java:123`) | guardian / ravager ground, arena 24 | 32, 5 | stratum 2 (lv 17) | 1,172 | 13.4 | 1.4 | P1<100%: Thornback Charge (charge x1.4, tell 26t, cd 120t, HEAVY); Gale of Petals (gust x0.8 r10, tell 24t, cd 200t)<br>P2<50%: Thorn Spray (nova x1·14, tell 24t, cd 140t); Lurkers (summon, tell 30t, cd 400t); Thornback Charge (charge x1.4, tell 18t, cd 120t) |
| The Choirmaster (`DeepBestiary.java:128`) | guardian / witch ground, arena 22 | 30, 4.5 | stratum 3 (lv 35) | 1,751 | 17.3 | 2.9 | P1<100%: Hymn of Salt (bolt x1·4, tell 14t, cd 50t); Refrain (heal, tell 30t, cd 260t)<br>P2<50%: Rising Tide (aura x0.5 r9, tell 44t, cd 320t, HEAVY); The Choir (summon, tell 30t, cd 400t); Undertow (pull x0.2 r10, tell 24t, cd 200t) |
| The Ossuary King (`DeepBestiary.java:133`) | guardian / wither_skeleton ground; RISES, arena 22 | 28, 5 (+3 stone_sword) | stratum 4 (lv 35) | 1,634 | 30.7 | 2.9 | P1<100%: Bone Crush (slam x1.5 r4, tell 30t, cd 110t, HEAVY); Rib Spikes (nova x1·12, tell 24t, cd 140t)<br>P2<50%: Rise (summon, tell 30t, cd 400t); Royal March (charge x1.4, tell 18t, cd 120t); Bone Crush (slam x1.5 r4, tell 22t, cd 110t) |
| The Great Listener (`DeepBestiary.java:138`) | guardian / listener ground; LISTENER, arena 24 | 30, 5.5 | stratum 5 (lv 57) | 2,548 | 28.9 | 4.8 | P1<100%: Found You (slam x1.6 r3.5, tell 30t, cd 110t, HEAVY); Echo Scream (beam x1.6, tell 30t, cd 140t)<br>P2<50%: Absolute Silence (darkness, tell 26t, cd 260t); Ears (summon, tell 30t, cd 400t); Found You (slam x1.6 r3.5, tell 22t, cd 110t) |
| The Furnace Engine (`DeepBestiary.java:143`) | guardian / iron_golem ground, arena 24 | 32, 5 | stratum 6 (lv 57) | 2,718 | 26.2 | 4.8 | P1<100%: Engine Slam (slam x1.5 r4.5, tell 30t, cd 110t, HEAVY); Arc Weld (beam x1.6, tell 30t, cd 140t)<br>P2<50%: Wake the Drones (summon, tell 30t, cd 400t); Rivet Rain (rain x1·5 r2.5, tell 34t, cd 180t); Engine Slam (slam x1.5 r4.5, tell 22t, cd 110t) |
| The Drowned Moon (`DeepBestiary.java:148`) | guardian / elder_guardian fly, arena 26 | 30, 5 | stratum 7 (lv 85) | 3,562 | 35.2 | 7.1 | P1<100%: Moonbeam (beam x1.8, tell 38t, cd 140t, HEAVY); Tide Turn (lift x0.3 r4, tell 30t, cd 220t)<br>P2<50%: Undertow (pull x0.2 r14, tell 24t, cd 200t); Tide Halo (nova x1·14, tell 24t, cd 140t); Tide Turn (lift x0.3 r5, tell 30t, cd 220t) |
| The Architect of Rooms (`DeepBestiary.java:153`) | guardian / evoker ground, arena 22 | 30, 5 | stratum 8 (lv 85) | 3,562 | 35.2 | 7.1 | P1<100%: Shifting Floor (fangs x1.2·14, tell 20t, cd 160t); Door Step (blink x1.2, tell 14t, cd 120t)<br>P2<50%: Other Rooms (decoy, tell 24t, cd 360t); Falling Ceiling (rain x1·5 r2.5, tell 34t, cd 180t); Shifting Floor (fangs x1.2·16, tell 20t, cd 160t) |
| The Shadow Regent (`DeepBestiary.java:158`) | guardian / mimic ground; MIMIC, arena 22 | 32, 5.5 (+7 netherite_sword) | stratum 9 (lv 118) | 5,075 | 114.4 | 9.8 | P1<100%: Your Parry (guard, tell 8t, cd 160t); Your Charge (charge x1.4, tell 18t, cd 120t)<br>P2<50%: Your Reflections (decoy, tell 24t, cd 360t); Shadowstep (blink x1.2, tell 14t, cd 120t); Hollow Halo (nova x1·12, tell 24t, cd 140t) |
| The Seed Warden (`DeepBestiary.java:163`) | guardian / shade fly, arena 24 | 34, 5.5 | stratum 10 (lv 118) | 5,392 | 50.3 | 9.8 | P1<100%: Signal Lance (beam x1.6, tell 30t, cd 140t); Signal Marks (rain x1·4 r2.5, tell 34t, cd 180t)<br>P2<50%: Motes (summon, tell 30t, cd 400t); Door Light (burst x2.2 r13/safe<3.5, tell 48t, cd 260t, HEAVY) |
| Sovereign of Hunger (`DeepBestiary.java:171`) | sovereign / ravager ground, arena 26 | 34, 5.5 | lv 118 | 7,033 | 50.3 | 9.8 | P1<100%: Devour (charge x1.6, tell 26t, cd 120t, HEAVY); Gnawing Pull (pull x0.2 r12, tell 24t, cd 200t)<br>P2<66%: Hounds of the Court (summon, tell 30t, cd 400t); Gorge (slam x1.5 r4.5, tell 22t, cd 110t); Devour (charge x1.4, tell 18t, cd 120t)<br>P3<33%: Starving Frenzy (frenzy, tell 20t, cd 400t); Famine (aura x0.5 r8, tell 44t, cd 320t, HEAVY); Devour (charge x1.4, tell 18t, cd 120t) |
| Sovereign of Silence (`DeepBestiary.java:177`) | sovereign / listener ground; LISTENER, arena 26 | 34, 5.5 | lv 118 | 7,033 | 50.3 | 9.8 | P1<100%: Found You (slam x1.6 r4, tell 30t, cd 110t, HEAVY); No Sound At All (darkness, tell 26t, cd 260t)<br>P2<66%: Inverted Scream (beam x2, tell 38t, cd 140t, HEAVY); Ears (summon, tell 30t, cd 400t)<br>P3<33%: Deafening Silence (burst x2.2 r15/safe<4, tell 48t, cd 260t, HEAVY); Hush (pull x0.2 r14, tell 24t, cd 200t); Found You (slam x1.5 r4, tell 22t, cd 110t) |
| Sovereign of Sleep (`DeepBestiary.java:183`) | sovereign / phantom fly, arena 28 | 32, 5.5 | lv 118 | 6,619 | 50.3 | 9.8 | P1<100%: Night Dive (charge x1.4, tell 26t, cd 120t, HEAVY); Lullaby (darkness, tell 26t, cd 260t)<br>P2<66%: Lullabies (summon, tell 30t, cd 400t); Dream Fall (rain x1·5 r2.5, tell 34t, cd 180t); Night Dive (charge x1.4, tell 18t, cd 120t)<br>P3<33%: Never Wake (burst x2.2 r15/safe<4, tell 48t, cd 260t, HEAVY); Night Dive (charge x1.4, tell 18t, cd 120t); Lullaby (darkness, tell 26t, cd 260t) |
| Sovereign of Machines (`DeepBestiary.java:189`) | sovereign / iron_golem ground, arena 26 | 36, 5.5 | lv 118 | 7,447 | 50.3 | 9.8 | P1<100%: Calculated Lance (beam x1.8, tell 30t, cd 140t); Hydraulic Slam (slam x1.5 r4.5, tell 30t, cd 110t, HEAVY)<br>P2<66%: Drones (summon, tell 30t, cd 400t); Orbital Strike (rain x1·6 r2.5, tell 34t, cd 180t); Calculated Lance (beam x1.6, tell 30t, cd 140t)<br>P3<33%: Shutdown (burst x2.2 r15/safe<4, tell 48t, cd 260t, HEAVY); Spark Lattice (nova x1·16, tell 24t, cd 140t); Hydraulic Slam (slam x1.5 r5, tell 22t, cd 110t) |
| Sovereign of Mirrors (`DeepBestiary.java:195`) | sovereign / mimic ground; MIMIC,REFLECT, arena 24 | 32, 5.5 (+6 diamond_sword) | lv 118 | 6,619 | 105.2 | 9.8 | P1<100%: Flawless Parry (guard, tell 8t, cd 160t); Mirrored Lunge (charge x1.4, tell 18t, cd 120t)<br>P2<66%: Hall of Mirrors (decoy, tell 24t, cd 360t); Through the Glass (blink x1.2, tell 14t, cd 120t); Mirrored Lunge (charge x1.4, tell 18t, cd 120t)<br>P3<33%: Shatter (nova x1·20, tell 32t, cd 140t, HEAVY); Hall of Mirrors (decoy, tell 24t, cd 360t); Flawless Parry (guard, tell 8t, cd 160t) |
| Sovereign of Storms (`DeepBestiary.java:201`) | sovereign / breeze hop, arena 28 | 32, 5.5 | lv 118 | 6,619 | 50.3 | 9.8 | P1<100%: Lightning (rain x1·4 r2.5, tell 34t, cd 180t); Thunderclap (gust x0.8 r10, tell 24t, cd 200t)<br>P2<66%: Storm Spears (bolt x1·5, tell 14t, cd 50t); Updraft (lift x0.3 r4, tell 30t, cd 220t); Lightning (rain x1·5 r2.5, tell 34t, cd 180t)<br>P3<33%: Lightning (rain x1·8 r2.5, tell 42t, cd 180t, HEAVY); Overdrive (frenzy, tell 20t, cd 400t); Thunderclap (gust x0.9 r12, tell 24t, cd 200t) |
| Sovereign of Roots (`DeepBestiary.java:207`) | sovereign / creaking ground, arena 24 | 36, 5.5 | lv 118 | 7,447 | 50.3 | 9.8 | P1<100%: Root Lance (fangs x1.2·16, tell 20t, cd 160t); Hold Fast (root x0.5 r3, tell 26t, cd 180t)<br>P2<66%: Rooted Ones (summon, tell 30t, cd 400t); Hearth Ring (fangs_ring x1.2·12, tell 20t, cd 200t); Root Lance (fangs x1.2·16, tell 20t, cd 160t)<br>P3<33%: Hold Fast (root x0.5 r4.5, tell 34t, cd 180t, HEAVY); Thorn Halo (nova x1·16, tell 24t, cd 140t); Hearth Ring (fangs_ring x1.2·14, tell 20t, cd 200t) |
| Sovereign of Ash (`DeepBestiary.java:213`) | sovereign / blaze fly; FIRE_IMMUNE, arena 26 | 32, 5.5 | lv 118 | 6,619 | 50.3 | 9.8 | P1<100%: Cinders (bolt x1·5, tell 14t, cd 50t); Ash Cloud (cloud x0.2, tell 18t, cd 200t)<br>P2<66%: Ashfall (rain x1·6 r2.5, tell 34t, cd 180t); Embers (summon, tell 30t, cd 400t); Cinders (bolt x1·5, tell 14t, cd 50t)<br>P3<33%: Burnout (aura x0.5 r9, tell 44t, cd 320t, HEAVY); Last Embers (nova x1·16, tell 24t, cd 140t); Ashfall (rain x1·7 r2.5, tell 34t, cd 180t) |
| The Signal's Shadow (`DeepBestiary.java:219`) | final / shade fly, arena 30 | 40, 6 | lv 118 | 11,032 | 54.9 | 9.8 | P1<100%: Instruction (beam x1.8, tell 30t, cd 140t); Signal Step (blink x1.2, tell 14t, cd 120t); Directive (bolt x1·5, tell 14t, cd 50t)<br>P2<66%: Echoes (summon, tell 30t, cd 400t); Signal Marks (rain x1·6 r2.5, tell 34t, cd 180t); Your Echoes (decoy, tell 24t, cd 360t)<br>P3<33%: Seed Collapse (burst x2.6 r16/safe<4, tell 48t, cd 260t, HEAVY); Come Down (pull x0.2 r18, tell 24t, cd 200t); Signal Halo (nova x1·20, tell 24t, cd 140t); Last Instruction (frenzy, tell 20t, cd 400t) |
| The Long Drop (`DeepBestiary.java:228`) | nightmare / phantom fly, arena 22 | 22, 4 | lv 17 (assumed) | 300 | 8.4 | 1.4 | P1<100%: Lift (lift x0.3 r3.5, tell 30t, cd 220t); Fall With Me (charge x1.4, tell 26t, cd 120t, HEAVY)<br>P2<50%: Lift (lift x0.3 r4.5, tell 30t, cd 220t); No Bottom (darkness, tell 26t, cd 260t); Fall With Me (charge x1.4, tell 18t, cd 120t) |
| The Pyre (`DeepBestiary.java:232`) | nightmare / blaze fly; FIRE_IMMUNE, arena 22 | 22, 4 | lv 17 (assumed) | 300 | 8.4 | 1.4 | P1<100%: Cinders (bolt x1·4, tell 14t, cd 50t); Pyre (aura x0.5 r6, tell 36t, cd 320t)<br>P2<50%: Firefall (rain x1·5 r2.5, tell 34t, cd 180t); Flare (nova x1·12, tell 24t, cd 140t) |
| The Undertow (`DeepBestiary.java:236`) | nightmare / drowned ground, arena 22 | 22, 4 | lv 17 (assumed) | 300 | 8.4 | 1.4 | P1<100%: Undertow (pull x0.2 r12, tell 24t, cd 200t); Rush of Water (cone x1 r6, tell 18t, cd 120t)<br>P2<50%: Lungful (aura x0.5 r7, tell 36t, cd 320t); Black Water (bolt x1·4, tell 14t, cd 50t) |
| The Hollow Crowd (`DeepBestiary.java:240`) | nightmare / zombie ground; RISES, arena 22 | 22, 4 | lv 17 (assumed) | 300 | 8.4 | 1.4 | P1<100%: Crush (slam x1.5 r3.5, tell 22t, cd 110t); More of Them (summon, tell 30t, cd 400t)<br>P2<50%: More of Them (summon, tell 30t, cd 400t); Surge (charge x1.4, tell 26t, cd 120t, HEAVY) |
| The Thousand Legs (`DeepBestiary.java:244`) | nightmare / spider ground, arena 22 | 22, 4 | lv 17 (assumed) | 300 | 8.4 | 1.4 | P1<100%: Web (root x0.5 r2.5, tell 26t, cd 180t); Drop (leap x1.3, tell 24t, cd 120t, HEAVY)<br>P2<50%: Hatchlings (summon, tell 30t, cd 400t); Web (root x0.5 r3, tell 26t, cd 180t) |
| The Fuse (`DeepBestiary.java:248`) | nightmare / creeper ground, arena 22 | 22, 4 | lv 17 (assumed) | 300 | 8.4 | 1.4 | P1<100%: Fuse Lines (rain x1·4 r2.5, tell 34t, cd 180t); Blast Ring (burst x2.2 r10/safe<3, tell 48t, cd 260t, HEAVY)<br>P2<50%: Fuse Lines (rain x1·6 r2.5, tell 26t, cd 180t); Blast Ring (burst x2.2 r12/safe<3, tell 48t, cd 260t, HEAVY) |
| The Faceless (`DeepBestiary.java:252`) | nightmare / human ground, arena 22 | 22, 4 | lv 17 (assumed) | 300 | 8.4 | 1.4 | P1<100%: Corner of Your Eye (blink x1.2, tell 14t, cd 120t); Lights Off (darkness, tell 26t, cd 260t)<br>P2<50%: Everywhere (decoy, tell 24t, cd 360t); Stare (beam x1.6, tell 38t, cd 140t, HEAVY); Corner of Your Eye (blink x1.2, tell 14t, cd 120t) |

---------------------------------------------------------------------------------------------------------------------

## 4. Reproducing the disappointment: a diamond sword against a Gate Lord

Computed by scratchpad `repro.py` so every case uses the same formulas. Inputs and where they come from:

### 4.1 Inputs

| Input | Value | Source |
|---|---|---|
| Diamond sword | 7 attack (player 1 + 6), 1.6 swings/s (4.0 − 2.4) | 7 VERIFIED: `gear/GearTests.java:293-296` asserts the C Hunter blade's modifier equals the diamond sword's, and the C blade is +6 (`gear/HunterTier.java:37`, `gear/GearItems.java:70-71`). 1.6/s ASSUMED (vanilla; the blades' −2.3 → 1.7/s in `docs/modules/gear.md` implies the same base 4.0). |
| STR | ×(1 + 0.025 × (STR − 5)) on (1 + weapon) | `progression/Progression.java:288, 294` (ADD_MULTIPLIED_BASE, which multiplies base + added values: ASSUMED vanilla attribute maths) |
| AGI | attack speed ×(1 + 0.01 × (AGI − 5)), cap 0.60 | `progression/Progression.java:289, 296` |
| VIT | max HP 20 + 0.6 × (VIT − 5); incoming × (1 − min(0.35, 0.005 × (VIT − 5))) before armour | `progression/Progression.java:290, 297`; `progression/ProgressionModule.java:320-328` |
| PER | crit chance min(0.35, 0.004 × (PER − 5)), ×1.6 | `progression/ProgressionModule.java:343-352` |
| Points | 3 per level (`Curve.POINTS_PER_LEVEL`); regimens and Truth points ignored (they'd add a few %) | `progression/Curve.java:10`; `progression/Progression.java:157` |
| Plausible build | ~42% STR, ~30% VIT, ~18% AGI, ~10% PER (a melee player) | assumption |
| Levels | D case: level 17 (D's content level, RANK_MID); B case: level 57; B trial: level 45 (the trial uses the rank's required level as the Lord's level) | `progression/Rank.java:12-14`; `hearth/trial/Trials.java:347` |
| Lord scaling | HP ×(1+0.075·lv)×14; ATTACK_DAMAGE ×(1+0.04·lv)×1.6; armour lv/12 | `mobs/MobScaling.java:30-75` |
| Lord melee | every 20 ticks in reach (ground), 30-49 ticks (fliers, 40 used) | `mobs/entity/AwakenMob.java:175` (interval ASSUMED vanilla), `mobs/entity/MobGoals.java:121` |
| Armour formula | dmg × (1 − clamp(armour − dmg / (2 + toughness/4), armour/5, 20) / 25) | ASSUMED vanilla 1.21 `CombatRules.getDamageAfterAbsorb` (unverified against 26.3 here) |
| Player armour | full diamond: 20 armour, 8 toughness, **unenchanted** | ASSUMED vanilla values; `gear/GearTests.java:286-290` bounds diamond to 19-20 |
| Player swing rate | full charge every ceil(20 / speed) ticks, never below 10 (the Lord's i-frames) | ASSUMED vanilla i-frames (`docs/api/entities.md:338-339`) |
| World difficulty | Normal (Hard would ×1.5 the melee and plain bolts, not Force bolts) | `src/main/resources/data/awaken/damage_type/*.json` (`scaling: never`) |

### 4.2 Headline numbers

| Case | Lord HP | Player hit after Lord armour (crit-weighted) | Hits to kill | Time to kill (perfect uptime) | Lord melee taken per hit | Player HP | Melee hits to die | Time to die in reach | Lord HP removed before dying |
|---|---|---|---|---|---|---|---|---|---|
| **D1** Gearwright Colossus, lv 17 | 1,083 | 10.53 | **103** | **61.8 s** | 4.06 | 28.4 | 7 | **~6 s** | 11% |
| D2 Ember Tyrant, lv 17 (axe counted) | 956 | 10.36 | 93 | 55.8 s | **19.25** | 28.4 | **2** | **~1 s** | 4% |
| D3 Drowned Archivist, lv 17 (trident counted) | 956 | 10.53 | 91 | 54.6 s | 16.01 | 28.4 | 2 | ~1 s | 4% |
| **B1** Gearwright Colossus, lv 57 | 2,511 | 19.32 | **130** | **65.0 s** | 7.81 | 50.0 | 7 | **~6 s** | 11% |
| B2 Sky Leviathan, lv 57 (flier) | 2,511 | 19.32 | 130 | 65 s *if always in reach* | 7.81 every ~2 s | 50.0 | 7 | ~12 s | ≤ 22% |
| B3 Mirror Knight, lv 57 (its sword and armour counted) | 2,216 | 12.58 (its 15.75 armour takes 37%) | 177 | 88.5 s | 27.40 | 50.0 | 2 | ~1 s | 2% |
| T B-rank trial duel, Colossus lv 45 | 2,083 | 16.49 | 127 | 69.8 s (limit 300 s) | 6.80 | 44.0 | 7 | ~6 s | 10% |
| B1+ B Colossus, Sharpness V + temper II + Trophy Hall +10% | 2,511 | 34.32 | 74 | 37.0 s | 7.81 | 50.0 | 7 | ~6 s | 19% |
| B1n B Colossus, netherite sword + netherite armour | 2,511 | 22.08 | 114 | 57.0 s | 7.03 | 50.0 | 8 | ~7 s | 14% |

What it means:
- **On-level, a sword kills a Lord in ~100-130 perfect swings, ~1 minute of uninterrupted contact, and survives 6-7 of
  its untelegraphed swings.** Dodging is the only defence, so the player must spend about **90%** of the fight not in
  reach (or not hit) while still landing every swing they take: the two are in direct conflict for a melee weapon.
- The **ratio is the same at D and B** (11% of the Lord per life): the scaling keeps the problem constant, so a
  higher tier of sword never fixes it. Better gear (netherite, Sharpness V, tempering, trophies) moves it from 11% to
  14-19%; nothing in the game today moves it to 100%.
- For comparison, a normal on-level creature takes 4.8 (D) and 6.0 (B) swings with the same build (22 HP base,
  ×2.275 / ×5.275): SPEC §2's "3-6 hits per normal mob" holds. **A Lord is 18-22 normal mobs of health** (×14 role, base 28-34 HP
  against a normal's ~22) that **hits 1.6× harder** and **can't be knocked back, stunned for long or interrupted**.
- With a held weapon counted (D2, D3, B3), a Lord's ordinary swing takes 56-68% of the player's health and its first
  telegraphed hit kills. If the SUSPECTED weapon stacking is real, those three Lords are unwinnable in melee.
- Fliers (5 of 15 Lords) are worse for a sword: they hover 2-5.5 blocks above and only come into reach on a dive.
- Powers don't rescue the math on their own: any one power hit is capped at 20% of a boss's health
  (`powers/PowerMath.java:16`), so at least five big hits, gated by energy and cooldowns.

### 4.3 The arithmetic, case by case

#### D1 worked by hand (Gearwright Colossus in a D-rank gate; player level 17, diamond sword, full diamond)

Player, 3 × 16 = 48 points: STR 25 (+20), VIT 19 (+14), AGI 13 (+8), PER 11 (+6).
- Swing: (1 + 6) × (1 + 20 × 0.025) = 7 × 1.5 = **10.50** (`Progression.java:294`).
- Attack speed: 1.6 × (1 + 8 × 0.01) = 1.728/s → 20 / 1.728 = 11.6 → a full charge every **12 ticks**
  (`Progression.java:296`).
- Crit: 6 × 0.004 = 2.4% at ×1.6 → expected ×(1 + 0.024 × 0.6) = ×1.0144 (`ProgressionModule.java:347-350`).
- Max HP 20 + 14 × 0.6 = **28.4**; resistance 14 × 0.005 = **7%** (`Progression.java:297`, `ProgressionModule.java:324-327`).

Lord (base 34 HP, 5 dmg, `GateBestiary.java:136`; lv 17, `Rank.java:12`):
- HP 34 × (1 + 0.075 × 17) × 14 = 34 × 2.275 × 14 = **1,082.9** (`MobScaling.java:30-32, 48, 64-67`).
- ATTACK_DAMAGE 5 × (1 + 0.04 × 17) × 1.6 = 5 × 1.68 × 1.6 = **13.44** (`MobScaling.java:34-36, 56, 73`).
- Armour 17 / 12 = 1.42, toughness 0 (`MobScaling.java:38-40, 75`).

Player → Lord: effective armour = clamp(1.42 − 10.50 / 2, 1.42 × 0.2, 20) = 0.28 → 1.1% off → 10.38 → × 1.0144 =
**10.53 per swing**. Hits = ceil(1,082.9 / 10.53) = **103**. Time = 103 × 12 ticks = 1,236 ticks = **61.8 s**, plus
2 × 1.5 s of phase-roar invulnerability if you swing into it (`AwakenBoss.java:84, 143-146`).

Lord → player:
- Melee: 13.44 × 0.93 = 12.50; effective armour = clamp(20 − 12.50 / (2 + 8/4), 4, 20) = 16.88 → 67.5% off →
  **4.06**. ceil(28.4 / 4.06) = **7 hits**, one every 20 ticks starting at t = 0 → dead at **t ≈ 6.0 s**. In those
  ~7 s the player lands ~12 swings = ~123 damage = **11.3%** of the Lord.
- Piston Slam (×1.5, heavy, 30-tick tell): 20.16 → 18.75 → eff. armour 15.31 → **7.26** (4 kill).
- Overload Arc (×2.2, phase 3, heavy, 38-tick tell): 29.57 → 27.50 → eff. armour 13.13 → **13.06** (3 kill).
- Over the 61.8 s the kill needs, the melee alone would deal 61.8 × 4.06 = 251 = **8.8 player health bars**.

#### B1 worked by hand (the same Lord in a B-rank gate; player level 57)

Player, 3 × 56 = 168 points: STR 75 (+70), VIT 55 (+50), AGI 35 (+30), PER 23 (+18).
- Swing 7 × (1 + 70 × 0.025) = 7 × 2.75 = **19.25**; speed 1.6 × 1.30 = 2.08/s → 9.6 ticks, i.e. every **10 ticks**
  (the Lord's i-frame floor: AGI past +25 buys nothing against one target).
- Crit 18 × 0.004 = 7.2% → ×1.0432; max HP 20 + 50 × 0.6 = **50**; resistance 50 × 0.005 = **25%**.

Lord at lv 57: HP 34 × 5.275 × 14 = **2,510.9**; ATTACK_DAMAGE 5 × 3.28 × 1.6 = **26.24**; armour 4.75.
- Player → Lord: eff. armour clamp(4.75 − 9.63, 0.95, 20) = 0.95 → 3.8% off → 18.52 × 1.0432 = **19.32**;
  ceil(2,510.9 / 19.32) = **130 hits × 10 ticks = 65.0 s**.
- Lord melee → player: 26.24 × 0.75 = 19.68 → eff. armour 20 − 19.68 / 4 = 15.08 → 60.3% off → **7.81**;
  ceil(50 / 7.81) = **7 hits → ~6 s**. Overload Arc 57.7 → 43.3 → eff. 9.18 → **27.40** (2 kill).

#### D2 worked by hand (Ember Tyrant, its netherite axe counted: SUSPECTED)

ATTACK_DAMAGE (5 + 9) × 1.68 × 1.6 = **37.63** (`GateBestiary.java:96` holds `Items.NETHERITE_AXE`, +9 in vanilla).
Melee 37.63 × 0.93 = 35.0 → eff. armour 20 − 35.0 / 4 = 11.25 → 45% off → **19.25** (two kill a 28.4-HP player, and
each sets you alight for 4 s, `AwakenMob.java:534`). Magma Fist ×1.5 = 56.4 → 52.5 → eff. armour 6.88 → **38.1**:
a one-shot from full health, behind a 30-tick tell. Without the axe it would be 13.44, like the Colossus.

#### The generated cases

**D1 · Gearwright Colossus, D-rank gate (lv 17) · diamond sword, full diamond armour**

- Lord: HP 1,082.9, ATTACK_DAMAGE 13.44, armour 1.42, toughness 0
- Player (level 17, 48 points): swing 10.50 raw, attack speed 1.728/s (full charge every 12 ticks; i-frame floor 10), crit 2.4% x1.6, VIT resist 7.0%, max HP 28.4
- Lord armour lets 10.38 through (effective armour 0.28 -> 1.1% off); expected per swing with crits 10.53
- **Hits to kill 103; time to kill 61.8 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| basic melee (MeleeAttackGoal, vanilla doHurtTarget) | 1 | 13.4 | 12.5 | 16.88 | **4.06** | 7 | every 20 ticks in reach, no wind-up |
| Piston Slam (SLAM, r4.5, heavy) | 1.5 | 20.2 | 18.7 | 15.31 | **7.26** | 4 | tell 30 t, cd 110+0..19 t |
| Spark Halo bolt (NOVA x12) | 1 | 13.4 | 12.5 | 16.88 | **4.06** | 7 | tell 24 t; 1 bolt usually connects |
| Colossal Stride (CHARGE, P2) | 1.4 | 18.8 | 17.5 | 15.63 | **6.56** | 5 | tell 18 t, line locks 6 t before |
| Overload Arc (BEAM, P3, heavy) | 2.2 | 29.6 | 27.5 | 13.13 | **13.06** | 3 | tell 38 t, aim locks 6 t before |

- Melee alone (a swing every 20 ticks, no telegraph): **4.06 damage/s**; player dies on hit 7 = **~6.0 s** standing in reach
- In that time the player lands ~12 swings = 123 damage = **11.3% of the Lord's health**; to finish, the player must avoid ~89% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 62 s needed: 251 = 8.8 x the player's health pool.

**D2 · Ember Tyrant, D-rank gate (lv 17) · its netherite axe counted (+9, SUSPECTED)**

- Lord: HP 955.5, ATTACK_DAMAGE 37.63, armour 3.42, toughness 0
- Player (level 17, 48 points): swing 10.50 raw, attack speed 1.728/s (full charge every 12 ticks; i-frame floor 10), crit 2.4% x1.6, VIT resist 7.0%, max HP 28.4
- Lord armour lets 10.21 through (effective armour 0.68 -> 2.7% off); expected per swing with crits 10.36
- **Hits to kill 93; time to kill 55.8 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| basic melee (+ ignite 4 s) | 1 | 37.6 | 35.0 | 11.25 | **19.25** | 2 | every 20 ticks, no wind-up |
| Magma Fist (SLAM r4, heavy) | 1.5 | 56.4 | 52.5 | 6.88 | **38.06** | 1 | tell 30 t |
| Tyrant's Advance (CHARGE) | 1.4 | 52.7 | 49.0 | 7.75 | **33.81** | 1 | tell 18 t |
| Eruption mark (RAIN) | 1 | 37.6 | 35.0 | 11.25 | **19.25** | 2 | tell 34 t, step out of the ring |

- Melee alone (a swing every 20 ticks, no telegraph): **19.25 damage/s**; player dies on hit 2 = **~1.0 s** standing in reach
- In that time the player lands ~3 swings = 35 damage = **3.6% of the Lord's health**; to finish, the player must avoid ~96% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 56 s needed: 1,074 = 37.8 x the player's health pool.

**D3 · Drowned Archivist, D-rank gate (lv 17) · its trident counted (+8, SUSPECTED)**

- Lord: HP 955.5, ATTACK_DAMAGE 33.60, armour 1.42, toughness 0
- Player (level 17, 48 points): swing 10.50 raw, attack speed 1.728/s (full charge every 12 ticks; i-frame floor 10), crit 2.4% x1.6, VIT resist 7.0%, max HP 28.4
- Lord armour lets 10.38 through (effective armour 0.28 -> 1.1% off); expected per swing with crits 10.53
- **Hits to kill 91; time to kill 54.6 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| basic melee | 1 | 33.6 | 31.2 | 12.19 | **16.01** | 2 | every 20 ticks |
| Tidal Page (CONE r7, heavy) | 1.4 | 47.0 | 43.7 | 9.06 | **27.89** | 2 | tell 26 t |

- Melee alone (a swing every 20 ticks, no telegraph): **16.01 damage/s**; player dies on hit 2 = **~1.0 s** standing in reach
- In that time the player lands ~3 swings = 35 damage = **3.7% of the Lord's health**; to finish, the player must avoid ~96% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 55 s needed: 874 = 30.8 x the player's health pool.

**B1 · Gearwright Colossus, B-rank gate (lv 57) · diamond sword, full diamond armour**

- Lord: HP 2,510.9, ATTACK_DAMAGE 26.24, armour 4.75, toughness 0
- Player (level 57, 168 points): swing 19.25 raw, attack speed 2.080/s (full charge every 10 ticks; i-frame floor 10), crit 7.2% x1.6, VIT resist 25.0%, max HP 50.0
- Lord armour lets 18.52 through (effective armour 0.95 -> 3.8% off); expected per swing with crits 19.32
- **Hits to kill 130; time to kill 65.0 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| basic melee | 1 | 26.2 | 19.7 | 15.08 | **7.81** | 7 | every 20 ticks, no wind-up |
| Piston Slam (SLAM, heavy) | 1.5 | 39.4 | 29.5 | 12.62 | **14.62** | 4 | tell 30 t |
| Spark Halo bolt | 1 | 26.2 | 19.7 | 15.08 | **7.81** | 7 | tell 24 t |
| Colossal Stride (CHARGE) | 1.4 | 36.7 | 27.6 | 13.11 | **13.10** | 4 | tell 18 t |
| Overload Arc (BEAM, heavy) | 2.2 | 57.7 | 43.3 | 9.18 | **27.40** | 2 | tell 38 t |

- Melee alone (a swing every 20 ticks, no telegraph): **7.81 damage/s**; player dies on hit 7 = **~6.0 s** standing in reach
- In that time the player lands ~14 swings = 270 damage = **10.8% of the Lord's health**; to finish, the player must avoid ~89% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 65 s needed: 508 = 10.2 x the player's health pool.

**B2 · Sky Leviathan, B-rank gate (lv 57, its home rank) · diamond sword, diamond armour**

- Lord: HP 2,510.9, ATTACK_DAMAGE 26.24, armour 4.75, toughness 0
- Player (level 57, 168 points): swing 19.25 raw, attack speed 2.080/s (full charge every 10 ticks; i-frame floor 10), crit 7.2% x1.6, VIT resist 25.0%, max HP 50.0
- Lord armour lets 18.52 through (effective armour 0.95 -> 3.8% off); expected per swing with crits 19.32
- **Hits to kill 130; time to kill 65.0 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| dive melee (FlyHunt, every 30-49 t) | 1 | 26.2 | 19.7 | 15.08 | **7.81** | 7 | flier: in reach only on its dive |
| Leviathan Dive (CHARGE, heavy) | 1.4 | 36.7 | 27.6 | 13.11 | **13.10** | 4 | tell 26 t |
| Wingbeat (GUST r10 x0.6) | 0.6 | 15.7 | 11.8 | 17.05 | **3.76** | 14 | big knockback over the void |
| Pearl Barrage bolt (x6 fan) | 1 | 26.2 | 19.7 | 15.08 | **7.81** | 7 | tell 14 t |
| Storm Eye mark (RAIN x7, heavy) | 1 | 26.2 | 19.7 | 15.08 | **7.81** | 7 | tell 42 t |

- Melee alone (a swing every 40 ticks, no telegraph): **3.90 damage/s**; player dies on hit 7 = **~12.0 s** standing in reach
- In that time the player lands ~28 swings = 541 damage = **21.5% of the Lord's health**; to finish, the player must avoid ~78% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 65 s needed: 254 = 5.1 x the player's health pool.

**B3 · Mirror Knight, B-rank gate (lv 57) · diamond sword vs its diamond sword (+6) and diamond chest+helm (+11 armour, 4 toughness)**

- Lord: HP 2,215.5, ATTACK_DAMAGE 57.73, armour 15.75, toughness 4
- Player (level 57, 168 points): swing 19.25 raw, attack speed 2.080/s (full charge every 10 ticks; i-frame floor 10), crit 7.2% x1.6, VIT resist 25.0%, max HP 50.0
- Lord armour lets 12.06 through (effective armour 9.33 -> 37.3% off); expected per swing with crits 12.58
- **Hits to kill 177; time to kill 88.5 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| basic melee / Perfect Parry riposte | 1 | 57.7 | 43.3 | 9.18 | **27.40** | 2 | riposte = its melee |
| Mirror Lunge (CHARGE) | 1.4 | 80.8 | 60.6 | 4.85 | **48.86** | 2 | tell 18 t |
| Shatter bolt (NOVA x18, P3, heavy) | 1 | 57.7 | 43.3 | 9.18 | **27.40** | 2 | tell 32 t |

- Melee alone (a swing every 20 ticks, no telegraph): **27.40 damage/s**; player dies on hit 2 = **~1.0 s** standing in reach
- In that time the player lands ~4 swings = 50 damage = **2.3% of the Lord's health**; to finish, the player must avoid ~98% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 88 s needed: 2,425 = 48.5 x the player's health pool.

**T · the B Reassessment duel (lv 45, 5-minute limit, trials never kill) · Gearwright Colossus**

- Lord: HP 2,082.5, ATTACK_DAMAGE 22.40, armour 3.75, toughness 0
- Player (level 45, 132 points): swing 16.45 raw, attack speed 1.984/s (full charge every 11 ticks; i-frame floor 10), crit 5.6% x1.6, VIT resist 20.0%, max HP 44.0
- Lord armour lets 15.96 through (effective armour 0.75 -> 3.0% off); expected per swing with crits 16.49
- **Hits to kill 127; time to kill 69.8 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| basic melee | 1 | 22.4 | 17.9 | 15.52 | **6.80** | 7 | every 20 ticks |
| Piston Slam | 1.5 | 33.6 | 26.9 | 13.28 | **12.60** | 4 | tell 30 t |
| Overload Arc | 2.2 | 49.3 | 39.4 | 10.14 | **23.43** | 2 | tell 38 t |

- Melee alone (a swing every 20 ticks, no telegraph): **6.80 damage/s**; player dies on hit 7 = **~6.0 s** standing in reach
- In that time the player lands ~13 swings = 210 damage = **10.1% of the Lord's health**; to finish, the player must avoid ~90% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 70 s needed: 475 = 10.8 x the player's health pool.

**B1+ · same B Colossus, sword with Sharpness V (+3) and temper II (+2), Trophy Hall +10% (10 bosses)**

- Lord: HP 2,510.9, ATTACK_DAMAGE 26.24, armour 4.75, toughness 0
- Player (level 57, 168 points): swing 34.20 raw, attack speed 2.080/s (full charge every 10 ticks; i-frame floor 10), crit 7.2% x1.6, VIT resist 25.0%, max HP 50.0
- Lord armour lets 32.90 through (effective armour 0.95 -> 3.8% off); expected per swing with crits 34.32
- **Hits to kill 74; time to kill 37.0 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| basic melee | 1 | 26.2 | 19.7 | 15.08 | **7.81** | 7 |  |

- Melee alone (a swing every 20 ticks, no telegraph): **7.81 damage/s**; player dies on hit 7 = **~6.0 s** standing in reach
- In that time the player lands ~14 swings = 481 damage = **19.1% of the Lord's health**; to finish, the player must avoid ~81% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 37 s needed: 289 = 5.8 x the player's health pool.

**B1n · same B Colossus, netherite sword (+7) and netherite armour (20 / 12)**

- Lord: HP 2,510.9, ATTACK_DAMAGE 26.24, armour 4.75, toughness 0
- Player (level 57, 168 points): swing 22.00 raw, attack speed 2.080/s (full charge every 10 ticks; i-frame floor 10), crit 7.2% x1.6, VIT resist 25.0%, max HP 50.0
- Lord armour lets 21.16 through (effective armour 0.95 -> 3.8% off); expected per swing with crits 22.08
- **Hits to kill 114; time to kill 57.0 s** of uninterrupted full-charge swings (+1.5 s x2 phase shields if swung into)

| Lord attack | x ATTACK_DAMAGE | raw | after VIT | player eff. armour | damage taken | hits from full HP to die | note |
|---|---|---|---|---|---|---|---|
| basic melee | 1 | 26.2 | 19.7 | 16.06 | **7.03** | 8 |  |
| Overload Arc | 2.2 | 57.7 | 43.3 | 11.34 | **23.66** | 3 |  |

- Melee alone (a swing every 20 ticks, no telegraph): **7.03 damage/s**; player dies on hit 8 = **~7.0 s** standing in reach
- In that time the player lands ~16 swings = 353 damage = **14.1% of the Lord's health**; to finish, the player must avoid ~86% of the melee alone (before any ability).
- Damage the Lord's melee would deal over the 57 s needed: 401 = 8.0 x the player's health pool.


### 4.4 What could make it better or worse than this (sensitivity, mostly ASSUMED vanilla)

- **Protection IV on all four pieces** (vanilla: 16 EPF → −64% after armour) would cut the D1 melee to ~1.46 a hit:
  20 hits ≈ 19 s to die, ~33 swings ≈ 32% of the Lord per life. Still a third of the job.
- **The vanilla shield**, if it blocks as in 1.21.5+, cancels most of it (frontal, any non-`bypasses_shield` type), but
  then you aren't swinging; the Ember Tyrant's axe disables it.
- **Natural regeneration** at full hunger heals up to 1 HP every 10 ticks while saturation lasts; the gate modifier No
  Regen cancels heals ≤ 1 (`gates/run/Modifiers.java:111-115`).
- **Glass** modifier doubles damage both ways (`gates/run/Modifiers.java:127, 148`); Hard difficulty ×1.5 on the melee.
- **Powers**: the player-side audit covers them; note that a boss takes at most 20% of its health per power hit.
- **Party**: each extra player +50% Lord health (`gates/run/Rooms.java:63`), so each player's share is 75% of a solo Lord with two, 63% with four.
- **Fracture** after a death: −15% melee for 8 minutes (`progression/Progression.java:293-294`, `gates/run/Runs.java:62`).

---------------------------------------------------------------------------------------------------------------------

## 5. Every item a player can fight with (VERIFIED by grep for `.sword(`, `.axe(`, `DataComponents.WEAPON`, `BASE_ATTACK_DAMAGE_ID`, `extends SwordItem/BowItem/CrossbowItem/TridentItem/MaceItem`, `BlocksAttacks`)

| Item | Count | Stats | Abilities | Source |
|---|---|---|---|---|
| Hunter blades (Wildhide Fang, Beastbound Cleaver, Gatewrought Sabre, Veilwoven Edge, Lordscale Warblade, Sovereign Oathblade) | 6 | 5/6/7/8/9/10 attack, 1.8/1.7/1.7/1.7/1.7/1.8 per second; = stone/iron/diamond/netherite swords for E-B (test-enforced) | none | crafted (crafting table, level road 3/13/22/42/67/97) |
| Hunter foci (Charm, Totem, Lens, Censer, Orb, Sceptre) | 6 | club 3-8 melee at 1.2/s; +6-22% power damage in either hand; energy back 0.5-3 per power hit (once a second) | the power bonus | crafted |
| Hunter mattocks | 6 | 3-8 melee at 1.1/s; mines like stone…netherite picks | none | crafted |
| Vanilla weapons | all | unchanged; temperable (+1/level, max VI); gate loot tables never contain them; the landmark outer chest can hold an iron sword, a bow or a shield; the Sky happening's wreck barrel can hold a half-broken iron sword (25%) | vanilla enchants | vanilla, `src/main/resources/data/awaken/loot_table/chests/landmark_outer.json`, `living/pulse/Skies.java:92-96` |
| Power Cores | – | not a weapon; teaches a power | – | drops |

Code refs: `gear/GearItems.java:30-106`, `gear/HunterTier.java:31-44`, `gear/GearTests.java:293-302`,
`hearth/Upgrades.java:212-230, 287-296`. No other module registers an item with attack damage (`ShadowSoldier` and
Echoes are mobs). **Mobs never drop the weapons they hold** (`mobs/entity/AwakenMob.java:132`). So vanilla weapons
remain the player's main melee path until Hunter blades, and the blades are vanilla swords by design.

---------------------------------------------------------------------------------------------------------------------

## 6. Drops, rarity and discovery today

### 6.1 What drops

| Source | What | Evidence |
|---|---|---|
| Every creature | Loot table `awaken:entities/<id>`: themed vanilla drops, parts, essence via `awaken:mob_level_count` (floor(base + perLevel·lv + roll)). Gate Lord essence 6 + 0.4·lv (8-9 at E, 53-54 at S). | `mobs/MobLoot.java:61-95`; `src/main/resources/data/awaken/loot_table/entities/bell_warden.json`; `docs/modules/mobs.md` "Drops" |
| Boss kill (mobs) | + Lord's Heart (Lords), + Power Core: Lord 40%, guardian 50%, Sovereign 75%, final 100%, nightmare 15%; pity if the killer's last 3 bosses gave none (`BossRecord`, copyOnDeath). | `mobs/MobLoot.java:110-143` |
| Boss phase change | One part knocked loose per phase, once per boss (kept through resets). | `mobs/entity/AwakenBoss.java:88-91`; `mobs/MobLoot.java:149-158` |
| Elite / Warden | Power Core 6% / 15% (×1.5 after 20 min without a new thing); in a gate also `Loot.elite` (15% core). | `mobs/MobLoot.java:160-176`; `gates/run/Loot.java:77-86` |
| Gate cache / hoard / Treasure Gate / break Lord | rank table rolls, shards, cores 5% / 50% / 35% / as hoard; break Lord +2 rolls of the rank above. | `gates/run/Loot.java:65-119` |
| Power Core content | Power weighted 3× if unlocked at the drop's rank, variant: standard half the time. Item: one `awaken:power_core` with component `{power, variant}`, model select per power, emissive glow per power. | `powers/PowerRegistry.java:55-80`; `powers/content/PowerContent.java:57`; `docs/SPEC.md:181-189` |

### 6.2 How a rare drop is presented

- **Light pillar** over the item (white-gold core, violet epic, blue rare), only for drops of player kills
  (`living/combat/LootPillars.java:24-40`, `living/client/LivingFx.java:626-639`). No sound, no toast, no beam into the sky.
- **Inside a gate** the pickup goes silently into the Satchel; only a Power Core gets a toast "+ <name> (in your
  Satchel)" (`gates/run/Runs.java:665-675`). The Lord's hoard gets a line in the "Gate cleared" window
  ("Claimed: the Satchel and the hoard", `gates/run/Runs.java:503-510`), not a reveal of what is in it.
- **Boss kill**: `boss_defeated` sound, toast "<Lord> falls." (`mobs/MobLoot.java:182-197`), Day's End best moment,
  `markNewThing("trophy:<id>")` on a first kill.
- **Using a Power Core**: tooltip "Use to learn" (`powers/content/PowerCoreItem.java:57`).

### 6.3 Discovery systems a weapon codex could reuse

| System | Shape | Evidence |
|---|---|---|
| `Progression.discover(player, key, fractionOfLevel)` | one-shot counter `awaken:discovered/<key>` + `awaken:discoveries` + optional XP | `progression/Progression.java:198-205` |
| Codex "N / ??" sections | `StatusSections.add(Tab.CODEX, section)` reading `awaken:discovered/<kind>/` counters, naming only what you've seen (the Almanac does exactly this for happenings) | `living/client/AlmanacSection.java:1-30`; `progression/client/CodexEntries.java:7-29` |
| Novelty rhythm | `Progression.markNewThing(player, what)` resets the Director's "minutes since a new thing" (which already biases elite drops to cores after 20 min) | `progression/Progression.java:545-556`; `mobs/MobLoot.java:170`; `docs/SPEC.md:79-82` |
| Truth / Title / Latent conditions | data-driven `counter_at_least`, `streak_at_least`, `event`, `all/any` with perk rewards; Latents are hidden and announced by a Notice | `docs/SPEC.md:131-145`; `src/main/resources/data/awaken/awaken/{truth,title,latent}/` |
| Per-item data components | `awaken:temper` (hearth), `CORE_DATA` (powers), Smithing's Signature name | `hearth/Upgrades.java:287-296`; `powers/content/PowerContent.java:57`; `living/craft/Craft.java:46` |
| Boss Insight per player | `Profile.insight(bossId)` | `progression/Profile.java:56, 85` |

Constraint to remember: the **patent rule** (`docs/SPEC.md:13-16`): something done to one hostile NPC never changes a
*different* hostile NPC's stats. Weapon effects on the struck target, the player and world state are fine; "kill this
to weaken that" is not. Performance: ≤ 60 active combat entities, no per-tick allocation (`docs/SPEC.md:18-19`).

---------------------------------------------------------------------------------------------------------------------

## 7. VERIFIED vs SUSPECTED, in one list

**VERIFIED (code, data or tests)**
- SPEC §2 mob scaling and role multipliers exactly as specified (`mobs/MobScaling.java`, `mobs/MobsTests.java:46-66`).
- Lords: 28-34 base HP, 4-5 base damage, no base armour; boss KB resistance 1.0, not pushable.
- Ground Lords use vanilla `MeleeAttackGoal` with no telegraph; fliers dive every 30-49 ticks.
- Ability wind-ups 14-58 ticks, heavy +8, aim lock in the last 6 ticks, boss global gap 14 ticks, cooldown + 0-19.
- Life-layer `windup` is set only from the ability telegraph (`AwMobRenderer.java:90`).
- No player-driven interrupt of a wind-up; Stunned doesn't stop abilities.
- Phase roar 30 ticks invulnerable; arena reset after 600 lonely ticks.
- Diamond sword = C Hunter blade = 7 attack (`GearTests.java:293-296`).
- STR/AGI/VIT/PER numbers as in section 1.5; PER crit only when vanilla didn't crit; VIT applied before armour.
- Satchel captures every pickup and is view-only; death costs the Satchel + 8 min Fracture (−15% melee).
- No damage-type tags in the mod; status riders apply even when the hit is blocked.
- No 3D item models; 18 combat items, all crafted; mobs never drop held weapons.
- Loot pillar tag file missing (empty tag); Lord parts get no pillar.
- Pacing sim doesn't model fights (`tools/sim/pacing_sim.py:193-197, 238`).

**SUSPECTED / ASSUMED (confirm with a GameTest before relying on it)**
1. Held weapons add their attack damage to Lords and are multiplied by the scaling (Ember Tyrant 37.6 at D instead of 13.4).
   Test: spawn `awaken:ember_tyrant` with `MobHooks.spawnBoss(..., 17, "")`, wait one tick, assert ATTACK_DAMAGE.
2. Vanilla armour formula, `MeleeAttackGoal` 20-tick interval, 10-tick i-frame window (cap of 2 hits/s on one target),
   melee reach box (~0.83 blocks beyond the hitbox) are vanilla 1.21 values, unverified against 26.3 here.
3. The vanilla shield fully blocks frontal boss attacks (including rain and bursts) because no attack bypasses it.
4. Evoker-fang attacks bypass armour and shields (vanilla `indirect_magic`).
5. Two Power Core rolls, two pity timers and two Lord's Hearts per gate Lord kill.
6. Insight counted twice per gate-Lord death.
7. The first real Lord a story player meets is ~3× the "about 190 health" first-gate target.
8. Gate pressure waves join a slow Lord fight after 4 minutes on the boss floor.

---------------------------------------------------------------------------------------------------------------------

## 8. Suggested GameTests to settle the SUSPECTED items (none were run: read-only audit)

1. `combat_lord_weapon_stacking`: spawn `awaken:ember_tyrant`, `awaken:drowned_archivist`, `awaken:mirror_knight` with
   `MobHooks.spawnBoss(level, id, pos, 17, "")`, tick once, assert ATTACK_DAMAGE against 5·1.68·1.6 = 13.44 (designed) vs
   37.63 (suspected), and ARMOR on the Mirror Knight (1.42 vs 12.42).
2. `combat_lord_melee_interval`: a Colossus at lv 17 and a still player in diamond; count hits over 200 ticks
   (expect 10 if the 20-tick vanilla interval holds) and the damage of one (expect 4.06 with VIT 19).
3. `combat_shield_vs_abilities`: a player blocking with a vanilla shield facing a Bell Warden; force a Toll Wave and a
   rain mark; assert no damage (and whether the status rider still applies, `Abilities.java:76-79`).
4. `combat_player_iframe_cap`: a player at AGI +60% swinging every tick of full charge at one Lord; assert landed hits
   per second ≤ 2.
5. `mobs_insight_once`: a Lord kills a player on a gate boss floor; assert Insight +1, not +2.
6. `mobs_gate_lord_drops`: kill a gate Lord by a player; count Lord's Hearts and Power Core rolls reaching the player.

## 9. What this means for the planning document (consequences, not designs)

- The overhaul's first target is the **boss melee**: a telegraphed, parryable, dodgeable basic attack (the AbilityRunner
  and the Ritual's 25-tick tell / 22-tick recovery are the patterns), synced like `DATA_TELEGRAPH` so the life layer and
  any parry window read the same countdown.
- A **player interrupt/stagger** needs a hook in `AbilityRunner` (it has `interrupt()` already) and a poise value per
  boss; today bosses are immune to every form of displacement.
- **Weapon damage must scale with level** the way powers do, or weapon tiers must climb as fast as ×(1+0.075·lv);
  STR alone keeps normals at 3-6 hits only with ~40% of all points in STR and a fresh blade each rank.
- **Bosses need something for weapons to differ on** (armour, toughness, weak points, guard, flight) or every weapon
  collapses to DPS.
- **Satchel rule**: decide whether found weapons can be wielded inside the gate they drop in.
- **Rare-drop moment**: pillars exist for ground drops only; Satchel and hoard loot (where most gate loot goes) has no
  reveal. A weapon reveal belongs on the "Gate cleared" window and on Satchel pickup.
- **Held vanilla weapons on Lords** should be resolved (cosmetic only, or intended) before any balance pass, because
  they swing three Lords' numbers by 2.2-2.8×.
- **The simulator** (`tools/sim/pacing_sim.py`) will need a real fight model (DPS race, dodge rate) if the overhaul's
  numbers are to be tuned there like the rest of SPEC §2.
