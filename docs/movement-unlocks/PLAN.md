# The Way Up: how each move is earned (plan record, 10 Nov 2026)

Status: **PROPOSED. Nothing is built and nothing is approved.** Branch `claude/movement-unlocks-b62e18`. The approval page is
https://claude.ai/artifact/JWLvKJnkCf5Ti5YcoH35VN (built by `docs/movement-unlocks/build_page.py` from `page.src.html` and
`shots.py`; his answers save in its `answers` collection, `d1` to `d12`). This file is the builder's
contract once Euan says yes. It follows `.claude/PLANNING-PROCESS.md` and holds `.claude/UI-EXCELLENCE.md` for the unlock
moments. Its sister is Rope and Wings (`docs/movement/PLAN.md`, SPEC §62, approved 10 Nov): that plan decides how the rope
and the wings feel and adds six moves; this one decides how every rung and move is earned and taught.

## Euan's words (10 Nov 2026)

"the progression system for all of the movement and how that gets unlocked. We need to make sure that it's actually like
natural to get to that point, and that it feels like easy to, well, not easy, like it should still be difficult to get to
like the final point, but like it should be kind of a natural progression, and you shouldn't have to like, basically it
shouldn't be impossible to unlock the future movement. Like maybe we make it unlock more with like maybe some of the rank
assessments. I think that would be pretty interesting, where it's like based on rank assessments, and then that unlocks
somewhere that you discovered that you can go and unlock that item, and then it actually shows you how to use it and stuff
with like kind of AAA tooltips, visuals, like similar to some others we have maybe. And yeah, just make sure that that whole
progression is actually clear inside of the K menu. Or that it's clear. Actually, it doesn't have to be clear in the K menu,
actually. It's more so it's like it has to be clear the moment that you unlock it, or it has to have a moment that's natural
progression where you unlock it."

## 1. What he is asking for

- **The job.** The ladder (Runner, Tether, Leaper, Wings, Skyborn, Sovereign) is the game's biggest reward. A player who
  just plays must arrive at every rung, know it the moment it happens, and be able to use it a minute later.
- **The pain.** He can't tell how the later rungs are reached, and suspects some can't be.
- **What he wants.** A road that follows the ranks. Winning a rank fight shows you a place; you go there; the place gives
  you the move and shows you how to use it. The last rung is hard to reach. No rung is out of reach.
- **His ideas, to test rather than assume:** rank assessments as the trigger; a place you discovered as where it is given;
  teaching with tooltips and visuals like the game's best screens.
- **Fixed:** the ladder's six rungs and their order; the Provings as built (SPEC §47); no boss or Proving named before it
  is met; one voice (the System); plain speech; the server decides; every step on the Tasks page through `core.api.Goals`.
- **Not known before this audit:** what earns each rung in the code today. Section 2.

Success, in things that can be seen:

| | Strong | Weak | Not acceptable |
|---|---|---|---|
| Reaching a rung | Playing the ranks alone gets you every rung. At any moment the Tasks page holds one row for the way up, with a place. | The rungs can be reached but you have to know how. | A rung that waits on a number the game never shows, or on luck. |
| The moment | You do the move within a minute of getting it, in a place built for it, and your own key is on screen at the thing to aim at. | A title and a line of text. | A gift that plays while you are busy with something else. |
| The end | The last rung takes the hardest fight in the game and is used after it. | It arrives when there is nothing left to do with it. | It can be missed. |

## 2. What is there (read in the code, measured, and seen)

Read: SPEC §2, §4.4, §17 (all), §27, §43, §47.4 to §47.10, §53, §57; `body/ascent` (7,381 lines), `hearth/trial`,
`core/api/Goals`, `tasks/NowMap`, `gates/GatesNow`, the trail and level-road data. Measured: `tools/sim/pacing_sim.py`
(hours to each rank), the structure sets' spacing (a small Monte Carlo of the nearest site). Seen: four CI tours of the
unlock moments as they are (`tools/autopilot/unlock-tether.txt`, `unlock-wings.txt`, `unlock-sky.txt`, `leaper-trial.txt`;
run ids and what each showed are in section 2.4).

### 2.1 The road today

Hours are the pacing sim's balanced player; a careful player takes about a third longer.

