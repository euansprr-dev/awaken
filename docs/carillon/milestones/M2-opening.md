# Milestone 2 · The opening in blockout (Director)

Branch `carillon/m2-opening` (from carillon/main a670f7e), worktree /Volumes/MEDIA/Awaken-opening. Brief:
docs/carillon/briefs/M2-opening.md. Date 2026-10-25, Apple M4, the shared tour lock used by other sessions all along.

## What's built

The first eight minutes of a new Carillon world, as one module (`dev.awaken.opening`, ~5,900 lines) that the story
hands the player to at the start and takes back at the Void:

- **Spine.** `StoryProgress.Prologue` appends `SKY, RUN, ARRIVAL, WAKE, REGISTRY` (schema 3; old saves read the same).
  `OpeningState` (player attachment `awaken:opening`, schema 1) and `OpeningWorld` (SavedData, schema 1). The server
  owns the timeline (`OpeningDirector`); the client plays each beat from the age it's sent and reports only what it
  alone sees end (`Ready`: sky landed or skipped, wake done, a Registry screen closed). A relog resumes every beat
  where it was. `OpeningHooks` installed. No death in the opening (damage clamps at 2 HP from SKY to REGISTRY).
- **The sky shot.** One `OpeningCamera` (a client-only marker entity, per-frame placement, a single
  `finish(reason)`), the black card (CARILLON, an `[ERROR]` glitch, three card bells) during which the city pre-warms,
  then a 19 s designed flight over the north-west half looking south-east across the spire (path checked by
  `tools/opening/skypath.py`), Lens `awaken:carillon_dawn`, wind, district bells, a feathered letterbox with a bronze
  hairline and a place caption, landing in your eyes on the Roost. Hold jump 1 s to skip, after your first viewing.
- **Into your eyes.** The first-run question (in your eyes / over your shoulder, keys 1/2, Esc = first); the View key
  flips first and third person and skips the front view (setting `viewKeySkipsFront`).
- **The bell rings wrong.** Dawn set once for the world. The great bell rings out of time, the light falls into
  Choir Square (a column you can see from anywhere: an unfogged translucent body with an additive core), a Signal line,
  no waypoint. Citizens stop and point at it.
- **The run.** Three acts by district (Bellwatch, the Quay, the market), three routes each (rooftop, canal, street)
  generated on M1's painted city by `tools/opening/routes.py` (a voxel model of the city, an A* runner with the rank-E
  moves, a builder that lays planks where the roofs need them, then a re-plan on the city as built). 414 stage blocks
  (the Roost's slide, the couriers' stair, plank bridges on posts, a crane's arm with a trolley you hang past, ladders
  for the canal crossings, bronze marks where you hang). The street is at most 7.6 s down from any point of a high
  route. Act 2: the Bell Bridge goes into the river as you come within 22 of it (across the ground) (topping the last gable on the roofs, along
  the south quay on the canal route; street runners hear it go as they reach the market); its street edges close.
  Act 3: the market runs toward you while the Watch closes the square (CitizenHooks events). Tremors on a clock.
  **The Runner's eye**: after 6 s of hesitating at an edge the next hold pulses bronze (waits longer each time you're
  shown; setting `runnersEye` to turn it off); on the second hesitation the move's key glyph, once per move.
- **Arrival.** Alone: a 12 s cinematic (crane up to frame the gate under the spire, three tolls, the light throws the
  citizens back, the swing to your shoulder, the pull, the tear to black) through `GateHooks.openScripted` in the
  square (M1's interim lines in Prologue removed). A party: wait and count in the square, the cinematic 3 s after the
  last; stragglers pulled by the light 5 min after the first arrival or after 2 min idle.
- **The Ritual and the Void** are the story's, unchanged. Accepting in the opening sends you to the infirmary instead
  of the classic tear.
- **The infirmary.** Eyelids, Lens blur to focus (`opening_blur_1..3`, `awaken:carillon_wake`), Sun and an
  Association officer at your bed (citizens), their lines; party beds side by side.
- **The Registry (stub).** The walk through the atrium, the Assessment Lens reads you, the Status reveal (Holo's
  Status screen), the E-rank Hunter's Licence (a named item), the Holo wristband, the first power (the Starter
  offer waits for here), the first Directive, "The city is yours."
- **Skip.** The world option (game rule `awaken:skip_opening`, Create World > Game Rules), hold-to-skip on the sky
  shot, `/awaken opening skip [player]`. Also `/awaken opening status|restart|beat <...>|reset|drive <route> [rec]`.
- **GameTests** (`-Ptests=opening_`, 4, all pass locally): the state machine with a relog at every beat, the party
  wait and the straggler pull (fake players), no death in the opening, every way to skip.
