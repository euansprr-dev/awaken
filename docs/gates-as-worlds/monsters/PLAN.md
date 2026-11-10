# Monsters of the gates (plan, 10 Nov 2026)

Plan only. Nothing in the mod has changed: no bestiary line, no texture, no Java. Written by Euan's planning process
(`.claude/PLANNING-PROCESS.md`) together with his excellence process turned from screens to creatures (audit, a design
thesis, directions, real pictures of the proposal, then stop). The approval page is `page.html` here, published at
https://claude.ai/artifact/5AbsTYzFh8aNbt71DW4rDZ (private until shared); the roster of all 79 is `ROSTER.md`; the
prototype bodies are `proto/proto.py` (run it to redraw `img/`).

What is observed, what is measured from git, and what is only proposed are kept apart and labelled.

## 1. What he asked for, and what a good answer looks like

**His words (10 Nov).** "All the mobs are not custom... very normal, like, vanilla mobs inside of the gates... Is it
best to create a ton of more custom mobs for this, so we keep the dungeon mobs separate from the overworld mobs that
we've already built? ... really high quality mobs, so the experience inside of it is like you have a unique experience
fighting as well, not just fighting normal vanilla mobs."

**The job.** A gate is now a run you fight through leg by leg. He likes the dungeons. The things you fight in them
should be as much a part of that place as its walls are, and fighting each one should be its own small problem.

**The pain.** You walk into a hall under a bell the size of a house and the thing ringing a hand-bell at you is a
Minecraft zombie. Every fight is the same fight: it walks up, stops, swings.

**Translated into things we can check.**

| | Strong | Weak | Not acceptable |
|---|---|---|---|
| Look | From 20 blocks, in the gate's own light, you can name each monster by its outline, and none of them could be mistaken for a Minecraft mob | Better textures and a few extra boxes on the same vanilla bodies | Anything that floats, clips, splits at a joint, or reads "derpy" up close |
| Motion | Feet on the ground, weight over them, a walk that is that creature's own, a tell you read from the body | Our own body moving on a cycle it shares with ten others | Gliding, spinning on the spot, a head that snaps, the list in `docs/creatures/HANDOVER.md` |
| Fight | Each monster asks you to do one thing differently, and gives you an opening when you do it | New bodies that all walk up, stop and swing | What you see and what hits you disagree; a desync in multiplayer |
| Cost | A gate fight holds the SPEC's frame rate; a rank of themes lands in days, not weeks | | A frame-rate drop in a full pack fight; 79 bodies rushed in one go and none of them good |

**Hard constraints.** Gate monsters are their own species, never an overworld animal reused. One rig and animation
system, the one the mod has. Server decides, multiplayer-safe, SPEC header budgets (110 fps in a full gate fight on an
M4, 60 combat entities, 25 ms tick). No Mojang or Fresh Animations file ever enters the repo. Every new thing has a
name in plain speech. Nothing rolls out until one theme has passed his eye.

**Non-goals.** Overworld animals and the levelled vanilla forms (`docs/forms/PLAN.md`). The 15 Lords and the six
Provings, which already have bodies of their own (below). The Gate Worlds' thirteen signature creatures, queued under
the boss plan. The run itself, its dungeons, its pay.

