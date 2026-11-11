# The Leaper's Gauntlet, remade, and the Unlock title (plan record, 12 Nov 2026)

Status: **PROPOSED. Nothing is built.** It follows `.claude/PLANNING-PROCESS.md` and `.claude/UI-EXCELLENCE.md` and ends
at an approval page, https://claude.ai/artifact/1jMUNxA9HGq32BxcCJP8B1 (`docs/leapers-gauntlet/page.html`, its
pictures cut by `cut.py` into `shots/`; his answers save in its `answers` collection, `d1` to `d12`). SPEC §71 is the
short form. Planner: the movement-unlocks session.
The course itself is the gates session's to build; the unlock, the stands' scene and the lessons are this session's; the
leap's own rules (`AscentMoves`, `AscentRules`, `AscentSwing`, `AirRules`) are rope-hold-swing's and are not edited here.

## Euan's words

In the gates session, 11 Nov 2026, after playing the Gauntlet:

- "why was there one single... parkour block? It's like it took like maybe 30 seconds to finish the whole gate, and like
  I don't even know if I got XP from it. Like, what was the point of that gate?"
- "when it comes to that double jump place. I had no idea that's where I got the double jump from. That was not clear
  whatsoever. So we need to massively improve that. And actually use the double jump more. I think I had to use it one
  single time in the whole process. And it wasn't even clear. It's like I didn't even know I got it there. I thought I
  already had it, and it was just telling me to use it as an extra thing here. So we need to actually think about how to
  make that AAA as well."

In this session, 12 Nov 2026, on seeing today's title:

- "No, I don't think it should say you can jump in the air again. That's so boring. Like, AAA level would be something
  like an actual similar to when you discover something like a discoverer, at least like the big title, and it says you
  just unlocked double jump. Press space in the air again to use it, or something like that. So I think that would be
  so much better. And maybe every time you unlock a major thing in the game, it actually shows you that as well."

## 1. The brief

- **The job.** At D-rank, with the rope, the player runs one gate and comes out able to double jump, dash in the air
  and slam. It is the second rung of the ladder (SPEC §17, §63).
- **The pain.** He did not know the gate gave him the move, used it once, could not tell what he was paid, and the whole
  gate was over in about a minute. "What was the point of that gate?"
- **The desired experience.** The moment you get a move is named in the game's biggest type, at a place you chose to
  stand, with nothing else happening. Everything after it needs that move, several times. The pay is counted out in
  front of you. The gate is worth the walk.
- **His proposed solutions** (to honour, not only to investigate): a big title like a discovery's that says you just
  unlocked Double Jump and how to use it; the same title for every major unlock in the game; the double jump used much
  more.
- **Hard constraints.** Plain speech on every card. Every feature its own look. No pre-announced bosses. Never stuck.
  Sounds start from Minecraft's own recordings, no synthetic dings. Layouts hold at 427 by 240 (his 4K at GUI scale 9).
  The rope-hold-swing session owns the leap's rules; the gates session owns the course.
- **Unknowns checked here:** what he saw second by second (his log and a first-person tour); what the kit can build
  (asked of the gates session; its answer is folded in when it comes, and until then the course is specified by what
  each hall must make the player do, not by pieces).

Strong, weak and unacceptable, in what can be observed:

| | Strong | Weak | Unacceptable |
|---|---|---|---|
| Knowing | Asked afterwards, he says where each move came from | He knows he has it, not where from | "I thought I already had it" (today) |
| Using | Each move needed 3 or more times before the test, and the test needs all of them | A move needed once | A move named on a card and never needed (dash and slam today) |
| Pay | He can say what he was paid | A number flickers somewhere | "I don't even know if I got XP" (today) |
| Worth | 5 to 8 minutes the first time, a course worth running again | 2 minutes | Par 8.1 seconds (today) |

## 2. Boundary

A plan and mock-ups only. Read: the code on `build/awaken-v1` at edc314667, his play log (read-only), tours on GitHub.
One tour script was added on this branch (`tools/autopilot/gauntlet-now.txt`); no game code was changed.

## 3. Audit (verified in code at edc314667 unless marked)

