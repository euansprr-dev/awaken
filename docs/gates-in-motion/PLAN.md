# Gates in Motion: plan

Status: plan only, waiting for Euan's approval on the page https://claude.ai/artifact/EMPut1PgjJCgoXnDcSsK1v (his answers save there). Nothing here is built.
Draft SPEC §72 at the end of SPEC.md; page copy docs/gates-in-motion/gates-in-motion.html.

Euan, 12 Nov 2026: "right now the parkour is very boring. Like there's very not much wall running. The jumps are
extremely simple to do. No mantling or anything. It doesn't really feel dynamic. No sliding either. I think sliding would
be cool. Like actually thought out and a ton of different ones, so that the procedurally generated things like always make
it feel like a unique gate. And then also it's like during the spiral staircases, I think there could be a bit of fighting
there, like some simpler stuff just to keep it more dynamic. And then just extrapolate this to every possible level to see
how we can make all gates much more dynamic and interesting."

## 1. What he needs, and how we'll know

**The job.** Run a gate and feel like a free-runner in a place that is new every time. The climbing, crossing and going down
are part of the fun, and fights happen during them, not only in rooms.

**The pain.** Movement in gates asks for almost nothing:
- there's very little wall running;
- the jumps are easy;
- there's no mantling or sliding;
- the spiral stairs are empty.

Once you've seen the crossings, a rolled gate reads like the last one.

**What he wants instead.** Every stretch between fights asks for real moves: slide, mantle, wall run, wall jump, hang,
shimmy and grip. They come in combinations that change with the seed, the kind of gate and the rank. Small fights happen
on stairs and ledges. And this applies to every kind of gate, not just the rolled ones.

**His ideas, treated as leads:**
- more wall running;
- harder jumps;
- mantling;
- many different slides;
- fights on the spiral stairs;
- the same treatment for every level.

All of them are kept.

**What must hold:**
- No two gates alike: vary shape and parkour, not palettes.
- Nothing floats: half a block counts.
- Never stuck: every gate can be finished.
- Plain words, and teach once then go quiet.
- Safe for multiplayer: a co-op partner can play on an easier setting in the same gate.
- Gates already opened keep their layout.
- Performance stays inside the budgets.
- Code the whole plan, then test once at the end. CI does the checking, and the manager runs the full suite at merge.
- Open lands stay off until Euan has played Saltcomb.

**Not part of this plan:**
- No new moves: the kit is already full.
- No change to how any move feels: the body's rules stay as they are.
- No score or timer on screen.
- The rope, leaps and wings are never required.
- Boss rooms stay as they are.
- Bringing the open lands back is not part of this.
- It is not the look pass (BAR.md). Still, every new piece is built in each kind's own stone, never in vanilla blocks.

**A strong result.** A C-rank run of any kind asks you, inside ten minutes, to slide, mantle, wall run, wall jump and hang
at least once each, in an order you haven't seen before. On the way down a spiral you fight two or three small things on
the landings. The next run of the same kind plays differently.

**A weak result.** New pieces exist, but every run uses the same two. Or the moves are optional, so you just jump.

**Unacceptable:**
- a gate you can't finish;
- a piece that needs pixel-perfect input;
- an E-rank fall that kills;
- anything floating;
- a monster knocking you into a pit without warning.

## 2. What the code does today (verified)

