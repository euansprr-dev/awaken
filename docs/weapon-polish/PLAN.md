# Weapon polish (plan, 10 Nov 2026)

Status: **approved and built.** Euan, 10 Nov, of the second (voxel) version of the page
(https://claude.ai/artifact/EZkCwbGng9aNhA6rB4t6rC): "This is great go ahead with the full plan". The re-cut is in
`tools/art/specs/arms_weapons.py` (`blade`, `point`, `curved`, the Katana's links, six designs and the Shieldblade's
sword), the assets regenerated, SPEC 25 and `docs/combat/weapon-style.md` updated. `docs/weapon-polish/fingerprint.py
check` proves the spec builds all 111 weapons corner for corner as the drafts he saw. Not merged into build; unplayed.

CI on the built branch: the look tour passed (run 42817291316: the game boots on the One Stage K menu and draws the
re-cut weapons in first person, third person and the Hall). GameTests: the first full run was red on three tests that
no weapon file reaches (`delve_heart_in_rock`, `fights_reach_live_hive_regent`, `ground_soft_steps_paint`; run
42817251865); the second full run on the same code was green (run 42820926953), so they are flakes. Build itself was
red on other fight tests at the time (`fights_mind_drinks_hive_regent` on 325a3d003).

## Revision 2: voxel only (this is the plan now)

Euan, 10 Nov, on the first version's "ground blades" (sections 6 to 8 below): "I think this looks really bad. It takes
away from the MC... it doesn't feel like MC at all anymore. Nowhere in MC would we have shapes just like this. It has to
kind of stay that voxel style. Just like extremely well polished voxel style."

So the hard rule: **weapons are boxes at whole-texel sizes, plus the whole-angle turned boxes they already use. No
slanted face, wedge, loft or smooth taper.** The audit and root cause (sections 2 to 4) stand. The fix changes:

- **Straight blades take the Rapier's cut:** a slab one texel thick the blade's full width (its outer texel a side the
  edge's material), a ridge one texel wide and three deep down the middle (three wide on blades 9 or wider). The Rapier
  and the Gatewrought Daggers and Twinblades are already built this way and read slim in the hand.
- **Points** are restaged: steps of two texels in width over more texels (a Longsword's 5, 3, 1 over eight), ending one
  texel square. Never shorter than today's, at most about a third of the blade.
- **Katana:** the same six turned boxes and the same length along the blade (24), the bend spread evenly (1, 3, 5, 7,
  9, 11 degrees where today it is 0, 3, 6, 9, 13, 20), one texel thick, the last two links narrowing to the back.
- **Glaive, Scythe:** each link a flat one texel thick with a back one wide and three deep on links 4 or wider.
- **Tusk and talon daggers, the tusk Twinblades:** a texel thinner.
- **Shieldblade's sword:** 5 wide (was 6), the slab and its lit fuller as the ridge; stone 5 deep (was 7).
- **Left alone:** Maul, War-staff, Precursor Arm, Gauntlets, Rapier (has the cut), Axes and the Chainblades' tusk hooks
  (thinning them set neighbouring parts fighting over planes).

63 of 111 weapons change (57 forged, 6 Lord-forged: `docs/weapon-polish/changed.json`). Every changed weapon keeps its
ends within half a texel and passes the plane-fight check. No toolkit change is needed: `voxel.py` builds all of it.
Alternatives shown on the page: a thinner post (hard to see), flat blades with the ridge only painted (slimmer, less of
a model), and for the Katana the curve alone at today's thickness.

What the measurement says, so nobody oversells it: with the ridge kept, a Longsword's outline on screen is almost
unchanged (148 to 145 px at mid-blade); what goes is the post's mass. The Katana is 21% thinner and the Shieldblade's
sword 19%.

Stages: 0 Longsword and Katana in the game; 1 Greatblade, Shieldblade's sword, Daggers, Twinblades (24); 2 Glaive,
Scythe, Spear, Chainblade heads (21); 3 the six Lord-forged by hand. In the build this is edits to
`arms_weapons.py` only (its `blade()` helper, the link tables and a few designs), SPEC 25 and
`docs/combat/weapon-style.md` rule 3 in the same commits.

Draft code: `tools/art/arms_polish.py` (`MODE = "voxel"`: `vblade`, `vchain`, `AFTER_V`; the ground-blade code above it
is kept only as the record of what was rejected). Real-game tour of the voxel drafts: run 42812463084 on the throwaway
branch (queued at the time of writing).

---

# Revision 1 (rejected 10 Nov: its slanted faces are not Minecraft). Kept as the record.

## 1. The brief

Euan, 10 Nov: "analyze all the models of each weapon and see how we can again keep a similar language, but maybe some of
them are a bit too chunky and don't look that good in the hand. Some of the blades to me look a bit fat. Most are still
really good, like the Maul... the Katana is decent, but I think can still be slightly improved... I don't want to
completely scrap everything. It's more so... the kind of small polish pass that we can do to massively bring it to the
most extreme and awesome level."

- **Job:** every weapon should look sharp and right in the hand, at the Maul's level.
- **Pain:** blades read fat and chunky in first person; the Katana is only decent.
- **Keep:** the design language, every weapon's identity, the Maul as it is.
- **Not asked:** new weapons, new stats, new motion (weapon-feel owns holds and swings).
- **Strong result:** a blade reads as a blade at a glance in first person: thin at the edge, a clear point, one clean
  line of light down it; the weapon is still plainly the same weapon. **Weak:** thinner but still stacked boxes.
  **Unacceptable:** a new look that loses the chunky painted Minecraft feel (the two looks he already turned down:
  "still very, very blocky" and "too thin and noisy").

## 2. Audit (verified)

| Stage | What it does | Evidence | Keep / change |
|---|---|---|---|
| `tools/art/specs/arms_weapons.py` | every weapon designed by hand as boxes, 111 designs, one catalog | read in full; `check` builds all 111 | keep; blades re-cut |
| `tools/art/voxel.py` | boxes on a whole-texel grid, each face its own chart, plane-fight check | read in full | extend with a ground solid |
| painter (same file) | flat ramps, a lit and a dark band per edge, contact shadows, material maps | read | keep; one rule added for bevels |
| `tools/art/mesh.py` + `WeaponMesh.java` | generic triangles with normals, uv, along | read; loader takes any mesh up to 200,000 corners | keep |
| `arms_weapon.fsh` / `mesh_render.py` | the shader and its offline twin; `render_fp` is the game's projection | offline render compared with CI shot 42634309119 | keep |
| holds (`arms_holds.py`) | first and third person, solved per weapon from grip, edge side | read; unchanged by this plan | keep (weapon-feel's) |
| Hall of Arms, War Anvil | draw the same meshes (`hall/client/Meshes.java`, `WarAnvilRenderer`) | read | keep |
| icons | rendered from the mesh by `icon()` | read | regenerate |

What is hand-made: every design, ramp and paint rule. What is generated: every mesh, texture, icon, item JSON, hold,
`ArmsData.java`, `ArmsHilts.java`, the cost table.

## 3. The disappointment, reproduced

Real game, CI tour `arms-holds` run 42634309119 (9 Nov, the holds now in build), Gatewrought tier, 1280 x 720:
- Longsword, Shieldblade, Greatblade: the blade is three stacked planks (edge slab, core, edge slab) with a flat roof.
- Katana: a curved bar 3 by 2, its tip three steps, kinks where its links overlap.
- Glaive, Scythe: the blade is a 5 by 3 bar.
- Maul, War-staff, Precursor Arm, Gauntlets: block-natured, read well.
Offline renders of all 111 (`build/weapon-polish/before/`) match the game's shots.

## 4. Root causes

| Symptom | Cause | Evidence | Disconfirming test | Result |
|---|---|---|---|---|
| blades read fat | the blade's core is a square post: 3 wide and 3 deep on a 5 wide blade (weapon-style rule 3) | spec; in-game shots | re-cut the section, measure on screen | Longsword 17% thinner on screen, Katana 21%, Sovereign Greatblade 29% |
| fat shows more since 9 Nov | swords are held edge ahead like a vanilla sword, so the eye sees the section | SPEC 25 "the hold"; the 2 Nov audit had turned the flat to the eye | none needed: Euan asked for this hold | kept |
| blunt, blocky ends | tips, axe bits and spear heads are stacks of boxes (stairs), which rule 2 forbids for diagonals | spec; renders | draw true points | reads sharp in every view |
| "weapons are too big"? | rejected: a Longsword is 148 px across at mid-blade, a vanilla diamond sword 156 | `arms_polish.py measure` | | size is not the cause, mass is |
| hilts too deep? | mostly fixed 2 Nov (nothing deeper than 5); only the Shieldblade's stone (7) and guard top (5) are over | spec | | trimmed with its blade |

Why it happened: the look was built from one reference, a hammer. The Maul is that hammer. Every blade inherited the
hammer's mass.

## 5. Success contract

- Every bladed weapon: edge thickness 0 (a knife edge), spine at most 2 texels (2.5 for Greatblades), a true point.
- No weapon changes its length, grip, width, edge side, id, name, stats, recipe, hold or swing. Verified for all 82:
  ends within half a texel of today's.
- The Maul, War-staff, Precursor Arm, Gauntlets and the narwhal Rapier are untouched (29 weapons).
- Triangle count goes down or stays (e.g. Longsword 248 to 188); budget 6,000 triangles; atlas at most 64 x 256.
- Non-goals: motion, holds, sounds, trails, stats, the Hall's layout, new weapons, armour.
- Must not regress: the War Anvil's forging (it heats the mesh along its length), the Hall's wall, glows and streams,
  `arms_assets` and the plane-fight check.

## 6. Alternatives

| | What | Good | Bad |
|---|---|---|---|
| A. Thinner boxes | cores 3 deep become 2 | one-line changes, no new shapes | still stacked planks, still stairs at the tip |
| **B. Ground blades** | a flat, a real bevel to a knife edge, a true point; hilts stay boxes | reads as a blade from every side; fewer triangles; same texels and paint | one new piece in the toolkit; must not drift to the look he rejected |
| C. Flat like vanilla | blades 1 texel thick | thinnest | loses all weight; the look he called too thin |
| D. Re-sculpt | new dense meshes | most freedom | scraps the language; rejected twice before |

Recommendation: **B**. Evidence that would change it: the Stage 0 shots in the real game reading thin or noisy.

Guard rails so B stays the same language: the same texel size; flat facets only (no smoothing); at most one bevel a
side; no new detail kinds; hilts, guards, stones, hafts and chains untouched; block-natured weapons untouched.

## 7. Contract for the build

- Toolkit: `Solid` (a convex piece with slanted faces, each face its own chart at about one texel a unit, a bevel 2.24
  across takes 2 texels), `loft` (a section carried along a path, mitred at bends), `wedge` (an axe bit) move from the
  draft `tools/art/arms_polish.py` into `voxel.py`; `check()` gains: no twisted face past 3 degrees, texel stretch at
  most 15%, every knife edge closed, and the existing plane-fight rule over solids.
- Painter: a bevel toward the painted light is a step up, away a step down; the texel that holds the edge is the edge
  material. Everything else as today.
- Generated files only: `.mesh`, textures, icons, `ArmsHilts.java`. No Java by hand.
- SPEC 25 "Weapons" and `docs/combat/weapon-style.md` rules 2 and 3 are rewritten in the same commits.
- Rollback: every stage is one commit per style of generated files; `git revert` restores a style.

## 8. Stages

0. **Proof in the game.** Merge build (One Stage UI) into the branch. Toolkit into `voxel.py`. Longsword x6 and
   Katana x6. CI look tour `arms-polish` (first person, third person, Hall), the anvil's forging film, GameTests.
   Shots to Euan.
1. **Blades held edge ahead:** Greatblade, Shieldblade's sword, Daggers, Twinblades, Rapier (29).
2. **Heads on hafts:** Axe, Spear, Glaive, Scythe, Chainblade (30).
3. **The Lord-forged** (11 of 15), each re-cut and checked by hand.
Each stage: tour, GameTests on CI, one hand-back.

Estimate at AI pace: Stage 0 about 2 hours, Stages 1 to 3 about 4 hours together. The drafts already exist; most of the
work is moving the stand-ins into the spec and checking each weapon's shots.

## 9. Risks

- Reads like the lofted look he rejected: Stage 0 exists to see this in the game first.
- A slanted face and a box face sharing a plane (z-fight): the check is extended; drafts avoid it by starting a blade
  where its guard ends.
- Hand-keyed styles (Daggers, Scythe, Twinblades, Shieldblade) carry blade lengths in `arms_keys.py`: lengths are kept.
- The Hall measures a weapon's tilt from its ends (`Meshes.measure`): ends are kept, verified in the tour.
- Ember, wisp and mote streams sit on edges: their points are regenerated from the new edges; `arms_assets` checks them.

## 10. Coordination

- weapon-feel: nothing they bake needs redoing (same grip, length, edge side). Their Stage 6 hand-keying should follow
  these shapes. If Euan wants a scabbard, it is modelled here in the same language.
- Hall of Arms / One Stage UI: merge build first; the Hall draws whatever mesh the id has.

## 11. Decisions for Euan (on the page)

1. Ground blades (B), thinner boxes (A), or leave as is.
2. All 82 bladed weapons, or swords and the Katana only.
3. The Lord-forged too (last, by hand), or leave them.
4. The Katana at today's length (no motion work), or two texels longer (weapon-feel re-solves its swings).
5. After Stage 0's shots: carry on, or wait until he has played it.

## 12. Evidence from the real game

One look tour (`tools/autopilot/arms-polish.txt`), run twice on GitHub's machines:
- today's models: run 42791961802 (branch `claude/weapon-models-plan-c4ab96` at 9b5b6073);
- the final drafts: run 42795267891 (branch `claude/weapon-models-draft-c4ab96` at db243b29).
The game loads and draws the ground meshes in first person, third person and the Hall of Arms with no code change.
An earlier cut of the drafts toured at 42792334098 (points a texel longer); the page shows the final run.

## 13. Files

- `tools/art/arms_polish.py`: the draft harness (`directions`, `review`, `strips`, `measure`, `draft-write`).
- `tools/autopilot/arms-polish.txt`: the look tour, the same views on any branch.
- Branch `claude/weapon-models-draft-c4ab96`: a throwaway with the drafts written over the assets, for CI tours only.
  Never merge it.
