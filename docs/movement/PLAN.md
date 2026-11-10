# Rope and Wings: the plan record (10 Nov 2026)

Status: **proposed, not approved, nothing built.** The approval page is https://claude.ai/artifact/31giigd6bKnX4kS2sLZ22w
(source `docs/movement/page.src.html`, built by `build_page.py`; his answers save in its `answers` collection, `d1` to
`d12` and `feel`). This file is the builder's contract once Euan says yes. It follows `.claude/PLANNING-PROCESS.md` and holds
the standard of `.claude/UI-EXCELLENCE.md` for how movement looks, sounds and reads on screen.

## Euan's words (10 Nov 2026)

"analyze the entire movement system in the game. So more so like the later movement, like the rope, the wings, the things
like that. And I want to massively optimize the enjoyability of that and the satisfaction that comes from it. The rope,
like this kind of swinging, needs to be massively optimized. Right now it's just not good, in my opinion. It's not where
it needs to be. Like, I want it to be a bit more guided, and it should feel like you're basically playing like a kind of,
in a sense, a Spider-Man game nearly. And then the wings need to be massively improved in terms of visual quality. That's
the biggest thing. It's like right now they look a bit rushed. I want them to truly feel cinematic and awesome when you
have them, like truly, truly awesome. And then obviously it should be thought out how they tie it. They link the powers
and everything, so you can make sure you can use powers in the air. And any kind of extra movement that comes to, from
these, that makes it like them better and more satisfying to have, and like something that's truly insane, awesome to
unlock, would be awesome. Like, really push these to the maximum satisfaction level."

## 1. What he is asking for

- **The job.** Crossing the world on the rope and the wings is the reward for the hours before them. It has to be the
  most enjoyable thing in the game to do with your hands.
- **The pain.** The swing "is just not good". The wings "look a bit rushed". Powers and movement do not meet in the air.
- **What he wants.** A rope that guides you the way a Spider-Man game does: hold a key and go, wherever you are. Wings
  that are worth stopping to look at. Powers that work up there. New moves worth earning.
- **His ideas, to test rather than assume:** more assistance on the rope; better-looking wings; casting in the air.
- **Fixed:** the ladder and how each rung is earned; boxes on the grid, never a shape Minecraft doesn't have; a look of
  its own, never vanilla's; sounds from Minecraft's own recordings; one side decides and a reply never changes the
  screen; plain speech; the server checks every move; his keys; a steady 60 on Cinematic.
- **Not known before this audit:** what the rope does where he actually plays. That is what section 2 measures.

Success, in things that can be seen:

| | Strong | Weak | Not acceptable |
|---|---|---|---|
| Rope | In an ordinary wood, holding the key throws a rope within a second, and 15 s covers 190 blocks or more (today 103, with no rope at all). No hard hit on a rope anywhere. The city and the giant trees are no slower than today. | Ropes fire but you go no faster than running. | Flung into a wall, stopped, a rope that flickers, any move sent back. |
| Wings | A still from behind, from the front and in first person reads at once as wings, made of boxes, with layers. Each rung looks different at a glance. Opening is three beats. A beat goes down against the air. | Prettier paint on the same sheet. | Anything that is not boxes; a frame-rate cost he can see. |
| Powers | Every power that moves you moves you in the air. A cast shows in flight. You can aim away from where you fly. | Casts go off but the body ignores them. | A cast that closes the wings or drops you. |
| Moves | Each rung adds a move that changes where you can go or how you arrive. | A stat on a line of text. | A timing test laid over everything. |

## 2. What is there, and what was measured

