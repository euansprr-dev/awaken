# Fights worth winning: bosses, beasts and the Provings (plan, 2 Nov 2026)

Euan, 2 Nov 2026: "All boss fights must be brought to AAA level in terms of animation and fight sequence ... a lot of
boss poison and projectiles you can't parry and you don't actually see a projectile. I don't think you should be able
to parry EVERYTHING, but at least have a projectile to evade with a dash. Right now most projectiles seem like they just
spawn on you and you can't do anything about it. Both for beasts and bosses. And just in general bosses and beasts need
a better system in terms of fighting them so its EXTREMELY SATISFYING. Also make it so rank assessments are now
increasingly more difficult bosses. Also all animations on par with fresh animations."

Later the same day: "sometimes there is server lag when fighting and hits don't hit right away" (relayed by the
orchestrator, who asked for it to be measured and fixed early).

This is a plan. Nothing is built. Evidence: `tools/sim/reach_sim.py` (new, a planning harness), its table in
`docs/fights/reach-2026-11-02.md`, `tools/sim/combat_sim.py` (200 seeds a cell), and the GameTest `fights_tick_cost`
(new, a measurement: `mobs/entity/FightCostTests.java`). The approval page carries the same
plan in short: `docs/fights/page.html`, https://claude.ai/artifact/3YkJKH1GwpQdZx6notb19E

---------------------------------------------------------------------------------------------------------------------

## 1. What you want, and how we'll know

**The job.** The fights are the peak of the game: gate Lords, the 41 kit bosses, named beasts and hunts, and every rank
Reassessment. You want each one to feel like the best action games: you see every blow coming, you have an answer, the
answer you chose pays off, and you win because you read the fight, not because you had more health.

**What fails today** (section 2 has the numbers):
- Your hit's impact, sound and freeze wait for the server's reply, so they come a beat after your blade lands, and any
  server stall during the swing is added in full.
- Most ranged attacks don't travel. They appear under you, or hit at once, with only coloured dust before.
- Many have no class tell, so you can't tell a parry from a dash. Red, unparryable shots look like the parryable ones.
- Nearly every area hit poisons, blinds, slows or lifts you, and some of those land even when your dodge evaded the hit.
- Skill isn't paid: dodging and parrying lose to many Lords, and the approved fix (less damage) lets standing and
  trading win instead.
- Reassessments are a walled 48-block box with waves.
- Boss animation is measured for kinks and stillness, but not for feet, turning, blends or reactions, and nothing since
  the first three bosses has been seen in game.

**What success looks like (the contract).**
- *Your hit lands when your blade does.* The impact, sound, sparks and freeze play on the frame your blade reaches the
  foe, whatever the server is doing; the server still decides the damage.
- *Every hit you take, you could have seen and answered.* Every ranged harm comes from something that leaves the
  attacker and travels, or is drawn on the ground first. An aimed attack stops following you at least half a second
  before it lands, and shows that it stopped. A dash at that moment gets you out of everything except waves, which you
  jump or dash through.
- *Not everything parries.* Each boss has blows to parry and blows only a dash beats, and the colour says which, every
  time, for bosses and beasts alike.
- *Effects are earned.* A boss has one signature effect at most, on its signature moves, and only when the hit lands.
- *Skill pays.* Deflects, perfect dodges and punishes fill the boss's posture; it breaks, falls, and you land a
  finisher. Every Lord fight has that moment two to four times. A deflected shot flies back.
- *Fights have a rhythm.* Bosses fight in phrases of two to five blows with an opening after each, not one blow at a
  time, and don't fill the room with adds.
- *Each Reassessment is a great fight, harder each rank.* One Warden, its own hall, never announced, D to Sovereign,
  each harder than every Lord of its rank and than the Warden before.
- *Animation on Fresh Animations' level*: feet that plant, bodies that turn with weight, moves that flow from walking
  and into each other, and a real reaction to every parry, break and heavy hit.

**Strong, weak, unacceptable.** Strong: you die to a Lord and know exactly which blow and what you should have done.
Weak: shots look nicer but rings still go off under you. Unacceptable: any harm with nothing on screen before it, any
effect through a successful dodge, or a fight a player wins by standing still and trading.

**Not in this plan** (other sessions own them): weapon crafting, weapon items, the Armoury, enchanting, the bells (the
Great Bells come with rank, section 4.7), vanilla mobs' attacks, power casts (SPEC §32), PvP.

**Must not regress:** the dodge and guard timings, the strike classes, Lock-on (§40), CombatFeedback, the kit meters
the 41 bosses pass today, every current test.

---------------------------------------------------------------------------------------------------------------------

## 2. What's there today (evidence)

### 2.1 How attacks reach you

`tools/sim/reach_sim.py` reads every boss move table (`data/awaken/kit/moves`) and every creature ability in the
bestiaries, with the executors' geometry as written in `Abilities.java`, `AbilityRunner.java`, `Hazards.java` and
`AwBolt.java`. A player at 4, 8 or 14 blocks sees the first cue (a class tell, or the first ring of dust), reacts in
250 ms, and dashes with the Longsword's dodge (6 ticks, 2.6 blocks, evading on its first 5 ticks).

