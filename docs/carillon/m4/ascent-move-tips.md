# Move tips: triggers and controls (from the Ascent session, 25 Oct 2026)

These are for Carillon's shared tips system (one UI, owned by Carillon). Keys are the defaults; show the player's real
bindings (vanilla `options.keyJump/keySprint/keyShift/keyLeft/keyRight/keyUp`, and Awaken's `AwakenKeys.MOVEMENT`,
which defaults to Left Alt). The one-button free-run (the feel pass) will change several of these. I'll send new text
when it lands.

## The Runner's Kit (body module; everyone has it from the start, ranks ignored since spike S3)
Text: `Move.how` in `src/main/java/dev/awaken/body/Move.java`. Numbers: `RunnerRules` and `BodyRules`.

| move | when to show the tip (context) | controls now |
|---|---|---|
| ROLL | the first landing from a fall that would hurt (4+ blocks) | land holding sprint or sneak |
| TACTICAL_SPRINT | 3 s of sprinting on open ground | double-tap sprint: 3 s of harder running |
| SLIDE | sprinting on flat ground for 2 s | sneak while sprinting; jump out to keep the speed |
| MANTLE | in front of a ledge 1.1–1.6 above the feet, headroom 2 (`BodyRules.MANTLE_*`) | jump at it |
| WALL_RUN | sprinting within reach of a wall at under 35°, 1.5+ above the ground (`RUN_MIN_HEIGHT`, `RUN_MAX_ANGLE`) | sprint, jump, hold jump along the wall |
| WALL_JUMP | during the first wall-run | jump to kick off; land on another wall to run again |
| LEDGE_HANG | a ledge 1.2–2.4 over the head in front (`HANG_LO/HI`), or sneaking toward a drop of 1.55+ (`HANG_DROP`) | jump at it, or sneak forward off the edge |
| SHIMMY | the first hang | left or right along the edge |
| LEDGE_JUMP | the first hang lasting 1 s | jump: up (2.5), sideways with left/right (3.0), back with back |
| WALL_CLIMB | Wall Stride held, sprinting straight at a wall 3+ high | jump at it holding forward; jump again to kick off |

Context detectors already in code: `dev.awaken.body.client.RunnerMoves.wallBeside(p)` and `RunnerMoves.busy()`,
`BodyRules` mantle and hang probes (`RunnerRules.ProbeWindow`).

## The Runner envelope (for the city's look bible)
- Wall-run: 35 ticks at 0.30–0.42 b/t, about 10–15 blocks along a wall (Wall Stride: 80 ticks, about 25–30). Up to 4 links
  wall to wall, each keeping 0.8 of the speed.
- Mantle: a top 1.1–1.6 above the feet, 2 of headroom. Hang: an edge 1.2–2.4 over the head; a ledge jump reaches 2.5
  up or 3.0 sideways.
- Jump gap: vanilla sprint jump, about 4 blocks (tactical sprint about 5). With the Leaper add a double jump and an air dash.
- Rope (Tether): a zip goes up to any anchor or edge in reach overhead; a swing hangs from a ring or an edge 2+ above.

## The Ascent tiers (mine; I'll call the tips API at each moment)
| tier | moment | tip |
|---|---|---|
| Tether (the rope) | the Tether Vow's end (`TetherVow`) | hold Alt looking at a ring or an edge above: the rope bites. Hold to swing, W to pump, A/D to steer, let go of Alt to fly free, jump on the rope to launch. Straight overhead it zips you up and over the lip |
| Leaper | the Leaper's Gauntlet's far landing (`AscentTrials.leaperDone`) | in the air: jump to double jump, sprint to dash, sneak high up to dive slam |
| Wings | the sky platform's core (`WingCoreBlock`) | jump while falling to open the wings; look to steer, dive for speed, pull up to climb; sneak to fold. Windways between platforms carry a glide |
| Flight (Skyborn) | Skyborn granted | jump while gliding to fly: W thrusts, sprint boosts, jump and sneak climb and sink, double-tap A or D to barrel roll |