Read: SPEC §16.3, §17 (all), §24, §28.5, §32, §50 on the sister branches; `body/ascent` (26,457 lines in `body`, of
which `AscentMoves` 1,768, `AscentSwing` 633, `WingLayer` 323); `powers/Casting`, `powers/client/ClientCasting`,
`powers/fx/Ways` on `claude/powers-next-plan-557510`. Seen: CI tour runs 42834736615 and 42840884113
(`tools/autopilot/movement-now.txt`). Measured: the game's own swing bench, compiled without Minecraft, with real-sized
woods added (`docs/movement/proof/run.sh`, results in `proof/results.txt`); `tools/art/specs/ding_check.py` on the 24
movement sounds. His play copy was read only (key binds, feel settings, the build he is on); nothing there was touched.

### 2.1 The map

| Part | What it does today | Evidence | Verdict |
|---|---|---|---|
| The swing's planner (`AscentSwing.find`, `plan`) | Picks an anchor along the heading and fits a rope that keeps the feet 2.5 over the ground. Needs the anchor 4 over the hand, a rope of 8 or more, a radius of 6 or more. | Code; the bench. | Keep. It is good where it works. It works only where things stand 10 or more over the ground. |
| The swing itself (`step`, `tick`) | A fitted pendulum in the heading's plane, bends round walls, lets go at the top. | Bench: lane 190, city 243, no hard hit on the rope. | Keep. Change when it lets go (2.2, finding 3). |
| The chain (key held) | Throws again 2 ticks after a let-go. Silent when nothing swings. | Code; tour trace. | Extend: when nothing swings, sling. |
| The zip to a lip, the ledge vault, the ring ladder | Pulls you up a face and over it. | SPEC §17.6, §17.9; tests. | Keep. Add the spring off a top. |
| The swing bench (`SwingBench`) | Six places, 40 runs each, fails on any hard hit on the rope. | Its "forest" is trees 13 to 21 tall. | Extend with real woods; keep its gates. |
| The server's checks | Anchor real and in reach, tier held; a refused rope becomes free flight. | `AscentServer.onState`. | Keep. Log why; stop the client throwing a refused rope again (finding 4). |
| The wings (`WingLayer`) | 14 flat strips a wing, one painted 256 by 128 picture, see-through, plus a glow pass. | Code; pictures. | Replace the geometry and the poses; keep the layer, the slots and the sync. |
| Glide and flight physics (`AscentRules.glideStep`, `soarStep`), Aether, the boom, the roll, windways, updraft vents | Momentum flight that goes where you look. | SPEC §17.1, §17.8, §17.12; tests. | Keep. Add the aim hold, the skim, the takeoff, the flare. |
| Powers in the air | Casting has no ground rule, but a state sets the body's motion from its own velocity every tick. | `AscentMoves.apply`, `ClientCasting.press`, `AscentServer`. | Add one contract (section 4.3). |
| Movement sounds | 24 files, all synthesised by `audio_ascent.py`. | `ding_check.py`: 8 of 24 ring. | Replace with scores of Minecraft's recordings. |
| Flow, chains, mastery, tips | Work; mastery pays small numbers. | SPEC §17.6. | Keep. Mastery also gives the new moves. |

### 2.2 Findings

The rope:

1. **An ordinary wood gives the rope nothing.** The planner's own numbers need an anchor 10.05 blocks over the ground
   (a radius of 6, the feet 2.5 clear, the hand 1.55 up). Minecraft's own tree data: an oak's trunk is 4 to 6, a birch's
   5 to 7, a spruce's 5 to 8, a dark oak's 6 to 9, each with a block or two of leaves over it. In the bench, in an oak
   wood and a spruce wood of those sizes, the assisted rope threw **0.0 ropes a run in 40 runs**, and the body covered
   103 blocks in 15 s, the same as on open plains. *Verified: code, data, bench.*
2. **The rope's own test never saw a real wood.** The bench's "forest", the one the 28 Oct gates passed in, is trees 13
   to 21 tall. *Verified.*