**Unknowns I could not settle by reading.** Whether Euan wants gate monsters to stop being people (decision 3).
Whether the proof's five will pass on the first showing (history says plan for two rounds). What a full pack of new
bodies costs in frames on his Mac (measured only on the Mac, by the orchestrator's perf tour, at the proof).

## 2. Audit: what is really there

### 2.1 A correction to the brief

The brief said 95 of the 100 gate monsters wear a vanilla body and only five Lords have their own. Read from the
`Model` enum that is true, but the enum is only the fallback. `data/awaken/kit/index.json` lists 47 kit bodies: all
15 Lords and all six Provings are in it (the boss work of 28 to 30 Oct). So:

| Tier | Count | Body today |
|---|---|---|
| Lords | 15 | Their own kit bodies (unseen in game since the first three) |
| Provings | 6 | Their own kit bodies |
| **Normal** | **49** | **A vanilla body with a tint and a glow layer** |
| **Elite** | **15** | **A vanilla body** |
| **Warden** | **15** | **A vanilla body** |

**79 monsters, not 95, and they are exactly the ones you spend a run fighting.** They wear 39 different Minecraft
bodies; 33 of the 79 are people (zombie, skeleton, illager, enderman, piglin, witch).

### 2.2 The map

| Piece | What it does | Evidence | Verdict |
|---|---|---|---|
| `mobs/def/GateBestiary`, `Model` | A gate monster is a stat line plus a vanilla body's name | 79 lines name a `Model` with a vanilla texture | keep the stat lines and ids; the body changes |
| `AwMobRenderer` on vanilla layers | Draws Mojang's model with our skin and glow over it | `AwRenderers` lines 141 to 222 | becomes the fallback only |
| `MobGoals.ToldMelee` | Every one of the 79 fights the same way: stop, wind up 8 to 10 ticks, swing; a big body's third swing is heavy | SPEC §25 S0 | keep the rule, give each body its own strike |
| `Ability` + `Telegraph` | One or two abilities each, told by dust rings and lines while the body stands still | `Telegraph.java` | keep the numbers; the body tells it |
| Hero rigs (`fauna_rig.py` → `Fauna3Data` → `WildRenderer`) | Our own bodies: bones, packed textures, clips | Drip Lurker: 20 bones, 23 boxes, 14 clips, 220 lines of spec | **reuse: this carries a gate monster** |
| The cue (`FaunaMob.cue`) | The server tells the body which move is winding up and when it fires; the wind-up clip is stretched to the ability's real time | `HeroRenderer` lines 33 to 181 | reuse; it sits on `FaunaMob`, gate monsters are `AwakenMob`: lift it one class up |
| The life layer (`mobs/client/life`) | Breath, blinks, springs, the look, stagger, fall | SPEC §23 | reuse as it is |
| The motion layer (`Legs`, `LegRig`, `motion.json`) | Feet planted by IK, strides from ground speed, turns that step, flight that banks; every gait we would need already exists (ripple, alternate, bound, hop, arms that plant, a legless base, a flier) | SPEC §23.1; test `creature_motion_feet` | reuse as it is |
| The boss kit (`KitRenderer`, `FightRunner`, move tables) | Phrases, hit volumes from bones, minds; built for one boss in an arena; no planted feet | SPEC §35 | leave to Lords; too heavy for a pack of eight, and 44 of its 47 bodies are still unseen |
| `RealmPacks`, `MobHooks.spawnNamed` | A pack names its monsters by id | SPEC §50.9 | unchanged: same ids, new bodies arrive everywhere at once |
| Cave hunters in gates | Drip Lurker, Hushling, Thousandfoot already fight in gates on their own bodies | SPEC §50.9 "Creatures with bodies of their own" | the proof that the path works for a hostile |

### 2.3 Why they read as vanilla (seen, not guessed)

Pictures: `img/today_nave.jpg`, `today_warden.jpg`, `today_brood.jpg` (real-game CI shots of the run, 10 Nov). A close
tour, `tools/autopilot/monsters-today.txt` (each Bell Crypt monster beside the mob whose body it wears, then rows from 8
and 20 blocks), was sent to GitHub on 10 Nov and was still waiting for a runner when this was written (run
42812918797); it adds close-ups, and nothing below depends on it.

1. **The outline is Mojang's.** A tint and a glow change the surface. At fighting distance you read shape first, and
   the shape is a zombie's. `img/silhouettes.jpg`: four of the Bell Crypt's five are an upright rectangle on two legs.
2. **The motion is Mojang's**, or Fresh Animations' if the pack is on: either way it is the walk you have watched for
   years.
3. **The fight is one fight.** `ToldMelee` is shared by all 79. What differs is a dust ring.
4. **They are small and dark in rooms built to be huge and dark.** In the nave the Toll Ghoul is about sixty pixels
   of blue-grey on blue-grey stone. The Sexton, the crypt's Warden, is a near-black shape on a dark floor.
5. **The voice is borrowed**: `Model.Voice` gives each one a vanilla mob's sounds.

Not a cause: the dungeons (he likes them), the stat lines, the pack system.

### 2.4 What "the Drip Lurker standard" is

He said it "looks incredible". It is **23 boxes**. So the bar is not detail. Reading its spec and SPEC §23.1, it is
four things: one idea you can say in a line (a stalactite that walks), an outline nothing else has, feet that plant
and a body that rides them, and moves the body acts out (it rears its front pair before it lunges; it dies curling up
one leg at a time). Every monster in this plan is held to those four.

### 2.5 What one creature really cost (git, not guesswork)

| Work | When | Measured |
|---|---|---|
| Fauna P3: 13 bodies (the Lurker among them) with clips, paint, voices, spawning, tests | 24 Oct, 00:32 to 01:48 | about 6 minutes a body, first pass |
| The motion layer (planted feet, IK, gaits, turns, flight), about thirty creatures put through it, two of Euan's reviews and their fixes | 29 Oct, 10:35 to 17:58 | seven and a half hours, once; it is built |
| Amber Reach's three (Pollen Grazer, Wax-folk, Amberfly) "on the Fresh Animations bar", with voices, loot, profiles and a tour | 9 Nov, 18:55 to 19:42 | 16 minutes each |
| 40 kit bosses, 70 to 160 boxes and 10 to 14 moves each | 28 Oct 20:15 to 30 Oct 02:21 | 45 minutes each; all but the first three never seen in game |
| A close look tour on GitHub | each | 10 to 13 minutes, plus the review |

**So one gate monster at the Lurker's standard is about an hour and a half**: 20 to 40 minutes to design, model,
paint and act, then two close tours with an honest frame-by-frame review between them. The cheap part is making it.
The dear part, and the part that was skipped whenever something of ours was later called derpy, is looking at it
properly before he does.

## 3. The thesis

**A gate monster is a thing that belongs to that place and nowhere else, that you know by its outline, and that makes
you do one thing differently.**

What follows from it:
- **Things, not people in costume.** 33 of the 79 are people, and a person-shape in Minecraft is a zombie
  whatever it wears. The Bell Crypt's ringer becomes a creature whose head is its bell. This is the Drip Lurker's
  lesson, and it avoids the hardest thing to animate well, a human.
- **Shape, then value, then one light.** Each theme's monsters are painted against their room (warm bronze and bone in
  the blue crypt) and carry one light, in one place, which is where you look and often where you hit.