| Stage | What it does today | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| The gate opens | By the first crown reached by rope (`WayUp.crownReached`) or Tether mastery 4 (`AscentTrials.leaperDue`); a panel: "A gate has opened for you. It stands in front of you. It is a course, not a fight." | `AscentTrials.openLeaper` | Says what it is not. Never says why to go in | Keep the triggers, change the words |
| The Tasks row | "Run the gate that opened where you climbed", even when it opened in front of you by mastery | `WayUp.steps` | Wrong for half the ways in | Fix |
| Arrival | The generic gate HUD: "D-rank · Clockwork Foundry · Layout Labyrinth · dead ends, loop… · Find the Rest Shrine." The gate's own notice, "New gate · Clockwork Foundry", is ambient and was filed unseen in his play | tour shot `now_04`; his debug log 17:26:18 | Nothing says this gate is the Gauntlet, or what it holds | Replace |
| The gate | One D-rank Foundry floor: antechamber, a hall of 1 by 3 slots, a calm room with the exit | `Gates.leaperTrialPlan`, `FloorBuilder` | A corridor | Replace |
| The course | Flat, four wide, 31 rows: a gap, two ledges, a ring, two presses, a wall-run, a ring, a run-up, one chasm of 5 rows. Its own check sets **par at 8.1 seconds** | `RigGauntlet.trial`; tour shots (`par 8.1`) | "One single parkour block" | Replace |
| The clock | Starts at the first step, on the first run, with a medal against par | `RigGauntletLive.start` | A lesson under a stopwatch | No clock until the test |
| The pit | Hurts, throws you back to the last ring, and toasts "The pit throws you back." as a critical line, so it shows over anything | `RigGauntletLive.back` | It was drawn over the gift three times | Not in the halls that teach; never over an unlock |
| The gift | At the last row before the chasm, mid-sprint: `TrialHooks.atEdge` gives the leap, a 3.2 s title "You can jump again in the air now.", the prompt "[Space] Press again in the air". Nothing stops you | `AscentTrials.edge`, `AscentScene` (`EDGE_LENGTH` 64) | See the trace below | Replace |
| The far landing | "That was the leap. It's yours." for 6.5 s, a card naming three moves, 10 Leaper mastery, a fifth of a level's XP, a medal | `AscentTrials.leaperDone` | The dash and the slam are named and never used | Extend |
| The pay | "+90 XP" in small type low on the left; no word of mastery | tour shot `now_13c` | "I don't even know if I got XP" | Show it |
| After | The exit opens in an empty room; the gate comes down | `RigGauntletLive.trialRun` | Nothing to come back to | Decision 9 |
| The leap's moves | Double jump (+0.52 up, once an airtime), air dash (0.95 a tick along your look, once an airtime), slam (from 4 or more up, 1.7 a tick down, throws what stands within 3.5) | `AscentRules` | All three given at once | Unlock one at a time (needs rope-hold-swing) |
| The discovery title | A spaced label, a line of light from the centre, the name at 1.8 size, one or two lines, its own swell, about five seconds, nothing else shown meanwhile | `signal/client/Moment` | The strongest "you found something" in the game, and unlocks do not use it | Reuse for unlocks |
| The Lord's payout | XP lands in front of you: one number climbing, a bell a level | `gates/run/LordPay`, `client/LordLevels` (SPEC §50.4) | The strongest "you were paid" in the game | Reuse for the Gauntlet's end |
| The Stopped Works | A rolled gate kind: a foundry stopped mid-stroke, with its own depth, air and look | `docs/gates-as-worlds/the-run/PLAY.md` | The Gauntlet's hall predates it | Build the Gauntlet in its look (gates session to confirm) |

## 4. The disappointment, reproduced

**His own run** (11 Nov, `run-pack/logs/debug.log`, read-only):

| Time | What the log shows |
|---|---|
| 17:07:17 | The rope given. 17:07:39: the leap's row wakes (a spire 370 blocks south) |
| 17:25:54 | Gate 7 ready |
| 17:26:18 | "New gate · Clockwork Foundry" offered as ambient and filed. He is inside |
| 17:26:31 to 17:26:42 | A press throws him back twice, the pit three times |
| 17:27:23 | A press again. 17:27:27: the wall-run |
| **17:27:28.6** | **The leap is given** (at the edge row, sprinting off a wall-run) |
| **17:27:29.6** | **"The pit throws you back."** Then again at 17:27:31.4 and 17:27:34.4 |
| about 17:27:37 | Across. Done |

