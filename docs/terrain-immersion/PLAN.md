# The Soft Land: AWAKEN's own terrain softening and foliage (plan)

**Status (2026-10-27, after Euan approved all 15 decisions):** P0 seamless ground, P1 soft steps, P2 the floor (without the three own grass heights, clover and roots) and P3 mountains are built and tested; P4 has its road bands but not its banks, P5 waits for the regions plan's level field. What is built, with the numbers and the tests, is SPEC §31 (`docs/SPEC.md`); this file is the plan and its reasoning. The text below is the plan as approved.

Written 2026-10-27 on branch `pack/terrain-immersion-plan`
for Euan's brief ("a bit less rough terrain, a bit more cinematic and natural look (stones etc.), more foliage feeling
that creates a more immersive look; the priority really is IMMERSION", and earlier "how the vegetation, paths, and
mountains look to be less like just block per block gaps, while STILL keeping it feeling like minecraft").

Approval page: https://claude.ai/artifact/EHXyQyds2wzL7dGfox8gnz (source `docs/terrain-immersion/page.html`; the same decisions in plain words, with the pictures).
Proof (disposable, not production): `docs/terrain-immersion/proof/` (a Java bench of the soft-step pass on AWAKEN's own
erosion, a small voxel renderer, the four scenes). Pictures: `docs/terrain-immersion/img/`.

---

## 0. The short version

The roughness Euan sees is not noisy terrain. It is four plain things, in this order of how much they cost the look:

1. **Brown stripes.** A vanilla grass block shows its dirt side wherever a step is exposed, so every hill and mountain reads
   as brown stairs with a green line on each tread. (Audit 2.1, 1 and 3.)
2. **Hard one-block steps** on gentle ground. Any slope is a staircase in a block world; on a rolling meadow 43% of all
   neighbouring columns differ by exactly one block. This is the grid, not the noise: dropping the erosion's finest octave
   changed it by 0.1 percentage points (bench, 2.4).
3. **Bare ground.** Flat tinted lawns, plants at vanilla's density and one height, no rocks, roots, litter or fallen wood,
   steep ground that stays soil and shows ore blobs as white streaks, roads that cut straight into the hill with a wall
   on each side and a coin-toss mosaic for a surface.
4. **Hard seams** between grass, dirt, path, gravel, sand and stone: square edges, no creep, no wear.

The plan is **AWAKEN's own, in five layers, cheapest and most visible first**. The mods Euan's session found are references
and fallbacks; none is the answer (section 4).

| Phase | What the player sees | Works in | Cost to build (project pace) |
|---|---|---|---|
| **P0 Seamless ground** | grass wraps green down a one-block step, grass creeps onto paths and dirt, the lawn's colour drifts in patches; near and far agree | **every world, at once** (client only) | ~3 h |
| **P1 Soft steps** | half-block turf and earth slabs on the foot of every one-block rise: -97% one-block risers on rolling land, -42% on 35° hills, walking up a hill without a jump | new worlds | ~5 h |
| **P2 The floor** | pebbles, twigs, three grass heights, drifts of litter, ferns and flowers, boulders, roots, fallen logs, moss, per-biome palettes | new worlds | ~5 h |
| **P3 Mountains** | slope-led ground (grass, scree, rock), strata bands, ledges, snow in layers | new worlds | ~3 h |
| **P4 Roads that sit in the land** | graded banks instead of walls, wheel ruts and a worn crown instead of coin-toss tiles, soft edges | new worlds | ~3 h |
| **P5 The land speaks** | rank palettes (regions), habitats for wild life, footstep tags | new worlds | ~2 h |

About 20 builder-hours in all: a day or so at the pace git history shows (all of AWAKEN was ~30 h), P0 and P3 in parallel,
then P1, then P2 and P4 in parallel. The things that do not compress are Euan's look after P0 and P1, the one new texture
set (about 30 small sprites through the art pipeline), and CI look tours.

The line, said plainly (section 5): blocks, pixels, half-block steps and light. Never bent geometry, never a texture over
16×16, never an eighth-block layer to sculpt the ground.

---

## 1. The brief

**The job.** Walk through AWAKEN's land and have it feel like a place: believable slopes, mountains with rock in them,
forests with a floor, roads that belong to the land.

