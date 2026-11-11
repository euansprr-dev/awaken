# Brief 06: five E gates brought over from the old branches

**Why.** Five of the plan's fourteen E-rank gates were built on 9 and 10 Nov by two builder sessions on another account and never
merged: Glowroot Hollow and Sporefall on `origin/claude/gates-builder-a`, Cinder Stair, The Long Shaft and The Ash Isles on
`origin/claude/gates-builder-b`. Since then the kit they stand on has moved a long way (the bench, `SheetLand`, `Settle`, real light
in the preview, the Run and its legs, depth and air, the gate monsters, the `ready` flag). Nobody has seen any of the five in the
real game. They are worth bringing over because they are the forms the E rank otherwise lacks: a Deep, a Land, a Climb, a Dungeon
and a Sky.

Start this brief only after brief 05 has handed back once: its first gate is the pattern for making a place into a run.

Two builders, one lane each:

- **Lane A (Mycel family):** Glowroot Hollow (Deep, Fungal Hollow), then Sporefall (Land, Fungal Hollow).
- **Lane B (Ember family):** Cinder Stair (Climb, Ember Mines), then The Long Shaft (Dungeon, Ember Mines), then The Ash Isles
  (Sky, Ember Mines).

**Done looks like, for each gate:** it is on your branch (cut from today's gates branch), compiles, is a run of legs at the bar,
passes the bench at seeds 1, 2 and 3, and has one real-game walk read shot by shot. As in brief 05, the gates session then looks
and sets `"ready"`.

## Read first

Everything brief 05 lists under "Read first" (the bar, §50.9, `Land.java`, The Hour Crypt and The Broodwarren as runs), then your
gates' rows in `docs/gates-as-worlds/ROSTER.md`:

| Gate | Rule | The great thing | Lord's place |
|---|---|---|---|
| Glowroot Hollow | Spore clouds carry you across gaps; breathing them slows you | A cavern with a mushroom as tall as a tower | Ring over a glowing drop |
| Sporefall | Puffball fields burst and launch you when you run over them | A mushroom forest under a split cliff | Root bowl under the largest cap |
| Cinder Stair | The lava rises while you're on the way up | A mine dug straight up a dead volcano | Broken summit in the crater |
| The Long Shaft | Cart rails carry you, and whatever's chasing you | A pit mine circled by a single rail | Forge floor at the bottom |
| The Ash Isles | Columns of hot ash lift you between islands | Floating slag islands round a burning core | Long bridge to the core |

## Part 1: bring it over

1. List what the old branch added for your gate: `git diff --stat $(git merge-base origin/build/awaken-v1 origin/claude/gates-builder-a)
   origin/claude/gates-builder-a` (or `-b`). Expect the land class in `realms/land/`, its rule class, the blueprint, biomes, a
   tour, tests, lang, and for the first gate of each family its whole block family (the Ember family alone was 275 files, most of
   them generated art).
2. Take files with `git show origin/claude/gates-builder-<x>:<path> > <path>` (or `git checkout origin/... -- <path>` for whole new
   files). **Do not merge the branch**: it carries merge commits and versions of shared files that are a day and a half stale.
3. For generated art, bring the **spec** (`tools/art/specs/realm_<family>.py`) and generate again with today's tools rather than
   copying PNGs, then run `tools/art/tone_check.py <prefix>`: family middle 95 to 150, nothing structural under 60. Darkness comes
   from light, never from a dark texture.
4. Put each shared one-liner in by hand, on today's version of the file: `LandPrograms` (now `Map.ofEntries`), `RealmLive.wake` (one
   line for the rule), `RealmState` (only if the rule saves something; bump nothing you do not need), `RealmTests` (the gate's
   walk and float tests), `data/awaken/dimension/realms.json`, lang and tags under `src/main/merge/gates/`.
5. Lane B only: builder B was stopped mid-run with five uncommitted files in its worktree
   (`/Volumes/MEDIA/Awaken/.claude/worktrees/agent-a5737058bb2717c16`: `AshIsles.java`, `LongShaft.java`,
   `worldgen/biome/realm/ash_sky.json`, `realm-ash_isles.txt`, `realm-long_shaft.txt`). If that folder still exists, read those
   files (do not edit that worktree) and take what is better than the committed version, saying which you took.
6. Make it compile against today's kit. The old code may call kit methods that have changed or draw without `Settle`: port it to
   stand where today's gates stand (`kit/SheetLand` if it fits, or the way The Quiet Barrow does it), and run the bench.
7. Every new block and item: recipe, loot or `Sources.declare`; its place in `blocks_motion_no_leaves.json` if solid; lang in
   plain speech. Creature ids in the blueprint must exist (the old Wax Galleries blueprint named two that did not).

Hand back **after the first gate of your lane compiles and benches clean**, before going further, with what the port cost and
what broke: the gates session and the other lane both need to know.

## Part 2: make it a run, and bring it to the bar

Exactly as brief 05, parts 1 and 2, with these notes:

- *Glowroot Hollow.* The spore clouds are the movement leg (carried across a gap) and the trap (breathing them slows you in a
  fight). The mushroom as tall as a tower must be seen from the way in.
- *Sporefall.* An open land: give each fight a place the pack holds (a hollow under a cap, a ring of puffballs that launch you out
  of it if you back into them). The split cliff is the climb.
- *Cinder Stair.* A climb, and the only gate so far whose route is upward all the way: the rising lava is its clock. The lava must
  never trap a player with no way on: check with the walk at the lava's highest level for each leg.
- *The Long Shaft.* It took six hours on the old branch and was never seen. Read it critically before porting: if the rail does
  not work as a ride you can fight on, say so and propose the smallest version that does (a rail down the pit as its movement
  legs, on foot between).
- *The Ash Isles.* A sky gate: every crossing is over a real fall. The fall catch (`Land.Course`) must cover every island gap, and
  the ash columns that lift you must be `UpdraftBlock` or the same push, tested by the walk.

## Checks

As brief 05: compile and bench (seeds 1, 2, 3) after the port and after each part; the gate's tour rewritten leg by leg; one
real-game walk per gate, read shot by shot.

## Yours

Lane A: `land/GlowrootHollow.java`, `land/Sporefall.java`, their rule classes, the Mycel family's spec and blocks, their
blueprints, biomes, tours, tests, lang. Lane B: `land/CinderStair.java`, `land/LongShaft.java`, `land/AshIsles.java`, their rules,
the Ember family (if not already in the build: check `gates/block/` first, the rolled Cinder Workings uses ember blocks), their
blueprints, biomes, tours, tests, lang. The shared one-liners in step 4. SPEC §50.8: each gate's notes.

## Traps

- A block family that already exists in the build under the same names (Ember) must not be registered twice. Compare ids first
  and reuse what is there; bring only what is missing.
- The old branches' `RealmTests` and `LandPrograms` are whole old files. Never copy them over today's.
- Their tours end with a `quit` and use `realm_enter`; today's tours also need `need realm`, `hud off` and `wait 200` before a shot.
- If a gate cannot be brought to compile in an hour, stop and hand back what blocks it. A clean rebuild from its roster row on
  today's kit may be cheaper than the port, and that is the gates session's call.