3. **Held, the rope hangs on too long.** It lets go at 95° past the bottom (`Tune.apex`), when the body is moving up
   and no longer along. The bench's published numbers (lane 190, city 243, forest 196) come from a bot that lets go by
   hand at a window quality over 0.85. A bot that only holds the key, which is how the rope is meant to be played and
   the only way his hands can play it (finding 6), covers **120, 166 and 124**. In the tour's street run the four
   let-goes at the top all had "window 0.00", and after them the body moved 5.5 blocks forward in 26 ticks and 10 in
   44: slower than a walk, for one to two seconds, before the next rope. *Verified: bench, trace of run 42840884113.*
4. **A landing ends the chain.** The held rope throws again only in the air (`AscentMoves.onInput`, state NONE: the
   chain needs `!onGround`). After a landing with the key still down, nothing is thrown until the key is let go and
   pressed again. In the tour's lane run the first rope hopped off after two ticks ("swing meets a wall: hop off it")
   and the runner then sprinted for seven seconds among giant trees with the key held. *Verified: code, tour.*
5. **It can still pull you into the thing.** A swing that meets a wall takes "a zip up it on a new rope". In the lane
   that was a 41-block zip to a crown's lip: a bonk of 1.16 blocks a tick, a stalled zip, a second zip, and the runner
   left standing on the crown. One of the tour's two lane runs. It is his complaint of 28 Oct. *Seen; one sample.*
6. **His rope key and his jump share one thumb.** The rope is on Left Option and jump on Space, so "Space jumps off
   the rope" (the launch, and the let-go the bench's numbers assume) cannot be pressed while the rope is held.
   *Verified from his options; the rest is reasoning about a hand.*
7. **In first person the rope's fist sits in the middle of the view,** over the place the swing is heading. *Seen.*
8. **The rope's four core sounds ring.** Throw, bite, let-go and snap each carry a held tone above 800 Hz at their first
   instant (1,766, 1,766, 991 and 1,981 Hz); so do the vow's line, "your body remembers" and two rappel sounds. All 24
   movement sounds are synthesised ("no samples", `audio_ascent.py`). *Measured.*

A robustness note, not a finding about play: when the server refuses a rope (it did 111 times in the first tour, whose
player was still held by the game's opening; never in the clean second tour), the client throws again at the same
block one to four ticks later for as long as the key is held. The reason for a refusal is logged nowhere.

The wings:

9. **The wing is one sheet.** One quad thick, no layers, a soft painting stretched along it, and it is not boxes.
   Seen from behind in a glide it is edge-on: a line. Closed, it is not drawn at all. Opening is one ease. *Verified:
   code, pictures.*
10. **A beat doesn't push down.** A flap turns the wing in its own plane, toward the head and back (`WingLayer.wing`
    turns the bones in x and y). Upright that reads as a beat; in flight, belly down, the wings scissor forward and
    never press on the air. *Verified in code; the page's model runs the same numbers.*
11. **Every rung wears the same wing.** The Sovereign's is the same sheet, gold. *Verified.*
12. **In first person there is almost nothing of them:** an arm, whatever is held, a corner of pale blue. *Seen.*

Powers and the air:

13. **A power's push is lost in the air.** While a state holds the body the client sets its motion from the state's own
    velocity each tick and the server zeroes its copy's. A Dash in a glide, Haymaker's and Spirit Palm's new air ways,
    Nova's lift, and the Levitation of the new Updraft power all ask the body to move, and it doesn't. *Verified by
    reading both sides; a GameTest is part of stage 0.*
