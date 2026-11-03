# Dreams worth having (lucid dreams remade)

Planning only, 3 Nov 2026. Nothing here is built. Page: `docs/dreams/page.html` (published as an Artifact).
Evidence tour: `tools/autopilot/dream-today.txt`, CI run 41586108055 (macos-15, 19 shots, pass).

Euan, 3 Nov: "think of a better usecase for lucid dreams, they are very boring now and add no value or fun to the game.
Could do some parkour or smth, or different puzzles that make you remember hidden secrets or moves if you finish them,
or maybe even get bells. With a timer etc. Right now it's very confusing and boring, and I usually jump in the void to
make it end cause it's so useless."

## 1. Understanding and the success contract

- **The job.** Sleeping is how Euan ends a day in a survival RPG. A dream sits between two days. It should be a short
  night adventure he wants, that pays him something he uses the next day.
- **The pain (verified, section 2).** A dream is forced on 40 to 95% of nights, lasts 3 to 8 minutes, has no stated
  goal, opens with your face against a wall, asks you to fill a meter by pressing H, shows a jumble of floating
  16x12x16 cut-outs of your day, and pays residue, a sliver of XP and bonuses that only help other dreams. The only
  quick way out is to fall into the void twice.
- **What he wants.** A clear goal and a timer, something fun to do that you can't do awake (parkour, puzzles), and a
  reward worth having: a move, a hidden secret, maybe a bell. A clean ending.
- **His ideas, weighed** (section 3): parkour on a timer, yes (the Runs); puzzles that make you remember secrets or
  moves, yes (the Rooms give secrets, the Runs give moves); bells, yes within the hundred (the six dream bells already
  counted move into their own dreams; more is his call, decision 7).
- **Hard constraints.** The hundred bells' plan (`docs/director/BELLS.md`: the dreams keep six). Plain speech. Every
  progression step on the Tasks page (`core.api.Goals`). No boss named before you meet it. Nothing fights for you (no
  Echoes, 29 Oct). Multiplayer-safe. Its own look. Everything has a way in (`journey_ledger`). Module rules: lucidium
  owns its package; bells, body and progression are touched only through `core.api` hooks agreed through the
  orchestrator.
- **Unknowns, labelled.** Whether Euan has met the six dream bells yet (merged 2 Nov; his note may predate them). Whether
  he plays dreams in multiplayer. How long a Run should be to feel right (we start at 60 to 120 s and tune from shots
  and his play).

**Strong result.** He lies down, knows within five seconds what this dream wants, plays two to four minutes of
something he can't do awake, wakes with a move, a place or a bell, and wants the next one. He never falls into the void
to leave. **Weak.** A clear goal, but a dull course; or fun, but nothing kept; or dreams most nights. **Unacceptable.**
A confusing start, no exit, a failure that wastes the night, dreams forced on him, prizes that break balance or the
bell count.

**Non-goals.** No new movement tiers. No change to how the Ascent's tiers are earned. No new bells unless he says so.
No hub world in this plan (alternative C). No change to the Veil or the Stillpoint beyond its existing In door.

**Must not regress.** Safe waking from every path (logout, crash, server stop, death); the night still passes in the
dream and Day's End follows; sneak for a dreamless night; the counters other modules read (`awaken:dreams`,
`awaken:lucid_dreams`, `awaken:dream_manifests`, `awaken:nightmares_beaten`, `awaken:mentor_duels_won`,
`awaken:secrets_found`); the level road's dream steps (12, 21, 23, 27, 29), the Dreamwalker path, the Truths and
latents that give `awaken:lucidity_start`, the Dream Recorder and the Stillpoint's In door; shared dreams.

## 2. Audit and diagnosis (verified)

The module (`dev.awaken.lucidium`, 7,743 lines) was built on 23 Oct, the first day of the project, and has barely moved
since (18 commits, the last real feature on 24 Oct). It predates the Ascent, the bells, the Tasks page, the notification
director and the Journey, so it plugs into none of them, except the six dream bells added from the bells side on 2 Nov.

