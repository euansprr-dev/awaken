# Spike S3 · Wall-run, ledge hang and ledge jump on the body framework

Worktree `/Volumes/MEDIA/Awaken-c-runner`, branch `carillon/spike-runner` (throwaway; the real one is workstream W2,
which starts after gate G0. Euan's movement decision is already in SPEC §16.0.1 and §16.3: everyone is a Runner from
the first second, ranks improve moves, Wall Run becomes Wall Stride).
Read first: docs/carillon/briefs/RULES.md, docs/SPEC.md §16.3 (the numbers to use), docs/modules/body.md,
src/main/java/dev/awaken/body/ (Move, BodyRules, BodyServer, client/ClientMoves, net/BodyPayloads, client/TryDriver).

## The questions
1. Do wall-run (with the sink), wall jump, ledge hang, shimmy and ledge jump fit the body's pattern (client predicts in
   `MovementInputUpdateEvent`, one `Start` payload, the server re-checks geometry and refuses or corrects)?
2. How do they hold up with latency? (scripted moves at 0, 100 and 150 ms added on the server side: count refusals and
   corrections out of 30 of each move)
3. Do they trigger by accident? (sprinting down a 2-wide alley and along a wall with a low fence: count unwanted
   wall-runs and grabs out of 20 passes)
4. Does hanging trip the server's floating kick? (it shouldn't: `ServerGamePacketListenerImpl.noBlocksAround` treats
   anyone within 1/16 of a block as supported; confirm in .ref and in a test that hangs for 200+ ticks)

## Build
- In the spike, all moves on for everyone (ignore ranks). Append the new moves at the end of `Move`.
- Wall-run, wall jump, ledge hang (an airborne, hand-height variant of `findLedge`: ledge top 1.2–2.4 above the feet),
  shimmy, ledge jump (up / sideways / back), jump help (coyote time, early-press buffer, edge snap), with exactly the
  §16.3 numbers. Sprint, jump and sneak only. A ledge hang holds while a screen is open; a wall-run ends.
- Server checks in `BodyServer` like the existing `startX` methods, refusals sent with `Refuse`.
- A debug latency knob for the spike (e.g. `/awaken body latency <ms>` delaying the server's handling of `Start`).
- Batched ledge probes; say what they cost per tick.
- A test course built by command (`/awaken body course`): two parallel walls for wall-to-wall, a warehouse face with a
  crane-arm ledge, a 2-wide alley, a low fence. `TryDriver` scripts to run each move; GameTests where the framework
  allows (server-side checks, refusals, no float kick).
- An autopilot script `tools/autopilot/carillon-s3.txt` with frame sequences of each move in third person (several shots
  per move), for feel.

## Hand back (docs/carillon/spikes/S3-runner.md)
- Each move: works / works with caveats / doesn't, with the reason.
- Latency table, accidental-trigger counts, the float-kick finding, probe cost.
- Shot sequences and your honest read of the feel. Which poses need Player Animation Library clips (W2 will add them via
  tools/art/specs/body_moves.py).
- Note: another session owns `body` today. This spike changes it only on its own branch; don't touch other worktrees.