- **Golden runs.** All nine recorded by the route driver (`/awaken opening drive <route> rec`, the client's own keys
  through the Ascent's recorder) into `tools/opening/golden/`.

## CI

Full GameTest suite on GitHub: **green on 7f9d206** (run 40146155107, 10 min). The push before it (f87a6a5, run
40145129249) failed one test outside this milestone, `fauna_natural_spawning_end_to_end` ("under a roof" on tick 10;
other branches show the same kind of fauna/terrain flake today); it passed on the next push with no fauna change.
Locally: `-Ptests=opening_` 4/4. FINAL_CI

## Tour

`tools/autopilot/carillon-m2.txt` (one `slot.sh light` tour, about 7 minutes of game after world creation): a new world, the opening played through
from the card, the rooftop acts played from their golden runs inside the opening, then the street and canal golden
runs. Full-size shots (gitignored): /Volumes/MEDIA/Awaken-opening/run-autopilot/screenshots/m2_*.png. Curated copies
(800 px) in docs/carillon/milestones/m2/:

- sky: m2_sky_a_top, _b_river, _c_market, _d_choir, _e_handoff (the four flight frames and the hand-off), _g_question,
  _f_eyes; the bell: m2_bell_a, _b, m2_light_roost
- the run: m2_act1_a, _b; m2_act2_a, m2_act2_bridge, m2_act2_crane; m2_act3_rush, m2_act3_square
- arrival: m2_arrival_a.._d, m2_hall; the Void: m2_void
- infirmary: m2_wake_a.._d, m2_infirmary; Registry: m2_registry_lens, _licence, _wristband, _status, _power, _yours

## Golden runs (played back blind, "off by d")

Recorded by the route driver (`drive <route> rec`); played back blind by the Ascent's pilot (`route play`) in the
milestone tour, from each recording's start. Rooftop acts play inside the opening; streets and canals after it.

| route | ticks | tour 1 off by | tour 2 off by |
|---|---|---|---|
| act1_roof | 536 | 0.81 | 0.81 |
| act2_roof (crane hang, shimmy, climb-out) | 449 | 0.00 | 0.00 |
| act3_roof | 277 | 6.66 | 6.25 |
| act1_street | 821 | 1.14 | 84.68 |
| act2_street | 305 | 0.31 | 0.39 |
| act3_street | 262 | 0.53 | 0.19 |
| act1_canal (stair, streets, ladder, lower quay) | 822 | 24.48 | 63.40 |
| act2_canal (two swims, three ladders) | 390 | 15.89 | 8.39 |
| act3_canal | 141 | 11.35 | 0.04 |

Read: the rooftop line plays back exactly through Act 2. act3_roof's 6 is the arrival taking over: the cinematic
starts as you come down into the square, before the recording's end. The streets and canals are not stable from
tour to tour (tour 1 also played them hungry; tour 2 fed them as recorded and act3_canal went from 11.4 to 0.04 while
act1_street went from 1.1 to 84.7). The city's people are pushable bodies (`CitizenBody.isPushable`) and the lower
quay and the couriers' stair are three blocks wide: a blind playback nudged once misses its next ladder or turn. The
Ascent's tactical sprint, ended by the server, is the other suspect. Proposal: playbacks that are checks run with
the bodies passing through the runner (or none on the route), or compare against the driver itself, not a recording.

## Is it thrilling? Honestly

THRILL

## What Euan should try

TRY

## Decisions taken (SPEC §16.4 proposals for the Carillon session)

- **States**: the five beats are appended to `Prologue`; in a Carillon world the opening owns NOT_STARTED/GLITCHED
  (the classic prologue's ticks stand aside while `CityHooks.startPoint` exists). Classic worlds are untouched.
- **Skip** is a game rule (so it's a world option on Create World and changeable with `/gamerule`).
- **View key**: F5 flips first and third person and skips the front view (a setting, on by default).
- **Dawn**: the first opening in a world sets the time to 23200 once (the light reads against a dawn sky).
- **Stage**: the run's furniture and the Association's ground floor (infirmary, atrium, Registry) are placed once per
  world (`OpeningStage` v1) into the standing city; M4's kit replaces them.
- **Routes** live in `data/awaken/opening/routes.json` (generated, pinned to M1's plan by sha256). The Bell Bridge
  falls within 22 blocks across the ground (or at the market); the arrival counts from 2 inside the square's edge.
- **Citizens**: new event kinds `rush` and `infirmary` and roles FLEE and ATTEND (CityEvents posts `bell_wrong`,
  `gate_rush`, `gate_burst`, `bell_bridge_down`, `infirmary_wake/done`, `tremor`).
- **Deferrals while in the opening**: the Starter offer (waits for the Registry), the fauna reveal, the Now line.
- **Music**: the run uses the Bell Crypt's layered score (tension, then combat from Act 2; the arrival's cinematic its
  lord layer). The opening wants its own score.
- **Atlas reveal** at the hand-off is left to M5.
- **Proposed API**: `GateHooks.enter(player, gateId)` (the opening calls the gates module's registry and `Runs.enter`
  directly to pull a straggler in).

Cross-module edits (all for the hand-over): story (`StoryProgress`, `Story` API additions, `Prologue` hand-off and
the Void's answer, `RitualClient.handOff`), powers (`Starter` waits), world/fauna (the reveal waits), citizens
(`CityEvents`, `Roles`), `Awaken`/`AwakenClient` (module init).

## Gaps

GAPS