**The pain** (Euan's own shots, `/Volumes/MEDIA/terrain-ref/`, numbered in the annotated copies in `img/audit-*.jpg`):
his road through the Lumen Forest; his river and mountain; a W7 road over stepped ground; a mining town at a mountain's foot.

**Proposed solutions he or his session mentioned:** natural slabs and layers, grass and path blending, ground cover, mountain
scree and snow, roads that sit in the land, Lens, motion and sound; five mods as references. Treated as options.

**Hard constraints:** still Minecraft (blocks, pixels, light); the repo is public (no Mojang assets); multiplayer-safe;
Euan plays on an M4 MacBook; every current system keeps working (section 3); nobody launches the client but Euan.

**Success** (outcome contract):

| | Strong | Weak | Unacceptable |
|---|---|---|---|
| Hillside | no brown stripe on a one-block step; gentle ground has half-steps; walking up needs no jump | steps halved but still brown | new holes, floating plants, stuck movement |
| Forest floor | something to look at in every direction within 10 m (stone, litter, log, fern) without it reading as scatter | more plants, same lawn | clutter that blocks movement or reads as noise |
| Mountain | rock where it is steep, layered; grass where it is gentle; scree between | rock everywhere | grey slabs of a different game |
| Road | the road sits in a bank; edges creep; wheels have ruts | prettier tiles in the same wall | half-slabs left in streets or yards |
| Whole | still reads as Minecraft in a screenshot at 1×: the pixel grid and block edges are all there | | looks like a smooth mesh, or like another game's pack |

**Costs held** (SPEC header and §15): features ≤ 0.5 ms a chunk on average; forest frame p50 within +0.3 ms of today; the
worldgen path never reads more noise columns than today (a Tectonic column read is 0.63 ms on the M4, §28.9).

**Non-goals:** changing Tectonic's preset or the world height; changing the erosion; smooth meshes; new biomes; replacing
the wild life, the sway or Lens. Working things that must not regress: Alpine's snow and erosion, waving leaves and plants,
Far Sight and City Sight, roads and settlements, the Ascent.

**Interpretation, stated:** "less rough" means *fewer visible block-per-block gaps* (seams and one-block risers), not a
different terrain shape. "Immersion" means a viewer at eye height always has believable detail to look at and the detail
agrees with itself, near and far.

---

## 2. The audit: what the land looks like and why

### 2.1 The map

Method: the four shots, the code (paths below are in this repo), and the proof renderer, which reproduces each disappointment
from a spec (`img/hillside-before.jpg`, `forest-before.jpg`, `road-before.jpg`, `mountain-before.jpg` beside the real shots).
Verified = read in code or measured in the bench; suspected = consistent with the evidence, not isolated.

| # | What you see | What makes it | Evidence | Verdict |
|---|---|---|---|---|
| 1 | Every hill step shows a bare brown dirt face with a thin green line; the whole slope is brown stairs (shots 1 of Lumen, 3 of river, 3 of W7 road, 2-3 of mountain town) | **Vanilla**: `grass_block` draws its dirt-and-fringe side on any exposed face. Nothing of ours touches it. | verified: no model or tint override for `grass_block` anywhere in `src`; our only terrain model wrapper is `terrain/client/WavingModel` (leaves, plants) | **extend**: a level-aware model wrapper for grass (P0) |
| 2 | Gentle ground is a staircase of one-block steps | **The voxel grid**, not roughness. | verified, bench: 43.1% of neighbouring column pairs differ by one block on a 35° hill; 43.1 / 43.0% with the erosion's finest octave at 16 vs 64 blocks | **add** half-steps (P1); leave Tectonic and the erosion alone |
| 3 | Mountains: brown-and-green zigzag, white streaks | thin soil on steep ground over **vanilla ore blobs** (granite, diorite, andesite reach the surface); our rock and scree rule (`terrain/alpine/AlpineSurface`, slope ≥ 1.45 scree, ≥ 1.8 rock) only runs in `#awaken:alpine` biomes and in upland above y 150 (SPEC §15), so a steep slope on forest or plains ground below 150 stays grass and dirt | verified in SPEC §15 and the code; the shot's mountain foot is below 150 (suspected, the shot has no coordinates) | **extend** the same maths to every steep slope and lower thresholds (P3) |
| 4 | The road is cut into the hill with a vertical wall each side | `village/road/RoadPaint.ground`: carriageway and a 1.6-block verge at the road's own integer y; outside that the land is untouched. The planner eases the road's height *along* itself, nothing across it. | verified in code (kinds STREET, YARD, FREE) | **extend**: grade the banks (P4) |
| 5 | Road surface: random grass tiles in the dirt, square patches | per-block coin rolls: `RoadPaint.paving` picks each block from `pct(wx, wz, 50)` bands, no neighbour or direction in it | verified | **replace** the roll with bands along the road (P4) |
| 6 | Hard square edges between path, grass, coarse dirt, sand, gravel | vanilla blocks side by side; a path is 15/16 high so its edge also reads as a step | verified (vanilla) | **add** edge creep (P0) |
| 7 | Flat forest floor, same plants everywhere, no rocks, roots or logs | the Lumen Forest feature list is vanilla's: `patch_grass_forest`, `flower_default`, mushrooms, `patch_leaf_litter` at vanilla density, plus our moss, caps and fireflies; nothing else on the ground. Elderwood is the same on vanilla's forests. | verified: `lumen_forest.json` step 9 lists exactly those | **add** (P2) |
| 8 | One lawn colour per biome, smooth | `BlockTintSources.grassBlock()`: the biome's colour, no variation within it | verified in `BlockColors.createDefault` | **add** tint drift (P0) |
| 9 | Plants: one height | vanilla short grass (0.9) and tall grass (2 blocks) only | verified (vanilla block list) | **add** (P2) |
| 10 | Far hills show none of the above | Far Sight shades a column from its biome colour; City Sight reads each face's sprite from the *static* model (`VoxelLooks.face` calls `collectParts(random, parts)` with no level), so a level-aware model shows its default faces there | verified in code | **fit**, section 3 |

### 2.2 What already works and stays

Alpine erosion (gullies, spurs), the snow line with drifts, scree and rock inside Alpine biomes; waving leaves and every
rooted plant (class rules in `PlantSway.rule`, so any plant we add that extends a vanilla plant class sways for free); falling
leaves; wild life (§29) reading the land for its habitats; Lens's shadows and haze; the city basin's soft rim; Far Sight 3.

### 2.3 What I tested rather than assumed

- **"The erosion's fine octaves make the jitter."** Not true. Bench on AWAKEN's real `Erosion` class: stopping at 32 or
  64-block cells moves the share of one-block steps from 43.1% to 43.1% and 43.0%. Leave the land's shape alone.
- **"A half-step pass is cheap and helps."** True where the slope is gentle (table in 2.4).
- **"Half-steps fix everything."** No. Above about 40° a staircase is the medium; slabs cannot halve a one-wide stair run
  without changing the macro shape. The fix there is material (rock, strata, scree), P3.

### 2.4 The bench (`proof/SoftBench.java`, results in `proof/softbench-results.txt`)

Land: AWAKEN's `Erosion` on synthetic ground, 512×512, one block resolution. Rule: a column takes a half slab on top when a
cardinal neighbour is exactly one higher, none is lower, none of the eight is two or more higher. It only adds; it never
removes a block; it reads heights only, so it is pure across chunk borders.

| Land | median / p90 slope | columns raised | one-block risers before → after | note |
|---|---|---|---|---|
| rolling meadow | 0.00 / 0.70 | 20.6% | 80,214 → 2,795 (**-97%**) | half-risers after: 153,870 |
| hillside (35° median) | 0.70 / 1.11 | 23.4% | 223,910 → 130,486 (**-42%**) | what is left is one-wide stair runs |
| mountain flank (58° median) | 1.58 / 2.54 | 2.4% | 264,679 → 254,735 (-4%) | cliffs untouched by design: faces ≥ 2 blocks are identical before and after |

Cost: **3.4-3.8 ns a column, about 1 µs a chunk** for the decision (heights only, this Mac, JIT warm). Writing the slabs
adds one section write per raised column (about 50 a chunk on hills). Both are noise beside a single Tectonic column read
(0.63 ms). The Python copy the renderer uses is checked equal to the Java output on all four lands.

---

## 3. What every current system asks of the plan

| System | What it needs | How the plan meets it |
|---|---|---|
| **Far Sight** (far renderer) | far ground from the generator's density, integer heights; shades by biome colour and the shared Alpine maths | soft steps move ground by at most ½ block: inside the dissolve at the hand-over. Tint drift and the new slope palette use the *same* value-noise and slope maths as `AlpineSurface` (`valueNoise`, `farHash`) so far and near agree. One shader addition (the drift), no new geometry. |
| **City Sight** (real chunks past the render distance) | classifies each state by `getShape` (`VoxelKinds`: slab = BOX, carpet and snow layer = FLAT, plant = THIN) and reads face sprites from the static model | slabs and layers come out right with no work (BOX, FLAT). One small change in `VoxelLooks`: a wrapped grass block draws its *side* from the top sprite, as the near view does (at that distance a one-block riser is a pixel). Tufts inside a slab are part of the model and invisible there, like other thin things. |
| **Painters** (`CityWriter`, `SettlementPaint`, `RoadPaint`, `CityTrail`; all at the `top_layer_modification` step) | write whole columns; "a chunk painted alone equals the same chunk painted with its neighbours" | the soft pass runs at `local_modifications`, **before** them (they run in the last step), and they overwrite their columns (streets and yards clear `g+1..g+4`), so **no half-slab can survive in a street or yard by construction**. A GameTest asserts it. Banks (P4) read the natural ground from the proto chunk's own heightmap, never from `RoadWorld`, so purity holds and no extra noise column is read. |
| **Ascent** (`BodyRules`, §17.21-17.27) | collision shapes decide everything: stepped slabs are a "run" for the stair slide (§17.22: two or more 0.25-0.6 steps down); mantle lips read real shapes (§17.23); step height is 0.6 | a half slab is 0.5, so walking is step-free. **Side effect to decide (D12):** a slide started on a soft hill now finds a run and glides down it, as it does on stairs. I think that is a gift. Three GameTests in P1: walk up and down a soft hill with no hop; slide down a run and stop at the flat; mantle a lip with a slab on it. |
| **Lands** (trees, sway) | plants and saplings need `#minecraft:supports_vegetation` ground (26.3's `VegetationBlock.mayPlaceOn`) and placed trees filter on a sapling surviving; `PlantSway.rule` is by class | vanilla plants and saplings **refuse** a slab (ours are not in `#minecraft:supports_vegetation`), so nothing floats and no tree takes root on one (to confirm for the Lands trees' placed features in the P1 tests). New plants extend vanilla plant classes: they sway with no change. Our tuft-in-slab gets one new sway rule (a rooted plant whose root is at y 0.5). Lumen and Elderwood ground is vanilla `grass_block` with biome tint, so wrap, slabs and drift apply as they are. |
| **Lens** | heightfield counts a slab, stair or carpet as a whole block (§22) | a half-block error in a shadow edge; invisible. No new Lens pass. The drift and palette are the colour work. |
| **Wild life** (§29) | `Sites` reads leaf tops, fences, bare rock, trunks (a log with 2+ clear blocks), flowers, water shore | boulders become "bare rock" perches, fallen logs "trunks": frogs, squirrels and birds get places to be. P5 adds habitat weights; no new code path. |
| **Regions** (§21 and the regions plan: "the land speaks first") | a pure level/rank field from seed and centre; a tint and sound by gap | cover palettes take a `rank` (E, D, C, B, A, S) from the same field: safe land lush, D up scrubby and bone-dry, C up bones and dead wood. One data table, built after regions' phase 2 lands. |
| **W7 roads, settlements, Carillon** | roads laid by planner threads at world generation; the city basin flat to 330 blocks and eased to 700 | the basin is untouched (soft pass skips nothing it needs to: flat ground raises nothing). The soft pass is exactly what the basin's soft rim wants. |
| **Worldgen cost** (§28.9 Cost) | no extra noise columns; Tectonic is slow | the pass reads the chunk's own WG heightmap (a few array reads); cover features keep within 0.5 ms a chunk each and a total of about 1 ms (measured with `/awaken terrain landscost`, which exists). |
| **Frame time** (SPEC header; M4) | forest p50 +0.3 ms at most | the wrapper runs on Sodium's mesh threads on grass blocks only; geometry keys let Sodium reuse parts; cover models are ≤ 12 quads; one Mac frame tour at the end. Estimates, not measurements, until the tour. |
| **Multiplayer** | server-authoritative, no per-player state | everything is a pure function of the seed (worldgen) or client-side (render). Nothing is stored per player. |
| **Existing worlds** | no migration | P0 changes every world at once (client only, one setting); P1-P5 are worldgen and, like Lands, Alpine and Villages, ride a data pack with a feature flag (`awaken:awakened_ground`): new worlds only. |

---

## 4. Alternatives and one recommendation per area

### A. Seamless ground (P0)

| Option | What it is | Verdict |
|---|---|---|
| Fast Better Grass (MIT resource pack; 26.3 listed) | grass sides use the grass top texture always | the same idea, but it also paints tall cliff faces green and knows nothing of our tint, fringe, City Sight or Far Sight |
| Continuity (NeoForge port, LGPL) + Overlays | general connected textures | heavy for the job; our tint drift and City Sight can't see it |
| Sodium Shadowy Path Blocks (LGPL) | smooth-light fix for non-full blocks such as paths | a bug fix, not a look; take it **only if** our wrapper leaves path edges dark |
| **Our own model wrapper** (recommended) | a `DelegateBlockStateModel` like `WavingModel`: `collectParts(level, pos, state, random, parts)` reads up to four neighbours and picks a variant; a geometry key (the 4-bit mask and the tint level) lets Sodium reuse parts | cheap, proven in this repo, no new mod on the hot path, one place to keep City Sight in step |

What P0 does:
- **Wrap**: a grass block's side face uses the green top texture when the neighbour in that direction is open and the ground just under it is within one block (a one-block riser), or is one of our slabs. A two-block riser keeps the dirt face: a real cliff.
- **Edge creep**: a path, dirt, coarse dirt, gravel or sand block gets grass blades creeping over its top from a grass neighbour: eight small sprites (four edges, four corners), tinted by the grass tint, one set for all ground types, overlay quads on the top face only. A second set lets path wear and gravel speckle creep onto grass beside a path.
- **Tint drift**: register a `BlockTintSource` over vanilla's `grassBlock()` and `grass()` that multiplies the biome colour by a low-frequency noise (about ±10% on a 24-block scale, ±5% on 6): meadow patches, not a flat carpet. The same noise in Far Sight's shader.
- **Setting** `ground_seamless` (default on). Off, the game is exactly vanilla.
- Fallback if P0 slips: add Fast Better Grass to the pack for an hour's worth of the main win.

### B. Soft steps (P1)

| Option | What it is | Verdict |
|---|---|---|
| Countered's Terrain Slabs (MIT, 500k downloads, 26.3 build 2026-10-20) | worldgen slabs of dirt, grass, mycelium, moss, gravel, sand, clay, deepslate; "vegetation and snow generate on top"; breaking drops the parent block | a good reference and a one-hour **visual trial** on our land at the start of P1. Not the answer: its rule is not ours to tune (our cliff exclusion, painters-first order), it knows none of our ground blocks (hoarfrost, mirepeat, fused glass), a second worldgen mod on the Tectonic stack costs an unknown, and I could not verify from its page how plants avoid floating. |
| Eighth-block layers (snow-layer style) to sculpt the ground | smooth rolling meadows | **No.** Pulls toward a smooth mesh (section 5), eight states per ground type, more quads. Layers stay for snow, litter and moss. |
| Smooth the shape (Tectonic `ultrasmooth`, fewer erosion octaves) | change the heights | **No.** Tested: the steps are the grid, not the noise (2.3). It would also change every world's map and Far Sight's. |
| **Own slab blocks and the foot rule** (recommended) | half-block turf and earth slabs on the foot of a one-block rise | cheap, pure, deterministic, the vanilla language (a slab), and half a block is under the 0.6 step so walking needs no jump |

Design:
- **The rule** is the bench's: a *foot* column (a cardinal neighbour exactly one higher, none lower, no cliff within one) gets a
  half slab on top of its ground block, the block beneath turns to its dirt, never anything removed. Not applied to water,
  to columns with a block above the ground, or to ground that is not a natural surface block.
- **Blocks**: one parametric `SoftSlabBlock` (extends `SlabBlock`, so waterlogging, double slabs and placement are vanilla's)
  for each natural ground that has no vanilla slab: grass (turf), dirt, coarse dirt, podzol, mud, gravel, sand, red sand and
  our Lands grounds (hoarfrost, mirepeat, riven shale, faultstone, fused glass, sun-gold grass). About 14 blocks, all
  generated from one table with the blockstate, model, loot, lang and tag JSON (the way `tools/content/lands_content.py`
  does). Stone, andesite, sandstone and the rest use **vanilla's own slabs and stairs**. A turf slab's side model uses the
  *top* half of the grass side texture so the fringe sits at the slab's top. Snow uses vanilla layers.
- **Plants on a slab**: vanilla plants refuse it, so nothing floats. To keep foot columns from reading bald, the turf slab has
  a `tuft` property (none, short, tall, fern, flower, bush) whose model draws the plant **inside the slab's own block**, from
  y 0.5 up. Chosen by a final pass at the density the plants around it have. No collision or outline difference; it sways
  through one new rule. (The proof renders show it: `hillside-soft.jpg`, `hillside-after.jpg`.)
- **Behaviour**: a turf slab under an opaque block decays to its dirt slab (random tick), as grass does; it does not spread.
  Breaking a slab gives the slab (a grass slab gives a dirt slab unless silk-touched); creative tab entry; no recipe.
  Spawning: animals' and monsters' valid-spawn checks are given our slabs (tags plus an `isValidSpawn` override), or about a
  fifth of slope columns would stop spawning.
- **Order** (generation): soft pass at `local_modifications` (after surface rules and carvers, before `surface_structures`,
  vegetation and the painters), tufts at `top_layer_modification` after the structures. Decisions read only the natural WG
  heightmap of the chunk and its neighbours, so a chunk generated alone equals one generated with its neighbours.
- **Where the line is on slopes**: soft steps help up to about 40°. Steeper ground is P3's job.

### C. The floor: cover and detail (P2)

Cheapest first:
1. **Retune vanilla features** (no code): vanilla 26.3 already ships `fallen_tree` (oak, birch, spruce, jungle, poplar, with
   mushroom and vine decorators), `forest_rock` (mossy boulders), `moss_patch`, `bush`, `wildflower`, `dry_grass`, leaf litter
   in four colours. Our biomes use few of them at vanilla's thin counts. P2 adds them to the Awakened biomes and to
   the vanilla forests, hills and plains by biome modifier in the flagged data pack, **in drifts** (noise-clustered counts,
   not even scatter): flowers in patches, litter under the oldest trees, ferns where the canopy is thick.
2. **A small set of our own blocks**, all extending vanilla classes so sway, shape and loot are free:
   *pebbles* (1-3 stones on the ground, 4 stone looks), *twigs*, *three grass heights* (low 0.5, medium, tall 1.4 as a
   double plant) and *clover* (a flat patch, like pink petals). About 30 sprites through the existing pixel-art pipeline
   (`tools/art/specs`), 3D models on the 16-grid, ≤ 12 quads each.
3. **Our own boulders and outcrops** (a feature, not a block): rounded masses of stone, mossy cobblestone and andesite with
   slab and stair edges so the silhouette has no hard corner, scaled by biome (mossy in forests, granite and andesite in
   highlands, sandstone in the steppe, frostspar in Frost Spires), 1 to 7 blocks across; and **roots**: logs surfacing at
   the foot of a tree (hero trees already have `Roots`).
4. **Per-biome palettes** in one data table (`tools/terrain/biomes.py` already owns the biome table; a `ground` column joins
   it): what, how dense, what colour level.

Not adopted: Sizeable Foliage and Geophilic (both All Rights Reserved; vanilla blocks and a few of ours do the job),
Wilder Wild (clashes with wild life), Sway and Interactive Foliage (we have our own).

### D. Mountains (P3)

No mod: Terralith is off limits, Alpine is ours. Extend what exists:
- **Slope-led ground for every steep slope**, not only Alpine biomes: grass under about 0.7; grass with coarse dirt and gravel
  patches 0.7-1.0; rock with grass pockets 1.0-1.4; rock bands with scree 1.4-1.8; bare rock above, as `AlpineSurface.stone`
  already does for its biomes but starting lower (thresholds are constants in one file shared by the chunk generator and
  Far Sight, so far mountains change with them).
- **Strata**: rock chosen by height so cliffs read as layered (stone, andesite, tuff, granite, a thin calcite band), with
  the layer index jittered by a slow noise so bands tilt. This also covers the white ore-blob streaks.
- **Scree skirts**: gravel with slabs at the foot of steep rock, thinning into grass.
- **Ledges** where rock meets gentle ground: stone slabs and stairs shelves (vanilla blocks).
- **Snow in layers** over the existing snow edge (vanilla snow layers 1-4 at the drift edge), not only snow blocks.
- **Gully planting**: ferns, bushes and spruce clumps where the slope eases (the treeline filter exists).

### E. Roads that sit in the land (P4)

- **Banks**: past the verge, ease the ground from the road's level to the natural ground over up to 7 blocks with the soft
  rule (cut banks and fill banks), reading the natural height from the proto chunk's heightmap.
- **Surface in bands**: two wheel ruts of path (a few gaps), a grass crown that breaks now and then, gravel shoulders,
  replacing the per-block coin roll. Cobbles and setts near a town stay, laid in rows.
- **Edges**: P0's fringe on every road edge; kerb stones and pebbles; flowers on the verge; half-steps in the road's own
  earth slab where the road climbs (it does this in stone now).
- **Tests**: no slab in any street or yard cell; the road's profile across never steps more than a block.

### F. The look (Lens, colour, light)

No new pass. Colour comes from the drift, the palettes and the rock. Lens keeps its shadows. Not adopted: dappled-light or
mist passes; those are Lens's own plan, and the best "light" win here is a floor with something for the light to fall on.

### G. Motion and sound

New plants sway with no change; leaf litter stays still (as vanilla's does). Boulders and logs become wild-life perches.
Add block tags `awaken:ground/litter`, `awaken:ground/brush`, `awaken:ground/gravel` so the footstep work (the Tread
Lightly mod or the Conductor) can key a crunch, a brush and a scrape off them. No new audio in this plan.

---

## 5. Where it stays Minecraft (the line)

| Fine: this is Minecraft raised | Not fine: this is another game |
|---|---|
| half-block steps (a slab is core Minecraft) | eighth-block layers to sculpt the ground |
| 16×16 textures; sprites on the 16-grid; small models of ≤ 12 quads | bent or beveled terrain, normal smoothing, parallax, textures above 16×16 |
| more blocks, smaller blocks, better seams | per-vertex smoothing of ground faces |
| colour drift, light, haze | a different art style for the land |

A test any reviewer can run on a shot at 1×: the pixel grid and the block edges are all still there, and the one thing gone
is the brown stripe. If you cannot see block edges at 10 m, it has gone too far.

---

## 6. Behaviour (contract)

| Piece | Trigger | Inputs | Processing | Output | State | Failure |
|---|---|---|---|---|---|---|
| Seamless ground | Sodium builds a section | the grass block, its 4 neighbours, the block under each | pick wrap mask, creep overlays; tint noise at (x, z) | model parts; tint colour | none (a geometry key) | setting off, or Iris on: vanilla model |
| Soft steps | chunk at `local_modifications` | WG heights of the chunk and its ring, the surface blocks | foot rule; slab and dirt writes straight into sections | slabs in the proto chunk | in the chunk | outside the flagged pack: nothing runs |
| Tufts | chunk at `top_layer_modification` | each turf slab, the biome's plant density | set `tuft` | blockstate | in the chunk | a painter already overwrote the slab: nothing to set |
| Cover | each vegetation step | biome palette, region rank, drift noise | features | blocks | in the chunk | feature over its budget: it shrinks (counts are data) |
| Mountains | surface rule | slope, height, noise | the Alpine maths, lowered | surface blocks | in the chunk | same as today |
| Road banks | road leg painted | the raster, the road's y, the natural heights | ease and soften | blocks in the raster margin | in the chunk | no natural height: leave the land as today |

Invariants: nothing removed that was solid ground; no slab under a structure, street or yard; the same seed gives the same
slabs on every machine and in any chunk order; nothing is stored per player; with `ground_seamless` off, the client matches
vanilla.

---

## 7. Cost

| | Estimate | Evidence | To measure |
|---|---|---|---|
| Soft pass | ≈ 1 µs a chunk deciding, ≈ 3 µs writing | bench (3.4 ns a column) | `terrain_ground_cost` GameTest; `/awaken terrain landscost` |
| Cover features | ≤ 0.5 ms a chunk each, ≈ 1 ms total | SPEC budget for features; vanilla features are the bulk | `/awaken terrain landscost` on 5 seeds |
| Slope palette, strata | free (surface-rule arithmetic, shared maths) | `AlpineSurface` is pure maths | Far Sight shader parity check |
| Mesh work | 4 neighbour reads on grass blocks only, off the render thread | Sodium's section snapshot reaches 2 blocks round (as `WavingModel` relies on) | frame tour |
| Quads | pebbles/twigs ≤ 12, tufts 4-8 a plant; forest floor maybe +25-40% ground-level quads | my estimate | frame tour on a dense Lumen forest |
| Frame | ≤ +0.3 ms p50 in the darkest forest (8.7 ms today, SPEC §15) | budget | **one** Mac frame tour at the end; look tours on CI |
| Worldgen total | within Tectonic's noise: the pass reads no noise | §28.9 | the road bench's fingerprint stays the same |

---

## 8. Failure register

| Case | Detect and contain | Test |
|---|---|---|
| Plant floats half a block over a slab | vanilla plants refuse slabs; tufts live inside the slab's block | `ground_no_floating_plants` (scan generated chunks) |
| Slab under a structure, street or yard | pass runs before the painters, which overwrite | `ground_painters_clear_slabs` on a settlement, a town and a road leg |
| Chunk order changes the result | decision reads only WG heights of the chunk and its ring | `ground_soft_pure` (two orders, equal blocks) |
| Grass slab under a block stays green | random tick decays it to its dirt slab | `ground_turf_decay` |
| Spawns lost on slope columns | spawn tags and `isValidSpawn` | `ground_spawn_on_slab` |
| Things placed "on the surface" by heightmap after worldgen sit half a block above a slab (camp, anomalies, Hearth pieces, gates) | one helper `ground.api.GroundTop.surfaceY(level, x, z)`; audit every `getHeight(` caller | `ground_surface_callers` (grep-level list in the build) |
| Near and far disagree by half a block or a colour | dissolve hides ½ block; same noise and slope maths both sides | `tools/farsight/seam_audit.py` tour; the far shader's drift constant equals the client's |
| Soft hill slides or stalls the player | Ascent tests in P1 | `body_soft_hill_walk`, `body_soft_hill_slide`, `body_soft_lip_mantle` |
| Mantle or slide snags on foot slabs | same | same |
| Waterlogged shores | v1 skips columns with water over them; beaches are a later pass | n/a |
| Another pack (Fast Better Grass) loaded too | both use the top texture; ours wins where it matters | none (harmless) |
| Per-section mesh cost rises | geometry keys; measure with the frame tour | frame tour |
| Old worlds | P0 only; P1-P5 behind the flag | `ground_flag_off_noop` |

---

## 9. Staged delivery

One module, `dev.awaken.ground` (blocks, soft pass, client wrappers, cover, data pack `awakened_ground`), written into SPEC as §31 in
the first commit. Small, additive touches by agreement with owners: `terrain.alpine.AlpineSurface` (thresholds),
`farsight.client.VoxelLooks` and `farsight_terrain.fsh` (parity), `village.road.RoadPaint` (banks and bands).

| Phase | Build | Gate (judged on CI look tours and renders; **Euan looks only at P0 and P1**) |
|---|---|---|
| **P0** Seamless ground | the wrapper, the 16 sprites, tint drift, City Sight and Far Sight parity, the setting, `ground_` model tests | a CI look tour of hill, forest and road in the Lumen Forest and Elderwood; side by side with the proof renders; **Euan looks on his own world** (it works there) |
| **P1** Soft steps | `SoftSlabBlock` family from a table, the pass, tufts, spawn tags, `GroundTop`, painter contract tests, Ascent tests; one hour with Countered's mod as a reference | CI look tour on three lands; body tests; **Euan makes a new world** |
| **P2** The floor | retuned vanilla features, pebbles, twigs, three heights, clover, boulders, roots, palettes table | look tour per biome; `landscost`; frame tour once |
| **P3** Mountains | thresholds, strata, scree skirts, ledges, snow layers, gully planting; far shader parity | look tour on a range; seam audit |
| **P4** Roads | banks, bands, edges, kerb stones | the W7 road tours that exist; the road bench fingerprint of the *plan* unchanged |
| **P5** The land speaks | rank palettes, wild life weights, footstep tags | after regions phase 2 |

**Estimate at the pace the history shows**: about 20 builder-hours, one builder for P0, then P1, then two builders for P2 and P4
with P3 beside P0: a day, a day and a half of wall-clock. Bottlenecks that do not compress: Euan's looks after P0 and P1; the
sprite set; CI look tours; one Mac frame tour. No tour is needed to *prove* a plan decision; the bench and renders are
the evidence, and the GameTests carry the rest.

---

## 10. Decisions

Recommended answer first; every one is "yes" unless it says otherwise.

| # | Decision | Recommended | Why |
|---|---|---|---|
| D1 | Build the land softening **ourselves**; mods are references (Countered's Terrain Slabs for a one-hour look, Fast Better Grass as a fallback, Shadowy Path Blocks only if path edges stay dark) | **Yes** | our ground blocks, painters, City Sight and Far Sight are ours; a mod can't know them |
| D2 | **P0 seamless ground first**, client side, on in every world, with a setting; Euan looks at it before P1 | **Yes** | the biggest look win for the least code; it works on his current world today |
| D3 | **Half-block steps** (slabs) for gentle ground, new worlds only; **no eighth-block layers** to sculpt | **Yes** | slabs are Minecraft; layers pull toward a smooth mesh; -97% one-block steps on rolling land |
| D4 | **Plants stand inside the slab's own block** (the `tuft` property) | **Yes** | no floating, no bald foot columns; vanilla plants still refuse slabs |
| D5 | Slab blocks are **real blocks**: obtainable, placeable, a grass slab breaks into a dirt slab (silk touch: itself); no recipe | **Yes** | players build with what they find; no recipe keeps the item list clean |
| D6 | The soft pass runs **before** the painters, which own their ground; **roads get banks and bands** (P4) | **Yes** | no half-slab can survive in a street or yard; roads stop being walls |
| D7 | **Leave Tectonic and the erosion alone** | **Yes** | measured: the steps are the grid, not the noise (43.1% vs 43.0%) |
| D8 | **Mountains: slope-led rock and strata for every steep slope** (not only Alpine biomes), thresholds lowered | **Yes** | the white streaks and brown stairs are soil over ore blobs on slopes the rule never reaches |
| D9 | **Ground cover is vanilla-first**: retune `fallen_tree`, `forest_rock`, `moss_patch`, `bush`, `wildflower`, `dry_grass`, litter, in drifts; a small set of our own (pebbles, twigs, three grass heights, clover) | **Yes** | most of it already exists in the game; only the small stuff needs art |
| D10 | **Our own boulders and outcrops** with slab and stair edges, by biome | **Yes** | Euan asked for stones; a rounded boulder is the cheapest cinematic detail |
| D11 | **City Sight and Far Sight match**: wrapped sides, the same tint drift, the same slope palette | **Yes** | a seam between near and far would undo the work |
| D12 | **A slide down a soft hillside is wanted** (the stair-slide rule already treats stepped slabs as a run) | **Yes** | it is the Runner's feel on every hill; three GameTests hold it |
| D13 | Worldgen levers ride a data pack and a feature flag: **new worlds only** (Euan makes a new world to see P1-P5) | **Yes** | the Lands, Alpine and Villages packs do the same; no old chunk changes |
| D14 | **Cover palettes follow the region rank** (safe land lush, D up scrubby, C up bones and dead wood), built after the regions plan's phase 2 | **Yes** | "the land speaks first" is in that plan; one table, no new system |
| D15 | **Budgets**: ≤ 0.5 ms a chunk per feature, ≈ 1 ms total, forest p50 +0.3 ms; **one** Mac frame tour at the end, look tours on CI | **Yes** | SPEC budgets; Euan's tour-queue rule |

---

## 11. Unknowns (verify rather than assume)

- Whether plants sit right on Countered's slabs: its page says vegetation generates on top; I did not read its source. It
  does not change the plan (our tuft design doesn't depend on it).
- The exact mountain shot's biome and height: the audit's row 3 is consistent with the code, not proven from the shot.
- Sodium's mesh cost of the wrapper on a dense forest: the geometry key is the mitigation; the frame tour measures it.
- How the stair-slide actually behaves on a long half-step hillside: P1's body tests answer it.
- The proof renderer is a stand-in (no Lens, no real Sodium AO): it shows the *change*, not the final look. The final look
  is judged on CI look tours.

## 12. Reproduce the proof

```
J=/Volumes/MEDIA/.jdks/jdk-25.0.5+8/Contents/Home/bin
$J/javac -d /tmp/softbench src/main/java/dev/awaken/terrain/alpine/Erosion.java docs/terrain-immersion/proof/SoftBench.java
$J/java -cp /tmp/softbench SoftBench /tmp/softbench-out
SOFTBENCH_OUT=/tmp/softbench-out python3 -I docs/terrain-immersion/proof/scenes.py all ladder   # reads textures from .ref/assets; writes img/
python3 -I docs/terrain-immersion/proof/annotate.py
```

The renderer reads the game's textures from the local reference copy at render time and never writes them to the repo; only
the rendered pictures are committed (like the screenshots already under `docs/`).
