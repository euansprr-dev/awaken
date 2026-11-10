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