| Rung | What earns it today | Where | When | What tells you | Where you can get stuck |
|---|---|---|---|---|---|
| Runner | Nothing: yours from the first second | Anywhere | 0 h | A tip the first time each move fits | Nowhere |
| Tether (the rope) | Winning the D rank fight (the Ringer). Its chain is the rope (`AscentTrials`, `TrialHooks.RINGER`) | The Drowned Belfry, inside the fight | 3.4 h | A toast: "The Ringer's chain is lying at your feet. It's yours now." Then a text tip | Nowhere. But nothing makes you use it: you go home 12 s later |
| Leaper (double jump, dash, slam) | D-rank, the rope, and **Tether mastery 4: 400 points the game never shows** (2 a rope, 3 a jump off one: 200 ropes for hands that only hold the key). Then a gate opens 20 blocks ahead, once | The Leaper's Gauntlet, a gate | 8 to 12 h by SPEC's guess; no measure | One toast when the gate opens. The gate is on no list: scripted gates are left out of the Now line (`GatesNow`), and nothing is on Tasks | **Soft, for good:** never swinging enough, with nothing saying so (in real woods the rope doesn't fire at all, Rope and Wings finding 1). **Hard:** walk on from the toast and the gate is somewhere behind you, offered once |
| Wings (the glide) | C-rank, and reaching a sky platform's core by rope up its rings (`WingCoreBlockEntity`) | A sky platform | 10.2 h at the earliest; no measure after that | Nothing, unless you met a platform before C (then the Saved for later call-back). The Horizon line that teased platforms has been off by default since 27 Oct | **Soft, for good:** nobody says to look up. The nearest platform is a median 280 to 380 blocks away and at worst about 1,300 |
| Skyborn (flight) | Winning the A rank fight (the Sky Warden): given when the roof lifts at a third of its health | The cathedral, inside the fight | 35.8 h | A title and a line, in the middle of the boss's last third | Nowhere |
| Sovereign (the form) | Any ending of the story (`StoryHooks.endingSeen`) | Wherever you are, 5 s after | After 61.4 h plus the Seed | A title and a line | Nowhere, but it arrives after the last fight |

### 2.2 Findings

1. **Two rungs already come from rank fights** (the rope at D, flight at A; the Journey's decision of 29 Oct, built 2 Nov).
   They can't be missed. *Verified in code and tests.*
2. **The two rungs between them wait on things the game never shows.** The leap: a hidden count of 400. Wings: finding a
   platform nobody points at. Neither has a row on the Tasks page: the movement module registers no `Goals` step at all.
   *Verified.*
3. **The leap's gate is offered once, by one toast, and is tracked nowhere.** *Verified (`AscentTrials.openLeaper`,
   `GatesNow` line 54).*
4. **A higher rung hands you the lower ones in silence.** `Ascent.grant` gives every rung under the one granted. A sky core
   gives the leap without its Gauntlet; the Sky Warden's roof gives wings, the leap and flight together, mid-fight, to
   someone who has never glided. Nothing is impossible because of this, and nothing is taught. *Verified.*
5. **Three built things can no longer happen in a new world.** The Tether Vow (§17.7: the vault's call, the line that
   pulls you up the pylon, "Reach the beacon mast") needs D-rank without the rope, and D now always comes with it. The
   Sky Leviathan's trial (§17.18) needs A-rank without flight, and A now always comes with it. And the promise the first
   vault makes in hour one, "Its line waits for a D-rank hunter", is never paid. *Verified.*
6. **What the game says about the road is out of date in four places.** K, Powers, "The Ascent": the rope "from a
   Precursor vault", flight "the sky calls you to a trial". The level road at 73: "the Sky Leviathan's gate opens". The
   Wings trail: "Pass the Skyborn trial". *Verified.*
7. **The top rung is given after the story ends.** *Verified.*
8. **The moment itself is a title and one line.** Six seconds of rising light, a title twice the size of text, a line such
   as "In the air: jump again · sprint to dash · sneak high up to slam.", then a text tip. Nothing asks you to do the move.
   Three of the titles are riddles by the plain-speech rule ("Your legs remember the air.", "The vault knows you.",
   "Sovereign."). *Verified in code; the tours' pictures are in 2.4.*
9. **Mastery, which gates three of the six new moves, has no face** but one quiet line over the hotbar when a level is
   gained. *Verified.*

### 2.3 Root causes

| Symptom | Cause | Evidence | Fix lands where |
|---|---|---|---|
| "I don't know how the later ones unlock" | Each rung was given its rule by a different plan (25 Oct, 26 Oct, 29 Oct, 2 Nov); no one rule, no row on Tasks | Five different triggers in five files | One rule, one thread on Tasks |
| "It might be impossible" | Two rungs wait on hidden conditions; the safety net (a later fight hands them down) is silent | Findings 2 to 4 | Every rung opened by something you are already doing, with a place |
| "It has to be clear the moment you unlock it" | The moment asks nothing of you and is sometimes given mid-fight | Finding 8 | The Lesson: the way out of the place is the move |
| Built content nobody sees | Later plans moved the trigger and left the old scene behind | Finding 5 | The road reuses the Vow; the Leviathan's trial is retired |

### 2.4 The tours

Filled in when the runs land (run ids, what each picture shows, anything that went wrong). The page's "today" pictures
are theirs.

## 3. Approaches

- **A. Repair in place.** Keep every rule. Add a Tasks row for the leap and for wings, track the leap's gate, fix the four
  stale texts. Three to five hours. It leaves the hidden count, the silent hand-me-downs and the moment as it is.
- **B. One road: a rank fight opens a rung, a place gives it, and the way out of the place is the move (recommended).**
  Uses what is built: the Provings, the vault's Vow, the Gauntlet, the sky platform's rings, core and windways. New: one
  rule for waking a place, one thread on Tasks, and the Lesson (one kit for every unlock).
- **C. Everything inside the rank fights.** D the rope, C the leap, B wings, A flight, Sovereign the form, each handed
  over in the fight as the rope and flight are now. Nothing can be missed and nothing is found: vaults, spires and
  platforms lose their reason, wings move from 10 hours to 24, and every move is taught while a boss is hitting you.

B wins because it is his own sentence ("based on rank assessments, and then that unlocks somewhere ... that you can go and
unlock that item, and then it actually shows you how to use it"), because most of it exists, and because it keeps the
world's places worth going to. What would change the recommendation: if he finds the walk between the fight and the place
a chore in play, C's hand-over can be switched on per rung (the fall-back in 4.6 is already that).

## 4. The design

### 4.1 The rule

**A rank fight opens a rung. A place gives it. The way out of the place is the move.**

1. **Opened.** Winning the fight is the only condition the game sets. Nothing counts in secret.
2. **A place wakes.** At once the System picks one place of the right kind: one you have already met (the Saved for
   later book, §27.1) if you have, else the nearest one by the world's seed. It lights up in the world (the rank's colour,
   a tall thin beam at night), the Now line carries it for ten minutes with a needle and a distance, and it stays as one
   row on Tasks until you have been.
3. **Given there, by doing it.** The place hands the move over in a spot where using it is the way on: up, across or
   down. That is the Lesson (4.7).
4. **Never stuck.** The row never expires. Any place of the kind will do, not only the one that woke. And if you reach
   the next rank fight without a rung, that fight hands it down as it does today, and its own lesson is owed to you the
   next time you are free (4.6).

### 4.2 The rope (Tether): D-rank

- **Opened** by the Ringer's fall, as built: its chain lies at your feet, "It's yours now."
- **Change:** the chain is cold iron until a vault wakes it. `TrialHooks.done(RINGER)` no longer grants the tier.
- **The place:** a Precursor vault. The one you met in hour one made a promise ("Its line waits for a D-rank hunter") and
  is in your Saved for later book; every world also has the spawn vault 240 to 360 blocks from the origin. Home from the
  fight: "A vault has lit up for the chain you're carrying. It's 310 blocks east of here."
- **Given there:** the Tether Vow, as built and tested (§17.7): you come out under its pylons, the vault takes the chain,
  the line throws itself and pulls you up the pylon, then you must throw it yourself to reach the beacon mast. Its words
  become plain speech (4.8). The door rule is dropped for a hunter who has won the Ringer: the chain is the proof, so any
  vault's forecourt will do, whatever its door's rank. (Euan's rule of 26 Oct, "you actually need the rank", still holds:
  the rank is what won the chain.)
- **Then:** the sling is yours with the rope (Rope and Wings). Its lesson is the walk home through the trees: the first
  time a held rope would sling, the marker's chevron shows with your key.

### 4.3 The leap (Leaper): between D and C

- **Opened** by what the rope is for: **the first crown you reach by rope** (a Tether Spire's, a great tree's; both
  exist, both are timed, both answer only the line). The hidden count stays as a second way (Tether mastery 4), so a world
  with no spire near still opens it. Whichever comes first.
- **The place:** the Leaper's Gauntlet, as built (§17.10), but its gate opens **at the foot of what you climbed** (or
  where you stand, for the second way), stays on Tasks with its place until it is run, and is offered again if it is ever
  lost.
