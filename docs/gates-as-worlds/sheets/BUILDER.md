# Builders: how this works (read once, then your sheet)

Euan, 10 Nov 2026: one session does the thinking and writes an exact sheet for each piece of work; three builders code from the
sheets side by side; a check that answers in seconds says whether it's right. You are one of the builders. The orchestrator is the
gates session on branch `claude/gates-as-worlds-plan-fcb81b`. This is a test of that way of working, so two numbers matter as much
as the code: **how many rounds you needed** (a round = you ran the check, it failed, you changed code) and **how long it took**.

## Your job

1. Read the repo's `CLAUDE.md` and your sheet. Don't read all of `docs/SPEC.md`; open only what your sheet points to.
2. Build exactly what the sheet says, in the files it gives you. Small choices inside the sheet's intent are yours: write them down.
3. Run the check the sheet names until it's green.
4. Commit on your own branch and hand back (format below).

If the sheet is wrong, contradicts the code, or leaves out something you can't settle from the code, **say so in the hand-back and
build the nearest thing that keeps the sheet's intent**. Don't redesign. Don't judge looks: the orchestrator does that.

## Rules that don't bend

- **Files.** Touch only the files your sheet lists as yours. If something else needs changing, describe the change in your hand-back.
  Never edit `src/main/java/dev/awaken/gates/realms/land/AmberReach.java`.
- **Amber's blocks must not move.** If you touch anything under `realms/kit`, `realms/check`, or how `Preview` paints, run
  `tools/preview/preview.sh tools/autopilot/realm-amber.txt 1,2,3` and check `run-preview/realm-amber/volume.sha` still starts
  `1 50cc4328`, `2 8947ec1e`, `3 bc407d30`.
- **Gradle only through the slot wrapper**, one run at a time:
  `/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 560 runGameTestServer -Ptests=<prefix>`.
  The Mac has 16 GB and three sessions share it. Run only your own tests. Don't re-run a check that passed.
- **Never start the game client** (`runClient`), and **never run a tour** (`tools/autopilot.sh`, `tools/tour-ci.sh`). The orchestrator
  runs real-game walks on GitHub.
- **APIs.** This is Minecraft 26.3 on NeoForge 26.3.0.95, not 1.21. Check `docs/api/` and grep `/Volumes/MEDIA/Awaken/.ref/src`
  (Minecraft + NeoForge), `/Volumes/MEDIA/Awaken/.ref/neo`, `/Volumes/MEDIA/Awaken/.ref/assets` before you use a signature.
- **No sub-agents and no other sessions.** You do the work yourself.
- **Stall guard.** If a tool call comes back with "PreToolUse hook did not respond", or a step takes about ten minutes for no reason,
  stop at once and say so in a hand-back. Don't wait and retry. A stall like that burned half of the last builders' usage.
- **Usage.** Keep your context small: don't print whole big files, read the parts you need. No side refactors.
- **Shell.** Start every Bash command with `cd <your worktree> &&`. Use absolute paths in file tools.
- **Commits.** Small, on your own branch, `git add` by path (never `-A`), each message ending with the co-author line in `CLAUDE.md`.
  Don't push. Never touch `build/awaken-v1`.
- Player-facing text is plain natural speech. New blocks and items need a recipe, loot or `Sources.declare` (test `journey_ledger`).
  The repo is public: no Mojang or Fresh Animations files.

## Hand-back (your last message; the first 400 characters are what the orchestrator sees first)

```
DONE|PARTIAL|BLOCKED <lane or gate>  branch <name> at <sha>  rounds <n>  minutes <n>
Works: <what, with measured numbers>
Not done / not sure: <plainly>
Choices I made: <short list>
Needs changing outside my files: <or "nothing">
```

## The kit, for gate builders (all merged on the gates branch, 10 Nov)

**The bench is your check.** `tools/bench/bench.sh check tools/autopilot/realm-<gate>.txt` draws the gate, looks for anything
floating, walks from the Mouth to every site and writes the picture sheet, in 3 to 5 seconds once it is up (the first call starts
it: one Gradle build, about a minute). It exits 0 only when floats are 0 and every site is reached. The report is
`run-bench/out/<name>/bench.txt`, beside `sheet.png`. `tools/bench/bench.sh shot <blueprint id> <x> <y> <z> <yaw> <pitch> [name]` is
one picture from one eye in half a second.
- Seen without a restart: anything under `realms/land/` (a new class there too), the tour file, the blueprint and biome JSON, textures.
- Needs `tools/bench/bench.sh restart`: a new block, the kit, anything outside `realms/land/`, and a blockout's JSON.
- One checker per worktree, about 2 GB each on a 16 GB Mac three builders share. Run `tools/bench/bench.sh down` before you hand back.
- Run it after every pass of your sheet, not once at the end. A round is a `check` that failed and made you change code. Count them.

**The report's light numbers are the game's own light.** Per shot: `lum` (middle brightness, 0 to 255), `dark` (percent under 20),
`bright` (percent over 200), and a `BLACK` flag. Indoors aim for `lum` 40 to 90 with `bright` at least 1. `tools/art/tone_check.py
<prefix>` must pass for any texture family you add or change: family middle 95 to 150, nothing structural under 60. Darkness comes
from light, never from a dark texture.

**A new gate is a `SheetLand` on a blockout.** Read `realms/kit/SheetLand.java`, `realms/blockout/Blockout.java` (`mass(name)`, and a
mass's `contains`, `top`, `edge`) and `realms/blockout/BlockoutLand.java` before you start. `SheetLand.paint` runs your `draw` and then
`Settle`, which removes small strays and counts them: the bench prints `settled: ...`. Treat every line of that as something to fix
at its cause, and say what was left in your hand-back. Ground marked `rough` moves from seed to seed: foot everything that stands
on it with `V.foot`, or take its height from `ground(x, z)`.

**Tours go straight in.** `cmd awaken gate realm_enter E awaken:<id>`, then `need realm`, then `hud off`, your shots, and `quit` last
(copy `tools/autopilot/realm-hour.txt`). You never run a tour; the orchestrator does, on GitHub.

**Shared one-liners** (`LandPrograms`, `RealmLive`, `RealmState`, `ThemeBlocks`, `data/awaken/dimension/realms.json`,
`looks_gates.json`) are allowed where your sheet says so. Keep each to the fewest lines; three builders touch the same files and the
orchestrator merges them. Lang and tags go in your own folder under `src/main/merge/`.

**Extra lines for a gate's hand-back:** rounds on the bench; minutes; what `settled:` still lists; the `lum`, `dark` and `bright` of
your painting shot; every place you departed from the sheet and why.
