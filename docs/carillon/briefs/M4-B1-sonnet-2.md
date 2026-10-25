# M4-B1 · Sonnet 2: the walls' kit and random texture variants (SPEC §16.0 decision 27)

Same rules as `M4-B1-sonnet-1.md` (worktree, no Gradle/game, Python + PIL only, one commit of your own files, no push).
Extend `tools/art/specs/city_kit.py` (textures in `build()`, models/blockstates in `content()`), re-run both, re-check.

Euan's decision 27: walls and roofs must never visibly tile. Vanilla's way: a blockstate variant is a LIST of models with
weights (`{"model": ..., "weight": 2, "y": 180}`), the game picks one per block position. Check the format in
`/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/blockstates/stone.json` (random list) and `chorus_plant.json`
(`weight`). Mirrored models (`minecraft:block/cube_mirrored_all`, see `models/block/stone_mirrored.json`) and y rotations
of 180 add more variety for free on full cubes (only where the texture has no up/down direction that would show).

## New blocks (already in `src/main/java/dev/awaken/city/kit/KitBlocks.java` and `CityKit.java`; read them)
1. `awaken:plaster` (colour: white, cream, ochre, rose, clay, mauve, oxblood, sage). Lime-washed render, the infill of
   Bellwatch's timber frames and the Market's pastel fronts. Per colour 4 textures `plaster_<colour>_0..3`:
   0 clean (weight 6), 1 a hairline crack and a faint stain (2), 2 a patch where the wash wore thin showing grey render
   beneath, about 3×4 px (1), 3 a weather streak running down from the top edge (1). Low-contrast mottling in 3–4 tones
   (never flat, never noisy like calcite), a few darker flecks, seamless between all four variants (no border lines).
   Target averages: white (226,224,214), cream (222,206,170), ochre (204,158,84), rose (208,160,150), clay (184,118,90),
   mauve (166,150,160), oxblood (128,58,48), sage (158,170,138). Blockstate: per colour a weighted list of
   cube_all models plus mirrored copies and y 180 rotations of the clean one.
2. `awaken:timber` (axis x/y/z, tone dark/pale): hewn frame timber. Side textures `timber_<tone>_side_0..3`: vertical
   grain streaks (0, weight 5), a knot (1, w2), a split along the grain (2, w1), a pair of oak pegs as two small lighter
   squares (3, w2); end textures `timber_<tone>_end_0..1` (end grain, squared by an adze, a few rings). Dark ≈ vanilla
   stripped_dark_oak_log but a touch greyer and weathered, average about (66,52,38); pale ≈ weathered spruce, about
   (128,100,66). Models like vanilla's stripped logs (`minecraft:block/cube_column`, `cube_column_horizontal`); the
   blockstate keeps vanilla's axis rotations (copy `stripped_dark_oak_log.json`) with a weighted list per axis×tone.
3. `awaken:city_bricks`: warm red-brown brick in vanilla's course layout (4 px courses, 8 px bricks, offset, mortar
   lighter), average about (150,92,76), slightly more varied than vanilla bricks. Variants `city_bricks_0..4`: plain (w5),
   a few burnt dark headers (w2), a few pale yellowish bricks (w2), a soot streak (w1), a patch of mismatched repair
   bricks (w1). Mirrored copies allowed.
4. `awaken:ashlar`: the ground storeys' dressed stone. It sits beside vanilla `stone_brick_stairs` courses, so keep
   stone_bricks' palette (average about (122,121,122), range 90–156) but with calmer, larger blocks (two 8 px courses,
   blocks 8 and 16 px long, alternating). Variants `ashlar_0..5`: plain (w5), one block a shade warmer (w2), a crack (w1),
   moss creeping along the bottom edge (w1), lichen spots (w1), a chipped arris (w1).
5. Roof tiles get variants too: keep `slate_tiles` and `clay_tiles` as variant 0 and add `slate_tiles_1..3`,
   `clay_tiles_1..3`: a few new (lighter) tiles (w2), a lichen or moss spot on slate / a darker weathered tile on clay
   (w1), a cracked or slipped tile (w1); variant 0 weight 5. All four must tile with each other. The full block uses the
   weighted list; the stairs and slab blockstates use, for EVERY state, a weighted list of the same state's model in each
   texture variant (so write stairs/inner/outer/slab/slab_top models per variant; keep x, y and uvlock as vanilla's).

## Lang and tags
Lang (`src/main/merge/city/assets/awaken/lang/en_us.json`): "Plaster", "Timber", "City Bricks", "Ashlar".
Mineable: pickaxe for city_bricks and ashlar and plaster; axe for timber.

## Checks and hand-back
As in brief 1: every state of every kit block maps to existing models and textures (re-run your check script over all
blocks, including the new ones and every weighted list); a contact sheet of the new textures
(`kit-sheet-2.png`); and a second mock `kit-mock-2.png`: for each new material, a 12×8 block wall drawn by picking
variants with their weights from a seeded random (so I can see that it doesn't tile), plus a Bellwatch timber frame (dark
timber posts every 3, plaster_white panels) and a Market front in plaster_rose with ashlar ground storey. Look at both
yourself and fix anything that tiles visibly, reads flat or noisy, or clashes with vanilla before handing back.