- **The body is the tell.** What a dust ring says today, the outline says: the Toller's bell swings up, the Mourner's
  ribs open like wings.
- **One job each.** In a pack, one slows, one shoots, one dives, one holds the door.

## 4. Three ways to do it

| | A. A few families, many paints | B. A body for each (recommended, staged) | C. Elites and Wardens only |
|---|---|---|---|
| What is built | Six skeletons animated once; 78 paint jobs and part swaps on them | 78 bodies, each its own shape, stance, walk numbers and moves, on the one engine | 30 bodies; the 49 normal monsters stay as they are |
| Builder time, at this project's real pace | about 30 hours | 90 to 120 hours, in stages | 35 to 45 hours |
| What you would see | Six monsters in fifteen costumes | A different company in every theme | The pack you fight for most of a run is still zombies and skeletons |
| Main risk | His standing rule: "every creature should move like itself, not on one shared cycle". Today's 79 already use 39 vanilla bodies and read samey; six would read more so | Volume. Things built fast and unseen get rejected (the 144 finds; the bosses still unseen in game) | It does not answer what he said |
| What limits the risk | | A proof first; then a rank at a time, each with a stop | |

**Recommended: B, with the good half of A.** The engine, the checks, the tour scripts and a clip helper for each kind
of skeleton are shared, which is where the saving really is: the motion layer already walks anything with legs, and a
new walker is a profile in `motion.json`, not new code. But each monster gets its own boxes, its own stance, its own
stride and its own moves. C is the fallback if the proof fails twice.

**What would change my mind.** If the proof's five take much longer than a day and a half, or do not pass after two
rounds, B at 78 is too much, and I would recommend C on the wild path for elites and Wardens and a much smaller set of
normal monsters shared between neighbouring themes.

**The orchestrator's recommendation, tested.** "Its own bodies, separate species, same rig and system": holds, and the
audit shows the system needs almost nothing new. "Avoid building 75 one by one by animating a few families once": half
right. The families save the engine work, which is already done. They do not save the part that makes a creature its
own, and using them to save that part is option A.

## 5. How it works

### 5.1 The path a monster takes

