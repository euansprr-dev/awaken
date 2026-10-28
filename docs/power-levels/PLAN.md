# Power levels: every level-up transforms the power

Status: PLAN, waiting for Euan's approval (2026-10-28). Nothing in `src/` changes on this branch. Branch
`claude/power-levels-plan-0eb980`. Page: (link added on publish). Ladder data: `docs/power-levels/ladders_draft.py` (run it to print
section 6's tables).

Euan, 2026-10-28: "When leveling up a power, it should have a real difference that makes it super satisfying to level up. Better
animation, slightly different attack, etc. ... not just like, oh, a little bit more damage ... every power has a max level or something
like that, and you can always level up your powers ... let's say the beam ... levels up from the simple beam to around the beam there's an
extra like spinny spiral ... It has to genuinely look insane ... We need to audit all the powers and create a plan to basically make each
level-up significant and actually make sense and be satisfying to level up."

## 0. The short version

- **Today a level-up changes numbers only.** Across all 63 powers a level adds +12% damage, -4% cooldown and, for 20 of them, +5%
  duration; four read it for one number of their own (Dash and Blink's distance, Flight's speed, Barrier's shield, Mend's heal). Nothing a power *does* changes at any level. On screen, about 40 powers draw some parts of their effect 10% bigger per level
  (the impact, ring and nova recipes read the level); the other 23, the Beam among them, are the same pixels at level 5 as at level 1.
  No animation, sound or icon changes with level. The level-up toast says "+12% damage, -4% cooldown" even for Wall, which has no
  damage. Using a Power Core plays vanilla dust and end-rod particles. Three passives (Wall Stride, Water Run, Danger Sense) can never
  level through use, because mastery only pays for hits and fights.
- **The cause is structural, not tuning.** The level reaches every client already (3 bits on every effect event), but there is no
  place where a power *says* what its level 3 is. Level is a multiplier in `PowerMath`, so it can only ever make numbers bigger.
- **The plan: a ladder per power.** Each power keeps its 5 levels. Every step from 2 to 5 has a name, a new layer in its look, and a
  change in play. Steps 3 and 5 also change the cast gesture and add a sound layer. Level 5 is the power's **Awakened form**: a new
  cast, a signature look in the family's awakened colours, and a capstone move. Example, Euan's beam: **Helix** (a spinning spiral,
  and the beam builds damage while held) → **Lance** (a double helix that pierces) → **Corona** (a burning pool where it lands) →
  **Sunspear** (born from a ring of runes at the palm, three ribbons, a white-gold core, a last pulse on letting go).
- **Levelling up becomes a moment.** Out of a fight: a System window names the new form and says exactly what it does, and your power
  blooms round you in its new look (others nearby see it). In a fight: a short sting and the key's icon lights up; the full moment waits
  until it's calm. Power Cores use the same moment (no vanilla particles). The K menu shows each power's whole ladder.
- **Strength stays on the pacing curve.** The flat +12% a level becomes +6%; the new steps carry the rest, so a level-5 power is about
  as strong as today's and the gates stay balanced. Every power can grow through use, passives included.
- **Proof first.** The system plus the beam's whole ladder, the starters' first steps and the evolution moment, for Euan to look at
  in game (a showcase command casts the beam at levels 1 to 5 side by side). Then the six starters, then family by family.
- **Estimate** at git pace: proof about 3 hours, all 63 powers about 10 to 14 hours of one builder (section 8.4). The last power plan
  estimated 20 hours and took about 2; this one has about 250 hand-written mechanics, so it won't shrink as much.
- **12 decisions** for Euan (section 9). Two would be settled by seeing the proof in game: whether the looks clear the bar, and
  whether the in-fight moment is enough.

## 1. Understanding and success

### 1.1 The brief, separated
- **The job:** levelling a power is a reason to keep using it, and the reward is a power that visibly and mechanically becomes more,
  every time, all the way to its top.
- **The pain:** a level-up today is a toast and a stat bump; the power looks, sounds and moves exactly the same.
- **The experience he wants:** each level is satisfying and makes sense for that power: a better animation, a slightly different
  attack, a new visual layer ("the beam ... an extra spinny spiral"), and a top level that feels magical. AAA, never vanilla particles.
- **His proposed solutions, treated as ideas:** a max level per power (kept: 5, decision 2); "you can always level up your powers"
  (read as: every power can be levelled, nothing is a dead end; the passives that can't today are fixed, decision 7); the beam's
  spiral (taken as the model for the whole ladder: a layer that also tells you what changed).
- **Hard constraints:** AAA look on our own effects engine (no vanilla particles); multiplayer-safe (others see your level's look; the
  server decides what happens); frame budgets on an M4 (the SPEC header: p50 ≥ 110 fps in a full gate fight); plain words; SPEC §6's
  Dash direction (no step changes where a move goes); the Ascent's movement tiers stay the Ascent's (no step hands out air jumps,
  flight or gliding); saves load unchanged.
- **Negotiable:** the exact names, numbers and order of the steps; which steps get a gesture; when the evolution moment shows.
- **Unknowns, labelled:** how the looks read at play distance (needs Euan's eyes in game: the proof gate); today's frame cost of power
  effects (never measured: `FxRender` has no timer; phase L0 adds one); whether 4 steps per power are enough (decision 2).

### 1.2 What strong, weak and unacceptable look like
- **Strong:** you level Beam to 3 in a gate. A short sting, the Z key's icon flares. When the room is clear the System window says
  "Beam · Lance — your beam now pierces the first enemy and hits a second", a double helix spins round you for two seconds, and the
  next beam you pour has two counter-turning ribbons and a flare at each body it passes. Your friend standing next to you sees the same
  beam. You want level 4.
- **Weak:** the beam gets brighter and wider at each level. True, but it is "a little bit more" again.
- **Unacceptable:** a step that changes what a key does (Dash going forward only, 27 Oct), a step whose look is vanilla particles, a
  level 5 that drops the frame rate in a crowded gate, a toast that claims something that isn't true, a level the player gets and can't
  tell what it did ("severance something").

### 1.3 Outcome contract
| | Contract |
|---|---|
| Usefulness | Every one of 63 powers has 4 named steps; each step changes something you can see from 16 blocks and something you can feel in play. |
| Correctness | The words for a step say exactly what it does, with the real numbers; the same text drives the K menu, the evolution moment and the Power Core hover (Codex). |
| Effort | No new key, no menu to visit: the change arrives as you play. The K menu shows the ladder for anyone who wants to plan. |
| Responsiveness | The new look and move apply on the next cast after the level-up, including the cast already in your hand if you press again. |
| Control | Nothing to choose (variants already give the choice). Effects settings (FULL / REDUCED / MINIMAL) thin the layers, never the moves. |
| Reliability | A missing look falls back to the level-1 look and a test fails at build time; a step's mechanics never depend on its look. |
| Cost | A max-level cast costs at most 0.5 ms more frame time at p50 than its level-1 cast in the gate bench (section 5.12). No new packets per cast. |

### 1.4 Not in this plan
- No skill trees or branching choices per level (variants are the choice; decision 1 explains). No new powers. No change to how Power
  Cores drop or to the variant rules. No gate or mob rebalancing. Bosses and creature animation (the boss-plan session). The Ascent's
  movement tiers. Arms' Weaves (they already scale +8% a power level; untouched).
- Must not regress: Dash direction (`powers_dash_goes_where_you_move`), reach matching hits (`power_fx_reach_matches_hits`), every cast
  emits its effect, cast profiles and wind-ups (§32), the pacing sim's mastery curve, saves.

## 2. The audit (verified in code, 2026-10-28)

Read: `powers/` (`PowerType`, `Loadout`, `Mastery`, `Powers`, `PowerMath`, `Cast`, `Casting`, `Fx`, `fx/*`, `client/fx/*` including
`FxRecipes` entry and the beam, `Shape`, `FxBudget`, `PowerCoreItem`, `client/PowerHud`, `client/PowersSection`, the nine
`catalog/*Powers`), `docs/modules/power-fx.md`, `docs/power-anims/PLAN.md`, `tools/art/specs/power_cast_table.py`, SPEC §2, §3, §6, §13
(mastery), §21.3, §25 (Weaves), §30.6, §32, `docs/perf/PERF-26OCT.md`, the Codex log. A static scan of every power's behaviour for what
reads the level (`docs/power-levels/audit_levels.py`, output in `audit_levels.txt`; it follows helpers by name, so a helper with another name can hide a use:
I checked the beam, constructs and passives by hand). I did not run the game.

### 2.1 Map
| Stage | What it does today | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Level storage | `Loadout.Owned(id, variant, level)`, `MAX_LEVEL = 5`, synced to the owner | `Loadout.java` | Sound base | keep |
| Earning | Mastery from landed casts (their cost), half the energy spent in a fight, kills; needs 7k/13k/32k/50k; duplicate core = +1 | `Mastery.java`, SPEC §13 | Main power L3 at player level ~20, L5 at ~44-48 | keep the curve; extend earning (2.4) |
| What a level does | `PowerMath`: damage ×(1 + 0.12 per level), cooldown ×(1 - 0.04), `Cast.ticks` +5% a level (20 powers); 4 read the level for one number of their own (Dash and Blink's distance, Flight's speed, Barrier, Mend) | `PowerMath.java`, `Cast.java`, catalog | Level = multiplier only | **replace with ladder + smaller multiplier** |
| Level on the wire | Every effect event packs the level in flag bits 8-10 (1-8) and goes to everyone within 72 blocks | `Fx.java:178,193` | Others already receive your level for free | **reuse** |
| Level on screen | `FxRecipes.build`: `big = 1 + 0.1 × (level-1)` passed to 14 recipe kinds (impact, slam, ring, nova, cone, smoke, dome, vortex, palm, echo, frost, summon, glyph, strike) | `FxRecipes.java:53` | 40 powers grow a little; 23 never change (Beam, constructs, drones, Shadow Step, Mend...) | replace with level layers |
| Animation, sound | Cast profiles (`casts.json`, 54 powers) have no level; sounds are chosen by the server with no level | `CastProfiles`, `Cast.sound` | Identical at every level | extend |
| Level-up moment | Toast "Name · level n" + "+12% damage, -4% cooldown. It grew through use." (always, even for no-damage powers), the learn sound | `Mastery.announce` | Says nothing true about Wall; nothing to see | replace |
| Core use | Signal window "Power levelled up" with rows; vanilla `DUST` + `END_ROD` particles at the player | `Powers.useCore` | Vanilla particles at the reward moment | replace |
| Showing levels | K menu: level pips and mastery segments; card says "Mastery 34% to level 4"; HUD keys show no level; core tooltip "Lv 2 → 3" | `PowersSection`, `PowerHud`, `PowerCoreItem` | No idea what the next level brings | extend |
| Variants | Up to 2 per power, multipliers + a few special behaviours; a duplicate core of another variant levels up, sneak-use swaps | `PowerType.Variant`, `Powers.learnOrLevel` | The existing choice | keep; steps apply to every variant |
| FX engine | Client-built from a ~50-byte event; pooled sprites (3,072 at FULL), 192 reusable meshes (rings, spheres, beams, trails, lightning, arcs, columns, tethers, seams, glyphs, discs, hex shells, slashes), debris, afterimages, screen feel; one effect ≤ 220 sprites | `power-fx.md`, `Shape.java`, `FxBudget.java` | A real engine to build on | **reuse**, add 5 primitives |
| Frame cost | Never measured for power effects; the game is GPU-bound at ~21 ms on Cinematic at the Roost | `PERF-26OCT.md` | No baseline | measure first (L0) |

### 2.2 Earning gaps
- **Wall Stride, Water Run, Danger Sense** never land hits or spend energy, so their bars never move: they can only level from duplicate
  cores. Their level also changes nothing but a cooldown they don't have.
- **Constructs, movement and sight powers** earn only half their cost, and only inside a fight. A Wall that stops ten arrows earns the
  same as one cast at the air during a fight. Fine as an anti-farm rule; harsh for powers whose job isn't to hit.
- **Hidden powers** (17 path signatures and latents) can't come from cores, so mastery is their only way up: their steps must be
  reachable through use (they are; 2.4 covers the utility ones among them).

### 2.3 Five cases, traced
1. **The disappointing case, Beam 2 → 3 in a fight.** Mastery fills → `Mastery.add` → `Owned(level 3)` → toast "+12% damage, -4%
   cooldown". Next press: `Cast.powerLevel = 3` → `PowerMath.damage` ×1.24 → `Fx.beam` event with level bits 3 → `FxRecipes.beam` (which
   never reads the level) → the same beam. The level reached the client and was dropped at the recipe.
2. **A strong existing result: the stamp (§32).** Wind-up, knee, boot in first person, the wave from the planted foot, the thump, the
   authored sound. The ladder builds on this; it must not undo the timing (release on the body's frame).
3. **Ordinary: Dash level 3.** Distance and cooldown change through the predictor; direction comes from the held keys. Any step for
   Dash must leave `Moves.steer` alone (the 27 Oct bug was a level-dependent forward-only path).
4. **Ambiguous: Wall at level 4.** No damage, a 2 s cooldown 12% shorter. The toast says "+12% damage". The ladder gives it a real
   change (Crystal: twice as tough) and the toast is replaced by the step's own words.
5. **Missing data: Wall Stride.** Owned, at level 1 forever unless a duplicate core drops. 2.4's earning rule fixes it.

### 2.4 Root causes
| Symptom | Cause | For | Against | Test | Impact if fixed |
|---|---|---|---|---|---|
| Level-up feels like nothing | Level only feeds multipliers; no per-power definition of a level exists | `PowerMath`, catalog reads | none | read every `powerLevel` use (done) | the whole feature |
| Beam identical at level 5 | Recipes ignore the level bits except 14 kinds' size | `FxRecipes.build` | none | the static scan + beam read | every look |
| Toast wrong for Wall | One hard-coded sentence for every power | `Mastery.announce` | none | read | trust |
| Reward moment is vanilla | `useCore` sends `DUST`/`END_ROD` server particles | `Powers.java:136` | none | read | the bar |
| Passives never level | Mastery pays only hits and fight energy | `Mastery` javadoc and code | cores still level them | read | "you can always level up" |
| Unknown frame cost | No timer in `FxRender` | grep | the engine has caps (220/effect) | L0 timer | safe budgets |

## 3. Alternatives

| | What | Quality | Effort | Risk |
|---|---|---|---|---|
| A. Targeted repair | Honest toasts; every recipe scales size, brightness and density with level; pitch and volume up with level; core moment on our engine | "More of the same", which is the complaint | ~2 h | low |
| **B. A ladder per power (recommended)** | Named steps 2-5 per power: a new look layer and a play change each; gesture and sound at 3 and 5; the Awakened form at 5; the evolution moment; the ladder in the K menu; the flat multiplier halved | Each level is a new thing; reads for others | ~10-14 h | medium: content volume, perf |
| C. Trees and new assets | A choice of two forms at levels 3 and 5, new models, new shaders per power | More agency, bespoke art | ~30+ h | high: doubles the content, and variants already are the choice |

B wins because the complaint is that a level is "a little bit more": only a change in kind fixes that, and B does it on the engine,
gesture and sound pipelines we already have. A is folded into B (the honest words, the core moment). C would double the steps to
author before we know the single ladder works; the data format leaves room for a choice at level 5 later if Euan wants agency
(the evidence that would change this: Euan saying in play that he wants to pick).

## 4. The design

### 4.1 What a step is
Every power has levels 1-5. A step (levels 2, 3, 4 and 5) is:

| Part | Level 2 | Level 3 | Level 4 | Level 5 (Awakened) |
|---|---|---|---|---|
| Name | yes | yes | yes | yes, the form's name |
| A new layer in the look | first layer | second layer | third layer | the signature look, in the family's awakened colours |
| A change in play | small (reach, speed, a status) | a new behaviour (the "slightly different attack") | a second boost | the capstone move |
| Body | the level-1 gesture | an accent (a second hand joins, a step, a hang) | as 3 | a new cast gesture, third and first person |
| Sound | as 1 | a family layer under the cast | as 3 | the power's signature layer |

Rules every step follows: it keeps every earlier step (layers stack, moves add); it works with every variant (the variant reshapes
it, the ladder notes where); it never changes what a key does or where a move goes; its look *tells* its move (the beam's spiral
tightens as it builds; a pierce flares at each body); its words carry its real numbers.

### 4.2 The data (one source for every reader)
- `tools/art/specs/power_ladder_table.py` (from this plan's `ladders_draft.py`) writes `data/awaken/powers/ladders.json`: per power,
  per step: `name`, `look` (text), `does` (text with `{n}` slots for live numbers), `riders` (shared mechanics with parameters, 4.3),
  `layers` (look layer ids and parameters, 4.4), `gesture` (accent or clip id), `sound` (tier layer id), `budget` (sprites, meshes).
- `powers.ladder.PowerLadder` (common, read on both sides like `CastProfiles`): `step(power, level)`, `text(power, level, player)`
  (fills the numbers), `riders(power, level)`. The K menu, the evolution moment and Codex's Power Core hover read this; nobody else
  writes level text.

### 4.3 The server: shared riders, own capstones
About 60% of the steps are one of a dozen shared mechanics; written once in `powers.ladder.Riders`, hooked where `Combat.hurt` and
the cast pipeline already pass, tested once each:
`pierce(n, falloff)`, `chain(n, falloff, range)`, `echo(delay, share)`, `aftershock(delay, share, radius)`, `linger(zone kind, ticks)`
(burning, frost, static, ink), `status(slow | blind | stun | mark, ticks)`, `extend(range | radius | duration | charge, ×)`,
`refund(share, when)`, `split(n, share)`, `ramp(max, ticks)`, `guard(projectiles, n)`, `push(×)`.
Bespoke steps (most level 5 capstones, some level 3s) live in their family's catalog class beside the power's behaviour, reading
`c.step(n)`. Every cast snapshots its level at the press (a channel keeps its level until let go). Movement steps that change motion
(Leap's Titanfall) go through the existing `Predictor`, which already receives the level.

### 4.4 The look: level layers on the engine
- **Where:** each family gets `client/fx/ladder/<Family>Looks.java`; `FxRecipes.build` calls the power's look hook with the event's
  level after the base recipe. Each family's looks are their own file, so the work can be split by family later without conflicts.
- **New primitives** (in `Shape`, drawn by `FxRender` with the existing render types, no allocation per frame):
  1. **HELIX**: 1-3 ribbons spiralling round a line (beams, rails, tethers, dash paths, the haymaker's forearm); turns per block,
     radius, spin, taper, a tightness that can follow a value (the beam's build-up). Up to 128 segments a ribbon, fewer with distance.
  2. **ORBIT**: motes, shards, runes or plates circling an anchor on tilted rings (the haymaker's shards, Brace's plates, Barrier's ward).
  3. **SIGIL**: a flat rune ring facing any direction (the Sunspear's palm ring, Legion's circle), from new 64 px family sigil sprites.
  4. **FISSURE**: glowing cracks along the ground that cool from gold to red (Fault Lines, Landbreaker, Crater Rim), seam lines over the
     ground's own blocks plus a decal.
  5. **DISC**: a tilted, spinning disc of light with a dark core (Black Star's accretion disc, Corona's end ring).
- **New sprites** (from `tools/art/specs/power_fx.py`, same pixel rules as today): family sigils, lotus petal, stone plate, prism shard,
  feather of flame.
- **Awakened palettes:** level-5 events swap the style's core for the family's awakened core (Kinetic molten gold-white, Velocity
  white-hot red, Flight pale silver, Gravity violet with a white rim, Energy white-gold, Shadow black with a violet-white edge, Mind
  gold, Architect cyan blueprint with gold seams, Tech white-cyan, Spirit jade-gold). The look stays the family's; the light is hotter.
- **The rule for quality:** a higher level is better composed, not more particles. Layers are mostly meshes (ribbons, rings, discs),
  which cost little and read at a distance; sprite counts stay inside the per-effect cap.

### 4.5 Body and sound
- **Gestures:** `power_gestures.py` and `fp_gestures.py` already build each power's cast from figure terms plus accents. A step names an
  accent (level 3) or a new gesture (level 5); the generator writes `p_<power>_l3` / `p_<power>_l5` (and first-person twins).
  `casts.json` gains per-level clip overrides; `CastAnims` picks the clip from the event's level bits, so others see your level's
  gesture with no new packet. The five powers still on family poses (Ground Slam, Meteor Dive, Hover, Flight, Leap) get their first
  profile at level 5. Wind-ups never get longer than today's (a level must not make a power slower to use).
- **Sound:** `audio_power_kits.py` renders a family tier layer (level 3: a low resonant body under the cast; level 5: the power's own
  signature tail, such as Sunspear's shimmer and the drop on its last pulse). Clients add the layer from the level bits on the release
  beat (no extra packet). Same mastering window as today's power sounds.
- **Lens:** the big level-5 forms (Daystar, Critical Mass, The Risen Guard, Red Hour, Black Star, Sunspear) get a short Lens look through
  `CastLens`, never over a story's or Arms' moment, only while Lens draws.

### 4.6 The evolution moment
- **Trigger:** a power reaches a new level (mastery or a core). Saved as `seen` (4.10) so the moment plays exactly once per step.
- **In a fight** (hurt in the last 5 s or a hostile within 8 blocks, the Signal's own rule): a 0.4 s sting, the key's icon flares with
  the new tier frame, and the next cast of that power gets a one-time bloom. No window, no pause, no lost input.
- **Out of a fight** (or when the fight ends): a System window, top centre: "BEAM · LEVEL 3 · LANCE" and one line from the ladder
  ("Your beam now pierces the first enemy and hits a second for 60%."), then "Press Z to try it." Round you, the power's new layer
  blooms for 2 s (an `EVOLVE` effect event, so others nearby see you evolve), a rising chord in the family's kit. Level 5 is bigger:
  the Awakened name, a ring of the family sigil under your feet, the awakened palette, a Lens beat.
- **Power Cores** play the same moment (the vanilla dust and end-rod particles go). The Journal keeps a line per step under Powers
  (Codex is building the "explain every unlock" Journal history; the step's text plugs into it).

### 4.7 Showing the ladder
- **K menu, Powers card:** under the mastery bar, the five steps as a short ladder: done steps lit with their names, the next one in
  amber with its full line and "Mastery 34% to Lance", later steps named with one line each. Level 5 shows its form's name.
- **HUD keys:** a tier frame round the key's icon (thin notches at 2-4, the gilded awakened frame at 5), from `powericons.py` as overlays
  (9 families × 4 tiers), not 315 new icons.
- **Power Core hover (Codex, in its queue):** "Lv 2 → 3 · Lance: your beam now pierces..." from `PowerLadder.text`, beside Codex's exact
  before/after numbers. Ask Codex to read the ladder rather than write its own level text.

### 4.8 Every power can grow
Use-based earning for the powers that can't hit, each capped per minute so nothing is farmable at a wall:
Wall Stride (seconds on a wall in a run), Water Run (blocks run on water), Danger Sense (warned blows that then miss you), constructs
(projectiles and blows they stop, seconds an ally stands on one in a fight), Third Eye and Read Intent (hits on foes they show), movement
powers (casts that take you out of a blow's way, distance in a chain). Rates tuned in `tools/sim/pacing_sim.py` so a utility power you
use as much as your main one levels about as fast.

### 4.9 Strength
Today a level-5 power does ×1.48 damage with a ×0.84 cooldown. Plan: the flat bonus becomes +6% damage a level (×1.24 at 5), cooldown
unchanged, and each power's steps are tuned so that at level 5 its value in a typical fight comes back to about ×1.45-1.55 (a ladder
check in the sim: damage per energy in the standard room against today's). So the gates' balance holds and the reward is the new
shape, not a bigger number. (Decision 3: or keep +12% and let steps add on top, about ×1.8 at level 5.)

### 4.10 State and lifecycle
- `Loadout` schema 3 adds `seen`: per power, the highest level whose evolution moment has played. A schema-2 save loads with
  `seen = level` for every power, and the first login after the update shows one window: "Your powers now change as they grow. You
  have: Beam · Lance, Shockwave · Stone Crest..." (one line each), then the Journal holds them. No flood of moments.
- A step gained in a fight sets nothing until the moment plays; a moment owed survives a logout.
- Summons (drones, turrets, soldiers) carry their level as synced entity data so their look matches when seen fresh.
- `ladders.json` is read-only data; a missing row falls back to level 1 and fails the build test.

### 4.11 Multiplayer
The server decides every step's effect (riders run in `Combat`/the cast pipeline). Your level reaches everyone within 72 blocks on
the effect events that already go out: their clients draw your level's look, gesture and sound layer. No new packet per cast; the
evolution moment is one event. Steps that touch other players (Mend's Shared, Bulwark's Rest, Hover's Skyhold, Ghost Step's doorway)
are written for allies. The patent rule holds: a step changes only the foe it hits.

### 4.12 Failure register
| Case | Detection | Behaviour | Test |
|---|---|---|---|
| A power has no ladder row or a broken one | build-time GameTest | build fails; at runtime level-1 look and no riders | `power_ladder_complete` |
| A step changes direction or targeting | Dash/Blink tests at every level | test fails | `powers_dash_goes_where_you_move` (now every level) |
| A step's reach differs from its look | reach test at every level | test fails | `power_fx_reach_matches_hits` (every level) |
| A cast throws at some level | sweep test | test fails | `power_ladder_every_level_casts` (every power × levels 1-5 × variants) |
| The pool is full at a big moment | existing reuse | a step's tell (the pierce flare) takes priority over decoration | pool test |
| REDUCED / MINIMAL effects | settings | layers thin; MINIMAL keeps only the tells; moves unchanged | settings test |
| Level-up mid-channel | snapshot | the channel keeps its level until let go | rider test |
| Old save | schema 2 → 3 | `seen = level`, one summary window | `power_ladder_save_migrates` |
| Frame budget exceeded | the bench (5.12) | a look is trimmed before merge | the Mac bench, by the orchestrator |
| Rollback | server config `powers.ladders` | off = level-1 looks and no riders, the old +12% | config test |

### 4.13 Performance
- **L0 measures first:** a timer round `FxRender`'s passes and the recipe builds (`/awaken powers fxstats`: CPU ms per frame, live sprites,
  meshes, vertices), and a client-side bench (`/awaken powers fxbench <power> <level> <count>`) that replays a recorded crowded-gate scene:
  40 mobs, 3 casters, impacts every few ticks, a held beam. No server, no network: it feeds `FxClient` events.
- **Budgets per power per level** (in the ladder data, checked by a test against the pool caps): level 2 and 4 add at most 1 mesh and
  20% sprites per effect; level 3 at most 2 meshes; level 5 at most 4 meshes live per cast; the per-effect cap stays 220 sprites. A held
  level-5 beam: at most 12 live meshes and 80 live sprites (today's beam keeps about 6 rings and 20 sprites alive).
- **Gate:** in the bench on the M4 at Cinematic, the scene at level 5 costs at most 0.5 ms more frame time at p50 and 1 ms at p99 than
  the same scene at level 1. The orchestrator runs it on the Mac (perf stays on the Mac) at the proof and at the end.
- **Cheap by design:** ribbons and rings are a few hundred vertices; no full-screen additive layer is added; halos over 3 blocks only at
  impacts, at most 4 at once; layers thin with distance like everything else.

### 4.14 Not applicable
No AI, retrieval or personal data. No network calls. Privacy: nothing new is stored beyond `seen`.

## 5. Before and after (same inputs)
| You | Today | With the ladder |
|---|---|---|
| Level Beam to 3 in a gate | Toast: "+12% damage, -4% cooldown". Same beam. | A sting and the key flares. When it's calm: "Beam · Lance", a double helix blooms round you. Your next beam has two counter-turning ribbons and pierces into the foe behind. |
| Level Dash to 3 | Toast. Slightly further. | "Dash · Momentum": your leading fist glows red after a dash and the next blow hits 25% harder. Still goes the way you move. |
| Level Wall to 4 | "+12% damage" (false). | "Wall · Crystal": the light sets into facets; it takes twice the hits. |
| Own Wall Stride | Level 1 forever without a duplicate core. | Fills as you wall-run; at 2, red sparks off each step and half a second more. |
| Use a duplicate Power Core | A window and vanilla sparkles. | The same evolution moment as levelling through use, in the power's own look. |

```
Today:   Mastery/Core ─► Loadout.level ─► PowerMath ×1.12^n ─► behaviour ─► Fx event (level bits) ─► recipe (size ×1.1, 14 kinds) ─► screen
                                       └► toast "+12% damage"
Plan:    Mastery/Core ─► Loadout.level ─┬► PowerMath ×1.06^n ─► behaviour + Riders(step) + capstone ─► Fx event (level bits)
                                         │                                                                  ├► base recipe + <Family>Looks(level)
                                         │                                                                  ├► CastAnims: clip for level
                                         │                                                                  └► sound tier layer
                                         ├► seen < level ─► evolution moment (calm) / sting (fight) ─► Journal
                                         └► PowerLadder.text ─► K menu ladder · Power Core hover (Codex)
```

## 6. The ladders (draft; every power)
Each cell: **name**: what it looks like. *What it does.* Level 5 adds the new cast gesture. Starters lead their family; hidden powers
are path signatures and latents. These are drafts for Euan to steer: names and numbers are easy to change in the table.

### Kinetic

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Haymaker (starter) | **Knuckle Shards**: Shards of amber light orbit the charging fist and close in as the charge fills. *Full charge comes 20% sooner.* | **Shockfist**: A cone of force bursts out of the far side of the target along the punch. *Foes behind the target take half damage and are pushed back.* | **Cannonball**: A launched target trails dust and a ring of force where it lands. *A fully charged target flies as a projectile and hits whatever it crashes into.* | **Meteor Fist**: Your forearm is wrapped in a spiral of embers while you charge. The punch tunnels the air with rings, and a ghost fist lands again a third of a second later. *A second impact for 40% of the first.* Body: A stepping lunge punch with the off arm thrown back. |
| Shockwave (starter) | **Stone Crest**: The wave carries a curling crest of dust and stone along its front. *Reaches 15% further.* | **Spine**: The wave leaves a crack along its path that erupts half a second later into a row of stone spikes. *The spikes hit anything still in the line.* | **Rolling Thunder**: The wave widens as it travels and kicks up the ground's own blocks. *Foes are thrown higher and stay down for half a second.* | **Landbreaker**: The stamp splits the earth: a rolling wall of upturned slabs rides the crest and a glowing fissure runs behind it, cooling from gold to red. *Foes hit are stunned for half a second and the fissure burns for 2 s.* Body: The knee comes higher with a hang at the top; both fists drop as the foot plants. |
| Ground Slam | **Fault Lines**: Glowing amber cracks run out from the impact and cool from gold to red over 2 s. *Foes standing on the cracks are slowed for a second.* | **Aftershock**: A second, lower ring rolls out half a second later with its own thump. *The aftershock hits again for half damage.* | **Upheaval**: Slabs of the real ground jut up round the impact and drop back. *Foes in the inner half are popped into the air.* | **Worldbreaker**: You hang a beat in the air with light wound round both fists, then a dome of force flattens the dust, the crater rim glows and slabs rise in a ring. *The slam pulls foes inward first, then the aftershock throws them out.* Body: Both fists raised overhead, a hang, a two-fisted drive into the ground. |
| Leap | **Comet Arc**: An amber ribbon traces your arc from the feet; a burst of dust at take-off. *15% further.* | **Hard Landing**: Your landing cracks Fault Lines into the ground. *The landing hits harder and slows foes for a second.* | **Rebound**: A ring of force snaps up off the landing. *Foes are thrown up, and you can't be knocked back for a second after.* | **Titanfall**: At the top of the arc, press again to drive straight down: a three-point landing, a glowing crater and a ring of rising slabs. *The plunge hits like a Ground Slam from that height.* Body: Knee and fist hit the ground together; the head comes up last. |
| | *The plunge is predicted on your client like the leap itself. No step adds an air jump (the Ascent's Leaper owns those).* | | | |
| Terrain Rip | **Molten Seams**: The ripped chunk glows along its cracks and trails dust as it flies. *A 15% wider splash.* | **Shrapnel**: The chunk bursts on impact into three tumbling rocks. *Each rock hits a different foe nearby.* | **Twin Rip**: Your off hand tears out a second chunk a beat after the first. *A second throw at the next closest foe.* | **Mountain Hurl**: You heave a three-block slab overhead with both hands; it tumbles end over end and lands as a rolling line of shock. *The landing becomes a Shockwave along the line of the throw.* Body: A two-handed overhead heave. |
| Brace | **Bronze Skin**: A bronze sheen flows over you and your feet crack the ground. *Reflects 10% more back at attackers.* | **Counter Shove**: When it ends, a ring of force shoves out from your feet. *The shove hits for part of what you absorbed.* | **Unmoved**: Arrows spark off the sheen and drop. *Projectiles do half damage while you're braced.* | **Mountain Stance**: Stone plates rise and circle you, catching what's thrown at you; when it ends they burst outward. *The plates block three hits and cut the foes near you as they fly.* Body: Fists crossed at the chest, then slammed apart. |
| Thunderclap (hidden) | **Static Hands**: Arcs dance between your hands during the wind-up. *The stun lasts 0.2 s longer.* | **Forking**: Each bolt forks to one more foe. *Every strike chains once.* | **Thunderhead**: A storm knot hangs over you for 3 s after the clap. *It drops one bolt a second on a foe near you.* | **Heaven's Clap**: Hands raised overhead, the clap throws a flat disc of lightning and a pillar strikes you. *For 3 s your blows chain lightning to a second foe.* Body: An overhead clap, the body rising onto the toes. |

### Velocity

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Dash | **Sonic Ring**: A crisp sonic ring and a pressure cone at the start; the red ribbons double. *Foes you pass through are shoved aside.* | **Momentum**: Your leading fist glows red for a second after a dash. *Your next blow within a second hits 25% harder.* | **Chain Charge**: Each chained air dash adds a red ring that stacks round you. *From the third dash in a chain, half the energy comes back.* | **Redline**: Your path stays drawn in the air as a red line of light that snaps a beat later. *The snap cuts foes along the path for 60%.* Body: A lower, longer lean with one arm trailing. |
| | *Direction never changes: a dash goes the way you move at every level and in every variant (SPEC section 6).* | | | |
| Flow | **Ripple**: The slowdown arrives as a shell sweeping out from you; colour drains outside it. *Lasts half a second longer.* | **Afterimages**: You leave red afterimages as you move. *Each kill during Flow adds half a second (2 s at most).* | **Tempo**: Flow ends with a red snap round you. *20% faster movement and attacks for 3 s after.* | **Red Hour**: Every foe you hit gets a hanging red slash mark; when Flow ends every mark snaps at once. *The marks deal 30% of the damage you did during Flow.* Body: A breath in with the eyes closed, then a sharp open. |
| Afterimage | **Vivid**: The copy holds your pose sharply inside a glowing red outline. *Lasts a second longer.* | **Mimic**: The copy swings once at the nearest foe. *Its blow hits for half of yours.* | **Snare**: Red threads hang off the copy. *Foes that strike it are slowed.* | **Phantom Rush**: The copy bursts into a ring of red crescents. *Press again within 2 s to swap places with it.* Body: A hard sideways step out of your own body. |
| Wall Stride | **Spark Steps**: Red sparks kick off each step on the wall. *Half a second longer on the wall.* | **Kick-off**: Leaving the wall leaves a red ring behind you. *A burst of speed as you leave.* | **Steady**: A thin red line marks your path along the wall. *You don't sink at all.* | **Street Runner**: A red speed ribbon trails you along the wall and you flip off the end. *Each new wall within a second of the last adds a second to the run.* Body: The flip off the wall's end. |
| | *Agree with the body module first: it owns wall-running (SPEC section 17).* | | | |
| Water Run | **Wake**: The water parts in a V-shaped wake with spray. *No slowdown as you step onto water.* | **Skip**: Each stride leaves a ring of light on the surface. *A leap off water keeps your speed.* | **Still Water**: The water stills in a circle under you. *You can stand on water for 2 s.* | **Tidewalker**: Every step leaves a glowing ring; at full sprint you throw a bow wave. *The wave pushes foes in the water away.* Body: Sprint posture leaning into the wave. |
| Lightning Step (hidden) | **Bright Path**: The path stays lit for a second. *2 blocks further.* | **Static Field**: Where you land, crackle spreads over the ground. *Foes in it are slowed for 2 s.* | **Double Flash**: A spare charge sparks at your heels. *A second flash within a second costs nothing.* | **Thunder Step**: A bolt strikes from the sky at both ends of your path. *Everything on the path is stunned for half a second.* Body: A snap of two fingers, then you are gone. |

### Flight

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Hover | **Wind Rings**: Rings of wind turn under your feet. *A second longer.* | **Updraft**: Wind spirals up round you. *Hold jump to rise slowly.* | **Calm Air**: Arrows bend away as they reach the wind round you. *Arrows that reach you do half damage.* | **Skyhold**: A disc of pale light forms under your feet. *Allies can stand on it while you hover.* Body: Arms spread low, palms down. |
| | *No step adds flight or a glide: the Ascent's Wings own those.* | | | |
| Flight | **Contrails**: White ribbons stream from hands and feet. *Drains 10% less.* | **Sonic Wake**: At top speed a vapour cone forms round you and booms. *10% faster at the top.* | **Long Haul**: The contrails stretch and hold longer. *Drains another 10% less.* | **Jetstream**: A burst with a boom ring and a long contrail. *Double-tap forward for a 2 s burst of speed.* Body: A tuck into a dive line. |
| | *Agree with the Ascent first (Skyborn and the Sovereign form are its own).* | | | |
| Gravity Well | **Lens**: A dark core with a bright violet rim; dust spirals inward. *Pulls 15% harder.* | **Debris Ring**: Chunks of the ground orbit the core. *The chunks hit foes they pass.* | **Crush**: The knot visibly tightens as it runs. *Its damage climbs over its length.* | **Black Star**: A tilted disc of violet light spins round the core; at the end it collapses and bursts. *The burst hits for 30% of what the well dealt.* Body: Both hands press down and twist. |
| Telekinesis | **Grip**: A ring of violet runes clamps the lifted mob. *Hold a second longer.* | **Pair**: A second ring takes the mob next to it. *Lifts a second foe near the first.* | **Tumble**: Lifted mobs tumble slowly in the air. *They hit whatever they pass.* | **Orbit Throw**: What you lift orbits round you; the throw curves to your aim with a violet trail. *Throws everything you hold at once, each at its own target.* Body: A two-handed gather, then a swing out like a sling. |
| Meteor Dive | **Burning Entry**: A comet trail and a shock cone as you dive. *Dives 15% faster.* | **Crater Rim**: A ring of molten rock where you land. *It burns for 2 s.* | **Rebound**: You bounce once into a second, smaller impact. *A second hit for half.* | **Starfall**: Three smaller meteors fall round the impact a beat after you. *Each meteor hits the nearest foe.* Body: A rising spin before the drop. |
| Gravity Flip (hidden) | **Upside Dust**: Dust and pebbles fall upward with the foes. *Foes hang half a second longer.* | **Collision**: Foes in the air drift together. *They crash into each other.* | **Weightless**: A pale shimmer stays on you. *You float for 2 s after.* | **Inverted Sky**: The sky flips twice: up, down, up, then the crash. *Two drops instead of one.* Body: Both hands turn over twice. |

### Energy

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Beam (starter) | **Helix**: A bright ribbon of light spirals round the beam and spins; embers peel off along it. *Held on one target, the beam builds up to 20% more damage over 1.5 s, and the spiral tightens as it builds.* | **Lance**: A second ribbon turns the other way (a double helix) and a flare bursts at every body it passes through. *Pierces the first foe and carries on to a second at 60%.* | **Corona**: Where the beam lands, a molten pool spreads and a ring of light turns round the end. *The pool burns foes standing in it for 2 s.* | **Sunspear**: The beam is born from a ring of runes at your palm. Three ribbons, a white-gold core, and on letting go a last pulse runs down the beam and bursts at the end. *The last pulse deals 20% of what the beam dealt.* Body: A wide braced stance, the off hand flat on the forearm; on letting go, the palm closes. |
| | *Prism: the centre beam keeps every layer, the side beams the core and one ribbon. Focused: a tighter spiral.* | | | |
| Charged Shot (starter) | **Coil**: A spinning coil of lightning wraps the charge orb. *Charges 15% faster.* | **Arc**: On a hit the bolt jumps to a second foe. *The jump hits for half.* | **Overcharge**: Hold past full and the orb turns white and spits sparks. *An overcharged bolt pierces.* | **Thunder Lance**: A full charge fires a lance that leaves a crackling line for 2 s, and a bolt from the sky strikes the target. *The line shocks foes that cross it.* Body: The bolt hand pulled back to the ear, then thrown like a spear. |
| Nova | **Orbits**: Two rings of light orbit the shell as it grows. *10% wider.* | **Searing Shell**: The shell holds a second longer. *Foes touching it burn.* | **Flare Arcs**: Plasma arcs leap from the shell to foes past its edge. *Up to three of them, at half damage.* | **Daystar**: You rise a little as a white-gold sun forms at your chest; the release is a shell, a ring on the ground and a pillar of light. *Foes are blinded for a second and the afterglow burns.* Body: Arms open wide, head back. |
| Barrier | **Hex Shell**: Hex cells light up where the shield is struck. *Lasts 2 s longer.* | **Stored Charge**: The shield brightens as it takes damage. *The break shock grows with what it absorbed.* | **Second Skin**: When it breaks, a thinner shell flickers back. *It re-forms once at a third strength for 3 s.* | **Halo Ward**: Three shield plates orbit you and catch arrows; the break is a burst of light. *The plates stop three projectiles.* Body: Palms pushed out, then drawn round in a circle. |
| Overload | **Gathering Arcs**: Arcs crawl over you while you gather. *The afterglow lasts 2 s longer.* | **Fork**: The blast throws lightning to foes beyond it. *Forks to five foes.* | **Live Wire**: Your energy hits crackle during the afterglow. *They jump to a second foe.* | **Critical Mass**: You're lifted while you gather; the blast pulls in, then explodes, and leaves a ring of plasma. *Pull then blast, and the ring burns for 3 s.* Body: Lifted, curled in, then thrown open. |
| Prism Lance (hidden) | **Refraction**: Each split throws a flare of split colour. *The split beams reach 4 blocks further.* | **Pierce**: Split beams leave a flare at each body. *Split beams pierce once.* | **Bounce**: Split beams glance off walls. *Split beams bounce once.* | **Spectrum Crown**: A crown of six prisms rises over your head and each fires its own beam. *Six beams find six different foes.* Body: The chest opened, the head lifted to the crown. |

### Shadow

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Shadow Step (starter) | **Ink Wake**: A trail of ink smoke with violet rims follows the step. *The backstab window is a second longer.* | **Blinding**: A burst of ink at the back of the target's head. *The backstab blinds for a second.* | **Return**: An ink pool stays where you left. *Press again within 2 s to step back to it.* | **Night Cut**: You arrive in a low crouch and a crescent of shadow slashes through the target by itself; tendrils from the pool hold foes nearby. *The crescent lands the backstab for you, and tendrils hold foes near you for a second.* Body: A low crouch arrival with a hand sweeping across. |
| Cloak | **Haze**: A faint shimmer; allies see your outline. *A second longer.* | **Shade Step**: Your hands trail ink while cloaked. *While cloaked, Shadow Step and Echo Swap cost nothing.* | **Ambush Mark**: The first strike leaves a violet crescent on the foe. *The mark bleeds for 3 s.* | **Nightfall**: A dome of dusk falls round you. *Foes inside lose track of your allies too.* Body: Arms crossed, then drawn down like a hood. |
| Echo Swap | **Ink Line**: An ink thread draws between the two places. *Reaches 4 blocks further.* | **Hobble**: Ink clings to the foes struck. *Struck foes are slowed.* | **Double Swap**: The thread hangs a moment. *Swap back within 2 s for free.* | **Left Behind**: A shadow copy stays where you were. *It draws attacks for 3 s.* Body: A clap that leaves your outline standing. |
| Legion (hidden) | **Rim Light**: Soldiers rise from ink pools with violet eyes and lit edges. *One more soldier.* | **Charge**: Soldiers rise already running. *They open with a lunge.* | **Ink Death**: Fallen soldiers spill ink. *The ink slows foes.* | **The Risen Guard**: A circle of runes spreads under you and the soldiers rise in a ring round a captain twice their size. *The captain is a knight that lasts the whole time.* Body: One hand raised, palm up, then closed. |

### Mind

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Danger Sense | **Edge Arc**: A warning arc on the edge of your screen points to the threat. *You know where it's coming from.* | **Sooner**: The red glow starts brighter. *Warns 0.2 s earlier.* | **Read the Shot**: Arrows aimed at you show as golden lines before they fly. *You see a shot's line.* | **Sixth Sense**: A gold ripple when you dodge a warned blow. *The attacker is slowed for a second.* Body: None (a passive). |
| Dominate | **Crown**: A gold crown glyph turns over the puppet's head. *A second longer.* | **Marked Prey**: What the puppet hits glows pink. *Those foes take 15% more from you.* | **Last Order**: The puppet's end is a psychic burst. *The burst hits foes round it.* | **Puppet King**: Gold threads run from your fingers to the puppet. *The puppet hits half again as hard, and an elite can be bent for the full time.* Body: Fingers raised like a puppeteer's. |
| Read Intent | **Rings**: Read foes get pink rings at their feet. *Reads 4 blocks further.* | **Tells**: A red flash a moment before a read foe attacks. *You see their next blow coming.* | **Through Walls**: The rings show through walls. *Reads through walls.* | **Open Book**: A read foe's weak point glows. *Hits on it are critical.* Body: Fingers to the temple, then a slow point. |
| Decoy | **Crisp**: The decoy has a pink rim and taunts. *A second longer.* | **Echo Cast**: The decoy mouths your casts. *It repeats your casts at 30%.* | **Confusion**: The burst leaves spinning pink runes over foes. *Foes strike each other for 2 s.* | **Hall of Mirrors**: Three decoys in a triangle that run together and burst at once. *Three times the burst.* Body: A flick of the hand three ways. |
| Foresight (hidden) | **Ghost Lines**: Faint lines show where foes are about to move. *You see their paths.* | **Longer**: The slowdown fades in more gently. *Half a second longer.* | **Counter**: The blow that misses meets a gold flash. *The attacker is staggered.* | **A Heartbeat Ahead**: Ghosts of foes show where they'll be in a second. *The first two blows miss, not one.* Body: Eyes closed, then open wide. |

### Architect

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Wall | **Blueprint**: The wall is drawn in glowing blueprint lines first, then fills in block by block. *Builds twice as fast and lasts 5 s longer.* | **Ricochet**: Arrows strike sparks off it. *Arrows that hit it fly back toward the shooter.* | **Crystal**: The hard light sets into crystal facets. *Twice as tough.* | **Mirror Wall**: A wall of polished light that throws back what hits it. *Reflects projectiles and beams.* Body: Both palms pushed out flat. |
| Ramp | **Blueprint**: Drawn in blueprint lines, then filled. *Builds twice as fast and lasts 5 s longer.* | **Rush**: Glowing chevrons run up the surface. *Running up it gives a burst of speed.* | **Rails**: Rails of light along both sides. *Foes that step on it are pushed off the side.* | **Launch Ramp**: The end flips up as you reach it. *It throws you in a long arc.* Body: A sweeping upward palm. |
| Floor | **Blueprint**: Drawn in blueprint lines, then filled. *Builds twice as fast and lasts 5 s longer.* | **Spring**: The surface ripples when stepped on. *Landing on it bounces you up.* | **Hold**: Thicker seams. *Lasts twice as long.* | **Sky Bridge**: The floor keeps building ahead as you walk. *It extends for 6 s.* Body: Hands sliding apart along a line. |
| Cone | **Blueprint**: Drawn in blueprint lines, then filled. *Builds twice as fast and lasts 5 s longer.* | **Lantern**: Light drips from the roof. *You heal slowly under it.* | **Shatterproof**: Arrows shatter on the eaves. *Takes no damage from arrows.* | **Pagoda**: A three-tier roof with lit eaves. *Heals and pushes foes out from under it.* Body: Hands drawing the roof's tiers. |
| Rampart | **Blueprint**: Drawn in blueprint lines, then filled. *Builds twice as fast and lasts 5 s longer.* | **Arrow Slits**: Battlements glow. *They fire bolts at foes.* | **Gatehouse**: The door glows for friends. *The door opens only for you and allies.* | **Keep**: A corner tower rises with a beacon on top. *The beacon slows foes nearby.* Body: Arms raised to frame a tower. |
| Spire | **Seams**: Seams of light run up the column as it rises. *The throw is 15% higher.* | **Spiked Rim**: Spikes ring the base. *Foes thrown take damage when they land.* | **Tall**: The column rises higher. *A bigger launch.* | **Spiral Tower**: A spiral of steps winds up round the spire. *You can climb it after the launch; it stands for 10 s.* Body: A spiralling upward sweep. |
| Collapse | **Gather**: Blocks lift and circle you before they fly. *Pulls in from 25% further.* | **Seek**: Each block leaves a blueprint line in the air. *Each block picks a different foe.* | **Shatter**: Blocks burst into glowing shards on impact. *Shards hit foes nearby.* | **Avalanche of Light**: Every block becomes a glowing comet trailing blueprint lines. *Every block stuns.* Body: Both arms pulled down like a rope. |
| Citadel (hidden) | **Runes**: Runes turn across the dome. *A second longer.* | **Spires**: Four spires rise at the edge. *They fire bolts at foes.* | **Sanctuary**: The ground inside glows. *Heals more.* | **Crown Citadel**: A crown of spires and a beacon of light. *Foes inside burn; allies inside are healed.* Body: Arms raised in a slow arc. |

### Tech

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Snap Cable | **Holo Cable**: The cable shows bright teal segments and a reticle where it'll land. *2 blocks further.* | **Stun Hook**: A hooked mob sparks. *It's stunned for half a second.* | **Quick Reel**: The cable hums tighter. *Reels 20% faster.* | **Reel Kick**: You arrive at a hooked mob feet first, with a teal shock ring. *A flying kick on arrival.* Body: A side-on cast, then a tuck. |
| Jet Burst | **Shock Diamonds**: A blue-cored exhaust with bright shock diamonds. *10% faster.* | **Scorch**: The exhaust lingers as a burning streak. *Burns longer.* | **Hang**: The jets sputter on for a moment. *A moment of hover after the burst.* | **Afterburner Boom**: A sonic boom ring and a scorching trail line. *The boom pushes foes away.* Body: A crouch, then a launch. |
| Drone | **Holo Wings**: The drones wear holographic wings and reticles. *A second longer.* | **Marks**: Their targets get a reticle. *Marked foes take 10% more from you.* | **Burst Fire**: Three-shot bursts. *Fires in bursts.* | **Overwatch**: Laser threads link the drones into a triangle. *The threads cut what crosses them.* Body: A tap on the wrist, then a flat palm. |
| Turret | **Scan Cone**: A holographic scan cone sweeps. *Sees 4 blocks further.* | **Slowing Shots**: Its shots leave frost-blue hex marks. *Shots slow.* | **Shield**: A hex shield round its base. *It takes half damage.* | **Sentinel**: A long barrel unfolds and charges. *Fires a charged rail every 5 s.* Body: A kneel to set it down. |
| Railgun | **Coils**: Coil rings spin along your arm as it charges. *Charges 15% faster.* | **Vacuum Tunnel**: Rings hang along the line of fire. *Foes in the line are knocked back.* | **Quick Charge**: The coils glow white. *Charges another 15% faster.* | **Gauss Lance**: The line stays as a blazing teal thread that explodes along its length half a second later. *The second blast hits everything in the line.* Body: Braced side-on, the recoil throwing the shoulder back. |
| Nanite Skin | **Hex Scales**: Hex scales ripple over you. *A second longer.* | **Arc Shock**: The shock jumps. *A melee hit's shock jumps to a second foe.* | **Repair**: Scales flow over your armour. *Repairs your armour as it runs.* | **Living Armour**: Plates form on your shoulders and forearms; at the end they burst into a swarm. *The swarm attacks foes near you.* Body: Arms crossed, the plates closing over them. |
| Drone Swarm (hidden) | **Holo Swarm**: Holographic trails behind every drone. *A second longer.* | **Laser Lines**: Drones fire visible laser lines. *Their shots pierce.* | **Retarget**: A reticle jumps to the next foe. *Drones switch targets the moment one dies.* | **Swarm Lance**: At the end the drones form a ring and fire one combined beam. *The beam hits everything in a line.* Body: A hand raised to the ring. |

### Spirit

| Power | 2 | 3 | 4 | 5 (Awakened) |
|---|---|---|---|---|
| Spirit Palm (starter) | **Lotus**: The palm print is ringed with jade lotus petals. *Pushes 15% harder.* | **Through**: The print passes on through the target. *It hits the foe behind too.* | **Heavy Echo**: The echo arrives as a deeper blue print. *The echo slows.* | **Guardian Palm**: A giant translucent jade hand slams forward behind your strike. *The giant hand hits everything in front of you.* Body: A full-body step into the palm. |
| Aura | **Wisps**: Jade wisps circle your feet in a fight. *Reaches a block further.* | **Calm**: Your aura brightens out of combat. *Heals faster out of combat.* | **Drag**: The pulse ripples outward. *The pulse slows foes.* | **Soul Guard**: Wisps orbit you and dart at what hits you. *They strike back at attackers.* Body: None (a passive). |
| Mend | **Lotus Ring**: A ring of lotus light opens under you. *Heals 10% more.* | **Ward**: A thin jade shell holds after. *3 s of damage resistance after.* | **Shared**: The ring spreads to allies. *Allies heal as much as you.* | **Bloom**: A jade lotus opens under you and petals rise. *Everyone on it heals for 4 s.* Body: Palms together, then opened like a flower. |
| Third Eye | **Eye Mark**: A glowing eye opens on your brow (others see it). *A second longer.* | **Exposed**: Seen foes glow brighter. *Seen foes take 10% more from you.* | **Secrets**: Hidden things glow too. *Shows hidden doors, chests and traps.* | **All-Seeing**: An eye opens in the sky above you. *Sees 64 blocks.* Body: Fingers to the brow, head tipped back. |
| Cord Lash | **Braided**: The cord braids with soul light. *2 blocks further.* | **Twin Lash**: The cord splits in two. *Hauls two foes.* | **Dazed**: The foe arrives with stars of soul light. *Dazed for longer.* | **Soul Chain**: The hauled foe is chained to another nearby. *They're pulled into each other.* Body: A two-handed whip and pull. |
| Bulwark (hidden) | **Runes**: Runes turn round the dome. *A second longer.* | **Repel**: The dome's edge ripples outward. *Pushes foes out harder.* | **Rest**: The ground inside glows jade. *Allies inside heal slowly.* | **Lotus Dome**: The dome forms as overlapping petals that open outward at the end. *The opening throws foes away.* Body: Arms raised and opened like petals. |
| Hundred Palms (hidden) | **Prints**: More palm prints, layered. *10% more palms.* | **Seeking**: The prints curve toward foes. *Palms home in.* | **Last Palm**: The final palm is twice the size. *The finisher launches further.* | **Thousand Arms**: A spirit figure rises behind you and strikes with its own arms beside yours. *The figure's palms add 30%.* Body: A rooted stance, palms in a blur. |
| Void Maw (hidden) | **Fangs**: A ring of shadow fangs round the maw. *Pulls 15% harder.* | **Hunger**: The throat glows deeper. *Swallows foes at a little more health.* | **Chew**: The maw spits debris. *The debris hits foes round it.* | **The Hollow Mouth**: The maw closes with a visible bite. *The bite heals you for what it swallows.* Body: An arm thrust out, the hand clenching shut. |
| Waking Dream (hidden) | **Shimmer**: What you shape shimmers like a dream. *Shapes 15% faster.* | **Bigger**: Doors and bridges come wider. *Shapes bigger.* | **Lasting**: The shimmer fades slower. *Lasts twice as long.* | **Lucid Architecture**: Stairs spiral, bridges arch, with rails of light. *Shapes follow the land's lines.* Body: Hands shaping a curve in the air. |
| Storm Call (hidden) | **Clouds**: A dark cloud gathers before the strikes. *Strikes 10% harder.* | **Chain**: Each strike jumps once. *Strikes chain to a second foe.* | **More**: More forks in every bolt. *One more strike.* | **Eye of the Storm**: The storm follows you for 6 s. *It keeps striking foes near you.* Body: Arms raised, then pulled down. |
| Ember Step (hidden) | **Flame Ribbons**: Ribbons of flame stream from your heels. *The trail is 20% longer.* | **Lasting**: The trail burns longer. *Burns 2 s longer.* | **Flare**: The trail flares when foes cross it. *Embers burst on them.* | **Phoenix Step**: A firebird of flame flies the path with you. *The firebird burns everything it passes.* Body: A low spinning step. |
| Frost Nova (hidden) | **Crystals**: Ice crystals spike up round the ring. *10% wider.* | **Shatter**: Frozen foes crack when hit. *Hitting a frozen foe bursts the frost.* | **Frost Field**: Frost lingers on the ground. *Slows for 3 s.* | **Winter Bloom**: A huge ice flower blooms round you and shatters. *The shatter hits again.* Body: A stamp, then the hands opening upward. |
| Ghost Step (hidden) | **Soul Trail**: A pale trail hangs along the step. *2 blocks further.* | **Phase Chill**: Foes you pass through frost over. *They're slowed.* | **Further**: The trail stretches. *2 more blocks.* | **Through the Veil**: A glowing doorway stays where you went in. *Allies can follow through it for 3 s.* Body: A step through, the hand trailing. |
| Twin Strike (hidden) | **Visible Twin**: A soul-blue outline of you repeats each blow. *A second longer.* | **Stronger**: The twin's outline sharpens. *The echo hits for 70%.* | **Longer**: The twin lingers. *2 s longer.* | **Twin Souls**: The twin becomes a full figure beside you. *It repeats your power casts too, at half.* Body: A step that splits into two. |

## 7. Evaluation
- **Baseline:** today's level-up for the five cases in 2.3 (the toast text, the identical beam, the passives stuck at 1).
- **Tests (GameTests, CI):** `power_ladder_complete` (every power, 4 steps, text, budgets within caps, every look and gesture id
  exists), `power_ladder_every_level_casts` (every power × level × variant casts without error and emits its effects), one test per
  shared rider (pierce, chain, echo, aftershock, linger, ramp, guard, refund, split), the Dash and reach tests at every level,
  `power_ladder_save_migrates`, `power_ladder_moment_once` (a step's moment plays once, waits out a fight, survives a relog),
  `power_ladder_earning` (each utility rule pays, and caps), the sim's strength check. Token budget: one test per mechanism, not per step.
- **Look (the real gate):** Euan, in game, with `/awaken powers showcase <power>` (casts the power at levels 1-5 one after another at a
  row of dummies, in first or third person). Nothing here can grade "looks insane" except his eyes.
- **Perf:** the bench gate (4.13).
- **Acceptance:** all tests green on CI; the bench within budget; Euan's yes on the proof before the families.

## 8. Delivery

### 8.1 Phases
| Phase | What | Gate |
|---|---|---|
| L0 | Frame timer and bench; baseline numbers | numbers recorded |
| L1 **Proof** | Ladder data and `PowerLadder`, riders core, level hooks, HELIX / ORBIT / SIGIL, awakened palettes, Loadout schema 3, the evolution moment (and the core's), the K menu ladder, HUD tier frames, honest words; **Beam's whole ladder**; level 2 for the other five starters; the showcase command | CI green; bench; **Euan looks in game** |
| L2 | The six starters' full ladders (Haymaker, Shockwave, Charged Shot, Spirit Palm, Shadow Step, and Dash) | CI green |
| L3 | Kinetic and Velocity | CI green |
| L4 | Energy and Flight & Gravity | CI green |
| L5 | Shadow, Mind and Spirit | CI green |
| L6 | Architect and Tech; the use-based earning (4.8); the strength pass in the sim | CI green; bench at the end |

Each phase ships on its own behind `powers.ladders` (default on once L1 is approved). Rollback: the flag, or revert the phase's merge.

### 8.2 Dependencies and coordination
- **Codex's Power Core hover preview** (gameplay owner's queue, not started on any branch today): read `PowerLadder.text` for the step
  line. If it lands first, L1 replaces its level text with the ladder's.
- **The body module** for Wall Stride and the Ascent for Flight, Hover and Leap: their steps are written to stay inside the power's
  own move; the builder checks §17 before building them.
- **Arms' Weaves** read the power level (+8% a level): unchanged.
- **The boss-plan session** owns creature animation; nothing here touches mobs.

### 8.3 Risks
- **Volume:** about 250 steps. The shared riders, the look primitives and the generated gestures carry most of it; the capstones are the
  hand work. If the proof shows a family's looks need more, that family's phase grows, not the plan.
- **Taste:** the proof is the check; Euan may want a different look language for the ladder. Cheap to change before L2.
- **Overlap with the Ascent** for movement powers: written conservatively; flagged per power.
- **Perf:** unmeasured today; L0 first, budgets in the data, the bench before merge.

### 8.4 Estimate (AI pace from git history)
All of AWAKEN (~1.14 M lines in `src`, ~360 k of them Java) was built in about 30 hours. The power effects engine went in on 23 Oct in
one commit of 10.2 k lines after about 1.5 hours; the power-animation system (54 cast profiles, 270 generated clips, 115 sounds, the
proof) took about 2 hours of commits from approval (12:24) to its last phase (14:25) on 27 Oct, against its plan's estimate of 20 hours,
because most of it was generated from tables. This plan is more hand-written: about 250 mechanics (60% shared riders), 63 look hooks
and 5 primitives, the UI and the moment. My estimate for one builder:
- L0 + L1 (proof): **about 3 hours**;
- L2-L6: **about 7-11 hours**;
- total **about 10-14 hours** of builder time, plus Euan's look at the proof.
Uncertainty: the look work can run long if the proof sends us back; the riders and data are the predictable part.

### 8.5 One session or two
One session holding the whole context is the default (Euan's rule, 28 Oct). After L1 the families are independent files (catalog class,
`ladder/<Family>Looks`, the ladder table rows), so two sessions could take L3-L6 in parallel and save about 4-5 hours of calendar time
for roughly the same usage plus a second session's reading. My recommendation is one session; it's Euan's call (decision 11).

## 9. Decisions for Euan (recommended answer first)
| # | Question | Recommended | Why |
|---|---|---|---|
| 1 | What a level-up gives | **A named step: a new look layer and a change in play every level; a new gesture and sound at 3 and 5; the Awakened form at 5** | "Not just a little bit more damage": every level is a change in kind, and you can see it |
| 2 | Max level | **Keep 5** (4 steps per power) | Every step can be real; more steps dilute. The format allows more later if you max a power and miss levelling |
| 3 | Strength | **Neutral: +6% a level flat, the steps carry the rest; level 5 about as strong as today** | Gates stay balanced; the reward is the new shape. Alternative: keep +12% and add steps on top (~×1.8 at 5) |
| 4 | What the K menu shows | **The whole ladder: names and one line for every step, the next one in full** | You see what you're working toward; these are your own powers, not undiscovered world content |
| 5 | The evolution moment | **Full window and bloom when calm; a sting and the key flaring in a fight** | Never interrupts a fight; always explains what changed |
| 6 | Proof first | **Yes: the Beam's whole ladder, the starters' level 2, the moment, the K menu, then your look in game** | Taste is the risk; one power done fully settles the bar before 62 more |
| 7 | Every power can level through use | **Yes, with per-minute caps** (Wall Stride, Water Run, Danger Sense, constructs, sight, movement) | "You can always level up your powers" |
| 8 | Order after the proof | **The starters, then family by family** | The powers everyone has first |
| 9 | Names on steps | **Yes** (Beam → Helix → Lance → Corona → Sunspear) | Names make a step a thing you got; plain words under each |
| 10 | Others see your level | **Yes, always** (their own effects setting thins it, as today) | Multiplayer: a level-5 friend should look it |
| 11 | One builder or two after the proof | **One** | Your rule; two saves about 4-5 hours of calendar time |
| 12 | Codex's Power Core hover | **Shows the next step's name and line from the ladder** | One source of level text; the orchestrator tells Codex |

## 10. What I did not verify
- The looks are described, not seen: no game launch, no tour (builders run none). The showcase command is how Euan sees them.
- The frame cost of today's power effects (no timer exists; L0 adds it).
- The static scan follows helpers by name; I hand-checked the beam, constructs and passives, not every helper of every power.
- Mastery earning for utility powers: rates are a design, not yet run through the sim.