One second passed between the gift (17:27:28.559) and the pit's throw (17:27:29.562). The gift's title was on screen
for 3.2 s, all of it spent falling, being thrown back and reading a toast about the pit. He then needed the move once.

**The same on today's build, pictured** (CI tour `gauntlet-now`, run 43008682852, first person, HUD on): the title
"You can jump again in the air now." with the clock, "par 8.1" and a "New power" line under it while the view is inside
the pit (`now_11d` to `now_11g`); the pit's toast beside the title (`now_11j` to `now_11l`); two pit toasts stacked over
the prompt (`now_12`); "+90 XP" in small type (`now_13c`); the far landing's sentence and card in an empty room
(`now_14a` to `now_14e`).

**A strong case to learn from, in the same game:** the vault's rope. You stand between two pylons (a place), the rope
pulls you up (the gift does something to you), and you cannot leave the mast without throwing it yourself. He had no
complaint about where the rope came from. And his own note on wings, 11 Nov: put the wing model in a glass case at the
core "to make you want it".

**Where the intended behaviour first disappears:** at the choice of where to give it (a row you sprint over) and of what
may be on screen with it (everything). The words came third: "You can jump again in the air now" reads as a tip.

## 5. Success contract

- **Knowing.** An unlock can only begin while the player stands still on a stand and holds Use for a second. From that
  tick until the title has gone, nothing else is drawn (every tier held, the gate's own HUD hidden) and nothing can move
  or hurt them.
- **Using.** Before the timed run: Double Jump needed 4 times, Air Dash 4, Slam 3. The timed run needs at least 6, 5 and
  3. The course's own check must fail if a hall can be crossed without its move.
- **Pay.** XP is counted up in the middle of the screen with a bell a step, as after a Lord; the mastery bar is shown
  filling; the medal and time are struck onto a plate. The player's own counts are shown ("Double jumps: 14").
- **Worth.** First run 5 to 8 minutes. The timed run's par about 85 seconds.
- **Never stuck.** No hall that teaches can hurt or throw back. After three misses at one spot the figure shows it again;
  after six the spot eases (a wider landing, a slower press) without removing the need for the move. Leaving and coming
  back starts at the last stand reached.
- **Reliability.** If the unlock title cannot show (HUD hidden), the move is still given and the K row and Tasks say so.
- **Non-goals.** No change to how the leap's moves feel. No new fight. No change to the other gates' Gauntlet rooms.
- **Must not regress:** `wayUp` off restores every old rule; a leap handed down by a sky core still works at once.
- **Baselines:** par 8.1 s; one use; gift to pit 1.0 s; pay as "+90 XP" in small type.

## 6. Causes

| Symptom | Cause | Evidence | Fix |
|---|---|---|---|
| "I had no idea that's where I got it" | The gift is a row crossed at a sprint, with the pit a second on | His log; `now_11` shots | A stand you stop at and hold Use on |
| "I thought I already had it" | The words are a tip's ("You can … now"); nothing says unlocked | `en_us.json`; `now_11g` | The Unlock title: label, the move's name, how |
| The title was not read | A critical toast, the clock, par and another notice share the screen | `now_11g`, `now_11k` | Everything held during an unlock |
| "I had to use it one single time" | The course predates the leap being its subject: 26 of its 31 rows are Runner moves | `RigGauntlet.trial` | Halls that need each move, then a test |
| Dash and slam never met | Named on the end card only | `now_14e` | A stand and a hall each |
| "30 seconds", "what was the point" | Par 8.1 s; the door's words name no reason | `par 8.1`; `GateCard` | A longer place with its point said at the door |
| "I don't even know if I got XP" | Small "+90 XP"; mastery silent | `now_13c` | A counted payout |

Not yet verified: how gate 7 opened for him (no crown line in the log, so most likely Tether mastery 4).

## 7. Research

| Claim | Source | Applies | Limits | Implication |
|---|---|---|---|---|
| Nintendo's 3D Mario levels teach one idea in four beats: introduce it safely, develop it, twist it, conclude, in about five minutes | Engadget, "This is why 'Mario' levels are brilliant" (17 Mar 2015), on director Koichi Hayashida; summarised on his Wikipedia page as kishōtenketsu | A hall a move | Secondary sources; 3D platformer, third person | Each hall: safe first use, a harder use, a twist with an older move, then the timed run as the conclusion |
| In the Zelda games a dungeon's item recontextualises the rest of the dungeon, and where a dungeon can be finished without its item the item feels optional | Mark Brown, Boss Keys (Link's Awakening, Twilight Princess, Zelda 1 episodes; transcripts on amara.org) | The whole gate | Fan analysis, not Nintendo documents | Everything after a stand needs that stand's move; show the lock (a gulf) before the key |

