# Brief 04: the rolled dungeons' rough edges

**Why.** Reading all seventeen kinds in the real game on 11 Nov (187 shots, tag w4, runs 42896117252 to 42896313750, in
`run-autopilot/screenshots-ci/realm-<kind>-<run>/` of the gates worktree) found no broken builds, and these five things that are
not good enough for "nothing broken... fully featured". Euan's rule for all of them: "vanilla-looking particles... read as an
add-on; every feature needs its own look", and "a jump nobody can see is not a jump".

Do them in this order. Each is its own commit.

## 1. The spitters' spray is Minecraft's llama spit

`realms/RealmTraps.java` sends `ParticleTypes.SPIT` along a spitter's line. Its first frames are a plain square: with the camera
in the line of fire it fills a corner of the view with flat pale squares (see `sunken_stacks_trap.png` in run 42875164141 and
`catacombs_trap.png` in run 42912016753). Replace it with the gates' own:

- The gates already have a coloured glowing mote the server can send (`GatesRegistry.P_TRAIL`, a `ColorParticleOption`) and a
  coloured flake (`P_DRIFT`); the gate monsters have their own shots (SPEC §60.7). Use what exists; add no sprite unless you must.
- A spitter's breath is a tight stream of motes in **its kind's colour** (each skin has `airTint()`; give the trap the colour when
  the generator makes it), fast along the line, a few slower flakes falling out of it, and nothing wider than a third of a block.
- Keep the timing and the damage exactly as they are. The warning a player reads (the spitter lighting before it fires) must still
  be the brightest thing on the wall.
- Sound: the spit's sound must be one of Minecraft's own recordings by name that suits a jet of something, not the llama's.

## 2. Two kinds have no stairs

The Lower Combs (`land/BroodBurrow.java`) and The Tallow Runs (`land/TallowRun.java`) return no stairs in `trim()`, because their
rock has no cut shapes, so their floors and roofs step in whole blocks: the thing Euan named ("it's still all one block distances").
Give each family a slab and a stair of its main rock and of its floor block, the way the other themes have them:

- Art comes from the specs (`tools/art/specs/`, the gate depth table `gate_depth.py` and `gates/block/DepthBlocks`): add the shapes
  there, generate, and check `tools/art/tone_check.py` for the family. No hand-made PNGs.
- Each new block: a recipe or `Sources.declare`, its lang line (plain speech), the mining tag it needs, and its place in
  `src/main/merge/gates/data/minecraft/tags/block/blocks_motion_no_leaves.json`.
- Then set `footStairs` and `roofStairs` in the two `trim()`s and the half step in `step(M)`.

## 3. Tour cameras that tell the truth

`/awaken gate realm_leg <kind> <index> <back> <up>` (`GateCommands.java`, with `Land.legView` and the same parsing in
`realms/preview/Preview.java`) walks toward the leg until the camera's cell is air. Three of 187 shots had a rock or a pillar in the
camera's face (`green_dark_ambush`, `hollow_hills_ambush`, `tallow_runs_warden`), and a kind whose seed has one fight or no trap
repeats a shot (The Cinder Workings on seed 383).

- Choose the camera's place by sight, not by distance: of the cells at about `back` from the leg, standing height, take the one
  with the longest clear line toward the leg's middle, and require at least six clear blocks in front of the lens. Fall back to
  the leg's own middle looking along its exit.
- If the leg does not exist on this seed, fail with a message the tour log shows, and make the tour skip that shot (the tour
  language has `need`; read `tools/autopilot.sh` for what a script can test). No duplicate pictures.
- Then choose each kind's tour seed again (`tools/autopilot/realm-rolled-tours.py`) so its run has at least two fights, a trap leg
  and three movement legs: the bench prints every leg of a seed. Brief 01 adds kinds to that script; you change only seeds, the
  shot list and the camera.

## 4. Crossings you can see in the darkest kinds

The generator lights every stand of a crossing and every eighth of a turn of a climb (`GeneratedCrawl.finish`, an unseen light at
level 14). What you stand on is lit; the walls and the far side are not. Middle brightness of the movement shots, 0 to 255: The
Hollow Hills 19 to 24, The Green Dark 20 to 24, The Unlit Stair 13 to 18, against 30 to 60 elsewhere.

- Light the walls, not the air: in the bays and scoops that gulf and stairwell walls now have (`Chasm.walls`, `Shaft.walls`), set a
  light every eight or so cells along, at about the height of the stands: the skin's own lamp where it has one (a new hook on the
  skin, default none), else an unseen light at level 10. A bay with a light in it is the reason the bay is there.
- Light the far shelf of every gulf a little more than the near one, so the way on is what the eye goes to.
- Targets in a real-game walk: movement shots at 28 or more in The Hollow Hills and The Green Dark, 20 or more in The Unlit Stair
  (it is the darkest kind on purpose: do not make it grey), and no change over 5 in a bright kind (walk The Silvered Halls to check).

## 5. One honest table of every kind's sound

Nobody has heard any kind's sound or music in place. Do not change any. Write `docs/gates-as-worlds/the-run/SOUND.md`: for each
kind, its ambient loop, mood sound, occasional sound and music (read them from `tools/realms/kinds_air.py`), what each recording
actually is in plain words, and a one-line worry where one seems wrong for the place (a nether loop in a library). It is the
sheet Euan and the sound session will listen from.

## Checks

- `tools/check-ci.sh compile`; `tools/check-ci.sh bench` on the two kinds whose blocks changed and on three kinds after part 4,
  seeds 1, 2 and the tour's: `floats: 0`, `reach: all`.
- Real-game walks, read shot by shot: a kind with a gallery after part 1; the two kinds after part 2; all nineteen tours once after
  part 3 (this is the one place a full round of walks is right: send them in one go and look once every five minutes); the three
  dark kinds and The Silvered Halls after part 4.

## Yours

`realms/RealmTraps.java`; `land/BroodBurrow.java` and `land/TallowRun.java` (`trim`, `step`, their `M`); the art specs and
`DepthBlocks` for those two families and what they generate; the `realm_leg` command in `GateCommands.java`, `Land.legView`,
`preview/Preview.java`'s parse; seeds and the shot list in the tours script; in `kit/crawl/GeneratedCrawl.java` only `finish()` and
a new helper beside it (brief 01 owns `generate()`); a lamp hook on the skins. SPEC §50.9: "A climb is never dark" (the wall
lights), the trap's spray, and the two families' shapes in §38.14's table.

## Traps

- A light or a lamp set into a bay must be on the dungeon's own rock, never on something a skin hung there (`Settle` will take it).
- `finish()` runs once per chunk with a one-block margin: decide everything by position, never by chance that differs between calls.
- Do not light a climb by brightening a texture or raising a biome's ambient light. Darkness comes from light, never from colour.