14. **A blink closes the wings.** Any move of more than 3 blocks that the state did not ask for ends it.
15. **A cast's gesture is hidden in flight.** The flight clip plays over the cast's (layers 1600 and 1500).
16. **You fly where you look,** so you cannot look at a target beside or below you without turning toward it.
17. **On the rope the weapon is away** and nothing reaches an enemy (SPEC §24).
18. **From the ground, flight is four presses of jump** (jump, the Leaper's double jump, wings, flight). *Trace.*

### 2.3 Trials (isolated, nothing in the game changed)

`docs/movement/proof/run.sh`; every row is a bot that only holds the key, 40 runs of 15 s, median blocks.

| | Oak wood | Spruce wood | Giant trees | City | Hard hits on a rope |
|---|---|---|---|---|---|
| Today | 103, 0 ropes | 101, 0 ropes | 120 | 166 | 0 |
| 1. Let low anchors swing (shortest rope 4.5) | 76 | 91 | 94 | 142 | 0 |
| 2. A sling with a look-ahead | 203 | 198 | 189 | 224 | 0 |
| 3. Let go by itself at 75° / 62° / 55° / 50° / 40° | 103 | 101 | 149 / 194 / 216 / 250 / 301 | 213 / 251 / 258 / 265 / 280 | 0 |
| **The proposal: let go at 55°, with the sling** | **203** | **198** | **253** | **265** | **0** |

Trial 1 is the simple repair, and it makes things worse: a short pendulum adds no speed, and the planner starts taking
low anchors in tall places. Trial 2 is a rough first sling (under a hundred lines): where nothing swings, the rope
bites a top ahead and above, pulls toward a point over it and lets go before it. Trial 3 moves one number (`apex`). With the sling as well, 50° gave two hard hits on a rope in the city and 55° none,
so the plan starts at 55°. The proposal still leaves a hard
bump in free flight in a few runs (oak 1 in 40, spruce 5, lane 3); the build removes them and the gate is zero.

### 2.4 The tours

- Run 42834736615 (first try): the game's opening kept its camera and its hold on the player for the whole run, and a
  `fill` over 250 blocks said "not loaded". Its pictures are of a Carillon balcony and are not used.
- Run 42840884113: the sandbox's lane and street with the key held, the glide, the dive, flight, the Sovereign. The
  wing and rope pictures on the page are from it. No rope was refused.
- The forest run (`tools/autopilot/movement-wood.txt`): see the end of this file.

## 3. Approaches

- **A. Repair in place.** Lower the swing's limits, repaint the wing, pass pushes through. Trial 1 shows the rope part
  doesn't deliver, and a repainted sheet is still a sheet.
- **B. One line, real wings, one contract for the air (recommended).** The rope's planner gains a second answer (the
  sling) and a better let-go; the wings are rebuilt as feathers in boxes with real poses and a look for each rung;
  powers, blinks and aim get one contract with the Ascent's states; the sounds are remade from recordings; a few earned
  moves. Everything that works today stays and stays tested.
