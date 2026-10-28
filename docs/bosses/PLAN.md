# Bosses and creatures, levelled up (plan, 28 Oct 2026)

Euan, 28 Oct: "The next big thing I want to work on here is basically like leveling up the whole combat with bosses
and stuff." This plan follows his planning process (`.claude/PLANNING-PROCESS.md`). Nothing here is built yet. It
stops for his approval. The approval page carries the same decisions in short.

Reference images (his): `/Volumes/MEDIA/boss-ref/euan-ref-common-boss.png` (the horned crystal-core brute: "the kind
of common bosses I want"), `euan-ref-colossus-secret-boss.webp` (the stone colossus: a secret boss "at some point"),
`euan-unit-zero-today.webp` (Unit Zero today).

---

## 1. What he needs, and how we'll know it worked

**The job.** Fights with bosses and creatures should feel like the rest of AWAKEN now feels: Minecraft, raised to AAA
polish. A boss should look like nothing else in the game, move like a living, heavy thing, fight with its own moves,
and read clearly on screen. And making the next boss should be easier than making the last one.

**The pain, in his words, and what it means:**

| He said | What it means |
|---|---|
| "all the bosses ... don't look good" | The bodies are borrowed. A Lord looks like a big zombie, iron golem or evoker. |
| "they move very statically, the same way vanilla mobs do before the add-on" | Nothing a boss does is performed by its body. It stands still while a ring of dust appears. |
| "they don't move uniquely ... They have their own moves" | Each boss needs a move set you can learn by watching it. |
| "I don't like their animations ... very, very basic" (all our mobs) | Our own creature bodies run on sine waves or simple key poses. Fresh Animations on vanilla mobs looks more alive. |
| "the boss bar just looks like a vanilla boss bar ... I don't know what the color means" | Use the HoloLens look, and the plates' colours: blue, orange, red. |
| "maybe the plate moves to something and it's a special plate for bosses" | When the fight starts, the boss's plate leaves its head and becomes the boss plate. |
| "a repeatable system ... so it becomes easier over time to make variation" | A kit: body plans, a move library, and tools, so a boss is assembled and then given its own signature. |
| the colossus: "leave the infrastructure ... maybe build one ... don't put it into the map ... if it's even possible ... without destroying performance" | Prove the cost first, then build one colossus in a test arena only. |

**A strong result** (what he should see):
- Every boss has a silhouette you could name from its shadow.
- Every boss performs 6 to 10 moves with its body. Each move gathers (you see it coming), strikes, and recovers (your
  opening). At least one is a combo, one is a delayed blow you have to read rather than time, one closes the gap, and one
  covers an area.
- A phase change is a 2 to 4 second performance: armour falls off, a core opens, new moves appear. The line it speaks
  shows under the plate, never in chat.
- Feet stay planted on the ground. A heavy boss's steps thud and shake the camera a little. A hit lands from the limb
  that swung, not from the middle of the body.
- The boss plate looks like the creature plates. Its colour means the same thing: blue you should win, orange a real
  fight, red only for an expert. Phase marks sit on the bar, and its stance strip shows when you can break it.
- New creatures and bosses come from the same kit, and each one looks and moves differently.

**A weak result:** new textures on the same vanilla bodies; or nicer idles but moves still shown as dust rings; or a
new bar that still uses colour for no reason.

**Unacceptable:** frame rate falling below the SPEC budget in a gate fight (p50 ≥ 110 fps on the M4, ≤ 60 combat
entities); a boss you can't read (a blow with no tell, a tell that lies); the gate's boss bar rules broken (the Codex
fix that hides a Lord's bar until you meet it); save or multiplayer breakage; anything that needs an optional pack mod
to look right.

**Non-goals.** No new bosses in the map this round (the colossus stays in a test arena). No new gate themes. No change to
loot, drops or the Lord-forged weapons. No first-person animation work (that's the anims module). No change to the
Living Wilds forms on vanilla monsters (Fresh Animations already does that job).

**What must not regress:** the 12 named beasts' current bodies and clips, the life layer (§23), every Arms rule (tells,
classes, deflect, posture, Execution: §25), the Codex gate room and boss bar rules (in flight on
`codex/gate-room-lifecycle`), the Breaks' roaming Lord, every GameTest.

---

## 2. What's there today (verified)

### 2.1 The bodies

I read every bestiary file (`mobs/def/*Bestiary.java`, 199 creatures) and every renderer. Full table in Appendix A.

| Who | Count | What they wear today | How they move |
|---|---|---|---|
| Gate Lords | 15 | 8 vanilla bodies scaled ×1.7 to ×4 (iron golem, piglin brute, evoker, ghast, bee, drowned, creaking, zombie); 5 of our old cube bodies (Bell, Lantern, Serpent ×2, Shade); 2 the player model (Unit Zero, Mirror Knight) | Vanilla bodies: Fresh Animations' walk and idle (EMF picks up any renderer that bakes a vanilla layer). Cube bodies: sine waves (`client/model/Motion.java`) plus the life layer. Player model: vanilla's arm swing only. Fresh Animations doesn't touch the player model and our renderer never uses the player animation library. |
| Descent guardians, Sovereigns, the Shadow | 19 | 13 vanilla bodies, 4 cube bodies, 2 player models | the same |
| Nightmares | 7 | 6 vanilla bodies, 1 player model | the same |
| Gate Wardens | 15 | all vanilla bodies (the warden, ravager, iron golem, enderman...) | Fresh Animations |
| Named beasts (overworld) | 12 | our hero rigs, modelled at true size (`tools/art/specs/fauna_heroes.py`, `fauna3.py`) | hand-keyed clips per move (wind-up and strike stretched to the real ability), walk locked to distance, life layer on top |
| Other creatures of ours | ~130 | ~100 gate monsters and elites on vanilla bodies; 14 of our cube bodies (moth, stag, tortoise, ray, scarab, lizard, snail, mushroom, marmot, plus the 5 above); 10 cave and water rigs; owl and firefly rigs; 7 vanilla animals repainted | Fresh Animations on the vanilla bodies; sine waves on the cube bodies; key clips on the rigs |

So **34 of the 41 bosses wear a body that already exists in vanilla or in our own early cube set, and 5 wear the player
model.** Not one boss has a body designed for it. Scaling a vanilla body also scales its pixels: a ×2 zombie has
pixels twice the size of the world's, which is a large part of why they read as cheap.

### 2.2 How a boss fights

`AwakenBoss` + `AbilityRunner` + `Telegraph`:
- A boss is a list of phases, each a list of abilities (25 kinds: SLAM, BEAM, RAIN, BURST, BLINK, SUMMON...). An ability
  winds up for N ticks with **vanilla dust particles** on the ground (`Telegraph.ring/line/cone`: `DustParticleOptions`),
  the glow brightening, and then fires from the entity's centre.
- **The body does nothing for a move.** Our own bodies tremble a little during a wind-up (the life layer's tension
  tremble). Vanilla bodies keep playing Fresh Animations' idle. Melee is `MobGoals.ToldMelee`: the body stops for 8 to 10
  ticks, then the vanilla arm swing.
- **The hero cue system exists but isn't used by bosses.** `HeroRenderer` maps an ability to `<move>_windup` and
  `<move>_strike` clips and stretches the wind-up to the ability's real wind-up. Only `FaunaMob` (the named beasts) sends
  cues. `AwakenBoss` doesn't.
- A phase change (`AwakenBoss.enterPhase`): 30 ticks invulnerable, a vanilla explosion particle, `BOSS_PHASE` sound, a
  Monster-Hunter part drops (`MobLoot.breakPart`), **the line goes to chat** (`sendSystemMessage`), and the bar changes
  colour.
- The colour: phase 0 uses the def's colour (Unit Zero BLUE), then `PHASE_COLOURS` = RED, PURPLE, WHITE. **The colour
  means nothing a player could learn.** Wardens are always RED.
- The bar is vanilla's `ServerBossEvent`, NOTCHED_6 for Lords, NOTCHED_10 for Sovereigns. Nothing draws it but vanilla.
  `signal.client.TrackerHud` only listens to its position.
- Voices are borrowed vanilla sounds (`Model.Voice`: Unit Zero is PLAYERLIKE, the Ember Tyrant a piglin).

### 2.3 What we can build on (strong, keep)
- **The hero rig pipeline.** One Python source of bones, packed UVs, paint and keyframes (`fauna_rig.py`,
  `fauna_anim.py`, `heroes.py`) → plain data (`HeroData.java`, parsed by `HeroRig`, with no Minecraft classes, so the
  server can read it too) → `HeroModel` bakes cubes into a LayerDefinition and clips into vanilla `KeyframeAnimation`s,
  blended like a small graph. Previews render the game's own poses offline. 11 rigs with about 15 clips each were built
  in about 2.5 hours (git: 5416c6a1 → 1a3dc9ed, 23-24 Oct).
- **The life layer** (§23): breathing, blinks, the slow head, springs on ears, tails and manes, idle fidgets, eased graph
  weights, the topple. Bound by bone names, so any new rig gets it for free.
- **Arms** (§25): every blow has a class (LIGHT, HEAVY, PERILOUS, GRAB), synced tells, deflect, posture (bosses 100,
  breaks for 40 ticks), Execution capped at 8% of a Lord's health, hit-stop and camera kick (`core.api.CombatFeedback`),
  `Arms.tellSound(kind)`.
- **The effects engine** (`powers.client.fx.FxClient`): our own particles with one budget; `tellMark` already draws an
  enemy tell's marks for the Living Wilds' forms.
- **The plates** (§21.1, `living.client.LivingPlates` on `holo.client.HoloWorld`): name, level chip, ten-segment bar with
  an amber drain, the stance strip, the rim. **`PlateRules.heat`** gives grey / blue / orange / red against the viewer's
  level (decision 15 of §21.10). This is exactly the colour language he asked for.
- **The combat sim** (`tools/sim/combat_sim.py`, `docs/combat/sim-2026-10-26.md`): every Lord against four player
  profiles. As built, Lord fights fail most targets: one blow takes 12-30% of an on-level player's health, ground Lords
  die in 40-60 s (target 80-120 s), fliers orbit out of reach. The recommended retune **still waits on Euan's three-Lord
  session**.
- Mesh tech: `arms.WeaponMesh` (our own binary mesh format and render path), Far Sight's persistent GPU buffers
  (`farsight.client.FarRenderer`) and its voxel mesher. NeoForge's `PartEntity` (multipart hitboxes, as the dragon uses;
  `Level.getEntities` includes parts, so Arms' sweeps hit them).

### 2.4 Root causes (biggest first)

| Symptom | Cause | Evidence | Fix |
|---|---|---|---|
| Bosses look cheap and samey | No boss has its own body; vanilla bodies scaled up, pixels scaled too | Appendix A; `Model` enum | Every boss gets its own body at true size |
| Bosses "don't move uniquely" | Moves are data with particle tells; the body takes no part. The cue system was never wired to bosses | `AbilityRunner`, `Telegraph`, `HeroRenderer` only for `FaunaMob` | Moves become performances shared by client and server |
| Moves land from the wrong place | Abilities fire from the entity's centre or the target; no limb has a hitbox | `Abilities.java` | Hit volumes travel with the limb that strikes |
| "Basic" creature animation | Cube bodies are sine waves; rigs are 8-key sampled cycles with no ground contact, no turn steps, no reactions by direction | `Motion.java`, `fauna_anim.cycle` | Kit runtime: foot planting, turns, reactions, more keyed clips |
| Bar's colour means nothing | Def colour, then a fixed phase cycle | `AwakenBoss.PHASE_COLOURS` | Boss plate in heat colours |
| Phase lines in chat | `sendSystemMessage` | `AwakenBoss.tell` | Subtitle under the plate |
| Lord fights too short and spiky | Damage and health not tuned to the move-based fight | combat sim | Retune with the new move sets (needs his OK) |

**Not yet proven (check in game, not by me):** that every one of our vanilla-bodied gate monsters really animates the
Fresh Animations way in his play copy (SPEC §23 says EMF picks them up; the creature-life hand-over said the same). If a
gate zombie of ours moves like vanilla's old zombie in his game, the cause is different and much cheaper, and I'd fix
it first. **One look settles it**: a gate zombie next to a wild one.

---

## 3. The options, and what I recommend

**A. Repair.** Keep the bodies. Add the boss plate. Drive vanilla bodies' arms through EMF `.jem` animations or by posing
their model parts after Fresh Animations. Cheap (a few hours), but every boss still has a vanilla silhouette, EMF is an
optional pack mod (without the pack the moves vanish), and posing parts under EMF fights its own animation. It fixes the
bar and little else.

**B. The Creature Kit (recommended).** Every boss and, in waves, every creature of ours gets a body built from a
**body plan** (a standard skeleton with named bones and part slots), a **move library** written once per plan, and a
**signature** added by hand. Moves are performances that the client draws and the server judges from **one move table**.
It extends the hero pipeline we already trust, adds the three things it lacks (ground contact, moves the server can see,
fight scripts) and gives the boss plate. A new boss means choosing a plan, setting its proportions, choosing its parts
and paint, picking moves and keying one or two signature moves. That's the "system" he asked for.

**C. New technology.** Smooth skinned meshes, GeckoLib, or Blockbench import. Skinned meshes don't look like Minecraft
(his references are blocky, and he wants "MC on a whole new level", not another game). GeckoLib would be a new
dependency that lags Minecraft releases and draws outside our Lens and Iris paths, for clips our pipeline already
makes. Blockbench import only helps a human artist; our builders author in code and can render what they make
offline. Kept only where B can't do it: **the colossus's body** (a voxel mesh with multipart hitboxes, §4.6).

**Why B:** it's the only option that fixes the cause (bodies with no part in the fight) for every boss. It reuses what
works (rigs, life layer, Arms, plates, sim). It costs the most work, but at our pace that's days, not months.
**What would change my mind:** if the one-look check in §2.4 shows Fresh Animations isn't reaching our gate monsters,
fix that first. And if the showcase bosses (phase 1) don't impress him in game, stop before the roll-out and rethink
the look, not the mechanism.

---

## 4. The design

### 4.1 The kit: body plans, parts, moves, style

**Body plans** (each a skeleton with standard bone names, so the life layer, foot planting, hit volumes and the move
library bind by name):

| Plan | Bones (short) | For |
|---|---|---|
| Brute | pelvis, spine ×2, chest, neck, head, jaw; shoulder, upper arm, forearm, hand, 3 fingers ×2; thigh, shin, foot ×2 | heavy bipeds: Ember Tyrant, Gearwright Colossus, Ossuary King, Furnace Engine, Yeti, Sexton |
| Duelist | as Brute, lighter, plus a weapon bone | fast bipeds: Unit Zero, Mirror Knight, Shadow Regent, the Signal's Shadow |
| Caster | pelvis, spine, chest, head, arms, a robe or skirt chain, a float bone | robed or floating: Abbot of Frost, Drowned Archivist, Choirmaster, Architect, Hollow Herald |
| Beast | four legs (three bones each), spine ×3, neck ×2, head, jaw, tail chain | Thornback, Sovereign of Hunger, the Rot, the Sweeper, the named beasts' quadrupeds |
| Serpent | head, jaw, 8 to 16 body links, fins | Glass Serpent, Sky Leviathan, Dune Wyrm, Plumed Coil |
| Flier | body, head, wings (3 bones each) or a hover core, dangling chains | Mother of Spores, Hive Regent, Sovereigns of Sleep, Storms and Ash |
| Crawler | body, head, 6 to 8 legs (three bones each), abdomen | Rootmother, Weaver Queen, the Thousand Legs |
| Construct | free bones with no fixed anatomy | Bell Warden, Lantern Broker, the Fuse, the Nothing |
| Colossus | Brute, at 40 to 60 blocks tall, voxel body, multipart | secret bosses only (§4.6) |

**Parts** are kit pieces placed on slots (horns, crests, a crystal core, shoulder plates, spines, a crown, a halo,
weapons), with their own paint and glow. The Living Wilds forms already enforce attachment rules (`forms_geom.py`:
attached, off the face, seen, in reach). The kit reuses them so no part floats. Parts can **break off** in a phase
change, which makes the existing Monster Hunter part drop (`MobLoot.breakPart`) visible: the plate you just got came
off its body.

**The move library** is one Python generator per move per plan, written once with parameters: `overhead_slam(weight,
reach, hands)`, `sweep(side, height)`, `backhand`, `stomp`, `leap_slam`, `shoulder_charge`, `grab_throw`, `roar`,
`stagger`, `kneel` (posture broken), `rise`, `phase_transform`, `collapse`; the Duelist's `lunge`, `three_cut`,
`blink_strike`, `backstep`, `parry_stance`; the Caster's `channel`, `summon_raise`, `cast`, `ward`, `drift`; the Beast's
`pounce`, `bite_chain`, `rear`, `tail_whip`, `shake`; the Serpent's `coil`, `strike`, `dive`, `erupt`, `constrict`; the
Flier's `dive`, `strafe`, `buffet`, `rain`, `land_slam`; the Crawler's `leg_stab`, `spray`, `rear_up`, `scuttle_charge`.
Each generator writes, from the same numbers:
- the clips (`<move>_windup`, `_active`, `_recover`, with an optional hold at the peak of the wind-up);
- its **root motion** (how far the body travels through the move);
- its **contacts** (which bone hits, on which tick) and **hit volumes** (that bone's path, sampled per tick, as
  capsules in the body's own space);
- its timing in ticks, its class (LIGHT, HEAVY, PERILOUS, GRAB) and its tracking (how fast it may turn during the
  wind-up, and the tick when it locks).

**Style** keeps two bosses on one plan from moving alike: `weight` (a slower, longer gather and an overshoot when it
settles), `temper` (feral: twitchy and uneven; regal: smooth and slow), `reach` and `asymmetry` (a dominant arm, a limp,
a hunch). Then **one or two signature moves are keyed by hand** for every boss, the moves people will remember.

### 4.2 The runtime (client, `mobs.client.kit`)

A small pose graph on every kit body. It replaces nothing that works; it adds layers:
1. **Locomotion**: idle (two or three variants), walk and run blended by speed and locked to distance (as hero rigs do
   now), strafe and back-step for Duelists and Casters, and **turn steps**: when the body has to turn more than 45°, it
   steps round with its feet instead of spinning on the spot.
2. **The action layer**: a move's wind-up, active and recovery clips, eased in and out (0.1-0.25 s). A held wind-up
   (the delayed blow) holds its peak pose with the life layer's tremble.
3. **Ground contact**: two-bone foot planting for legs (Brute, Duelist, Beast, Crawler). While a clip marks a foot as
   planted, the foot stays where it landed, on the ground's real height (a short block query per foot, only within 32
   blocks, cached per tick), and the pelvis lowers to reach. Slopes and steps stop floating feet. Heavy feet raise dust
   and, for big bodies, thud.
4. **Reactions**: hit reactions by direction (struck from the left, the body twists right), a heavier reel for a HEAVY,
   the stagger and the **kneel when posture breaks** (held while broken, then a rise), and a phase transformation clip.
5. **The life layer on top** (unchanged: breath, blinks, the head and eyes, springs, fidgets).

**Synced state**: the move id, its start tick and its hold (in the existing `DATA_TELL` slot, widened). Everyone sees
the same move at the same time. A late joiner computes where the move is from its start tick.

### 4.3 Moves on the server: what you fight

`mobs.boss` (new package in the mobs module):
- **The move table** (`data/awaken/kit/moves/<boss>.json`, schemaVersion 1, generated): timing, class, root motion,
  hit volumes per active tick, tracking. Read by the server and the client from one file, the way Arms reads
  `archetypes.json`.
- **Hit volumes**: during a move's active ticks, the server tests its capsules (rotated by the body's facing) against
  players' boxes. A blow lands where the fist or the blade is, and a near miss whiffs. The existing ability payloads (a
  slam's ring, a beam, a rain of marks) still do their effect, but they start where the move says: the fist that hit
  the ground, the palm that fired.
- **Tracking**: a wind-up turns toward you at the move's turn rate until its lock tick, then commits. That makes a
  sidestep at the right moment a real answer.
- **The fight script** (`data/awaken/kit/fights/<boss>.json`, schemaVersion 1): per phase, a pool of moves with weights
  and conditions (range, the target behind it, airborne, healing, a ranged player far off), **strings** (a combo whose
  next step can branch: three cuts, or two and a grab), **holds** (a delayed blow: HEAVY moves only, at most 0.6 s extra,
  and only on moves whose wind-up pose is unmistakable, so a delay is read from the body, never guessed), a recovery
  you can punish, an **intro** (where it waits as you walk in, what it does when the door seals), the **transition**
  into each phase (a 2-4 s performance, invulnerable, parts break off, the line on the plate), an **enrage** under 15%
  (faster, never more damage), and the **death** (2-4 s; it falls its own way).
- **Every boss's move set has**: a gap-closer, a combo, one delayed blow, an area move, an answer to a player who keeps
  away (a ranged move or a leap), an answer to a player behind it, and a signature move with its own look. A GameTest
  checks this for every boss.
- **What stays**: `AwakenBoss`'s home and arena rules (and the Codex fixes on top of them), phases, parts and drops;
  `ToldMelee` for everything without a fight script; every Arms rule. Posture break pauses the script (already: "a
  broken stance starts no ability").
- **Tuning**: Lord damage by rank and Lord health from the combat sim's recommendation, re-run with the move tables
  (the sim learns to read them). Targets stay the sim's: a Parrier on level wins 80% or more in 80-120 s, an Expert 98%
  in 75 s or less, a Tank 10% or less. **This needs his OK** (it changes powers players' fights too). It was already
  waiting for him.

### 4.4 The Boss Plate

```
  before the fight: the plate floats over the boss, as plates do now

  the fight starts (the bar's usual rules: you met it, you're in its room)
  → the plate lifts off its head, flies to the top of the screen (0.45 s) and opens:

      ┌─[ Lv 31 ]──────────────────────────────────────────────────────┐
      │  UNIT ZERO                                                      │   name, big; rim and brackets in its own glow
      │  Lord of the Precursor gate                                     │   title (beasts: "Terror of the Sky Vale")
      │  ▰▰▰▰▰▰▰▰▰▰▰▰▰▰◆▰▰▰▰▰▰▰▰▰▰▰▰▰◆▱▱▱▱▱▱▱▱▱▱▱▱▱▱                │   health in the heat colour; ◆ = phase marks
      │  ▔▔▔▔▔▔▔▔▔▔▔▔ stance                                           │   amber as it strains; gold BROKEN = strike now
      └─────────────────────────────────────────────────────────────────┘
                          “Countdown: three.”                              its line, 3 s, under the plate (never chat)
```
- **Colour means one thing**: the bar and the level chip take the plate's heat colour against *your* level (grey: far
  below you; blue: you should win; orange: a real fight; red: only an expert, the chip pulses). The boss's own colour
  is only on the rim and brackets, so it's still Unit Zero's blue.
- **Phase marks** where the phases begin. Crossing one flashes the mark, and the line comes up under the plate.
- **Stance strip** (Arms' posture) under the health; **BROKEN** in gold while it's broken.
- **A signature move's name** comes up small under the plate for 1.2 s ("ZERO POINT"): only signature moves, never
  every swing.
- **Wardens** get a smaller plate; summons get none (their world plates stay).
- **The end**: on a kill the segments shatter and the name holds for a beat. When you leave, it flies back down to the
  boss's head.
- **Reduced motion**: no flight; it fades in place.
- **How it's built (no mixin)**: the server keeps its `ServerBossEvent` (so who sees a bar keeps the Codex rule:
  `GateHooks.showsBossBar`) and sends one small payload binding the bar's UUID to the entity, its phase marks, title and
  level. The client cancels vanilla's drawing for our bars only (`CustomizeGuiOverlayEvent.BossEventProgress` is
  cancellable; we set its increment so others stack) and draws the plate on the Holo glass. Other mods' and vanilla
  bars (the wither, the dragon) stay vanilla. The Breaks' roaming Lord and the Hearth trials' bars move onto the plate
  too.
- **Who owns it**: the plate's look lives with the plates (`living.client`, next to `LivingPlates`); the mobs module
  sends the data through a small `core.api.BossPlates` interface (SPEC §12).

### 4.5 Look, sound and effects

- **Look** (his reference: the horned crystal-core brute): chunky blocks with a strong silhouette, one oversized
  feature (forearms, horns, a halo, a cap), and one **light story**: a core, eyes and cracks that glow with the fight
  (brighter in a wind-up, cracking open in phase 2, white-hot at the end). Modelled **at true size**, never scaled, so
  its pixels match the world's. Painted with the existing painter (bevels, edge light, contact shadows).
- **Tells in our own look**, not vanilla dust: `FxClient.tellMark`-style marks by class (LIGHT white, HEAVY amber with
  a glint, PERILOUS red, GRAB violet; the colours Arms already uses) plus `Arms.tellSound(kind)`. The body's pose is the
  first tell. The mark on the ground confirms it.
- **Sound**: every boss its own voice (idle, roar, hurt, death), footfalls by weight and material, swings and impacts
  per plan (`tools/art/specs/audio_*.py` and `audiokit`). No borrowed vanilla voices for bosses.
- **Weight**: footfalls of big bodies and every slam give a small camera kick (`CombatFeedback.kick`), fading with
  distance, off with the camera-shake setting and reduced motion. Dust at the foot or fist that struck.

### 4.6 Secret bosses and the colossus

**Can it be done without wrecking performance? Yes, with a measured gate.** What a 40-60 block colossus costs:

- **Drawing.** I voxelised a rough colossus the size of his reference (about 60 blocks tall, stepped stone plates and
  spines; the probe script is `docs/bosses/probe/colossus_quads.py`): **about 6,900 visible block faces** (3,600 if
  merged). Drawn the normal entity way, that's about 28,000 vertices a frame, about what 200 zombies cost, twice with
  Iris's shadows. My estimate is 0.6-1.5 ms a frame on the M4. **Not measured.** If it's over 1.5 ms, each bone's mesh
  goes on the GPU once and only its matrix changes each frame (Far Sight's pattern), about 0.1 ms. A lower-detail mesh
  (merged faces) beyond 96 blocks.
- **Hitboxes.** 12-16 boxes that follow its bones (NeoForge `PartEntity`, as the dragon does; Arms' sweeps already hit
  parts). Weak points (a glowing core, a rune on its back) take full damage, and armoured parts take little.
- **Being seen.** A custom culling box and render distance, and a tracking range of 16 chunks (the server's view
  distance still caps it).
- **The server.** One entity with 16 parts and scripted steps inside its arena, with no vanilla pathfinding (it can't
  plan for a 60-block body anyway). The bone positions its hitboxes need are baked into its move table, so the server
  never evaluates a clip. Cost: negligible.
- **Where it lives.** A gate floor is 32 blocks tall, so it can't be there. For now: a test arena only (a flat ruin
  plain in a small test dimension) reached by an admin command, `/awaken bosses arena <id>`. Not in the map, as he asked.
  The **secret boss registry** (`data/awaken/kit/secret/<id>.json`: its arena, how it's summoned, its music, its reward)
  is the infrastructure. Placing one in the world later is a data change plus a structure.
- **Climbing it** (Shadow of the Colossus): its plates can be solid (`canBeCollidedWith`, as boats are). Carrying the
  player with a moving limb needs the body module's climbing to follow a moving surface. That's real work and a real
  risk, so it's a **separate experiment after the prototype, with its own OK**.

**The gate:** C0 builds a disposable probe (a colossus body standing in the test arena, no AI) and one perf run on the
Mac (perf runs stay on the Mac, `slot.sh tour`; the orchestrator asks Euan). Pass: ≤ 1.5 ms a frame for the body at 64
blocks, with Iris and Lens on. Only then C1: the prototype with 6-8 moves, weak points, arena and sound.

### 4.7 Data, saves, compatibility

- **Source of truth:** `tools/art/specs/kit/` (plans, move library, parts) and one spec per creature
  (`tools/art/specs/bosses/<id>.py`). Generated outputs: rig and clips as resources (`assets/awaken/kit/rigs/<id>.json`,
  instead of more giant generated Java files like `HeroData.java`), moves and fights as data
  (`data/awaken/kit/moves|fights/<id>.json`), textures as now. All with `schemaVersion`. All deterministic (seeded), so a
  rebuild only changes what you touched.
- **Saves:** `AwakenBoss` keeps `awaken_phase` and `awaken_parts_broken`. New: nothing saved mid-move (a reload starts it
  idle, as now). The entity ids don't change, so old worlds keep their bosses, and the new body appears on the next
  load.
- **Old bodies stay as the fallback.** If a creature's kit rig or move table is missing or fails to load, it wears its
  current body and fights as now (logged once). Each creature switches to its kit body only when its tests pass. No
  flag day.
- **Multiplayer:** server-authoritative moves and hits; the plate's heat colour is computed per viewer; nothing
  player-specific changes the boss (patent rule holds).
- **Named beasts:** their hero rigs move onto the kit runtime (foot planting, turn steps, the server move table) without
  being redrawn, after Codex's Plumed Coil navigation fix (`codex/fauna-navigation`) lands.

### 4.8 What can go wrong

| Case | Detection | What happens | Test |
|---|---|---|---|
| Rig or move table missing or malformed | load check, schema | old body, fights as now, logged once | `kit_tables_load` |
| Client clip and server move disagree (lengths) | load check compares lengths | the server's timing wins; the client stretches the clip | `kit_clip_matches_move` |
| A blow with no readable tell | every move's wind-up ≥ its class minimum (8 ticks, held ones longer) and its pose differs from idle | build stops | `kit_every_blow_told` |
| A hold that can't be read | holds only on HEAVY moves with a pose distance threshold | build stops | `kit_holds_readable` |
| Hit volume lands through a wall | capsule test only against players in the boss's line of sight | no hit | `kit_hit_volume_walls` |
| Player lags | Arms' latency grace already covers guards | unchanged | existing |
| Joining mid-move | move start tick synced | sees the move where it is | `kit_move_sync` |
| Boss reset (everyone left) | existing `reset` | script restarts at its intro, plate flies back | existing + `kit_reset_script` |
| Phase skipped by burst damage | phases enter in order, one transition each | no skipped performance | `kit_phase_order` |
| Too many bosses on screen | plate stacks 2, more queue | | `boss_plate_stack` |
| Kit body over budget | lab counts cubes and faces per body; perf run in phase 1 | build stops over budget | `kit_body_budget` |
| Colossus over 1.5 ms | C0 perf run | persistent-buffer path, or stop | perf report |

### 4.9 Performance budgets
- A kit body: at most 1,200 cube faces (a vanilla iron golem is about 100; a hero rig 300-600). Drawn through the normal
  entity path, so Iris, Lens and Entity Culling treat it like any mob.
- Kit runtime: no allocation per frame (render states carry references, as the life layer does); foot planting only
  within 32 blocks; at most 0.3 ms a frame for 60 kit bodies.
- Server: hit capsules only while a move is active; a fight script step is a table lookup; no per-tick allocation.
- The gate fight budget holds: p50 ≥ 110 fps with 60 combat entities on the M4. Measured once in phase 1 on the Mac.

---

## 5. Before and after

**Unit Zero (the one he showed), the C-rank Precursor gate's last floor.**
- *Before:* a blue player-model figure walks at you with vanilla's arm swing. Chat: "Unit Zero: A voice counting down in
  no language." A blue vanilla bar with 6 notches. A line of blue dust appears on the floor, and a beam leaves its chest.
  At two thirds: a puff of explosion, "Countdown: three." in chat, the bar turns red. At a third the bar turns purple.
  You don't know why.
- *After:* it kneels in the middle of the arena, its halo dark, its plate over it. The door seals. The plate lifts to the
  top of the screen: **UNIT ZERO**, Lord of the Precursor gate, its level in orange (five above you: a real fight). It
  unfolds over two seconds, the halo lights, and under the plate: "A voice counting down in no language."
  - **Zero Lance**: it plants its back foot, draws its palm back, and the halo spins up and tilts at you. It follows
  you until the last third of the tell, then fires a red PERILOUS beam. You step aside as it locks.
  - **Phase Step**: it folds into a line of light and unfolds beside you, already cutting (a LIGHT you can deflect). The
  side it unfolds on shows which way it'll cut.
  - **Severing**: three palm-edge cuts. The third is held for a beat (the delayed blow: its arm stays cocked high),
  so you deflect the rhythm and then wait for the third.
  - At two thirds it stops, its forearm plating ejects (the Zero Plating drops where it falls), the phase mark flashes,
  and "Countdown: three." comes up. **Orbital Mark**: the halo rises into the sky and four marks lock under you.
  - At a third its core opens and counting digits orbit it. **Zero Point**: it rises a block, curls in, the arena's
  lights dim, and a ring bursts out. Step inside it.
  - Fill its stance and it drops to one knee, sparking, with **BROKEN** in gold on the plate. Your heavy executes.
  - It dies with the halo stopping, the digits falling to zero, and the body folding back to the kneel it started in.
  The plate shatters.

**The Ember Tyrant (E, the first Lord many players meet), his "common boss" reference.** A horned brute at true size,
about 6 blocks tall, with a molten crystal core in its chest, oversized forearms veined with magma, and an axe. Moves:
an overhead two-hand hammer (HEAVY), a backhand (LIGHT), an axe chop string, a shoulder charge (GRAB), **Eruption** (it
rears back and slams both fists, and a ring of magma rolls out: PERILOUS, the signature), and **Meltdown** (phase 3: the
core goes white, its shoulder plates fall, the floor round it melts. Step out). Each footfall thuds.

**Mother of Spores (E, the one he just saw glitching).** No longer a ghast: a floating fungal matriarch, a vast cap with
a veil of gills and tendrils, a pale face under it, spore sacs. **Spore Rain** (she rises, the cap pulses, circles of
spores fall), Tendril Lash, **Birthing** (her sacs burst into the colony), and **the Drop** (she falls and the cap slams
the floor: a shockwave you jump or roll).

**A missing piece.** If a boss's kit data fails to load, it wears today's body and fights as today. The plate still shows
(the plate doesn't depend on the kit). Nothing breaks, and the log says which file.

**How it flows**
```
tools/art/specs/kit (plans, move library, parts) + specs/bosses/<id>.py (proportions, parts, paint, picks, signatures)
        │ build_all.py kit <id>      (deterministic; previews and checks run here)
        ├── textures/entity/mobs/<id>*.png
        ├── assets/awaken/kit/rigs/<id>.json        → client: KitModel (cubes + clips) → pose graph + life layer
        └── data/awaken/kit/moves/<id>.json          → server: BossMoves (timing, class, root motion, capsules)
            data/awaken/kit/fights/<id>.json         → server: FightScript (phases, pools, strings, holds, transitions)
server: FightScript picks a move → syncs (move, start, hold) → runs root motion, tracking, capsules, payload abilities
client: reads the sync → plays the clips, plants feet, reacts → Boss Plate (living.client) reads BossPlates payload
```

**The Creature Lab** (offline, no game): for every move, a contact sheet of frames with the striking bone's path drawn,
the hit volumes overlaid, foot contacts marked, and the tell's length; an animated GIF of each move and of the
locomotion; and checks that stop the build: feet sliding more than 0.1 block while planted, a wind-up pose too close
to idle, a part floating or clipping into the body, a move shorter than its class's tell, a body over its budget. This
lets a builder see the motion without the game. Today's tools only show key poses. It grows from
`tools/art/anim_lab.py` and the hero previews.

---

## 6. Proving it, and the order of work

**Baseline (today):** 0 of 41 bosses have their own body or perform a move with their body; 0 have a combo, a hold or a
transition performance; 41 of 41 show a vanilla bar whose colour is arbitrary; Lord fights fail 4 of 6 sim targets.

**Checks at every step:**
- GameTests (`kit_*`, `boss_plate_*`): tables load and match the clips; every boss has the required move kinds; every
  blow is told for its class; hits land only in active ticks and only from the striking limb's capsule; move sync for
  late joiners; phase order and transitions; reset; the plate payload; the bar rules (the Codex tests still pass).
  One test per mechanism, the full suite once at hand-off, CI on every push.
- The Creature Lab's checks, at build time.
- The combat sim with the move tables: the sim's targets, before and after, per Lord.
- **His eyes**: builders run no tours. At the end of phase 1 the three showcase bosses need his look in game: an admin
  `/awaken bosses studio <id>` spawns one in a lit test arena, performing its moves in turn, then fighting. That's the
  go/no-go for the roll-out.

**Phases** (estimates at this project's pace: the 11 hero rigs with about 15 clips each took about 2.5 hours. The new
bar asks for more care per body, about 3× that, and bosses add fight scripts and sound):

| Phase | What | Estimate | Gate |
|---|---|---|---|
| 0 | The kit: plans (Brute, Duelist, Flier first), move library v1, runtime (pose graph, foot planting, turn steps, reactions), move table, hit volumes, fight scripts, the Creature Lab, the Boss Plate | 8-12 h | tests + lab checks |
| 1 | Showcase: the Ember Tyrant, Unit Zero, Mother of Spores (bodies, moves, fights, sounds); sim retune; one perf run | 5-7 h | **Euan looks in game** |
| 2 | The other 12 Gate Lords; Caster, Serpent and Construct plans | 14-20 h | sim targets per Lord |
| 3 | The 15 gate Wardens; the Breaks' roaming Lord on the plate | 8-12 h | |
| 4 | The 12 named beasts onto the kit runtime (planted feet, turn steps, server moves, plate) | 4-6 h | after `codex/fauna-navigation` |
| 5 | The Descent: 10 guardians, 8 Sovereigns, the Signal's Shadow; Beast and Crawler plans | 18-26 h | |
| 6 | The 7 nightmares | 4-6 h | |
| 7 | Every other creature of ours: first the 14 cube bodies and the cave, water, owl and firefly rigs; then the ~100 gate monsters on vanilla bodies, one theme at a time (they keep Fresh Animations until their theme's turn) | 30-50 h | per theme |
| C0 | Colossus probe and one Mac perf run | 1-2 h + the run | ≤ 1.5 ms a frame |
| C1 | One colossus prototype in the test arena; the secret boss registry | 8-12 h | his look |
| C2 | Climbing the colossus (experiment) | later | its own OK |

**In all:** about 100-150 hours of one builder's work, most of it the roll-out (phases 2, 5 and 7). Phase 0 + 1 (about
13-19 hours) is the part to approve first. What doesn't compress: build and test time on the shared Mac, the one Mac
perf run, and his time to look at the showcase.

**Splitting:** phases 0 and 1 need one builder holding the whole design. **After phase 1 the roll-out splits cleanly**:
each family (a gate theme's Lord and Warden, a Descent stratum, a Sovereign) is its own spec files and data, and the
kit is fixed by then. Two or three builders in parallel would finish phases 2-7 in about a third of the time. That's
the "truly massive" case, so it's his call after phase 1.

**Rollout and rollback:** each creature switches to its kit body only when its tests pass; until then it keeps today's
body. A client setting `kitBodies` (on) puts every creature back on its old body if something looks wrong (server moves
still run, so fights stay fair). The boss plate has its own setting (`bossPlate`: on; off = vanilla bar).

**Risks:**
- The look is subjective. Phase 1 is the check, before the roll-out commits to it.
- Foot planting on the ground's real height can pop on odd blocks (slabs, carpets). Treat them as their collision
  height and test in the lab.
- In-flight work: `codex/gate-room-lifecycle` changes `AwakenMob`'s bar code (who sees a bar, the " · Warden" name).
  The plate builds on top of it after it merges. `codex/fauna-navigation` touches the flying beasts, so phase 4 waits.
- The sim retune changes powers players' fights too. That's why it needs his OK.

---

## 7. Decisions for Euan

1. **The approach: the Creature Kit** (body plans, a move library, a signature per boss, moves the server sees).
   *Recommended: yes.* It fixes the cause (borrowed bodies that take no part in the fight) and is the repeatable system
   he asked for.
2. **Every boss gets its own body, at true size, never a scaled vanilla one.** *Yes.* Scaled bodies are a large part of
   why they look cheap. Nightmares keep the shape of the fear they come from (the falling, the crowd), rebuilt as
   wrong versions of it.
3. **Look: chunky blocks, one oversized feature, one light story** (core, eyes, cracks that change with the fight), as in
   his reference. *Yes.*
4. **Showcase first: the Ember Tyrant, Unit Zero and Mother of Spores, then he looks in game before the roll-out.**
   *Yes.* Three different body plans; two are E bosses most players meet early, and one is the boss he showed.
5. **The boss plate replaces the vanilla bar for all our bosses and Wardens**; it lifts off the boss's head when the
   fight starts. *Yes.*
6. **Colour means one thing**: the bar and chip use the plates' heat colours against your level (grey, blue, orange,
   red); the boss's own colour is only on the rim. *Yes.*
7. **Phase lines leave the chat** and appear under the plate; signature moves show their name briefly. *Yes.*
8. **Fight tuning**: apply the combat sim's Lord damage and health curve with the new move sets (target: an on-level
   Parrier wins 80%+ in 80-120 s). *Yes.* His look at the three showcase Lords is the three-Lord session it was waiting
   for.
9. **Delayed blows (Elden Ring style)**: only on HEAVY moves, at most 0.6 s extra, only where the held pose is
   unmistakable. *Yes.* Reading beats guessing.
10. **Every creature of ours moves onto the kit, in waves after the bosses.** Gate monsters on vanilla bodies keep Fresh
    Animations until their theme's turn. *Yes.*
11. **The colossus: build the secret boss infrastructure and one prototype in a test arena (admin command only), after a
    perf probe passes (≤ 1.5 ms a frame).** Not in the map. *Yes.*
12. **Climbing the colossus**: a separate experiment after the prototype. *Later.*
13. **Who builds:** this session builds phases 0 and 1 itself. After his look at the showcase, he decides whether to split
    the roll-out across two or three builders. *Decide after phase 1.*
14. **One look now, before building**: does one of our gate zombies move like a Fresh Animations zombie in his game? If
    not, that's a cheaper bug and I'd fix it first. *Please check when he next plays.*

---

## Appendix A: every boss and Warden today

Body: V = vanilla body (scaled), C = our old cube body (sine), P = player model, H = hero rig. Plan = the kit plan it
moves to.

| Tier | Theme | Name | Body today | Scale | Phases / abilities | Plan |
|---|---|---|---|---|---|---|
| Lord | Bell | The Bell Warden | C Bell | 1 | 3 / 9 | Construct |
| Lord | Fungal | Mother of Spores | V ghast | 0.75 | 3 / 7 | Flier |
| Lord | Ember | The Ember Tyrant | V piglin brute | 2.0 | 3 / 8 | Brute |
| Lord | Hive | The Hive Regent | V bee | 4.0 | 3 / 9 | Flier |
| Lord | Temple | The Root Saint | V creaking | 1.8 | 3 / 8 | Caster |
| Lord | Drowned | The Drowned Archivist | V drowned | 1.9 | 3 / 8 | Caster |
| Lord | Clockwork | The Gearwright Colossus | V iron golem | 1.8 | 3 / 8 | Brute |
| Lord | Glass | The Glass Serpent | C Serpent | 2.2 | 3 / 8 | Serpent |
| Lord | Lantern | The Lantern Broker | C Lantern | 1 | 3 / 7 | Construct |
| Lord | Frost | The Abbot of Frost | V evoker | 1.7 | 3 / 8 | Caster |
| Lord | Mirror | The Mirror Knight | P (your face) | 1.4 | 3 / 9 | Duelist |
| Lord | Precursor | Unit Zero | P (own skin) | 1 | 3 / 8 | Duelist |
| Lord | Nightmare | The Sleeper | V zombie | 2.2 | 3 / 8 | Brute |
| Lord | Sky | The Sky Leviathan | C Serpent | 3.2 | 3 / 8 | Serpent |
| Lord | Abyss | The Hollow Herald | C Shade | 1.4 | 3 / 8 | Caster |
| Warden | Bell | The Sexton | V warden | 1 | 1 / 3 | Brute |
| Warden | Fungal | The Rot | V ravager | 1 | 1 / 3 | Beast |
| Warden | Ember | The Overseer | V wither skeleton | 1.35 | 1 / 3 | Brute |
| Warden | Drowned | The Curator | V elder guardian | 0.8 | 1 / 3 | Flier |
| Warden | Clockwork | The Regulator | V iron golem | 1 | 1 / 3 | Brute |
| Warden | Glass | The Mirage | V illusioner | 1 | 1 / 3 | Duelist |
| Warden | Hive | The Swarm Mind | V phantom | 2.2 | 1 / 3 | Flier |
| Warden | Temple | The Overgrowth | V iron golem | 1 | 1 / 3 | Brute |
| Warden | Lantern | The Lamplighter | V enderman | 1 | 1 / 3 | Duelist |
| Warden | Frost | The Yeti | V polar bear | 1.4 | 1 / 3 | Brute |
| Warden | Mirror | The Unblinking | V enderman | 1.2 | 1 / 3 | Duelist |
| Warden | Precursor | The Sweeper | V ravager | 1 | 1 / 3 | Beast |
| Warden | Nightmare | The Waking | V warden | 1 | 1 / 3 | Brute |
| Warden | Sky | The Gale | V breeze | 1.8 | 1 / 3 | Flier |
| Warden | Abyss | The Nothing | V wither | 0.6 | 1 / 3 | Construct |
| Beast | Greenwild | The Briar Sow | H | 1.15 | 3 / 8 | Beast (keep body) |
| Beast | Sunscorch | The Dune Wyrm | H | 1.4 | 3 / 8 | Serpent (keep body) |
| Beast | Frostwild | The Old Antler | H | 1.2 | 3 / 8 | Beast (keep body) |
| Beast | Mire | The Mire Mother | H | 1.2 | 3 / 8 | Beast (keep body) |
| Beast | Canopy | The Plumed Coil | H | 1.5 | 3 / 8 | Serpent (keep body) |
| Beast | Duskwood | The Weaver Queen | H | 1.3 | 3 / 8 | Crawler (keep body) |
| Beast | Blossom | The Empress Moth | H | 1.8 | 3 / 8 | Flier (keep body) |
| Beast | Crag | The Stonehorn | H | 1.6 | 3 / 8 | Beast (keep body) |
| Beast | Spore | The Mycelord | H | 1.5 | 3 / 8 | Brute (keep body) |
| Beast | Tidewater | The Lantern Maw | H | 1.3 | 3 / 8 | Serpent (keep body) |
| Beast | Hollows | The Thousandfoot | H | 1.3 | 3 / 8 | Crawler (keep body) |
| Beast | Hollows | The Seep Heart | H | 1 | 3 / 14 | Construct (keep body) |
| Guardian | Rim | The Rootmother | V spider | 2.4 | 2 / 5 | Crawler |
| Guardian | Gardens | The Thornback | V ravager | 1 | 2 / 5 | Beast |
| Guardian | Choir | The Choirmaster | V witch | 2.0 | 2 / 5 | Caster |
| Guardian | Orchard | The Ossuary King | V wither skeleton | 2.0 | 2 / 5 | Brute |
| Guardian | Silent | The Great Listener | C Listener | 1.8 | 2 / 5 | Brute |
| Guardian | Foundry | The Furnace Engine | V iron golem | 1.8 | 2 / 5 | Brute |
| Guardian | Inverted | The Drowned Moon | V elder guardian | 1.6 | 2 / 5 | Flier |
| Guardian | Dreaming | The Architect of Rooms | V evoker | 1.8 | 2 / 5 | Caster |
| Guardian | Court | The Shadow Regent | P | 1.6 | 2 / 5 | Duelist |
| Guardian | Seed | The Seed Warden | C Shade | 1.6 | 2 / 4 | Caster |
| Sovereign | Court | Sovereign of Hunger | V ravager | 1.8 | 3 / 8 | Beast |
| Sovereign | Court | Sovereign of Silence | C Listener | 1.9 | 3 / 7 | Brute |
| Sovereign | Court | Sovereign of Sleep | V phantom | 3.2 | 3 / 8 | Flier |
| Sovereign | Court | Sovereign of Machines | V iron golem | 1.9 | 3 / 8 | Brute |
| Sovereign | Court | Sovereign of Mirrors | P | 1.8 | 3 / 8 | Duelist |
| Sovereign | Court | Sovereign of Storms | V breeze | 2.4 | 3 / 8 | Flier |
| Sovereign | Court | Sovereign of Roots | V creaking | 1.9 | 3 / 8 | Caster |
| Sovereign | Court | Sovereign of Ash | V blaze | 2.4 | 3 / 8 | Flier |
| Final | Seed | The Signal's Shadow | C Shade | 2.0 | 3 / 10 | Duelist |
| Nightmare | Fear | The Long Drop | V phantom | 2.4 | 2 / 5 | Flier |
| Nightmare | Fear | The Pyre | V blaze | 2.0 | 2 / 4 | Flier |
| Nightmare | Fear | The Undertow | V drowned | 2.0 | 2 / 4 | Caster |
| Nightmare | Fear | The Hollow Crowd | V zombie | 1.7 | 2 / 4 | Brute (a crowd) |
| Nightmare | Fear | The Thousand Legs | V spider | 2.6 | 2 / 4 | Crawler |
| Nightmare | Fear | The Fuse | V creeper | 2.0 | 2 / 4 | Construct |
| Nightmare | Fear | The Faceless | P | 1 | 2 / 5 | Duelist |

(Abilities counts include the same ability repeated across phases.) Not in the bestiary but related: the Living Wilds'
three legendary beasts (Old Greymane, Hush, the Glass Wyrm) have no bodies yet (§21.11). They'd be natural first uses of
the kit after phase 2. The opening's Ritual statues (`RitualData`) are a cinematic and stay as they are.

## Appendix B: the colossus probe

`docs/bosses/probe/colossus_quads.py` (disposable, changes nothing in the game): a rough voxel colossus of his
reference's proportions (torso 16×18×10 blocks, arms and legs 6-7 blocks thick, about 60 blocks tall), with stepped
plates on 18-25% of its surface and spines. Output:

```
torso      x1: voxels  1670  visible faces  1220  merged quads   657
pelvis     x1: voxels   331  visible faces   376  merged quads   153
head       x1: voxels   245  visible faces   310  merged quads   164
upper_arm  x2: voxels   347  visible faces   474  merged quads   265
forearm    x2: voxels   372  visible faces   476  merged quads   241
hand       x2: voxels   130  visible faces   208  merged quads   127
finger     x8: voxels    16  visible faces    40  merged quads     6
thigh      x2: voxels   387  visible faces   464  merged quads   281
shin       x2: voxels   427  visible faces   506  merged quads   314
foot       x2: voxels   117  visible faces   194  merged quads    81
TOTAL visible block faces 6870, greedy-merged quads 3640; vertices 14560
```

It counts geometry only. It doesn't measure frame time, which C0 does on the Mac.