| Part | What it actually does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Sleep (`dream/Sleep`) | Rolls 40% + SPI% + bonuses (max 95%) on every sleep; the first night always dreams | `Sleep.begin`, `Lucidity.dreamChance` | A dream about half your nights, wanted or not | replace with "only when one is waiting" |
| Plan (`plan/DreamPlanner`) | 4 to 8 snapshot islands on a golden-angle spiral, a spanning tree of bridges, 1 to 2 portal doors, a nightmare, a mentor, a vision, two signs, 3 to 8 min | `DreamPlanner.plan` | A tour of chunks with no goal | keep the cell, compiler and palette; replace the layout |
| Day Journal (`journal/DayJournal`) | Captures 16x12x16 templates at moments of your day | doc §1 | The one personal touch; the islands read as cut-outs (shot dt_08) | keep; use your home as the start and one Room |
| Lucidity (`dream/Lucidity`) | A 0 to 100 meter: H +12 every 3 s (half after five), signs +15, door +20 | `Lucidity`, `Dreams.realityCheck` | Five presses in about 12 s make you lucid: the core verb is spam H | replace: lucidity becomes the dream's clock |
| Lucid powers (`dream/Manifest`) | 50+: fly anywhere, hold H to shape a door, stair or bridge; 80+: summon a creature to fight beside you | `Dreams.gain`, `Manifest` | Tools with nothing to use them on; summon breaks the "nothing fights for you" rule | shape becomes a Room's tool; flight only in its own Run; summon cut |
| Rooms that change (`ActiveDream`) | Islands unseen for 6 s rewrite themselves | doc §6 | Constant shifting with no rule you can learn | keep as one Room's rule (Look away) |
| Nightmare (`plan/Nightmares`) | Your week's deaths spawn a fight on one island | `Dreams.startNightmare` | Fine on its own, lost in the jumble | keep as the Chase |
| Mentor (`dream/Mentors`) | Your Path's mentor duels you; +20% of a level | `Mentors` | Fine on its own | keep as the Duel |
| Visions (`dream/Visions`) | A real far place shown free on an island, then a Codex rumour | `Visions.prepare/reveal` | A reward given for nothing | keep the finder; make it a Room's prize |
| Ending (`Dreams.tickPlayer`) | Time (3 to 8 min), falling twice, dying, the bell, a command | `Dreams.tickPlayer` | The quick exit is the void | replace: catch falls, a Wake up button |
| Dream bells (`bells.DreamBells`) | Six bells, one a dream, each behind a small puzzle; strike needs lucidity 40/60/80 | `DreamBells` | The only goals dreams have today | keep the six; move each into its own dream |
| HUD (`client/LucidityHud`) | A meter at the left, diamonds for signs, control hints | shot dt_10 | Reads as a stat screen, not a goal | replace with a top crescent clock and one goal line |
| Dream Journal | Three intentions (+10), "Write down the dream" (+0.5 recall) | shot dt_15 | Chores with tiny numbers | intentions choose tonight's dream; it writes itself |
| Tasks | Dreams are not on the Tasks page | shot dt_16 | You can't tell a dream is coming or why it matters | one line when a dream waits |

**Reproduced end to end** (tour `dream-today`, run 41586108055; `run-autopilot/screenshots-ci/dream-today-41586108055`):

1. Arrival (dt_01): your face against a wall between a building and a fence; the HUD says 0, DREAMING, two empty
   diamonds, "H: reality check". The first line on screen is the revelation "That was your day, rearranged... Look at
   your hands next time.": past tense while you're still in it (`revelation/first_dream.json` fires on
   `counter:awaken:dreams>=1`, which goes up as you enter).