```
today      GateBestiary line ── Model.ZOMBIE ── AwMobRenderer (Mojang's model, our tint) ── ToldMelee + dust ring

proposed   GateBestiary line ── body "toller" ─┬─ rig + clips (tools/art spec → generated data) ── WildRenderer
           (same id, same stats)               ├─ motion.json profile ── feet, stride, turns
                                               ├─ creatures.json temperament ── breath, springs, look
                                               ├─ cue (server) ── wind-up, strike, opening, acted by the body
                                               └─ fight verbs (data on the def, server) ── what you do differently
           no rig, or it failed to load ───────── today's body, logged once
```

- **Ids never change.** `awaken:toll_ghoul` stays `awaken:toll_ghoul`: packs, loot tables, the Journal, kill counts and
  saves all keep working. Only the name shown and the body change. A world saved mid-gate loads with the new bodies.
- **Hitboxes** come from the body's true size (`.size()` on the def, both sides), as the kit bosses' do.
- **The cue** moves from `FaunaMob` to `AwakenMob` with a fourth stage, RECOVER, so the body can show its opening.
  It is synced entity data: a player who arrives late sees the right pose.
- **Fallback, three levels.** A missing or broken rig draws today's body and logs once. A theme is converted by its
  bestiary lines, so one theme can be turned back alone. A whole stage is a merge that can be reverted.

### 5.2 Fights: six small rules, written as data

None of these is per-monster code. Each is a field on the def, one rule on the server, one test. All are shown by the
body, a sound and the ground mark together, never by colour or motion alone.

| Rule | What it means | First user |
|---|---|---|
| **Opening** | After a committed move it cannot turn or strike for a set time, and blows then count double toward breaking its stance | every monster with a big move |
| **Stopped by a blow** | A hit on it while it winds up cancels the move and staggers it | the Toller: strike the raised bell |
| **Front that holds** | Blows from the front do nothing; you must get round it or bait the move that turns it over | the Pallbearer's lid |
| **Planted to shoot** | It cannot turn while it draws, and what it shoots is part of its body, so you can see how much it has left | the Mourner's ribs |
| **A miss costs it** | A dive or charge that misses leaves it stuck where it lands | the Chime rings on the floor |
| **Looks like the room** | It waits as scenery and only its one light gives it away | already in the run (lurkers); many more |

The ordinary swing stays SPEC §25's rule (told, 8 to 10 ticks, classes LIGHT, HEAVY, PERILOUS), but the reach and the
clip are the body's own. The patent rule holds: nothing done to one monster changes another's numbers.

### 5.3 The states every monster must have

| State | What shows | If it is missing |
|---|---|---|
| Waiting in a den | An idle that is its own, and a way of standing guard | test fails |
| Noticing you | A turn of the part that carries its light | |
| Walking, running, turning | Planted feet, or its own way of going if it has none | `creature_motion_feet` fails |
| Each move: tell, strike, opening | Three poses you can tell apart at 20 blocks | `rig.check()` fails the build |
| Struck, staggered | A jerk over planted feet; a longer reel when its stance breaks | |
| Dying | A death that is a small performance, under a second and a half | |
| Far away (past 32 blocks) | Springs and fidgets off, the outline and the light still right | life layer does this already |
| Many at once | Nothing allocated per frame; at most 40 bones (60 for an elite or Warden) | perf gate |
| Reduced motion on | Fidgets off, springs halved; tells keep their pose, sound and ground mark | life layer does this already |
| Rig failed | Today's body | fallback test |

### 5.4 Look rules (the craft, concretely)

- **Whole pixels, boxes on the grid, stepped tapers.** No round shapes, no scaled pixels.
- **20 to 40 boxes** for a normal monster, up to 60 for an elite or Warden. The Lurker has 23.
- **No sculpted faces.** A void, a veil, a mask, a bell's mouth, with one or two points of light.
- **Value against the room.** Each theme lists its room's value and hue, and its monsters take the opposite.
- **One light a monster**, in the theme's colour, and it means something in the fight.
- **A voice of its own**, built from Minecraft's own recordings named in `sounds.json` (his rule of 9 Nov), with no
  high synthetic ding.
- **Names in plain speech**, and a line in the Journal that reads like someone telling you about it.

### 5.5 Where this meets the neighbours