- **Given there:** as built: the chasm no running jump crosses; at its edge the leap is given; the far landing makes it
  yours. The Lesson adds your key at the edge and the gold figure jumping it once ahead of you (the Gauntlet's own ghost).
- **The Tasks row** after the Vow: "Climb a Tether Spire with your rope", with a needle to the nearest spire or great tree
  within 1,500 blocks (a median 160 to 290 away). None that near: the row reads "Get to know your rope" with no needle,
  and the second way opens it.

### 4.4 Wings: C-rank

- **Opened** by the Examiner's fall (the C rank fight). The core's own rule is unchanged: C-rank, up its rings by rope.
- **The place:** a sky platform. One you have met, else the nearest by seed (a median 280 to 380 blocks; the worst case in
  the placement maths is about 1,300). Its rings and chain glow brighter for you and a beam stands on it at night. "A sky
  platform has lit up for you. It's 420 blocks north-east. You climb its rings by rope."
- **Given there:** the core, as built. Then the first flight, which is new wiring of built parts: the scene ends facing the
  pier; a line of light rings runs off its end along the windway (or, with no second platform within 1,100, out and down
  toward open ground); "Run off the end of the pier and press <jump> in the air." The windway carries a first glide up to
  a thousand blocks and sets you down. Your first landing is the flare (Rope and Wings: wings cup, feathers fall).
- **A fall is not a death there:** for 60 s after the core's gift, inside the platform's ward, a fall is caught as the
  Ascent's soft landing already does, so a missed key press costs a climb, not your pack.

### 4.5 The open sky (Skyborn): A-rank

- As built: the Sky Warden's roof lifts at a third of its health and flight is given there; its fall makes it yours. It is
  the one rung whose place is the fight itself, and the fight needs the move. Kept.
- **Changes:** the edge's moment uses the Lesson's key strip in place of the line of text; if wings were never earned they
  are given first with their own beat, two seconds earlier; the takeoff (Rope and Wings) is taught at home, the first time
  you stand outdoors on the ground after the fight.
- **The Sky Leviathan's separate trial is retired** (unreachable since 2 Nov): its trigger, its toast, the level road's
  stop at 73 and the trail's step are removed or reworded. The Leviathan stays a Lord of the sky gates.

### 4.6 The Sovereign: the last rank

- **Opened** by the last rank fight (the Last Loop), which makes you Sovereign rank and opens the Seed. **Change:** the
  form is given there, not after an ending, so the hardest fight in the game pays the last rung and the form is used in
  the finale. Any ending still gives it to a save that somehow lacks it.
- **Its field stops routing bosses.** Today every `Enemy` within 24 blocks loses you and runs, which would empty the last
  fight. Lords, Wardens, guardians, Sovereigns and the last are slowed no more than a quarter and keep their target; the
  half again on every hit stays.
- **The place** is where you wake after the fight; the Lesson is the form itself: "When the gold line is full, press
  <ascend>." with the key on the Aether line the first time it fills.

**Handed down (the safety net, all rungs):** a later fight still gives any rung you lack. What changes is that each rung
handed down keeps a flag "lesson owed", and its Lesson plays the next time you are free and somewhere it can be done.

### 4.7 The Lesson: one kit for every unlock (UI)

Thesis: at the moment a move becomes yours, you are standing where it is the only way on, your own key is drawn on the
thing to aim at, and everything leaves the screen the second you have done it.

| Part | What it is | Reuses |
|---|---|---|
| The gift | The rising light and swell, 2.5 s, one plain sentence. Never over a fight: a rung given mid-fight shows only the key strip | `AscentScene`, the sister plan's sounds |
| Your key, on the thing | A key cap with your own binding and up to seven words, pinned to the target in the world (the pylon's plate, the chasm's far lip, the pier's end). Off screen it sits at the edge with an arrow. One at a time | `Kit`'s key caps (§57.1), the rope's marker |
| The gold figure | A pale gold you does the move once, ahead of you, from a recorded run. Again after 12 s if you haven't | `GauntletGhost`, `AscentRoutes` |
| Do it once | The server sees the state begin (it already counts this for tips). The prompt goes at that tick | `AscentTips` |
| The card | Four seconds, bottom centre, the band's glass: the move's name and its two or three keys as caps, no sentence. The same row then lives on K, Powers | `Kit`, `PowersPlace` |
| Mastery's face | Each rung's row on K, Powers shows its mastery as ten pips, and the next pip that gives a move is marked with a dark diamond | `Kit` |

States: HUD hidden (nothing drawn; the row on K still teaches); reduced motion (no gold figure, no beam, the key cap
only); a screen open (waits); in a fight (key strip only); a relog mid-lesson (not yet done, so it plays again); another
player beside you (theirs is theirs; the gold figure is drawn for its owner only); the key unbound (the cap reads "Not
set" and K, Controls is named). Nothing is ever cut off (§57.6): a line that does not fit wraps under its cap.

The six new moves (Rope and Wings) get the same Lesson at the moment each is earned, where it can be done: the sling at
the first place a held rope would sling; the spring at the first top a pull lands on after mastery 3; the rope strike at
the first creature in reach after mastery 5; the flare at the first landing on wings; the skim the first time you fly
low and fast after mastery 3; the takeoff at home after the A rank fight.

### 4.8 Words (plain speech; read each aloud)

| Today | Proposed |
|---|---|
| "The vault knows you." | "The vault has recognised the chain." |
| "The line is yours." / "Hold <key> to throw the line. Let go to fly." | "It's a rope now." with the cap: <rope> "Hold to throw it" |
| "Reach the beacon mast." | <rope> "Throw it at the mast and climb up" |
| "Vow kept." | "That's it. The rope is yours." |
| "A gate is beating like a running heart: the Leaper's Gauntlet." | "A gate has opened at the foot of the spire." |
| "Your legs remember the air." / "Jump. In the air, jump again." | "You can jump again in the air now." with the cap: <jump> "Press again in the air" |
| "The leap is yours." / "In the air: jump again · sprint to dash · sneak high up to slam." | "That was the leap. It's yours." and the card: <jump> again · <sprint> dash · <sneak> slam from high up |
| "Wings of light." / "In the air: jump, then jump again to open them." | "You have wings now." then, at the pier: <jump> "Run off the end and press this in the air" |
| "The sky remembers you." / "Open your wings, then jump again: fly." | The strip only: <jump> "Press again on open wings to fly" |
| "The sky is yours." | "You can fly now. It's yours." and the card |
| "Sovereign." / "Aether full, press U: thirty seconds as the sky's own…" | "You can take the Sovereign's form now." then <ascend> "Press when the gold line is full" |

### 4.9 The Tasks page and the K menu

- One thread, `rank.way_up`, on the Gates and rank lane (`core.api.Goals`), Lead voice (the kind of thing and the kind of
  place; never what the move is; never a fight). One row at a time: "Take the Ringer's chain to the vault that lit up",
  "Climb a Tether Spire with your rope", "Run the gate at the foot of the spire", "Climb the sky platform that lit up",
  "Fly from its pier". Each `.teaches` its rung, so `tasks_ledger` holds the road to "no unlock without a teacher".
- Nothing is listed for flight or the form: a Proving is never named ahead (§43).
- K, Powers, "What your body does": a row a rung held, with its keys as caps and its mastery pips; the next rung as a dark
  diamond with its Lead; the rest dark. The four stale lines go.

### 4.10 Data and state

- `AscentMemory` schema 4 (reads 3): per rung, the place that woke (dimension and block, or none), "given", "lesson done",
  "lesson owed". Per player, kept through death, synced to its owner. Flags are set only when the thing has happened, so a
  relog or a crash replays a lesson and never loses a rung.
- The chain is not an item (nothing to lose): it is "D-rank without the rope".
- Places are found, not stored: `Promises` first, then the structure search the dev commands already use, on a later
  tick, never loading chunks on the server thread (§28.9).
- Old saves: a rung already held is held and its lesson is not owed. A D-rank save without the rope sees the vault wake at
  its next login.
- Many players: every flag is per player; a party in a Proving each get their own place; a vault, a spire or a platform
  serves everyone who comes.

### 4.11 Failure and edge cases

| Case | What happens | Test |
|---|---|---|
| The place that woke is gone, changed or unloaded | Any place of the kind works; the row re-aims at the next nearest after a failed arrival | GameTest |
| No vault, spire or platform within range | Vault: the spawn vault always exists. Spire: the second way (mastery). Platform: the search widens to any distance; the Sky Warden hands wings down at A | placement test over 200 seeds |
| You never go | The row stays. The next rank fight hands the rung down and its lesson is owed | GameTest |
| You fall during a lesson | Caught as a soft landing inside the place's ward; the prompt returns | GameTest |
| You log out mid-lesson | Not done, so it plays again; a rung given stays given | GameTest |
| The leap's gate can't form (inside Carillon's basin, on a build) | It forms at the foot of what you climbed; for the second way it waits and the row says "outside the walls" | GameTest |
| The `ascent` world option is off | No rows, no places wake, the fights are won on foot as today | existing |
| The Sovereign's field and the last boss | Bosses are not routed | GameTest |
| A tower, a pearl or a power's flight to a core or crown | Refused as today ("It answers only to a line") | existing |

## 5. Proof before it reaches him

- `way_up_road`: a fresh player walked rung by rung by the real grants: at every stop there is exactly one way-up row or a
  Proving ahead; every row has a place; no rung is held with its lesson neither done nor owed.
- `way_up_places`: over 200 seeds, a vault, a crown or the second way, and a sky platform are found from a spread of
  positions; the distances are printed.
- One test a mechanism: the chain is cold until a forecourt; a crown opens the gate at its foot and the row tracks it; the
  C fight wakes a platform; the first flight's soft landing; a rung handed down owes its lesson; the form at the last
  fight; the field leaves bosses.
- `tasks_ledger`, `ui_text_never_cut`, `ui_word_budget` and `journey_ledger` stay green.
- The suite on CI, once. One look tour of the five moments.
- Then his hands: a fresh world to D and the vault is the first twenty minutes worth playing.

## 6. Stages and cost

From this repo's history: the Tether Vow, its test and its tour were one commit on 25 Oct; the whole progression plan
(Saved for later, the locks, the first thread, six phases) was built in one day on 26 Oct; Rope and Wings sized its tips,
Tasks rows and tests at 2 to 3 hours.

| Stage | What | Hours of building |
|---|---|---|
| 0 | Truth: `way_up_road` failing on today's gaps, the four stale texts | 1 to 2 |
| 1 | The rule: waking a place, schema 4, the Tasks thread, the handed-down flag | 3 to 4 |
| 2 | The Lesson kit: the key on the thing, the gold figure, the card, the K rows and pips | 4 to 6 |
| 3 | The five places: the vault and the cold chain, crown to Gauntlet, the platform and the first flight, the Sky Warden's edge, the form at the last fight and its field | 4 to 6 |
| 4 | The six new moves' lessons (after Rope and Wings is merged) | 2 to 3 |
| 5 | Words, SPEC, the one test pass, one look tour | 2 to 3 |

16 to 24 hours of building, one session, coded whole and tested once at the end. What could move it: his first play of
the vault; whether the first flight needs more than the windway gives it.

Rollback: `wayUp` in `awaken-body.toml` (on). Off: every rule exactly as today.

## 7. Meeting the sister sessions

- **Rope and Wings** (`claude/movement-plan-e38dfa`): its earn rules stand (the sling with the rope, the spring at Tether
  mastery 3, the rope strike at 5, the flare with wings, the skim at Wings mastery 3, the takeoff with flight). This plan
  supplies their lessons and the K rows, so that plan's stage 6 need only keep its tips' text. One number for them: with
  the sling, a held rope throws about every two seconds, so mastery 3 and 5 arrive after about four and ten minutes of
  swinging; the points a rope are theirs to tune.
- **One Stage** (merged): the K rows use `Kit` only; no new widget.
- **The Next Thing** (Tasks, stages 0 to 2 built): the thread uses its `.thread`, `.lead`, `.teaches`.
- **Fights** (merged): `RingerWarden`, `SkyWarden` and `LoopWarden` each change by one hook call.

## 8. What Euan decides

On the page, one recommendation each: the rule; the rope's vault; the leap's crown; the sky platform that lights up;
the first flight; flight staying in the A fight; the Sovereign at the last fight; the Lesson; the Tasks row; rungs handed
down; the new moves' lessons.