- **C. A free rope.** A rope that bites the sky, wall crawling, a physics web. It would undo decision 6 of 28 Oct ("no
  rope in open sky"), the anti-cheat's assumptions and the bench. Nothing measured asks for it.

## 4. The design

### 4.1 The rope: the line finds the way

- **One planner, three answers, in this order.** A swing where something stands tall enough (today's numbers, kept). A
  **sling** where it doesn't. A pull up and over a wall in the way (today's, with a limit: below). Held, the rope
  keeps going from one to the next. Tapped, it pulls you to what you look at (today's).
- **The sling.** Needs a top ahead and above the hand: a tree's crown, a roof's edge, a hill's crest, 6 to 30 blocks
  on. Never the ground you stand on and never open sky. The rope bites it, pulls toward a point 2.4 over it (0.13 a
  tick², up to 1.3 blocks a tick, mastery and Flow as for the pump) and lets go before it, rising enough to clear it.
  Each candidate is played forward with the body's box, pull and flight both, exactly as the swing's search does; the
  first clear one is taken. Coming down onto a crown after the point is a landing, and the run goes on.
- **Letting go while it still goes forward.** Held, the rope lets go at 55° past the bottom (`Tune.apex`, from 95)
  when the flight after it is clear, and later only when it isn't (`letGoClear`, as now). Your own let-go in the
  window keeps its snap (+14% and a lift): the hands that time it are still faster than the hands that hold.
- **The chain goes on through a landing.** With the key held, moving at a run and something to throw at, the feet's
  touch-down hops into the next rope (the hop a press from the ground already makes). Standing still, or with nothing
  ahead, a held key does nothing, as now.
- **A pull up a wall only when it is clear and near.** The zip a swing takes at a wall is swept like the sling's and
  limited to 16 blocks; otherwise the swing lets go early with its hop and looks again.
- **The spring.** Arriving on a top with forward held, the vault runs straight into a leap off it with most of the
  speed, and the next rope. Without forward you stand on it, as today.
- **Refused ropes.** The reason is logged. A rope the server refuses is not thrown at that block again for a second.
- **The marker** says which answer a press would give: a ring for a swing, a chevron for a sling, a bracket for a pull.
  The rope brightens through the let-go window and its hum rises with it, so the moment can be seen and heard.
- **First person.** The rope's fist moves to the frame's lower corner on its own side; the rope leaves it toward the
  anchor as now (`AscentFp.ROPE_X/Y/Z` and `fp_moves.py ROPE_FIST` move together).
- **The rope strike** (Tether mastery 5). Attacking on the rope or in free flight, with a creature under the crosshair
  or locked, more than 4 blocks off and in the rope's reach: the rope bites it and pulls you in feet first. The blow
  lands with your speed and you come off it upward with your air moves back. The pull is the client's (predicted, like
  every rope); whether it hit and for how much is the server's alone. The blow is a `PLAYER_ATTACK` and a
  `FightMoments.HIT`, so Brand and the Hoarfrost Lances answer it.
- **Keys.** Unchanged: the rope on its own key. Holding is enough. Space on the rope still jumps off for those whose
  hands reach.
- **The switch.** `ropeGuide` in `awaken-feel-client.toml` (on). Off: the rope exactly as §17.31 has it.
- **The bench.** New places: an oak and birch wood, a spruce wood, rolling hills, a street of stair and slab roofs.
  Every gate is run with a bot that **only holds the key**, beside the bot that lets go by hand. The gates of §17.31
  stay (no hard hit on a rope, no stopped run, no place shorter than before) and gain, for the holding bot: the oak
  wood at 190 blocks or more, the lane at 240, the city at 250, and no hard hit in free flight in any place.

### 4.2 The wings: feathers of light, in boxes

- **Geometry.** Three bones a wing and, hung from them, 22 flight feathers (9 primaries, 8 secondaries, 5 tertials)
  under two rows of coverts (24). Every feather is a slab one texel thick; its point is steps; a raised shaft down its
  middle. About 230 boxes a wing (1,400 quads; 2,800 for a pair, 8,300 for the Sovereign's six). One small feather atlas (pixel shading, a ramp per rung). Drawn by the same layer
  (`WingLayer`), the same slots, the same sync: no new renderer, no mixin, nothing per frame allocated.
- **Poses** (each a set of bone angles, a fan for the primaries and a flap about the body's long axis):
  folded on the back; opening (a 4-tick gather, an 8-tick spread that runs out from the body feather by feather, a
  6-tick settle); the glide (flat, the tips trembling with speed, swept back by it); the beat (down against the air,
  the hand lagging; back up with the wrist folded and each feather turned on its shaft to let the air through); the
  dive (tucked into a W); the hover (upright, slow whole beats); the flare (cupped forward to land).
- **Each rung has its own wings.** Wings: one pair, about six blocks across (today's is a little over four). Skyborn: a larger pair and a
  second, smaller pair under it. The Sovereign's form: six gold wings and a turning halo of boxes, for its 30 s.
- **Closed wings.** After a landing they fold onto the back, stay four seconds, and fade into the mark between the
  shoulder blades (a small dark plate with a lit core, worn from the day the wings are given).
- **First person.** The leading edge and the primaries at the frame's sides: on a beat the tips sweep through the top
  corners; in a dive the folded wrists close in; in a turn the low wing's feathers come into view.
- **Light.** Emissive, so Lens's bloom carries the glow; one soft halo quad per bone rather than a second copy of the
  wing. Square motes shed from the tips on a hard beat and on the flare (`awaken:ascent_mote`, already ours).
- **Far away.** Past 48 blocks every other feather and no coverts; past 96 the three bones and six broad slabs.
- **The switch.** `wingsLook` classic or new (new by default) for one release, then the old sheet is deleted.

### 4.3 The air: one contract for powers

- **A push carries.** Anything that sets the body's motion from outside while a state holds it (a predicted cast, a
  server impulse, a blast) is read as a push and goes into the state's own velocity: added in free flight, on wings
  and in flight; on a rope, its part along the swing. One place: the Ascent compares the motion it left with the motion
  it finds. `core.api.MovementHooks.push` for callers that want to say so plainly.
- **A blink keeps your state.** A teleport from a cast the client just made (within 20 ticks, up to 16 blocks) moves
  the state with the body; anything else still ends it.
- **A cast shows.** While a state is airborne the cast layer's arms play over the flight clip. Third person gets five
  shared flight gestures (a thrust, a sweep, a stamp down, a lift, a throw); first person uses the powers session's
  `__air` clips.
- **The aim hold.** On wings and in flight, while the guard (the use key) or a held power's key is down the flight
  keeps its line and level and the look is free; a cast or a shot goes where you look. Let go and the flight follows the look again over 6 ticks. A hover already aims freely.
- **Updraft** (the powers session's new power) registers its column with `AirHooks`, so open wings ride it; its ladder
  step that throws riders along their look starts a glide.
- **A weapon in flight.** An attack on wings or in flight brings the arms in for the swing and back out; the Ascent's
  stances stand aside for it.
- **One side decides.** The caster's hands predict a cast in the air exactly as on the ground. The server accepts or
  refuses the cast as it does today and never changes what the hands are showing.

### 4.4 Moves worth earning

| Move | Rung | How | What it gives |
|---|---|---|---|
| The sling | Tether, from the first rope | Hold the rope where nothing stands tall | The rope everywhere there is something above you |
| The spring | Tether mastery 3 | Hold forward as a pull arrives on a top | Rooftops and crowns without stopping |
| The rope strike | Tether mastery 5 | Attack with a creature in reach | A fight you can enter from the air |
| The flare | Wings, from the first glide | Come down slow and level | A landing that is a moment: the wings cup, the feet touch, feathers of light fall |
| The skim | Wings mastery 3 | Fly within 2.5 blocks of ground, water or a wall at 1.0 b/t or more | The glide's drag is halved there (never removed: level flight must still end), a wake follows you, Flow rises |
| The takeoff | Skyborn, from the first flight | Look up (20° or more), sneak for 8 ticks, then jump | Straight up 14 blocks into flight, for a tenth of the Aether. Today the ground to flight is four presses of jump (jump, the Leaper's double jump, wings, flight). Holding jump can't be the trigger: vanilla hops on the press and again on every landing |
| Six wings | Sovereign | The form | Six gold wings and a halo for the form's 30 s; out of the form a Sovereign flies on Skyborn's two pairs |

No move asks for timing except the rope's own let-go, which was always about timing.

### 4.5 Sound

Every movement sound is rebuilt as a score of Minecraft's own recordings named in `sounds.json` (no Mojang audio in the
repo), layered in code, with the powers session's tool (`tools/art/specs/power_voices.py`). First picks, each with two
alternates on a local listening page that is never hosted: the throw from a fishing bobber's cast and a crossbow's
shot; the bite from a trident in the ground and a chain set down; the strain from a crossbow being drawn; the let-go
from a lead coming untied; the wings opening from an elytra put on and a dragon's wingbeat; the beat from a phantom's
and a dragon's; the wind from the elytra's own loop; the boom from the Warden's and thunder. A client setting, Classic
or New, as the powers have.

## 5. Failure and edge cases

| Case | What happens | Test |
|---|---|---|
| Nothing above and ahead | A press is the miss it always was; held, silence | bench "plains" |
| The sling's path is blocked | Not chosen; the next candidate; else nothing | bench gates |
| The top is gone when the rope arrives | The server refuses; free flight; not thrown again at it for a second | GameTest |
| A sling over water | The Ascent stands aside in water as today; the look-ahead treats water as a floor | GameTest |
| A rope strike's target dies or moves out of reach mid-pull | The pull ends as a let-go; no blow | GameTest |
| Two pushes in one tick; a push on a rope | Summed; on a rope only the part along the swing | GameTest |
| A blink into a wall | The state ends as today (a teleport the client didn't ask for) | GameTest |
| Aim hold near terrain | The edge hold (§28.9) and the flight's own collision are unchanged; the hold ends on any hit | GameTest |
| 32 players with wings in view | The far forms; the slots already cap at 32 | look tour, count the quads |
| Reduced motion | No motes, no tremble, half the beat's swing | setting |
| A server without the new client | The server's checks are unchanged; an old client simply has no sling | by design |

## 6. Proof before it reaches him

- GameTests, one per mechanism: the sling's choice and let-go; the window let-go; a refused rope is not rethrown; a
  push in each state; a blink keeps the state; the aim hold; the takeoff's cost; the skim's rule; the rope strike is the
  server's; the wing's box count by distance (a pure function).
- The bench with real woods, at the gates above.
- The suite on CI, once. One look tour: the wood, the city, the wings from three sides at each rung.
- Then his hands: the rope in a wood and in Carillon, the wings, a fight from the air. `/awaken rope tune on` already
  takes numbers live; the wings get the same (`/awaken wings tune on`).

## 7. Stages and cost

From this repo's history: the assisted swing (planner, bench, feel, live tuning) was one working day on 28 Oct; the
whole of the Ascent's first two phases was 42 commits on 25 Oct; the powers session built 13,000 lines between about
16:00 and 21:00 today.

| Stage | What | Hours of building |
|---|---|---|
| 0 | Truth: refusal reasons, real woods and the holding bot in the bench, the test that shows a push is lost | 1 to 2 |
| 1 | The rope: sling, let-go, the chain through a landing, the spring, the wall pull's limit, the marker, the fist | 4 to 6 |
| 2 | The wings: feathers, poses, three rungs, first person, far forms | 5 to 7 |
| 3 | The air: pushes, blinks, casts shown, aim hold, Updraft, weapons | 4 to 5 |
| 4 | Moves: flare, skim, takeoff, rope strike | 4 to 5 |
| 5 | Sound: 24 scores and the new moves', the listening page | 2 to 3 |
| 6 | Tips, Tasks steps, SPEC, the one test pass | 2 to 3 |

22 to 31 hours of building, one session, coded whole and tested once at the end (his rule of 10 Nov). What could move
it: the wings' poses after his first look; the rope's numbers after his first ten minutes.

Rollback: each part has its switch (`ropeGuide`, `wingsLook`, the sounds' Classic), and the air contract is additive.

## 8. Meeting the sister sessions

- **Powers, the next level** (`claude/powers-next-plan-557510`): their Ways already name the air. This plan makes their
  air ways move the body in a state, asks Updraft to register with `AirHooks`, and supplies third-person flight
  gestures (their third person has no ways yet). Their first-person arm and `ArmLayers` are untouched.
- **Weight in the Hand** (merged): an attack in flight needs their swing to play while the Ascent's stance stands aside;
  no change to their clips.
- **Armour Finish**: the mark between the shoulder blades sits over the chest plate's back; a note asks them for the
  back's depth per set.
- **Steady Light**: the wings are emissive boxes in the entity pass, nothing in Lens; the flight tour's frame numbers
  are checked against theirs.

## 9. What Euan decides

On the page, one recommendation each: the rope's rule; the let-go and the landing; the keys; the wings' look; a look for each rung;
closed wings; powers in the air; the aim hold; the moves; the sounds; what waits; the order of work.