- **Forms** (`docs/forms/PLAN.md`): overworld creatures that level up. Different creatures, same cue fields. No overlap.
- **Bosses** (`docs/bosses/`): Lords, Provings and the Gate Worlds' thirteen stay with the kit. Wardens here are big
  monsters on the wild path with the six rules. If he later wants a Warden to fight in phrases like a Lord, that is
  the boss plan's R3, and its body can be read by the kit as it is (same rig format).
- **Creatures hand-over**: this plan uses its workflow and its review list unchanged.
- **The run**: packs, herds and pay are untouched.
- **The Journey** ("beasts, not people"): this goes the same way.

## 6. The proposal, in pictures

The roster for all fifteen themes is `ROSTER.md`. The proof theme is drawn here as real rigs on the mod's own toolkit,
by its own offline renderer: `img/lineup.jpg`, `img/silhouettes.jpg`, and for each monster `img/turn_*.jpg` (three
sides and crypt light) and `img/fight_*.jpg` (its fight, pose by pose).

**These are an afternoon's first pass** to show direction, outline and the fight. They have no clips, no feet planted
by the game, and they have not been seen in the game's own light. What they prove: five bodies that share nothing
with a Minecraft mob can be said in 9 to 27 boxes each. What they do not prove: that they move well.

| Today | Proposed | In a line | What you do differently |
|---|---|---|---|
| Toll Ghoul, a zombie holding a bell | **The Toller** | A hunched ringer whose head is its bell, a cold flame where the clapper hangs | Its bell swings up before it tolls. Strike it then and the toll is lost |
| Crypt Archer, a skeleton with a bow | **The Mourner** | A tall veiled column with no arms; its ribs lie shut across its front | It leans back and its ribs open like wings before three of them fly. Open, it cannot turn, and you can count what it has left |
| Bell Wisp, a vex | **The Chime** | A hand-bell that flies, the wisp burning in its mouth | It tips its mouth at you, then dives. Step aside and it lies ringing on the floor |
| Crypt Keeper, a wither skeleton | **The Pallbearer** | A stone coffin that walks on two long arms | Blows on its lid do nothing. It heaves itself up and drops on you, then lies with its open back to the ceiling |
| The Sexton, Minecraft's Warden | **The Sexton** | A gravedigger bent double under the headstones it carries, eyeless, a lantern on a pole in front of it | It hunts by sound. When it snuffs the lantern the room goes dark, and its spade comes down where it last heard you |

## 7. Proving it, then rolling it out

### Stage 0: the Bell Crypt (the proof)

Why this theme: it has the two most recognisable Minecraft mobs there are, so the change is plainest; the Hour Crypt
is one of his two bar gates and four lands use the theme; and its five cover the hardest cases (an upright thing, a
shooter, a flier, a heavy with a front that holds, a giant). The Hive would be safer, because crawlers are what the
engine already does best, and a proof should test the risky part.

Built: the cue on `AwakenMob` with its fourth stage; the six rules; the five bodies with clips, profiles, voices,
names and Journal lines; tests; the tours.

**What he is shown, before anything else is built:**
1. For each of the five, a clip filmed close, at leg height, beside the mob it replaces under Fresh Animations: walk,
   run, stop, a turn on the spot, ten seconds at rest.
2. For each, its fight against a standing target: the tell, the strike, the opening, then a blow and its death.
3. The pack in the Hour Crypt's nave, in the nave's own light, from 8 blocks and from 20.
4. The same five as black outlines beside today's.
Side-by-side mp4s at game speed, with what changed and what is still weak said plainly. He is not asked to play a
gate; the bodies are in his play copy after a merge if he wants to.

**Pass or stop.** He says the five pass, or says what is wrong and there is one more round. Two failed rounds and the
plan falls back to C.

### After the proof

By rank, the order players meet them, with a stop for his look after each rank:

| Stage | Themes | Bodies | Builder time |
|---|---|---|---|
| 0 | Bell Crypt + the shared parts | 5 | a day and a half |
| 1 | The rest of E: Hive, Fungal Hollow, Ember Mines, Overgrown Temple | 22 | four days |
| 2 | D: Drowned Archive, Clockwork Foundry, Glass Desert, Lantern Night Market | 21 | four days |
| 3 | C: Frost Monastery, Mirror Palace, Precursor Lab, Nightmare | 20 | four days |
| 4 | B and A: Sky Archipelago, Abyssal Shrine | 10 | two days |