Links are on the page. No further reading was needed: the remaining questions are about our own kit.

## 8. Approaches

| | A. Repair the moment | B. Take it, learn it, prove it (recommended) | C. The spire is the course |
|---|---|---|---|
| What | Today's hall. A stop before the chasm, the Unlock title, the pit's toast held, two more chasms, the payout | Three stands, a hall each, one timed run, a counted payout, in the Stopped Works' look | No gate: the leap is taken on a Tether Spire's crown and the course is the spire and its yard, in the open world |
| Knowing | Fixed | Fixed, three times over | Fixed |
| Using | 3 uses, dash and slam still unmet | 11 uses before the test, 14 in it | As B |
| Worth | About 90 s | 5 to 8 min, and a course to return to | The best fit to "the way out of the place is the move" |
| Cost | About half a day | Mine 10 to 14 h; gates 12 to 20 h; rope-hold-swing 1 to 2 h | Worldgen for every spire; shared space in co-op; the gates' pit, rings and medals lost |
| Risk | Low | The course is new geometry and needs a new check | High |

**B**, with A's title and stand landing first (they do not depend on the new course). C loses today; it would be the
natural next step if the Gauntlet ever leaves its gate. What would change this: the gates session finding the halls
cannot be laid by hand in the Stopped Works' kit, in which case B is built in the old Foundry hall's blocks.

## 9. Behaviour

### 9.1 The Unlock title (a shared kit; decisions 1 and 2)

`core.api.Unlocks.show(player, id)`; the client draws it in the System's layer beside the discovery title.