| Part | What it does | Evidence | So | Keep / extend / replace |
|---|---|---|---|---|
| The moves | Everyone has these from the first second: slide, mantle, wall run, wall jump, hang, shimmy, ledge jump, vault, grip and roll | `Body.unlocked` returns true when `BodyConfig.runnersKit` is on, and it's on by default (`Body.java:41`, `BodyConfig.java:21`). The rope, leaps and wings are earned (The Way Up, §63.2) | The moves are there. Gates never ask for them | Keep |
| Crossings | 8 kinds, and every one is a sprint jump over 1-3 cells, level or down | `CrawlLand.java:723`: "nothing is ever higher than what you jump from" | The jumps are simple by design | Extend. Today's crossings stay as the E pieces and the fallback |
| Crossings per run | A standard run and up has one chasm leg (plus a rare branch) | `GeneratedCrawl.java:649, 752` | One movement set piece per run. Everything else is walking | Replace with pieces on every movement leg |
| Shafts | One spiral: 1.5 turns, 24 down, three breaks and two crumble arcs. Going up has no breaks | `GeneratedCrawl.java:665-666`: the same fixed sizes at every rank | The spiral stairs are the same everywhere, and empty | Extend: six forms, with fights on the landings |
| Rank | Rank only changes how many waves a stand has | `GeneratedCrawl.java:365`. Crossings and shafts take no rank | An S gate's jumps are an E gate's jumps | Extend: a rank table |
| Kinds | 17 skins. Their crossings come from just two lists, one for dug places and one for built places | `GeneratedCrawl.style()` | Kinds differ in stone more than in movement | Extend: each kind gets its own movement accents |
| Ways | Tunnels 17-24 long, walked | `GeneratedCrawl.generate` | Long flat walks between rooms | Extend: pieces in the ways |
| The checker | Knows steps, drops up to 3, swimming, rungs and the 1-3 cell sprint jump | `WalkCheck.java:100, 164` | **The generator only builds what it can prove, and it can only prove walking and jumping** | Extend: the checker learns the moves |
| Required-move declarations | `Land.Leap(move, box)` marks a crossing that needs a move, and `needless` proves the move is required | `Land.java:144` (the Gauntlet's double jump, dash and slam) | We already declare required moves | Generalise to every move |
| Catching falls | A Course box catches a fall and puts you back on the last ledge, costing 3 + 8% of max health unless the course is free | `Land.java:60`, `RealmRun` | A never-stuck tool already exists | Reuse on every piece |
| Move probes | `RunnerRules` probes are pure functions over a `BlockGetter` | `body/RunnerRules.java` | They can run on a painted land in a check | Reuse as the proof |
| Real-rule tests | A mock player runs the real rules | `body_slide_gap_and_wall`, `body_mantle_ledges`, `body_stair_slide`, `body_land_slide`, `body_vault_rails`, `body_air_climb` | The pattern for testing each piece | Reuse |
| Teaching | `LessonFigure`, a client-only gold figure that shows a move once. The Way Up Lesson (§63.3) | `body/ascent/LessonFigure.java` | We can teach once, then go quiet | Reuse |
| Flow | `AscentFlow`, the client's Flow meter and chains | `body/ascent/client/AscentFlow.java` | A quiet reward for a clean line | Reuse lightly |
| Plan versions | `RealmPlan.CURRENT_VERSION` keeps old gates' layouts | `RealmPlan` | Old gates are safe | Reuse (bump it) |
| Fights | Dens (F and A legs), stands (wave rooms), Wardens and the Lord. Movement legs have no pack | `RealmLive.java:183-200` | Fights and movement never meet | Extend |

**Gate families.** This is what "every possible level" means:
- **Rolled crawls** (`GeneratedCrawl`, 17 kinds). These are the most-played gates, and the first target:
  - Frozen Cells, Rime Hollows, Stopped Works, Cinder Workings;
  - Green Dark, Tallow Runs, Unlit Stair, Silvered Halls;
  - Lower Combs, Hollow Hills, Sporewells, Root Cellars;
  - Sleepless Ward, Sunken Stacks, Sealed Wing, Bone Roads, Glass Warrens.
- **Designed crawls** (`CrawlLand`): the Broodwarren and the Leaper's Gauntlet.
- **Designed realms:**
  - `KitLand`: Amber Reach, Hour Crypt, Kingsroot Keep, Saltcomb, Vine Seat;
  - `SheetLand`: Quiet Barrow, Wax Galleries.
- **Rolled lands** (`GeneratedLand`): the Ember, Frost, Fungal, Glass and Temple countries. These are open lands and all off
  today.
- **Older Gate World floors** (`gates/world`). Stage 0 checks whether any of these still roll.

**Why parkour is boring, most likely cause first:**

| Symptom | Cause | For | Against | Smallest test | If fixed |
|---|---|---|---|---|---|
| No slides, mantles or wall runs | The checker knows only walking and the sprint jump, so the generator only builds those | WalkCheck, CrawlLand:723 | None found: no crossing anywhere needs anything else | Stage 0 count of required moves across 816 runs | Highest: the root of it |
| Feels flat | One movement set piece per run. The rest is tunnels | generate | — | Count of movement legs per run | High |
| Easy at every rank | Crossings and shafts ignore rank | :365, :665 | — | Read the code (done) | High |
| Gates feel alike | Two crossing lists cover 17 kinds | style() | The skins differ a lot | Overlap of piece sequences between kinds and seeds | Medium-high |
| Not dynamic | Fights only happen in rooms | RealmLive | — | Count of fights on movement legs (0) | High |
| "No mantling" | Maybe the moves are never asked for or shown, so he hasn't met them | No tells in gates | He may already know them | Ask him (Decision 2) | Unknown |

## 3. What other games teach (design precedent, not proof)

| Claim | Source | Use here | Limits |
|---|---|---|---|
| Platform levels feel good when built from short "rhythm groups" of moves, each checked playable, then strung together | Smith, Whitehead, Mateas et al., *Launchpad: A Rhythm-Based Level Generator for 2-D Platformers*, IEEE TCIAIG 3(1), 2011 | Pieces are the right unit, and every piece and every line is checked | 2-D, academic. Our check is the seed sweep and Euan's play |
| Hand-made room templates with random chunks give variety and a guaranteed path | Derek Yu, *Spelunky* (Boss Fight Books, 2016), and Darius Kazemi's generator write-up (2013) | Hand-make the pieces and roll their sizes. Fully random geometry would look wrong | Feel comes from the hand-made part |
| Generate the order of challenges first, then fit them to space | Joris Dormans, *Adventures in level design*, PCG Workshop 2010 | The composer picks a leg's moves first, then fits them to the tunnel, chasm or shaft | Grammar size has to stay small to stay readable |
| Players read a line from signs in the world | DICE, *Mirror's Edge* (2008), Runner Vision | Every family gets a physical sign in the kind's own stone | Ours stays subtle: no red tint |

## 4. Three ways to do it

**A. Targeted repair.** Add four new crossings (a wall-run gulf, a slide gate, mantle steps, a hang lip) and put fights on
shaft landings. About a day.
- Loses: still one movement set piece per run, still the same every time, no change by rank, and no reach into the other
  levels.

**B. Pieces strung by seed (recommended).**
- Hand-make about 24 pieces of movement ("beats") in five families, each with sizes that roll.
- A composer strings them into every movement leg, by seed, kind and rank.
- The checker proves each line with the same move rules the player's body uses.
- Six forms of shaft, with fights on the landings.
- Fights in motion: holds, perches, a chase, and rooms that go up.
- It reuses the generator, Courses, RunnerRules and plan versions.
- About 3-5 days for one builder.

**C. Fully generated parkour.** Shape the geometry from a simulated runner. The most variety on paper, but hard to make
look built (the Gates bar), hard to keep fair, and slow. A simulated ghost runner stays a later experiment.

**Why B.** "Actually thought out and a ton of different ones" means the pieces are made by hand. "Always feel like a unique
gate" means the order and the sizes change with the seed and the kind.

**What would change this.** If Stage 0 shows pieces can't fit in the tunnels without breaking the look, they go into
chasms, shafts and rooms only. That is still B.

## 5. How it behaves

### 5.1 A beat
`Beat` is a record:
- id and family;
- the moves it asks for;
- sizes, rolled within a range set by the rank;
- an entry and an exit (position, facing, height change);
- a box;
- catch ledges;
- dressing roles, so each kind's skin dresses it;
- an optional fight slot;
- an optional co-op cycle.

It sits inside a Course, so a fall comes back to the beat's start.

### 5.2 The kit (21 beats on the way, 3 side routes)

**Low (slide)**
1. **Slide gate**: under a fallen beam or a half-shut door, one block high, then straight on.
2. **Slide jump**: slide off an edge into the jump, which carries further than a sprint jump.
3. **Chute**: a long stair or ramp you slide down (the stair slide, up to 6 s).
4. **Landing slide**: drop 3-5 and land into a slide under a low roof.
5. **Closing teeth** (C and up): slide under a press, a bulkhead or a portcullis that opens and shuts on a cycle.
6. **Rail run**: rails, coffins or benches to vault, one after another.

**Wall**
7. **Wall-run gulf**: a gap you cross along a wall, 5 long at E and up to 9 at A and S. The move reaches about 11.
8. **Chimney**: two walls 2-3 apart, wall-jump up 4-8.
9. **Switchback**: run the left wall, jump, run the right.
10. **Run to hang**: the run ends at a lip you catch.
11. **Corner run**: round an inside corner on the wall.

**Ledge**
12. **Mantle steps**: rises of 1.1-1.6, one after another.
13. **Hang and shimmy**: a lip along a drop, 4-10 to shimmy.
14. **Drop to hang**: step off an edge and catch a lip below.
15. **Jump to hang**: jump a gap to a lip 1.2-2.4 above your feet. This is the only way up by a jump.
16. **Ledge to ledge**: hang, then jump up or sideways to the next lip.
17. **Broken wall**: climb a fallen wall by hangs and mantles.

**Grip**
18. **Pipe or chain line**: hang from a pipe or a chain over a gap and move along it.
19. **Chain fall**: go down hanging chains, one to the next.

**Flow and fall**
20. **Crumble run**: the floor falls in behind you, so keep moving.
21. **Drop and roll**: a 4-6 drop onto ground, with a roll.

**Side routes (never required)**
22. **Stride wall**: a Wall Stride climb to a find.
23. **Swing out**: a rope anchor for a shortcut, for those who have the rope.
24. **Leap gap**: a Gauntlet move (double jump, dash or slam) to a hidden find.

### 5.3 Six shafts
1. **Spiral with landings**: three landings, with fights (holds) on one to three of them.
2. **Broken spiral**: cross the breaks by a jump, by a wall run along the shaft wall, or by dropping to a hang.
3. **Chimney**: wall-jump up, or go down by chains.
4. **Chain fall**: chains down the middle, with landings between.
5. **Ledges down**: hang and drop, ledge to ledge round the wall.
6. **Square well**: wall-run each side, turn at the corners, and go down by the ramp.

Shafts that go up get the climbing forms (chimney, ledges up, broken wall). Shafts that go down get the falls.

### 5.4 The composer
For every movement leg (ways, chasms, shafts, and the run between dens) it takes:
- the leg's length, height change and room shape;
- the rank;
- the kind's accents.

From those it picks a line of beats. Its rules:
- never the same beat twice in a row;
- every leg asks for at least one move that isn't walking or the sprint jump, and two families at C and up;
- across a run, no family is more than 40% of the beats;
- each kind leans toward 2-3 families, and its signature beat appears at most once a run.

| Rank | Beats a leg | How they join | Fights in motion | A caught fall costs (Decision 4) |
|---|---|---|---|---|
| E | 2-3 | one move each, generous sizes | one hold per shaft, 2 small monsters | nothing |
| D | 3-4 | pairs (slide then mantle, run then hang) | holds of 2-3 | nothing |
| C | 4-5 | chains of 3, with timed beats (teeth, crumble) | perches across one gulf | 3 + 8% (today's cost) |
| B | 4-6 | chains of 4, chimneys, switchbacks | a chase once a run | today's |
| A | 5-7 | chains of 5 or more, long wall runs (8-9) | fights on landings partway along a line | today's |
| S | 6-8 | long lines across whole rooms, everything mixed | a hold on every landing | today's |

Every size stays inside the proof's margin (section 5.5).

**Each kind's accents** (weighted families and one signature beat; built in the kind's own stone):

| Kind | Leans to | Signature |
|---|---|---|
| Frozen Cells | slides on ice, cell bars to vault | the iced gallery, a long slide under the bars |
| Rime Hollows | hang and shimmy on icicle lips, ice chutes | the ice chute down |
| Stopped Works | the pipe run overhead, presses, grating | a slide under a press that starts again |
| Cinder Workings | crumble runs over the glow, mine rails, drop and roll | the burning floor run |
| Green Dark | vines and roots to grip, mossy mantles | the vine curtain climb |
| Tallow Runs | low wax tunnels, wax chutes | the long wax chute |
| Unlit Stair | spiral stairs with holds, niches to wall-jump | the long spiral fight down |
| Silvered Halls | long wall runs, balconies to hang, rails | the mirror gallery wall run |
| Lower Combs | low burrows, comb walls | the brood chase |
| Hollow Hills | low ways, kerb mantles, timber frames to hang | the fallen shaft climb |
| Sporewells | fungus shelves to mantle, wells to chimney | the well chimney |
| Root Cellars | roots to grip and hang, half-built walls to run | the root bridge shimmy |
| Sleepless Ward | corridor wall runs, beds to vault, half-fallen doors to slide | the corridor where the floor stops |
| Sunken Stacks | shelf bays to mantle and run, fallen shelves | the run along the drowned stacks |
| Sealed Wing | panel walls to run, bulkheads that close | the closing bulkheads |
| Bone Roads | niches to mantle, low vaults, bell ropes | the bell-rope descent |
| Glass Warrens | glass sheets to run, glass floors that shatter, sand chutes | the shattering floor |

### 5.5 The checker learns the moves
- `Land.Move(move, box, from, to)` generalises `Land.Leap`.
- `MoveProof` walks the painted land with WalkCheck's edges plus move edges. Each move edge is tested by the same
  `RunnerRules` probes the body uses: slide clearance, mantle lip height, the wall and its length for a wall run, and the
  lip for a hang.
- A beat passes only if its line exists with every move at no more than 80% of its reach. For example, a wall run proves
  over at most 9 when the move reaches about 11.
- `needless` shows the move is really needed: take the move away and the line must fail. So a beat truly asks for the move.
- Real-rule template GameTests run a mock player through every beat at its smallest and largest size, the way
  `body_mantle_ledges` does today.
- **When a line fails:** reroll the beat (up to 6 times), then swap it for a simpler beat, then use today's crossing. Each
  fallback is logged as `beats.fallback` and counted by the seed sweep.

### 5.6 Fights in motion
- **Holds.** A landing at least 5×5 with 2-4 light monsters from the kind's own roster, scaled for the party as in §70.
  - They wake when you are 12 away along the line.
  - They are tied to their landing and don't follow you down.
  - A hold never locks the way.
  - Its kills count toward the run's spoils like a den's, but are never required.
- **Perches** (C and up). One or two ranged monsters on ledges across a gulf shoot while you cross. A side line reaches them
  if you want them.
- **Chase** (B and up, once a run). A corridor where the brood or the swarm comes from behind. Its beats have no dead ends.
- **Rooms that go up.** Dens get mantle steps, ledges and a ring you can wall-run, so a den fight can climb.
- **Stands.** The wave rooms get pillars to mantle, a wall-run ring and a slide gap under a fallen beam.
- **Being hit on a wall** (Decision 6). The recommendation: a hit costs health but never throws you off a wall or a ledge.

### 5.7 Teaching (teach once, then go quiet)
- **Signs in the world.** Each family has a physical sign in the kind's own stone:
  - low: dust and a draught under a fallen beam;
  - wall: scuffs along the wall at running height;
  - ledge: a worn, pale lip;
  - grip: a pipe or chain rubbed bright.
- **The first time a family appears for a player,** the gold figure (`LessonFigure`) shows the move once and the Now line
  says it in plain words, for example "Slide under it: sprint, then crouch." After that there is nothing.
- **Memory.** GateMemory gets a schema bump to record which families each player has been shown.

### 5.8 A clean line
Crossing a whole line without stopping or falling gives a quiet sound and a little Flow, as `AscentFlow` already does. No
score and no timer.

### 5.9 Co-op
- Timed beats cycle, so a partner always gets another go.
- Holds scale with the party (§70).
- A caught fall costs a partner on an easier setting less (her own difficulty, §70).
- Both players get the same line.
- A hold wakes for whoever comes first and stays on its landing.
- The gold figure shows each player their own first time.

### 5.10 Data and saved state
- Beats come from the seed, rank, kind and plan version, so nothing new is saved per gate.
- `RealmPlan.CURRENT_VERSION` goes up by one, and gates already opened keep their layout.
- GateMemory schema +1, reading the old version: the families each player has been taught.
- A new config, `gateBeats`, on by default once approved. Off means today's crossings for new gates: the rollback.

### 5.11 Performance
- **Generation.** Stage 0 measures generation time per run. The budget is +15%, with no cost to a frame.
- **Monsters.** At most two holds are awake at once, four monsters each at most, plus two perches.
- **Proofs.** They run at generation and cost milliseconds per leg. They never run on a tick.

### 5.12 When things go wrong

| Case | What happens | Test |
|---|---|---|
| A beat can't fit in a leg | Reroll, then a simpler beat, then today's crossing (logged) | Seed sweep: fallbacks at most 3% of legs |
| Another part cuts into a beat | MoveProof on the painted land catches it, and the beat is rerolled | Overlap fixture |
| Runner's Kit is off on a server | Beats that need a runner move are never chosen, and rung or ladder versions take their place | Config-off sweep |
| A fall mid-beat | The Course catches it and puts you at the beat's start. The cost follows Decision 4 | Course covers every beat |
| A monster hits you mid-wall-run | No knock-off (Decision 6) | Mock hit test |
| A monster falls off its landing | It stays tied to the landing, goes if it leaves the shaft, and is never required | Hold leash test |
| A partner is stuck behind teeth | The teeth cycle | Cycle test |
| A lag spike mid-run | The body's rules are unchanged, and the Course catches the fall | — |
| A gate is mid-run during an update | Its plan version keeps its layout | Old-version load test |
| Something floats | FloatCheck runs on every beat's pieces: beams sit in walls and chains hang from the roof | Float sweep |
| An up shaft | The only up jump is jump to hang. Without the Runner's Kit, the chimney has rungs | Up-shaft sweep |

## 6. Before and after (illustrative; nothing built yet)

1. **Ordinary: the Hollow Hills, D rank, a standard run.**
   - Before: a 20-long tunnel; a chasm of pillars you cross in four 2-cell jumps; a 24-down spiral with no one on it; then
     the dens.
   - After:
     - In the tunnel, you slide under a fallen timber frame and mantle up a kerb.
     - The chasm becomes a wall run along the shored wall onto a lip. You shimmy, then mantle out.
     - The shaft is a broken spiral with two barrow things waiting on the middle landing.
   - What you do next: the same dens. The way between them is the change.
2. **His case: the Unlit Stair's spiral.**
   - Before: 1.5 turns, three breaks you jump, nothing to fight.
   - After:
     - Three landings, with holds on two of them: two small things at E, three or four at B.
     - A stair slide joins the landings.
     - At C and up, one break is crossed by a wall run along the niche wall.
3. **Hard: the Silvered Halls, S rank, in co-op.** Euan is on normal; his partner is on an easier setting.
   - Mirror-gallery wall runs of 8-9, then bulkheads that close every 4 s, then a chimney.
   - The bulkheads cycle, so she always gets another go.
   - A caught fall costs her less than it costs him.
   - The holds are scaled for two.
4. **A failure: a low-roofed rift in the Cinder Workings.**
   - The wall-run beat doesn't fit under the roof, so MoveProof fails it.
   - The composer tries a simpler beat (a crumble run). If that fails too, it falls back to today's pillars and logs it.
   - The run is still finishable, and the sweep counts the fallback.

## 7. How it will be judged
**Baseline (Stage 0).** A seed sweep of 17 kinds × 8 seeds × 6 ranks = 816 runs, counting per run:
- the required moves by family, using MoveProof on today's runs;
- the legs that ask for any move beyond walking;
- the fights on movement legs;
- the overlap between seeds.

Expected today: zero required slides, mantles, wall runs or hangs, and zero fights on movement legs.

**Gates before hand-off:**
- **Families.** Every standard run and up asks for at least 4 of the 5 families at C and up, and at least 3 at E and D.
- **Every leg.** Each movement leg asks for at least one move beyond walking and the sprint jump, proved needed.
- **Variety.** Two seeds of the same kind share at most 50% of their beat order. At the same seed, two kinds lean to
  different families.
- **Safety.** Fallbacks are at most 3% of legs. Nothing gets stuck (RouteCheck) and nothing floats (FloatCheck).
- **Real-rule tests.** Every beat passes its real-rule test at its smallest and largest size.
- **Speed.** Generation time is at most +15%.
- **Old gates.** Gates on the old plan version load unchanged.
- **Shots.** One CI tour per kind, showing two beats and a hold, judged against BAR.md.
- **The real gate.** Euan plays a seed list: one run per kind at C, plus a few at E and S. This is his verdict, not an
  agent's.

## 8. Stages (code the whole plan, then test once)
0. **Measure.** The baseline sweep, generation time, and whether the old Gate World floors still roll.
1. **The checker learns the moves.** `Land.Move`, `MoveProof`, `needless` for moves, and the template test harness.
2. **The kit.** The 24 beats with dressing roles for every skin. A bench hall (`/awaken gate beats`) lays every beat in a
   row for tours.
3. **The composer.** Six shafts, the rank table, kind accents, the plan version bump and the fallback.
4. **Fights in motion.** Holds, perches, the chase, rooms that go up, the stand kit, and the no-knock-off rule.
5. **Teaching, flow and co-op.** Signs, the gold figure once per family, GateMemory, Flow and cycles.
6. **Every level.**
   - The Broodwarren: hand-placed beat spots.
   - The designed realms' ways: beat spots placed by hand, with care at Amber Reach and the Hour Crypt, which are the bar.
   - The rolled and open lands' ways: outdoor beats (mantle rocks, slides under fallen trees, wall runs along cliff faces).
     Built, but off until the lands come back.
   - The Leaper's Gauntlet: left as it is, since it was remade on 11 Nov.
7. **Check once.** Compile, the named tests, the seed sweep, and CI tours of every kind and of the bench hall. Then a seed
   list for Euan. The manager runs the suite at merge.

**Effort.** About 3-5 days for one builder session at xhigh. The biggest unknowns:
- dressing 24 beats in 17 skins;
- how much care the designed realms need.

**Rollback.** `gateBeats` off gives new gates today's crossings, and the plan version keeps any gate already open.

## 9. Decisions for Euan (the page saves his answers)
1. **The approach.** B, pieces strung by seed (recommended), or A, four new crossings.
2. **Did you know you can already slide, mantle, wall run and hang?** Yes / some of them / no. This decides how much
   teaching goes in.
3. **How hard from E.** One move at a time at E, growing to long chains at S (recommended), or chains from E.
4. **What a caught fall costs.** Nothing at E and D, today's cost from C (recommended); today's cost everywhere; or
   nothing anywhere.
5. **How much fighting while moving.**
   - Recommended: holds on every shaft, perches from C, and a chase from B.
   - Light: shafts only.
   - Heavy: most movement legs.
6. **Hits on a wall.** They cost health but never knock you off (recommended), or they can knock you off at A and S with a
   clear sign first.
7. **Teaching.** Signs plus the gold figure once per family (recommended), or signs only.
8. **Order.** Rolled crawls first, then every other level in the same build (recommended), or rolled crawls only for now.
9. **Rope, leaps and wings in gates.** Optional side routes and shortcuts, never required (recommended), or required once
   you hold them.
10. **Gates already opened.** They keep their layout (recommended), or they are remade on your next visit.