A theme is one batch, by one builder, without sub-agents: bodies, close tours, review, mp4s, SPEC bullets, hand-back.
The figures assume an hour and a half a body and a second paint pass on one in three, and they are the least certain
numbers on this page.

### Tests (one per mechanism)

`gate_monsters_own_bodies` (no monster of a converted theme names a vanilla body; every one has a rig, a profile, a
temperament and a voice), `creature_motion_feet` extended to the new rigs, `monster_cue_stages` (wind-up, strike,
opening reach a late joiner), one test for each of the six rules, `monster_fallback` (a broken rig draws the old body),
`monster_outlines` (offline: every pair of outlines in a theme differs by a set share of its area).

### Gates before each merge

Green GameTests on the exact commit. Every clip reviewed against the hand-over's list. The outline sheet. And, on the
Mac only, one full pack plus a pressure wave in the Hour Crypt's nave holding the SPEC's frame rate against the same
scene before the change.

### Risks left open

- The prototypes may look better as stills than they move. The proof exists to find out.
- 78 is a lot. The stops are real: any rank can be the last.
- Sounds chosen on paper have been wrong before; each voice is auditioned in context.
- Fresh Animations users will see vanilla mobs elsewhere move by FA and ours by our layer. That is true today of the
  cave hunters and has not read as a clash.

## 8. The plan, argued against

A structured read from four sides. It is a review, not a playtest.

- **Someone in their first gate.** Will they know what to do? Each tell is a change of outline plus a sound plus the
  ground mark the game already draws, and the first monster met (the Toller) teaches the commonest rule: hit it while
  it winds up. Weak spot: "blows on the lid do nothing" must be shown, not learned by dying. The Pallbearer's lid gets
  a clear ringing deflect, as a parry has.
- **Someone on their fiftieth.** Does it get old? The rules combine in a pack, and rolled crawls change the room. Weak
  spot: one theme has five monsters and you will have learned them in an hour. That is true of any hand-made set, and
  the roster gives each theme its own five rather than re-dressing these.
- **Someone who can't rely on colour, on motion, or on sound.** No tell uses colour alone; reduced motion keeps every
  tell's pose; every tell has a sound and a ground mark, so any one of the three can be missing. Not checked: nothing
  here has been tested by a person who needs it.
- **Whoever keeps the server honest.** Six rules, each data, each with one test; the cue is synced entity data that a
  late joiner reads; nothing a client says is believed. Weak spot: 78 rigs are 78 generated files and a larger jar.
  Measured: the 15 rigs of the Lurker's kind are 228 KB of generated data together, about 15 KB each, so 78 more is
  near 1.2 MB (a boss's kit rig can be 1 MB on its own). The build should report the total so it is never a surprise.

**Is this the real frustration, or a nearby easier one?** He said two things: they look vanilla, and fighting them is
not its own experience. A re-skin answers neither and bodies alone answer only the first, which is why the six rules
are in the same stage as the bodies. **Could something simpler do most of it?** Putting more Lurkers and Hushlings in
packs is free and already started (SPEC §50.9), and it is why the Hive plays better than the crypt today. It does
not scale: three species cannot be every theme's company. **What would show the diagnosis wrong?** If he looks at the
proof and still says "vanilla", then shape was not the problem, and the next thing to look at is how they move and
sound, before any more are built.

## 9. Decisions for Euan

Each has my pick. The page lays them out.

1. **How much.** A body for each monster, proven on one theme and rolled out by rank *(pick)*; or a few families with
   paints; or elites and Wardens only.
2. **Which theme first.** The Bell Crypt *(pick)*; or the Hive.
3. **Things, not people.** Gate monsters stop being people in costume *(pick)*; or keep people-shapes with better bodies.
4. **Names.** Rename where the creature's idea changed *(pick)*; or keep every old name.
5. **Fights.** The six rules come with the bodies *(pick)*; or bodies first, fights later.
6. **Wardens.** On the same path as the rest, bigger *(pick)*; or hold them for the boss kit.
7. **Stops.** One after the proof and one after each rank *(pick)*; or after every theme; or none after the proof.
8. **The old bodies.** Kept only as a silent fallback *(pick)*; or a player setting to bring them back.
9. **Voices.** Each monster gets its own with its body *(pick)*; or borrowed voices stay for now.
10. **The Mirror Shade** keeps wearing your face, as the one monster that should *(pick)*.