| | Bosses (41 move tables) | Every other creature (gate monsters, elites, wardens, named beasts) |
|---|---|---|
| Attacks that hurt at range or put an effect on you | 148 | 247 |
| Something you can see travel from the attacker | **34%** | 40% |
| No class tell at all (the move's class is NONE) | **58 (39%)** | 0 |
| Can't be parried | 53% | 46% |
| ...of them shots that look like the parryable ones | 12 | 0 |
| Carry an effect on a hit | **95%** | 84% |
| Push, pull, lift, slow or darken you even when a dodge evades the hit | **28** | 41 |
| A dash at the first cue gets you out (8 blocks) | 70% | 87% |
| How they reach you | marks under you 26, instant rings 25, rings of shots 20, shots 14, charges 13, instant lines 10, pulls 9, pulses 8, darkness 8, ... | charges 50, instant rings 34, leaps 29, shots 29, marks under you 23, ... |

What the 45 boss attacks a dash at the cue doesn't escape are: instant rings you're inside (11), darkness (8, no hit
at all), pulls (7), bolt fans the dash runs into (6), lifts (4), roots (3), clouds wider than the dash (2), leaps that
follow the dash (2), beams that lock too late (2).

**The lock.** Aimed things (beams, clouds, roots, lifts, leaps, bolts) follow you until they lock. In 22 of the 36
aimed boss attacks the lock comes **5 ticks (0.25 s)** before the hit, and nothing shows it. So a dash when you first
see it is followed; only a dash timed to the hit works. That is the "spawns on you" feeling.

The total warning is not the problem: median 1.35 s from the first cue to the hit, shortest 0.65 s. What's missing is
something to see and a moment to act on.

### 2.2 Root causes

| What you feel | Cause in the code | Evidence | Fix |
|---|---|---|---|
| "It spawns on me" | Area kinds strike at a point with nothing travelling: `rainStrike` lands at marks fixed under you, `area()` hurts a whole ring at once, beams are hitscan, clouds are placed at your feet | `Abilities.java`, `AbilityRunner.rainMarks`; 66% of boss ranged attacks | Carriers (4.1): every ranged harm travels or is drawn first |
| "I can't see the projectile" | Shots are item sprites at 0.9 scale (`ThrownItemRenderer`), the same look for every class | `AwBolt`, `MobTypes.BOLT`; 12 red shots look like white ones | A real shot: glowing core, trail, class colour, its own sound |
| "Can't parry, can't tell" | A move with class NONE shows no class tell (`FightRunner.begin`) but its ability still lands as LIGHT or PERILOUS | 58 of 148 boss attacks | Every damaging move has a class |
| Rings in the boss's theme colour | `AbilityRunner.show()` draws dust in the theme's glow colour | read in code | Class colour on every tell, ring, shot and wave |
| "So much poison" | Every themed area hit carries its theme's `BoltKind` effect | 141 of 148 | One signature effect per boss, on its signature moves |
| Effects through a dodge | `root()`, `lift()`, `pull()`, `gust()` apply the effect or push before the hurt; `darkness()` never hurts at all | `Abilities.java` | Effect and push only with a landed hit |
| Dodging doesn't save you | Aim follows until 5 ticks before the hit; clouds 3.0 wide outlast a 2.6 dash | lock table above | Lock at least 10 ticks before, shown; fields no wider than a dash |
| Skill isn't paid | No boss recoil on a deflect; a deflected bolt just breaks; finishers capped at 8-12% of a Lord's health; posture only from deflects | `ArmsServer.releaseStopped`, `archetypes.json` bossCap, SPEC §25 | 4.4 |
| "Hits don't hit right away" | Your hit's impact, sound and freeze play only on the server's reply; a press waits up to a tick in the server's queue; any stall in the wind-up is added in full | `ArmsPredict.onResult`; 26.3's `processPacketsAndTick`; 2.6 | Stage 0: the impact plays on your screen at once |
| Fights feel like single blows | The fight (R3: phrases) was never built for any boss | `docs/bosses/ROLLOUT.md` Fight column "—"; single-blow phrases 52-100% (`baseline-2026-10-28.md`) | Phrases with openings |

### 2.3 Does skill win today?

`combat_sim.py`, 200 seeds a cell, the 15 gate Lords, with the class, effect and stagger fixes that are now in the code
(`--preset fixes`):

| | Tank (stands and trades) | Dodger | Parrier | Expert |
|---|---|---|---|---|
| Win rate against E-rank Lords | 0% | 8-67% | **0-34%** | 66-98% |
| Lords meeting the approved target | 13/15 (wins ≤10%) | 0/15 (≥60%, 80-120 s) | 1/15 (≥80%) | 10/15 (≥98%) |

The parrier's top killers are the unparryable areas and novas (Spore Rain, Glass Storm, Flood, Void Halo, Storm Eye)
plus burning and HEAVY slams; fights last 40-60 s against a target of 80-120 s.

The approved curve (`--preset recommended-hp`: Lords deal less, have more health) was never applied. Run now, it lifts
the dodger to 11/15 and the parrier to 12/15, but **standing and trading then beats 7 of 15 Lords** more than 10% of the
time (the Abbot of Frost 99%, the Sky Leviathan and the Hollow Herald 96%). Less damage makes skill and no skill both
win. The lever has to be the other one: blows that keep hurting, every blow answerable, and skill paid in posture,
openings and finishers.

### 2.4 Reassessments

`hearth/trial/Trials.java`: D survives three waves, C waves and the Path, B duels a Gate Lord picked by a hash, A runs
five rooms, S fights a mirror at half health, all in a 48-block walled box (`TrialChunkGenerator`, cells 512 apart).
The Provings (GOALS.md part 2, approved with the Journey on 29 Oct) are designed in outline (six Wardens, their halls
and gifts) and not built. The D trial already opens with the Journey's `ringer` Great Fault.

### 2.5 Animation

The kit meters (`kit_bodies.py motion`) check kinks (none over about 5°), held stillness, dips and the floor sweep; all
41 pass on paper. Not measured: planted feet sliding, how a body turns, the seams between moves and walking, the
release frame of a shot, secondary motion, and reactions (there is no parried clip; hurt, kneel, rise and collapse
exist). The bosses' look was judged "derpy/vanilla" at the showcase (28 Oct); the 41 were rebuilt to the Forge's bar
since and haven't been seen in game. Creatures: the remaining FA gaps are listed in `docs/creatures/HANDOVER.md`
(vanilla-bodied fauna unverified under FA).

### 2.6 Hits that land late

**The server's tick in a fight.** `fights_tick_cost` (GameTest, headless server on the M4): a real survival player on
a stone floor while a boss, a pack of gate monsters or both fight it, 200 ticks a case, the first 40 left out, every
tick timed by the server's own measure (the one `/tick query` reads). The busiest case also ran under the server's
section profiler (`run/fights-tick-profile.txt`).

| Case | Mean ms a tick | p95 | Worst | Packets to the player a tick |
|---|---|---|---|---|
| The player alone | 0.64 | 1.18 | 7.3 | 0.1 |
| The Mother of Spores | 1.62 | 2.66 | **91.5** (once) | 3.5 |
| The Signal's Shadow | 0.46 | 1.36 | 2.8 | 0.7 |
| The Drowned Archivist | 0.56 | 1.31 | 11.8 | 2.9 |
| The Gearwright Colossus | 0.56 | 1.32 | 3.0 | 2.1 |
| The Bell Warden | 0.25 | 0.69 | 3.6 | 6.2 |
| 12 gate monsters | 0.96 | 2.58 | 4.5 | 22.3 |
| The Mother of Spores and 12 gate monsters | 1.66 | 3.49 | 6.9 | 24.6 |

A tick's budget is 50 ms. In the busiest case's profile the 13 fighters take 66% of the tick, each 4-7% (the Mother
7%), and chunk polling 9%. Nothing stands out. The one stall, 91 ms, came when the first boss of the session appeared;
the same boss appearing later stayed under 7 ms, so it's a first-use cost (loading), not the fight. **The fight's own
work doesn't lag the server.** What can is everything this test doesn't have: the game's built-in server sharing the
Mac with the client, new chunks being made, a save. I didn't read your play copy's log, which would show "Can't keep
up!" lines if the server stalled during fights.

**The path from your click to the hit** (read in the code and 26.3's sources):

| Step | How long it waits | Where |
|---|---|---|
| The click is read | the next client tick, 0-50 ms (as in vanilla); your swing starts then | `InputEvent.InteractionKeyMappingTriggered` |
| The press reaches the server | the start of the server's next tick, 0-50 ms (26.3 handles queued packets once, at the start of each tick) | `MinecraftServer.processPacketsAndTick` |
| The strike | its wind-up in server ticks. A press still held waits at the hit until you let go (by design: a hold becomes a charge), at most the charge's 6 ticks and 4 more | `ArmsServer.moveTick` |
| The hit's reply reaches your screen | the next frame, 0-17 ms | `Minecraft.runTick` |
| The impact, sound, sparks and freeze | **only when that reply arrives** | `ArmsPredict.onResult` (HIT) |

So on a healthy server your impact plays 0-67 ms after your blade reaches the foe (about 33 on average), and the freeze
catches the blade already past it, which reads as a stutter after the hit. A server stall during the wind-up is added
in full: one 100 ms tick in a 3-tick wind-up makes the hit 150-250 ms late.

**The other way round.** The client plays a boss's swing on its own clock, which keeps running at 20 ticks a second
while the server falls behind, then snaps back when the server's time arrives (every 20 server ticks). During a stall,
a boss blow on screen lands before its damage does, and a parry timed to the picture comes early.

**Not the cause:**
- The bosses' delayed blows: 58 moves hold, all HEAVY, all at most 12 ticks (0.6 s), as SPEC §35 says.
- The latency grace on blows aimed at you: zero on a near connection, so zero in single player (up to 3 ticks on a
  remote server, by design, so a guard raised in time still meets the blow).
- The packet cap (§28.9): it holds packets only when the server's queue takes more than 50 ms in one tick. It never
  fired in the test, and it logs `[packetcap]` when it does.

### 2.7 What we keep

The four strike classes (`core.api.Strikes`: LIGHT, HEAVY, PERILOUS, GRAB) and their tells and sounds (`ArmsTells`);
dodge, guard, deflect, block, Riposte, Slipcut, Resonance and the Art; posture with its strip on the plates; the kit
(move tables, fight scripts with strings and links, `FightRunner`, `KitRenderer`); hit-stop and camera kick (on by
default); Lock-on; `Strikes.deliver`. The plan fixes how the bosses use them, not the player's verbs.

---------------------------------------------------------------------------------------------------------------------

## 3. Three ways, and the pick

| | What it is | Gains | Loses |
|---|---|---|---|
| **A. Tune** | Longer locks, a class on every move, effects only on a hit | A day's work; fewer cheap hits | Marks still go off under you; nothing comes at you; no payoff for skill |
| **B. One fight language (recommended)** | Every ranged harm through five visible carriers, one colour-and-sound language for classes, phrases with openings, posture paid out in breaks and finishers, the Provings on the same rules | Fixes each cause in 2.2 with the parts that exist; every boss and beast at once | Every move table and executor is touched; a few days per stage |
| **C. A new combat engine** | Behaviour trees per boss, a new animation runtime | Unlimited | Throws away the kit and 41 finished bodies; weeks before anything is playable |

B, because the causes are in how attacks are delivered and paid, not in the engine. The pieces that make a Sekiro or
Elden Ring fight (classes, tells, posture, breaks, finishers, a dodge with i-frames) already exist here; the bosses just
don't use them. What would change my mind: if the three pilot bosses in stage 2 still don't feel right with the
language in, the problem is deeper (the bodies or the AI), and we'd look at C for the Wardens only.

---------------------------------------------------------------------------------------------------------------------

## 4. The design

### 4.0 Hits land now (stage 0)

- **Your hit plays on your screen at once.** On the frame your blade reaches a foe, the client checks the same reach and
  arc the server uses (one rule in `ArmsRules`, shared by both sides) and plays the impact, sound, sparks, freeze and
  the foe's flinch right then. The server still decides the damage. Its reply adds only what the client can't know (a
  kill, a break, a cleave's other bodies) and never plays the impact twice. If the server finds the foe out of reach
  after all (it moved in the last tick), that blow shows with no damage. That's rare, it's counted, and a small margin
  on the client's reach keeps it under 1 in 50.
- **The server keeps up, and says when it doesn't.** While a fight is on, any tick over 50 ms logs one line with its
  length and what took it (the fight's own parts, timed; chunks; saving), so the next play session names the stall
  instead of us guessing. The first-boss stall is found and loaded at start.
- **Boss swings follow the server's real pace.** The client times the server from the time it sends every 20 ticks and
  plays boss moves at that pace, so a blow you see land is a blow that lands, and a parry keeps its timing in a stall.
- **The fight stays cheap.** `fights_tick_cost` becomes a budget: a boss and 12 monsters under 5 ms a tick on average
  (three times that on CI's slower runners), so no later change makes fights the cause.

### 4.1 Five carriers: everything that hurts at range travels, or is drawn first

| Carrier | Replaces | What you see | How you answer | Numbers |
|---|---|---|---|---|
| **Shot** | bolts, arrows, rings of shots | A glowing core with a trail, in the class colour, leaving the hand or mouth on the release frame, humming as it flies | White-gold: parry, block or dash. Amber: parry or dash. Red: dash or put cover between | Speed 0.9 (white), 0.7 (amber), 0.6 (red) blocks a tick; no homing; visible at 48 blocks |
| **Volley** | marks under you (rain) | Things thrown up from the body (you see them rise), then a ring on the ground where each will land: where you stood at the throw, never moving | Dash out of the ring | Ring ≤ 2.2 blocks (a dash is 2.6); 0.7-1.0 s from ring to impact |
| **Wave** | instant rings, slams' reach, auras' pulses, gusts, darkness | A ring that leaves the boss's feet and rolls outward along the ground, about a block tall | Jump it, dash through it, or stand inside its safe ring | 0.45-0.7 blocks a tick; at 8 blocks you see it coming 0.6-0.9 s |
| **Line** | beams, cones, fangs, roots | A beam that aims, then **locks with a flash and a sound** before it fires; or roots and fangs racing from the boss along the ground | Step off the line after the lock | Lock ≥ 10 ticks (0.5 s) before firing; ground lines 0.6-0.8 blocks a tick |
| **Body** | charges, leaps, pulls, grabs | The boss itself; a leap shows its landing ring when it locks; a pull is a visible draw of wind or water into the boss | Sidestep; leave the landing ring; dash against the pull (a dash outruns it); grabs are violet: dash only | Leap lock ≥ 10 ticks before landing; pull slower than a dash, and no harm by itself |

A **field** (a cloud, a burning patch, an aura) is what a volley or wave can leave behind: a visible edge, no harm for
the first half second, never put down on you directly, small enough to walk out of.

The rule (invariants I2-I4, section 4.9): an aimed carrier follows you until its lock, at least 10 ticks before it can
land, and the lock shows. After the lock, a dash gets you out of everything but a wave, which you jump or dash through.

### 4.2 One tell language

- **Colour is the class**, on the boss's tell flare, the shot's core, the ring's edge, the wave's crest and the lock
  flash: white-gold = parry, block or dash; amber = parry or dash (it breaks a block); red = dash or jump only;
  violet = a grab, dash only. The theme's colour stays on the body, smoke and sparks. These are the `ArmsTells` colours
  the player already knows from melee.
- **Sound**: the class's tell sound at the wind-up (exists), a lock sound for aimed things, and the carrier's own sound
  while it travels, playing from the carrier in mono (as the bells now do), so you hear where it is.
- **Always told**: no move that hurts has class NONE (58 boss attacks today).
- **Not everything parries.** Each boss: at least 40% of its blows white-gold or amber, at least 20% red, the rest
  waves and fields you move out of.

### 4.3 Effects

- A boss has **one signature effect at most** (the Mother's poison, the Abbot's chill), on at most two moves.
- An effect, push, pull, lift or slow lands **only with a hit that landed**. A dodge or a deflect stops all of it.
- Blindness and darkness only from a signature hit, never from an area with no hit. Burning only from a direct hit,
  for one second (the length already approved in the sim's `recommended` preset).
- Creatures keep their themed effects on their own attacks under the same rule (only with a landed hit).

### 4.4 The duel: rhythm, payoff, feel

- **Phrases.** Each boss's fight script becomes 4-7 phrases a phase, of 2-5 blows. Single blows at most 30% of what it
  starts (52-100% today). Ranged phrases when you're far, melee when you're close; it answers your habits: after two
  deflects, a red blow; when you back off, a gap closer. Delayed blows (the existing HEAVY hold) catch panic dodges.
- **Openings.** Every phrase ends with an opening of 0.8-1.5 s (longer after longer phrases) where the boss starts
  nothing. That's when you punish.
- **Parry pays.** A deflect makes the boss's limb recoil (a new parried clip on every boss), sparks in the class colour,
  a clang and a 2-3 tick hit-stop. Deflect the last blow of a phrase and the boss staggers. A deflected white-gold shot
  **flies back** at the boss and fills its posture; an amber one breaks on the blade.
- **Dodge pays.** A perfect dodge through a blow fills the boss's posture a little, and the Slipcut opens (exists).
- **Posture breaks, the boss falls, you finish it.** Deflects, perfect dodges and hits in openings fill the strip under
  its health. Full, it drops to a knee for 2 s (the existing broken pose) and your finisher lands with its own
  animation, camera and sound, worth up to **15% of a Lord's health** (8-12% today). A Lord breaks two to four times a
  fight. Posture drains when you back off, so pressure is rewarded.
- **Weight.** Bosses don't flinch from every light hit during a wind-up (as now); they flinch from heavy hits, deflects
  and hits in openings. Hits sound like what they strike (bronze, glass, bark, flesh). Hit-stop and camera kick stay on.
- **No add spam.** At most one summon move per boss, only where the adds are the boss's idea (the Hive Regent), at most
  two alive, never in the first phase.

### 4.5 Difficulty

Keep blows that hurt enough that standing and trading loses. Don't apply the lower-damage curve (2.3). Instead, once the
carriers and payoff are in, the sim learns them (a told blow can be dashed at the lock; posture fills from deflects and
perfect dodges; openings after phrases; finishers at 15%) and health is tuned per Lord to the approved targets: tank
wins ≤10%, dodger ≥60%, parrier ≥80%, expert ≥98% with half health left, fights 80-120 s. A Lord that misses its target
doesn't ship.

### 4.6 Beasts

- Named beasts, hunt quarries, gate monsters, elites and wardens all attack through the same `AbilityRunner`, so the
  carriers, colours, lock and effect rule reach all 247 of their attacks in stage 1 with no extra work per beast.
- Big beasts (named beasts and hunt quarries) **topple** when their posture breaks: down on their side for 2-3 s, the
  opening (Monster Hunter's topple), with a topple clip.
- Pack hunters and the Gnawed bite with vanilla melee and no tell today. They get a short told lunge (a crouch, a growl,
  white-gold, parryable), and at most two of a pack strike at once.
- Herds' trample and stampede are already visible; they stay.

### 4.7 The Provings: each Reassessment one great fight, harder each rank

The six Wardens from GOALS.md part 2 (approved), built on this plan's rules and on a ladder that makes each harder:

| Rank | Warden | Hall | Phases | Blows a phrase | White-gold tell | Posture | Sim target (dodger / parrier / expert / tank) | Length |
|---|---|---|---|---|---|---|---|---|
| D · 10 | The Ringer | The Drowned Belfry | 2 | 2-3 | 0.8 s | 120 | 75% / 80% / 99% / ≤10% | 4-5 min |
| C · 25 | The Examiner | The Hall of Reflections | 3 | 3-4 | 0.7 s | 150 | 60 / 65 / 97 / ≤5 | 5-6 min |
| B · 45 | The Unmade | The Broken Span | 3 | 3-4 | 0.65 s | 180 | 50 / 55 / 95 / 0 | 6 min |
| A · 70 | The Sky Warden | The cathedral breaking above the clouds | 3 | 4-5 | 0.6 s | 200 | 40 / 45 / 92 / 0 | 6-7 min |
| S · 100 | The Keeper's Shadow | The Court's threshold | 4 | 4-5 | 0.55 s | 220 | 30 / 35 / 88 / 0 | 7 min |
| Sovereign · 135 | The Last Loop | The Cradle's antechamber | 4 | 4-6 | 0.5 s | 250 | 20 / 25 / 85 / 0 | 8 min |

- Red blows stay told at least 0.6 s at every rank: the dash rule never gets tighter than a reaction. The Wardens get
  harder by longer phrases, more mix-ups, more phases, harder posture and changing halls, not by cheap hits.
- Each Warden must test harder than every gate Lord of its rank and than the Warden before (sim, same profiles).
- **Halls**, 100-200 blocks across, built for one fight in the trials dimension (cells are 512 apart, so they fit), lit
  with Lens, changing with the phases as GOALS says (the water drains, a floor gives way, the roof opens).
- **Never announced.** Nothing names the Warden, its hall or its gift before you meet it (approved).
- **Fair.** A way out in every hall; fall and you wake at its door, free to go again now; after three falls the Signal
  says plainly what killed you (approved). A party at the level takes it together, and it scales.
- **The reward**, a surprise each time: the rank, the gift the Journey moved into the fight (the Tether at D, your Path at
  C, the Unmade as your first General at B, Skyborn at A, the Court's doors at S, the Seed's door at Sovereign), and the
  Warden's trophy for your Hall. (Decision 8 asks whether to add a fighting technique from each Warden.)
- **The Great Bells** stay with the rank (`Bells.java` rings Tenor at D up to Bourdon at Sovereign). A Proving gives
  the rank, so its bell follows with no new drop call. The Ringer's "Tenor hangs free above the pit" is a scene in the
  hall; the bells session is told.
- Each rank's box trial stays until its Warden passes your look in game, then it goes.

### 4.8 Animation to Fresh Animations' level

New meters next to the existing ones, so "on par with FA" is measured, and your eye on the pilots in game as the bar:

1. **Feet**: a planted foot slides at most 0.03 blocks a tick, in moves and in turns.
2. **Turning**: the head leads, then chest, then hips; no yaw jump over 12° a tick; the feet step when the body turns
   more than 45°. Bosses turn during their wind-ups (tracking you) with the same lead.
3. **Seams**: every move starts from what the body was doing (walk, run, idle, another move) and ends into it, with no
   kink at the seams.
4. **Release**: a shot or volley leaves on the hand's release key, from the hand (within a tick and 0.3 blocks).
5. **Reactions**: every boss gets parried, staggered, knocked down and getting up, on top of its directional hurt;
   every big beast a topple.
6. **Follow-through**: cloth, chains, tails and hair lag the body by 2-6 ticks and settle (FA's hallmark), with a lag
   meter.

Fresh Animations is the reference we watch beside ours (as the creature batches did); none of its files come into the
repo.

### 4.9 Invariants (enforced by tests)

- I1. Every move that hurts has a class; never NONE.
- I2. Every ranged harm comes through a carrier that players' clients see from its release until it lands or ends.
- I3. An aimed carrier stops following at least 10 ticks before it can land, and shows its lock.
- I4. A dash at the lock gets out of every carrier but a wave; a wave can be jumped or dashed through.
- I5. No effect, push, pull, lift or slow without a landed hit; none through an evade or a deflect.
- I6. Per boss: at most one signature effect, one summon move, two adds alive.
- I7. Per boss: at least 40% of blows parryable (white-gold or amber), at least 20% red.
- I8. Per boss: single-blow phrases at most 30% of what it starts; an opening after every phrase.
- I9. Your own strike's impact never waits for the server; the server's word is final on damage.
- I10. A boss and 12 monsters cost the server under 5 ms a tick on average (headless, M4).

### 4.10 What can go wrong

| Case | Handling | Test |
|---|---|---|
| Lag: a shot drawn where it isn't | Boss carriers sync every 2 ticks (bolts 10 today); hits are judged on the server along the carrier's path | carrier test checks the client sees it each tick of its flight |
| Uneven ground | Waves follow the ground; a wall stops one; it ends over a drop; a wave can't climb more than a block | wave GameTest on a step |
| Several players | Each carrier aims at one player and hurts whoever is in its path; a volley puts one ring per player (up to 4) | dash test runs with two players |
| Cover | Shots and lines stop on blocks: hiding is an answer | — |
| Many carriers | At most 24 alive per boss; each drawn as one mesh, not a particle storm | perf check on the Mac's bench before stage 4 ends |
| Update mid-fight | Fight state isn't saved; a boss restarts its phrase; an old box trial in progress runs to its end | — |
| A new ability added later with no carrier | The language test fails the build | I1-I8 test |

---------------------------------------------------------------------------------------------------------------------

## 5. Before and after

**The Mother of Spores** (E, the poison complaint). *Today*: all four of her ranged moves poison. Spore Rain (red,
told) drops marks under you at the start of the wind-up and strikes 1.25 s later with nothing falling. Spore Bloom is
an instant ring you can't dash out of from 8 blocks (safe only inside 4 blocks). Her halo of shots looks like the
parryable kind. *After*: she shakes pods up from her cap (you see three rise), rings appear where you stood, they fall
0.8 s later; you dash out. The Bloom becomes a spore wave rolling out from her: jump it or dash through. Poison only if
the Bloom wave catches you. Her shots glow white-gold; deflect one and it flies back into her.

**The Drowned Archivist** (D, untold, effects through a dodge). *Today*: Undertow and Ink Cloud have no class tell and
land 0.75 s after the first dust. Undertow drags you in from 12 blocks whether or not you dodge (the pull comes before
the hit), and blinds you if it hits. The cloud lands on you, 3 blocks wide (wider than a dash), and pulses for 5 s. *After*: Undertow is red; the water visibly
runs toward her, slower than a dash, so you dash away; then her wave rolls out. Ink Cloud: she spits an ink globe you
watch fly; it bursts where it lands into a 2-block pool you walk out of; blindness only if the globe itself hits you.

**The Signal's Shadow** (the final boss, under the Seed). *Today*: 5 of its 6 ranged moves have no class tell; Instruction, a beam, locks 0.3 s
before it fires. *After*: red tell, the beam locks with a flash and a sound 0.5 s before; dash after the flash.

**The Bell Warden's Echo Bolts** (E, the good case, kept). *Today*: told, white-gold, real bolts; a dash clears the fan.
*After*: bronze glows that hum; deflect one on the beat and it rings back into the Warden's posture; three back and it
staggers.

**The Briar Sow** (a named beast). *Today*: roots under you slow you before the hit lands. *After*: roots race along the
ground from it; you sidestep; slowed only if they catch you.

**Rank D** (the box). *Today*: survive three waves in a walled 48-block box. *After*: the Reading Bell doesn't stop
ringing, you're knee-deep in the Drowned Belfry, and the Ringer drops into the light.

**The flow, today and after:**

```
Today:  move picked -> tell only if its class isn't NONE -> wind-up (theme dust; aim follows you)
        -> lock 5 ticks before -> strike lands at a point / ring / line at once -> effect (sometimes before the hit)
After:  phrase picked -> move, always classed -> tell (class colour + sound) -> aim follows
        -> LOCK (flash + sound, >= 10 ticks before) -> released on the hand's frame -> carrier travels (seen, heard)
        -> reaches you: dodge (evade) / deflect (recoil; shot flies back) / block / hit (+ effect only now)
        -> posture fills -> break -> boss falls -> finisher
        -> phrase ends -> opening (0.8-1.5 s)
```

---------------------------------------------------------------------------------------------------------------------

## 6. Tests, gates and stages

**Tests** (one per mechanism; local runs filtered, the full suite on CI):
- `arms_hit_now`: the client's sweep and the server's agree on a grid of positions and aims (the shared rule), and a
  predicted hit's impact isn't played again by the server's reply.
- `fights_tick_cost`: the measurement above, with the I10 budget.
- `fights_language`: walks all 41 move tables and every bestiary ability for I1, I3, I6, I7, I8.
- `fights_carriers_seen`: each carrier is tracked by a nearby player from release to its end.
- `fights_dash_escapes`: a test player dashes at the lock from a volley, a locked line and a leap, and is unhurt; a
  wave is jumped.
- `fights_effect_needs_hit`: an evaded root, pull and cloud leave no effect.
- `fights_shot_returns`: a deflected white-gold shot flies back and fills the boss's posture.
- `fights_break_finisher`: a break drops the boss and a finisher is capped at 15%.
- `fights_phrase_opening`: a phrase ends with an opening where the boss starts nothing.
- `proving_<warden>`: the hall builds, a win gives the rank and the gift, a fall wakes you at the door.
- Tools, run at each hand-back: `reach_sim.py` (every carrier escapes at the lock: 100%), `combat_sim.py` with the new
  model (each Lord at its targets), and the new animation meters.

**Stages** (each playable on its own; one CI tour each; nothing reaches build/awaken-v1 but through the orchestrator):

0. **Hits land now.** Your hit plays on your screen at once, the lag watch, boss swings at the server's pace, the
   fight budget. *Gate*: tests green; you feel it in game. About half a day to a day. It touches Arms' strike
   prediction (`ArmsPredict`, `ArmsRules`, the sweep in `ArmsServer`), so the weapon-forge session hears which files
   first.
1. **See it coming** (every boss and beast at once). Carriers, the tell language, the lock, classes on every move, the
   effect rule, the new shot look, the beasts' told lunge. Same bosses, but nothing lands unseen. *Gate*: tests green,
   reach model 100%, CI tour of each carrier mid-flight. About 1-2 days.
2. **The duel, on three pilots**: the Bell Warden (a figure), the Glass Serpent (a serpent), the Gearwright Colossus (a
   giant), all early so you meet them soon. Phrases, openings, parry recoil, shots back, the posture payoff and
   finisher, hit sounds, the six animation meters, the sim's new model. *Gate*: **you play these three** in game; go or
   no-go for the rest. About 1-2 days.
3. **The Ringer**, the first Proving, and the Drowned Belfry, replacing the D box. *Gate*: you play it. About 1-2 days.
4. **The other 38 bosses and the fighting beasts** to the stage-2 bar, in batches by body (figures, beasts, serpents,
   fliers, giants), a close CI tour per batch, each Lord at its sim target. About 3-4 days.
5. **The other five Wardens**, one at a time, each replacing its box trial when you've played it. About 1-2 days each.

The order puts the late hits first (they spoil everything else), then your biggest complaint, proves "satisfying" on three bosses before touching 38, and brings the
Ringer early because every player meets it at hour four. Times are rough builder time at the pace of the last weeks.

**Rollback.** Each stage merges on its own; reverting a merge restores the old behaviour. Old box trials stay until
each Warden passes. No new settings: the language is the game, not an option.

**One builder.** I build all of it myself. The only piece that could run beside it is the architecture of the four
later halls once the Ringer has passed; that's for later, not now.

**Not verifiable by me:** the feel in your hands, the sounds (unheard until you play), FA side by side in game, and
the vanilla-bodied fauna under FA.

---------------------------------------------------------------------------------------------------------------------

## 7. Decisions for you

1. **Hits land now, first**: your hit's impact plays on your screen the moment your blade reaches the foe, with the
   server still deciding the damage; a lag watch logs any fight tick over 50 ms and what took it; boss swings follow
   the server's real pace. Built before anything else. *Recommended: yes.* (The other way: keep waiting for the
   server's word, which is always right and always a beat late.)
2. **Everything that hurts you at range travels or is drawn first**, through the five carriers, and an aimed attack
   locks visibly at least half a second before it lands. Marks-under-you become thrown volleys; instant rings become
   waves you jump or dash through. *Recommended: yes.*
3. **What a parry beats**: white-gold = parry, block or dash; amber = parry or dash; red = dash or jump only. At least
   40% of each boss's blows parryable, at least 20% red. A deflected white-gold shot flies back into the boss's
   posture. *Recommended: yes.* (The other way: no shot can be parried, only dashed.)
4. **Effects only from a landed hit**, one signature effect per boss on its signature moves, nothing through a dodge.
   *Recommended: yes.* (The other way: no effects from bosses at all.)
5. **The payoff**: deflects, perfect dodges and punishes fill the posture; a break drops the boss and your finisher is
   worth up to 15% of a Lord's health (8-12% today), with a recoil on every parry. *Recommended: yes.*
6. **Difficulty**: keep blows that hurt and tune each Lord's health with the new sim to the approved targets, instead
   of the never-applied lower-damage curve (which lets standing and trading beat 7 of 15 Lords). *Recommended: yes.*
7. **The Provings ladder** as in 4.7: six Wardens in their own halls, each harder than its rank's Lords and the one
   before, never announced; each box trial goes when its Warden passes your look. *Recommended: yes.*
8. **The reward**: the rank, the Journey's gift and the Warden's trophy (as approved). Or also a **fighting technique**
   learnt from each Warden (the Ringer's: a perfect deflect that rings a bell and staggers what's near). *Recommended:
   the approved gifts and trophy; a technique each adds six new player moves to balance, better after the six exist.*
9. **The order**: stage 0 first, then stage 1 for every boss and beast at once; then three pilots you play before
   the other 38; the Ringer right after the pilots. *Recommended: yes.*

Note for the gate-worlds session: a gate-world elite is also called the Ringer. The Warden keeps the name (the
Journey's Great Fault is already named for it).