2. The dream's own directions arrive late or never. The bell's line was offered at tick 2453 and shown at 3027, 29 s
   later (the notification director's calm gap). "You can fly. Double-tap jump." and "You fall... and land where you
   began." were filed unread when you woke (`[herald] file`). The dream speaks through a queue built for the waking world.
3. From above (dt_08, dt_09): six moss discs with cut-out bits of the city and the wild, joined by ribbons of terracotta,
   cloud steps and a spiral stair. Nothing is a destination; nothing says "go here".
4. Going lucid (dt_10, dt_11): "You are dreaming." with "Fly. Shape it. Remember it.", a title toast and three
   control hints. Powers, no purpose.
5. Leaving: a fall into the void (the second, counting one earlier) ends it with "You fell, and jolted awake". On waking
   the Dreaming Bell, which asks no lucidity at all and which the player never went near, left the line "You weren't
   lucid enough to reach it and hold it. Be clearer in the next one." (`DreamBells.woke`'s default case).
6. What you keep (dt_15): the journal's summary "A dream of a place you chose to remember, a place you chose to
   remember, your first dripstone caves." and "Recall 0".

**Root causes, ranked.**

| Symptom | Cause | Evidence for | Against | Smallest test | Impact if fixed |
|---|---|---|---|---|---|
| Boring | No goal and no designed play: the plan is a layout of memories, not a challenge | planner code, dt_08 | the six bell puzzles are goals (only one a dream, easy to miss) | one dream with one goal and a course | highest |
| Confusing | Thirteen mechanics, none tied to a goal; directions stuck in the notification queue | doc §5 to §8; herald log | some lines do show | count the concepts a player must know; time the first line | high |
| No value | Rewards are residue, tiny XP and lucidity bonuses that only help dreams | `Dreams.wake`, `trainingXp`, perks | visions and bells do matter | list what reaches the waking game | high |
| Void exit | Falling is the fastest way out; nothing else ends a dream on purpose | `tickPlayer` | `/awaken dream wake` exists (a command) | count exits a player can find | high |
| Forced | A dream on 40 to 95% of nights | `Sleep.begin` | sneak skips (hidden) | nights per dream in a week | medium |

Hypothesis left open: that Runs feel good depends on the Runner and Ascent moves feeling reliable in a tight course.
Euan called them "genuinely becoming really good" (26 Oct); the first Run tests that early (stage 2's first gate).

## 3. What a dream is for

The story already says it. The Lucidium is "where minds make things". The Sleepers went In, into a shared dream. The
Signal is you, from a loop that reached the bottom, sending advice back up "through the Dream". The awakening's first
line is "Your body remembers." So a dream is **where you remember what an earlier you already knew**.

In the journey, the waking game is the hundred bells, your rank, your Hearth and the truth. A dream is a short trial at
night that gives back one thing for tomorrow:

- **a move**: your body remembers how (a Run);
- **a hidden place**: you wake knowing where it is (a Room);
- **a bell**: one of the six the dreams keep (six dreams built for them);
- **a fear let go** or **a lesson from your mentor** (the Chase and the Duel, today's content, given a goal).

The loop: lie down with a dream waiting, wake in your own room in the dream, press H to take control (the goal and the
clock appear), play one trial, reach the end, take what it gives, wake with it. The morning card says what you kept.

**His ideas against that goal.** Parkour on a timer is the best way to remember a move: the course is built around it,
so you learn it by doing it, under a clock. Puzzles are the best way to remember a place: a dream rule (look away and it
moves; don't wake the sleeper; notice what's wrong) is a lucid-dreaming idea made into play, and the prize is a real
secret in your world. Bells already have six homes in dreams; the plan gives each its own dream instead of hiding it in
a jumble.

## 4. Alternatives and the recommendation

| | A. Targeted repair | B. Remembered trials (recommended) | C. The Sleepers' hall |
|---|---|---|---|
| What | Today's island dream plus a goal line, a clock, a catch and a Wake up button; the bell puzzles as goals | Each dream is one designed trial (a Run, a Room, the Chase or the Duel) with one goal, a clock, a catch, an exit and a prize | A persistent dream hub; doors lead to trials; your progress lights the hall |
| Fun | Still a walk among chunks | Parkour and puzzles you can't do awake | Same trials, plus choice |
| Value | Bells only, six and done | Moves, places, bells, fears, the mentor; places keep coming all game | Same as B |
| Clarity | Better | Four things to know: H starts, the goal line, the clock, Esc wakes | Adds a hub to learn |
| Cost at this pace | about 3 h | about 20 to 26 h over four stages | B plus about 8 h |
| Risk | Low | Course feel; content volume | A nightly walk before the fun |

**Why B.** A fixes the exit and the confusion but leaves the boredom: nothing to do between the goal and the door. C
gives choice, but adds a walk to every night and a second place to maintain; it can come later on top of B (replays
already have a home in the Dream Recorder and the Stillpoint's In door). B reuses almost all of the module: the cells,
the off-thread compiler, the palette, the safe wake, the Day Journal, Manifest, the nightmare and mentor spawns, the
visions finder, the shared-dream code. **What would change the recommendation:** if the first Run (stage 2's gate)
reads as a worse Gauntlet, the Runs shrink to a small part and the Rooms carry the plan.

## 5. The behaviour, as a contract

### 5.1 When a dream comes

- **Only when one is waiting** (replaces the 40 to 95% roll). At most one a night, and not two nights running unless
  you ask for one in the Dream Journal. New dreams join the waiting list at most one a play-day.
- **What makes one wait** (the lucidium's dream ledger, server, per player):
  - the first dream: the night after the level road's "Lucid dreams" step (level 12): the Dreaming Bell's dream;
  - a Run for each move family your body can do, in order (Runner moves from the start; the rope after the Tether
    Vow; the leap after the Leaper's trial; the wings after a sky core);
  - a Room when a place worth finding lies within reach and you haven't found it (the visions finder: a surface
    structure 160 to 1,400 away you've never been near; later a region's wonder or secret where its module exposes one);
  - a bell dream when your rank reaches the bell's (Daydream C, Lullaby C, Falling B, Nightmare's B, Lucid A; their
    BellCatalog ranks), one at a time, in today's order;
  - the Chase when one kind of death came twice this week (today's nightmare families); the Duel once you have a Path,
    every few nights.
- **How you know, without being told:** at night your bed sheds a few pearl motes while a dream waits (client, your own
  bed only). The Tasks page shows one line: "A dream is waiting. Sleep in your bed tonight." (Now, goal OUT; a bell's
  dream under BELLS: "A bell is waiting in a dream. Sleep in your bed tonight."). What the dream holds is not said.
- **Sneak as you lie down** to sleep without it (today's rule, now told once on the Tasks line's hover).

### 5.2 Arriving

- You arrive in your own room: the Day Journal's latest home snapshot, rebuilt in the dream's colours, the window
  looking out over the cloud sea. With no home snapshot yet, a dream bedroom from the kit. You face the open door.
- Through the door you can see the end: the Waking Door (a tall frame of pale light) or the bell, small and far off.
- No meter. One line, centre screen, drawn by the dream itself (never the notification queue): "You're dreaming.
  Press H to take control." After your third dream: "Press H." Nothing happens until you do; the night waits.

### 5.3 H: taking control (becoming lucid)

- H: your hand flickers, the room stops drifting, the colours sharpen (today's lucid shader). The goal appears top
  centre in one plain line, and under it a thin crescent that empties: the clock.
  - Run: "Get to the door at the end before the dream fades." and, small: "This dream remembers the wall-run."
  - Rooms: "Something in this room is wrong. Find it." / "The bell moves whenever you look away from it. Catch it." /
    "Something is asleep next to the bell. Take the bell without waking it." / "You're falling. Find a way to land." /
    "This hallway keeps coming back. Find the way out of it." / "There's no door. Make one."
  - Chase: "Your fear is coming after you. Get to the door."
  - Duel: "Your mentor is waiting. Fight until they give in."
- **Lucidity is now the clock**: how long you can stay lucid. Each dream has its own time (a Run: its par times 1.8,
  at least 60 s; a Room: 180 s; the Chase 120 s; the Duel 150 s). Every point of starting lucidity you already earn
  (intention 10, the Dream Bell's second sleep 25, the Dreamwalker path 30, the Truth 20, latents 15 and 10, the
  murmuration 10) is one more second, up to 60. Nothing that grants lucidity today is orphaned.
- H again while lucid: the true path (or the wrong thing) glints for 2 s and costs 2 s. Once every 4 s.
- Dream signs are placed in every dream: notice one (look at it for half a second) for 8 more seconds ("That's not
  right. +8 seconds"). Your personal sign is always one of them.

### 5.4 Ending (never by the void)

- **You win**: reach the door, catch or take the bell, find the wrong thing, land, make the mentor give in. The prize
  plays in the dream for 2 to 4 s (5.5), then you wake.
- **The clock runs out**: "The dream slipped away." You wake. The dream is still waiting; try it another night.
- **You fall**: the dream catches you. A soft swoop puts you back at the last ring you passed (the room's start in a
  Room) and costs 3 s. No fall ever wakes you; there is nothing to gain from the void.
- **You want out**: Esc shows a "Wake up" button in a dream (in place of nothing; singleplayer and servers alike). You
  wake at once; nothing lost; the dream still waits.
- **You die** (the Chase, the Duel): you wake Shaken, as today; the dream still waits.
- Logout, crash, server stop: today's safe wake, unchanged; the dream still waits.

### 5.5 What you keep

- **A move** (Runs): "Your body remembers the wall-run. Sprint and hold jump along a wall to run on it." The move is
  marked remembered in Status > THE BODY with its keys and the line from the dream; its Ascent track gets 50 mastery
  points on the first finish and 50 on the first gold (level = floor(sqrt(points / 25)), so about one level early on);
  its tip counts done. Medals by the Gauntlet's rule (gold within par x 1.1 + 1 s, silver within par x 1.5 + 2 s); your
  best run becomes your ghost in replays.
- **A hidden place** (Rooms): the place is shown at the end, undistorted, through a window of dream glass (today's
  vision look): "You saw a real place. It's on your map now, far to the north-east." It goes on your Atlas (the patch
  around it drawn in, `AtlasHooks.reveal`, and a pearl mark, `MapMarks`) and in the Journal's Visions. Walking up to it
  awake: "You dreamed of this place." and `awaken:secrets_found` (today's).
- **A bell** (the six): you take it in the dream and wake holding it, as today (`Bells` through `BellHooks`).
- **A fear let go** (the Chase): today's reward (the week's fear cleared, +10% of a level, `nightmares_beaten`).
- **Your mentor's lesson** (the Duel): today's (+20% of a level, `mentor_duels_won`).
- **Every finish**: world XP like a Gauntlet run (6% of a level the first time, 2% on a replay at most once a day),
  2 Dream Residue (the recipes keep their way in). The morning card lists what you kept and your time.
- The Dream Journal writes itself: each dream, what it was, your best time and medal, what it gave. "Write down the
  dream" goes; the recall bonus becomes "a second per dream you've finished", capped at 10, so old saves keep theirs.
- Intentions become the choice of tonight's dream when more than one waits: "Tonight I want to... learn a move / find
  something hidden / face my fear" (and "find a bell" while one waits). The default is the oldest waiting.

### 5.6 The trials

**Runs** (one move family each; an authored order of pieces laid by code, proved by a solver that sets the par, like
`RigGauntlet.solve`):

| Run | Waits | Pieces | Dream twists |
|---|---|---|---|
| The first dream (the Dreaming Bell) | level 12 | walk, a gap, a step up, H to start | the road lays itself as you look along it |
| The wall | from the start | wall-runs over gaps, wall jumps between facing walls | walls slide in from the clouds as you sprint |
| The ledge | from the start | hang, shimmy, ledge jump, drop to the ledge below | ledges that fold out of a sheer face |
| Over and under | from the start | vaults over fences and walls, slides under low beams, the landing slide | beams that lower as you come |
| The climb | Wall Stride | pipe grips, wall climbs | a tower that grows as you climb it |
| The rope | the Tether Vow | swings, release windows, zips to tops | anchors that light only in reach |
| The leap | the Leaper's trial | double jumps, air dashes, dive slams onto pads | islands that drift apart |
| The wings | a sky core | dives and glides through rings | updraft columns of pale light |

Dream twists use what the engine can do: blocks that appear or vanish (drawn rising like the Rig's moving blocks, then
placed), pieces that move between beats, short levitation columns. No gravity flips (Minecraft can't turn a player's
gravity).

**Rooms** (one dream rule each; built from your places where they fit; each repeats with new places as new hidden places
become waiting):

| Rule | Win | Fail state | First time |
|---|---|---|---|
| What's wrong here | Your place from today, rebuilt with three things wrong (a sign, a block, a door); look at each and press H | a wrong guess costs 5 s | the Lucid Bell (A) |
| Look away | The goal moves 16 to 28 away whenever it leaves your view; reach it using the room's mirrors of dream glass | none; it just moves | the Daydream Bell (C) |
| Hush | Something sleeps by the goal; creep in; sprinting within 18 or walking within 12 wakes it | it wakes and comes for you; the goal moves | the Lullaby Bell (C) |
| The long fall | You fall past the goal; looking down keeps you falling; looking up lands you | none | the Falling Bell (B) |
| The fold | A hallway that loops; the way out is the door you passed, walked backwards | none | a hidden place |
| No door | A sealed room; hold H on the right wall to make a door (today's Manifest) | the wrong wall makes a door into a wall | a hidden place (the level road's "Shape the dream") |

**The Chase**: a Run with your week's fear behind you (today's families and spawns), the first time carrying the
Nightmare's Bell. **The Duel**: today's mentor in a ring of dream glass, with the clock.

### 5.7 Multiplayer

Sleepers within 10 blocks of each other share the dream (today's rule). In a Run each sees the others running; everyone
who reaches the door keeps the move. A Room's place goes to everyone there. A bell to each who takes it (one each, as
the bells' rule is per player). The night waits for the dreamers, at most the dream's clock (today it waits up to
8 minutes). Nothing a dream gives can be given to someone not in it.

## 6. Architecture, data and state

- **Server authority**: the ledger, the clock, rings, catches, wins and prizes are server-side. The client draws the
  room's drift, the course's moving pieces, the crescent clock, the goal line, ghosts and the bed's motes.
- **Plans**: `DreamPlan` gains a kind (RUN, ROOM, CHASE, DUEL), its trial id and its pieces; `planVersion` 2. The cell
  system, `BlueprintCompiler` (off-thread), the palette and the preload are reused. A version-1 plan in the history
  replays as before; only version 2 dreams count for medals.
- **`Dreamer`** (attachment, schema bump, kept through death): `waiting` (id, since day, why), `finished` (id → best
  ticks, medal, day, times), `remembered` moves, `lastDreamDay`, `lastNewDay`. Old fields read for compatibility
  (`recall` → finished count; intentions kept).
- **`DreamState`**: `lucidAt`, `clockEnd`, `ring`, `catches`, `won`; `falls` dropped.
- **Old worlds**: at first login after the update the ledger is seeded from what the player already has (tiers, rank,
  Path, held dream bells), at most three waiting.
- **Cross-module calls** (one line each, agreed through the orchestrator):
  - bells: `BellHooks.dreamBellNext(player)` and `BellHooks.dreamBellTaken(player, id)`; `DreamBells` keeps its words
    and the Book's leads, the lucidium runs the puzzles. The count stays six.
  - body: `MovementHooks.remember(player, move)` and `MovementHooks.addMastery(player, track, points)`; THE BODY shows the
    mark; the tip counts done.
  - progression: nothing new (counters as today).
  - tasks: `Goals.register` from the lucidium.
  - atlas and map: `AtlasHooks.reveal`, `MapMarks.register` (existing).
- **Client**: a pause-screen "Wake up" button (`ScreenEvent.Init.Post.addListener` on `PauseScreen`, both verified in
  `.ref`), sending a payload the server checks (dreaming, not already waking). The dream's lines and clock are drawn by
  the lucidium's own HUD layer, never the notice queue.
- **Revelations**: `first_dream` moves to waking (trigger on the first finish, past tense right), `first_lucid` to the
  first H.
- **Feature flag**: a server option `dreamsRemade` (on once stage 1 passes review; off gives today's dream) until stage 4
  removes the old path.

## 7. Failure register

| Case | Detection | What the player sees | Recovery | Test |
|---|---|---|---|---|
| Plan not ready at sleep | `Planner.ready` empty | a normal night | the dream still waits | `dreams_waiting_ledger` |
| No home snapshot | journal has no HOME | the dream bedroom from the kit | none needed | `dreams_arrival` |
| Course fails its solver | `solve` false after 4 gentler variants | that Run isn't offered tonight (logged) | next variant seed tomorrow | `dreams_runs_solvable` |
| Fall below the course | y under the course floor - 6 | the catch, -3 s | last ring | `dreams_catch_never_wakes` |
| Clock runs out | `clockEnd` | "The dream slipped away." | still waiting | `dreams_clock` |
| Wake up pressed | payload | wake card, nothing lost | still waiting | `dreams_wake_button` |
| Death in the Chase or Duel | death event | Shaken | still waiting | `lucidium_safe_wake` |
| Logout or crash mid-dream | today | wakes in bed | still waiting | `lucidium_safe_wake` |
| Directions held by the notice queue | lines drawn by the dream itself | at once | n/a | `dreams_arrival` |
| A shared dream's host leaves | participants | the others go on | today | `together_*` |
| A bell already held | `Bells.has` | that bell dream never waits | n/a | `bells_dream_puzzles` |
| A hidden place already found | visions `found` | the Room offers the next | n/a | `dreams_hidden_place` |
| Old save mid-dream at update | v1 state | woken safely, ledger seeded | n/a | `dreams_upgrade` |

## 8. Look and sound

Its own look, not the jumble's quartz and moss: the Lucidium's lilac hour and cloud sea stay; the courses are pearl
stone and ribbon roads that lay themselves, rings of soft gold light (own render type, not end rods), the Waking Door a
tall frame of pale light with a slow pulse; the catch a soft swoop and a heartbeat; the clock's last ten seconds a
quiet tick that slows. A style sheet first (`tools/art/specs/dreams.py`), checked with a studio tour, before the courses
are built. Own particles and sounds; the music swells with flow in a Run.

## 9. Evaluation and delivery

**Baseline** (today): 13 mechanics to learn, no stated goal, the first direction 29 s late, 3 to 8 min, a dream on 40
to 95% of nights, the void as the quick exit, prizes that stay in dreams.

**Acceptance gates.** A player knows the goal within 5 s of arriving (the line is on screen at arrival and at H); every
dream can be left in one action without the void; every dream has exactly one goal line and one clock; every Run passes
its solver at every rank; no fall wakes you; a dream comes only when waiting, at most one a night; each prize shows in
the morning card and in its waking place (THE BODY, the Atlas, the Book); the bell count stays 100; all existing
counters keep counting; Euan plays one and doesn't reach for the void.

**Stages** (one builder on Opus at xhigh, no sub-agents; CI GameTests; CI look tours on GitHub macOS; each stage handed
back to the orchestrator; Euan sees the shots):

0. **Look** (about 2 h): style sheet, the road, rings, the Waking Door, the crescent clock and goal line; a studio tour.
1. **The frame and the first dream** (about 4 h): the ledger, one a night, the bed's motes, the Tasks line; arriving in
   your room; H to start; the clock; the catch; Wake up; the prize moment; the wake card; the Journal writing itself;
   the Dreaming Bell's dream as a short Run; revelations moved. Behind `dreamsRemade`. Tests: `dreams_waiting_ledger`,
   `dreams_arrival`, `dreams_clock`, `dreams_catch_never_wakes`, `dreams_wake_button`, updated `bells_dream_puzzles`,
   `lucidium_safe_wake`. Tour: `dreams-first` (arrival, H, the goal, a catch, the bell, the morning card).
   **Go/no-go:** the arrival shot reads at a glance; the first Run feels like a dream, not a gate.
2. **Runs** (about 7 h): the course kit, the solver, medals, ghosts, moves remembered, mastery; the wall, the ledge,
   over and under, the climb (rope, leap and wings in stage 4). Tests: `dreams_runs_solvable`,
   `dreams_move_remembered`. Tour: `dreams-runs` (each piece by `/awaken dream at <piece>`, a finish, a medal).
3. **Rooms, bells, the Chase and the Duel** (about 7 h): the six rules, hidden places on the Atlas, the six bells moved
   into their dreams (with the bells side), the Chase and the Duel reframed, intentions as tonight's choice. Tests:
   `dreams_room_rules`, `dreams_hidden_place`, `bells_dream_puzzles`, `dreams_shared`. Tour: `dreams-rooms`.
4. **The rest and the polish** (about 5 h): the rope, leap and wings Runs, replays through the Dream Recorder and the
   Stillpoint's In door (medals and ghosts), friends' runners in shared dreams, the sound pass, old worlds seeded, the
   old path and `dreamsRemade` removed. Tests: `dreams_upgrade`, `journey_ledger`. Tour: `dreams-look-4`.

Estimates are at this project's pace (the whole module was built in a day); the bottlenecks are CI tours and Euan's
review of the look. **Rollback**: `dreamsRemade` off restores today's dream until stage 4.

**Risks.** Moves that misbehave in a tight course (stage 2's first Run is the check); content volume (start with four
Runs and three Rooms, add after shots); multiplayer nights wait on dreamers (bounded by the clock); cross-module hooks
(bells, body) need their owners' yes through the orchestrator.

## 10. Decisions for Euan

1. A dream's job: win back one thing an earlier you knew (a move, a hidden place, a bell, a fear let go, a lesson).
2. A dream comes only when one is waiting, at most one a night and not two nights running unless you ask; your bed
   glows and the Tasks page says one is waiting; sneak to skip.
3. You arrive in your own room; H takes control and starts it; lucidity becomes the clock (every bonus you earn today
   becomes seconds; dream signs add time).
4. Never by the void: falls are caught (back to the last ring, 3 s), Esc has "Wake up", time running out wakes you and
   the dream waits.
5. Runs: parkour on the clock, one move each, medals and your ghost; finishing it, your body remembers the move (THE
   BODY, its keys, mastery).
6. Rooms: one dream rule each; the prize is a real hidden place, put on your map.
7. Bells: the six dream bells each get their own dream; the count stays six (rank replaces lucidity). More dream bells
   would mean fewer elsewhere in the hundred: recommended no.
8. The Chase (your fear behind you) and the Duel (your mentor) stay, with a goal and the clock.
9. The Dream Journal chooses tonight's dream when several wait and writes itself; replays through the Dream Recorder and
   the Stillpoint for medals.
10. Cut: summoning a creature to fight beside you (nothing fights for you), the meter, the island jumble, rooms
    rewriting themselves at random, "write down the dream", XP for casting powers in dreams.
11. Its own look and sound, style sheet first.
12. Moves you can only learn in dreams (new moves): not in this plan; Runs teach the moves the game has but never tells
    (the landing slide, the drop to the ledge below, the release window). Recommended: no for now.