- **Looks like** the discovery title, one size up, in the unlock's own colour (movement: the Lesson's gold):
  the spaced label `U N L O C K E D`; a line of light opening from the centre, twice a discovery's width; the name at
  three times the size of normal text ("Double Jump"; a discovery's is 1.8); under it the how-line with the player's
  own key as a cap ("Press [Space] again in the air"). Six seconds. On a 427 by 240 screen a name up to 22 letters fits
  at that size; a longer name drops to twice, and is never cut off.
- **Sounds like:** the System's moment swell under Minecraft's own `ui.toast.challenge_complete` (the sound the game
  already uses for its biggest advancement), at 0.9 pitch. Reduced motion: no rise, no light sweep; the words only.
- **While it shows:** every tier of message is held, tips are held, a tip already up fades, the gate's HUD is hidden, and
  the Lesson waits. It never shows in a fight; there it waits for the fight to end (as a gift's scene does today).
- **Then** the how-line stays as the Lesson's pinned prompt where the move is first used, so the eye follows it from the
  title to the place.
- **Never lost:** an unlock is not paced and never filed. If two arrive together they play one after the other.
- **Which things are major** (decision 2): a new way to move, a new power, a new rank, a new layer of the world. On his
  D-rank day the log shows "A new stratum opened below", "Bell #40" and "The Hearth burns brighter" filed unseen after
  45 s; the first of those is an unlock by this rule. Everything else stays as it is.

Unlock titles for the ladder (mine, replacing today's sentences): Rope · "Hold [key] to swing. Let go to fly." Double
Jump · "Press [Space] again in the air." Air Dash · "Press [Ctrl] in the air." Slam · "Press [Shift] in the air, from
high up." Dive · "Press [attack] in the air to dive where you look." (when rope-hold-swing has built it). Wings · "In a
fall, press [Space] twice." Flight · "On open wings, press [Space] again." The Sovereign's Form · "Press [key] when the
gold line is full."

### 9.2 The stand (decisions 3 and 4)

A Foundry press on a round dais (block `awaken:leap_stand`, the Ascent's own, placed by the course at a mark).

Trigger → response → state → recovery:

1. Within 5 blocks: the stand's own light; a cap pinned on it, "Hold [Use]". No word of what it gives.
2. Use held 20 ticks, standing on the dais, on the ground: the press comes down (12 ticks), light climbs the legs, the
   body is held still for 30 ticks. Let go early and nothing happens.
3. At the stroke the server unlocks that stand's move (saved at once) and the Unlock title plays.
4. The stand goes dark for that player. The hall's first door opens on the move being made.
5. A crash or a quit after the stroke: the move is kept; the title plays again on return if it never finished.

The leap is unlocked a move at a time inside the Gauntlet (a mask of leap moves a player may make: double jump, dash,
slam, dive). Given any other way (a sky core, the Sky Warden's roof, an old save), all are open at once, as today. A
player who arrives already holding them finds the stands dark and the halls open: the lessons still play.

### 9.3 The Gauntlet (the gates session builds; decisions 5 to 10)

| | Hall | What it makes you do | Leap moves needed |
|---|---|---|---|
| 0 | **The way in.** No clock | Runner moves you have. It ends at a gulf 6 wide with the far side 2 higher. A gold figure crosses it with a second jump. You cannot. A net of chains lifts you back | 0 |
| 1 | **Stand: Double Jump.** Hall "Twice" | The gulf you could not cross. A step 3 up. A gap onto a high ledge. A second jump off a wall-run | 4 |
| 2 | **Stand: Air Dash.** Hall "Through" | A gap of 9 (jump, then dash). A line of presses dashed between beats. A corner (the dash turns you). A wide gulf: jump, jump, dash | 4 dashes, 2 double jumps |
| 3 | **Stand: Slam.** Hall "Down" | A shaft 8 deep with a cracked deck that only a slam breaks. Three constructs on a plate, thrown by a slam. A stuck hammer freed by a slam on its plate | 3 |
| 3b | **Stand: Dive** (only once rope-hold-swing has built it) | From a gantry, three small lit plates far below, each landed on by an aimed dive | 3 |
| 4 | **The Long Run.** The clock starts at the first step | One course on three levels: 6 double jumps, 5 dashes, 3 slams (2 dives), with wall-runs and one rope swing. Rings checkpoint it. The pit throws back and does not hurt. Par about 85 s. Medals | 14 |
| 5 | **The tally floor** | XP counted up with bells. The mastery bar filling. Time and medal struck on a plate. Your counts. The way out | |

No hall before the Long Run has a clock, a pit or anything that hurts. Halls 1 to 3 follow the four beats of §7: a safe
first use, a harder one, a twist with a move you had, then the next stand.

The door: the crossing names it "The Leaper's Gauntlet" with one line, "A works that stopped mid-stroke. What it made is
still on its stands." The HUD's line follows the hall you are in ("Reach the first stand", "Cross the hall", "Run the
Long Run"). The gate's panel and Tasks row say its point: "It holds a new way to move. No fighting."

### 9.4 The pay (decision 8)

At the tally floor, reusing the Lord's payout: one number in the middle of the view climbing to the XP paid, a bell a
step (vanilla `block.bell.use`); then the mastery bar from where it was to where it is; then the plate: time, medal, and
"Double jumps 14 · Dashes 9 · Slams 4". Proposed: half a level's XP the first time (a fifth today), plus the medal's
share as now. A re-run pays the medal's share only.

### 9.5 Coming back (decision 9)

Proposed: the gate stays standing where it opened, as a course. A re-run starts at the Long Run's door with the clock.
Its card shows your best time and medal. (Today it comes down once everyone has left.)

### 9.6 Invariants

- A move is never unlocked while the player is moving, in the air, in a fight, or with a screen open.
- A move unlocked is saved before its title starts. A title never grants anything.
- Nothing is drawn over an Unlock title, and an Unlock title is drawn over nothing that matters (never in a fight).
- No hall can be crossed without its move, and every hall can be crossed with only the moves unlocked so far.
- `wayUp` off: the old course's rule (the leap at the edge) if the old course is kept as a fallback, else the stands
  with all three moves at the first stand.

## 10. Data and state

- `WayMemory` schema 2: `leap` (bits: double, dash, slam, dive) and `stand` (the last stand reached in an unfinished
  Gauntlet). Optional fields; a schema 1 record reads as "all moves open if the leap is held".
- `AscentMemory` unchanged. The Tasks row stays until `LEAPER_TRIAL_DONE`.
- Two players in the gate: each takes each move at the stand for themselves; a hall's door opens for everyone once any
  one of them opens it; the pay and the plate are each player's own.

## 11. AI

Not applicable.

## 12. In the working moment

The page's storyboard shows the first stand frame by frame. The player's part at each: walk in, try the gulf, fail
softly, see the figure do it, walk to the lit stand, hold Use, read one title, cross the gulf.

## 13. Failures

| Case | Behaviour |
|---|---|
| HUD hidden (F1) | The move is given; the title is skipped; the K row and Tasks carry it |
| Quit during the stroke | Saved at the stroke; on return the title replays once |
| Dies in the Long Run | The pit never hurts; a death from anything else returns you to the last ring |
| Leaves mid-gate | The gate stays; return starts at the last stand reached |
| The gate is gone | Opened again at its place (today's rule, `WayUp.gate`) |
| Arrives holding the leap | Stands dark, halls open, lessons play, the Long Run and the pay as for anyone |
| Unbound key | The how-line's cap reads "Not set"; the K menu's Controls page is named |
| Reduced motion | No figure, no press animation, no light sweep; words and caps only |
| The aimed dive is not built yet | Hall 3b is left out; the Long Run asks for no dives |
| The per-move mask is not built yet | All three moves open at the first stand; the second and third stands still play their titles as "now use it" |
| Six misses at one spot | The spot eases; the log says so |

## 14. Privacy and boundaries

Nothing leaves the game. His log was read on this Mac only.

## 15. Performance and observability

- The Unlock title is one GUI layer; the stand ticks only for players within 8 blocks.
- The log gains lines a play can be read from: "[ascent] the way up: DOUBLE JUMP unlocked at a stand", and at the
  tally "[gauntlet] run: 14 double jumps, 9 dashes, 4 slams, 2:31, bronze, eased 1".

## 16. Evaluation

- **Tests (mine):** each stand unlocks exactly its move, once, only while standing and holding; the mask refuses the
  others; a leap handed down opens all; a relog keeps them; the Tasks row leaves at the end.
- **The course's check (gates):** its runner must fail each hall without that hall's move and pass with it; it must
  use at least the counts in §9.3.
- **Tours on GitHub:** `gauntlet-now` (today, kept) beside a new `gauntlet-new` from the same camera: the stand, the
  stroke, the title, each hall's first use, the Long Run's start, the tally.
- **The real test:** his play. After it, can he say where each move came from and what he was paid. The log's lines
  give the counts and times.

## 17. Demonstrations

On the page: the trace above; today pictured; the Unlock title mocked over the real game for five unlocks; a
frame-by-frame storyboard of the first stand; the Gauntlet's map; the tally. The mock-ups are drawn in HTML over real
screenshots: they show layout and words, not the finished look.

## 18. Order of work

1. **The Unlock title and the ladder's titles** (mine; no dependency): playable on all five rungs.
2. **The stand, the mask's memory, the lessons for dash and slam, words, Tasks and K rows** (mine); the mask honoured
   in the leap's rules (rope-hold-swing).
3. **The course** (gates): halls 0 to 3, the Long Run, the tally floor, the crossing and HUD lines, the check.
4. **The gold figure's own drawing** (mine): the way in depends on it.
5. Hall 3b when the aimed dive exists.

The old course stays in the code until the new one's check passes. Effort: mine 10 to 14 hours; gates 12 to 20;
rope-hold-swing 1 to 2 for the mask. What could move it: the kit's limits; the figure's renderer.

## 19. Decisions

On the page, numbered as there.

## Who builds what

| Piece | Who |
|---|---|
| `core.api.Unlocks`, the Unlock title, the ladder's titles | movement-unlocks |
| `leap_stand`, its scene, the leap mask's memory and sync, `TrialHooks` marks, lessons, words, Tasks, K rows, the gold figure | movement-unlocks |
| The mask honoured in `AscentMoves` and `AscentServer` events; the aimed dive; the double-jump mantle | rope-hold-swing |
| The Gauntlet's place, halls, pieces, check, clock and pit rules, crossing, HUD lines, tally floor, re-run | gates |
| A new power, a new rank, a new stratum calling `Unlocks.show` | their modules, one line each, if decision 2 is yes |
