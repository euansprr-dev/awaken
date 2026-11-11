# Builders: read this once, then your brief

You are one of several builder sessions finishing AWAKEN's gates. The gates session (branch `claude/gates-as-worlds-plan-fcb81b`)
wrote your brief, will look at your pictures and will merge your branch. `README.md` in this folder says what the whole job is.

## Read, in this order

1. The repo's `CLAUDE.md` (build, API rules for Minecraft 26.3, module rules, commit trailer).
2. This page.
3. Your brief, and only the files it points to. Do not read all of `docs/SPEC.md`: open the sections your brief names.
4. `docs/gates-as-worlds/BAR.md` if your brief builds or changes a place.

## What Euan wants, in his words (these decide every small choice)

- "I don't want a maze. I want like immersive worlds with their own like style."
- "As long as there's a ton of different variations, so I never feel like I'm repeating the same gates. And then the immersion is
  truly there, so each gate feels like its own atmosphere... a very well-built dungeon that has atmosphere, that goes up, that goes
  down, that has good fighting."
- On the rolled kinds: "they all look very similar... add more variation in terms of, like, the different parkour things."
- On depth: "completely flat floor... completely flat walls... it's still all one block distances. There's no half blocks, there's
  no grass, there's no mushrooms." "It should never be vanilla blocky."
- "There's some stuff floating. We need to make sure that never happens."
- "Focus on the actual coding... trust yourself more... stop relying on the tests as a way to catch mistakes, and start thinking of
  everything beforehand."
- 11 Nov: "all ranks have all their dungeons and everything is actually... nothing broken... fully featured."

## Rules that don't bend

- **Euan is playing on this Mac.** Until your brief or the gates session says otherwise: no local Gradle, no local Java, no bench on
  the Mac, no game client, ever. Check on GitHub (below). Never touch `/Volumes/MEDIA/Awaken-play`.
- **Your own branch and worktree.** Start every Bash command with `cd <your worktree> &&`; absolute paths in file tools. Never call
  EnterWorktree. Push your branch to `euansprr-dev/awaken` as often as you like; never force-push; never push or merge
  `build/awaken-v1`; never merge another builder's branch.
- **Files.** Touch what your brief lists as yours. Shared one-line files only where your brief says, fewest lines. If something
  else needs changing, say so in the hand-back. Never edit `realms/land/AmberReach.java`, and do not change
  how the kit paints what it uses: Amber Reach's golden hashes must not change. They are the preview's (`tools/preview/preview.sh`,
  `volume.sha`: seed 1 `50cc4328`, 2 `8947ec1e`, 3 `bc407d30`), not the bench's `volume:` line, and Amber Reach is too big for the
  bench on GitHub (its scan runs out of memory there and then reports sites it could not walk to). Do not bench it on GitHub;
  the suite's own `realm_amber_*` tests guard it when the manager runs them.
- **APIs.** Minecraft 26.3 on NeoForge 26.3.0.95 is not 1.21. Before any Minecraft or NeoForge call you have not just seen used in
  this repo, check `docs/api/` and grep `/Volumes/MEDIA/Awaken/.ref/src`, `.ref/neo`, `.ref/assets`. Never guess a signature.
- **No sub-agents, no other sessions.** You do the work yourself.
- **No test suites.** Never dispatch the GameTests workflow and never wait on one. One test per new mechanism, written by you, run
  by name through the check below. The manager session runs the one full suite after everything merges.
- **GitHub's API** is shared by every session: one list call per look, no faster than every five minutes while you wait. If it says
  you are limited, wait (`gh api rate_limit`).
- **Commits.** Small, `git add` by path (never `-A`), never bare `git stash`. End each message with the trailer in `CLAUDE.md`.
- **Content rules.** Player-facing text is plain natural speech. Never announce a boss ahead. Sounds are Minecraft's own recordings
  by name in `sounds.json`, never synthesised. No Mojang or Fresh Animations files (the repo is public). Every new block and item
  needs a recipe, loot or `Sources.declare` (test `journey_ledger`), and every solid block of ours goes in
  `src/main/merge/gates/data/minecraft/tags/block/blocks_motion_no_leaves.json` (test `core_blocks_motion`).
- **SPEC.** What you decide that the spec does not say goes into `docs/SPEC.md` §50 in the same commit, in the section your brief names.
- **`"ready": true`** in a blueprint lets gates in the world lead there. You never set it. The gates session does, after looking.
- **Stall guard.** A tool call that hangs ten minutes, or "PreToolUse hook did not respond": stop and say so. Do not wait and retry.

## Checking without the Mac

Brief 00 builds this; until it has merged, only its own builder uses GitHub this way.

- `tools/check-ci.sh compile` compiles your pushed branch on GitHub's Linux machines (about four minutes).
- `tools/check-ci.sh bench <tour.txt or blueprint id> [seed ...]` runs the bench there: draws the gate, counts anything floating,
  walks from the way in to every site and leg, and brings back `bench.txt`, the picture sheet and the shots into
  `run-bench/ci/<name>-<run id>/`. A gate is sound only when `floats: 0` and `reach: all N sites`.
- `tools/check-ci.sh tests <prefix[,prefix]>` runs only the named GameTests (your new mechanism's own test).
- `tools/tour-ci.sh tools/autopilot/<tour>.txt 1500 LENS=cinematic RD=28` walks it in the real game on GitHub's macOS machines
  (about fifteen minutes; shots land in `run-autopilot/screenshots-ci/<tour>-<run id>/`). One walk per pass at most, and read one
  walk before you start many: whatever the first shows makes the rest stale.

Push first; every one of these runs what is on GitHub, not what is on disk.

## What we already learned the hard way

- **The bench's pictures have flat light.** Depth, darkness and air can only be judged in the real game's shots.
- **Look at every shot yourself for what is broken**, before you hand back: a camera inside rock or with a pillar in its face, a
  black frame, a floating thing, a wall that is one flat plane for thirty blocks, two shots that are the same picture. You do not
  judge taste; the gates session does. You do catch broken.
- **A tour shot waits ten seconds** (`wait 200`) after it moves, so the air has filled as it would round someone standing there.
- **Minecraft's particle shader throws away anything under a tenth opaque.** A faint sprite is not drawn at all; a big coarse one
  reads as glass; a fine-grained one as static. Do not try to make light or mist from sprites (SPEC §50.9 "Air").
- **The client's air stops at 260 sprites and takes a land's list in order.** What must not be starved goes first in `sky.air`.
- **Nothing in Lens reads Gate Air** (a land's shafts, mist layers and lights). Only the client's small life does.
- **A layout comes from the seed alone, whatever the kind.** Check several seeds, and the seeds the suite uses (1 and 2).
- **`Settle` takes away small strays and counts them** (`settled:` in the bench report). A hanging thing needs solid rock over it.
- **Carve inward, never build outward, beside anything a player stands on or jumps from.** A thing that stands proud can block a
  jump; a recess cannot. Keep any recess at least five cells above the feet of whoever stands beside it.
- **A room is told of every way into it before anything asks its floor height there** (`Cave.door`), or a doorway ends on a bank.
- **Real light, not a claim of light:** an unseen `minecraft:light` block where light should fall. A light at a tall roof never
  reaches the floor; put more down the way it falls.
- **Dark themes need lit jumps.** What you stand on and what you jump to must both be lit. "A jump nobody can see is not a jump."
- **A fetch that moves `origin/build` between two commands will make your merge message lie.** Resolve the hash once into a
  variable and use it.

## Hand-back (your last message; its first 400 characters are relayed to the gates session)

```
DONE|PARTIAL|BLOCKED <brief number and name>  branch <name> at <sha>, pushed
Works: <what, with the numbers the checks printed>
Checked: <compile run id; bench run ids and seeds; walk run ids; which shots you read>
Not done / not sure: <plainly, including anything you could not verify>
Choices I made: <short list>
Needs changing outside my files: <or "nothing">
For Euan to decide: <or "nothing">
```
